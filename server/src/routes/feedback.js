const { Router } = require('express');
const prisma = require('../db');
const { authenticate } = require('../middleware/auth');

const router = Router();

// Санитизация текста — защита от XSS
function sanitizeText(str) {
  return str.replace(/[<>"'`&]/g, (ch) => ({
    '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#x27;', '`': '&#x60;', '&': '&amp;',
  }[ch]));
}

// POST /api/feedback — создать обратную связь
router.post('/', authenticate, async (req, res) => {
  try {
    const { type, text, appVersion, osInfo } = req.body;

    // Валидация типа
    const validTypes = ['bug', 'suggestion', 'question'];
    if (!validTypes.includes(type)) {
      return res.status(400).json({ error: 'Неверный тип обратной связи' });
    }

    // Валидация длины полей
    if (appVersion && appVersion.length > 50) {
      return res.status(400).json({ error: 'appVersion слишком длинный' });
    }
    if (osInfo && osInfo.length > 200) {
      return res.status(400).json({ error: 'osInfo слишком длинный' });
    }
    if (!text || text.trim().length === 0 || text.length > 5000) {
      return res.status(400).json({ error: 'Текст обязателен (макс 5000 символов)' });
    }

    const feedback = await prisma.feedback.create({
      data: {
        userId: req.userId,
        type,
        text: sanitizeText(text.trim()),
        appVersion: sanitizeText((appVersion || 'unknown').slice(0, 50)),
        osInfo: sanitizeText((osInfo || 'unknown').slice(0, 200)),
      },
    });

    res.status(201).json({ id: feedback.id });
  } catch (err) {
    console.error('Ошибка создания feedback:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

// GET /api/feedback — только свои отзывы (для пользователя)
router.get('/', authenticate, async (req, res) => {
  try {
    const feedbacks = await prisma.feedback.findMany({
      where: { userId: req.userId },
      orderBy: { createdAt: 'desc' },
      take: 50,
    });

    res.json({ feedbacks });
  } catch (err) {
    console.error('Ошибка получения feedback:', err);
    res.status(500).json({ error: 'Внутренняя ошибка сервера' });
  }
});

module.exports = router;
