const { Router } = require('express');
const crypto = require('crypto');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const sharp = require('sharp');
const prisma = require('../db');
const { authenticate, requireVerified } = require('../middleware/auth');
const { validateFile } = require('../services/fileValidator');
const { findUserSockets, emitToUser } = require('../utils/socketUtils');

const rateLimit = require('express-rate-limit');
const logger = require('../utils/logger');
const router = Router();

// Rate limiter для загрузок аватаров/обложек каналов
const channelUploadLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 20,
  message: { error: 'Слишком много загрузок.' },
});

// [S10] Rate limiter для создания каналов — макс 5 в час на пользователя
const channelCreateLimiter = rateLimit({
  windowMs: 60 * 60 * 1000, // 1 час
  max: 5,
  keyGenerator: (req) => req.userId || req.ip,
  message: { error: 'Слишком много каналов. Попробуйте позже.' },
});

// Директория для аватаров каналов
const avatarDir = path.join(__dirname, '..', '..', 'uploads', 'avatars');
if (!fs.existsSync(avatarDir)) fs.mkdirSync(avatarDir, { recursive: true });

const tempDir = path.join(__dirname, '..', '..', 'uploads', 'temp');
if (!fs.existsSync(tempDir)) fs.mkdirSync(tempDir, { recursive: true });

const channelImageUpload = multer({
  dest: tempDir,
  limits: { fileSize: 10 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    const allowed = ['image/jpeg', 'image/png', 'image/webp'];
    cb(null, allowed.includes(file.mimetype));
  },
});

const VALID_CATEGORIES = ['gaming', 'music', 'art', 'tech', 'education', 'entertainment', 'news', 'sports', 'science', 'other'];

// Все эндпоинты требуют авторизации
router.use(authenticate);

