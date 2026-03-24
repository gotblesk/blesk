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
});

const path = require('path');

// Middleware
app.use(helmet({
  crossOriginResourcePolicy: { policy: 'cross-origin' },
  crossOriginEmbedderPolicy: false,
  strictTransportSecurity: { maxAge: 31536000, includeSubDomains: true },
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'", "'unsafe-inline'", "'unsafe-eval'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      imgSrc: ["'self'", 'data:', 'blob:'],
      connectSrc: ["'self'", 'ws:', 'wss:', 'http:', 'https:'],
      fontSrc: ["'self'", 'https://fonts.gstatic.com'],
      mediaSrc: ["'self'", 'blob:'],
    },
  },
}));
app.use(cors({ origin: corsHandler, credentials: true }));
app.use(express.json({ limit: '100kb' }));

// Статика — аватары и загрузки (разрешаем cross-origin для Electron)
app.use('/uploads', (req, res, next) => {
  res.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
  res.setHeader('Access-Control-Allow-Origin', '*');
  next();
}, express.static(path.join(__dirname, '..', 'uploads')));

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

app.use('/api/auth', authLimiter, authRoutes);
app.use('/api/channels', chatLimiter, channelRoutes);
app.use('/api/chats', chatLimiter, uploadRoutes);
app.use('/api/chats', chatLimiter, chatRoutes);
app.use('/api/users', chatLimiter, userRoutes);
app.use('/api/notifications', chatLimiter, notificationRoutes);
app.use('/api/friends', chatLimiter, friendRoutes);
app.use('/api/feedback', chatLimiter, feedbackRoutes);
app.use('/api/voice', voiceLimiter, voiceRoutes);
app.use('/api/internal', internalLimiter, internalRoutes);

// Проверка работоспособности
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', version: '0.1.0-alpha' });
});

// WebSocket — авторизация + обработчики
const { socketAuth } = require('./ws/authMiddleware');
const { chatHandler } = require('./ws/chatHandler');
const { voiceHandler } = require('./ws/voiceHandler');
const { callHandler } = require('./ws/callHandler');
const { createWorkers } = require('./services/mediasoup');

io.use(socketAuth);

io.on('connection', (socket) => {
  console.log(`🟢 ${socket.userId} подключился`);
  chatHandler(io, socket);
  voiceHandler(io, socket);
  callHandler(io, socket);
});

// Запуск сервера с mediasoup
const PORT = process.env.PORT || 3000;

(async () => {
  try {
    await createWorkers();
    console.log('mediasoup Workers запущены');
  } catch (err) {
    console.error('mediasoup не удалось запустить:', err.message);
    console.log('Голосовые комнаты будут недоступны');
  }

  httpServer.listen(PORT, () => {
    console.log(`blesk server запущен на порту ${PORT}`);
  });
})();

// Глобальные обработчики ошибок — предотвращают падение сервера
process.on('uncaughtException', (err) => {
  console.error('⚠️ Uncaught Exception:', err);
});

process.on('unhandledRejection', (reason) => {
  console.error('⚠️ Unhandled Rejection:', reason);
});
