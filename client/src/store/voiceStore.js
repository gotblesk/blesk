import { create } from 'zustand';
import API_URL from '../config';

export const useVoiceStore = create((set, get) => ({
  // Текущая голосовая комната
  currentRoomId: null,
  currentRoomName: null,

  // Участники: { [userId]: { username, hue, muted, deafened, speaking } }
  participants: {},

  // Локальное состояние микрофона
  isMuted: false,
  isDeafened: false,

  // Настройки устройств
  inputDeviceId: null,
  noiseSuppression: true,
  echoCancellation: true,

  // Уровни звука для визуализации
  audioLevels: {},

  // Список доступных голосовых комнат
  rooms: [],
  loading: false,

  // Загрузить список голосовых комнат
  loadRooms: async () => {
    set({ loading: true });
    try {
      const token = localStorage.getItem('token');
      const res = await fetch(`${API_URL}/api/voice/rooms`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const rooms = await res.json();
        set({ rooms });
      }
    } catch {
      // тихий фейл
    } finally {
      set({ loading: false });
    }
  },

  // Создать голосовую комнату
  createRoom: async (name) => {
    try {
      const token = localStorage.getItem('token');
      const res = await fetch(`${API_URL}/api/voice/rooms`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ name }),
      });
      if (res.ok) {
        const room = await res.json();
        set((state) => ({ rooms: [room, ...state.rooms] }));
        return room;
      }
    } catch {
      // тихий фейл
    }
    return null;
  },

  // Войти в комнату (UI state)
  setCurrentRoom: (roomId, roomName) => {
    set({ currentRoomId: roomId, currentRoomName: roomName, participants: {} });
  },

  // Выйти из комнаты (UI state)
  clearCurrentRoom: () => {
    set({
      currentRoomId: null,
      currentRoomName: null,
      participants: {},
      isMuted: false,
      isDeafened: false,
      audioLevels: {},
    });
  },

  // Обновить участника
  addParticipant: (userId, data) => {
    set((state) => ({
      participants: {
        ...state.participants,
        [userId]: { ...data, speaking: false },
      },
    }));
  },

  // Удалить участника
  removeParticipant: (userId) => {
    set((state) => {
      const { [userId]: _, ...rest } = state.participants;
      return { participants: rest };
    });
  },

  // Обновить данные участника
  updateParticipant: (userId, updates) => {
    set((state) => {
      if (!state.participants[userId]) return state;
      return {
        participants: {
          ...state.participants,
          [userId]: { ...state.participants[userId], ...updates },
        },
      };
    });
  },

  // Переключить мут
  toggleMute: () => {
    set((state) => ({ isMuted: !state.isMuted }));
  },

  // Переключить деафен
  toggleDeafen: () => {
    set((state) => ({ isDeafened: !state.isDeafened }));
  },

  // Установить уровень звука
  setAudioLevel: (userId, level) => {
    set((state) => ({
      audioLevels: { ...state.audioLevels, [userId]: level },
    }));
  },

  // Обновить количество участников в списке комнат
  updateRoomParticipants: (roomId, count) => {
    set((state) => ({
      rooms: state.rooms.map((r) =>
        r.id === roomId ? { ...r, participantCount: count } : r
      ),
    }));
  },
}));
