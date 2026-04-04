import { create } from 'zustand';
import API_URL from '../config';
import { getAuthHeaders } from '../utils/authFetch';

function getHeaders() {
  return {
    'Content-Type': 'application/json',
    ...getAuthHeaders(),
  };
}

export const useChannelStore = create((set, get) => ({
  channels: [],
  myChannels: [],
  posts: {},
  loadingBrowse: false,
  // Loading/error states для постов
  loadingPosts: {},   // { [channelId]: true }
  postsError: {},     // { [channelId]: string }
  browseError: null,  // string | null
  activeChannelId: null,
  hasMorePosts: {},   // { [channelId]: boolean }
  loadingMorePosts: {}, // { [channelId]: true }

  // Загрузить каналы для обзора
  loadBrowse: async ({ sort, category, search } = {}) => {
    if (get().loadingBrowse) return;
    set({ loadingBrowse: true, browseError: null });
    try {
      const params = new URLSearchParams();
      if (sort) params.set('sort', sort);
      if (category) params.set('category', category);
      if (search) params.set('search', search);
      const qs = params.toString();
      const res = await fetch(`${API_URL}/api/channels${qs ? `?${qs}` : ''}`, {
        headers: getHeaders(), credentials: 'include',
      });
      if (!res.ok) throw new Error('Не удалось загрузить каналы');
      const data = await res.json();
      const rawChannels = data.channels ?? data;
      set({ channels: Array.isArray(rawChannels) ? rawChannels : [] });
    } catch (err) {
      console.error('Ошибка загрузки каналов:', err);
      set({ browseError: err.message || 'Ошибка загрузки' });
    } finally {
      set({ loadingBrowse: false });
    }
  },

  // Загрузить мои каналы (подписки + собственные)
  loadMyChannels: async () => {
    try {
      const res = await fetch(`${API_URL}/api/channels/my`, {
        headers: getHeaders(), credentials: 'include',
      });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ myChannels: [...(data.owned ?? []), ...(data.subscribed ?? [])] });
    } catch (err) {
      console.error('Ошибка загрузки моих каналов:', err);
    }
  },

  // Загрузить посты канала (с loading/error states)
  loadPosts: async (channelId) => {
    set((state) => ({
      loadingPosts: { ...state.loadingPosts, [channelId]: true },
      postsError: { ...state.postsError, [channelId]: null },
    }));
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/posts`, {
        headers: getHeaders(), credentials: 'include',
      });
      if (!res.ok) throw new Error('Не удалось загрузить посты');
      const data = await res.json();
      const rawPosts = data.posts ?? data ?? [];
      const incoming = [...rawPosts].reverse();
      // Merge с существующими (не перезаписывать socket-полученные)
      set((state) => {
        const existing = state.posts[channelId] || [];
        const incomingIds = new Set(incoming.map(i => i.id));
        const merged = [...existing.filter(p => !incomingIds.has(p.id)), ...incoming];
        merged.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
        return {
          posts: { ...state.posts, [channelId]: merged },
          hasMorePosts: { ...state.hasMorePosts, [channelId]: rawPosts.length >= 20 },
        };
      });
    } catch (err) {
      console.error('Ошибка загрузки постов:', err);
      set((state) => ({
        postsError: { ...state.postsError, [channelId]: err.message || 'Ошибка' },
      }));
    } finally {
      set((state) => ({
        loadingPosts: { ...state.loadingPosts, [channelId]: false },
      }));
    }
  },

  // Загрузить старые посты (cursor-based pagination)
  loadMorePosts: async (channelId, beforeId) => {
    if (!beforeId) return;
    const state = get();
    if (state.loadingMorePosts[channelId]) return;
    if (state.hasMorePosts[channelId] === false) return;

    set((s) => ({
      loadingMorePosts: { ...s.loadingMorePosts, [channelId]: true },
    }));
    try {
      const res = await fetch(
        `${API_URL}/api/channels/${channelId}/posts?before=${beforeId}&limit=20`,
        { headers: getHeaders(), credentials: 'include' }
      );
      if (!res.ok) throw new Error('Не удалось загрузить посты');
      const data = await res.json();
      const rawPosts = data.posts ?? data ?? [];
      const older = [...rawPosts].reverse();

      set((s) => {
        const existing = s.posts[channelId] || [];
        const existingIds = new Set(existing.map(p => p.id));
        const newPosts = older.filter(p => !existingIds.has(p.id));
        const merged = [...newPosts, ...existing];
        merged.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
        return {
          posts: { ...s.posts, [channelId]: merged },
          hasMorePosts: { ...s.hasMorePosts, [channelId]: rawPosts.length >= 20 },
        };
      });
    } catch (err) {
      console.error('Ошибка загрузки старых постов:', err);
    } finally {
      set((s) => ({
        loadingMorePosts: { ...s.loadingMorePosts, [channelId]: false },
      }));
    }
  },

  // Подписаться на канал
  subscribe: async (channelId) => {
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/subscribe`, {
        method: 'POST',
        headers: getHeaders(), credentials: 'include',
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || 'Ошибка подписки');
      }
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
      throw err;
    }
  },

  // Отписаться от канала
  unsubscribe: async (channelId) => {
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/subscribe`, {
        method: 'DELETE',
        headers: getHeaders(), credentials: 'include',
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
        headers: getHeaders(), credentials: 'include',
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

  // Удалить пост локально (после подтверждения с сервера)
  deletePost: async (channelId, postId, socketRef) => {
    try {
      if (socketRef?.current) {
        socketRef.current.emit('message:delete', { messageId: postId, chatId: channelId });
      }
      set((state) => ({
        posts: {
          ...state.posts,
          [channelId]: (state.posts[channelId] || []).filter(p => p.id !== postId),
        },
      }));
    } catch (err) {
      console.error('Ошибка удаления поста:', err);
    }
  },

  // Обновить канал локально
  updateChannelLocal: (channelId, data) => {
    set((state) => ({
      channels: state.channels.map((c) =>
        c.id === channelId ? { ...c, ...data, channelMeta: { ...c.channelMeta, ...data } } : c
      ),
      myChannels: state.myChannels.map((c) =>
        c.id === channelId ? { ...c, ...data, channelMeta: { ...c.channelMeta, ...data } } : c
      ),
    }));
  },

  // Удалить канал (API + локально)
  deleteChannel: async (channelId) => {
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}`, {
        method: 'DELETE',
        headers: getHeaders(),
        credentials: 'include',
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.error || 'Не удалось удалить канал');
      }
      set((state) => ({
        channels: state.channels.filter((c) => c.id !== channelId),
        myChannels: state.myChannels.filter((c) => c.id !== channelId),
        posts: Object.fromEntries(
          Object.entries(state.posts).filter(([k]) => k !== channelId)
        ),
      }));
      return { ok: true };
    } catch (err) {
      console.error('deleteChannel:', err);
      return { error: err.message || 'Ошибка удаления' };
    }
  },

  // Удалить канал из локального состояния
  removeChannel: (channelId) => {
    set((state) => ({
      channels: state.channels.filter((c) => c.id !== channelId),
      myChannels: state.myChannels.filter((c) => c.id !== channelId),
    }));
  },

  setActiveChannel: (id) => set({ activeChannelId: id }),
}));
