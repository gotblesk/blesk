const { Router } = require('express');
const prisma = require('../db');
const { authenticate } = require('../middleware/auth');

const router = Router();

// Отправить заявку в друзья
router.post('/request', authenticate, async (req, res) => {
  try {
    const { userId: targetId } = req.body;
    if (!targetId) return res.status(400).json({ error: 'Укажите userId' });
    if (targetId === req.userId) return res.status(400).json({ error: 'Нельзя добавить себя' });

    // Проверяем что пользователь существует
    const target = await prisma.user.findUnique({
      where: { id: targetId },
      select: { id: true, username: true },
    });
    if (!target) return res.status(404).json({ error: 'Пользователь не найден' });

    // Проверяем нет ли уже заявки
    const existing = await prisma.friendRequest.findFirst({
      where: {
        OR: [
          { senderId: req.userId, receiverId: targetId },
          { senderId: targetId, receiverId: req.userId },
        ],
      },
    });
    if (existing) {
      if (existing.status === 'accepted') {
        return res.status(400).json({ error: 'Вы уже друзья' });
      }
      if (existing.status === 'pending') {
        return res.status(400).json({ error: 'Заявка уже отправлена' });
      }
    }

    // Имя отправителя для уведомления
    const sender = await prisma.user.findUnique({
      where: { id: req.userId },
      select: { id: true, username: true, hue: true, avatar: true },
    });

    // Создаём заявку
    const request = await prisma.friendRequest.create({
      data: { senderId: req.userId, receiverId: targetId },
    });

    // Создаём уведомление
    const notification = await prisma.notification.create({
      data: {
        userId: targetId,
        type: 'friend_request',
        title: `${sender.username} хочет дружить`,
        body: 'Заявка в друзья',
        fromUserId: req.userId,
      },
      include: {
        fromUser: { select: { id: true, username: true, hue: true, avatar: true } },
      },
    });

    // Отправить через socket (io передаётся через app.locals)
    const io = req.app.locals.io;
    if (io) {
      // Найти сокет получателя
      for (const [, s] of io.sockets.sockets) {
        if (s.userId === targetId) {
          s.emit('notification:new', notification);
        }
      }
    }

    res.status(201).json({ id: request.id, status: request.status });
  } catch (err) {
    console.error('POST /api/friends/request error:', err);
    res.status(500).json({ error: 'Ошибка отправки заявки' });
  }
});

// Входящие заявки
router.get('/requests/pending', authenticate, async (req, res) => {
  try {
    const requests = await prisma.friendRequest.findMany({
      where: { receiverId: req.userId, status: 'pending' },
      orderBy: { createdAt: 'desc' },
      include: {
        sender: { select: { id: true, username: true, hue: true, avatar: true } },
      },
    });
    res.json(requests);
  } catch (err) {
    console.error('GET /api/friends/requests/pending error:', err);
    res.status(500).json({ error: 'Ошибка' });
  }
});

// Принять заявку
router.post('/requests/:id/accept', authenticate, async (req, res) => {
  try {
    const request = await prisma.friendRequest.findUnique({
      where: { id: req.params.id },
      include: {
        sender: { select: { id: true, username: true, hue: true, avatar: true } },
        receiver: { select: { id: true, username: true, hue: true, avatar: true } },
      },
    });

    if (!request || request.receiverId !== req.userId) {
      return res.status(404).json({ error: 'Заявка не найдена' });
    }
    if (request.status !== 'pending') {
      return res.status(400).json({ error: 'Заявка уже обработана' });
    }

    // Обновляем статус
    await prisma.friendRequest.update({
      where: { id: req.params.id },
      data: { status: 'accepted' },
    });

    // Создаём чат (если ещё нет)
    let room = await prisma.room.findFirst({
      where: {
        type: 'chat',
        AND: [
          { participants: { some: { userId: request.senderId } } },
          { participants: { some: { userId: request.receiverId } } },
        ],
      },
    });

    if (!room) {
      room = await prisma.room.create({
        data: {
          name: '',
          type: 'chat',
          ownerId: request.senderId,
          participants: {
            create: [
              { userId: request.senderId },
              { userId: request.receiverId },
            ],
          },
        },
      });
    }

    // Уведомление отправителю заявки
    const notification = await prisma.notification.create({
      data: {
        userId: request.senderId,
        type: 'friend_accepted',
        title: `${request.receiver.username} принял заявку`,
        body: 'Теперь вы друзья',
        fromUserId: req.userId,
        roomId: room.id,
      },
      include: {
        fromUser: { select: { id: true, username: true, hue: true, avatar: true } },
      },
    });

    // Socket: уведомить отправителя
    const io = req.app.locals.io;
    if (io) {
      for (const [, s] of io.sockets.sockets) {
        if (s.userId === request.senderId) {
          s.emit('notification:new', notification);
          // Присоединить к комнате нового чата
          s.join(room.id);
        }
      }
      // Присоединить текущего пользователя к комнате
      for (const [, s] of io.sockets.sockets) {
        if (s.userId === req.userId) {
          s.join(room.id);
        }
      }
    }

    res.json({ ok: true, roomId: room.id });
  } catch (err) {
    console.error('POST /api/friends/requests/:id/accept error:', err);
    res.status(500).json({ error: 'Ошибка принятия заявки' });
  }
});

// Отклонить заявку
router.post('/requests/:id/decline', authenticate, async (req, res) => {
  try {
    const request = await prisma.friendRequest.findUnique({
      where: { id: req.params.id },
    });
    if (!request || request.receiverId !== req.userId) {
      return res.status(404).json({ error: 'Заявка не найдена' });
    }
    if (request.status !== 'pending') {
      return res.status(400).json({ error: 'Заявка уже обработана' });
    }

    await prisma.friendRequest.update({
      where: { id: req.params.id },
      data: { status: 'declined' },
    });

    res.json({ ok: true });
  } catch (err) {
    console.error('POST /api/friends/requests/:id/decline error:', err);
    res.status(500).json({ error: 'Ошибка' });
  }
});

// Список друзей
router.get('/', authenticate, async (req, res) => {
  try {
    const requests = await prisma.friendRequest.findMany({
      where: {
        status: 'accepted',
        OR: [
          { senderId: req.userId },
          { receiverId: req.userId },
        ],
      },
      include: {
        sender: { select: { id: true, username: true, tag: true, hue: true, avatar: true, status: true } },
        receiver: { select: { id: true, username: true, tag: true, hue: true, avatar: true, status: true } },
      },
    });

    const friends = requests.map((r) =>
      r.senderId === req.userId ? r.receiver : r.sender
    );

    res.json(friends);
  } catch (err) {
    console.error('GET /api/friends error:', err);
    res.status(500).json({ error: 'Ошибка загрузки друзей' });
  }
});

module.exports = router;