// ─── GET /my — каналы пользователя (свои + подписки) ───
// ВАЖНО: /my ПЕРЕД /:id чтобы "my" не матчился как :id
router.get('/my', async (req, res) => {
  try {
    const userId = req.userId;

    // Каналы где пользователь owner/admin (через RoomParticipant)
    const ownedChannels = await prisma.room.findMany({
      where: {
        type: 'channel',
        participants: { some: { userId, role: { in: ['owner', 'admin'] } } },
      },
      include: {
        channelMeta: true,
        owner: { select: { id: true, username: true, hue: true, avatar: true } },
      },
      orderBy: { createdAt: 'desc' },
    });

    // Каналы на которые подписан
    const subscriptions = await prisma.channelSubscriber.findMany({
      where: { userId },
      include: {
        channel: {
          include: {
            channelMeta: true,
            owner: { select: { id: true, username: true, hue: true, avatar: true } },
          },
        },
      },
      orderBy: { subscribedAt: 'desc' },
    });

    const subscribedChannels = subscriptions.map((s) => s.channel);

    // Убрать дубликаты (если owner и подписчик одновременно)
    const seen = new Set(ownedChannels.map((c) => c.id));
    const uniqueSubscribed = subscribedChannels.filter((c) => !seen.has(c.id));

    res.json({
      owned: ownedChannels,
      subscribed: uniqueSubscribed,
    });
  } catch (err) {
    logger.error({ err }, 'GET /channels/my error');
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// ─── GET / — обзор каналов (публичные) ───
router.get('/', async (req, res) => {
  try {
    const userId = req.userId;
    const { sort = 'popular', category, search, page = 1, limit = 20 } = req.query;
    const parsedLimit = parseInt(limit) || 20;
    const take = Math.min(Math.max(parsedLimit, 1), 50);
    const skip = (Math.max(parseInt(page) || 1, 1) - 1) * take;

    // Фильтры
    const where = {
      type: 'channel',
      channelMeta: { isPublic: true },
    };

    // [IMP-3] Валидация категории против whitelist
    if (category && category !== 'all') {
      if (!VALID_CATEGORIES.includes(category)) {
        return res.status(400).json({ error: 'Недопустимая категория' });
      }
      where.channelMeta.category = category;
    }

    if (search && search.length > 100) {
      return res.status(400).json({ error: 'Слишком длинный поисковый запрос' });
    }

    if (search) {
      where.OR = [
        { name: { contains: search } },
        { channelMeta: { description: { contains: search } } },
      ];
    }

    // Сортировка
    let orderBy;
    switch (sort) {
      case 'growing':
        // По подписчикам за последнюю неделю — упрощённо по subscriberCount
        orderBy = { channelMeta: { subscriberCount: 'desc' } };
        break;
      case 'new':
        orderBy = { createdAt: 'desc' };
        break;
      case 'popular':
      default:
        orderBy = { channelMeta: { subscriberCount: 'desc' } };
        break;
    }

    const [channels, total] = await Promise.all([
      prisma.room.findMany({
        where,
        include: {
          channelMeta: true,
          owner: { select: { id: true, username: true, hue: true, avatar: true } },
        },
        orderBy,
        skip,
        take,
      }),
      prisma.room.count({ where }),
    ]);

    // Проверить подписки текущего пользователя
    const channelIds = channels.map((c) => c.id);
    const userSubs = await prisma.channelSubscriber.findMany({
      where: { userId, channelId: { in: channelIds } },
      select: { channelId: true },
    });
    const subscribedSet = new Set(userSubs.map((s) => s.channelId));

    const result = channels.map((ch) => ({
      ...ch,
      isSubscribed: subscribedSet.has(ch.id),
    }));

    res.json({
      channels: result,
      total,
      page: parseInt(page) || 1,
      totalPages: Math.ceil(total / take),
    });
  } catch (err) {
    logger.error({ err }, 'GET /channels error');
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// ─── GET /:id — детали канала ───
router.get('/:id', async (req, res) => {
  try {
    const userId = req.userId;
    const { id } = req.params;

    const channel = await prisma.room.findUnique({
      where: { id },
      include: {
        channelMeta: true,
        owner: { select: { id: true, username: true, hue: true, avatar: true } },
      },
    });

    if (!channel || channel.type !== 'channel') {
      return res.status(404).json({ error: 'Канал не найден' });
    }

    // Проверить подписку
    const subscription = await prisma.channelSubscriber.findUnique({
      where: { channelId_userId: { channelId: id, userId } },
    });

    // [CRIT-1] Приватный канал — доступ только для owner/подписчиков/участников
    if (channel.channelMeta && !channel.channelMeta.isPublic) {
      const hasAccess = channel.ownerId === userId || !!subscription ||
        !!(await prisma.roomParticipant.findUnique({
          where: { roomId_userId: { roomId: id, userId } },
        }));
      if (!hasAccess) return res.status(403).json({ error: 'Нет доступа к приватному каналу' });
    }

    // Проверить роль пользователя (owner/admin/member)
    const participant = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId: id, userId } },
      select: { role: true },
    });

    res.json({
      ...channel,
      isSubscribed: !!subscription,
      isOwner: channel.ownerId === userId,
      userRole: participant?.role || null,
    });
  } catch (err) {
    logger.error({ err }, 'GET /channels/:id error');
    res.status(500).json({ error: 'Ошибка сервера' });
  }
});

// ─── POST / — создать канал ───
router.post('/', requireVerified, channelCreateLimiter, async (req, res) => {
  try {
    const userId = req.userId;
    const { name, description, category } = req.body;

    if (!name?.trim()) {
      return res.status(400).json({ error: 'Название канала обязательно' });
    }
    // [IMP-4] Минимальная длина
    if (name.trim().length < 2) {
      return res.status(400).json({ error: 'Название слишком короткое (мин 2)' });
    }
    if (name.trim().length > 64) {
      return res.status(400).json({ error: 'Название слишком длинное (макс 64)' });
    }
    // [IMP-3] Валидация длины описания
    if (description && description.length > 500) {
      return res.status(400).json({ error: 'Описание слишком длинное (макс 500)' });
    }

    if (category && !VALID_CATEGORIES.includes(category)) {
      return res.status(400).json({ error: 'Недопустимая категория канала' });
    }

    // [CRIT-4] Лимит каналов на пользователя
    const ownedCount = await prisma.room.count({ where: { type: 'channel', ownerId: userId } });
    if (ownedCount >= 10) {
      return res.status(400).json({ error: 'Достигнут лимит каналов (макс. 10)' });
    }

    // Создать Room + ChannelMeta + RoomParticipant в транзакции
    const result = await prisma.$transaction(async (tx) => {
      const room = await tx.room.create({
        data: {
          name: name.trim(),
          type: 'channel',
          ownerId: userId,
        },
      });

      const meta = await tx.channelMeta.create({
        data: {
          roomId: room.id,
          description: description?.trim() || '',
          category: category || 'other',
        },
      });

      await tx.roomParticipant.create({
        data: {
          roomId: room.id,
          userId,
          role: 'owner',
        },
      });

      return { ...room, channelMeta: meta };
    });

    res.status(201).json(result);
  } catch (err) {
    logger.error({ err }, 'POST /channels error');
    res.status(500).json({ error: 'Ошибка создания канала' });
  }
});

// ─── PATCH /:id — обновить канал (только owner) ───
router.patch('/:id', async (req, res) => {
  try {
    const userId = req.userId;
    const { id } = req.params;
    const { name, description, category } = req.body;

    // Проверить владельца
    const channel = await prisma.room.findUnique({ where: { id } });
    if (!channel || channel.type !== 'channel') {
      return res.status(404).json({ error: 'Канал не найден' });
    }
    if (channel.ownerId !== userId) {
      return res.status(403).json({ error: 'Только владелец может редактировать канал' });
    }

    // Обновить Room и ChannelMeta
    const updates = {};
    const metaUpdates = {};

    if (name?.trim()) {
      if (name.trim().length > 64) {
        return res.status(400).json({ error: 'Название слишком длинное (макс 64)' });
      }
      updates.name = name.trim();
    }
    if (description !== undefined) {
      if (description.length > 500) return res.status(400).json({ error: 'Описание слишком длинное (макс 500)' });
      metaUpdates.description = description.trim();
    }
    if (category) {
      if (!VALID_CATEGORIES.includes(category)) {
        return res.status(400).json({ error: 'Недопустимая категория канала' });
      }
      metaUpdates.category = category;
    }

    const [updatedRoom, updatedMeta] = await prisma.$transaction([
      Object.keys(updates).length > 0
        ? prisma.room.update({ where: { id }, data: updates })
        : prisma.room.findUnique({ where: { id } }),
      Object.keys(metaUpdates).length > 0
        ? prisma.channelMeta.update({ where: { roomId: id }, data: metaUpdates })
        : prisma.channelMeta.findUnique({ where: { roomId: id } }),
    ]);

    const result = { ...updatedRoom, channelMeta: updatedMeta };

    // [Channel edit broadcast] Оповестить подписчиков об изменении канала
    const io = req.app.locals.io;
    if (io) {
      io.to(id).emit('channel:updated', { channelId: id, channel: result });
    }

    res.json(result);
  } catch (err) {
    logger.error({ err }, 'PATCH /channels/:id error');
    res.status(500).json({ error: 'Ошибка обновления канала' });
  }
});

// ─── POST /:id/avatar — загрузить аватар канала (owner) ───
// [CRIT-3] channelUploadLimiter вместо chatLimiter для загрузок
router.post('/:id/avatar', channelUploadLimiter, channelImageUpload.single('avatar'), async (req, res) => {
  try {
    const userId = req.userId;
    const { id } = req.params;

    if (!req.file) return res.status(400).json({ error: 'Файл не загружен' });

    const channel = await prisma.room.findUnique({ where: { id } });
    if (!channel || channel.type !== 'channel') {
      fs.unlinkSync(req.file.path);
      return res.status(404).json({ error: 'Канал не найден' });
    }
    if (channel.ownerId !== userId) {
      fs.unlinkSync(req.file.path);
      return res.status(403).json({ error: 'Только владелец может менять аватар' });
    }

    const validation = await validateFile(req.file.path, req.file.originalname, req.file.mimetype, req.file.size);
    if (!validation.ok || !validation.mime.startsWith('image/')) {
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: 'Недопустимый формат изображения' });
    }

    const MIME_EXT = { 'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp' };
    const ext = MIME_EXT[validation.mime] || '.jpg';
    const filename = `channel-${id}${ext}`;
    const finalPath = path.join(avatarDir, filename);

    // Удалить старые форматы
    for (const e of ['.jpg', '.png', '.webp']) {
      if (e !== ext) {
        const old = path.join(avatarDir, `channel-${id}${e}`);
        if (fs.existsSync(old)) fs.unlinkSync(old);
      }
    }

    fs.renameSync(req.file.path, finalPath);

    const avatarUrl = `/uploads/avatars/${filename}`;
    await prisma.channelMeta.update({ where: { roomId: id }, data: { avatarUrl } });

    res.json({ avatarUrl });
  } catch (err) {
    if (req.file?.path && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
    logger.error({ err }, 'POST /channels/:id/avatar error');
    res.status(500).json({ error: 'Ошибка загрузки аватара' });
  }
});

// ─── POST /:id/cover — загрузить обложку канала (owner) ───
router.post('/:id/cover', channelUploadLimiter, channelImageUpload.single('cover'), async (req, res) => {
  try {
    const userId = req.userId;
    const { id } = req.params;

    if (!req.file) return res.status(400).json({ error: 'Файл не загружен' });

    const channel = await prisma.room.findUnique({ where: { id } });
    if (!channel || channel.type !== 'channel') {
      fs.unlinkSync(req.file.path);
      return res.status(404).json({ error: 'Канал не найден' });
    }
    if (channel.ownerId !== userId) {
      fs.unlinkSync(req.file.path);
      return res.status(403).json({ error: 'Только владелец может менять обложку' });
    }

    const validation = await validateFile(req.file.path, req.file.originalname, req.file.mimetype, req.file.size);
    if (!validation.ok || !validation.mime.startsWith('image/')) {
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: 'Недопустимый формат изображения' });
    }

    // Resize cover to max 1200px width
    const coverFilename = `channel-${id}-cover.jpg`;
    const coverPath = path.join(avatarDir, coverFilename);

    // Удалить старую обложку
    if (fs.existsSync(coverPath)) fs.unlinkSync(coverPath);

    await sharp(req.file.path)
      .resize({ width: 1200, withoutEnlargement: true })
      .jpeg({ quality: 85 })
      .toFile(coverPath);

    // Удалить temp файл
    fs.unlinkSync(req.file.path);

    const coverUrl = `/uploads/avatars/${coverFilename}`;
    await prisma.channelMeta.update({ where: { roomId: id }, data: { coverUrl } });

    res.json({ coverUrl });
  } catch (err) {
    if (req.file?.path && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
    logger.error({ err }, 'POST /channels/:id/cover error');
    res.status(500).json({ error: 'Ошибка загрузки обложки' });
  }
});

