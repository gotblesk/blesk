const fileType = require('file-type');

const ALLOWED_MIME = new Set([
  'image/jpeg', 'image/png', 'image/gif', 'image/webp',
  'application/pdf', 'application/zip', 'application/x-zip-compressed',
  'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'text/plain', 'video/mp4', 'video/webm', 'audio/mpeg', 'audio/ogg',
]);

const BLOCKED_EXT = new Set([
  '.exe', '.bat', '.cmd', '.sh', '.ps1', '.msi', '.dll', '.com', '.vbs', '.jar', '.scr',
]);

const SIZE_LIMITS = {
  image: 10 * 1024 * 1024,
  video: 50 * 1024 * 1024,
  audio: 20 * 1024 * 1024,
  default: 25 * 1024 * 1024,
};

async function validateFile(filePath, originalname, mimetype, size) {
  const path = require('path');
  const ext = path.extname(originalname).toLowerCase();

  if (BLOCKED_EXT.has(ext)) return { ok: false, error: 'Этот тип файла запрещён' };

  const fs = require('fs');
  const buffer = Buffer.alloc(4100);
  const fd = fs.openSync(filePath, 'r');
  fs.readSync(fd, buffer, 0, 4100, 0);
  fs.closeSync(fd);

  const detected = await fileType.fromBuffer(buffer);
  const actualMime = detected?.mime || mimetype;

  if (!ALLOWED_MIME.has(actualMime)) return { ok: false, error: 'Формат файла не поддерживается' };

  const category = actualMime.startsWith('image/') ? 'image'
    : actualMime.startsWith('video/') ? 'video'
    : actualMime.startsWith('audio/') ? 'audio' : 'default';

  if (size > SIZE_LIMITS[category]) {
    const maxMB = Math.round(SIZE_LIMITS[category] / (1024 * 1024));
    return { ok: false, error: `Файл слишком большой (макс. ${maxMB} МБ)` };
  }

  return { ok: true, mime: actualMime };
}

module.exports = { validateFile, ALLOWED_MIME };
