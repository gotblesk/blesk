const prisma = require('../db');
const jwt = require('jsonwebtoken');
const { fetchOgData } = require('../services/ogScraper');
const { emitToUser } = require('../utils/socketUtils');
// Rate limiter: макс 5 сообщений за 3 секунды на пользователя
const messageRateLimits = new Map(); // userId → [timestamps]
// Таймауты набора текста: `${userId}:${chatId}` → timeoutId
const typingTimeouts = new Map();
// Throttle для typing:start: `${userId}:${chatId}` → timestamp
const typingRateLimits = new Map();
const RATE_LIMIT_WINDOW = 3000; // 3 секунды
const RATE_LIMIT_MAX = 5; // 5 сообщений

function isRateLimited(uid) {
  const now = Date.now();
  let timestamps = messageRateLimits.get(uid);
  if (!timestamps) {
    timestamps = [];
    messageRateLimits.set(uid, timestamps);
  }
  // Убрать старые
  while (timestamps.length > 0 && now - timestamps[0] > RATE_LIMIT_WINDOW) {
    timestamps.shift();
  }
  if (timestamps.length >= RATE_LIMIT_MAX) {
    return true;
  }
  timestamps.push(now);
  return false;
}

// Чистка Map раз в минуту чтобы не утекала память
setInterval(() => {
  const now = Date.now();
  for (const [uid, ts] of messageRateLimits) {
    while (ts.length > 0 && now - ts[0] > RATE_LIMIT_WINDOW) ts.shift();
    if (ts.length === 0) messageRateLimits.delete(uid);
  }
  // Чистка typingRateLimits — записи старше 10 секунд
  for (const [key, timestamp] of typingRateLimits) {
    if (now - timestamp > 10000) {
      typingRateLimits.delete(key);
    }
  }
}, 60000);

