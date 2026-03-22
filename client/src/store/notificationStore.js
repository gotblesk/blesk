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
      if (!res.ok) return;

      const data = await res.json();
      set({
        notifications: data,
        unreadCount: data.filter((n) => !n.isRead).length,
      });
    } catch (err) {
      console.error('fetchNotifications error:', err);
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
