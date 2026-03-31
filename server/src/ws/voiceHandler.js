const prisma = require('../db');
const logger = require('../utils/logger');
const { createRouter, createWebRtcTransport } = require('../services/mediasoup');
const { activeCalls } = require('./callHandler');
// In-memory хранилище голосовых комнат
// roomId → { router, peers: Map<userId, PeerData> }
const voiceRooms = new Map();

// [P4] Хранить ID интервалов для корректного cleanup
const voiceIntervalIds = [];

// TTL cleanup — удаляет ghost-peer'ов без активных транспортов (каждые 60 сек)
const _ttlCleanupInterval = setInterval(() => {
  for (const [roomId, room] of voiceRooms) {
    for (const [peerId, peer] of room.peers) {
      const hasActiveTransport = [...peer.transports.values()].some(
        t => !t.closed && t.connectionState !== 'failed'
      );
      if (!hasActiveTransport && peer.transports.size > 0) {
        logger.warn({ peerId, roomId }, '[voice] Ghost peer — removing');
        cleanupPeer(peer);
        room.peers.delete(peerId);
      }
    }
    // Удалить пустую комнату
    if (room.peers.size === 0) {
      room.router.close();
      voiceRooms.delete(roomId);
    }
  }
}, 60000);
voiceIntervalIds.push(_ttlCleanupInterval);

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
voiceIntervalIds.push(setInterval(() => {
  const now = Date.now();
  for (const [uid, ts] of voiceChatRateLimits) {
    while (ts.length > 0 && now - ts[0] > VOICE_CHAT_RATE_WINDOW) ts.shift();
    if (ts.length === 0) voiceChatRateLimits.delete(uid);
  }
}, 60000));

// Rate limiter для voice signaling events (userId → [timestamps])
const voiceSignalRateLimits = new Map();
const VOICE_SIGNAL_WINDOW = 10000; // 10 секунд
const VOICE_SIGNAL_MAX = 30; // 30 событий за 10 секунд

function isVoiceSignalRateLimited(uid) {
  const now = Date.now();
  let timestamps = voiceSignalRateLimits.get(uid);
  if (!timestamps) { timestamps = []; voiceSignalRateLimits.set(uid, timestamps); }
  while (timestamps.length > 0 && now - timestamps[0] > VOICE_SIGNAL_WINDOW) timestamps.shift();
  if (timestamps.length >= VOICE_SIGNAL_MAX) return true;
  timestamps.push(now);
  return false;
}

