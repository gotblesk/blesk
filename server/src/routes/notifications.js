const { Router } = require('express');
const { PrismaClient } = require('@prisma/client');
const { authenticate } = require('../middleware/auth');

const prisma = new PrismaClient();
const router = Router();

// Список уведомлений (последние 50)
router.get('/', authenticate, async (req, res) => {
  try {
    const notifications = await prisma.notification.findMany({
      where: { userId: req.userId },
      orderBy: { createdAt: 'desc' },
      take: 50,
      include: {
        fromUser: { select: { id: true, username: true, hue: true, avatar: true } },
      },
    });
    res.json(notifications);
  } catch (err) {
    console.error('GET /api/notifications error:', err);
    res.status(500).json({ error: 'Ошибка загрузки уведомлений' });
  }
});

// Число непрочитанных
router.get('/unread-count', authenticate, async (req, res) => {
  try {
    const count = await prisma.notification.count({
      where: { userId: req.userId, isRead: false },
    });
    res.json({ count });
  } catch (err) {
    console.error('GET /api/notifications/unread-count error:', err);
    res.status(500).json({ error: 'Ошибка' });
  }
});

// Пометить одно как прочитанное
router.post('/:id/read', authenticate, async (req, res) => {
  try {
    const notification = await prisma.notification.findUnique({
      where: { id: req.params.id },
    });
    if (!notification || notification.userId !== req.userId) {
      return res.status(404).json({ error: 'Уведомление не найдено' });
    }
    await prisma.notification.update({
      where: { id: req.params.id },
      data: { isRead: true },
    });
    res.json({ ok: true });
  } catch (err) {
    console.error('POST /api/notifications/:id/read error:', err);
    res.status(500).json({ error: 'Ошибка' });
  }
});

// Прочитать все
router.post('/read-all', authenticate, async (req, res) => {
  try {
    await prisma.notification.updateMany({
      where: { userId: req.userId, isRead: false },
      data: { isRead: true },
    });
    res.json({ ok: true });
  } catch (err) {
    console.error('POST /api/notifications/read-all error:', err);
    res.status(500).json({ error: 'Ошибка' });
  }
});

// Удалить одно
router.delete('/:id', authenticate, async (req, res) => {
  try {
    const notification = await prisma.notification.findUnique({
      where: { id: req.params.id },
    });
    if (!notification || notification.userId !== req.userId) {
      return res.status(404).json({ error: 'Уведомление не найдено' });
    }
    await prisma.notification.delete({ where: { id: req.params.id } });
    res.json({ ok: true });
  } catch (err) {
    console.error('DELETE /api/notifications/:id error:', err);
    res.status(500).json({ error: 'Ошибка' });
  }
});

module.exports = router;
