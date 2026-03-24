const jwt = require('jsonwebtoken');
const prisma = require('../db');

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
  console.error('FATAL: JWT_SECRET не задан в .env — сервер не может запуститься безопасно');
  process.exit(1);
}

// Кеш банов: userId → { banned, ts }
const banCache = new Map();
const BAN_CACHE_TTL = 60000; // 60 секунд

// Проверка JWT токена (только access, refresh не принимается)
async function authenticate(req, res, next) {
  const header = req.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'Требуется авторизация' });
  }

  const token = header.slice(7);
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    // Refresh токен не должен использоваться как access
    if (payload.type === 'refresh') {
      return res.status(401).json({ error: 'Недействительный токен' });
    }
    req.userId = payload.userId;

    // Проверка бана с кешем (не делать запрос при каждом вызове)
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

// Генерация токенов
function generateTokens(userId) {
  const token = jwt.sign({ userId }, JWT_SECRET, { expiresIn: '15m' });
  const refreshToken = jwt.sign({ userId, type: 'refresh' }, JWT_SECRET, { expiresIn: '7d' });
  return { token, refreshToken };
}

// Проверка refresh токена
function verifyRefreshToken(token) {
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    if (payload.type !== 'refresh') return null;
    return payload;
  } catch {
    return null;
  }
}

// Middleware: требует подтверждённый email (для чувствительных маршрутов)
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

// Инвалидировать кеш бана (вызывать при бане/разбане)
function invalidateBanCache(userId) {
  banCache.delete(userId);
}

module.exports = { authenticate, generateTokens, verifyRefreshToken, requireVerified, invalidateBanCache };
