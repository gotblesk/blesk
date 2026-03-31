const { Router } = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const sharp = require('sharp');
const rateLimit = require('express-rate-limit');
const prisma = require('../db');
const { authenticate } = require('../middleware/auth');
const { validateFile, sanitizeFilename } = require('../services/fileValidator');
const { scanFile } = require('../services/fileScanner');
const logger = require('../utils/logger');

const router = Router();

const uploadLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 20,
  message: { error: 'Слишком много загрузок.' },
});

const attachDir = path.join(__dirname, '..', '..', 'uploads', 'attachments');
const thumbsDir = path.join(__dirname, '..', '..', 'uploads', 'thumbs');
if (!fs.existsSync(attachDir)) fs.mkdirSync(attachDir, { recursive: true });
if (!fs.existsSync(thumbsDir)) fs.mkdirSync(thumbsDir, { recursive: true });

const upload = multer({
  dest: attachDir,
  limits: { fileSize: 50 * 1024 * 1024, files: 5 },
});

// [HIGH-3] Единая карта MIME → расширение (вместо дублирования)
const MIME_EXT = {
  'image/jpeg': '.jpg', 'image/png': '.png', 'image/gif': '.gif', 'image/webp': '.webp',
  'video/mp4': '.mp4', 'video/webm': '.webm',
  'audio/mpeg': '.mp3', 'audio/ogg': '.ogg', 'audio/webm': '.webm', // [CRIT-2]
  'application/pdf': '.pdf', 'application/zip': '.zip', 'text/plain': '.txt',
  'application/msword': '.doc',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': '.docx',
};

/**
 * [HIGH-1] Обработка изображений — EXIF stripping + thumbnail
 * @returns {{ url, thumbnailUrl, width, height }}
 */
async function processImage(filePath, storedName, mime) {
  const ext = MIME_EXT[mime] || '.bin';
  const url = `/uploads/attachments/${storedName}`;
  let thumbnailUrl = null;
  let width = null;
  let height = null;

  if (!mime.startsWith('image/')) return { url, thumbnailUrl, width, height };

  // [HIGH-1] Стрип EXIF/GPS metadata с оригинала (кроме GIF — sharp ломает анимацию)
  if (mime !== 'image/gif') {
    try {
      const stripped = filePath + '.stripped';
      await sharp(filePath).withMetadata(false).toFile(stripped);
      fs.renameSync(stripped, filePath);
    } catch { /* если sharp не справился — оставить как есть */ }
  }

  // Генерация превью (только для не-GIF)
  if (mime !== 'image/gif') {
    try {
      const meta = await sharp(filePath).metadata();
      width = meta.width;
      height = meta.height;
      const thumbName = `${path.basename(storedName, ext)}.jpg`;
      await sharp(filePath)
        .resize({ width: 400, withoutEnlargement: true })
        .jpeg({ quality: 80 })
        .toFile(path.join(thumbsDir, thumbName));
      thumbnailUrl = `/uploads/thumbs/${thumbName}`;
    } catch { /* thumbnail failed, ok */ }
  }

  return { url, thumbnailUrl, width, height };
}

