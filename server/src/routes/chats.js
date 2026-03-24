const { Router } = require('express');
const prisma = require('../db');
const { authenticate, requireVerified } = require('../middleware/auth');

const router = Router();

// Список чатов пользователя
router.get('/', authenticate, async (req, res) => {
  try {
    const participations = await prisma.roomParticipant.findMany({
      where: { userId: req.userId },
      include: {
        room: {
          include: {
            messages: {
              orderBy: { createdAt: 'desc' },
              take: 1,
              include: {
                user: { select: { id: true, username: true } },
              },
            },
            participants: {
              include: {
                user: { select: { id: true, username: true, hue: true, status: true, avatar: true } },
              },
            },
          },
        },
      },
    });

    const chats = await Promise.all(
      participations
        .filter((p) => p.room.type === 'chat' || p.room.type === 'group')
        .map(async (p) => {
          const unreadCount = await prisma.message.count({
            where: {
              roomId: p.roomId,
              createdAt: { gt: p.lastReadAt },
              userId: { not: req.userId },
            },
          });

          const lastMessage = p.room.messages[0] || null;

          // Для личного чата — другой участник
          // Для группы — все участники
          const otherParticipants = p.room.participants.filter(
            (pp) => pp.userId !== req.userId
          );

          const base = {
            id: p.room.id,
            name: p.room.name,
            type: p.room.type,
            avatar: p.room.avatar,
            lastMessage: lastMessage
              ? {
                  text: lastMessage.encrypted ? 'Зашифрованное сообщение' : lastMessage.text,
                  username: lastMessage.user.username,
                  createdAt: lastMessage.createdAt,
                  encrypted: lastMessage.encrypted || false,
                }
              : null,
            unreadCount,
          };

          if (p.room.type === 'group') {
            return {
              ...base,
              ownerId: p.room.ownerId,
              memberCount: p.room.participants.length,
              participants: p.room.participants.map((pp) => ({
                ...pp.user,
                role: pp.role,
              })),
            };
          }

          // Личный чат
          return {
            ...base,
            otherUser: otherParticipants[0]?.user || null,
          };
        })
    );

    chats.sort((a, b) => {
      const aTime = a.lastMessage?.createdAt || 0;
      const bTime = b.lastMessage?.createdAt || 0;
      return new Date(bTime) - new Date(aTime);
    });

    res.json(chats);
  } catch (err) {
    console.error('GET /api/chats error:', err);
    res.status(500).json({ error: 'Ошибка загрузки чатов' });
  }
});

// История сообщений
router.get('/:id/messages', authenticate, async (req, res) => {
  try {
    const { id } = req.params;
    const { before, limit = 50 } = req.query;
    const parsedLimit = parseInt(limit) || 50;
    const take = Math.min(Math.max(parsedLimit, 1), 100);

    const participant = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId: id, userId: req.userId } },
    });
    if (!participant) {
      return res.status(403).json({ error: 'Нет доступа к чату' });
    }

    const where = { roomId: id };
    if (before) {
      const beforeMsg = await prisma.message.findUnique({ where: { id: before } });
      if (beforeMsg) {
        where.createdAt = { lt: beforeMsg.createdAt };
      }
    }

    const messages = await prisma.message.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      take,
      include: {
        user: { select: { id: true, username: true, hue: true } },
        replyTo: {
          include: {
            user: { select: { id: true, username: true } },
          },
        },
        attachments: true,
      },
    });

    res.json(messages.reverse());
  } catch (err) {
    console.error('GET /api/chats/:id/messages error:', err);
    res.status(500).json({ error: 'Ошибка загрузки сообщений' });
  }
});

