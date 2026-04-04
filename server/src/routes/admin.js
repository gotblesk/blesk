const { Router } = require('express');
const crypto = require('crypto');
const prisma = require('../db');
const { authenticate, invalidateBanCache } = require('../middleware/auth');
const { requireAdmin, logAdminAction } = require('../middleware/adminAuth');
const { findUserSockets, emitToUserAll, getUserSocketsMap } = require('../utils/socketUtils');

const router = Router();

const ADMIN_SECRET = process.env.ADMIN_SECRET;
const pkg = require('../../package.json');
const logger = require('../utils/logger');

// [P5] Кеш статистики — TTL 5 секунд
let _statsCache = null;
let _statsCacheAt = 0;
const STATS_CACHE_TTL = 5000;

// ─── GET /stats ───
router.get('/stats', authenticate, requireAdmin, async (req, res) => {
  try {
    // Отдать кешированный результат если он свежее 5 секунд
    if (_statsCache && Date.now() - _statsCacheAt < STATS_CACHE_TTL) {
      return res.json(_statsCache);
    }

    const startOfDay = new Date();
    startOfDay.setHours(0, 0, 0, 0);

    // Подсчёт онлайн юзеров через userSockets Map (O(1))
    const userSocketsMap = getUserSocketsMap();
    const onlineUserIds = userSocketsMap ? new Set(userSocketsMap.keys()) : new Set();

    const [usersTotal, messagesToday, messagesTotal, channelsTotal, reportsNew, feedbackNew] =
      await Promise.all([
        prisma.user.count(),
        prisma.message.count({ where: { createdAt: { gte: startOfDay } } }),
        prisma.message.count(),
        prisma.room.count({ where: { type: 'channel' } }),
        prisma.report.count({ where: { status: 'new' } }),
        prisma.feedback.count({ where: { status: 'new' } }),
      ]);

    const statsResult = {
      usersTotal,
      usersOnline: onlineUserIds.size,
      messagesToday,
      messagesTotal,
      channelsTotal,
      reportsNew,
      feedbackNew,
    };

    // Сохранить в кеш
    _statsCache = statsResult;
    _statsCacheAt = Date.now();

    res.json(statsResult);
  } catch (err) {
    logger.error({ err }, 'stats error');
    res.status(500).json({ error: 'Ошибка получения статистики' });
  }
});

// ─── GET /users ───
router.get('/users', authenticate, requireAdmin, async (req, res) => {
  try {
    let { page = 1, limit = 50, search, role, banned, sort = 'createdAt', order = 'desc' } = req.query;
    page = Math.max(1, parseInt(page) || 1);
    limit = Math.min(100, Math.max(1, parseInt(limit) || 50));
    const skip = (page - 1) * limit;

    if (search && search.length > 100) {
      return res.status(400).json({ error: 'Слишком длинный поиск' });
    }

    const where = {};
    if (search) where.username = { contains: search, mode: 'insensitive' };
    if (role) where.role = role;
    if (banned !== undefined) where.banned = banned === 'true';

    const allowedSort = ['createdAt', 'username', 'bleskCoins'];
    const orderField = allowedSort.includes(sort) ? sort : 'createdAt';
    const orderDir = order === 'asc' ? 'asc' : 'desc';

    const [users, total] = await Promise.all([
      prisma.user.findMany({
        where,
        select: {
          id: true, username: true, tag: true, email: true,
          role: true, banned: true, bannedReason: true,
          verifiedLevel: true, avatar: true, hue: true,
          status: true, bleskCoins: true, createdAt: true,
        },
        orderBy: { [orderField]: orderDir },
        skip,
        take: limit,
      }),
      prisma.user.count({ where }),
    ]);

    res.json({ users, total, page, pages: Math.ceil(total / limit) });
  } catch (err) {
    logger.error({ err }, 'users error');
    res.status(500).json({ error: 'Ошибка получения списка' });
  }
});

