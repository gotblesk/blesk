const { Router } = require('express');
const { authenticate } = require('../middleware/auth');
const logger = require('../utils/logger');

const router = Router();

const TENOR_API_KEY = process.env.TENOR_API_KEY || 'AIzaSyBqwDKjwi9kGJGUUBpxJ7v0Qi9HPVVn8hg';
const TENOR_BASE = 'https://tenor.googleapis.com/v2';

// GET /api/gif/search?q=...&limit=20
router.get('/search', authenticate, async (req, res) => {
  try {
    const { q, limit = 20 } = req.query;
    if (!q || typeof q !== 'string' || !q.trim()) {
      return res.status(400).json({ error: 'Параметр q обязателен' });
    }
    const safeLimit = Math.min(50, Math.max(1, parseInt(limit) || 20));

    const url = `${TENOR_BASE}/search?q=${encodeURIComponent(q)}&key=${TENOR_API_KEY}&limit=${safeLimit}&media_filter=tinygif,gif`;
    const response = await fetch(url);
    if (!response.ok) {
      return res.status(502).json({ error: 'Tenor API недоступен' });
    }
    const data = await response.json();
    res.json(data);
  } catch (err) {
    logger.error({ err }, 'gif search error');
    res.status(500).json({ error: 'Ошибка поиска GIF' });
  }
});

// GET /api/gif/featured?limit=20
router.get('/featured', authenticate, async (req, res) => {
  try {
    const { limit = 20 } = req.query;
    const safeLimit = Math.min(50, Math.max(1, parseInt(limit) || 20));

    const url = `${TENOR_BASE}/featured?key=${TENOR_API_KEY}&limit=${safeLimit}&media_filter=tinygif,gif`;
    const response = await fetch(url);
    if (!response.ok) {
      return res.status(502).json({ error: 'Tenor API недоступен' });
    }
    const data = await response.json();
    res.json(data);
  } catch (err) {
    logger.error({ err }, 'gif featured error');
    res.status(500).json({ error: 'Ошибка загрузки GIF' });
  }
});

module.exports = router;