// Создать чат (личный или групповой)
router.post('/', authenticate, requireVerified, async (req, res) => {
  try {
    const { participantId, participantIds, name } = req.body;

    // --- Групповой чат ---
    if (participantIds && Array.isArray(participantIds)) {
      if (participantIds.length < 1) {
        return res.status(400).json({ error: 'Добавьте хотя бы одного участника' });
      }
      if (participantIds.length > 99) {
        return res.status(400).json({ error: 'Максимум 99 участников в группе' });
      }
      if (!name || name.trim().length === 0 || name.trim().length > 50) {
        return res.status(400).json({ error: 'Название группы: 1-50 символов' });
      }

      // Проверяем что все — друзья
      for (const pid of participantIds) {
        const friendship = await prisma.friendRequest.findFirst({
          where: {
            status: 'accepted',
            OR: [
              { senderId: req.userId, receiverId: pid },
              { senderId: pid, receiverId: req.userId },
            ],
          },
        });
        if (!friendship) {
          return res.status(403).json({ error: 'Все участники должны быть друзьями' });
        }
      }

      const allIds = [req.userId, ...participantIds];

      const room = await prisma.room.create({
        data: {
          name: name.trim(),
          type: 'group',
          ownerId: req.userId,
          participants: {
            create: allIds.map((uid) => ({
              userId: uid,
              role: uid === req.userId ? 'owner' : 'member',
            })),
          },
        },
        include: {
          participants: {
            include: {
              user: { select: { id: true, username: true, hue: true, status: true, avatar: true } },
            },
          },
        },
      });

      // Присоединяем сокеты участников к комнате
      const io = req.app.locals.io;
      if (io) {
        for (const [, s] of io.sockets.sockets) {
          if (allIds.includes(s.userId)) {
            s.join(room.id);
          }
        }
      }

      return res.status(201).json({
        id: room.id,
        name: room.name,
        type: 'group',
        memberCount: allIds.length,
        participants: room.participants.map((p) => ({
          ...p.user,
          role: p.role,
        })),
      });
    }

    // --- Личный чат (как раньше) ---
    if (!participantId) {
      return res.status(400).json({ error: 'Укажите участника' });
    }

    const otherUser = await prisma.user.findUnique({
      where: { id: participantId },
      select: { id: true, username: true, hue: true, status: true, avatar: true },
    });
    if (!otherUser) {
      return res.status(404).json({ error: 'Пользователь не найден' });
    }

    const friendship = await prisma.friendRequest.findFirst({
      where: {
        status: 'accepted',
        OR: [
          { senderId: req.userId, receiverId: participantId },
          { senderId: participantId, receiverId: req.userId },
        ],
      },
    });
    if (!friendship) {
      return res.status(403).json({ error: 'Можно писать только друзьям' });
    }

    const existing = await prisma.room.findFirst({
      where: {
        type: 'chat',
        AND: [
          { participants: { some: { userId: req.userId } } },
          { participants: { some: { userId: participantId } } },
        ],
      },
    });

    if (existing) {
      return res.json({ id: existing.id, existing: true });
    }

    const creator = await prisma.user.findUnique({
      where: { id: req.userId },
      select: { id: true, username: true, hue: true, avatar: true },
    });

    const room = await prisma.room.create({
      data: {
        name: '',
        type: 'chat',
        ownerId: req.userId,
        participants: {
          create: [
            { userId: req.userId, role: 'owner' },
            { userId: participantId, role: 'member' },
          ],
        },
      },
    });

    try {
      const notification = await prisma.notification.create({
        data: {
          userId: participantId,
          type: 'system',
          title: 'Новый чат',
          body: `Чат с ${creator.username}`,
          fromUserId: req.userId,
          roomId: room.id,
        },
        include: {
          fromUser: { select: { id: true, username: true, hue: true, avatar: true } },
        },
      });

      const io = req.app.locals.io;
      if (io) {
        for (const [, s] of io.sockets.sockets) {
          if (s.userId === participantId) {
            s.emit('notification:new', notification);
          }
        }
      }
    } catch {}

    res.status(201).json({ id: room.id, otherUser, existing: false });
  } catch (err) {
    console.error('POST /api/chats error:', err);
    res.status(500).json({ error: 'Ошибка создания чата' });
  }
});

// Список участников группы
router.get('/:id/members', authenticate, async (req, res) => {
  try {
    const participant = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId: req.params.id, userId: req.userId } },
    });
    if (!participant) {
      return res.status(403).json({ error: 'Нет доступа' });
    }

    const members = await prisma.roomParticipant.findMany({
      where: { roomId: req.params.id },
      include: {
        user: { select: { id: true, username: true, tag: true, hue: true, status: true, avatar: true } },
      },
    });

    res.json(members.map((m) => ({ userId: m.userId, user: m.user, role: m.role })));
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// Добавить участника в группу (owner/admin)
router.post('/:id/members', authenticate, async (req, res) => {
  try {
    const { userId: newUserId } = req.body;
    const roomId = req.params.id;

    const room = await prisma.room.findUnique({ where: { id: roomId } });
    if (!room || room.type !== 'group') {
      return res.status(404).json({ error: 'Группа не найдена' });
    }

    // Проверяем что запрашивающий — owner или admin
    const requester = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId, userId: req.userId } },
    });
    if (!requester || (requester.role !== 'owner' && requester.role !== 'admin')) {
      return res.status(403).json({ error: 'Нет прав' });
    }

    // Проверяем дружбу
    const friendship = await prisma.friendRequest.findFirst({
      where: {
        status: 'accepted',
        OR: [
          { senderId: req.userId, receiverId: newUserId },
          { senderId: newUserId, receiverId: req.userId },
        ],
      },
    });
    if (!friendship) {
      return res.status(403).json({ error: 'Можно добавлять только друзей' });
    }

    // Проверяем не в группе ли уже
    const existing = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId, userId: newUserId } },
    });
    if (existing) {
      return res.status(400).json({ error: 'Уже в группе' });
    }

    await prisma.roomParticipant.create({
      data: { roomId, userId: newUserId, role: 'member' },
    });

    const newUser = await prisma.user.findUnique({
      where: { id: newUserId },
      select: { id: true, username: true, hue: true, status: true, avatar: true },
    });

    // Socket: оповестить группу
    const io = req.app.locals.io;
    if (io) {
      io.to(roomId).emit('group:member-added', { roomId, user: { ...newUser, role: 'member' } });
      // Присоединить сокет нового участника
      for (const [, s] of io.sockets.sockets) {
        if (s.userId === newUserId) {
          s.join(roomId);
        }
      }
    }

    res.json({ ok: true, user: newUser });
  } catch (err) {
    console.error('POST members error:', err);
    res.status(500).json({ error: 'Ошибка' });
  }
});