// ─── GET /users/:id ───
router.get('/users/:id', authenticate, requireAdmin, async (req, res) => {
  try {
    const user = await prisma.user.findUnique({
      where: { id: req.params.id },
      select: {
        id: true, username: true, tag: true, email: true, emailVerified: true,
        role: true, banned: true, bannedAt: true, bannedReason: true, bannedBy: true,
        verifiedLevel: true, avatar: true, bio: true, hue: true,
        status: true, customStatus: true, bleskCoins: true,
        lastSeenAt: true, showLastSeen: true, createdAt: true,
        userTags: { include: { tag: true } },
        _count: { select: { sentMessages: true, participations: true } },
      },
    });
    if (!user) return res.status(404).json({ error: 'Пользователь не найден' });
    res.json(user);
  } catch (err) {
    logger.error({ err }, 'users/:id error');
    res.status(500).json({ error: 'Ошибка получения пользователя' });
  }
});

// ─── PATCH /users/:id ───
router.patch('/users/:id', authenticate, requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    const allowed = ['tag', 'role', 'bio', 'verifiedLevel', 'username'];
    const changes = {};

    for (const key of allowed) {
      if (req.body[key] !== undefined) changes[key] = req.body[key];
    }

    if (Object.keys(changes).length === 0) {
      return res.status(400).json({ error: 'Нет данных для обновления' });
    }

    // Валидация
    if (changes.tag && !/^#\d{4}$/.test(changes.tag)) {
      return res.status(400).json({ error: 'Тег: формат #0000–#9999' });
    }
    if (changes.role && !['user', 'moderator', 'admin'].includes(changes.role)) {
      return res.status(400).json({ error: 'Роль: user, moderator или admin' });
    }
    if (changes.verifiedLevel !== undefined) {
      changes.verifiedLevel = parseInt(changes.verifiedLevel);
      if (isNaN(changes.verifiedLevel) || changes.verifiedLevel < 0 || changes.verifiedLevel > 3) {
        return res.status(400).json({ error: 'verifiedLevel: 0–3' });
      }
    }

    const existing = await prisma.user.findUnique({ where: { id } });
    if (!existing) return res.status(404).json({ error: 'Пользователь не найден' });

    // [S3] Нельзя менять свою собственную роль
    if (changes.role && id === req.adminUser.id) {
      return res.status(403).json({ error: 'Нельзя изменить свою роль' });
    }

    // Нельзя менять роль другого админа (защита от эскалации привилегий)
    if (changes.role && existing.role === 'admin' && id !== req.adminUser.id) {
      return res.status(403).json({ error: 'Нельзя менять роль другого администратора' });
    }

    if (changes.username) {
      const dup = await prisma.user.findFirst({ where: { username: changes.username, NOT: { id } } });
      if (dup) return res.status(409).json({ error: 'Имя пользователя занято' });
    }

    const user = await prisma.user.update({ where: { id }, data: changes });
    await logAdminAction(req.adminUser.id, 'user.edit', 'user', id, { changes });

    res.json({ ok: true, user: { id: user.id, username: user.username, tag: user.tag, role: user.role } });
  } catch (err) {
    logger.error({ err }, 'users/:id patch error');
    res.status(500).json({ error: 'Ошибка обновления' });
  }
});

// ─── POST /users/:id/ban ─── забанить ───
router.post('/users/:id/ban', authenticate, requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    const { reason } = req.body;
    if (!reason) return res.status(400).json({ error: 'Укажите причину бана' });

    // [S25] Нельзя забанить себя
    if (id === req.adminUser.id) {
      return res.status(403).json({ error: 'Нельзя забанить себя' });
    }

    const existing = await prisma.user.findUnique({ where: { id } });
    if (!existing) return res.status(404).json({ error: 'Пользователь не найден' });
    if (existing.role === 'admin') return res.status(400).json({ error: 'Действие запрещено' });

    await prisma.user.update({
      where: { id },
      data: { banned: true, bannedAt: new Date(), bannedReason: reason, bannedBy: req.adminUser.id },
    });

    // Инвалидировать кеш бана — бан применяется мгновенно
    invalidateBanCache(id);

    // Дисконнект всех сокетов забаненного юзера
    for (const s of findUserSockets(id)) {
      s.emit('auth:banned', { reason });
      s.disconnect(true);
    }

    await logAdminAction(req.adminUser.id, 'user.ban', 'user', id, { reason });
    res.json({ ok: true });
  } catch (err) {
    logger.error({ err }, 'ban error');
    res.status(500).json({ error: 'Ошибка бана' });
  }
});

