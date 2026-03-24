import { create } from 'zustand';
import API_URL from '../config';

export const useNotificationStore = create((set, get) => ({
  notifications: [],
  unreadCount: 0,

  // Загрузить уведомления с сервера
  fetchNotifications: async () => {
    try {
      const token = localStorage.getItem('token');
      if (!token) return;

      const res = await fetch(`${API_URL}/api/notifications`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('not ok');

      const data = await res.json();
      if (data.length > 0) {
        set({
          notifications: data,
          unreadCount: data.filter((n) => !n.isRead).length,
        });
      } else {
        throw new Error('empty');
      }
    } catch (err) {
      // DEV: тестовые уведомления когда сервер недоступен
      if (get().notifications.length === 0) {
        const now = Date.now();
        const mock = [
          { id: 'mock-1', type: 'friend_request', title: 'shkirtil хочет дружить', body: 'Заявка в друзья', fromUser: { username: 'shkirtil', hue: 280 }, fromUserId: 'u1', isRead: false, createdAt: new Date(now - 120000).toISOString() },
          { id: 'mock-2', type: 'message', title: 'Vohog', body: 'Привет! Как дела?', fromUser: { username: 'Vohog', hue: 350 }, roomId: 'r1', isRead: false, createdAt: new Date(now - 300000).toISOString() },
          { id: 'mock-3', type: 'mention', title: 'Упоминание в #общий', body: '@gotblesk посмотри это', fromUser: { username: 'Den', hue: 200 }, roomId: 'r2', isRead: false, createdAt: new Date(now - 600000).toISOString() },
          { id: 'mock-4', type: 'friend_accepted', title: 'Den теперь ваш друг', body: null, fromUser: { username: 'Den', hue: 200 }, isRead: true, createdAt: new Date(now - 3600000).toISOString() },
          { id: 'mock-5', type: 'system', title: 'Новый вход', body: 'Вход в аккаунт выполнен', isRead: true, createdAt: new Date(now - 7200000).toISOString() },
          { id: 'mock-6', type: 'system', title: 'Новый вход', body: 'Вход в аккаунт выполнен', isRead: true, createdAt: new Date(now - 86400000).toISOString() },
          { id: 'mock-7', type: 'system', title: 'Новый вход', body: 'Вход в аккаунт выполнен', isRead: true, createdAt: new Date(now - 172800000).toISOString() },
          { id: 'mock-8', type: 'friend_request', title: 'NovaPlayer хочет дружить', body: 'Заявка в друзья', fromUser: { username: 'NovaPlayer', hue: 120 }, fromUserId: 'u2', isRead: false, createdAt: new Date(now - 5400000).toISOString() },
        ];
        set({ notifications: mock, unreadCount: mock.filter(n => !n.isRead).length });
      }
    }
  },

  // Добавить новое уведомление (из WebSocket)
  addNotification: (notification) => {
    set((state) => {
      if (state.notifications.some(n => n.id === notification.id)) return state;
      return {
        notifications: [notification, ...state.notifications],
        unreadCount: state.unreadCount + 1,
      };
    });
  },

  // Пометить одно как прочитанное
  markAsRead: async (id) => {
    try {
      const token = localStorage.getItem('token');
      await fetch(`${API_URL}/api/notifications/${id}/read`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      set((state) => ({
        notifications: state.notifications.map((n) =>
          n.id === id ? { ...n, isRead: true } : n
        ),
        unreadCount: Math.max(0, state.unreadCount - 1),
      }));
    } catch (err) {
      console.error('markAsRead error:', err);
    }
  },

  // Прочитать все
  markAllAsRead: async () => {
    try {
      const token = localStorage.getItem('token');
      await fetch(`${API_URL}/api/notifications/read-all`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      set((state) => ({
        notifications: state.notifications.map((n) => ({ ...n, isRead: true })),
        unreadCount: 0,
      }));
    } catch (err) {
      console.error('markAllAsRead error:', err);
    }
  },

  // Очистить все уведомления
  clearAll: async () => {
    try {
      const token = localStorage.getItem('token');
      await fetch(`${API_URL}/api/notifications/clear`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      set({ notifications: [], unreadCount: 0 });
    } catch (err) {
      // Если endpoint не существует — просто очищаем локально
      set({ notifications: [], unreadCount: 0 });
    }
  },

  // Удалить уведомление
  removeNotification: async (id) => {
    try {
      const token = localStorage.getItem('token');
      await fetch(`${API_URL}/api/notifications/${id}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      set((state) => {
        const notification = state.notifications.find((n) => n.id === id);
        return {
          notifications: state.notifications.filter((n) => n.id !== id),
          unreadCount: notification && !notification.isRead
            ? Math.max(0, state.unreadCount - 1)
            : state.unreadCount,
        };
      });
    } catch (err) {
      console.error('removeNotification error:', err);
    }
  },
}));