// Удалить участника / выйти из группы
router.delete('/:id/members/:userId', authenticate, async (req, res) => {
  try {
    const roomId = req.params.id;
    const targetUserId = req.params.userId;
    const isSelf = targetUserId === req.userId;

    const room = await prisma.room.findUnique({ where: { id: roomId } });
    if (!room || room.type !== 'group') {
      return res.status(404).json({ error: 'Группа не найдена' });
    }

    // Проверить что целевой участник существует
    const target = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId, userId: targetUserId } },
    });
    if (!target) {
      return res.status(404).json({ error: 'Участник не найден в группе' });
    }

    if (isSelf && target.role === 'owner') {
      return res.status(403).json({ error: 'Владелец не может покинуть группу' });
    }

    if (!isSelf) {
      // Удаление другого — только owner/admin
      const requester = await prisma.roomParticipant.findUnique({
        where: { roomId_userId: { roomId, userId: req.userId } },
      });
      if (!requester || (requester.role !== 'owner' && requester.role !== 'admin')) {
        return res.status(403).json({ error: 'Нет прав' });
      }

      // Нельзя удалить owner'а
      if (target.role === 'owner') {
        return res.status(403).json({ error: 'Нельзя удалить создателя группы' });
      }
    }

    await prisma.roomParticipant.delete({
      where: { roomId_userId: { roomId, userId: targetUserId } },
    });

    const io = req.app.locals.io;
    if (io) {
      io.to(roomId).emit('group:member-removed', { roomId, userId: targetUserId, selfLeave: isSelf });
      // Отключить сокет от комнаты
      for (const [, s] of io.sockets.sockets) {
        if (s.userId === targetUserId) {
          s.leave(roomId);
        }
      }
    }

    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// Обновить группу (name, avatar) — owner/admin
router.patch('/:id', authenticate, async (req, res) => {
  try {
    const roomId = req.params.id;
    const { name, avatar } = req.body;

    const requester = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId, userId: req.userId } },
    });
    if (!requester || (requester.role !== 'owner' && requester.role !== 'admin')) {
      return res.status(403).json({ error: 'Нет прав' });
    }

    const data = {};
    if (name && name.trim().length > 0 && name.trim().length <= 50) {
      data.name = name.trim();
    }
    if (avatar !== undefined) {
      if (avatar !== null && (typeof avatar !== 'string' || avatar.length > 500 || !/^\/uploads\//.test(avatar))) {
        return res.status(400).json({ error: 'Некорректный путь аватара' });
      }
      data.avatar = avatar;
    }

    if (Object.keys(data).length === 0) {
      return res.status(400).json({ error: 'Нечего обновлять' });
    }

    await prisma.room.update({ where: { id: roomId }, data });

    const io = req.app.locals.io;
    if (io) {
      io.to(roomId).emit('group:updated', { roomId, ...data });
    }

    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// Закрепить/открепить сообщение
router.post('/:id/messages/:msgId/pin', authenticate, async (req, res) => {
  try {
    const { id: roomId, msgId } = req.params;

    const requester = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId, userId: req.userId } },
    });
    if (!requester) {
      return res.status(403).json({ error: 'Нет доступа' });
    }

    if (requester.role === 'member') {
      return res.status(403).json({ error: 'Только администратор может закреплять сообщения' });
    }

    const message = await prisma.message.findUnique({ where: { id: msgId } });
    if (!message || message.roomId !== roomId) {
      return res.status(404).json({ error: 'Сообщение не найдено' });
    }

    const newPinned = !message.pinned;
    await prisma.message.update({
      where: { id: msgId },
      data: { pinned: newPinned },
    });

    const io = req.app.locals.io;
    if (io) {
      io.to(roomId).emit('message:pinned', { chatId: roomId, messageId: msgId, pinned: newPinned });
    }

    res.json({ ok: true, pinned: newPinned });
  } catch (err) {
    res.status(500).json({ error: 'Ошибка' });
  }
});

// Пометить чат как прочитанный
router.post('/:id/read', authenticate, async (req, res) => {
  try {
    // Проверить что пользователь — участник чата
    const participant = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId: req.params.id, userId: req.userId } },
    });
    if (!participant) {
      return res.status(403).json({ error: 'Нет доступа' });
    }
    await prisma.roomParticipant.update({
      where: { roomId_userId: { roomId: req.params.id, userId: req.userId } },
      data: { lastReadAt: new Date() },
    });
    res.json({ ok: true });
  } catch {
    res.status(500).json({ error: 'Ошибка' });
  }
});

module.exports = router;
