const { Router } = require('express');
const crypto = require('crypto');
const prisma = require('../db');

const router = Router();

// Секретный ключ для админских действий (ОБЯЗАТЕЛЬНО задать в .env)
const ADMIN_SECRET = process.env.ADMIN_SECRET;

// Middleware: проверить секретный ключ
function adminAuth(req, res, next) {
  if (!ADMIN_SECRET) {
    return res.status(503).json({ error: 'ADMIN_SECRET не настроен на сервере' });
  }
  const secret = req.headers['x-admin-secret'];
  if (!secret || secret.length !== ADMIN_SECRET.length ||
      !crypto.timingSafeEqual(Buffer.from(secret), Buffer.from(ADMIN_SECRET))) {
    return res.status(403).json({ error: 'Доступ запрещён' });
  }
  next();
}

// POST /api/admin/broadcast-update — уведомить всех онлайн-юзеров о новом обновлении
router.post('/broadcast-update', adminAuth, async (req, res) => {
  try {
    const { version, changelog } = req.body;
    if (!version) {
      return res.status(400).json({ error: 'Укажите version' });
    }

    const io = req.app.locals.io;
    if (!io) {
      return res.status(500).json({ error: 'Socket.IO не инициализирован' });
    }

    // Получить всех подключённых пользователей
    const connectedUserIds = new Set();
    for (const [, socket] of io.sockets.sockets) {
      if (socket.userId) connectedUserIds.add(socket.userId);
    }

    // Создать уведомления в БД — по одному на каждого юзера
    const notifMap = new Map(); // userId → notification
    for (const uid of connectedUserIds) {
      const notification = await prisma.notification.create({
        data: {
          userId: uid,
          type: 'system',
          title: `Обновление ${version}`,
          body: changelog || 'Доступна новая версия blesk. Перезапустите приложение для обновления.',
        },
        include: {
          fromUser: { select: { id: true, username: true, hue: true, avatar: true } },
        },
      });
      notifMap.set(uid, notification);
    }

    // Отправить через сокет каждому (все табы одного юзера получат)
    const notifiedSockets = new Set();
    for (const [, socket] of io.sockets.sockets) {
      if (socket.userId && notifMap.has(socket.userId)) {
        // Уведомление отправляем только один раз на userId (первый сокет)
        if (!notifiedSockets.has(socket.userId)) {
          socket.emit('notification:new', notifMap.get(socket.userId));
          notifiedSockets.add(socket.userId);
        }
        // Баннер обновления — на все сокеты (все вкладки увидят)
        socket.emit('app:update-available', { version, changelog });
      }
    }

    res.json({
      ok: true,
      notified: connectedUserIds.size,
      version,
    });
  } catch (err) {
    console.error('broadcast-update error:', err);
    res.status(500).json({ error: 'Ошибка рассылки' });
  }
});

module.exports = router;
