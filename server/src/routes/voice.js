const { Router } = require('express');
const prisma = require('../db');
const { authenticate } = require('../middleware/auth');
const { voiceRooms, cleanupPeer } = require('../ws/voiceHandler');

const router = Router();

// Список голосовых комнат — только свои и те куда приглашён
router.get('/rooms', authenticate, async (req, res) => {
  try {
    const rooms = await prisma.room.findMany({
      where: {
        type: 'voice',
        OR: [
          { ownerId: req.userId },
          { participants: { some: { userId: req.userId } } },
        ],
      },
      include: {
        participants: {
          include: {
            user: { select: { id: true, username: true, hue: true } },
          },
        },
      },
      orderBy: { createdAt: 'desc' },
    });

    // Добавить live-данные из in-memory
    const result = rooms.map((room) => {
      const voiceRoom = voiceRooms.get(room.id);
      const liveParticipants = [];

      if (voiceRoom) {
        for (const [peerId, peer] of voiceRoom.peers) {
          liveParticipants.push({
            userId: peerId,
            username: peer.username,
            hue: peer.hue,
            muted: peer.muted,
          });
        }
      }

      // Приглашённые пользователи (из БД)
      const invited = room.participants.map((p) => ({
        userId: p.user.id,
        username: p.user.username,
        hue: p.user.hue,
        role: p.role,
      }));

      return {
        id: room.id,
        name: room.name,
        ownerId: room.ownerId,
        participantCount: liveParticipants.length,
        participants: liveParticipants,
        invited,
        createdAt: room.createdAt,
      };
    });

    res.json(result);
  } catch (err) {
    console.error('GET /api/voice/rooms error:', err);
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

    // Лимит: макс 3 комнаты на пользователя
    const userRoomCount = await prisma.room.count({
      where: { type: 'voice', ownerId: req.userId },
    });
    if (userRoomCount >= 3) {
      return res.status(400).json({ error: 'Максимум 3 голосовые комнаты' });
    }

    const room = await prisma.room.create({
      data: {
        name: name.trim(),
        type: 'voice',
        ownerId: req.userId,
        // Владелец автоматически — участник
        participants: {
          create: { userId: req.userId, role: 'owner' },
        },
      },
    });

    res.status(201).json({
      id: room.id,
      name: room.name,
      ownerId: room.ownerId,
      participantCount: 0,
      participants: [],
      invited: [],
      createdAt: room.createdAt,
    });
  } catch (err) {
    console.error('POST /api/voice/rooms error:', err);
    res.status(500).json({ error: 'Ошибка создания комнаты' });
  }
});

// Пригласить друга в голосовую комнату (только владелец)
router.post('/rooms/:id/invite', authenticate, async (req, res) => {
  try {
    const { userId: targetId } = req.body;
    if (!targetId) return res.status(400).json({ error: 'Укажите userId' });

    const room = await prisma.room.findUnique({
      where: { id: req.params.id },
    });

    if (!room || room.type !== 'voice') {
      return res.status(404).json({ error: 'Комната не найдена' });
    }
    if (room.ownerId !== req.userId) {
      return res.status(403).json({ error: 'Только владелец может приглашать' });
    }

    // Проверить что целевой пользователь — друг
    const friendship = await prisma.friendRequest.findFirst({
      where: {
        status: 'accepted',
        OR: [
          { senderId: req.userId, receiverId: targetId },
          { senderId: targetId, receiverId: req.userId },
        ],
      },
    });
    if (!friendship) {
      return res.status(400).json({ error: 'Можно приглашать только друзей' });
    }

    // Проверить что ещё не приглашён
    const existing = await prisma.roomParticipant.findUnique({
      where: { roomId_userId: { roomId: room.id, userId: targetId } },
    });
    if (existing) {
      return res.status(400).json({ error: 'Уже приглашён' });
    }

    // Добавить как участника
    await prisma.roomParticipant.create({
      data: { roomId: room.id, userId: targetId, role: 'member' },
    });

    // Данные приглашённого
    const invitedUser = await prisma.user.findUnique({
      where: { id: targetId },
      select: { id: true, username: true, hue: true },
    });

    // Уведомление
    const owner = await prisma.user.findUnique({
      where: { id: req.userId },
      select: { username: true },
    });

    const notification = await prisma.notification.create({
      data: {
        userId: targetId,
        type: 'system',
        title: `${owner.username} пригласил вас в голосовую «${room.name}»`,
        body: 'Комната появилась в вашем списке',
        fromUserId: req.userId,
        roomId: room.id,
      },
      include: {
        fromUser: { select: { id: true, username: true, hue: true, avatar: true } },
      },
    });

    // Socket уведомление
    const io = req.app.locals.io;
    if (io) {
      for (const [, s] of io.sockets.sockets) {
        if (s.userId === targetId) {
          s.emit('notification:new', notification);
        }
      }
    }

    res.json({
      ok: true,
      user: {
        userId: invitedUser.id,
        username: invitedUser.username,
        hue: invitedUser.hue,
        role: 'member',
      },
    });
  } catch (err) {
    console.error('POST /api/voice/rooms/:id/invite error:', err);
    res.status(500).json({ error: 'Ошибка приглашения' });
  }
});

