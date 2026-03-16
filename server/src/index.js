require('dotenv').config();
const express = require('express');
const { createServer } = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');

const app = express();
const httpServer = createServer(app);

// Socket.io с CORS
const io = new Server(httpServer, {
  cors: {
    origin: process.env.CLIENT_URL || 'http://localhost:5173',
    methods: ['GET', 'POST'],
  },
});

// Middleware
app.use(helmet());
app.use(cors({ origin: process.env.CLIENT_URL || 'http://localhost:5173' }));
app.use(express.json({ limit: '10mb' }));

// Rate limiting — раздельные лимиты
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 100,
  message: { error: 'Слишком много запросов. Попробуйте позже.' },
});

const chatLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 300,
  message: { error: 'Слишком много запросов. Попробуйте позже.' },
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
const adminRoutes = require('./routes/admin');

app.use('/api/auth', authLimiter, authRoutes);
app.use('/api/chats', chatLimiter, chatRoutes);
app.use('/api/users', chatLimiter, userRoutes);
app.use('/api/notifications', chatLimiter, notificationRoutes);
app.use('/api/friends', chatLimiter, friendRoutes);
app.use('/api/feedback', chatLimiter, feedbackRoutes);
app.use('/api/voice', chatLimiter, voiceRoutes);
app.use('/api/admin', adminRoutes);

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
