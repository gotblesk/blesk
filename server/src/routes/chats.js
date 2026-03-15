const { Router } = require('express');
const { PrismaClient } = require('@prisma/client');
const { authenticate } = require('../middleware/auth');

const prisma = new PrismaClient();
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
              where: { userId: { not: req.userId } },
              include: {
                user: { select: { id: true, username: true, hue: true, status: true, avatar: true } },
              },
            },
          },
        },
      },
    });

    const chats = await Promise.all(
      participations.map(async (p) => {
        const unreadCount = await prisma.message.count({
          where: {
            roomId: p.roomId,
            createdAt: { gt: p.lastReadAt },
            userId: { not: req.userId },
          },
        });

        const lastMessage = p.room.messages[0] || null;
        const otherUser = p.room.participants[0]?.user || null;

        return {
          id: p.room.id,
          name: p.room.name,
          type: p.room.type,
          otherUser,
          lastMessage: lastMessage
            ? {
                text: lastMessage.text,
                username: lastMessage.user.username,
                createdAt: lastMessage.createdAt,
              }
            : null,
          unreadCount,
        };
      })
    );

    // Сортировка по времени последнего сообщения
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

    // Проверяем участие
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
      take: Math.min(parseInt(limit), 100),
      include: {
        user: { select: { id: true, username: true, hue: true } },
      },
    });

    res.json(messages.reverse());
  } catch (err) {
    console.error('GET /api/chats/:id/messages error:', err);
    res.status(500).json({ error: 'Ошибка загрузки сообщений' });
  }
});

// Создать чат (1-on-1)
router.post('/', authenticate, async (req, res) => {
  try {
    const { participantId } = req.body;

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

    // Проверяем нет ли уже чата
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

    // Имя создателя для уведомления
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
            { userId: req.userId },
            { userId: participantId },
          ],
        },
      },
    });

    // Системное уведомление другому участнику
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

// Пометить чат как прочитанный
router.post('/:id/read', authenticate, async (req, res) => {
  try {
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
