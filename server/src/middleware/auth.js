const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'blesk-dev-secret-change-in-production';

// Проверка JWT токена (только access, refresh не принимается)
function authenticate(req, res, next) {
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

module.exports = { authenticate, generateTokens, verifyRefreshToken };
