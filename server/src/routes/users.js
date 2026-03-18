const { Router } = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const prisma = require('../db');
const { authenticate } = require('../middleware/auth');

const router = Router();

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
        username: { contains: q },
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

    const ext = req.file.mimetype === 'image/png' ? '.png' : '.jpg';
    const filename = req.userId + ext;
    const finalPath = path.join(avatarDir, filename);

    // Удалить старый аватар если был другого формата
    const otherExt = ext === '.png' ? '.jpg' : '.png';
    const otherPath = path.join(avatarDir, req.userId + otherExt);
    if (fs.existsSync(otherPath)) fs.unlinkSync(otherPath);

    // Переместить загруженный файл
    fs.renameSync(req.file.path, finalPath);

    await prisma.user.update({
      where: { id: req.userId },
      data: { avatar: filename },
    });

    res.json({ avatar: filename });
  } catch (err) {
    // Очистить временный файл при ошибке
    if (req.file?.path && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
    res.status(500).json({ error: 'Ошибка загрузки аватара' });
  }
});

// Обновление профиля
router.put('/me', authenticate, async (req, res) => {
  try {
    const { bio, status, customStatus, hue, showLastSeen } = req.body;
    const data = {};

    // Валидация полей
    if (bio !== undefined) {
      if (typeof bio !== 'string' || bio.length > 200) {
        return res.status(400).json({ error: 'Bio должно быть до 200 символов' });
      }
      data.bio = bio.replace(/[<>]/g, '');
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
      data.customStatus = customStatus.replace(/[<>]/g, '');
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
        for (const [, s] of io.sockets.sockets) {
          if (s.userId === req.userId) {
            s.userStatus = data.status;
          }
        }

        if (data.status === 'invisible') {
          // При переключении на невидимку — показать всем что ушёл в офлайн
          io.emit('user:offline', { userId: req.userId });
        } else {
          // Для DND и online — показать статус
          io.emit('user:statusChange', {
            userId: req.userId,
            status: data.status,
            customStatus: data.customStatus || user.customStatus,
          });
          // Если был invisible → стал online/dnd — показать как вошёл
          io.emit('user:online', { userId: req.userId, status: data.status });
        }
      }
    }

    res.json(user);
  } catch {
    res.status(500).json({ error: 'Ошибка обновления профиля' });
  }
});

module.exports = router;
