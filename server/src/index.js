require('dotenv').config();

// Валидация критичных переменных окружения
if (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 16) {
  console.error('FATAL: JWT_SECRET must be set and at least 16 characters');
  process.exit(1);
}

const express = require('express');
const { createServer } = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const cookieParser = require('cookie-parser');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');

const app = express();
const httpServer = createServer(app);

// CORS: разрешаем запросы без origin (Electron, мобильные) + явные origins
const corsHandler = (origin, callback) => {
  // Electron и мобильные приложения отправляют origin: null/undefined
  if (!origin) return callback(null, true);
  const allowed = [process.env.CLIENT_URL, 'http://localhost:5173', 'http://localhost:3000'].filter(Boolean);
  if (allowed.includes(origin)) return callback(null, true);
  callback(null, false);
};

const io = new Server(httpServer, {
  cors: { origin: corsHandler, methods: ['GET', 'POST'], credentials: true },
  // [CRIT-3] maxHttpBufferSize для защиты от oversized payloads
  maxHttpBufferSize: 1e6, // 1MB макс. размер WebSocket пакета
});

// [CRIT-3] Redis adapter — для горизонтального масштабирования
// Подключается автоматически если установлена переменная REDIS_URL
if (process.env.REDIS_URL) {
  try {
    const { createAdapter } = require('@socket.io/redis-adapter');
    const { createClient } = require('redis');
    const pubClient = createClient({ url: process.env.REDIS_URL });
    const subClient = pubClient.duplicate();
    Promise.all([pubClient.connect(), subClient.connect()]).then(() => {
      io.adapter(createAdapter(pubClient, subClient));
      console.log('Redis adapter подключён для Socket.io');
    }).catch((err) => {
      console.warn('Redis adapter не удалось подключить:', err.message);
      console.log('Работаем в single-instance режиме');
    });
  } catch {
    // @socket.io/redis-adapter не установлен — работаем без него
    console.log('Socket.io: single-instance mode (Redis adapter не установлен)');
  }
}

// Передать io в app для доступа из REST routes
app.set('io', io);

const path = require('path');

// Middleware
app.use(helmet({
  crossOriginResourcePolicy: { policy: 'cross-origin' },
  crossOriginEmbedderPolicy: false,
  strictTransportSecurity: { maxAge: 31536000, includeSubDomains: true },
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      imgSrc: ["'self'", 'data:', 'blob:'],
      connectSrc: ["'self'", 'ws:', 'wss:', 'http:', 'https:'],
      fontSrc: ["'self'", 'https://fonts.gstatic.com'],
      mediaSrc: ["'self'", 'blob:'],
    },
  },
}));
app.use(cors({ origin: corsHandler, credentials: true }));
app.use(cookieParser());
app.use(express.json({ limit: '100kb' }));
// [IMP-2] Trust proxy для корректного rate limiting за Cloudflare
app.set('trust proxy', 1);

// [CRIT-1] Аватары — публичные (без auth), вложения — через auth routes в upload.js
app.use('/uploads/avatars', (req, res, next) => {
  res.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
  res.setHeader('Access-Control-Allow-Origin', '*');
  next();
}, express.static(path.join(__dirname, '..', 'uploads', 'avatars')));
// Attachments и thumbs обслуживаются через авторизованные endpoints в upload.js

// Rate limiting — раздельные лимиты
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 100,
  message: { error: 'Слишком много запросов. Попробуйте позже.' },
});

const chatLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 600,
  message: { error: 'Слишком много запросов. Попробуйте позже.' },
});

const voiceLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 200,
  message: { error: 'Слишком много запросов.' },
});

const uploadLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 20,
  message: { error: 'Слишком много загрузок.' },
});

const internalLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 300,
  message: { error: 'Слишком много запросов.' },
});

// Передать io в роуты через app.locals
app.locals.io = io;

// Роуты
const authRoutes = require('./routes/auth');
const chatRoutes = require('./routes/chats');
const userRoutes = require('./routes/users');
const notificationRoutes = require('./routes/notifications');
const friendRoutes = require('./routes/friends');
const feedbackRoutes = require('./routes/feedback');
const voiceRoutes = require('./routes/voice');
const internalRoutes = require('./routes/admin');
const uploadRoutes = require('./routes/upload');
const channelRoutes = require('./routes/channels');
const shieldRoutes = require('./routes/shield');
const { csrfProtection } = require('./middleware/csrf');

