const { Router } = require('express');
const prisma = require('../db');
const { authenticate } = require('../middleware/auth');
const logger = require('../utils/logger');

const router = Router();

// Список уведомлений с пагинацией
router.get('/', authenticate, async (req, res) => {
  try {
    const page = Math.max(1, parseInt(req.query.page) || 1);
    const limit = Math.min(100, Math.max(1, parseInt(req.query.limit) || 50));
    const skip = (page - 1) * limit;

    const [notifications, total] = await Promise.all([
      prisma.notification.findMany({
        where: { userId: req.userId },
        orderBy: { createdAt: 'desc' },
        skip,
        take: limit,
        include: {
          fromUser: { select: { id: true, username: true, hue: true, avatar: true } },
        },
      }),
      prisma.notification.count({ where: { userId: req.userId } }),
    ]);

    res.json({ notifications, total, page });
  } catch (err) {
    logger.error({ err }, 'GET /api/notifications error');
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
    logger.error({ err }, 'GET /api/notifications/unread-count error');
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
    logger.error({ err }, 'POST /api/notifications/read-all error');
    res.status(500).json({ error: 'Ошибка' });
  }
});

// Очистить все уведомления пользователя
router.delete('/clear', authenticate, async (req, res) => {
  try {
    await prisma.notification.deleteMany({
      where: { userId: req.userId },
    });
    res.json({ ok: true });
  } catch (err) {
    logger.error({ err }, 'DELETE /api/notifications/clear error');
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
    logger.error({ err }, 'POST /api/notifications/:id/read error');
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
    logger.error({ err }, 'DELETE /api/notifications/:id error');
    res.status(500).json({ error: 'Ошибка' });
  }
});

module.exports = router;
