const { Router } = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const prisma = require('../db');
const { authenticate } = require('../middleware/auth');
const sharp = require('sharp');
const { validateFile } = require('../services/fileValidator');
const { findUserSockets } = require('../utils/socketUtils');
const logger = require('../utils/logger');

const router = Router();

// Санитизация текста — защита от XSS
function sanitizeText(str) {
  return str.replace(/[<>"'`&]/g, (ch) => ({
    '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#x27;', '`': '&#x60;', '&': '&amp;',
  }[ch]));
}

// Настройка multer для загрузки аватаров
const avatarDir = path.join(__dirname, '..', '..', 'uploads', 'avatars');
if (!fs.existsSync(avatarDir)) fs.mkdirSync(avatarDir, { recursive: true });

const avatarUpload = multer({
  dest: avatarDir,
  limits: { fileSize: 5 * 1024 * 1024 }, // 5MB
  fileFilter: (req, file, cb) => {
    const allowed = ['image/jpeg', 'image/png', 'image/webp'];
    cb(null, allowed.includes(file.mimetype));
  },
});

// Поиск пользователей по username
router.get('/search', authenticate, async (req, res) => {
  try {
    const { q } = req.query;
    if (!q || q.length < 2) {
      return res.json([]);
    }

    const users = await prisma.user.findMany({
      where: {
        username: { contains: q, mode: 'insensitive' },
        id: { not: req.userId },
      },
      select: { id: true, username: true, tag: true, hue: true, avatar: true, status: true },
      take: 10,
    });

    res.json(users);
  } catch {
    res.status(500).json({ error: 'Ошибка поиска' });
  }
});

// Список заблокированных
router.get('/blocked', authenticate, async (req, res) => {
  try {
    const blocked = await prisma.blockedUser.findMany({
      where: { userId: req.userId },
      include: { blocked: { select: { id: true, username: true, avatar: true, hue: true } } },
      orderBy: { createdAt: 'desc' },
    });
    res.json(blocked.map(b => b.blocked));
  } catch (err) {
    logger.error({ err: err.message }, 'Get blocked error');
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// Профиль пользователя по id
router.get('/:id', authenticate, async (req, res) => {
  try {
    const user = await prisma.user.findUnique({
      where: { id: req.params.id },
      select: {
        id: true,
        username: true,
        tag: true,
        hue: true,
        avatar: true,
        bio: true,
        status: true,
        customStatus: true,
        publicKey: true,
        lastSeenAt: true,
        showLastSeen: true,
        createdAt: true,
      },
    });

    if (!user) {
      return res.status(404).json({ error: 'Пользователь не найден' });
    }

    // Скрыть lastSeenAt если пользователь отключил показ и это не свой профиль
    if (user.showLastSeen === false && req.userId !== user.id) {
      user.lastSeenAt = null;
    }

    // Проверить дружбу
    const friendship = await prisma.friendRequest.findFirst({
      where: {
        status: 'accepted',
        OR: [
          { senderId: req.userId, receiverId: req.params.id },
          { senderId: req.params.id, receiverId: req.userId },
        ],
      },
    });

    res.json({ ...user, isFriend: !!friendship });
  } catch {
    res.status(500).json({ error: 'Ошибка получения профиля' });
  }
});

// Загрузка аватара
router.post('/me/avatar', authenticate, avatarUpload.single('avatar'), async (req, res) => {
  try {
    if (!req.file) return res.status(400).json({ error: 'Файл не загружен или формат не поддерживается' });

    // Валидация magic bytes (защита от подмены Content-Type)
    const validation = await validateFile(req.file.path, req.file.originalname, req.file.mimetype, req.file.size);
    if (!validation.ok || !validation.mime.startsWith('image/')) {
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: 'Недопустимый формат изображения' });
    }

    // [S9] Валидация размеров изображения до обработки
    const imgMeta = await sharp(req.file.path).metadata();
    if (imgMeta.width > 8000 || imgMeta.height > 8000) {
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: 'Изображение слишком большое' });
    }
    if (imgMeta.width < 32 || imgMeta.height < 32) {
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: 'Изображение слишком маленькое' });
    }

    // Ресайз аватара: макс 512x512, JPEG 85% — экономия хранилища
    const ext = '.jpg';
    const filename = req.userId + ext;
    const finalPath = path.join(avatarDir, filename);
    const tempPath = path.join(avatarDir, `${req.userId}.tmp`);

    await sharp(req.file.path)
      .resize(512, 512, { fit: 'inside', withoutEnlargement: true })
      .jpeg({ quality: 85 })
      .toFile(tempPath);

    // Удалить оригинальный загруженный файл
    try { fs.unlinkSync(req.file.path); } catch (err) { logger.error({ err: err.message }, 'Failed to delete original upload'); }

    // Удалить старые аватары других форматов
    for (const e of ['.jpg', '.png', '.webp']) {
      if (e !== ext) {
        const old = path.join(avatarDir, req.userId + e);
        try { fs.unlinkSync(old); } catch (err) { logger.error({ err: err.message }, 'Failed to delete old avatar'); }
      }
    }

    // Атомарный rename из temp в финальный путь
    fs.renameSync(tempPath, finalPath);

    await prisma.user.update({
      where: { id: req.userId },
      data: { avatar: filename },
    });

    // Оповестить участников общих комнат об обновлении аватара
    const io = req.app.locals.io;
    if (io) {
      const participations = await prisma.roomParticipant.findMany({
        where: { userId: req.userId },
        select: { roomId: true },
      });
      for (const p of participations) {
        io.to(p.roomId).emit('user:updated', { userId: req.userId, avatar: filename, updatedAt: new Date().toISOString() });
      }
    }

    res.json({ avatar: filename });
  } catch (err) {
    // Очистить временные файлы при ошибке
    if (req.file?.path && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
    const tempPath = path.join(avatarDir, `${req.userId}.tmp`);
    if (fs.existsSync(tempPath)) try { fs.unlinkSync(tempPath); } catch (err) { logger.error({ err: err.message }, 'Failed to cleanup temp avatar file'); }
    res.status(500).json({ error: 'Ошибка загрузки аватара' });
  }
});

