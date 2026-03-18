const { Router } = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const sharp = require('sharp');
const prisma = require('../db');
const { authenticate } = require('../middleware/auth');
const { validateFile } = require('../services/fileValidator');

const router = Router();

const attachDir = path.join(__dirname, '..', '..', 'uploads', 'attachments');
const thumbsDir = path.join(__dirname, '..', '..', 'uploads', 'thumbs');
if (!fs.existsSync(attachDir)) fs.mkdirSync(attachDir, { recursive: true });
if (!fs.existsSync(thumbsDir)) fs.mkdirSync(thumbsDir, { recursive: true });

const upload = multer({
  dest: attachDir,
  limits: { fileSize: 50 * 1024 * 1024, files: 5 },
});

// ─── Загрузка файла в канал (owner/admin) ───
router.post('/channels/:channelId/upload', authenticate, upload.single('file'), async (req, res) => {
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

    // Расширение из реального MIME, а не из user input
    const MIME_EXT = {
      'image/jpeg': '.jpg', 'image/png': '.png', 'image/gif': '.gif', 'image/webp': '.webp',
      'video/mp4': '.mp4', 'video/webm': '.webm', 'audio/mpeg': '.mp3', 'audio/ogg': '.ogg',
      'application/pdf': '.pdf', 'application/zip': '.zip', 'text/plain': '.txt',
      'application/msword': '.doc',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document': '.docx',
    };
    const ext = MIME_EXT[validation.mime] || '.bin';
    const storedName = `${crypto.randomUUID()}${ext}`;
    const finalPath = path.join(attachDir, storedName);
    fs.renameSync(req.file.path, finalPath);

    const url = `/uploads/attachments/${storedName}`;
    let thumbnailUrl = null;
    let width = null;
    let height = null;

    // Генерация превью для изображений
    if (validation.mime.startsWith('image/') && validation.mime !== 'image/gif') {
      try {
        const meta = await sharp(finalPath).metadata();
        width = meta.width;
        height = meta.height;
        const thumbName = `${path.basename(storedName, ext)}.jpg`;
        await sharp(finalPath)
          .resize({ width: 400, withoutEnlargement: true })
          .jpeg({ quality: 80 })
          .toFile(path.join(thumbsDir, thumbName));
        thumbnailUrl = `/uploads/thumbs/${thumbName}`;
      } catch { /* thumbnail failed, ok */ }
    }

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
              filename: req.file.originalname,
              storedName,
              mimeType: validation.mime,
              size: req.file.size,
              url,
              thumbnailUrl,
              width,
              height,
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
    if (req.file?.path && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
    console.error('channel upload error:', err);
    res.status(500).json({ error: 'Ошибка загрузки файла' });
  }
});

router.post('/:chatId/upload', authenticate, upload.single('file'), async (req, res) => {
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

    // Расширение из реального MIME, а не из user input
    const MIME_EXT = {
      'image/jpeg': '.jpg', 'image/png': '.png', 'image/gif': '.gif', 'image/webp': '.webp',
      'video/mp4': '.mp4', 'video/webm': '.webm', 'audio/mpeg': '.mp3', 'audio/ogg': '.ogg',
      'application/pdf': '.pdf', 'application/zip': '.zip', 'text/plain': '.txt',
      'application/msword': '.doc',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document': '.docx',
    };
    const ext = MIME_EXT[validation.mime] || '.bin';
    const storedName = `${crypto.randomUUID()}${ext}`;
    const finalPath = path.join(attachDir, storedName);
    fs.renameSync(req.file.path, finalPath);

    const url = `/uploads/attachments/${storedName}`;
    let thumbnailUrl = null;
    let width = null;
    let height = null;

    // Генерация превью для изображений
    if (validation.mime.startsWith('image/') && validation.mime !== 'image/gif') {
      try {
        const meta = await sharp(finalPath).metadata();
        width = meta.width;
        height = meta.height;
        const thumbName = `${path.basename(storedName, ext)}.jpg`;
        await sharp(finalPath)
          .resize({ width: 400, withoutEnlargement: true })
          .jpeg({ quality: 80 })
          .toFile(path.join(thumbsDir, thumbName));
        thumbnailUrl = `/uploads/thumbs/${thumbName}`;
      } catch { /* thumbnail failed, ok */ }
    }

    // Создание сообщения + вложения в одной транзакции
    const message = await prisma.message.create({
      data: {
        roomId: chatId,
        userId,
        text: text?.trim() || '',
        type: 'media',
        replyToId: replyToId || undefined,
        attachments: {
          create: [{
            filename: req.file.originalname,
            storedName,
            mimeType: validation.mime,
            size: req.file.size,
            url,
            thumbnailUrl,
            width,
            height,
          }],
        },
      },
      include: {
        user: { select: { id: true, username: true, hue: true, avatar: true } },
        attachments: true,
        replyTo: { include: { user: { select: { id: true, username: true } } } },
      },
    });

    // Отправка через сокет
    const io = req.app.locals.io;
    if (io) io.to(chatId).emit('message:new', message);

    res.json({ message });
  } catch (err) {
    if (req.file?.path && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
    console.error('upload error:', err);
    res.status(500).json({ error: 'Ошибка загрузки файла' });
  }
});

module.exports = router;