// ─── POST /users/:id/unban ─── разбанить ───
router.post('/users/:id/unban', authenticate, requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    const existing = await prisma.user.findUnique({ where: { id } });
    if (!existing) return res.status(404).json({ error: 'Пользователь не найден' });

    await prisma.user.update({
      where: { id },
      data: { banned: false, bannedAt: null, bannedReason: null, bannedBy: null },
    });

    // Инвалидировать кеш бана — разбан применяется мгновенно
    invalidateBanCache(id);

    await logAdminAction(req.adminUser.id, 'user.unban', 'user', id);
    res.json({ ok: true });
  } catch (err) {
    logger.error({ err }, 'unban error');
    res.status(500).json({ error: 'Ошибка разбана' });
  }
});

// ─── POST /users/:id/tags ─── выдать тег юзеру ───
router.post('/users/:id/tags', authenticate, requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    const { tagId } = req.body;
    if (!tagId) return res.status(400).json({ error: 'Укажите tagId' });

    const [user, tag] = await Promise.all([
      prisma.user.findUnique({ where: { id } }),
      prisma.tag.findUnique({ where: { id: tagId } }),
    ]);
    if (!user) return res.status(404).json({ error: 'Пользователь не найден' });
    if (!tag) return res.status(404).json({ error: 'Тег не найден' });

    // Проверить дубликат
    const exists = await prisma.userTag.findUnique({ where: { userId_tagId: { userId: id, tagId } } });
    if (exists) return res.status(409).json({ error: 'Тег уже выдан' });

    const userTag = await prisma.userTag.create({
      data: { userId: id, tagId, grantedBy: req.adminUser.id },
      include: { tag: true },
    });

    await logAdminAction(req.adminUser.id, 'user.tag.grant', 'user', id, { tagId, tagName: tag.name });
    res.json({ ok: true, userTag });
  } catch (err) {
    logger.error({ err }, 'tag grant error');
    res.status(500).json({ error: 'Ошибка выдачи тега' });
  }
});

// ─── DELETE /users/:id/tags/:tagId ─── забрать тег ───
router.delete('/users/:id/tags/:tagId', authenticate, requireAdmin, async (req, res) => {
  try {
    const { id, tagId } = req.params;

    const userTag = await prisma.userTag.findUnique({ where: { userId_tagId: { userId: id, tagId } } });
    if (!userTag) return res.status(404).json({ error: 'Тег не найден у пользователя' });

    await prisma.userTag.delete({ where: { id: userTag.id } });

    await logAdminAction(req.adminUser.id, 'user.tag.revoke', 'user', id, { tagId });
    res.json({ ok: true });
  } catch (err) {
    logger.error({ err }, 'tag revoke error');
    res.status(500).json({ error: 'Ошибка удаления тега' });
  }
});

// ═══ Теги ═══

// ─── GET /tags ─── все теги ───
router.get('/tags', authenticate, requireAdmin, async (req, res) => {
  try {
    const tags = await prisma.tag.findMany({
      include: { _count: { select: { userTags: true } } },
      orderBy: { createdAt: 'desc' },
    });
    res.json({ tags });
  } catch (err) {
    logger.error({ err }, 'tags error');
    res.status(500).json({ error: 'Ошибка получения тегов' });
  }
});

