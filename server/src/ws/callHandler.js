const { PrismaClient } = require('@prisma/client');

const prisma = new PrismaClient();

// Активные звонки: chatId → { callerId, callerSocketId, startedAt, participants: Set<userId>, timeout }
const activeCalls = new Map();

// Найти сокет пользователя по userId
function findUserSocket(io, targetUserId) {
  for (const [, s] of io.sockets.sockets) {
    if (s.userId === targetUserId) return s;
  }
  return null;
}

// Очистить звонок и удалить из Map
function cleanupCall(io, chatId) {
  const call = activeCalls.get(chatId);
  if (!call) return;

  if (call.timeout) {
    clearTimeout(call.timeout);
    call.timeout = null;
  }

  activeCalls.delete(chatId);
}

// Получить тип чата и участников
async function getChatInfo(chatId) {
  const room = await prisma.room.findUnique({
    where: { id: chatId },
    select: { id: true, name: true, type: true },
  });

  if (!room) return null;

  const participants = await prisma.roomParticipant.findMany({
    where: { roomId: chatId },
    select: { userId: true },
  });

  const type = room.type === 'chat' ? 'personal' : 'group';
  return { room, type, participantIds: participants.map((p) => p.userId) };
}

function callHandler(io, socket) {
  const userId = socket.userId;

  // ═══ Инициировать звонок ═══
  socket.on('call:initiate', async ({ chatId }) => {
    try {
      // Проверить что пользователь участник комнаты
      const participant = await prisma.roomParticipant.findUnique({
        where: { roomId_userId: { roomId: chatId, userId } },
      });
      if (!participant) {
        return socket.emit('call:error', { chatId, error: 'Вы не участник этого чата' });
      }

      // Проверить что нет активного звонка
      if (activeCalls.has(chatId)) {
        return socket.emit('call:error', { chatId, error: 'В этом чате уже идёт звонок' });
      }

      const chatInfo = await getChatInfo(chatId);
      if (!chatInfo) {
        return socket.emit('call:error', { chatId, error: 'Чат не найден' });
      }

      // Данные звонящего
      const caller = await prisma.user.findUnique({
        where: { id: userId },
        select: { username: true, hue: true },
      });

      // Создать запись активного звонка
      const call = {
        callerId: userId,
        callerSocketId: socket.id,
        startedAt: new Date(),
        participants: new Set(),
        timeout: null,
      };
      activeCalls.set(chatId, call);

      // Данные для входящего звонка
      const incomingData = {
        chatId,
        callerId: userId,
        callerName: caller.username,
        callerHue: caller.hue,
        type: chatInfo.type,
        chatName: chatInfo.room.name,
      };

      // Отправить входящий звонок другим участникам
      for (const participantId of chatInfo.participantIds) {
        if (participantId === userId) continue;
        const targetSocket = findUserSocket(io, participantId);
        if (targetSocket) {
          targetSocket.emit('call:incoming', incomingData);
        }
      }

      // Таймаут 30 секунд — если никто не принял
      call.timeout = setTimeout(() => {
        const activeCall = activeCalls.get(chatId);
        if (!activeCall || activeCall.participants.size > 0) return;

        // Уведомить звонящего о пропущенном
        const callerSocket = findUserSocket(io, activeCall.callerId);
        if (callerSocket) {
          callerSocket.emit('call:missed', { chatId });
        }

        // Уведомить остальных
        for (const participantId of chatInfo.participantIds) {
          if (participantId === activeCall.callerId) continue;
          const targetSocket = findUserSocket(io, participantId);
          if (targetSocket) {
            targetSocket.emit('call:missed', { chatId });
          }
        }

        cleanupCall(io, chatId);
      }, 30000);
    } catch (err) {
      console.error('call:initiate error:', err);
      socket.emit('call:error', { chatId, error: 'Ошибка инициализации звонка' });
    }
  });

  // ═══ Принять звонок ═══
  socket.on('call:accept', async ({ chatId }) => {
    try {
      const call = activeCalls.get(chatId);
      if (!call) {
        return socket.emit('call:error', { chatId, error: 'Звонок не найден' });
      }

      // Добавить в участники
      call.participants.add(userId);

      // Для личных чатов — сбросить таймаут при первом принятии
      if (call.timeout && call.participants.size === 1) {
        clearTimeout(call.timeout);
        call.timeout = null;
        // Добавить и звонящего в участники
        call.participants.add(call.callerId);
      }

      // Уведомить всех в чате
      const chatInfo = await getChatInfo(chatId);
      if (chatInfo) {
        for (const participantId of chatInfo.participantIds) {
          const targetSocket = findUserSocket(io, participantId);
          if (targetSocket) {
            targetSocket.emit('call:accepted', { chatId, userId });
          }
        }
      }

      // Клиент далее сделает voice:join('call:' + chatId) через mediasoup
    } catch (err) {
      console.error('call:accept error:', err);
      socket.emit('call:error', { chatId, error: 'Ошибка принятия звонка' });
    }
  });

  // ═══ Отклонить звонок ═══
  socket.on('call:decline', async ({ chatId }) => {
    try {
      const call = activeCalls.get(chatId);
      if (!call) return;

      const chatInfo = await getChatInfo(chatId);

      if (chatInfo && chatInfo.type === 'personal') {
        // Личный чат — уведомить звонящего и завершить
        const callerSocket = findUserSocket(io, call.callerId);
        if (callerSocket) {
          callerSocket.emit('call:declined', { chatId, userId });
        }
        cleanupCall(io, chatId);
      } else {
        // Группа — просто уведомить (остальные могут принять)
        const callerSocket = findUserSocket(io, call.callerId);
        if (callerSocket) {
          callerSocket.emit('call:declined', { chatId, userId });
        }
      }
    } catch (err) {
      console.error('call:decline error:', err);
    }
  });

  // ═══ Завершить звонок (выйти) ═══
  socket.on('call:end', async ({ chatId }) => {
    handleCallEnd(io, socket, userId, chatId);
  });

  // ═══ Отменить звонок (до принятия) ═══
  socket.on('call:cancel', async ({ chatId }) => {
    try {
      const call = activeCalls.get(chatId);
      if (!call) return;

      // Только звонящий может отменить
      if (call.callerId !== userId) return;

      const chatInfo = await getChatInfo(chatId);
      if (chatInfo) {
        for (const participantId of chatInfo.participantIds) {
          if (participantId === userId) continue;
          const targetSocket = findUserSocket(io, participantId);
          if (targetSocket) {
            targetSocket.emit('call:cancelled', { chatId });
          }
        }
      }

      cleanupCall(io, chatId);
    } catch (err) {
      console.error('call:cancel error:', err);
    }
  });

  // ═══ Отключение сокета — cleanup звонков ═══
  socket.on('disconnect', () => {
    for (const [chatId, call] of activeCalls) {
      if (call.participants.has(userId) || call.callerId === userId) {
        handleCallEnd(io, socket, userId, chatId);
      }
    }
  });
}