function chatHandler(io, socket) {
  const userId = socket.userId;

  // [CRIT-1] Периодическая ревалидация токена (каждые 10 минут)
  const JWT_SECRET = process.env.JWT_SECRET;
  const tokenRecheckInterval = setInterval(async () => {
    try {
      // socket.auth.token обновляется клиентом при каждом переподключении —
      // всегда читаем из него, а не из socket.handshake.auth (иммутабелен)
      let token = socket.auth?.token || socket.handshake.auth?.token;
      if (!token && socket.handshake.headers?.cookie) {
        const cookieMatch = socket.handshake.headers.cookie.match(/blesk_token=([^;]+)/);
        if (cookieMatch) token = decodeURIComponent(cookieMatch[1]);
      }
      if (!token) { socket.disconnect(true); return; }
      jwt.verify(token, JWT_SECRET);
      // Проверить бан
      const u = await prisma.user.findUnique({ where: { id: userId }, select: { banned: true } });
      if (!u || u.banned) {
        socket.emit('auth:banned', { reason: 'Аккаунт заблокирован' });
        socket.disconnect(true);
      }
    } catch {
      // Токен истёк — отключить
      socket.disconnect(true);
    }
  }, 10 * 60 * 1000);
  socket.on('disconnect', () => clearInterval(tokenRecheckInterval));

  // При подключении — join во все комнаты пользователя
  async function joinUserRooms() {
    const participations = await prisma.roomParticipant.findMany({
      where: { userId },
      select: { roomId: true },
    });
    for (const p of participations) {
      socket.join(p.roomId);
    }

    // Присоединить к каналам на которые подписан
    const subs = await prisma.channelSubscriber.findMany({
      where: { userId },
      select: { channelId: true },
    });
    for (const s of subs) socket.join(s.channelId);

    // Загрузить текущий статус пользователя из БД
    const user = await prisma.user.findUnique({
      where: { id: userId },
      select: { status: true },
    });

    const savedStatus = user?.status || 'online';
    // Клиент может отключить видимость онлайна (showOnline: false → невидимка)
    const showOnline = socket.handshake?.auth?.showOnline !== false;

    // Если невидимка (по статусу или настройке showOnline) — не оповещать
    if (savedStatus === 'invisible' || !showOnline) {
      socket.userStatus = 'invisible';

      // Если showOnline выключен — обновить статус в БД на invisible
      if (!showOnline && savedStatus !== 'invisible') {
        await prisma.user.update({
          where: { id: userId },
          data: { status: 'invisible' },
        });
      }
    } else {
      // Установить онлайн или восстановить DND
      const newStatus = savedStatus === 'dnd' ? 'dnd' : 'online';
      socket.userStatus = newStatus;

      // Оповестить только тех, кто в одних комнатах (не broadcast всем)
      const rooms = Array.from(socket.rooms).filter((r) => r !== socket.id);
      for (const roomId of rooms) {
        socket.to(roomId).emit('user:online', { userId, status: newStatus });
      }

      await prisma.user.update({
        where: { id: userId },
        data: { status: newStatus },
      });
    }
  }

  // Отправка сообщения
  // [HIGH-1] shieldHeader проходит транзитом; callback для ACK
  socket.on('message:send', async ({ chatId, text, tempId, replyToId, encrypted, shieldHeader }, callback) => {
    // [MED-5] Валидация размера payload
    if (!chatId || typeof chatId !== 'string' || chatId.length > 36) return;
    if (typeof text !== 'string' || !text.trim()) return;
    if (text.length > 4000) return;
    if (encrypted !== undefined && typeof encrypted !== 'boolean') return;
    if (shieldHeader && JSON.stringify(shieldHeader).length > 10000) return;

    try {
      // Сначала авторизация — проверяем что пользователь участник чата
      const participant = await prisma.roomParticipant.findUnique({
        where: { roomId_userId: { roomId: chatId, userId } },
      });
      if (!participant) {
        socket.emit('message:error', { tempId, error: 'Вы не участник этого чата' });
        return;
      }

      // [CRIT-3] Каналы — только owner/admin могут публиковать через message:send
      const room = await prisma.room.findUnique({ where: { id: chatId }, select: { type: true } });
      if (room?.type === 'channel' && !['owner', 'admin'].includes(participant.role)) {
        socket.emit('message:error', { tempId, error: 'Нет прав на публикацию в канале' });
        return;
      }

      // Потом rate limiting — макс 5 сообщений за 3 секунды
      if (isRateLimited(userId)) {
        socket.emit('message:error', { tempId, error: 'Слишком быстро! Подождите немного.' });
        return;
      }

      // Сообщение отправлено — сбросить таймаут набора текста
      clearTypingTimeout(chatId);

      // Сохраняем в БД
      const msgData = {
        roomId: chatId,
        userId,
        text: text.trim(),
        type: 'text',
        encrypted: encrypted === true,
      };

      // Ответ на сообщение — проверить что оно из того же чата
      if (replyToId) {
        const replyMsg = await prisma.message.findUnique({
          where: { id: replyToId },
          select: { roomId: true },
        });
        if (replyMsg && replyMsg.roomId === chatId) {
          msgData.replyToId = replyToId;
        }
      }

      const message = await prisma.message.create({
        data: msgData,
        include: {
          user: { select: { id: true, username: true, hue: true } },
          replyTo: {
            select: {
              id: true,
              text: true,
              user: { select: { username: true } },
            },
          },
        },
      });

      // Отправляем всем в комнате (shieldHeader передаётся транзитом)
      const emitData = {
        id: message.id,
        tempId,
        chatId: message.roomId,
        userId: message.userId,
        username: message.user.username,
        hue: message.user.hue,
        text: message.text,
        encrypted: message.encrypted,
        createdAt: message.createdAt,
        replyTo: message.replyTo || null,
      };
      // Shield header — только для первого сообщения X3DH (транзит)
      if (shieldHeader && typeof shieldHeader === 'object') {
        emitData.shieldHeader = shieldHeader;
      }
      io.to(chatId).emit('message:new', emitData);

      // Фоновый скрапинг OG-данных для ссылок в сообщении
      if (!encrypted) {
        const urlMatch = text?.match(/(https?:\/\/[^\s]+)/);
        if (urlMatch) {
          fetchOgData(urlMatch[0]).then(ogData => {
            if (ogData) {
              io.to(chatId).emit('message:og', { messageId: message.id, chatId, linkPreview: ogData });
            }
          }).catch(() => {});
        }
      }

      // Проверяем упоминания (@username) — макс 5 на сообщение
      const MAX_MENTIONS = 5;
      const mentionRegex = /@(\w+)/g;
      let match;
      const mentionedUsernames = new Set();
      while ((match = mentionRegex.exec(text)) !== null && mentionedUsernames.size < MAX_MENTIONS) {
        mentionedUsernames.add(match[1]);
      }

      if (mentionedUsernames.size > 0) {
        // Batch: участники чата с мьют-статусом (1 запрос вместо N+1)
        const chatParticipants = await prisma.roomParticipant.findMany({
          where: { roomId: chatId },
          select: { userId: true, isMuted: true, mutedUntil: true },
        });
        const participantMap = new Map(chatParticipants.map((p) => [p.userId, p]));

        const [mentionedUsers, room] = await Promise.all([
          prisma.user.findMany({
            where: {
              username: { in: [...mentionedUsernames] },
              id: { not: userId },
            },
            select: { id: true },
          }),
          prisma.room.findUnique({
            where: { id: chatId },
            select: { name: true },
          }),
        ]);

        // Фильтруем: только участники чата, не замьюченные
        const now = new Date();
        const filteredMentions = mentionedUsers.filter((u) => {
          const p = participantMap.get(u.id);
          if (!p) return false;
          const isChatMuted = p.isMuted && (!p.mutedUntil || p.mutedUntil > now);
          return !isChatMuted;
        });

        for (const mentioned of filteredMentions) {
          const notification = await prisma.notification.create({
            data: {
              userId: mentioned.id,
              type: 'mention',
              title: `${message.user.username} упомянул вас`,
              body: room?.name ? `в чате ${room.name}` : 'в чате',
              fromUserId: userId,
              roomId: chatId,
            },
            include: {
              fromUser: { select: { id: true, username: true, hue: true, avatar: true } },
            },
          });

          emitToUser(mentioned.id, 'notification:new', notification);
        }
      }
    } catch (err) {
      console.error('message:send error:', err);
      socket.emit('message:error', { tempId, error: 'Не удалось отправить' });
    }
  });

  // Редактирование сообщения (поддержка E2E encrypted flag)
  socket.on('message:edit', async ({ messageId, chatId, text, encrypted }) => {
    if (!messageId || !chatId || !text?.trim()) return;
    if (text.length > 4000) return;

    try {
      const [message, participant] = await Promise.all([
        prisma.message.findUnique({ where: { id: messageId } }),
        prisma.roomParticipant.findUnique({
          where: { roomId_userId: { roomId: chatId, userId } },
        }),
      ]);
      if (!message || message.userId !== userId || message.roomId !== chatId) return;
      if (!participant) return;

      const updateData = { text: text.trim() };
      // Сохранить флаг encrypted если передан (для E2E)
      if (encrypted !== undefined) updateData.encrypted = !!encrypted;

      await prisma.message.update({
        where: { id: messageId },
        data: updateData,
      });

      io.to(chatId).emit('message:edited', {
        messageId, chatId, text: text.trim(),
        encrypted: updateData.encrypted ?? message.encrypted ?? false,
        editedAt: new Date(),
      });
    } catch (err) {
      console.error('message:edit error:', err);
    }
  });

  // Удаление сообщения
  socket.on('message:delete', async ({ messageId, chatId }) => {
    if (!messageId || !chatId) return;

    try {
      // Проверить что пользователь участник комнаты
      const memberCheck = await prisma.roomParticipant.findUnique({
        where: { roomId_userId: { roomId: chatId, userId } },
      });
      if (!memberCheck) return;

      const message = await prisma.message.findUnique({ where: { id: messageId } });
      if (!message || message.roomId !== chatId) return;

      // Удалить может автор или admin/owner группы
      const isAuthor = message.userId === userId;
      if (!isAuthor) {
        const participant = await prisma.roomParticipant.findUnique({
          where: { roomId_userId: { roomId: chatId, userId } },
        });
        if (!participant || !['owner', 'admin'].includes(participant.role)) return;
      }

      // Удалить вложения
      await prisma.attachment.deleteMany({ where: { messageId } });

      // [CRIT-4] Phantom Messages — шум ТОЧНО того же размера что и оригинал
      if (message.encrypted) {
        const crypto = require('crypto');
        // Генерировать raw bytes нужной длины, затем кодировать в base64 того же размера
        const originalLen = Buffer.byteLength(message.text, 'utf8');
        // base64(N bytes) = ceil(N/3)*4 chars. Нужно N raw bytes чтобы base64 был >= originalLen
        const rawBytes = Math.ceil(originalLen * 3 / 4);
        const phantomNoise = crypto.randomBytes(rawBytes).toString('base64').slice(0, originalLen);
        await prisma.message.update({
          where: { id: messageId },
          data: { text: phantomNoise, type: 'phantom' },
        });
      } else {
        await prisma.message.delete({ where: { id: messageId } });
      }

      io.to(chatId).emit('message:deleted', { messageId, chatId });
    } catch (err) {
      console.error('message:delete error:', err);
    }
  });

  // Очистить таймаут набора текста для конкретного чата
  function clearTypingTimeout(chatId) {
    const key = `${userId}:${chatId}`;
    const existing = typingTimeouts.get(key);
    if (existing) {
      clearTimeout(existing);
      typingTimeouts.delete(key);
    }
  }

  // ═══ Read Receipts ═══
  socket.on('message:read', ({ chatId, messageIds }) => {
    if (!chatId || !Array.isArray(messageIds) || messageIds.length === 0) return;
    if (!socket.rooms.has(chatId)) return;
    // Лимит: макс 100 сообщений за раз
    const ids = messageIds.slice(0, 100);
    // Отправить другим участникам комнаты
    socket.to(chatId).emit('message:readBy', { chatId, messageIds: ids, userId });
  });

  // Typing indicators — только если пользователь в этой Socket.io комнате
  socket.on('typing:start', ({ chatId }) => {
    if (!chatId || !socket.rooms.has(chatId)) return;

    // Throttle: не чаще 1 раза в 2 секунды на чат
    const typingKey = `${userId}:${chatId}`;
    const lastTyping = typingRateLimits.get(typingKey) || 0;
    if (Date.now() - lastTyping < 2000) return;
    typingRateLimits.set(typingKey, Date.now());

    socket.to(chatId).emit('typing:start', { chatId, userId });

    // Серверный таймаут: если через 5 сек нет typing:stop или нового сообщения —
    // автоматически отправить typing:stop
    clearTypingTimeout(chatId);
    const key = `${userId}:${chatId}`;
    typingTimeouts.set(key, setTimeout(() => {
      typingTimeouts.delete(key);
      socket.to(chatId).emit('typing:stop', { chatId, userId });
    }, 5000));
  });

  socket.on('typing:stop', ({ chatId }) => {
    if (!chatId || !socket.rooms.has(chatId)) return;
    clearTypingTimeout(chatId);
    socket.to(chatId).emit('typing:stop', { chatId, userId });
  });

  // ═══ Реакции на сообщения ═══
  socket.on('message:react', async ({ messageId, emoji }) => {
    if (!messageId || typeof messageId !== 'string' || messageId.length > 36) return;
    if (!emoji || typeof emoji !== 'string' || emoji.length > 2) return;

    try {
      // Проверить что сообщение существует и получить roomId
      const message = await prisma.message.findUnique({
        where: { id: messageId },
        select: { roomId: true },
      });
      if (!message) return;

      // Проверить что пользователь — участник комнаты
      const participant = await prisma.roomParticipant.findUnique({
        where: { roomId_userId: { roomId: message.roomId, userId } },
      });
      if (!participant) return;

      // Toggle реакции в БД
      const existing = await prisma.messageReaction.findUnique({
        where: { messageId_userId_emoji: { messageId, userId, emoji } },
      });

      if (existing) {
        await prisma.messageReaction.delete({ where: { id: existing.id } });
      } else {
        await prisma.messageReaction.create({ data: { messageId, userId, emoji } });
      }

      // Загрузить все реакции для этого сообщения
      const allReactions = await prisma.messageReaction.findMany({
        where: { messageId },
        include: { user: { select: { id: true, username: true } } },
      });

      // Сериализовать для клиента
      const serialized = {};
      for (const r of allReactions) {
        if (!serialized[r.emoji]) serialized[r.emoji] = { count: 0, users: [], userIds: [] };
        serialized[r.emoji].count++;
        serialized[r.emoji].users.push(r.user.username);
        serialized[r.emoji].userIds.push(r.user.id);
      }

      io.to(message.roomId).emit('message:reacted', {
        messageId,
        chatId: message.roomId,
        reactions: serialized,
      });
    } catch (err) {
      console.error('message:react error:', err);
    }
  });

  // Пакетный запрос реакций для видимых сообщений
  socket.on('message:reactions:get', async ({ messageIds }) => {
    if (!Array.isArray(messageIds) || messageIds.length === 0) return;
    // Лимит: макс 100 сообщений за раз
    const ids = messageIds.slice(0, 100);

    try {
      const allReactions = await prisma.messageReaction.findMany({
        where: { messageId: { in: ids } },
        include: { user: { select: { id: true, username: true } } },
      });

      const result = {};
      for (const r of allReactions) {
        if (!result[r.messageId]) result[r.messageId] = {};
        if (!result[r.messageId][r.emoji]) result[r.messageId][r.emoji] = { count: 0, users: [], userIds: [] };
        result[r.messageId][r.emoji].count++;
        result[r.messageId][r.emoji].users.push(r.user.username);
        result[r.messageId][r.emoji].userIds.push(r.user.id);
      }

      socket.emit('message:reactions:batch', result);
    } catch (err) {
      console.error('message:reactions:get error:', err);
    }
  });

  // Пересылка сообщения в другой чат
  socket.on('message:forward', async ({ messageId, targetChatId }, callback) => {
    if (!messageId || typeof messageId !== 'string' || messageId.length > 36) {
      return callback?.({ error: 'Неверный ID сообщения' });
    }
    if (!targetChatId || typeof targetChatId !== 'string' || targetChatId.length > 36) {
      return callback?.({ error: 'Неверный ID чата' });
    }

    if (isRateLimited(userId)) {
      return callback?.({ error: 'Слишком быстро! Подождите немного.' });
    }

    try {
      // Найти оригинальное сообщение
      const original = await prisma.message.findUnique({
        where: { id: messageId },
        include: {
          user: { select: { username: true } },
        },
      });
      if (!original || original.type === 'phantom') {
        return callback?.({ error: 'Сообщение не найдено' });
      }

      // Проверить что пользователь участник обоих чатов
      const [sourceParticipant, targetParticipant] = await Promise.all([
        prisma.roomParticipant.findUnique({
          where: { roomId_userId: { roomId: original.roomId, userId } },
        }),
        prisma.roomParticipant.findUnique({
          where: { roomId_userId: { roomId: targetChatId, userId } },
        }),
      ]);

      if (!sourceParticipant) {
        return callback?.({ error: 'Нет доступа к исходному чату' });
      }
      if (!targetParticipant) {
        return callback?.({ error: 'Нет доступа к целевому чату' });
      }

      // Не пересылать зашифрованные сообщения (E2E)
      if (original.encrypted) {
        return callback?.({ error: 'Нельзя переслать зашифрованное сообщение' });
      }

      // Создать новое сообщение с пометкой о пересылке
      const forwardText = `[Переслано от ${original.user.username}]\n${original.text}`;

      const message = await prisma.message.create({
        data: {
          roomId: targetChatId,
          userId,
          text: forwardText,
          type: 'text',
        },
        include: {
          user: { select: { id: true, username: true, hue: true } },
        },
      });

      io.to(targetChatId).emit('message:new', {
        id: message.id,
        chatId: targetChatId,
        userId: message.userId,
        username: message.user.username,
        hue: message.user.hue,
        text: message.text,
        createdAt: message.createdAt,
        replyTo: null,
      });

      callback?.({ ok: true });
    } catch (err) {
      console.error('message:forward error:', err);
      callback?.({ error: 'Не удалось переслать сообщение' });
    }
  });

  // Публикация поста в канал через сокет (текстовый)
  // [IMP-10] Валидация channelId
  socket.on('channel:post', async ({ channelId, text, tempId }) => {
    if (!channelId || typeof channelId !== 'string' || channelId.length > 36) return;
    if (!text?.trim()) return;
    if (text.length > 4000) return;

    if (isRateLimited(userId)) {
      socket.emit('message:error', { tempId, error: 'Слишком быстро! Подождите немного.' });
      return;
    }

    try {
      // Проверить права (owner или admin)
      const participant = await prisma.roomParticipant.findUnique({
        where: { roomId_userId: { roomId: channelId, userId } },
      });

      if (!participant || !['owner', 'admin'].includes(participant.role)) {
        socket.emit('message:error', { tempId, error: 'Нет прав на публикацию' });
        return;
      }

      // Создать сообщение и инкрементировать счётчик
      const [message] = await prisma.$transaction([
        prisma.message.create({
          data: {
            roomId: channelId,
            userId,
            text: text.trim(),
            type: 'text',
          },
          include: {
            user: { select: { id: true, username: true, hue: true, avatar: true } },
            attachments: true,
          },
        }),
        prisma.channelMeta.update({
          where: { roomId: channelId },
          data: { postCount: { increment: 1 } },
        }),
      ]);

      // Отправить всем подписчикам
      io.to(channelId).emit('message:new', {
        id: message.id,
        tempId,
        chatId: channelId,
        userId: message.userId,
        username: message.user.username,
        hue: message.user.hue,
        avatar: message.user.avatar,
        text: message.text,
        type: message.type,
        attachments: message.attachments,
        createdAt: message.createdAt,
        isChannel: true,
      });
    } catch (err) {
      console.error('channel:post error:', err);
      socket.emit('message:error', { tempId, error: 'Не удалось опубликовать пост' });
    }
  });

  // Disconnect
  socket.on('disconnect', async () => {
    // Очистить таймауты набора текста
    const keysToDelete = [];
    for (const [key] of typingTimeouts) {
      if (key.startsWith(`${userId}:`)) keysToDelete.push(key);
    }
    for (const key of keysToDelete) {
      clearTimeout(typingTimeouts.get(key));
      typingTimeouts.delete(key);
    }

    // Очистить typingRateLimits
    for (const key of typingRateLimits.keys()) {
      if (key.startsWith(`${userId}:`)) {
        typingRateLimits.delete(key);
      }
    }

    try {
      // [CRIT-2] ВСЕГДА обновлять lastSeenAt + статус (предотвращает zombie online)
      // Читаем актуальный статус из БД (socket.userStatus может быть устаревшим)
      const dbUser = await prisma.user.findUnique({
        where: { id: userId },
        select: { status: true },
      });
      const actualStatus = dbUser?.status || socket.userStatus || 'online';

      // [HIGH-2] Использовать сохранённый список комнат (socket.rooms пуст после disconnect)
      // Невидимки не оповещают — они никогда не были видны как online
      if (actualStatus !== 'invisible') {
        const rooms = socket.joinedRoomIds || [];
        for (const roomId of rooms) {
          io.to(roomId).emit('user:offline', { userId });
        }
      }

      if (actualStatus === 'invisible' || actualStatus === 'dnd') {
        // Оставить статус, но обновить lastSeenAt
        await prisma.user.update({
          where: { id: userId },
          data: { lastSeenAt: new Date() },
        });
      } else {
        await prisma.user.update({
          where: { id: userId },
          data: { status: 'offline', lastSeenAt: new Date() },
        });
      }
    } catch (err) { console.error('Failed to update user status on disconnect:', err.message); }
  });

  // [CRIT-4] Запускаем join — при ошибке отключаем сокет
  joinUserRooms()
    .then(() => {
      // [HIGH-2] Сохранить список комнат ДО disconnect (после disconnect socket.rooms пуст)
      socket.joinedRoomIds = Array.from(socket.rooms).filter((r) => r !== socket.id);
    })
    .catch((err) => {
      console.error('joinUserRooms failed, disconnecting:', err);
      socket.disconnect(true);
    });
}

module.exports = { chatHandler };