// ─── POST /:id/subscribe — подписаться на канал ───
router.post('/:id/subscribe', async (req, res) => {
  try {
    const userId = req.userId;
    const { id } = req.params;

    // Проверить что канал существует
    const channel = await prisma.room.findUnique({
      where: { id },
      include: { channelMeta: true },
    });
    if (!channel || channel.type !== 'channel') {
      return res.status(404).json({ error: 'Канал не найден' });
    }

    // [CRIT-2] Приватный канал — нельзя подписаться без приглашения
    if (channel.channelMeta && !channel.channelMeta.isPublic && channel.ownerId !== userId) {
      return res.status(403).json({ error: 'Нельзя подписаться на приватный канал без приглашения' });
    }

    // Проверить нет ли уже подписки
    const existing = await prisma.channelSubscriber.findUnique({
      where: { channelId_userId: { channelId: id, userId } },
    });

    if (!existing) {
      await prisma.channelSubscriber.create({
        data: { channelId: id, userId },
      });
      await prisma.channelMeta.update({
        where: { roomId: id },
        data: { subscriberCount: { increment: 1 } },
      });
    }

    // Присоединить активные сокеты пользователя к комнате канала
    for (const s of findUserSockets(userId)) {
      s.join(id);
    }

    res.json({ subscribed: true });
  } catch (err) {
    // Если подписка уже есть (unique constraint) — не ошибка
    if (err.code === 'P2002') {
      return res.json({ subscribed: true });
    }
    logger.error({ err }, 'POST /channels/:id/subscribe error');
    res.status(500).json({ error: 'Ошибка подписки' });
  }
});

