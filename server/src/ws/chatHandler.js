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
  socket.on('message:send', async ({ chatId, text, tempId, replyToId, encrypted }) => {
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

      // Отправляем всем в комнате
      io.to(chatId).emit('message:new', {
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
      });

      // Проверяем упоминания (@username)
      const mentionRegex = /@(\w+)/g;
      let match;
      const mentionedUsernames = new Set();
      while ((match = mentionRegex.exec(text)) !== null) {
        mentionedUsernames.add(match[1]);
      }

      if (mentionedUsernames.size > 0) {
        // Ограничить уведомления только участниками чата (не утекать за пределы)
        const chatParticipants = await prisma.roomParticipant.findMany({
          where: { roomId: chatId },
          select: { userId: true },
        });
        const participantIdSet = new Set(chatParticipants.map((p) => p.userId));

        const mentionedUsers = await prisma.user.findMany({
          where: {
            username: { in: [...mentionedUsernames] },
            id: { not: userId },
          },
          select: { id: true },
        });
        // Фильтруем: только участники чата
        const filteredMentions = mentionedUsers.filter((u) => participantIdSet.has(u.id));

        const room = await prisma.room.findUnique({
          where: { id: chatId },
          select: { name: true },
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

  // Редактирование сообщения
  socket.on('message:edit', async ({ messageId, chatId, text }) => {
    if (!messageId || !chatId || !text?.trim()) return;
    if (text.length > 4000) return;

    try {
      const message = await prisma.message.findUnique({ where: { id: messageId } });
      if (!message || message.userId !== userId || message.roomId !== chatId) return;

      await prisma.message.update({
        where: { id: messageId },
        data: { text: text.trim() },
      });

      io.to(chatId).emit('message:edited', { messageId, chatId, text: text.trim(), editedAt: new Date() });
    } catch (err) {
      console.error('message:edit error:', err);
    }
  });

  // Удаление сообщения
  socket.on('message:delete', async ({ messageId, chatId }) => {
    if (!messageId || !chatId) return;

    try {
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
      await prisma.message.delete({ where: { id: messageId } });

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

  // Публикация поста в канал через сокет (текстовый)
  socket.on('channel:post', async ({ channelId, text, tempId }) => {
    if (!channelId || !text?.trim()) return;
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
    // Очистить таймауты набора текста (собираем ключи чтобы не мутировать Map)
    const keysToDelete = [];
    for (const [key] of typingTimeouts) {
      if (key.startsWith(`${userId}:`)) keysToDelete.push(key);
    }
    for (const key of keysToDelete) {
      clearTimeout(typingTimeouts.get(key));
      typingTimeouts.delete(key);
    }

    // Если невидимка — не оповещать об офлайне (для других и так не был виден)
    if (socket.userStatus !== 'invisible') {
      // Оповестить только тех, кто в одних комнатах
      const rooms = Array.from(socket.rooms).filter((r) => r !== socket.id);
      for (const roomId of rooms) {
        socket.to(roomId).emit('user:offline', { userId });
      }
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