// Чистка voice signal rate limits
voiceIntervalIds.push(setInterval(() => {
  const now = Date.now();
  for (const [uid, ts] of voiceSignalRateLimits) {
    while (ts.length > 0 && now - ts[0] > VOICE_SIGNAL_WINDOW) ts.shift();
    if (ts.length === 0) voiceSignalRateLimits.delete(uid);
  }
}, 60000));

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
      try { consumer.close(); } catch (err) { logger.error({ err: err.message }, 'Failed to close consumer'); }
    }
    peer.consumers.clear();
  }
  // Закрыть все producers
  if (peer.producers) {
    for (const producer of peer.producers.values()) {
      try { producer.close(); } catch (err) { logger.error({ err: err.message }, 'Failed to close producer'); }
    }
    peer.producers.clear();
  }
  // Закрыть все transports
  if (peer.transports) {
    for (const transport of peer.transports.values()) {
      try { transport.close(); } catch (err) { logger.error({ err: err.message }, 'Failed to close transport'); }
    }
    peer.transports.clear();
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
        // [HIGH-5] Проверить что звонок активен (с grace period)
        const activeCall = activeCalls.get(chatId);
        if (!activeCall) {
          return callback?.({ error: 'Нет активного звонка' });
        }
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

      // Лимит участников комнаты
      const MAX_VOICE_PARTICIPANTS = 25;
      if (room.peers && room.peers.size >= MAX_VOICE_PARTICIPANTS) {
        return callback?.({ error: 'Комната заполнена (максимум 25 участников)' });
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
      logger.error({ err }, 'voice:join error');
      callback?.({ error: 'Ошибка подключения' });
    }
  });

  // ═══ Создать WebRTC транспорт ═══
  socket.on('voice:createTransport', async ({ roomId, direction }, callback) => {
    try {
      if (isVoiceSignalRateLimited(userId)) return callback?.({ error: 'Слишком много запросов' });
      const room = voiceRooms.get(roomId);
      if (!room || !room.peers.has(userId)) {
        return callback?.({ error: 'Не в комнате' });
      }

      // Валидация направления
      if (direction && !['send', 'recv'].includes(direction)) {
        return callback?.({ error: 'Недопустимое направление транспорта' });
      }

      const peer = room.peers.get(userId);

      // Лимит транспортов на пользователя
      const MAX_TRANSPORTS_PER_USER = 4; // send + recv + spare
      if (peer.transports && peer.transports.size >= MAX_TRANSPORTS_PER_USER) {
        return callback?.({ error: 'Превышен лимит транспортов' });
      }

      const { transport, params } = await createWebRtcTransport(room.router);
      transport.appData.direction = direction || 'send';
      peer.transports.set(transport.id, transport);

      callback?.({ params });
    } catch (err) {
      logger.error({ err }, 'voice:createTransport error');
      callback?.({ error: 'Ошибка создания транспорта' });
    }
  });

  // ═══ Подключить транспорт (DTLS) ═══
  socket.on('voice:connectTransport', async ({ roomId, transportId, dtlsParameters }, callback) => {
    try {
      if (isVoiceSignalRateLimited(userId)) return callback?.({ error: 'Слишком много запросов' });
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
      logger.error({ err }, 'voice:connectTransport error');
      callback?.({ error: 'Ошибка подключения транспорта' });
    }
  });

  // ═══ Начать отправку аудио/видео (produce) ═══
  socket.on('voice:produce', async ({ roomId, transportId, kind, rtpParameters, appData }, callback) => {
    try {
      if (isVoiceSignalRateLimited(userId)) return callback?.({ error: 'Слишком много запросов' });
      const room = voiceRooms.get(roomId);
      if (!room || !room.peers.has(userId)) {
        return callback?.({ error: 'Не в комнате' });
      }

      const peer = room.peers.get(userId);
      const transport = peer.transports.get(transportId);
      if (!transport) {
        return callback?.({ error: 'Транспорт не найден' });
      }

      // Лимит producers на пользователя
      const MAX_PRODUCERS_PER_USER = 4; // 1 audio + 1 camera + 1 screen + 1 spare
      if (peer.producers && peer.producers.size >= MAX_PRODUCERS_PER_USER) {
        return callback?.({ error: 'Превышен лимит потоков' });
      }

      // [P20] Валидация кодеков — только разрешённые MIME-типы
      const ALLOWED_AUDIO_CODECS = ['audio/opus', 'audio/pcmu', 'audio/pcma'];
      const ALLOWED_VIDEO_CODECS = ['video/vp8', 'video/vp9', 'video/h264', 'video/h265', 'video/av1'];
      if (rtpParameters?.codecs) {
        for (const codec of rtpParameters.codecs) {
          const mime = (codec.mimeType || '').toLowerCase();
          if (kind === 'audio' && !ALLOWED_AUDIO_CODECS.includes(mime)) {
            return callback?.({ error: `Неподдерживаемый аудио-кодек: ${mime}` });
          }
          if (kind === 'video' && !ALLOWED_VIDEO_CODECS.includes(mime)) {
            return callback?.({ error: `Неподдерживаемый видео-кодек: ${mime}` });
          }
        }
      }

      // [P21] Санитизация appData — разрешаем только { type: 'camera'|'screen'|'audio' }
      const rawType = appData?.type;
      const ALLOWED_TYPES = ['camera', 'screen', 'audio'];
      const sanitizedType = ALLOWED_TYPES.includes(rawType)
        ? rawType
        : (kind === 'audio' ? 'audio' : 'camera');
      appData = { type: sanitizedType };

      // Валидация типа видео-продюсера
      if (kind === 'video' && !['camera', 'screen'].includes(appData.type)) {
        return callback?.({ error: 'Invalid video producer type' });
      }

      // Закрыть существующий producer того же типа перед созданием нового
      const appDataType = appData?.type; // 'camera', 'screen', 'audio'
      if (appDataType && peer.producers) {
        for (const [, existingProducer] of peer.producers) {
          if (existingProducer.appData?.type === appDataType && !existingProducer.closed) {
            existingProducer.close();
            peer.producers.delete(existingProducer.id);
            break;
          }
        }
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
      logger.error({ err }, 'voice:produce error');
      callback?.({ error: 'Ошибка создания producer' });
    }
  });

  // ═══ Получить аудио другого участника (consume) ═══
  socket.on('voice:consume', async ({ roomId, producerId, rtpCapabilities }, callback) => {
    try {
      if (isVoiceSignalRateLimited(userId)) return callback?.({ error: 'Слишком много запросов' });
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
        return callback?.({ error: 'No recv transport found' });
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
      logger.error({ err }, 'voice:consume error');
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
      logger.error({ err }, 'voice:resume error');
      callback?.({ error: 'Ошибка resume' });
    }
  });

  // ═══ Мут/анмут ═══
  socket.on('voice:mute', ({ roomId, muted }) => {
    // [HIGH-3] Rate limiting для mute/deafen
    if (isVoiceSignalRateLimited(userId)) return;
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
    // [HIGH-3] Rate limiting
    if (isVoiceSignalRateLimited(userId)) return;
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
    // Санитизация текста от XSS
    const sanitized = text.trim().replace(/[<>"'`&]/g, (ch) => ({
      '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#x27;', '`': '&#x60;', '&': '&amp;'
    }[ch]));
    const message = {
      // [MED-3] Криптографически стойкий ID вместо Math.random()
      id: `vc_${require('crypto').randomUUID()}`,
      userId,
      username: peer.username,
      hue: peer.hue,
      text: sanitized,
      timestamp: Date.now(),
    };

    // Отправить всем в комнате включая отправителя
    io.to(`voice:${roomId}`).emit('voice:chat:message', { roomId, message });
  });

  // ═══ Выгнать участника из голосовой комнаты ═══
  socket.on('voice:kick', async ({ roomId, userId: targetUserId }, callback) => {
    try {
      const room = voiceRooms.get(roomId);
      if (!room) return callback?.({ error: 'Комната не найдена' });

      // Проверить что инициатор — владелец комнаты (для звонков — любой участник)
      const isCall = roomId.startsWith('call:');
      if (!isCall) {
        const dbRoom = await prisma.room.findUnique({ where: { id: roomId } });
        if (!dbRoom || dbRoom.ownerId !== userId) {
          return callback?.({ error: 'Недостаточно прав' });
        }
      }

      const targetPeer = room.peers.get(targetUserId);
      if (targetPeer) {
        // Закрыть все транспорты/producers/consumers цели
        for (const [, transport] of targetPeer.transports) {
          try { transport.close(); } catch (err) { /* ignore */ }
        }
        cleanupPeer(targetPeer);
        room.peers.delete(targetUserId);

        // Уведомить выгнанного пользователя
        const { emitToUser } = require('../utils/socketUtils');
        emitToUser(targetUserId, 'voice:kicked', { roomId });

        // Уведомить остальных
        socket.to(`voice:${roomId}`).emit('voice:peer-left', { peerId: targetUserId });

        cleanupRoom(roomId);
      }

      callback?.({ success: true });
    } catch (err) {
      logger.error({ err }, 'voice:kick error');
      callback?.({ error: err.message });
    }
  });

  // ═══ Выйти из голосовой комнаты ═══
  socket.on('voice:leave', ({ roomId }) => {
    leaveRoom(io, socket, userId, roomId);
  });

  // ═══ Отключение сокета — cleanup ═══
  socket.on('disconnect', () => {
    // Собираем ID комнат в массив, чтобы не мутировать Map при итерации
    const roomIds = [];
    for (const [roomId, room] of voiceRooms) {
      if (room.peers.has(userId)) roomIds.push(roomId);
    }
    for (const roomId of roomIds) {
      try { leaveRoom(io, socket, userId, roomId); } catch (err) { logger.error({ roomId, err: err.message }, 'Failed to leave voice room on disconnect'); }
    }
    // Очистить rate limits отключившегося пользователя
    voiceChatRateLimits.delete(userId);
    voiceSignalRateLimits.delete(userId);
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

// [P4] Очистить все интервалы при завершении процесса
function clearVoiceIntervals() {
  for (const id of voiceIntervalIds) clearInterval(id);
  voiceIntervalIds.length = 0;
}

// Экспорт voiceRooms для REST API
module.exports = { voiceHandler, voiceRooms, cleanupPeer, clearVoiceIntervals };
