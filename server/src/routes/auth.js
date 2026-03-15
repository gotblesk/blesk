const { Router } = require('express');
const bcrypt = require('bcrypt');
const { PrismaClient } = require('@prisma/client');
const { generateTokens, verifyRefreshToken } = require('../middleware/auth');

const router = Router();
const prisma = new PrismaClient();

// Генерация тега #0001–#9999
function generateTag() {
  return '#' + String(Math.floor(1000 + Math.random() * 9000));
}

// POST /api/auth/register
router.post('/register', async (req, res) => {
  try {
    const { username, password } = req.body;

    if (!username || !password) {
      return res.status(400).json({ error: 'Имя пользователя и пароль обязательны' });
    }
    if (username.length < 3 || username.length > 24) {
      return res.status(400).json({ error: 'Имя пользователя: 3–24 символа' });
    }
    if (password.length < 8) {
      return res.status(400).json({ error: 'Пароль: минимум 8 символов' });
    }
    if (!/^[a-zA-Z0-9_]+$/.test(username)) {
      return res.status(400).json({ error: 'Только латиница, цифры и _' });
    }

    const existing = await prisma.user.findUnique({ where: { username } });
    if (existing) {
      return res.status(409).json({ error: 'Это имя уже занято' });
    }

    const passwordHash = await bcrypt.hash(password, 12);
    const hue = Math.floor(Math.random() * 360);

    const user = await prisma.user.create({
      data: {
        username,
        tag: generateTag(),
        passwordHash,
        hue,
      },
    });

    const tokens = generateTokens(user.id);

    res.status(201).json({
      user: {
        id: user.id,
        username: user.username,
        tag: user.tag,
        avatar: user.avatar,
        hue: user.hue,
        status: user.status,
      },
      ...tokens,
    });
  } catch (err) {
    console.error('Ошибка регистрации:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/login
router.post('/login', async (req, res) => {
  try {
    const { username, password } = req.body;

    if (!username || !password) {
      return res.status(400).json({ error: 'Имя пользователя и пароль обязательны' });
    }

    const user = await prisma.user.findUnique({ where: { username } });
    if (!user) {
      return res.status(401).json({ error: 'Неверное имя или пароль' });
    }

    const valid = await bcrypt.compare(password, user.passwordHash);
    if (!valid) {
      return res.status(401).json({ error: 'Неверное имя или пароль' });
    }

    const tokens = generateTokens(user.id);

    // Системное уведомление о входе
    try {
      await prisma.notification.create({
        data: {
          userId: user.id,
          type: 'system',
          title: 'Новый вход',
          body: 'Вход в аккаунт выполнен',
        },
      });
    } catch {}

    res.json({
      user: {
        id: user.id,
        username: user.username,
        tag: user.tag,
        avatar: user.avatar,
        hue: user.hue,
        status: user.status,
      },
      ...tokens,
    });
  } catch (err) {
    console.error('Ошибка авторизации:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/refresh
router.post('/refresh', async (req, res) => {
  try {
    const { refreshToken } = req.body;
    if (!refreshToken) {
      return res.status(400).json({ error: 'Refresh token обязателен' });
    }

    const payload = verifyRefreshToken(refreshToken);
    if (!payload) {
      return res.status(401).json({ error: 'Недействительный refresh token' });
    }

    const user = await prisma.user.findUnique({ where: { id: payload.userId } });
    if (!user) {
      return res.status(401).json({ error: 'Пользователь не найден' });
    }

    const tokens = generateTokens(user.id);
    res.json(tokens);
  } catch (err) {
    console.error('Ошибка обновления токена:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// GET /api/auth/me — текущий пользователь
router.get('/me', async (req, res) => {
  try {
    const header = req.headers.authorization;
    if (!header || !header.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Требуется авторизация' });
    }

    const jwt = require('jsonwebtoken');
    const JWT_SECRET = process.env.JWT_SECRET || 'blesk-dev-secret-change-in-production';
    const token = header.slice(7);

    let payload;
    try {
      payload = jwt.verify(token, JWT_SECRET);
    } catch {
      return res.status(401).json({ error: 'Недействительный токен' });
    }

    const user = await prisma.user.findUnique({ where: { id: payload.userId } });
    if (!user) {
      return res.status(404).json({ error: 'Пользователь не найден' });
    }

    res.json({
      user: {
        id: user.id,
        username: user.username,
        tag: user.tag,
        avatar: user.avatar,
        bio: user.bio,
        hue: user.hue,
        status: user.status,
        bleskCoins: user.bleskCoins,
        createdAt: user.createdAt,
      },
    });
  } catch (err) {
    console.error('Ошибка получения профиля:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

module.exports = router;
