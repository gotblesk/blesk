const prisma = require('../db');
// Активные звонки: chatId → { callerId, callerSocketId, startedAt, participants: Set<userId>, timeout }
const activeCalls = new Map();

// [HIGH-4] Найти сокет пользователя через глобальный userSockets Map (O(1))
// userSockets устанавливается из index.js при инициализации
let _userSockets = null;
function setUserSockets(map) { _userSockets = map; }

function findUserSocket(io, targetUserId) {
  // O(1) через глобальный Map
  if (_userSockets) {
    const sockets = _userSockets.get(targetUserId);
    if (sockets && sockets.size > 0) return sockets.values().next().value;
    return null;
  }
  // Fallback O(N) — только если Map ещё не установлен
  for (const [, s] of io.sockets.sockets) {
    if (s.userId === targetUserId) return s;
  }
  return null;
}

// Найти ВСЕ сокеты пользователя (для мульти-девайс)
function findAllUserSockets(io, targetUserId) {
  if (_userSockets) {
    return [...(_userSockets.get(targetUserId) || [])];
  }
  const result = [];
  for (const [, s] of io.sockets.sockets) {
    if (s.userId === targetUserId) result.push(s);
  }
  return result;
}

// Проверить — занят ли пользователь в другом звонке
function isUserBusy(userId) {
  for (const [, call] of activeCalls) {
    if (call.participants.has(userId) && call.participants.size >= 2) return true;
  }
  return false;
}

