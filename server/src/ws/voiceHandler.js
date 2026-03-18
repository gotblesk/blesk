const prisma = require('../db');
const { createRouter, createWebRtcTransport } = require('../services/mediasoup');
// In-memory хранилище голосовых комнат
// roomId → { router, peers: Map<userId, PeerData> }
const voiceRooms = new Map();

// Rate limiter для текстовых сообщений в голосовых комнатах
// userId → [timestamps]
const voiceChatRateLimits = new Map();
const VOICE_CHAT_RATE_WINDOW = 3000; // 3 секунды
const VOICE_CHAT_RATE_MAX = 5; // 5 сообщений

function isVoiceChatRateLimited(uid) {
  const now = Date.now();
  let timestamps = voiceChatRateLimits.get(uid);
  if (!timestamps) {
    timestamps = [];
    voiceChatRateLimits.set(uid, timestamps);
  }
  // Убрать старые
  while (timestamps.length > 0 && now - timestamps[0] > VOICE_CHAT_RATE_WINDOW) {
    timestamps.shift();
  }
  if (timestamps.length >= VOICE_CHAT_RATE_MAX) {
    return true;
  }
  timestamps.push(now);
  return false;
}

// Чистка Map раз в минуту чтобы не утекала память
setInterval(() => {
  const now = Date.now();
  for (const [uid, ts] of voiceChatRateLimits) {
    while (ts.length > 0 && now - ts[0] > VOICE_CHAT_RATE_WINDOW) ts.shift();
    if (ts.length === 0) voiceChatRateLimits.delete(uid);
  }
}, 60000);

// PeerData = { socketId, username, hue, muted, deafened, transports: Map, producers: Map, consumers: Map }

// Получить или создать комнату с mediasoup Router
async function getOrCreateRoom(roomId) {
  if (voiceRooms.has(roomId)) {
    return voiceRooms.get(roomId);
  }

  const router = await createRouter();
  const room = {
    router,
    peers: new Map(),
  };
  voiceRooms.set(roomId, room);
  return room;
}

// Очистить ресурсы пира
function cleanupPeer(peer) {
  // Закрыть все consumers
  if (peer.consumers) {
    for (const consumer of peer.consumers.values()) {
      consumer.close();
    }
  }
  // Закрыть все producers
  if (peer.producers) {
    for (const producer of peer.producers.values()) {
      producer.close();
    }
  }
  // Закрыть все transports
  if (peer.transports) {
    for (const transport of peer.transports.values()) {
      transport.close();
    }
  }
}

// Удалить пустую комнату
function cleanupRoom(roomId) {
  const room = voiceRooms.get(roomId);
  if (room && room.peers.size === 0) {
    room.router.close();
    voiceRooms.delete(roomId);
  }
}

