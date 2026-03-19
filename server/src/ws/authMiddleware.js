const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
  console.error('FATAL: JWT_SECRET не задан в .env');
  process.exit(1);
}

function socketAuth(socket, next) {
  const token = socket.handshake.auth?.token;

  if (!token) {
    return next(new Error('Требуется авторизация'));
  }

  try {
    const payload = jwt.verify(token, JWT_SECRET);
    // Refresh token нельзя использовать для WebSocket авторизации
    if (payload.type === 'refresh') {
      return next(new Error('Нельзя использовать refresh token'));
    }
    socket.userId = payload.userId;
    next();
  } catch {
    next(new Error('Неверный токен'));
  }
}

module.exports = { socketAuth };