// ─── POST /tags ─── создать тег ───
router.post('/tags', authenticate, requireAdmin, async (req, res) => {
  try {
    const { name, color, type, rarity, description, icon } = req.body;
    if (!name || !name.trim()) return res.status(400).json({ error: 'Укажите название тега' });

    // Валидация hex-цвета
    if (color && !/^#[0-9a-fA-F]{6}$/.test(color)) {
      return res.status(400).json({ error: 'Цвет: формат #RRGGBB' });
    }

    const existing = await prisma.tag.findUnique({ where: { name: name.trim() } });
    if (existing) return res.status(409).json({ error: 'Тег с таким именем уже существует' });

    const validTypes = ['system', 'achievement', 'seasonal', 'community'];
    const validRarities = ['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic'];

    const tag = await prisma.tag.create({
      data: {
        name: name.trim(),
        color: color || '#c8ff00',
        type: validTypes.includes(type) ? type : 'system',
        rarity: validRarities.includes(rarity) ? rarity : 'common',
        description: description || '',
        icon: icon || null,
      },
    });

    await logAdminAction(req.adminUser.id, 'tag.create', 'tag', tag.id, { name: tag.name });
    res.status(201).json({ ok: true, tag });
  } catch (err) {
    logger.error({ err }, 'tags create error');
    res.status(500).json({ error: 'Ошибка создания тега' });
  }
});

// ─── PATCH /tags/:id ─── редактировать тег ───
router.patch('/tags/:id', authenticate, requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    const allowed = ['name', 'color', 'type', 'rarity', 'description', 'icon'];
    const changes = {};
    for (const key of allowed) {
      if (req.body[key] !== undefined) changes[key] = req.body[key];
    }
    if (Object.keys(changes).length === 0) {
      return res.status(400).json({ error: 'Нет данных для обновления' });
    }

    if (changes.color && !/^#[0-9a-fA-F]{6}$/.test(changes.color)) {
      return res.status(400).json({ error: 'Цвет: формат #RRGGBB' });
    }

    const existing = await prisma.tag.findUnique({ where: { id } });
    if (!existing) return res.status(404).json({ error: 'Тег не найден' });

    if (changes.name) {
      const dupTag = await prisma.tag.findFirst({ where: { name: changes.name, NOT: { id } } });
      if (dupTag) return res.status(409).json({ error: 'Тег с таким именем уже существует' });
    }

    const tag = await prisma.tag.update({ where: { id }, data: changes });
    await logAdminAction(req.adminUser.id, 'tag.edit', 'tag', id, { changes });
    res.json({ ok: true, tag });
  } catch (err) {
    logger.error({ err }, 'tags edit error');
    res.status(500).json({ error: 'Ошибка обновления тега' });
  }
});

// ─── DELETE /tags/:id ─── удалить тег (каскадно UserTag) ───
router.delete('/tags/:id', authenticate, requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    const existing = await prisma.tag.findUnique({ where: { id } });
    if (!existing) return res.status(404).json({ error: 'Тег не найден' });

    await prisma.tag.delete({ where: { id } }); // каскадно удалит UserTag
    await logAdminAction(req.adminUser.id, 'tag.delete', 'tag', id, { name: existing.name });
    res.json({ ok: true });
  } catch (err) {
    logger.error({ err }, 'tags delete error');
    res.status(500).json({ error: 'Ошибка удаления тега' });
  }
});

// ═══ Жалобы (Reports) ═══

// ─── GET /reports ───
router.get('/reports', authenticate, requireAdmin, async (req, res) => {
  try {
    let { status, page = 1, limit = 50 } = req.query;
    page = Math.max(1, parseInt(page) || 1);
    limit = Math.min(100, Math.max(1, parseInt(limit) || 50));

    const where = {};
    if (status) where.status = status;

    const [reports, total] = await Promise.all([
      prisma.report.findMany({
        where,
        include: {
          reporter: { select: { id: true, username: true, tag: true, avatar: true } },
        },
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
      }),
      prisma.report.count({ where }),
    ]);

    res.json({ reports, total, page, pages: Math.ceil(total / limit) });
  } catch (err) {
    logger.error({ err }, 'reports error');
    res.status(500).json({ error: 'Ошибка получения жалоб' });
  }
});

