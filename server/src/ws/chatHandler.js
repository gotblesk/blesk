const prisma = require('../db');
// Rate limiter: макс 5 сообщений за 3 секунды на пользователя
const messageRateLimits = new Map(); // userId → [timestamps]
// Таймауты набора текста: `${userId}:${chatId}` → timeoutId
const typingTimeouts = new Map();
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
}, 60000);

function chatHandler(io, socket) {
  const userId = socket.userId;

  // При подключении — join во все комнаты пользователя
  async function joinUserRooms() {
    const participations = await prisma.roomParticipant.findMany({
      where: { userId },
      select: { roomId: true },
    });
    for (const p of participations) {
      socket.join(p.roomId);
    }

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

      socket.broadcast.emit('user:online', { userId, status: newStatus });

      await prisma.user.update({
        where: { id: userId },
        data: { status: newStatus },
      });
    }
  }

  // Отправка сообщения
  socket.on('message:send', async ({ chatId, text, tempId, replyToId }) => {
    if (!chatId || !text?.trim()) return;
    if (text.length > 4000) return; // Лимит длины сообщения

    // Rate limiting — макс 5 сообщений за 3 секунды
    if (isRateLimited(userId)) {
      socket.emit('message:error', { tempId, error: 'Слишком быстро! Подождите немного.' });
      return;
    }

    // Сообщение отправлено — сбросить таймаут набора текста
    clearTypingTimeout(chatId);

    try {
      // Проверяем что пользователь участник чата
      const participant = await prisma.roomParticipant.findUnique({
        where: { roomId_userId: { roomId: chatId, userId } },
      });
      if (!participant) return;

      // Сохраняем в БД
      const msgData = {
        roomId: chatId,
        userId,
        text: text.trim(),
        type: 'text',
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

      // Отправляем всем в комнате
      io.to(chatId).emit('message:new', {
        id: message.id,
        tempId,
        chatId: message.roomId,
        userId: message.userId,
        username: message.user.username,
        hue: message.user.hue,
        text: message.text,
        createdAt: message.createdAt,
        replyTo: message.replyTo || null,
      });

      // Проверяем упоминания (@username)
      const mentionRegex = /@(\w+)/g;
      let match;
      const mentionedUsernames = new Set();
      while ((match = mentionRegex.exec(text)) !== null) {
        mentionedUsernames.add(match[1]);
      }

      if (mentionedUsernames.size > 0) {
        const mentionedUsers = await prisma.user.findMany({
          where: {
            username: { in: [...mentionedUsernames] },
            id: { not: userId },
          },
          select: { id: true },
        });

        const room = await prisma.room.findUnique({
          where: { id: chatId },
          select: { name: true },
        });

        for (const mentioned of mentionedUsers) {
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

          for (const [, s] of io.sockets.sockets) {
            if (s.userId === mentioned.id) {
              s.emit('notification:new', notification);
            }
          }
        }
      }
    } catch (err) {
      console.error('message:send error:', err);
      socket.emit('message:error', { tempId, error: 'Не удалось отправить' });
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

  // Typing indicators — только если пользователь в этой Socket.io комнате
  socket.on('typing:start', ({ chatId }) => {
    if (!chatId || !socket.rooms.has(chatId)) return;
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

  // Disconnect
  socket.on('disconnect', async () => {
    // Очистить все таймауты набора текста для этого пользователя
    for (const [key, timeoutId] of typingTimeouts) {
      if (key.startsWith(`${userId}:`)) {
        clearTimeout(timeoutId);
        typingTimeouts.delete(key);
      }
    }

    // Если невидимка — не оповещать об офлайне (для других и так не был виден)
    if (socket.userStatus !== 'invisible') {
      socket.broadcast.emit('user:offline', { userId });
    }
    try {
      // Сохраняем offline только если не invisible/dnd (чтобы сохранить статус при переподключении)
      const savedStatus = socket.userStatus;
      if (savedStatus === 'invisible' || savedStatus === 'dnd') {
        // Оставить как было — не менять на offline
      } else {
        await prisma.user.update({
          where: { id: userId },
          data: { status: 'offline', lastSeenAt: new Date() },
        });
      }
    } catch {}
  });

  // Запускаем join
  joinUserRooms().catch(console.error);
}

module.exports = { chatHandler };
