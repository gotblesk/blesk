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

module.exports = router;