// ─── PATCH /reports/:id ─── обработать жалобу ───
router.patch('/reports/:id', authenticate, requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    const { status } = req.body;
    const validStatuses = ['new', 'reviewed', 'resolved', 'rejected'];
    if (!status || !validStatuses.includes(status)) {
      return res.status(400).json({ error: 'Статус: new, reviewed, resolved или rejected' });
    }

    const existing = await prisma.report.findUnique({ where: { id } });
    if (!existing) return res.status(404).json({ error: 'Жалоба не найдена' });

    const data = { status };
    if (status === 'resolved' || status === 'rejected') {
      data.resolvedBy = req.adminUser.id;
    }

    const report = await prisma.report.update({ where: { id }, data });
    await logAdminAction(req.adminUser.id, 'report.update', 'report', id, { status });
    res.json({ ok: true, report });
  } catch (err) {
    logger.error({ err }, 'reports patch error');
    res.status(500).json({ error: 'Ошибка обработки жалобы' });
  }
});

// ═══ Сообщения ═══

// ─── DELETE /messages/:id ─── удалить сообщение ───
router.delete('/messages/:id', authenticate, requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    const message = await prisma.message.findUnique({ where: { id } });
    if (!message) return res.status(404).json({ error: 'Сообщение не найдено' });

    await prisma.message.delete({ where: { id } });

    // Уведомить комнату об удалении
    const io = req.app.locals.io;
    if (io) {
      io.to(message.roomId).emit('message:deleted', { messageId: id, roomId: message.roomId });
    }

    await logAdminAction(req.adminUser.id, 'message.delete', 'message', id, { roomId: message.roomId });
    res.json({ ok: true });
  } catch (err) {
    logger.error({ err }, 'messages delete error');
    res.status(500).json({ error: 'Ошибка удаления сообщения' });
  }
});

// ═══ Аудит-логи ═══

// ─── GET /logs ───
router.get('/logs', authenticate, requireAdmin, async (req, res) => {
  try {
    let { action, adminId, page = 1, limit = 50, from, to } = req.query;
    page = Math.max(1, parseInt(page) || 1);
    limit = Math.min(100, Math.max(1, parseInt(limit) || 50));

    const where = {};
    if (action) where.action = action;
    if (adminId) where.adminId = adminId;
    if (from || to) {
      where.createdAt = {};
      if (from) where.createdAt.gte = new Date(from);
      if (to) where.createdAt.lte = new Date(to);
    }

    const [logs, total] = await Promise.all([
      prisma.auditLog.findMany({
        where,
        include: { admin: { select: { id: true, username: true } } },
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
      }),
      prisma.auditLog.count({ where }),
    ]);

    res.json({ logs, total, page, pages: Math.ceil(total / limit) });
  } catch (err) {
    logger.error({ err }, 'logs error');
    res.status(500).json({ error: 'Ошибка получения логов' });
  }
});

// ═══ Обратная связь (Feedback) ═══

// ─── GET /feedback ───
router.get('/feedback', authenticate, requireAdmin, async (req, res) => {
  try {
    let { status, type, page = 1, limit = 50 } = req.query;
    page = Math.max(1, parseInt(page) || 1);
    limit = Math.min(100, Math.max(1, parseInt(limit) || 50));

    const where = {};
    if (status) where.status = status;
    if (type) where.type = type;

    const [feedbacks, total] = await Promise.all([
      prisma.feedback.findMany({
        where,
        include: { user: { select: { id: true, username: true, tag: true, avatar: true } } },
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
      }),
      prisma.feedback.count({ where }),
    ]);

    res.json({ feedbacks, total, page, pages: Math.ceil(total / limit) });
  } catch (err) {
    logger.error({ err }, 'feedback error');
    res.status(500).json({ error: 'Ошибка получения фидбека' });
  }
});