function voiceHandler(io, socket) {
  const userId = socket.userId;

  // ═══ Войти в голосовую комнату ═══
  socket.on('voice:join', async ({ roomId }, callback) => {
    try {
      // Для звонков (call:chatId) — проверить что пользователь участник чата
      const isCall = roomId.startsWith('call:');

      if (isCall) {
        const chatId = roomId.slice(5); // убрать 'call:'
        const participant = await prisma.roomParticipant.findUnique({
          where: { roomId_userId: { roomId: chatId, userId } },
        });
        if (!participant) {
          return callback?.({ error: 'Вы не участник этого чата' });
        }
      } else {
        // Проверить что комната существует и это voice
        const dbRoom = await prisma.room.findUnique({ where: { id: roomId } });
        if (!dbRoom || dbRoom.type !== 'voice') {
          return callback?.({ error: 'Комната не найдена' });
        }

        // Проверить что пользователь — владелец или участник
        const isOwner = dbRoom.ownerId === userId;
        if (!isOwner) {
          const member = await prisma.roomParticipant.findUnique({
            where: { roomId_userId: { roomId, userId } },
          }).catch(() => null);
          if (!member) {
            return callback?.({ error: 'Вы не участник этой комнаты' });
          }
        }
      }

      // Получить данные пользователя
      const user = await prisma.user.findUnique({
        where: { id: userId },
        select: { username: true, hue: true },
      });

      const room = await getOrCreateRoom(roomId);

      // Если уже в комнате — пропустить
      if (room.peers.has(userId)) {
        return callback?.({ error: 'Уже в комнате' });
      }

      // Создать пир
      const peer = {
        socketId: socket.id,
        username: user.username,
        hue: user.hue,
        muted: false,
        deafened: false,
        transports: new Map(),
        producers: new Map(),
        consumers: new Map(),
      };

      room.peers.set(userId, peer);
      socket.join(`voice:${roomId}`);

      // Отправить RTP capabilities и список участников
      const existingPeers = [];
      for (const [peerId, peerData] of room.peers) {
        if (peerId !== userId) {
          existingPeers.push({
            userId: peerId,
            username: peerData.username,
            hue: peerData.hue,
            muted: peerData.muted,
            deafened: peerData.deafened,
            // Producer IDs + типы для создания consumers
            producers: Array.from(peerData.producers.entries()).map(([id, p]) => ({
              producerId: id,
              producerType: p.appData.type || 'audio',
            })),
          });
        }
      }

      callback?.({
        routerRtpCapabilities: room.router.rtpCapabilities,
        peers: existingPeers,
      });

      // Уведомить остальных
      socket.to(`voice:${roomId}`).emit('voice:user-joined', {
        userId,
        username: user.username,
        hue: user.hue,
      });
    } catch (err) {
      console.error('voice:join error:', err);
      callback?.({ error: 'Ошибка подключения' });
    }
  });

  // ═══ Создать WebRTC транспорт ═══
  socket.on('voice:createTransport', async ({ roomId, direction }, callback) => {
    try {
      const room = voiceRooms.get(roomId);
      if (!room || !room.peers.has(userId)) {
        return callback?.({ error: 'Не в комнате' });
      }

      const { transport, params } = await createWebRtcTransport(room.router);
      // Сохранить направление транспорта (send/recv) в appData
      transport.appData.direction = direction || 'send';
      const peer = room.peers.get(userId);
      peer.transports.set(transport.id, transport);

      callback?.({ params });
    } catch (err) {
      console.error('voice:createTransport error:', err);
      callback?.({ error: 'Ошибка создания транспорта' });
    }
  });

  // ═══ Подключить транспорт (DTLS) ═══
  socket.on('voice:connectTransport', async ({ roomId, transportId, dtlsParameters }, callback) => {
    try {
      const room = voiceRooms.get(roomId);
      if (!room || !room.peers.has(userId)) {
        return callback?.({ error: 'Не в комнате' });
      }

      const peer = room.peers.get(userId);
      const transport = peer.transports.get(transportId);
      if (!transport) {
        return callback?.({ error: 'Транспорт не найден' });
      }

      await transport.connect({ dtlsParameters });
      callback?.({ ok: true });
    } catch (err) {
      console.error('voice:connectTransport error:', err);
      callback?.({ error: 'Ошибка подключения транспорта' });
    }
  });

  // ═══ Начать отправку аудио/видео (produce) ═══
  socket.on('voice:produce', async ({ roomId, transportId, kind, rtpParameters, appData }, callback) => {
    try {
      const room = voiceRooms.get(roomId);
      if (!room || !room.peers.has(userId)) {
        return callback?.({ error: 'Не в комнате' });
      }

      const peer = room.peers.get(userId);
      const transport = peer.transports.get(transportId);
      if (!transport) {
        return callback?.({ error: 'Транспорт не найден' });
      }

      const producer = await transport.produce({ kind, rtpParameters, appData: appData || {} });
      peer.producers.set(producer.id, producer);

      producer.on('transportclose', () => {
        producer.close();
        peer.producers.delete(producer.id);
      });

      callback?.({ producerId: producer.id });

      // Уведомить остальных о новом producer
      socket.to(`voice:${roomId}`).emit('voice:newProducer', {
        userId,
        producerId: producer.id,
        kind,
        producerType: producer.appData.type || 'audio',
      });
    } catch (err) {
      console.error('voice:produce error:', err);
      callback?.({ error: 'Ошибка создания producer' });
    }
  });

  // ═══ Получить аудио другого участника (consume) ═══
  socket.on('voice:consume', async ({ roomId, producerId, rtpCapabilities }, callback) => {
    try {
      const room = voiceRooms.get(roomId);
      if (!room || !room.peers.has(userId)) {
        return callback?.({ error: 'Не в комнате' });
      }

      // Проверить поддержку кодека
      if (!room.router.canConsume({ producerId, rtpCapabilities })) {
        return callback?.({ error: 'Не могу consume — несовместимые кодеки' });
      }

      const peer = room.peers.get(userId);

      // Найти recv transport по направлению из appData
      let recvTransport = null;
      for (const t of peer.transports.values()) {
        if (t.appData?.direction === 'recv') {
          recvTransport = t;
          break;
        }
      }

      if (!recvTransport) {
        // Фоллбэк: последний созданный транспорт
        recvTransport = Array.from(peer.transports.values()).pop();
      }

      if (!recvTransport) {
        return callback?.({ error: 'Нет транспорта для приёма' });
      }

      const consumer = await recvTransport.consume({
        producerId,
        rtpCapabilities,
        paused: true, // Начинаем на паузе, клиент сделает resume
      });

      peer.consumers.set(consumer.id, consumer);

      consumer.on('transportclose', () => {
        consumer.close();
        peer.consumers.delete(consumer.id);
      });

      consumer.on('producerclose', () => {
        consumer.close();
        peer.consumers.delete(consumer.id);
        socket.emit('voice:consumerClosed', { consumerId: consumer.id });
      });

      callback?.({
        consumerId: consumer.id,
        producerId,
        kind: consumer.kind,
        rtpParameters: consumer.rtpParameters,
      });
    } catch (err) {
      console.error('voice:consume error:', err);
      callback?.({ error: 'Ошибка создания consumer' });
    }
  });

  // ═══ Возобновить consumer ═══
  socket.on('voice:resume', async ({ roomId, consumerId }, callback) => {
    try {
      const room = voiceRooms.get(roomId);
      if (!room || !room.peers.has(userId)) {
        return callback?.({ error: 'Не в комнате' });
      }

      const peer = room.peers.get(userId);
      const consumer = peer.consumers.get(consumerId);
      if (!consumer) {
        return callback?.({ error: 'Consumer не найден' });
      }

      await consumer.resume();
      callback?.({ ok: true });
    } catch (err) {
      console.error('voice:resume error:', err);
      callback?.({ error: 'Ошибка resume' });
    }
  });

  // ═══ Мут/анмут ═══
  socket.on('voice:mute', ({ roomId, muted }) => {
    const room = voiceRooms.get(roomId);
    if (!room || !room.peers.has(userId)) return;

    const peer = room.peers.get(userId);
    peer.muted = muted;

    // Пауза/возобновление только аудио producers (не видео)
    for (const producer of peer.producers.values()) {
      if (producer.kind === 'audio') {
        if (muted) {
          producer.pause();
        } else {
          producer.resume();
        }
      }
    }

    socket.to(`voice:${roomId}`).emit('voice:user-muted', { userId, muted });
  });

  // ═══ Деафен ═══
  socket.on('voice:deafen', ({ roomId, deafened }) => {
    const room = voiceRooms.get(roomId);
    if (!room || !room.peers.has(userId)) return;

    const peer = room.peers.get(userId);
    peer.deafened = deafened;

    // Пауза/возобновление всех consumers
    for (const consumer of peer.consumers.values()) {
      if (deafened) {
        consumer.pause();
      } else {
        consumer.resume();
      }
    }

    socket.to(`voice:${roomId}`).emit('voice:user-deafened', { userId, deafened });
  });

  // ═══ Текстовый чат в голосовой комнате ═══
  socket.on('voice:chat', ({ roomId, text }) => {
    if (!text || typeof text !== 'string' || text.trim().length === 0) return;
    if (text.length > 500) return; // Лимит длины сообщения

    // Rate limiting — макс 5 сообщений за 3 секунды
    if (isVoiceChatRateLimited(userId)) {
      socket.emit('voice:chat:error', { error: 'Слишком быстро! Подождите немного.' });
      return;
    }

    const room = voiceRooms.get(roomId);
    if (!room || !room.peers.has(userId)) return;

    const peer = room.peers.get(userId);
    const message = {
      id: `vc_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      userId,
      username: peer.username,
      hue: peer.hue,
      text: text.trim(),
      timestamp: Date.now(),
    };

    // Отправить всем в комнате включая отправителя
    io.to(`voice:${roomId}`).emit('voice:chat:message', { roomId, message });
  });

  // ═══ Выйти из голосовой комнаты ═══
  socket.on('voice:leave', ({ roomId }) => {
    leaveRoom(io, socket, userId, roomId);
  });

  // ═══ Отключение сокета — cleanup ═══
  socket.on('disconnect', () => {
    for (const [roomId, room] of voiceRooms) {
      if (room.peers.has(userId)) {
        leaveRoom(io, socket, userId, roomId);
      }
    }
  });
}

// Выйти из комнаты + cleanup
function leaveRoom(io, socket, userId, roomId) {
  const room = voiceRooms.get(roomId);
  if (!room || !room.peers.has(userId)) return;

  const peer = room.peers.get(userId);
  cleanupPeer(peer);
  room.peers.delete(userId);
  socket.leave(`voice:${roomId}`);

  // Уведомить остальных
  io.to(`voice:${roomId}`).emit('voice:user-left', { userId });

  // Очистить пустую комнату
  cleanupRoom(roomId);
}

// Экспорт voiceRooms для REST API
module.exports = { voiceHandler, voiceRooms, cleanupPeer };
