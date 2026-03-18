import { create } from 'zustand';
import API_URL from '../config';

function getHeaders() {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${localStorage.getItem('token')}`,
  };
}

export const useChannelStore = create((set, get) => ({
  channels: [],
  myChannels: [],
  posts: {},
  loadingBrowse: false,
  activeChannelId: null,

  // Загрузить каналы для обзора
  loadBrowse: async ({ sort, category, search } = {}) => {
    if (get().loadingBrowse) return;
    set({ loadingBrowse: true });
    try {
      const params = new URLSearchParams();
      if (sort) params.set('sort', sort);
      if (category) params.set('category', category);
      if (search) params.set('search', search);
      const qs = params.toString();
      const res = await fetch(`${API_URL}/api/channels${qs ? `?${qs}` : ''}`, {
        headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ channels: data.channels ?? data ?? [] });
    } catch (err) {
      console.error('Ошибка загрузки каналов:', err);
    } finally {
      set({ loadingBrowse: false });
    }
  },

  // Загрузить мои каналы (подписки + собственные)
  loadMyChannels: async () => {
    try {
      const res = await fetch(`${API_URL}/api/channels/my`, {
        headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ myChannels: [...(data.owned ?? []), ...(data.subscribed ?? [])] });
    } catch (err) {
      console.error('Ошибка загрузки моих каналов:', err);
    }
  },

  // Загрузить посты канала
  loadPosts: async (channelId) => {
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/posts`, {
        headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set((state) => ({
        posts: { ...state.posts, [channelId]: (data.posts ?? data ?? []).reverse() },
      }));
    } catch (err) {
      console.error('Ошибка загрузки постов:', err);
    }
  },

  // Подписаться на канал
  subscribe: async (channelId) => {
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/subscribe`, {
        method: 'POST',
        headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      // Обновить списки
      get().loadMyChannels();
      set((state) => ({
        channels: state.channels.map((c) =>
          c.id === channelId
            ? { ...c, subscribersCount: (c.subscribersCount || 0) + 1, isSubscribed: true }
            : c
        ),
      }));
    } catch (err) {
      console.error('Ошибка подписки:', err);
    }
  },

  // Отписаться от канала
  unsubscribe: async (channelId) => {
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/subscribe`, {
        method: 'DELETE',
        headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      get().loadMyChannels();
      set((state) => ({
        channels: state.channels.map((c) =>
          c.id === channelId
            ? { ...c, subscribersCount: Math.max(0, (c.subscribersCount || 1) - 1), isSubscribed: false }
            : c
        ),
        myChannels: state.myChannels.filter((c) => c.id !== channelId),
      }));
    } catch (err) {
      console.error('Ошибка отписки:', err);
    }
  },

  // Создать канал
  createChannel: async ({ name, description, category }) => {
    try {
      const res = await fetch(`${API_URL}/api/channels`, {
        method: 'POST',
        headers: getHeaders(),
        body: JSON.stringify({ name, description, category }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || 'Ошибка создания канала');
      }
      const channel = await res.json();
      set((state) => ({
        myChannels: [channel, ...state.myChannels],
        channels: [channel, ...state.channels],
      }));
      return channel;
    } catch (err) {
      console.error('Ошибка создания канала:', err);
      throw err;
    }
  },

  // Получить новый пост (через сокет)
  receivePost: (message) => {
    set((state) => {
      const channelId = message.roomId || message.chatId;
      const existing = state.posts[channelId] || [];
      if (existing.some((p) => p.id === message.id)) return state;
      return {
        posts: { ...state.posts, [channelId]: [...existing, message] },
      };
    });
  },

  // Установить активный канал
  setActiveChannel: (id) => set({ activeChannelId: id }),
}));
