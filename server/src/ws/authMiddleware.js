const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'blesk-dev-secret-change-in-production';

function socketAuth(socket, next) {
  const token = socket.handshake.auth?.token;

  if (!token) {
    return next(new Error('Требуется авторизация'));
  }

  try {
    const payload = jwt.verify(token, JWT_SECRET);
    socket.userId = payload.userId;
    next();
  } catch {
    next(new Error('Неверный токен'));
  }
}

module.exports = { socketAuth };