// Обработка выхода пользователя из звонка
async function handleCallEnd(io, socket, userId, chatId) {
  try {
    const call = activeCalls.get(chatId);
    if (!call) return;

    call.participants.delete(userId);

    // Уведомить остальных
    const chatInfo = await getChatInfo(chatId);
    if (chatInfo) {
      for (const participantId of chatInfo.participantIds) {
        if (participantId === userId) continue;
        const targetSocket = findUserSocket(io, participantId);
        if (targetSocket) {
          targetSocket.emit('call:user-left', { chatId, userId });
        }
      }
    }

    // Если больше никого нет — завершить звонок
    if (call.participants.size === 0) {
      if (chatInfo) {
        for (const participantId of chatInfo.participantIds) {
          const targetSocket = findUserSocket(io, participantId);
          if (targetSocket) {
            targetSocket.emit('call:ended', { chatId });
          }
        }
      }

      // Системное сообщение в БД о завершённом звонке
      try {
        const duration = Math.floor((Date.now() - call.startedAt.getTime()) / 1000);
        if (duration > 0) {
          await prisma.message.create({
            data: {
              roomId: chatId,
              userId: call.callerId,
              text: `Звонок завершён (${formatDuration(duration)})`,
              type: 'system',
            },
          });
        }
      } catch {
        // Не критично — если не удалось записать системное сообщение
      }

      cleanupCall(io, chatId);
    }
  } catch (err) {
    console.error('call:end error:', err);
  }
}

// Форматировать длительность звонка
function formatDuration(seconds) {
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  if (mins === 0) return `${secs} сек`;
  return `${mins} мин ${secs} сек`;
}

module.exports = { callHandler, activeCalls };
