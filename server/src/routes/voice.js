const { Router } = require('express');
const { PrismaClient } = require('@prisma/client');
const { authenticate } = require('../middleware/auth');
const { voiceRooms } = require('../ws/voiceHandler');

const prisma = new PrismaClient();
const router = Router();

// Список голосовых комнат с количеством участников
router.get('/rooms', authenticate, async (req, res) => {
  try {
    const rooms = await prisma.room.findMany({
      where: { type: 'voice' },
      orderBy: { createdAt: 'desc' },
    });

    // Добавить live-данные из in-memory
    const result = rooms.map((room) => {
      const voiceRoom = voiceRooms.get(room.id);
      const participants = [];

      if (voiceRoom) {
        for (const [peerId, peer] of voiceRoom.peers) {
          participants.push({
            userId: peerId,
            username: peer.username,
            hue: peer.hue,
            muted: peer.muted,
          });
        }
      }

      return {
        id: room.id,
        name: room.name,
        ownerId: room.ownerId,
        participantCount: participants.length,
        participants,
        createdAt: room.createdAt,
      };
    });

    res.json(result);
  } catch {
    res.status(500).json({ error: 'Ошибка получения комнат' });
  }
});

// Создать голосовую комнату
router.post('/rooms', authenticate, async (req, res) => {
  try {
    const { name } = req.body;

    if (!name || name.trim().length < 1 || name.length > 50) {
      return res.status(400).json({ error: 'Название от 1 до 50 символов' });
    }

    const room = await prisma.room.create({
      data: {
        name: name.trim(),
        type: 'voice',
        ownerId: req.userId,
      },
    });

    res.status(201).json({
      id: room.id,
      name: room.name,
      ownerId: room.ownerId,
      participantCount: 0,
      participants: [],
      createdAt: room.createdAt,
    });
  } catch {
    res.status(500).json({ error: 'Ошибка создания комнаты' });
  }
});

module.exports = router;