app.use('/api/auth', authLimiter, authRoutes);
app.use('/api/channels', chatLimiter, csrfProtection, channelRoutes);
// [CRIT-3] uploadLimiter вместо chatLimiter для файлов
app.use('/api/chats', uploadLimiter, csrfProtection, uploadRoutes);
app.use('/api/chats', chatLimiter, csrfProtection, chatRoutes);
app.use('/api/users', chatLimiter, csrfProtection, userRoutes);
app.use('/api/notifications', chatLimiter, csrfProtection, notificationRoutes);
app.use('/api/friends', chatLimiter, csrfProtection, friendRoutes);
app.use('/api/feedback', chatLimiter, csrfProtection, feedbackRoutes);
app.use('/api/voice', voiceLimiter, csrfProtection, voiceRoutes);
app.use('/api/internal', internalLimiter, csrfProtection, internalRoutes);
app.use('/api/shield', chatLimiter, csrfProtection, shieldRoutes);

// Проверка работоспособности
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', version: '0.1.0-alpha' });
});

// WebSocket — авторизация + обработчики
const { socketAuth } = require('./ws/authMiddleware');
const { chatHandler } = require('./ws/chatHandler');
const { voiceHandler } = require('./ws/voiceHandler');
const { callHandler, setUserSockets: setCallUserSockets } = require('./ws/callHandler');
const { createWorkers } = require('./services/mediasoup');
const { initScanner } = require('./services/fileScanner');

io.use(socketAuth);

// [HIGH-4] Глобальный Map<userId, Set<socket>> — O(1) поиск сокетов по userId
const userSockets = new Map();

// Утилита для поиска сокетов пользователя (используется всеми handlers)
function findUserSockets(targetUserId) {
  return userSockets.get(targetUserId) || new Set();
}
function findUserSocket(targetUserId) {
  const sockets = userSockets.get(targetUserId);
  if (!sockets || sockets.size === 0) return null;
  return sockets.values().next().value;
}

// Передать userSockets в callHandler для O(1) поиска
setCallUserSockets(userSockets);

io.on('connection', (socket) => {
  const uid = socket.userId;

  // Добавить в userSockets
  if (!userSockets.has(uid)) userSockets.set(uid, new Set());
  userSockets.get(uid).add(socket);

  socket.on('disconnect', () => {
    const sockets = userSockets.get(uid);
    if (sockets) {
      sockets.delete(socket);
      if (sockets.size === 0) userSockets.delete(uid);
    }
  });

  chatHandler(io, socket);
  voiceHandler(io, socket);
  callHandler(io, socket);
});

// [CRIT-1 A1] Глобальный Express error handler
app.use((err, req, res, next) => {
  console.error('Express error:', err);
  const status = err.status || err.statusCode || 500;
  res.status(status).json({
    error: process.env.NODE_ENV === 'production' ? 'Внутренняя ошибка сервера' : err.message,
  });
});

// Запуск сервера с mediasoup
const PORT = process.env.PORT || 3000;

(async () => {
  // [IMP-6 A1] Валидация ADMIN_SECRET
  if (!process.env.ADMIN_SECRET || process.env.ADMIN_SECRET.length < 16) {
    console.warn('WARNING: ADMIN_SECRET не задан или слишком короткий — admin broadcast отключён');
  }

  try {
    await createWorkers();
    console.log('mediasoup Workers запущены');
  } catch (err) {
    console.error('mediasoup не удалось запустить:', err.message);
    console.log('Голосовые комнаты будут недоступны');
  }

  // ClamAV антивирусный сканер (graceful — если недоступен, файлы загружаются без проверки)
  await initScanner();

  httpServer.listen(PORT, () => {
    console.log(`blesk server запущен на порту ${PORT}`);
  });

  // Очистка expired refresh tokens каждые 6 часов
  setInterval(async () => {
    try {
      const result = await prisma.refreshToken.deleteMany({
        where: { expiresAt: { lt: new Date() } },
      });
      if (result.count > 0) {
        console.log(`Очищено ${result.count} expired refresh tokens`);
      }
    } catch (err) {
      console.error('Ошибка очистки refresh tokens:', err.message);
    }
  }, 6 * 60 * 60 * 1000);

  // Очистка expired email codes каждые 2 часа
  setInterval(async () => {
    try {
      const result = await prisma.emailCode.deleteMany({
        where: { expiresAt: { lt: new Date() } },
      });
      if (result.count > 0) {
        console.log(`Очищено ${result.count} expired email codes`);
      }
    } catch (err) {
      console.error('Ошибка очистки email codes:', err.message);
    }
  }, 2 * 60 * 60 * 1000);
})();

// [IMP-3 A1] Graceful crash — exit на fatal errors (PM2 перезапустит)
process.on('uncaughtException', (err) => {
  console.error('FATAL uncaughtException:', err);
  process.exit(1);
});

process.on('unhandledRejection', (reason) => {
  console.error('FATAL unhandledRejection:', reason);
  process.exit(1);
});