// Обновление профиля
router.put('/me', authenticate, async (req, res) => {
  try {
    const { username, bio, status, customStatus, hue, showLastSeen, showOnline, showTyping } = req.body;
    const data = {};

    // Смена никнейма
    if (username !== undefined) {
      const name = String(username).trim();
      if (name.length < 3 || name.length > 24) {
        return res.status(400).json({ error: 'Имя от 3 до 24 символов' });
      }
      if (!/^[a-zA-Z0-9_а-яА-ЯёЁ]+$/.test(name)) {
        return res.status(400).json({ error: 'Только буквы, цифры и подчёркивание' });
      }
      // Уникальность
      const existing = await prisma.user.findFirst({ where: { username: name, NOT: { id: req.userId } } });
      if (existing) {
        return res.status(400).json({ error: 'Это имя уже занято' });
      }
      data.username = name;
    }

    // Валидация полей
    if (bio !== undefined) {
      if (typeof bio !== 'string' || bio.length > 200) {
        return res.status(400).json({ error: 'Bio должно быть до 200 символов' });
      }
      data.bio = sanitizeText(bio);
    }

    if (status !== undefined) {
      const allowed = ['online', 'dnd', 'invisible'];
      if (!allowed.includes(status)) {
        return res.status(400).json({ error: 'Недопустимый статус' });
      }
      data.status = status;
    }

    if (customStatus !== undefined) {
      if (typeof customStatus !== 'string' || customStatus.length > 50) {
        return res.status(400).json({ error: 'Кастомный статус до 50 символов' });
      }
      // Санитизация HTML-символов
      data.customStatus = sanitizeText(customStatus);
    }

    if (hue !== undefined) {
      const h = Number(hue);
      if (isNaN(h) || h < 0 || h > 360) {
        return res.status(400).json({ error: 'Hue должен быть от 0 до 360' });
      }
      data.hue = h;
    }

    if (showLastSeen !== undefined) {
      if (typeof showLastSeen !== 'boolean') {
        return res.status(400).json({ error: 'showLastSeen должен быть boolean' });
      }
      data.showLastSeen = showLastSeen;
    }

    if (showOnline !== undefined) {
      if (typeof showOnline !== 'boolean') {
        return res.status(400).json({ error: 'showOnline должен быть boolean' });
      }
      data.showOnline = showOnline;
    }

    if (showTyping !== undefined) {
      if (typeof showTyping !== 'boolean') {
        return res.status(400).json({ error: 'showTyping должен быть boolean' });
      }
      data.showTyping = showTyping;
    }

    if (Object.keys(data).length === 0) {
      return res.status(400).json({ error: 'Нет данных для обновления' });
    }

    const user = await prisma.user.update({
      where: { id: req.userId },
      data,
      select: {
        id: true,
        username: true,
        tag: true,
        hue: true,
        avatar: true,
        bio: true,
        status: true,
        customStatus: true,
        bleskCoins: true,
      },
    });

    // Оповещение через socket если статус изменился
    if (data.status) {
      const io = req.app.locals.io;
      if (io) {
        // Обновить userStatus на сокете
        for (const s of findUserSockets(req.userId)) {
          s.userStatus = data.status;
        }

        // Отправлять события только участникам общих комнат
        const participations = await prisma.roomParticipant.findMany({
          where: { userId: req.userId },
          select: { roomId: true },
        });
        const roomIds = participations.map(p => p.roomId);

        if (data.status === 'invisible') {
          // При переключении на невидимку — показать участникам общих комнат что ушёл в офлайн
          for (const roomId of roomIds) {
            io.to(roomId).emit('user:offline', { userId: req.userId });
          }
        } else {
          // Для DND и online — показать статус
          for (const roomId of roomIds) {
            io.to(roomId).emit('user:statusChange', {
              userId: req.userId,
              status: data.status,
              customStatus: data.customStatus || user.customStatus,
            });
            // Если был invisible → стал online/dnd — показать как вошёл
            io.to(roomId).emit('user:online', { userId: req.userId, status: data.status });
          }
        }
      }
    }

    res.json(user);
  } catch {
    res.status(500).json({ error: 'Ошибка обновления профиля' });
  }
});

// Заблокировать пользователя
router.post('/:id/block', authenticate, async (req, res) => {
  try {
    const { id: blockedId } = req.params;
    if (blockedId === req.userId) return res.status(400).json({ error: 'Нельзя заблокировать себя' });

    await prisma.blockedUser.upsert({
      where: { userId_blockedId: { userId: req.userId, blockedId } },
      create: { userId: req.userId, blockedId },
      update: {},
    });

    res.json({ ok: true });
  } catch (err) {
    logger.error({ err: err.message }, 'Block user error');
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// Разблокировать
router.delete('/:id/block', authenticate, async (req, res) => {
  try {
    await prisma.blockedUser.deleteMany({
      where: { userId: req.userId, blockedId: req.params.id },
    });
    res.json({ ok: true });
  } catch (err) {
    logger.error({ err: err.message }, 'Unblock error');
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

module.exports = router;