// ─── Загрузка файла в канал (owner/admin) ───
router.post('/channels/:channelId/upload', uploadLimiter, authenticate, upload.single('file'), async (req, res) => {
  let finalPath = null;
  try {
    const { channelId } = req.params;
    const { text } = req.body;
    const userId = req.userId;

    if (!req.file) return res.status(400).json({ error: 'Файл не загружен' });

    // Проверить права (owner или admin канала)
    const participant = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId: channelId, userId } },
    });
    if (!participant || !['owner', 'admin'].includes(participant.role)) {
      fs.unlinkSync(req.file.path);
      return res.status(403).json({ error: 'Только владелец или админ может загружать файлы' });
    }

    // Валидация файла
    const validation = await validateFile(req.file.path, req.file.originalname, req.file.mimetype, req.file.size);
    if (!validation.ok) {
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: validation.error });
    }

    const ext = MIME_EXT[validation.mime] || '.bin';
    const storedName = `${crypto.randomUUID()}${ext}`;
    finalPath = path.join(attachDir, storedName);
    fs.renameSync(req.file.path, finalPath);

    // ClamAV антивирусная проверка (graceful — если недоступен, пропускает)
    const scanResult = await scanFile(finalPath);
    if (!scanResult.clean) {
      fs.unlinkSync(finalPath);
      finalPath = null;
      return res.status(400).json({ error: 'Файл заблокирован: обнаружена угроза' });
    }

    // Обработка изображений (EXIF strip + thumbnail)
    const media = await processImage(finalPath, storedName, validation.mime);

    // Создание сообщения + вложения + инкремент postCount
    const [message] = await prisma.$transaction([
      prisma.message.create({
        data: {
          roomId: channelId,
          userId,
          text: text?.trim() || '',
          type: 'media',
          attachments: {
            create: [{
              filename: sanitizeFilename(req.file.originalname),
              storedName,
              mimeType: validation.mime,
              size: req.file.size,
              url: media.url,
              thumbnailUrl: media.thumbnailUrl,
              width: media.width,
              height: media.height,
            }],
          },
        },
        include: {
          user: { select: { id: true, username: true, hue: true, avatar: true } },
          attachments: true,
        },
      }),
      prisma.channelMeta.update({
        where: { roomId: channelId },
        data: { postCount: { increment: 1 } },
      }),
    ]);

    // Отправка через сокет с флагом isChannel
    const io = req.app.locals.io;
    if (io) {
      io.to(channelId).emit('message:new', {
        ...message,
        chatId: channelId,
        isChannel: true,
      });
    }

    res.json({ message });
  } catch (err) {
    // [MED-3] Очистить оба файла при ошибке
    if (req.file?.path && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
    if (finalPath && fs.existsSync(finalPath)) fs.unlinkSync(finalPath);
    logger.error({ err }, 'channel upload error');
    res.status(500).json({ error: 'Ошибка загрузки файла' });
  }
});

router.post('/:chatId/upload', uploadLimiter, authenticate, upload.single('file'), async (req, res) => {
  let finalPath = null;
  try {
    const { chatId } = req.params;
    const { text, replyToId } = req.body;
    const userId = req.userId;

    if (!req.file) return res.status(400).json({ error: 'Файл не загружен' });

    // Проверка участия в чате
    const participant = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId: chatId, userId } },
    });
    if (!participant) {
      fs.unlinkSync(req.file.path);
      return res.status(403).json({ error: 'Вы не участник этого чата' });
    }

    // Валидация файла
    const validation = await validateFile(req.file.path, req.file.originalname, req.file.mimetype, req.file.size);
    if (!validation.ok) {
      fs.unlinkSync(req.file.path);
      return res.status(400).json({ error: validation.error });
    }

    const ext = MIME_EXT[validation.mime] || '.bin';
    const storedName = `${crypto.randomUUID()}${ext}`;
    finalPath = path.join(attachDir, storedName);
    fs.renameSync(req.file.path, finalPath);

    // ClamAV антивирусная проверка (graceful — если недоступен, пропускает)
    const scanResult = await scanFile(finalPath);
    if (!scanResult.clean) {
      fs.unlinkSync(finalPath);
      finalPath = null;
      return res.status(400).json({ error: 'Файл заблокирован: обнаружена угроза' });
    }

    // Обработка изображений (EXIF strip + thumbnail)
    const media = await processImage(finalPath, storedName, validation.mime);

    // Валидация replyToId — должен быть из того же чата
    let validReplyToId = undefined;
    if (replyToId) {
      const replyMsg = await prisma.message.findUnique({
        where: { id: replyToId },
        select: { roomId: true },
      });
      if (replyMsg && replyMsg.roomId === chatId) {
        validReplyToId = replyToId;
      }
    }

    // Создание сообщения + вложения
    const message = await prisma.message.create({
      data: {
        roomId: chatId,
        userId,
        text: text?.trim() || '',
        type: 'media',
        replyToId: validReplyToId,
        attachments: {
          create: [{
            filename: sanitizeFilename(req.file.originalname),
            storedName,
            mimeType: validation.mime,
            size: req.file.size,
            url: media.url,
            thumbnailUrl: media.thumbnailUrl,
            width: media.width,
            height: media.height,
          }],
        },
      },
      include: {
        user: { select: { id: true, username: true, hue: true, avatar: true } },
        attachments: true,
        replyTo: { include: { user: { select: { id: true, username: true } } } },
      },
    });

    // Отправка через сокет (добавляем chatId — Prisma возвращает roomId, а клиент ожидает chatId)
    const io = req.app.locals.io;
    if (io) io.to(chatId).emit('message:new', { ...message, chatId });

    res.json({ message });
  } catch (err) {
    // [MED-3] Очистить оба файла при ошибке
    if (req.file?.path && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
    if (finalPath && fs.existsSync(finalPath)) fs.unlinkSync(finalPath);
    logger.error({ err }, 'upload error');
    res.status(500).json({ error: 'Ошибка загрузки файла' });
  }
});

