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

app.use('/api/auth', authLimiter, authRoutes);
app.use('/api/chats', chatLimiter, chatRoutes);
app.use('/api/users', chatLimiter, userRoutes);
app.use('/api/notifications', chatLimiter, notificationRoutes);
app.use('/api/friends', chatLimiter, friendRoutes);
app.use('/api/feedback', chatLimiter, feedbackRoutes);

// Проверка работоспособности
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', version: '0.1.0-alpha' });
});

// WebSocket — авторизация + обработчик чата
const { socketAuth } = require('./ws/authMiddleware');
const { chatHandler } = require('./ws/chatHandler');

io.use(socketAuth);

io.on('connection', (socket) => {
  console.log(`🟢 ${socket.userId} подключился`);
  chatHandler(io, socket);
});

const PORT = process.env.PORT || 3000;
httpServer.listen(PORT, () => {
  console.log(`blesk server запущен на порту ${PORT}`);
});
