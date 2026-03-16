const { PrismaClient } = require('@prisma/client');

const prisma = new PrismaClient();

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

    // Если невидимка — не оповещать других и не менять статус
    if (savedStatus === 'invisible') {
      // Сохранить что сокет активен, но не светить это
      socket.userStatus = 'invisible';
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

      // Ответ на сообщение
      if (replyToId) {
        msgData.replyToId = replyToId;
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

  // Typing indicators
  socket.on('typing:start', ({ chatId }) => {
    socket.to(chatId).emit('typing:start', { chatId, userId });
  });

  socket.on('typing:stop', ({ chatId }) => {
    socket.to(chatId).emit('typing:stop', { chatId, userId });
  });

  // Disconnect
  socket.on('disconnect', async () => {
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
          data: { status: 'offline' },
        });
      }
    } catch {}
  });

  // Запускаем join
  joinUserRooms().catch(console.error);
}

module.exports = { chatHandler };