// [CRIT-1] Авторизованное скачивание файлов (вместо public static)
router.get('/attachments/:filename', authenticate, async (req, res) => {
  try {
    const filename = req.params.filename;
    if (!filename || typeof filename !== 'string') return res.status(400).end();
    // Защита от path traversal
    const safeName = path.basename(filename);
    if (safeName !== filename) return res.status(400).end();

    // Проверить что пользователь участник чата с этим вложением
    const attachment = await prisma.attachment.findFirst({
      where: { storedName: safeName },
      include: { message: { select: { roomId: true } } },
    });
    if (!attachment) return res.status(404).json({ error: 'Файл не найден' });

    const participant = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId: attachment.message.roomId, userId: req.userId } },
    });
    if (!participant) return res.status(403).json({ error: 'Нет доступа' });

    const filePath = path.join(attachDir, safeName);
    if (!fs.existsSync(filePath)) return res.status(404).json({ error: 'Файл не найден' });

    // [HIGH-4] Content-Disposition с оригинальным именем файла
    res.setHeader('Content-Disposition', `inline; filename="${encodeURIComponent(attachment.filename)}"`);
    res.setHeader('Content-Type', attachment.mimeType);
    res.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
    res.sendFile(filePath);
  } catch (err) {
    logger.error({ err }, 'attachment download error');
    res.status(500).json({ error: 'Ошибка скачивания' });
  }
});

// [IMP-5] Авторизованное скачивание thumbnails с room membership check
router.get('/thumbs/:filename', authenticate, async (req, res) => {
  try {
    const filename = req.params.filename;
    if (!filename || typeof filename !== 'string') return res.status(400).end();
    const safeName = path.basename(filename);
    if (safeName !== filename) return res.status(400).end();

    // Проверить что пользователь участник чата с этим thumbnail
    // Thumb name = UUID.jpg, attachment storedName = UUID.ext
    const uuidPart = safeName.replace(/\.[^.]+$/, '');
    const attachment = await prisma.attachment.findFirst({
      where: { storedName: { startsWith: uuidPart } },
      include: { message: { select: { roomId: true } } },
    });
    if (attachment) {
      const participant = await prisma.roomParticipant.findUnique({
        where: { roomId_userId: { roomId: attachment.message.roomId, userId: req.userId } },
      });
      if (!participant) {
        // Проверить подписку (для каналов)
        const sub = await prisma.channelSubscriber.findUnique({
          where: { channelId_userId: { channelId: attachment.message.roomId, userId: req.userId } },
        });
        if (!sub) return res.status(403).end();
      }
    }

    const filePath = path.join(thumbsDir, safeName);
    if (!fs.existsSync(filePath)) return res.status(404).end();

    res.setHeader('Content-Type', 'image/jpeg');
    res.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
    res.setHeader('Cache-Control', 'public, max-age=86400');
    res.sendFile(filePath);
  } catch {
    res.status(500).end();
  }
});

module.exports = router;
