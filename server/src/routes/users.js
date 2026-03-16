const { Router } = require('express');
const { PrismaClient } = require('@prisma/client');
const { authenticate } = require('../middleware/auth');

const prisma = new PrismaClient();
const router = Router();

// Поиск пользователей по username
router.get('/search', authenticate, async (req, res) => {
  try {
    const { q } = req.query;
    if (!q || q.length < 2) {
      return res.json([]);
    }

    const users = await prisma.user.findMany({
      where: {
        username: { contains: q },
        id: { not: req.userId },
      },
      select: { id: true, username: true, tag: true, hue: true, avatar: true, status: true },
      take: 10,
    });

    res.json(users);
  } catch {
    res.status(500).json({ error: 'Ошибка поиска' });
  }
});

// Обновление профиля
router.put('/me', authenticate, async (req, res) => {
  try {
    const { bio, status, customStatus, hue } = req.body;
    const data = {};

    // Валидация полей
    if (bio !== undefined) {
      if (typeof bio !== 'string' || bio.length > 200) {
        return res.status(400).json({ error: 'Bio должно быть до 200 символов' });
      }
      data.bio = bio;
    }

    if (status !== undefined) {
      const allowed = ['online', 'dnd', 'invisible'];
      if (!allowed.includes(status)) {
        return res.status(400).json({ error: 'Недопустимый статус' });
      }
      data.status = status;
    }

    if (customStatus !== undefined) {
      if (typeof customStatus !== 'string' || customStatus.length > 50) {
        return res.status(400).json({ error: 'Кастомный статус до 50 символов' });
      }
      data.customStatus = customStatus;
    }

    if (hue !== undefined) {
      const h = Number(hue);
      if (isNaN(h) || h < 0 || h > 360) {
        return res.status(400).json({ error: 'Hue должен быть от 0 до 360' });
      }
      data.hue = h;
    }

    if (Object.keys(data).length === 0) {
      return res.status(400).json({ error: 'Нет данных для обновления' });
    }

    const user = await prisma.user.update({
      where: { id: req.userId },
      data,
      select: {
        id: true,
        username: true,
        tag: true,
        hue: true,
        avatar: true,
        bio: true,
        status: true,
        customStatus: true,
        bleskCoins: true,
      },
    });

    // Оповещение через socket если статус изменился
    if (data.status) {
      const io = req.app.locals.io;
      if (io) {
        io.emit('user:statusChange', {
          userId: req.userId,
          status: data.status,
          customStatus: data.customStatus || user.customStatus,
        });
      }
    }

    res.json(user);
  } catch {
    res.status(500).json({ error: 'Ошибка обновления профиля' });
  }
});

module.exports = router;
