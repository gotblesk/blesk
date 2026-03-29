const jwt = require('jsonwebtoken');
const prisma = require('../db');

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) {
  console.error('FATAL: JWT_SECRET не задан в .env');
  process.exit(1);
}

// Парсинг cookie-строки из заголовка (для WebSocket handshake)
function parseCookies(str) {
  if (!str) return {};
  return Object.fromEntries(
    str.split(';').map((c) => {
      const [key, ...rest] = c.trim().split('=');
      return [decodeURIComponent(key), decodeURIComponent(rest.join('='))];
    })
  );
}

async function socketAuth(socket, next) {
  // Проверить httpOnly cookie, затем auth.token (backward compat)
  const cookies = parseCookies(socket.handshake.headers?.cookie);
  const cookieToken = cookies['blesk_token'] || null;
  const token = cookieToken || socket.handshake.auth?.token;

  if (!token) {
    return next(new Error('Требуется авторизация'));
  }

  try {
    const payload = jwt.verify(token, JWT_SECRET);
    // Refresh token нельзя использовать для WebSocket авторизации
    if (payload.type === 'refresh') {
      return next(new Error('Нельзя использовать refresh token'));
    }

    // Проверка бана — забаненный юзер не может подключиться
    const user = await prisma.user.findUnique({
      where: { id: payload.userId },
      select: { id: true, banned: true },
    });
    if (!user) return next(new Error('Пользователь не найден'));
    if (user.banned) return next(new Error('Аккаунт заблокирован'));

    socket.userId = payload.userId;
    next();
  } catch (err) {
    if (err.message === 'Аккаунт заблокирован' || err.message === 'Пользователь не найден') {
      return next(err);
    }
    next(new Error('Неверный токен'));
  }
}

module.exports = { socketAuth };