// Удалить приглашённого из комнаты (только владелец)
router.delete('/rooms/:id/kick/:userId', authenticate, async (req, res) => {
  try {
    const room = await prisma.room.findUnique({ where: { id: req.params.id } });
    if (!room || room.ownerId !== req.userId) {
      return res.status(403).json({ error: 'Нет доступа' });
    }

    const kickedUserId = req.params.userId;

    await prisma.roomParticipant.deleteMany({
      where: { roomId: room.id, userId: kickedUserId },
    });

    // Очистить mediasoup peer и отключить сокет из комнаты
    const voiceRoom = voiceRooms.get(room.id);
    if (voiceRoom && voiceRoom.peers.has(kickedUserId)) {
      const peer = voiceRoom.peers.get(kickedUserId);
      cleanupPeer(peer);
      voiceRoom.peers.delete(kickedUserId);

      // Уведомить кикнутого и остальных
      const io = req.app.locals.io;
      if (io) {
        for (const [, s] of io.sockets.sockets) {
          if (s.userId === kickedUserId) {
            s.emit('voice:kicked', { roomId: room.id });
            s.leave(`voice:${room.id}`);
          }
        }
        io.to(`voice:${room.id}`).emit('voice:user-left', { userId: kickedUserId });
      }

      // Очистить пустую комнату
      if (voiceRoom.peers.size === 0) {
        voiceRoom.router.close();
        voiceRooms.delete(room.id);
      }
    }

    res.json({ ok: true });
  } catch (err) {
    console.error('DELETE /api/voice/rooms/:id/kick error:', err);
    res.status(500).json({ error: 'Ошибка' });
  }
});

// Удалить голосовую комнату (только владелец)
router.delete('/rooms/:id', authenticate, async (req, res) => {
  try {
    const room = await prisma.room.findUnique({
      where: { id: req.params.id },
    });

    if (!room) {
      return res.status(404).json({ error: 'Комната не найдена' });
    }

    if (room.ownerId !== req.userId) {
      return res.status(403).json({ error: 'Только владелец может удалить комнату' });
    }

    // Очистить in-memory: отключить активных участников и закрыть router
    const voiceRoom = voiceRooms.get(room.id);
    if (voiceRoom) {
      // Уведомить всех в комнате что она удалена
      const io = req.app.locals.io;
      if (io) {
        io.to(`voice:${room.id}`).emit('voice:room-deleted', { roomId: room.id });
      }
      // Очистить ресурсы каждого пира
      for (const peer of voiceRoom.peers.values()) {
        cleanupPeer(peer);
      }
      // Закрыть mediasoup router
      try { voiceRoom.router.close(); } catch {}
      voiceRooms.delete(room.id);
    }

    // Удалить участников и комнату
    await prisma.roomParticipant.deleteMany({ where: { roomId: room.id } });
    await prisma.room.delete({ where: { id: req.params.id } });

    res.json({ ok: true });
  } catch (err) {
    console.error('DELETE /api/voice/rooms/:id error:', err);
    res.status(500).json({ error: 'Ошибка удаления комнаты' });
  }
});

module.exports = router;