// Очистить звонок и удалить из Map
function cleanupCall(io, chatId) {
  const call = activeCalls.get(chatId);
  if (!call) return;

  if (call.timeout) {
    clearTimeout(call.timeout);
    call.timeout = null;
  }
  if (call.maxDurationTimeout) {
    clearTimeout(call.maxDurationTimeout);
    call.maxDurationTimeout = null;
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
    if (!chatId || typeof chatId !== 'string') return socket.emit('call:error', { error: 'Некорректный chatId' });
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

      // [Баг #2] Проверить — заняты ли целевые пользователи (сигнал "занято")
      if (chatInfo.type === 'personal') {
        const targetId = chatInfo.participantIds.find((pid) => pid !== userId);
        if (targetId && isUserBusy(targetId)) {
          return socket.emit('call:busy', { chatId });
        }
      }

      // Данные звонящего
      const caller = await prisma.user.findUnique({
        where: { id: userId },
        select: { username: true, hue: true, avatar: true },
      });

      // Создать запись активного звонка
      const call = {
        callerId: userId,
        callerSocketId: socket.id,
        startedAt: null, // [Баг #14] startedAt будет установлен при accept, не при initiate
        initiatedAt: new Date(), // Время инициации (для пропущенных)
        participants: new Set(),
        targetUserIds: new Set(),
        timeout: null,
      };
      // Звонящий сразу становится участником
      call.participants.add(userId);
      // Сохранить кому адресован звонок (все участники чата кроме инициатора)
      for (const pid of chatInfo.participantIds) {
        if (pid !== userId) call.targetUserIds.add(pid);
      }
      activeCalls.set(chatId, call);

      // [Баг #1] Макс. длительность звонка — 4 часа (broadcast перед cleanup)
      const MAX_CALL_DURATION = 4 * 60 * 60 * 1000;
      call.maxDurationTimeout = setTimeout(async () => {
        const activeCall = activeCalls.get(chatId);
        if (!activeCall) return;
        // Уведомить всех участников перед очисткой
        const info = await getChatInfo(chatId);
        if (info) {
          for (const pid of info.participantIds) {
            const s = findUserSocket(io, pid);
            if (s) s.emit('call:ended', { chatId });
          }
        }
        // Системное сообщение о макс. длительности
        try {
          await prisma.message.create({
            data: {
              roomId: chatId,
              userId: activeCall.callerId,
              text: 'Звонок завершён (макс. длительность)',
              type: 'system',
            },
          });
        } catch { /* не критично */ }
        cleanupCall(io, chatId);
      }, MAX_CALL_DURATION);

      // Данные для входящего звонка
      const incomingData = {
        chatId,
        callerId: userId,
        callerName: caller.username,
        callerHue: caller.hue,
        callerAvatar: caller.avatar,
        type: chatInfo.type,
        chatName: chatInfo.room.name,
      };

      // Отправить входящий звонок другим участникам
      for (const participantId of chatInfo.participantIds) {
        if (participantId === userId) continue;
        // [Баг #2] Проверить занятость каждого участника в группе
        if (isUserBusy(participantId)) continue;
        const targetSocket = findUserSocket(io, participantId);
        if (targetSocket) {
          targetSocket.emit('call:incoming', incomingData);
        }
      }

      // [Баг #15] Таймаут 30 секунд — если никто не принял → сохранить пропущенный в истории
      call.timeout = setTimeout(async () => {
        const activeCall = activeCalls.get(chatId);
        if (!activeCall || activeCall.participants.size >= 2) return;

        const freshChatInfo = await getChatInfo(chatId);
        if (!freshChatInfo) { cleanupCall(io, chatId); return; }

        // Уведомить звонящего о пропущенном
        const callerSocket = findUserSocket(io, activeCall.callerId);
        if (callerSocket) {
          callerSocket.emit('call:missed', { chatId });
        }

        // Уведомить остальных
        for (const participantId of freshChatInfo.participantIds) {
          if (participantId === activeCall.callerId) continue;
          const targetSocket = findUserSocket(io, participantId);
          if (targetSocket) {
            targetSocket.emit('call:missed', { chatId });
          }
        }

        // [Баг #15] Записать пропущенный звонок в историю чата
        try {
          await prisma.message.create({
            data: {
              roomId: chatId,
              userId: activeCall.callerId,
              text: 'Пропущенный звонок',
              type: 'system',
            },
          });
        } catch { /* не критично */ }

        cleanupCall(io, chatId);
      }, 30000);
    } catch (err) {
      console.error('call:initiate error:', err);
      socket.emit('call:error', { chatId, error: 'Ошибка инициализации звонка' });
    }
  });

  // ═══ Принять звонок ═══
  socket.on('call:accept', async ({ chatId }) => {
    if (!chatId || typeof chatId !== 'string') return socket.emit('call:error', { error: 'Некорректный chatId' });
    try {
      const call = activeCalls.get(chatId);
      if (!call) {
        return socket.emit('call:error', { chatId, error: 'Звонок не найден' });
      }

      // Проверить что звонок адресован этому пользователю
      if (!call.targetUserIds.has(userId)) {
        return socket.emit('call:error', { chatId, error: 'Звонок не адресован вам' });
      }

      // [Баг #4] Предотвратить повторное принятие
      if (call.participants.has(userId)) {
        return;
      }

      // Проверить что пользователь участник чата
      const participant = await prisma.roomParticipant.findUnique({
        where: { roomId_userId: { roomId: chatId, userId } },
      });
      if (!participant) {
        return socket.emit('call:error', { chatId, error: 'Вы не участник этого чата' });
      }

      // Добавить в участники
      call.participants.add(userId);

      // [Баг #14] Установить startedAt при первом принятии
      if (!call.startedAt) {
        call.startedAt = new Date();
      }
      // [HIGH-5] Grace period — voice:join должен успеть после call:accept
      call.voiceJoinGrace = Date.now() + 15000; // 15 сек на присоединение к mediasoup

      // Подтверждение принимающему (включая корректный startedAt)
      socket.emit('call:accept-confirmed', { chatId, userId, startedAt: call.startedAt.getTime() });

      // Сбросить таймаут при первом принятии (звонящий уже в participants)
      if (call.timeout && call.participants.size >= 2) {
        clearTimeout(call.timeout);
        call.timeout = null;
      }

      // Уведомить всех в чате (включая startedAt для синхронизации таймера)
      const chatInfo = await getChatInfo(chatId);
      if (chatInfo) {
        for (const participantId of chatInfo.participantIds) {
          const targetSocket = findUserSocket(io, participantId);
          if (targetSocket) {
            targetSocket.emit('call:accepted', { chatId, userId, startedAt: call.startedAt.getTime() });
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
    if (!chatId || typeof chatId !== 'string') return socket.emit('call:error', { error: 'Некорректный chatId' });
    try {
      const call = activeCalls.get(chatId);
      if (!call) return;

      // Проверить что пользователь участник чата
      const participant = await prisma.roomParticipant.findUnique({
        where: { roomId_userId: { roomId: chatId, userId } },
      });
      if (!participant) return;

      // Убрать из целевых
      call.targetUserIds.delete(userId);

      const chatInfo = await getChatInfo(chatId);

      if (chatInfo && chatInfo.type === 'personal') {
        // Личный чат — уведомить звонящего и завершить
        const callerSocket = findUserSocket(io, call.callerId);
        if (callerSocket) {
          callerSocket.emit('call:declined', { chatId, userId });
        }
        cleanupCall(io, chatId);
      } else {
        // Группа — уведомить звонящего
        const callerSocket = findUserSocket(io, call.callerId);
        if (callerSocket) {
          callerSocket.emit('call:declined', { chatId, userId });
        }
        // [Баг #16] Если все целевые отклонили — очистить звонок
        if (call.targetUserIds.size === 0 && call.participants.size <= 1) {
          // Уведомить звонящего
          if (callerSocket) {
            callerSocket.emit('call:missed', { chatId });
          }
          // Записать пропущенный
          try {
            await prisma.message.create({
              data: {
                roomId: chatId,
                userId: call.callerId,
                text: 'Звонок отклонён',
                type: 'system',
              },
            });
          } catch { /* не критично */ }
          cleanupCall(io, chatId);
        }
      }
    } catch (err) {
      console.error('call:decline error:', err);
    }
  });

  // ═══ Завершить звонок (выйти) ═══
  socket.on('call:end', async ({ chatId }) => {
    if (!chatId || typeof chatId !== 'string') return socket.emit('call:error', { error: 'Некорректный chatId' });
    const call = activeCalls.get(chatId);
    if (!call || (!call.participants.has(userId) && call.callerId !== userId)) {
      return socket.emit('call:error', { chatId, error: 'Вы не участник этого звонка' });
    }
    handleCallEnd(io, socket, userId, chatId);
  });

  // ═══ Отменить звонок (до принятия) ═══
  socket.on('call:cancel', async ({ chatId }) => {
    if (!chatId || typeof chatId !== 'string') return socket.emit('call:error', { error: 'Некорректный chatId' });
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
    // Собрать chatId-ы в массив, чтобы не мутировать Map во время итерации
    const chatIds = [];
    for (const [chatId, call] of activeCalls) {
      if (call.participants.has(userId) || call.callerId === userId) {
        chatIds.push(chatId);
      }
    }
    for (const chatId of chatIds) {
      const call = activeCalls.get(chatId);
      if (!call) continue;

      // [Баг #3] Если звонящий отключился до ответа — отправить call:cancelled (не call:ended)
      if (call.callerId === userId && call.participants.size <= 1 && !call.startedAt) {
        (async () => {
          try {
            const chatInfo = await getChatInfo(chatId);
            if (chatInfo) {
              for (const pid of chatInfo.participantIds) {
                if (pid === userId) continue;
                const s = findUserSocket(io, pid);
                if (s) s.emit('call:cancelled', { chatId });
              }
            }
            cleanupCall(io, chatId);
          } catch (err) {
            console.error('disconnect cancel cleanup error:', err);
            activeCalls.delete(chatId);
          }
        })();
      } else {
        // Обычный выход из активного звонка
        (async () => {
          try {
            await handleCallEnd(io, socket, userId, chatId);
          } catch (err) {
            console.error('disconnect cleanup error:', err);
            activeCalls.delete(chatId);
          }
        })();
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

    // [Баг #30] Уведомить только участников звонка, не всех в чате
    const chatInfo = await getChatInfo(chatId);

    // Собрать тех, кто в звонке + тех, кому звонок был адресован
    const notifySet = new Set([...call.participants, ...call.targetUserIds, call.callerId]);

    for (const pid of notifySet) {
      if (pid === userId) continue;
      const targetSocket = findUserSocket(io, pid);
      if (targetSocket) {
        targetSocket.emit('call:user-left', { chatId, userId });
      }
    }

    // Если больше никого нет — завершить звонок
    if (call.participants.size === 0) {
      // Уведомить только тех кто в звонке / адресован
      for (const pid of notifySet) {
        const targetSocket = findUserSocket(io, pid);
        if (targetSocket) {
          targetSocket.emit('call:ended', { chatId });
        }
      }

      // Системное сообщение в БД о завершённом звонке
      try {
        if (call.startedAt) {
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

module.exports = { callHandler, activeCalls, setUserSockets };