// ─── PATCH /feedback/:id ─── изменить статус ───
router.patch('/feedback/:id', authenticate, requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    const { status } = req.body;
    const validStatuses = ['new', 'reviewed', 'resolved'];
    if (!status || !validStatuses.includes(status)) {
      return res.status(400).json({ error: 'Статус: new, reviewed или resolved' });
    }

    const existing = await prisma.feedback.findUnique({ where: { id } });
    if (!existing) return res.status(404).json({ error: 'Фидбек не найден' });

    const feedback = await prisma.feedback.update({ where: { id }, data: { status } });
    await logAdminAction(req.adminUser.id, 'feedback.update', 'feedback', id, { status });
    res.json({ ok: true, feedback });
  } catch (err) {
    logger.error({ err }, 'feedback patch error');
    res.status(500).json({ error: 'Ошибка обновления фидбека' });
  }
});

// ═══ Каналы ═══

// ─── GET /channels ───
router.get('/channels', authenticate, requireAdmin, async (req, res) => {
  try {
    let { page = 1, limit = 50, search } = req.query;
    page = Math.max(1, parseInt(page) || 1);
    limit = Math.min(100, Math.max(1, parseInt(limit) || 50));

    if (search && search.length > 100) {
      return res.status(400).json({ error: 'Слишком длинный поиск' });
    }

    const where = { type: 'channel' };
    if (search) where.name = { contains: search, mode: 'insensitive' };

    const [channels, total] = await Promise.all([
      prisma.room.findMany({
        where,
        include: {
          owner: { select: { id: true, username: true, tag: true } },
          channelMeta: true,
        },
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
      }),
      prisma.room.count({ where }),
    ]);

    res.json({ channels, total, page, pages: Math.ceil(total / limit) });
  } catch (err) {
    logger.error({ err }, 'channels error');
    res.status(500).json({ error: 'Ошибка получения каналов' });
  }
});

// ─── DELETE /channels/:id ─── удалить канал каскадно ───
router.delete('/channels/:id', authenticate, requireAdmin, async (req, res) => {
  try {
    const { id } = req.params;
    const room = await prisma.room.findUnique({ where: { id } });
    if (!room) return res.status(404).json({ error: 'Канал не найден' });
    if (room.type !== 'channel') return res.status(400).json({ error: 'Это не канал' });

    // Каскадное удаление связанных данных
    await prisma.$transaction([
      prisma.attachment.deleteMany({ where: { message: { roomId: id } } }),
      prisma.channelSubscriber.deleteMany({ where: { channelId: id } }),
      prisma.channelMeta.deleteMany({ where: { roomId: id } }),
      prisma.message.deleteMany({ where: { roomId: id } }),
      prisma.roomParticipant.deleteMany({ where: { roomId: id } }),
      prisma.room.delete({ where: { id } }),
    ]);

    await logAdminAction(req.adminUser.id, 'channel.delete', 'channel', id, { name: room.name });
    res.json({ ok: true });
  } catch (err) {
    logger.error({ err }, 'channels delete error');
    res.status(500).json({ error: 'Ошибка удаления канала' });
  }
});

