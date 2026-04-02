const { Router } = require('express');
const prisma = require('../db');
const { authenticate, requireVerified } = require('../middleware/auth');
const { emitToUser, findUserSockets, emitToUserAll } = require('../utils/socketUtils');
const logger = require('../utils/logger');

const router = Router();

// Отправить заявку в друзья
router.post('/request', authenticate, requireVerified, async (req, res) => {
  try {
    const { userId: targetId } = req.body;
    if (!targetId) return res.status(400).json({ error: 'Укажите userId' });
    if (targetId === req.userId) return res.status(400).json({ error: 'Нельзя добавить себя' });

    // Лимит: макс 20 неотвеченных заявок
    const pendingCount = await prisma.friendRequest.count({
      where: { senderId: req.userId, status: 'pending' },
    });
    if (pendingCount >= 20) {
      return res.status(429).json({ error: 'Слишком много неотвеченных заявок' });
    }

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
      if (existing.status === 'declined') {
        // Разрешить повторную отправку — обновить существующую запись
        await prisma.friendRequest.update({
          where: { id: existing.id },
          data: { status: 'pending', senderId: req.userId, receiverId: targetId },
        });

        const sender = await prisma.user.findUnique({
          where: { id: req.userId },
          select: { id: true, username: true, hue: true, avatar: true },
        });

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

        emitToUser(targetId, 'notification:new', notification);

        return res.status(201).json({ id: existing.id, status: 'pending' });
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

    // Отправить через socket
    emitToUser(targetId, 'notification:new', notification);

    res.status(201).json({ id: request.id, status: request.status });
  } catch (err) {
    logger.error({ err }, 'POST /api/friends/request error');
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
    logger.error({ err }, 'GET /api/friends/requests/pending error');
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

    // Socket: уведомить отправителя + присоединить обоих к комнате
    for (const s of findUserSockets(request.senderId)) {
      s.emit('notification:new', notification);
      s.join(room.id);
    }
    for (const s of findUserSockets(req.userId)) {
      s.join(room.id);
    }

    // Оповестить обоих — перезагрузить список чатов
    const friendAcceptedData = { roomId: room.id };
    emitToUserAll(request.senderId, 'friend:accepted', friendAcceptedData);
    emitToUserAll(req.userId, 'friend:accepted', friendAcceptedData);

    res.json({ ok: true, roomId: room.id });
  } catch (err) {
    logger.error({ err }, 'POST /api/friends/requests/:id/accept error');
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
    logger.error({ err }, 'POST /api/friends/requests/:id/decline error');
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
    logger.error({ err }, 'GET /api/friends error');
    res.status(500).json({ error: 'Ошибка загрузки друзей' });
  }
});

// Удалить из друзей
router.delete('/:friendId', authenticate, async (req, res) => {
  try {
    const { friendId } = req.params;

    const request = await prisma.friendRequest.findFirst({
      where: {
        status: 'accepted',
        OR: [
          { senderId: req.userId, receiverId: friendId },
          { senderId: friendId, receiverId: req.userId },
        ],
      },
    });

    if (!request) {
      return res.status(404).json({ error: 'Дружба не найдена' });
    }

    await prisma.friendRequest.delete({ where: { id: request.id } });

    // [CRIT-4] Оповестить ТОЛЬКО двух пользователей (не broadcast всем)
    const otherUserId = request.senderId === req.userId ? request.receiverId : request.senderId;
    const removeData = { userId: req.userId, friendId: otherUserId };
    emitToUserAll(req.userId, 'friend:removed', removeData);
    emitToUserAll(otherUserId, 'friend:removed', removeData);

    res.json({ ok: true });
  } catch (err) {
    logger.error({ err }, 'DELETE /api/friends/:friendId error');
    res.status(500).json({ error: 'Ошибка удаления из друзей' });
  }
});

// Заблокировать пользователя
router.post('/:userId/block', authenticate, async (req, res) => {
  try {
    const { userId: targetId } = req.params;
    if (targetId === req.userId) {
      return res.status(400).json({ error: 'Нельзя заблокировать себя' });
    }

    const target = await prisma.user.findUnique({
      where: { id: targetId },
      select: { id: true },
    });
    if (!target) return res.status(404).json({ error: 'Пользователь не найден' });

    // Создаём запись блокировки (upsert чтобы не дублировать)
    await prisma.blockedUser.upsert({
      where: { userId_blockedId: { userId: req.userId, blockedId: targetId } },
      create: { userId: req.userId, blockedId: targetId },
      update: {},
    });

    // Удалить дружбу если есть
    const friendship = await prisma.friendRequest.findFirst({
      where: {
        status: 'accepted',
        OR: [
          { senderId: req.userId, receiverId: targetId },
          { senderId: targetId, receiverId: req.userId },
        ],
      },
    });
    if (friendship) {
      await prisma.friendRequest.delete({ where: { id: friendship.id } });
      emitToUserAll(req.userId, 'friend:removed', { userId: req.userId, friendId: targetId });
      emitToUserAll(targetId, 'friend:removed', { userId: req.userId, friendId: targetId });
    }

    // Отклонить pending заявки между пользователями
    await prisma.friendRequest.updateMany({
      where: {
        status: 'pending',
        OR: [
          { senderId: req.userId, receiverId: targetId },
          { senderId: targetId, receiverId: req.userId },
        ],
      },
      data: { status: 'declined' },
    });

    res.json({ ok: true });
  } catch (err) {
    logger.error({ err }, 'POST /api/friends/:userId/block error');
    res.status(500).json({ error: 'Ошибка блокировки' });
  }
});

// Разблокировать пользователя
router.delete('/:userId/block', authenticate, async (req, res) => {
  try {
    const { userId: targetId } = req.params;

    const record = await prisma.blockedUser.findUnique({
      where: { userId_blockedId: { userId: req.userId, blockedId: targetId } },
    });
    if (!record) {
      return res.status(404).json({ error: 'Пользователь не заблокирован' });
    }

    await prisma.blockedUser.delete({ where: { id: record.id } });

    res.json({ ok: true });
  } catch (err) {
    logger.error({ err }, 'DELETE /api/friends/:userId/block error');
    res.status(500).json({ error: 'Ошибка разблокировки' });
  }
});

module.exports = router;