// ─── GET /:id/subscribers — список подписчиков ───
router.get('/:id/subscribers', async (req, res) => {
  try {
    const { id } = req.params;
    const page = parseInt(req.query.page) || 1;
    const limit = Math.min(parseInt(req.query.limit) || 50, 100);

    const room = await prisma.room.findUnique({ where: { id } });
    if (!room || room.type !== 'channel') return res.status(404).json({ error: 'Канал не найден' });

    const [subscribers, total] = await Promise.all([
      prisma.channelSubscriber.findMany({
        where: { channelId: id },
        include: { user: { select: { id: true, username: true, avatar: true, hue: true, status: true } } },
        orderBy: { subscribedAt: 'asc' },
        skip: (page - 1) * limit,
        take: limit,
      }),
      prisma.channelSubscriber.count({ where: { channelId: id } }),
    ]);

    res.json({
      subscribers: subscribers.map((s) => ({ ...s.user, subscribedAt: s.subscribedAt })),
      total,
      page,
      limit,
    });
  } catch (err) {
    logger.error({ err }, 'channel subscribers error');
    res.status(500).json({ error: 'Ошибка загрузки подписчиков' });
  }
});

// ─── PATCH /:id/mute — мьют/анмьют уведомлений ───
router.patch('/:id/mute', async (req, res) => {
  try {
    const { id } = req.params;
    const { isMuted, mutedUntil } = req.body;

    const sub = await prisma.channelSubscriber.findUnique({
      where: { channelId_userId: { channelId: id, userId: req.userId } },
    });
    if (!sub) return res.status(404).json({ error: 'Вы не подписаны' });

    const updated = await prisma.channelSubscriber.update({
      where: { id: sub.id },
      data: {
        isMuted: typeof isMuted === 'boolean' ? isMuted : sub.isMuted,
        mutedUntil: mutedUntil ? new Date(mutedUntil) : null,
      },
    });

    res.json({ isMuted: updated.isMuted, mutedUntil: updated.mutedUntil });
  } catch (err) {
    logger.error({ err }, 'channel mute error');
    res.status(500).json({ error: 'Ошибка' });
  }
});

