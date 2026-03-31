const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const logger = require('../utils/logger');

const JWT_SECRET = process.env.JWT_SECRET;

// In-memory CSRF token store (userId → token)
const csrfTokens = new Map();
const CSRF_TTL = 60 * 60 * 1000; // 1 hour

function generateCsrfToken(userId) {
  const token = crypto.randomBytes(32).toString('hex');
  csrfTokens.set(userId, { token, createdAt: Date.now() });
  return token;
}

function validateCsrfToken(userId, token) {
  const entry = csrfTokens.get(userId);
  if (!entry) return false;
  if (Date.now() - entry.createdAt > CSRF_TTL) {
    csrfTokens.delete(userId);
    return false;
  }
  if (!token || token.length !== 64) return false;
  return crypto.timingSafeEqual(Buffer.from(entry.token), Buffer.from(token));
}

// Извлечь userId из cookie/header без полной валидации бана (lightweight)
function extractUserId(req) {
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

// Middleware: validate CSRF on mutating requests
function csrfProtection(req, res, next) {
  // Skip GET, HEAD, OPTIONS (safe methods)
  if (['GET', 'HEAD', 'OPTIONS'].includes(req.method)) return next();

  const origin = req.headers.origin;

  // Извлечь userId из токена (authenticate ещё не запускался на уровне route)
  const userId = req.userId || extractUserId(req);
  // Skip if no userId (unauthenticated routes like login/register)
  if (!userId) return next();

  const csrfToken = req.headers['x-csrf-token'];
  if (!csrfToken || !validateCsrfToken(userId, csrfToken)) {
    // Логируем но не блокируем (graceful — клиент мог не успеть получить токен)
    logger.warn({ userId, origin }, 'CSRF warning: invalid token');
    return res.status(403).json({ error: 'Недействительный CSRF-токен' });
  }
  next();
}

// Cleanup expired tokens every 30 min
setInterval(() => {
  const now = Date.now();
  for (const [key, entry] of csrfTokens) {
    if (now - entry.createdAt > CSRF_TTL) csrfTokens.delete(key);
  }
}, 30 * 60 * 1000);

module.exports = { generateCsrfToken, validateCsrfToken, csrfProtection };
