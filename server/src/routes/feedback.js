const { Router } = require('express');
const prisma = require('../db');
const { authenticate } = require('../middleware/auth');

const router = Router();

// POST /api/feedback — создать обратную связь
router.post('/', authenticate, async (req, res) => {
  try {
    const { type, text, appVersion, osInfo } = req.body;

    // Валидация типа
    const validTypes = ['bug', 'suggestion', 'question'];
    if (!validTypes.includes(type)) {
      return res.status(400).json({ error: 'Неверный тип обратной связи' });
    }

    // Валидация текста
    if (!text || text.trim().length === 0) {
      return res.status(400).json({ error: 'Текст не может быть пустым' });
    }

    if (text.length > 2000) {
      return res.status(400).json({ error: 'Текст слишком длинный (макс. 2000 символов)' });
    }

    const feedback = await prisma.feedback.create({
      data: {
        userId: req.userId,
        type,
        text: text.trim(),
        appVersion: appVersion || 'unknown',
        osInfo: osInfo || 'unknown',
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