// ─── DELETE /:id/subscribe — отписаться от канала ───
router.delete('/:id/subscribe', async (req, res) => {
  try {
    const userId = req.userId;
    const { id } = req.params;

    // Удалить подписку
    const deleted = await prisma.channelSubscriber.deleteMany({
      where: { channelId: id, userId },
    });

    if (deleted.count > 0) {
      // Декрементировать счётчик (не ниже 0)
      await prisma.$executeRaw`UPDATE "channel_meta" SET "subscriber_count" = GREATEST("subscriber_count" - 1, 0) WHERE "room_id" = ${id}`;

      // Отключить сокеты пользователя от комнаты канала
      for (const s of findUserSockets(userId)) {
        s.leave(id);
      }
    }

    res.json({ subscribed: false });
  } catch (err) {
    logger.error({ err }, 'DELETE /channels/:id/subscribe error');
    res.status(500).json({ error: 'Ошибка отписки' });
  }
});

// ─── GET /:id/posts — посты канала (с пагинацией по курсору) ───
router.get('/:id/posts', async (req, res) => {
  try {
    const userId = req.userId;
    const { id } = req.params;
    const { before, limit = 30 } = req.query;
    const parsedLimit = parseInt(limit) || 30;
    const take = Math.min(Math.max(parsedLimit, 1), 50);

    // Проверить доступ: публичный канал или подписчик
    const channel = await prisma.room.findUnique({
      where: { id },
      include: { channelMeta: true },
    });

    if (!channel || channel.type !== 'channel') {
      return res.status(404).json({ error: 'Канал не найден' });
    }

    if (!channel.channelMeta?.isPublic) {
      // Приватный канал — проверить подписку или ownership
      const hasAccess =
        channel.ownerId === userId ||
        (await prisma.channelSubscriber.findUnique({
          where: { channelId_userId: { channelId: id, userId } },
        })) ||
        (await prisma.roomParticipant.findUnique({
          where: { roomId_userId: { roomId: id, userId } },
        }));

      if (!hasAccess) {
        return res.status(403).json({ error: 'Нет доступа к этому каналу' });
      }
    }

    // Запрос постов с курсором
    const where = { roomId: id };
    // [IMP-6] Строгая валидация курсора
    if (before && typeof before === 'string') {
      const d = new Date(before);
      if (!isNaN(d.getTime()) && d.getTime() > 0 && d <= new Date()) {
        where.createdAt = { lt: d };
      }
    }

    const posts = await prisma.message.findMany({
      where,
      include: {
        user: { select: { id: true, username: true, hue: true, avatar: true } },
        attachments: true,
        replyTo: {
          select: {
            id: true,
            text: true,
            user: { select: { username: true } },
          },
        },
      },
      orderBy: { createdAt: 'desc' },
      take,
    });

    res.json({ posts });
  } catch (err) {
    logger.error({ err }, 'GET /channels/:id/posts error');
    res.status(500).json({ error: 'Ошибка загрузки постов' });
  }
});

