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

  // Настройки устройств (загружаем из localStorage)
  inputDeviceId: (() => {
    try { return localStorage.getItem('blesk-input-device') || null; } catch { return null; }
  })(),
  noiseSuppression: (() => {
    try { const v = localStorage.getItem('blesk-noise-suppression'); return v === null ? true : v === 'true'; } catch { return true; }
  })(),
  echoCancellation: (() => {
    try { const v = localStorage.getItem('blesk-echo-cancellation'); return v === null ? true : v === 'true'; } catch { return true; }
  })(),

  // Уровни звука для визуализации
  audioLevels: {},

  // Громкость пользователей (0-200), по умолчанию 100
  userVolumes: (() => {
    try {
      return JSON.parse(localStorage.getItem('blesk-user-volumes')) || {};
    } catch {
      return {};
    }
  })(),

  // Установить громкость конкретного пользователя
  setUserVolume: (userId, volume) => {
    const clamped = Math.max(0, Math.min(200, Math.round(volume)));
    set((state) => {
      const updated = { ...state.userVolumes, [userId]: clamped };
      localStorage.setItem('blesk-user-volumes', JSON.stringify(updated));
      return { userVolumes: updated };
    });
  },

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

  // Удалить голосовую комнату (только владелец)
  deleteRoom: async (roomId) => {
    try {
      const token = localStorage.getItem('token');
      const res = await fetch(`${API_URL}/api/voice/rooms/${roomId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        set((state) => ({ rooms: state.rooms.filter((r) => r.id !== roomId) }));
        return { ok: true };
      }
      const data = await res.json();
      return { error: data.error || 'Ошибка' };
    } catch {
      return { error: 'Не удалось удалить комнату' };
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
      const data = await res.json();
      if (res.ok) {
        set((state) => ({ rooms: [data, ...state.rooms] }));
        return { room: data };
      }
      return { error: data.error || 'Ошибка' };
    } catch {
      return { error: 'Не удалось создать комнату' };
    }
  },

  // Пригласить друга в голосовую комнату
  inviteToRoom: async (roomId, userId) => {
    try {
      const token = localStorage.getItem('token');
      const res = await fetch(`${API_URL}/api/voice/rooms/${roomId}/invite`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ userId }),
      });
      const data = await res.json();
      if (res.ok) {
        // Обновить invited в локальном состоянии
        set((state) => ({
          rooms: state.rooms.map((r) =>
            r.id === roomId
              ? { ...r, invited: [...(r.invited || []), data.user] }
              : r
          ),
        }));
        return { ok: true };
      }
      return { error: data.error || 'Ошибка' };
    } catch {
      return { error: 'Не удалось пригласить' };
    }
  },

  // Кикнуть из голосовой комнаты
  kickFromRoom: async (roomId, userId) => {
    try {
      const token = localStorage.getItem('token');
      const res = await fetch(`${API_URL}/api/voice/rooms/${roomId}/kick/${userId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        set((state) => ({
          rooms: state.rooms.map((r) =>
            r.id === roomId
              ? { ...r, invited: (r.invited || []).filter((i) => i.userId !== userId) }
              : r
          ),
        }));
        return { ok: true };
      }
      const data = await res.json();
      return { error: data.error || 'Ошибка' };
    } catch {
      return { error: 'Ошибка' };
    }
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
