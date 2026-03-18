const { Router } = require('express');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const prisma = require('../db');
const { generateTokens, verifyRefreshToken } = require('../middleware/auth');
const { generateCode, sendVerificationCode } = require('../services/email');

const router = Router();
const JWT_SECRET = process.env.JWT_SECRET || 'blesk-dev-secret-change-in-production';

// Раздельные rate-limit Maps для email-кодов по эндпоинтам (email → timestamp)
// Чтобы resend-code, forgot-password и change-password/request не мешали друг другу
const resendCodeLimits = new Map();
const forgotPasswordLimits = new Map();
const changePasswordLimits = new Map();
const EMAIL_CODE_COOLDOWN = 60000; // 60 секунд

// Проверить rate limit для конкретного эндпоинта
function isEmailCodeRateLimited(limitsMap, email) {
  const lastTime = limitsMap.get(email);
  if (lastTime && Date.now() - lastTime < EMAIL_CODE_COOLDOWN) {
    const wait = Math.ceil((EMAIL_CODE_COOLDOWN - (Date.now() - lastTime)) / 1000);
    return wait;
  }
  return 0;
}

// Записать время отправки кода
function markEmailCodeSent(limitsMap, email) {
  limitsMap.set(email, Date.now());
}

// Чистка rate-limit Maps раз в 5 минут
setInterval(() => {
  const now = Date.now();
  for (const map of [resendCodeLimits, forgotPasswordLimits, changePasswordLimits]) {
    for (const [email, ts] of map) {
      if (now - ts > EMAIL_CODE_COOLDOWN) map.delete(email);
    }
  }
}, 300000);

// Генерация тега #0001–#9999
function generateTag() {
  const num = Math.floor(1 + Math.random() * 9999); // 1-9999
  return '#' + String(num).padStart(4, '0');
}

// Генерация уникального тега для пользователя
async function generateUniqueTag(username) {
  const maxAttempts = 20;
  for (let i = 0; i < maxAttempts; i++) {
    const tag = generateTag();
    const existing = await prisma.user.findFirst({
      where: { username, tag },
    });
    if (!existing) return tag;
  }
  // Фоллбэк: последовательный поиск свободного тега через БД
  const existingTags = await prisma.user.findMany({
    where: { username },
    select: { tag: true },
  });
  const usedTags = new Set(existingTags.map((u) => u.tag));
  for (let n = 1; n <= 9999; n++) {
    const candidate = '#' + String(n).padStart(4, '0');
    if (!usedTags.has(candidate)) return candidate;
  }
  // Все 9999 тегов заняты — невозможно создать пользователя с таким именем
  throw new Error(`Все теги для username "${username}" заняты`);
}

// Валидация email
function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

