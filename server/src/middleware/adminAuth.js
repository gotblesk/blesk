const prisma = require('../db');
const logger = require('../utils/logger');

// Middleware: проверка роли
async function requireAdmin(req, res, next) {
  try {
    const user = await prisma.user.findUnique({
      where: { id: req.userId },
      select: { id: true, role: true, banned: true, username: true },
    });
    if (!user || user.banned) {
      return res.status(403).json({ error: 'Доступ запрещён' });
    }
    if (user.role !== 'admin') {
      return res.status(403).json({ error: 'Доступ запрещён' });
    }
    req.adminUser = user;
    next();
  } catch (err) {
    logger.error({ err: err.message }, 'adminAuth error');
    res.status(500).json({ error: 'Ошибка сервера' });
  }
}

// Аудит-лог
async function logAdminAction(adminId, action, targetType, targetId, details = null) {
  try {
    await prisma.auditLog.create({
      data: { adminId, action, targetType, targetId: String(targetId), details },
    });
  } catch (err) {
    logger.error({ err: err.message }, 'Ошибка записи аудит-лога');
  }
}

module.exports = { requireAdmin, logAdminAction };
