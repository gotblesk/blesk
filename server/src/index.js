require('dotenv').config();

// Валидация критичных переменных окружения
if (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 32) {
  console.error('FATAL: JWT_SECRET must be set and at least 32 characters');
  process.exit(1);
}
if (!process.env.JWT_REFRESH_SECRET || process.env.JWT_REFRESH_SECRET.length < 32) {
  console.error('FATAL: JWT_REFRESH_SECRET must be set and at least 32 characters');
  process.exit(1);
}

const express = require('express');
const { createServer } = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const cookieParser = require('cookie-parser');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');

const prisma = require('./db');
const logger = require('./utils/logger');

const app = express();
const httpServer = createServer(app);

// CORS: разрешаем запросы без origin (Electron, мобильные) + явные origins
const corsHandler = (origin, callback) => {
  if (!origin) return callback(null, true);
  const allowed = [process.env.CLIENT_URL].filter(Boolean);
  if (process.env.NODE_ENV !== 'production') {
    allowed.push('http://localhost:5173', 'http://localhost:3000');
  }
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
      logger.info('Redis adapter подключён для Socket.io');
    }).catch((err) => {
      logger.warn({ err: err.message }, 'Redis adapter не удалось подключить');
      logger.info('Работаем в single-instance режиме');
    });
  } catch (err) {
    logger.info({ err: err?.message }, 'Socket.io: single-instance mode (Redis adapter не установлен)');
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
      connectSrc: [
        "'self'", 'wss://blesk.fun', 'https://blesk.fun', 'wss://*.blesk.fun', 'https://*.blesk.fun',
        ...(process.env.NODE_ENV !== 'production' ? ['ws://localhost:*', 'http://localhost:*'] : []),
      ],
      fontSrc: ["'self'", 'https://fonts.gstatic.com'],
      mediaSrc: ["'self'", 'blob:'],
      frameAncestors: ["'none'"],
      formAction: ["'self'"],
    },
  },
}));
app.use(cors({ origin: corsHandler, credentials: true }));
app.use(cookieParser());
app.use(express.json({ limit: '100kb' }));
// [IMP-2] Trust proxy для корректного rate limiting за Cloudflare
app.set('trust proxy', 1);

// Статические файлы — аватары, вложения, превью
// UUID-имена файлов обеспечивают защиту от перебора
const uploadsHeaders = (req, res, next) => {
  res.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Cache-Control', 'public, max-age=3600, must-revalidate');
  next();
};
app.use('/uploads/avatars', uploadsHeaders, express.static(path.join(__dirname, '..', 'uploads', 'avatars')));
// Вложения и превью доступны только через authenticated download в upload.js
// app.use('/uploads/attachments', uploadsHeaders, express.static(path.join(__dirname, '..', 'uploads', 'attachments')));
// app.use('/uploads/thumbs', uploadsHeaders, express.static(path.join(__dirname, '..', 'uploads', 'thumbs')));

// Health check — до rate limiting и роутов
app.get('/health', async (req, res) => {
  try {
    await prisma.$queryRaw`SELECT 1`;
    res.json({ status: 'ok', uptime: process.uptime(), db: 'connected' });
  } catch {
    res.status(503).json({ status: 'degraded', uptime: process.uptime(), db: 'disconnected' });
  }
});

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
// [CRIT-3] uploadLimiter только для upload-эндпоинтов (не для всех /api/chats)
app.use('/api/chats', chatLimiter, csrfProtection, chatRoutes);
app.use('/api/chats', csrfProtection, uploadRoutes);
app.use('/api/users', chatLimiter, csrfProtection, userRoutes);
app.use('/api/notifications', chatLimiter, csrfProtection, notificationRoutes);
app.use('/api/friends', chatLimiter, csrfProtection, friendRoutes);
app.use('/api/feedback', chatLimiter, csrfProtection, feedbackRoutes);
app.use('/api/voice', voiceLimiter, csrfProtection, voiceRoutes);
app.use('/api/internal', internalLimiter, csrfProtection, internalRoutes);
app.use('/api/shield', chatLimiter, csrfProtection, shieldRoutes);