// Извлечь userId из Bearer token (только access, refresh не принимается)
function getUserIdFromToken(req) {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) return null;
  try {
    const payload = jwt.verify(header.slice(7), JWT_SECRET);
    // Refresh токен не должен использоваться как access
    if (payload.type === 'refresh') return null;
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
        tag: await generateUniqueTag(username),
        passwordHash,
        email,
        emailVerified: false,
        hue,
      },
    });

    // Rate limit на отправку email при регистрации (защита от спама)
    const regWait = isEmailCodeRateLimited(resendCodeLimits, email);
    if (regWait === 0) {
      const code = generateCode();
      await prisma.emailCode.create({
        data: {
          email,
          code,
          expiresAt: new Date(Date.now() + 10 * 60 * 1000), // 10 минут
        },
      });
      markEmailCodeSent(resendCodeLimits, email);
      await sendVerificationCode(email, code);
    }

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

    // Rate limit: раздельный для resend-code (не мешает forgot-password и change-password)
    const waitSec = isEmailCodeRateLimited(resendCodeLimits, user.email);
    if (waitSec > 0) {
      return res.status(429).json({ error: `Подождите ${waitSec} сек.` });
    }

    const code = generateCode();
    await prisma.emailCode.create({
      data: {
        email: user.email,
        code,
        expiresAt: new Date(Date.now() + 10 * 60 * 1000),
      },
    });

    markEmailCodeSent(resendCodeLimits, user.email);

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
      // Константное время ответа — bcrypt на фиктивном хеше, чтобы атакующий
      // не мог определить существование пользователя по времени ответа
      await bcrypt.compare(password, '$2b$12$invalidhashpaddingtomakeitsamelengthasbcrypt');
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
        customStatus: user.customStatus,
        bleskCoins: user.bleskCoins,
        email: user.email,
        emailVerified: user.emailVerified,
        publicKey: user.publicKey,
        createdAt: user.createdAt,
      },
    });
  } catch (err) {
    console.error('Ошибка получения профиля:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// ═══ Восстановление пароля ═══

// POST /api/auth/forgot-password — отправить код сброса на email
router.post('/forgot-password', async (req, res) => {
  try {
    const { email } = req.body;
    if (!email || !isValidEmail(email)) {
      return res.status(400).json({ error: 'Введите корректный email' });
    }

    const user = await prisma.user.findUnique({ where: { email } });
    // Всегда отвечаем успехом — чтобы не раскрывать наличие аккаунта
    if (!user) {
      return res.json({ success: true });
    }

    // Rate limit: раздельный для forgot-password (не мешает resend-code и change-password)
    const waitSec = isEmailCodeRateLimited(forgotPasswordLimits, email);
    if (waitSec > 0) {
      return res.status(429).json({ error: 'Подождите перед повторной отправкой' });
    }

    const code = generateCode();
    await prisma.emailCode.create({
      data: {
        email,
        code,
        expiresAt: new Date(Date.now() + 10 * 60 * 1000),
      },
    });

    markEmailCodeSent(forgotPasswordLimits, email);

    await sendVerificationCode(email, code);
    res.json({ success: true });
  } catch (err) {
    console.error('forgot-password error:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/reset-password — проверить код и сменить пароль
router.post('/reset-password', async (req, res) => {
  try {
    const { email, code, newPassword } = req.body;

    if (!email || !code || !newPassword) {
      return res.status(400).json({ error: 'Заполните все поля' });
    }
    if (newPassword.length < 8) {
      return res.status(400).json({ error: 'Пароль: минимум 8 символов' });
    }

    const user = await prisma.user.findUnique({ where: { email } });
    if (!user) {
      return res.status(400).json({ error: 'Неверный код' });
    }

    // Проверить код
    const emailCode = await prisma.emailCode.findFirst({
      where: {
        email,
        code: code.trim(),
        used: false,
        expiresAt: { gt: new Date() },
      },
      orderBy: { createdAt: 'desc' },
    });

    if (!emailCode) {
      return res.status(400).json({ error: 'Неверный или просроченный код' });
    }

    const passwordHash = await bcrypt.hash(newPassword, 12);

    await prisma.$transaction([
      prisma.user.update({
        where: { id: user.id },
        data: { passwordHash },
      }),
      prisma.emailCode.update({
        where: { id: emailCode.id },
        data: { used: true },
      }),
    ]);

    res.json({ success: true });
  } catch (err) {
    console.error('reset-password error:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// ═══ Смена пароля в профиле ═══

// POST /api/auth/change-password — запросить код для смены пароля
router.post('/change-password/request', async (req, res) => {
  try {
    const userId = getUserIdFromToken(req);
    if (!userId) return res.status(401).json({ error: 'Требуется авторизация' });

    const user = await prisma.user.findUnique({ where: { id: userId } });
    if (!user || !user.email) {
      return res.status(400).json({ error: 'Email не привязан' });
    }

    // Rate limit: раздельный для change-password (не мешает resend-code и forgot-password)
    const waitSec = isEmailCodeRateLimited(changePasswordLimits, user.email);
    if (waitSec > 0) {
      return res.status(429).json({ error: `Подождите ${waitSec} сек.` });
    }

    const code = generateCode();
    await prisma.emailCode.create({
      data: {
        email: user.email,
        code,
        expiresAt: new Date(Date.now() + 10 * 60 * 1000),
      },
    });

    markEmailCodeSent(changePasswordLimits, user.email);

    await sendVerificationCode(user.email, code);
    res.json({ success: true, email: user.email.replace(/(.{2})(.*)(@.*)/, '$1***$3') });
  } catch (err) {
    console.error('change-password/request error:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/change-password/confirm — подтвердить код и сменить пароль
router.post('/change-password/confirm', async (req, res) => {
  try {
    const userId = getUserIdFromToken(req);
    if (!userId) return res.status(401).json({ error: 'Требуется авторизация' });

    const { code, currentPassword, newPassword } = req.body;
    if (!code || !currentPassword || !newPassword) {
      return res.status(400).json({ error: 'Заполните все поля' });
    }
    if (newPassword.length < 8) {
      return res.status(400).json({ error: 'Новый пароль: минимум 8 символов' });
    }

    const user = await prisma.user.findUnique({ where: { id: userId } });
    if (!user) return res.status(404).json({ error: 'Пользователь не найден' });

    // Проверить текущий пароль
    const valid = await bcrypt.compare(currentPassword, user.passwordHash);
    if (!valid) {
      return res.status(400).json({ error: 'Неверный текущий пароль' });
    }

    // Проверить код
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

    const passwordHash = await bcrypt.hash(newPassword, 12);

    await prisma.$transaction([
      prisma.user.update({
        where: { id: userId },
        data: { passwordHash },
      }),
      prisma.emailCode.update({
        where: { id: emailCode.id },
        data: { used: true },
      }),
    ]);

    res.json({ success: true });
  } catch (err) {
    console.error('change-password/confirm error:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/keys — сохранить публичный ключ E2E шифрования
router.post('/keys', async (req, res) => {
  try {
    const userId = getUserIdFromToken(req);
    if (!userId) return res.status(401).json({ error: 'Требуется авторизация' });

    const { publicKey } = req.body;
    if (!publicKey || typeof publicKey !== 'string' || publicKey.length > 100) {
      return res.status(400).json({ error: 'Некорректный ключ' });
    }
    await prisma.user.update({
      where: { id: userId },
      data: { publicKey },
    });
    res.json({ ok: true });
  } catch (err) {
    console.error('keys error:', err);
    res.status(500).json({ error: 'Ошибка сохранения ключа' });
  }
});

module.exports = router;