router.post('/broadcast-update', authenticate, requireAdmin, async (req, res) => {

  try {
    const { version, changelog } = req.body;
    if (!version) return res.status(400).json({ error: 'Укажите version' });

    const io = req.app.locals.io;
    if (!io) return res.status(500).json({ error: 'Socket.IO не инициализирован' });

    const userSocketsMap = getUserSocketsMap();
    const connectedUserIds = userSocketsMap ? [...userSocketsMap.keys()] : [];

    const notifData = connectedUserIds.map(uid => ({
      userId: uid,
      type: 'system',
      title: `Доступно обновление ${version}`,
      body: changelog || 'Доступна новая версия blesk. Перезапустите приложение для обновления.',
    }));
    await prisma.notification.createMany({ data: notifData });

    // [P8] Отправить socket-события чанками по 100 пользователей с задержкой 10ms
    const notifPayload = {
      type: 'system',
      title: `Доступно обновление ${version}`,
      body: changelog || 'Доступна новая версия blesk. Перезапустите приложение для обновления.',
    };
    const CHUNK_SIZE = 100;
    const CHUNK_DELAY_MS = 10;
    for (let i = 0; i < connectedUserIds.length; i += CHUNK_SIZE) {
      const chunk = connectedUserIds.slice(i, i + CHUNK_SIZE);
      if (i > 0) await new Promise(resolve => setTimeout(resolve, CHUNK_DELAY_MS));
      for (const uid of chunk) {
        emitToUserAll(uid, 'notification:new', notifPayload);
        emitToUserAll(uid, 'app:update-available', { version, changelog });
      }
    }

    await logAdminAction('system', 'broadcast.update', 'system', null, { version, notified: connectedUserIds.length });

    res.json({ ok: true, notified: connectedUserIds.length, version });
  } catch (err) {
    logger.error({ err }, 'broadcast-update error');
    res.status(500).json({ error: 'Ошибка рассылки' });
  }
});

// ═══ База данных (read-only) ═══

const DB_TABLES = ['users', 'rooms', 'messages', 'tags', 'feedback', 'reports', 'notifications'];

// ─── GET /db/tables ───
router.get('/db/tables', authenticate, requireAdmin, (req, res) => {
  res.json({ tables: DB_TABLES });
});

// Маппинг имён таблиц → Prisma модели
const TABLE_MAP = {
  users: 'user',
  rooms: 'room',
  messages: 'message',
  tags: 'tag',
  feedback: 'feedback',
  reports: 'report',
  notifications: 'notification',
};

// Поля, которые скрываем для каждой таблицы
const HIDDEN_FIELDS = {
  users: ['passwordHash', 'publicKey', 'email', 'phone'],
};

// ─── GET /db/:table ─── данные таблицы (read-only) ───
router.get('/db/:table', authenticate, requireAdmin, async (req, res) => {
  try {
    const { table } = req.params;
    if (!DB_TABLES.includes(table)) {
      return res.status(400).json({ error: 'Неизвестная таблица' });
    }

    let { page = 1, limit = 50 } = req.query;
    page = Math.max(1, parseInt(page) || 1);
    limit = Math.min(50, Math.max(1, parseInt(limit) || 50));

    const model = TABLE_MAP[table];
    const [rows, total] = await Promise.all([
      prisma[model].findMany({
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
      }),
      prisma[model].count(),
    ]);

    // Скрыть чувствительные поля
    const hidden = HIDDEN_FIELDS[table] || [];
    const cleanRows = rows.map((row) => {
      const clean = { ...row };
      for (const field of hidden) delete clean[field];
      return clean;
    });

    // Получить колонки из первой строки
    const columns = cleanRows.length > 0 ? Object.keys(cleanRows[0]) : [];

    res.json({ rows: cleanRows, total, columns, page, pages: Math.ceil(total / limit) });
  } catch (err) {
    logger.error({ err }, 'db error');
    res.status(500).json({ error: 'Ошибка чтения таблицы' });
  }
});

// ═══ Информация о сервере ═══

// ─── GET /server/config ───
router.get('/server/config', authenticate, requireAdmin, async (req, res) => {
  try {
    const io = req.app.locals.io;
    let dbStatus = 'connected';
    try {
      await prisma.$queryRaw`SELECT 1`;
    } catch {
      dbStatus = 'error';
    }

    res.json({
      version: pkg.version,
      uptime: Math.floor(process.uptime()),
      nodeVersion: process.version,
      connectedSockets: io ? io.sockets.sockets.size : 0,
      dbStatus,
    });
  } catch (err) {
    logger.error({ err }, 'server/config error');
    res.status(500).json({ error: 'Ошибка получения конфигурации' });
  }
});

module.exports = router;