// ─── POST /:id/posts — создать пост (owner/admin) ───
router.post('/:id/posts', async (req, res) => {
  try {
    const userId = req.userId;
    const { id } = req.params;
    const { text } = req.body;

    if (!text?.trim()) {
      return res.status(400).json({ error: 'Текст поста обязателен' });
    }

    if (text.length > 4000) {
      return res.status(400).json({ error: 'Текст слишком длинный (макс 4000)' });
    }

    // Проверить права (owner или admin)
    const participant = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId: id, userId } },
    });

    if (!participant || !['owner', 'admin'].includes(participant.role)) {
      return res.status(403).json({ error: 'Только владелец или админ может публиковать' });
    }

    // Создать сообщение и инкрементировать счётчик
    const [message] = await prisma.$transaction([
      prisma.message.create({
        data: {
          roomId: id,
          userId,
          text: text.trim(),
          type: 'text',
        },
        include: {
          user: { select: { id: true, username: true, hue: true, avatar: true } },
          attachments: true,
        },
      }),
      prisma.channelMeta.update({
        where: { roomId: id },
        data: { postCount: { increment: 1 } },
      }),
    ]);

    // Отправить через сокет
    const io = req.app.locals.io;
    if (io) {
      io.to(id).emit('message:new', {
        id: message.id,
        chatId: id,
        userId: message.userId,
        username: message.user.username,
        hue: message.user.hue,
        avatar: message.user.avatar,
        text: message.text,
        type: message.type,
        attachments: message.attachments,
        createdAt: message.createdAt,
        isChannel: true,
      });
    }

    res.status(201).json({ message });
  } catch (err) {
    logger.error({ err }, 'POST /channels/:id/posts error');
    res.status(500).json({ error: 'Ошибка публикации поста' });
  }
});

// ─── DELETE /:id — удалить канал (только owner) ───
router.delete('/:id', async (req, res) => {
  try {
    const userId = req.userId;
    const { id } = req.params;

    const channel = await prisma.room.findUnique({ where: { id } });
    if (!channel || channel.type !== 'channel') {
      return res.status(404).json({ error: 'Канал не найден' });
    }
    if (channel.ownerId !== userId) {
      return res.status(403).json({ error: 'Только владелец может удалить канал' });
    }

    // Получить всех подписчиков ДО удаления
    const subscribers = await prisma.channelSubscriber.findMany({
      where: { channelId: id },
      select: { userId: true },
    });

    // Каскадное удаление: ChannelMeta и ChannelSubscriber удалятся через onDelete: Cascade
    // Сообщения и участники тоже нужно удалить
    await prisma.$transaction([
      prisma.attachment.deleteMany({
        where: { message: { roomId: id } },
      }),
      prisma.message.deleteMany({ where: { roomId: id } }),
      prisma.roomParticipant.deleteMany({ where: { roomId: id } }),
      prisma.channelSubscriber.deleteMany({ where: { channelId: id } }),
      prisma.channelMeta.deleteMany({ where: { roomId: id } }),
      prisma.room.delete({ where: { id } }),
    ]);

    // Уведомить всех подписчиков об удалении канала
    const io = req.app.locals.io;
    if (io) {
      io.to(id).emit('channel:deleted', { channelId: id });
      // Дополнительно уведомить каждого подписчика напрямую (на случай если уже покинули комнату)
      for (const sub of subscribers) {
        emitToUser(sub.userId, 'channel:deleted', { channelId: id });
      }
    }

    res.json({ deleted: true });
  } catch (err) {
    logger.error({ err }, 'DELETE /channels/:id error');
    res.status(500).json({ error: 'Ошибка удаления канала' });
  }
});

module.exports = router;
