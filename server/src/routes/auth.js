const { Router } = require('express');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const prisma = require('../db');
const { authenticate, generateTokens, verifyRefreshToken, hashRefreshToken } = require('../middleware/auth');
const crypto = require('crypto');
const { generateCode, sendVerificationCode } = require('../services/email');
const { authenticator } = require('otplib');
const QRCode = require('qrcode');

const router = Router();
const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) { console.error('FATAL: JWT_SECRET не задан'); process.exit(1); }

// httpOnly cookie helper — устанавливает access + refresh cookies
function setAuthCookies(res, tokens) {
  res.cookie('blesk_token', tokens.token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    maxAge: 15 * 60 * 1000, // 15 минут
    path: '/',
  });
  res.cookie('blesk_refresh', tokens.refreshToken, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    maxAge: 7 * 24 * 60 * 60 * 1000, // 7 дней
    path: '/api/auth',
  });
}

// Очистить auth cookies (при logout / смене пароля)
function clearAuthCookies(res) {
  res.clearCookie('blesk_token', { path: '/' });
  res.clearCookie('blesk_refresh', { path: '/api/auth' });
}

// Раздельные rate-limit Maps для email-кодов по эндпоинтам (email → timestamp)
// Чтобы resend-code, forgot-password и change-password/request не мешали друг другу
const resendCodeLimits = new Map();
const forgotPasswordLimits = new Map();
const changePasswordLimits = new Map();
const EMAIL_CODE_COOLDOWN = 60000; // 60 секунд

// Защита от перебора кодов: email → { attempts, lockedUntil }
const codeAttempts = new Map();
const MAX_CODE_ATTEMPTS = 5;
const CODE_LOCKOUT_MS = 10 * 60 * 1000;

// [HIGH-1] Защита от brute-force логина: username → { attempts, lockedUntil }
const loginAttempts = new Map();
const MAX_LOGIN_ATTEMPTS = 5;
const LOGIN_LOCKOUT_MS = 15 * 60 * 1000; // 15 минут

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

// Проверить блокировку по попыткам ввода кода
function isCodeLocked(email) {
  const entry = codeAttempts.get(email);
  if (!entry) return false;
  if (entry.lockedUntil && Date.now() < entry.lockedUntil) return true;
  if (entry.lockedUntil && Date.now() >= entry.lockedUntil) {
    codeAttempts.delete(email);
    return false;
  }
  return false;
}

// Записать неудачную попытку ввода кода
function recordFailedAttempt(email) {
  let entry = codeAttempts.get(email);
  if (!entry) { entry = { attempts: 0, lockedUntil: null }; codeAttempts.set(email, entry); }
  entry.attempts++;
  if (entry.attempts >= MAX_CODE_ATTEMPTS) {
    entry.lockedUntil = Date.now() + CODE_LOCKOUT_MS;
  }
}

// Сбросить счётчик при успешном вводе кода
function clearAttempts(email) { codeAttempts.delete(email); }

// Чистка rate-limit Maps раз в 5 минут
setInterval(() => {
  const now = Date.now();
  for (const map of [resendCodeLimits, forgotPasswordLimits, changePasswordLimits]) {
    for (const [email, ts] of map) {
      if (now - ts > EMAIL_CODE_COOLDOWN) map.delete(email);
    }
  }
  // Чистка устаревших блокировок
  for (const [email, entry] of codeAttempts) {
    if (entry.lockedUntil && now >= entry.lockedUntil) codeAttempts.delete(email);
  }
  // Чистка login lockout
  for (const [user, entry] of loginAttempts) {
    if (entry.lockedUntil && now >= entry.lockedUntil) loginAttempts.delete(user);
  }
}, 300000);

// [IMP-6] Очистка просроченных email кодов — раз в час
setInterval(async () => {
  try {
    await prisma.emailCode.deleteMany({ where: { expiresAt: { lt: new Date() } } });
  } catch (err) { console.error('Failed to cleanup expired email codes:', err.message); }
}, 60 * 60 * 1000);

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

// Извлечь userId из cookie или Bearer token (только access, refresh не принимается)
function getUserIdFromToken(req) {
  const cookieToken = req.cookies?.blesk_token;
  const header = req.headers.authorization;
  const token = cookieToken || (header?.startsWith('Bearer ') ? header.slice(7) : null);
  if (!token) return null;
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    if (payload.type === 'refresh') return null;
    return payload.userId;
  } catch {
    return null;
  }
}

