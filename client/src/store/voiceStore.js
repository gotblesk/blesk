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
  outputDeviceId: (() => {
    try { return localStorage.getItem('blesk-output-device') || 'default'; } catch { return 'default'; }
  })(),
  noiseSuppression: (() => {
    try { const v = localStorage.getItem('blesk-noise-suppression'); return v === null ? true : v === 'true'; } catch { return true; }
  })(),
  echoCancellation: (() => {
    try { const v = localStorage.getItem('blesk-echo-cancellation'); return v === null ? true : v === 'true'; } catch { return true; }
  })(),

  // Порог VAD (чувствительность микрофона, 0-100)
  vadThreshold: (() => {
    try { const v = localStorage.getItem('blesk-vad-threshold'); return v === null ? 15 : Number(v); } catch { return 15; }
  })(),

  // Качество соединения: 'good' | 'fair' | 'poor' | null
  connectionQuality: null,

  // Видео-состояние
  cameraOn: false,
  screenShareOn: false,
  localCameraStream: null,
  // { [userId]: { camera: MediaStream | null, screen: MediaStream | null } }
  videoStreams: {},

  // Включить/выключить камеру
  setCameraOn: (on) => set({ cameraOn: on }),
  setScreenShareOn: (on) => set({ screenShareOn: on }),
  setLocalCameraStream: (stream) => set({ localCameraStream: stream }),

  // Установить видеопоток удалённого участника
  setVideoStream: (userId, type, stream) => {
    set((state) => {
      const existing = state.videoStreams[userId] || {};
      return {
        videoStreams: {
          ...state.videoStreams,
          [userId]: { ...existing, [type]: stream },
        },
      };
    });
  },

  // Убрать видеопоток
  removeVideoStream: (userId, type) => {
    set((state) => {
      const existing = state.videoStreams[userId];
      if (!existing) return state;
      const updated = { ...existing, [type]: null };
      if (!updated.camera && !updated.screen) {
        const { [userId]: _, ...rest } = state.videoStreams;
        return { videoStreams: rest };
      }
      return { videoStreams: { ...state.videoStreams, [userId]: updated } };
    });
  },

  // Очистить все видеопотоки
  clearVideoStreams: () => set({ videoStreams: {}, localCameraStream: null, cameraOn: false, screenShareOn: false }),

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

  // Установить устройство вывода звука
  setOutputDevice: (deviceId) => {
    const id = deviceId || 'default';
    set({ outputDeviceId: id });
    localStorage.setItem('blesk-output-device', id);
  },

  // Установить порог VAD
  setVadThreshold: (value) => {
    const clamped = Math.max(0, Math.min(100, Math.round(value)));
    set({ vadThreshold: clamped });
    localStorage.setItem('blesk-vad-threshold', String(clamped));
  },

  // Установить качество соединения
  setConnectionQuality: (quality) => {
    set({ connectionQuality: quality });
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

  // Выйти из комнаты (UI state + освобождение ресурсов)
  clearCurrentRoom: () => {
    // Остановить все удалённые видеопотоки перед обнулением
    const state = get();
    if (state.localCameraStream) {
      state.localCameraStream.getTracks().forEach((t) => t.stop());
    }
    for (const streams of Object.values(state.videoStreams)) {
      if (streams.camera) streams.camera.getTracks().forEach((t) => t.stop());
      if (streams.screen) streams.screen.getTracks().forEach((t) => t.stop());
    }
    set({
      currentRoomId: null,
      currentRoomName: null,
      participants: {},
      isMuted: false,
      isDeafened: false,
      audioLevels: {},
      connectionQuality: null,
      cameraOn: false,
      screenShareOn: false,
      localCameraStream: null,
      videoStreams: {},
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
