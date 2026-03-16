const { Router } = require('express');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const { PrismaClient } = require('@prisma/client');
const { generateTokens, verifyRefreshToken } = require('../middleware/auth');
const { generateCode, sendVerificationCode } = require('../services/email');

const router = Router();
const prisma = new PrismaClient();
const JWT_SECRET = process.env.JWT_SECRET || 'blesk-dev-secret-change-in-production';

// Генерация тега #0001–#9999
function generateTag() {
  return '#' + String(Math.floor(1000 + Math.random() * 9000));
}

// Валидация email
function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

// Извлечь userId из Bearer token
function getUserIdFromToken(req) {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) return null;
  try {
    const payload = jwt.verify(header.slice(7), JWT_SECRET);
    return payload.userId;
  } catch {
    return null;
  }
}

// POST /api/auth/register
router.post('/register', async (req, res) => {
  try {
    const { username, password, email } = req.body;

    if (!username || !password) {
      return res.status(400).json({ error: 'Имя пользователя и пароль обязательны' });
    }
    if (!email) {
      return res.status(400).json({ error: 'Email обязателен' });
    }
    if (!isValidEmail(email)) {
      return res.status(400).json({ error: 'Некорректный email' });
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

    // Проверка уникальности username и email
    const existingUser = await prisma.user.findUnique({ where: { username } });
    if (existingUser) {
      return res.status(409).json({ error: 'Это имя уже занято' });
    }

    const existingEmail = await prisma.user.findUnique({ where: { email } });
    if (existingEmail) {
      return res.status(409).json({ error: 'Этот email уже используется' });
    }

    const passwordHash = await bcrypt.hash(password, 12);
    const hue = Math.floor(Math.random() * 360);

    const user = await prisma.user.create({
      data: {
        username,
        tag: generateTag(),
        passwordHash,
        email,
        emailVerified: false,
        hue,
      },
    });

    // Создать и отправить код верификации
    const code = generateCode();
    await prisma.emailCode.create({
      data: {
        email,
        code,
        expiresAt: new Date(Date.now() + 10 * 60 * 1000), // 10 минут
      },
    });
    await sendVerificationCode(email, code);

    const tokens = generateTokens(user.id);

    res.status(201).json({
      user: {
        id: user.id,
        username: user.username,
        tag: user.tag,
        avatar: user.avatar,
        hue: user.hue,
        status: user.status,
        email: user.email,
        emailVerified: user.emailVerified,
      },
      ...tokens,
    });
  } catch (err) {
    console.error('Ошибка регистрации:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/verify-email
router.post('/verify-email', async (req, res) => {
  try {
    const userId = getUserIdFromToken(req);
    if (!userId) {
      return res.status(401).json({ error: 'Требуется авторизация' });
    }

    const { code } = req.body;
    if (!code) {
      return res.status(400).json({ error: 'Введите код' });
    }

    const user = await prisma.user.findUnique({ where: { id: userId } });
    if (!user || !user.email) {
      return res.status(400).json({ error: 'Email не найден' });
    }

    if (user.emailVerified) {
      return res.json({ success: true, message: 'Email уже подтверждён' });
    }

    // Ищем актуальный код
    const emailCode = await prisma.emailCode.findFirst({
      where: {
        email: user.email,
        code: code.trim(),
        used: false,
        expiresAt: { gt: new Date() },
      },
      orderBy: { createdAt: 'desc' },
    });

    if (!emailCode) {
      return res.status(400).json({ error: 'Неверный или просроченный код' });
    }

    // Подтвердить email и пометить код
    await prisma.$transaction([
      prisma.user.update({
        where: { id: userId },
        data: { emailVerified: true },
      }),
      prisma.emailCode.update({
        where: { id: emailCode.id },
        data: { used: true },
      }),
    ]);

    res.json({ success: true });
  } catch (err) {
    console.error('Ошибка верификации email:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/resend-code
router.post('/resend-code', async (req, res) => {
  try {
    const userId = getUserIdFromToken(req);
    if (!userId) {
      return res.status(401).json({ error: 'Требуется авторизация' });
    }

    const user = await prisma.user.findUnique({ where: { id: userId } });
    if (!user || !user.email) {
      return res.status(400).json({ error: 'Email не найден' });
    }

    if (user.emailVerified) {
      return res.json({ success: true, message: 'Email уже подтверждён' });
    }

    // Rate limit: последний код отправлен менее 60 сек назад?
    const lastCode = await prisma.emailCode.findFirst({
      where: { email: user.email },
      orderBy: { createdAt: 'desc' },
    });

    if (lastCode && Date.now() - lastCode.createdAt.getTime() < 60000) {
      const wait = Math.ceil((60000 - (Date.now() - lastCode.createdAt.getTime())) / 1000);
      return res.status(429).json({ error: `Подождите ${wait} сек.` });
    }

    const code = generateCode();
    await prisma.emailCode.create({
      data: {
        email: user.email,
        code,
        expiresAt: new Date(Date.now() + 10 * 60 * 1000),
      },
    });

    const sent = await sendVerificationCode(user.email, code);
    if (!sent) {
      return res.status(500).json({ error: 'Не удалось отправить письмо' });
    }

    res.json({ success: true });
  } catch (err) {
    console.error('Ошибка повторной отправки:', err);
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
        email: user.email,
        emailVerified: user.emailVerified,
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

// GET /api/auth/me
router.get('/me', async (req, res) => {
  try {
    const userId = getUserIdFromToken(req);
    if (!userId) {
      return res.status(401).json({ error: 'Требуется авторизация' });
    }

    const user = await prisma.user.findUnique({ where: { id: userId } });
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
        email: user.email,
        emailVerified: user.emailVerified,
        createdAt: user.createdAt,
      },
    });
  } catch (err) {
    console.error('Ошибка получения профиля:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

module.exports = router;
