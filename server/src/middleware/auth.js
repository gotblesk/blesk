const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const prisma = require('../db');

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET || JWT_SECRET.length < 32) {
  console.error('FATAL: JWT_SECRET не задан или слишком короткий (мин. 32 символа)');
  process.exit(1);
}

// Отдельный секрет для refresh токенов (обязателен)
const JWT_REFRESH_SECRET = process.env.JWT_REFRESH_SECRET;
if (!JWT_REFRESH_SECRET || JWT_REFRESH_SECRET.length < 32) {
  console.error('FATAL: JWT_REFRESH_SECRET не задан или < 32 символов');
  process.exit(1);
}

// Кеш банов: userId → { banned, ts }
const banCache = new Map();
const BAN_CACHE_TTL = 60000;

// Проверка JWT токена (только access, refresh не принимается)
async function authenticate(req, res, next) {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Требуется авторизация' });
  }

  const token = header.slice(7);
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    if (payload.type === 'refresh') {
      return res.status(401).json({ error: 'Недействительный токен' });
    }
    req.userId = payload.userId;

    // Проверка бана с кешем
    const cached = banCache.get(payload.userId);
    if (!cached || Date.now() - cached.ts > BAN_CACHE_TTL) {
      const u = await prisma.user.findUnique({ where: { id: payload.userId }, select: { banned: true } });
      banCache.set(payload.userId, { banned: u?.banned || false, ts: Date.now() });
      if (u?.banned) return res.status(403).json({ error: 'Аккаунт заблокирован' });
    } else if (cached.banned) {
      return res.status(403).json({ error: 'Аккаунт заблокирован' });
    }

    next();
  } catch {
    return res.status(401).json({ error: 'Недействительный токен' });
  }
}

// [CRIT-1] Генерация токенов с раздельными секретами
function generateTokens(userId) {
  const token = jwt.sign({ userId }, JWT_SECRET, { expiresIn: '15m' });
  const refreshToken = jwt.sign({ userId, type: 'refresh' }, JWT_REFRESH_SECRET, { expiresIn: '7d' });
  return { token, refreshToken };
}

// [CRIT-1] Проверка refresh токена отдельным секретом
function verifyRefreshToken(token) {
  try {
    const payload = jwt.verify(token, JWT_REFRESH_SECRET);
    if (payload.type !== 'refresh') return null;
    return payload;
  } catch {
    return null;
  }
}

// [CRIT-2] Хеширование refresh токена для хранения в БД
function hashRefreshToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

// Middleware: требует подтверждённый email
async function requireVerified(req, res, next) {
  try {
    const user = await prisma.user.findUnique({
      where: { id: req.userId },
      select: { emailVerified: true },
    });
    if (!user || !user.emailVerified) {
      return res.status(403).json({ error: 'Подтвердите email для выполнения этого действия' });
    }
    next();
  } catch {
    return res.status(500).json({ error: 'Ошибка проверки верификации' });
  }
}

// Инвалидировать кеш бана
function invalidateBanCache(userId) {
  banCache.delete(userId);
}

module.exports = { authenticate, generateTokens, verifyRefreshToken, hashRefreshToken, requireVerified, invalidateBanCache };
