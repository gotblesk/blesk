import { create } from 'zustand';

export const useCallStore = create((set, get) => ({
  // Активный звонок (в котором пользователь участвует)
  activeCall: null,
  // { chatId, voiceRoomId, callerId, status: 'ringing'|'active'|'ended', startedAt, participants: [] }

  // Входящий звонок (ожидает ответа)
  incomingCall: null,
  // { chatId, callerId, callerName, callerHue, type: 'personal'|'group', chatName }

  // Инициировать звонок (опционально с видео)
  initiateCall: (chatId, options = {}) => {
    set({
      activeCall: {
        chatId,
        voiceRoomId: 'call:' + chatId,
        callerId: null,
        status: 'ringing',
        startedAt: Date.now(),
        participants: [],
        videoEnabled: options.video || false,
      },
    });
  },

  // Установить входящий звонок
  setIncomingCall: (data) => {
    set({ incomingCall: data });
  },

  // Очистить входящий звонок
  clearIncomingCall: () => {
    set({ incomingCall: null });
  },

  // Принять звонок — перевести в активный
  acceptCall: () => {
    const { incomingCall } = get();
    if (!incomingCall) return;

    set({
      activeCall: {
        chatId: incomingCall.chatId,
        voiceRoomId: 'call:' + incomingCall.chatId,
        callerId: incomingCall.callerId,
        status: 'active',
        startedAt: Date.now(),
        participants: [incomingCall.callerId],
        videoEnabled: incomingCall.videoEnabled || false,
      },
      incomingCall: null,
    });
  },

  // Установить активный звонок напрямую
  setActiveCall: (data) => {
    set({
      activeCall: {
        chatId: data.chatId,
        voiceRoomId: 'call:' + data.chatId,
        callerId: data.callerId || null,
        status: data.status || 'active',
        startedAt: data.startedAt || Date.now(),
        participants: data.participants || [],
        videoEnabled: data.videoEnabled || false,
      },
    });
  },

  // Очистить активный звонок
  clearActiveCall: () => {
    set({ activeCall: null });
  },

  // Добавить участника в звонок
  addCallParticipant: (userId) => {
    const { activeCall } = get();
    if (!activeCall) return;
    if (activeCall.participants.includes(userId)) return;

    set({
      activeCall: {
        ...activeCall,
        status: 'active',
        participants: [...activeCall.participants, userId],
      },
    });
  },

  // Убрать участника из звонка, завершить если никого не осталось
  removeCallParticipant: (userId) => {
    const { activeCall } = get();
    if (!activeCall) return;

    const remaining = activeCall.participants.filter((id) => id !== userId);
    if (remaining.length === 0) {
      // Последний участник ушёл — завершить звонок
      set({ activeCall: null });
    } else {
      set({
        activeCall: {
          ...activeCall,
          participants: remaining,
        },
      });
    }
  },
}));