// Проверка работоспособности
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', version: '1.1.1-beta' });
});

// WebSocket — авторизация + обработчики
const { socketAuth } = require('./ws/authMiddleware');
const { chatHandler } = require('./ws/chatHandler');
const { voiceHandler, clearVoiceIntervals } = require('./ws/voiceHandler');
const { callHandler, setUserSockets: setCallUserSockets } = require('./ws/callHandler');
const { createWorkers, setOnWorkerDied } = require('./services/mediasoup');
const { initScanner } = require('./services/fileScanner');

// Уведомить клиентов в голосовых комнатах при крэше worker'а
setOnWorkerDied((workerIdx) => {
  logger.warn({ workerIdx }, 'mediasoup worker died — уведомляем клиентов');
  io.emit('voice:worker-restart', { worker: workerIdx, message: 'Голосовой сервер перезапускается. Переподключение через несколько секунд.' });
});

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

// Инициализировать socketUtils для O(1) поиска из route-файлов
const socketUtils = require('./utils/socketUtils');
socketUtils.init(io, userSockets);

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
  logger.error({ err }, 'Express error');
  const status = err.status || err.statusCode || 500;
  res.status(status).json({
    error: process.env.NODE_ENV === 'production' ? 'Внутренняя ошибка сервера' : err.message,
  });
});

// Запуск сервера с mediasoup
const PORT = process.env.PORT || 3000;

(async () => {
  // [IMP-6 A1] Валидация ADMIN_SECRET
  if (!process.env.ADMIN_SECRET || process.env.ADMIN_SECRET.length < 32) {
    if (process.env.NODE_ENV === 'production') {
      logger.error('FATAL: ADMIN_SECRET must be set and at least 32 characters in production');
      process.exit(1);
    }
    logger.warn('WARNING: ADMIN_SECRET не задан или слишком короткий — admin broadcast отключён');
  }

  try {
    await createWorkers();
    logger.info('mediasoup Workers запущены');
  } catch (err) {
    logger.error({ err: err.message }, 'mediasoup не удалось запустить');
    logger.info('Голосовые комнаты будут недоступны');
  }

  // ClamAV антивирусный сканер (graceful — если недоступен, файлы загружаются без проверки)
  await initScanner();

  httpServer.listen(PORT, () => {
    logger.info({ port: PORT }, 'blesk server запущен');
  });

  const gracefulShutdown = async (signal) => {
    logger.info({ signal }, 'Shutting down gracefully...');
    clearVoiceIntervals(); // [P4] Очистить интервалы voice handler
    io.close();
    httpServer.close();
    await prisma.$disconnect();
    process.exit(0);
  };
  process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
  process.on('SIGINT', () => gracefulShutdown('SIGINT'));

  // Очистка expired refresh tokens каждые 6 часов
  setInterval(async () => {
    try {
      const result = await prisma.refreshToken.deleteMany({
        where: { expiresAt: { lt: new Date() } },
      });
      if (result.count > 0) {
        logger.info({ count: result.count }, 'Очищено expired refresh tokens');
      }
    } catch (err) {
      logger.error({ err: err.message }, 'Ошибка очистки refresh tokens');
    }
  }, 6 * 60 * 60 * 1000);

  // Очистка expired email codes каждые 2 часа
  setInterval(async () => {
    try {
      const result = await prisma.emailCode.deleteMany({
        where: { expiresAt: { lt: new Date() } },
      });
      if (result.count > 0) {
        logger.info({ count: result.count }, 'Очищено expired email codes');
      }
    } catch (err) {
      logger.error({ err: err.message }, 'Ошибка очистки email codes');
    }
  }, 2 * 60 * 60 * 1000);
})();

// [IMP-3 A1] Graceful crash — exit на fatal errors (PM2 перезапустит)
process.on('uncaughtException', (err) => {
  logger.error({ err }, 'FATAL uncaughtException');
  process.exit(1);
});

process.on('unhandledRejection', (reason) => {
  logger.error({ reason }, 'FATAL unhandledRejection');
  process.exit(1);
});