// GET /api/auth/csrf — получить CSRF-токен
router.get('/csrf', authenticate, (req, res) => {
  const { generateCsrfToken } = require('../middleware/csrf');
  const token = generateCsrfToken(req.userId);
  res.json({ csrfToken: token });
});

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
    // [MED-2] Проверка сложности: минимум 2 класса символов
    const hasLower = /[a-zа-яё]/.test(password);
    const hasUpper = /[A-ZА-ЯЁ]/.test(password);
    const hasDigit = /[0-9]/.test(password);
    const hasSpecial = /[^a-zA-Zа-яА-ЯёЁ0-9]/.test(password);
    const classes = [hasLower, hasUpper, hasDigit, hasSpecial].filter(Boolean).length;
    if (classes < 2) {
      return res.status(400).json({ error: 'Пароль должен содержать минимум 2 типа символов (буквы, цифры, спецсимволы)' });
    }
    if (!/^[a-zA-Z0-9_]+$/.test(username)) {
      return res.status(400).json({ error: 'Только латиница, цифры и _' });
    }

    // [HIGH-4] Проверка уникальности — единое сообщение (без enumeration)
    const [existingUser, existingEmail] = await Promise.all([
      prisma.user.findUnique({ where: { username } }),
      prisma.user.findUnique({ where: { email } }),
    ]);
    if (existingUser || existingEmail) {
      return res.status(409).json({ error: 'Имя пользователя или email уже заняты' });
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

    // [CRIT-2] Сохранить refresh token в БД
    try {
      await prisma.refreshToken.create({
        data: {
          userId: user.id,
          tokenHash: hashRefreshToken(tokens.refreshToken),
          expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
        },
      });
    } catch (err) { console.error('Failed to save refresh token on register:', err.message); }

    setAuthCookies(res, tokens);

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
    });
  } catch (err) {
    console.error('Ошибка регистрации:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/verify-email
router.post('/verify-email', authenticate, async (req, res) => {
  try {
    const userId = req.userId;

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

    // Защита от перебора кодов
    if (isCodeLocked(user.email)) {
      return res.status(429).json({ error: 'Слишком много попыток. Подождите 10 минут.' });
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
      recordFailedAttempt(user.email);
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

    clearAttempts(user.email);
    res.json({ success: true });
  } catch (err) {
    console.error('Ошибка верификации email:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/resend-code
router.post('/resend-code', authenticate, async (req, res) => {
  try {
    const userId = req.userId;

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

    // [HIGH-1] Проверить lockout по имени
    const lockEntry = loginAttempts.get(username);
    if (lockEntry && lockEntry.lockedUntil && Date.now() < lockEntry.lockedUntil) {
      const waitMin = Math.ceil((lockEntry.lockedUntil - Date.now()) / 60000);
      return res.status(429).json({ error: `Слишком много попыток. Подождите ${waitMin} мин.` });
    }

    const user = await prisma.user.findUnique({ where: { username } });
    if (!user) {
      await bcrypt.compare(password, '$2b$12$invalidhashpaddingtomakeitsamelengthasbcrypt');
      return res.status(401).json({ error: 'Неверное имя или пароль' });
    }

    // [HIGH-7] Бан-проверка ПЕРЕД проверкой пароля
    if (user.banned) {
      return res.status(403).json({ error: 'Аккаунт заблокирован', bannedReason: user.bannedReason });
    }

    const valid = await bcrypt.compare(password, user.passwordHash);
    if (!valid) {
      // [HIGH-1] Записать неудачную попытку
      let entry = loginAttempts.get(username);
      if (!entry) { entry = { attempts: 0, lockedUntil: null }; loginAttempts.set(username, entry); }
      entry.attempts++;
      if (entry.attempts >= MAX_LOGIN_ATTEMPTS) {
        entry.lockedUntil = Date.now() + LOGIN_LOCKOUT_MS;
      }
      return res.status(401).json({ error: 'Неверное имя или пароль' });
    }

    // Успешный вход — сбросить счётчик
    loginAttempts.delete(username);

    // Проверка 2FA
    if (user.twoFactorEnabled) {
      const tempToken = jwt.sign({ userId: user.id, type: '2fa_pending' }, JWT_SECRET, { expiresIn: '5m' });
      return res.json({ requires2FA: true, tempToken });
    }

    // [CRIT-2] Генерировать токены и сохранить refresh в БД
    const tokens = generateTokens(user.id);
    try {
      await prisma.refreshToken.create({
        data: {
          userId: user.id,
          tokenHash: hashRefreshToken(tokens.refreshToken),
          expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
        },
      });
    } catch (err) { console.error('Failed to save refresh token on login:', err.message); }

    setAuthCookies(res, tokens);

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
    } catch (err) { console.error('Failed to create login notification:', err.message); }

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
    });
  } catch (err) {
    console.error('Ошибка авторизации:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// [CRIT-2] POST /api/auth/refresh — с ротацией и ревокацией
router.post('/refresh', async (req, res) => {
  try {
    // Принимаем из body ИЛИ из httpOnly cookie
    const refreshToken = req.body.refreshToken || req.cookies?.blesk_refresh;
    if (!refreshToken) {
      return res.status(400).json({ error: 'Refresh token обязателен' });
    }

    const payload = verifyRefreshToken(refreshToken);
    if (!payload) {
      return res.status(401).json({ error: 'Недействительный refresh token' });
    }

    // Проверить что токен есть в БД (не отозван)
    const tokenHash = hashRefreshToken(refreshToken);
    const storedToken = await prisma.refreshToken.findUnique({ where: { tokenHash } });
    if (!storedToken) {
      // Токен не найден — возможно уже использован (token reuse attack)
      // Инвалидировать ВСЕ токены пользователя для безопасности
      await prisma.refreshToken.deleteMany({ where: { userId: payload.userId } });
      return res.status(401).json({ error: 'Refresh token отозван' });
    }

    // Удалить использованный токен (ротация)
    await prisma.refreshToken.delete({ where: { id: storedToken.id } });

    const user = await prisma.user.findUnique({ where: { id: payload.userId } });
    if (!user) {
      return res.status(401).json({ error: 'Пользователь не найден' });
    }

    if (user.banned) {
      return res.status(403).json({ error: 'Аккаунт заблокирован', bannedReason: user.bannedReason });
    }

    // Новые токены (ротация — новый refresh каждый раз)
    const tokens = generateTokens(user.id);

    // Сохранить новый refresh в БД
    await prisma.refreshToken.create({
      data: {
        userId: user.id,
        tokenHash: hashRefreshToken(tokens.refreshToken),
        expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
      },
    });

    setAuthCookies(res, tokens);

    res.json({});
  } catch (err) {
    console.error('Ошибка обновления токена:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/logout — инвалидация refresh token
router.post('/logout', async (req, res) => {
  try {
    // Принимаем из body ИЛИ из httpOnly cookie
    const refreshToken = req.body.refreshToken || req.cookies?.blesk_refresh;
    if (refreshToken) {
      const tokenHash = hashRefreshToken(refreshToken);
      await prisma.refreshToken.deleteMany({ where: { tokenHash } });
    }
    clearAuthCookies(res);
    res.json({ ok: true });
  } catch {
    clearAuthCookies(res);
    res.json({ ok: true }); // Logout всегда "успешен"
  }
});

// GET /api/auth/me
router.get('/me', authenticate, async (req, res) => {
  try {
    const userId = req.userId;

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
        role: user.role,
        banned: user.banned,
        bannedReason: user.bannedReason,
        twoFactorEnabled: user.twoFactorEnabled,
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

    // Защита от перебора кодов
    if (isCodeLocked(email)) {
      return res.status(429).json({ error: 'Слишком много попыток. Подождите 10 минут.' });
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
      recordFailedAttempt(email);
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
      // [HIGH-2] Инвалидировать ВСЕ refresh tokens при смене пароля
      prisma.refreshToken.deleteMany({ where: { userId: user.id } }),
    ]);

    clearAttempts(email);
    clearAuthCookies(res);
    res.json({ success: true });
  } catch (err) {
    console.error('reset-password error:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// ═══ Смена пароля в профиле ═══

// POST /api/auth/change-password — запросить код для смены пароля
router.post('/change-password/request', authenticate, async (req, res) => {
  try {
    const userId = req.userId;

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
    res.json({ success: true, message: 'Код отправлен на вашу почту' });
  } catch (err) {
    console.error('change-password/request error:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/change-password/confirm — подтвердить код и сменить пароль
router.post('/change-password/confirm', authenticate, async (req, res) => {
  try {
    const userId = req.userId;

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

    // Защита от перебора кодов
    if (isCodeLocked(user.email)) {
      return res.status(429).json({ error: 'Слишком много попыток. Подождите 10 минут.' });
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
      recordFailedAttempt(user.email);
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
      // [HIGH-2] Инвалидировать ВСЕ сессии при смене пароля
      prisma.refreshToken.deleteMany({ where: { userId } }),
    ]);

    clearAttempts(user.email);
    clearAuthCookies(res);
    res.json({ success: true });
  } catch (err) {
    console.error('change-password/confirm error:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// ═══ Двухфакторная аутентификация (TOTP) ═══

// POST /api/auth/2fa/setup — начать настройку 2FA
router.post('/2fa/setup', authenticate, async (req, res) => {
  try {
    const user = await prisma.user.findUnique({ where: { id: req.userId } });
    if (!user) return res.status(404).json({ error: 'Пользователь не найден' });
    if (user.twoFactorEnabled) return res.status(400).json({ error: '2FA уже включена' });

    const secret = authenticator.generateSecret();
    const otpauthUrl = authenticator.keyuri(user.username, 'blesk', secret);
    const qrDataUrl = await QRCode.toDataURL(otpauthUrl);

    // Сохраняем secret но НЕ активируем (ждём верификацию)
    await prisma.user.update({ where: { id: req.userId }, data: { twoFactorSecret: secret } });

    res.json({ qr: qrDataUrl, secret, manual: secret });
  } catch (err) {
    console.error('2fa/setup error:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/2fa/verify — подтвердить код и активировать 2FA
router.post('/2fa/verify', authenticate, async (req, res) => {
  try {
    const { code } = req.body;
    if (!code) return res.status(400).json({ error: 'Код обязателен' });

    const user = await prisma.user.findUnique({ where: { id: req.userId } });
    if (!user?.twoFactorSecret) return res.status(400).json({ error: 'Сначала настройте 2FA' });
    if (user.twoFactorEnabled) return res.status(400).json({ error: '2FA уже активна' });

    const valid = authenticator.check(code, user.twoFactorSecret);
    if (!valid) return res.status(400).json({ error: 'Неверный код' });

    await prisma.user.update({ where: { id: req.userId }, data: { twoFactorEnabled: true } });
    res.json({ ok: true });
  } catch (err) {
    console.error('2fa/verify error:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/2fa/disable — отключить 2FA
router.post('/2fa/disable', authenticate, async (req, res) => {
  try {
    const { code } = req.body;
    if (!code) return res.status(400).json({ error: 'Код обязателен для отключения' });

    const user = await prisma.user.findUnique({ where: { id: req.userId } });
    if (!user?.twoFactorEnabled) return res.status(400).json({ error: '2FA не включена' });

    const valid = authenticator.check(code, user.twoFactorSecret);
    if (!valid) return res.status(400).json({ error: 'Неверный код' });

    await prisma.user.update({ where: { id: req.userId }, data: { twoFactorEnabled: false, twoFactorSecret: null } });
    res.json({ ok: true });
  } catch (err) {
    console.error('2fa/disable error:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/2fa/login — завершить вход с 2FA кодом
router.post('/2fa/login', async (req, res) => {
  try {
    const { tempToken, code } = req.body;
    if (!tempToken || !code) return res.status(400).json({ error: 'Токен и код обязательны' });

    let payload;
    try {
      payload = jwt.verify(tempToken, JWT_SECRET);
    } catch {
      return res.status(400).json({ error: 'Токен истёк или недействителен' });
    }
    if (payload.type !== '2fa_pending') return res.status(400).json({ error: 'Неверный токен' });

    const user = await prisma.user.findUnique({ where: { id: payload.userId } });
    if (!user || !user.twoFactorEnabled) return res.status(400).json({ error: 'Ошибка' });

    const valid = authenticator.check(code, user.twoFactorSecret);
    if (!valid) return res.status(400).json({ error: 'Неверный код' });

    const tokens = generateTokens(user.id);
    const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
    await prisma.refreshToken.create({
      data: { userId: user.id, tokenHash: hashRefreshToken(tokens.refreshToken), expiresAt },
    }).catch(err => console.error('Failed to save refresh token:', err.message));

    setAuthCookies(res, tokens);

    // Системное уведомление о входе
    try {
      await prisma.notification.create({
        data: { userId: user.id, type: 'system', title: 'Новый вход', body: 'Вход в аккаунт выполнен (2FA)' },
      });
    } catch (err) { console.error('Failed to create login notification:', err.message); }

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
    });
  } catch (err) {
    console.error('2fa/login error:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// POST /api/auth/keys — сохранить публичный ключ E2E шифрования
router.post('/keys', authenticate, async (req, res) => {
  try {
    const userId = req.userId;

    const { publicKey } = req.body;
    // X25519 public key = 32 bytes → base64 = 44 chars
    if (!publicKey || typeof publicKey !== 'string' || publicKey.length !== 44) {
      return res.status(400).json({ error: 'Некорректный ключ' });
    }
    // Проверить что это валидный base64
    try {
      const decoded = Buffer.from(publicKey, 'base64');
      if (decoded.length !== 32) {
        return res.status(400).json({ error: 'Ключ должен быть ровно 32 байта' });
      }
    } catch {
      return res.status(400).json({ error: 'Некорректный формат ключа' });
    }
    await prisma.user.update({
      where: { id: userId },
      data: { publicKey },
    });

    // Уведомить всех участников общих чатов о смене ключа
    try {
      const io = req.app.locals.io;
      if (io) {
        // Найти все комнаты где пользователь участвует
        const rooms = await prisma.roomParticipant.findMany({
          where: { userId },
          select: { roomId: true },
        });
        for (const { roomId } of rooms) {
          io.to(roomId).emit('user:keyChanged', { userId });
        }
      }
    } catch { /* Не критично */ }

    res.json({ ok: true });
  } catch (err) {
    console.error('keys error:', err);
    res.status(500).json({ error: 'Ошибка сохранения ключа' });
  }
});

module.exports = router;
