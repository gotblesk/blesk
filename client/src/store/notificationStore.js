import { create } from 'zustand';
import API_URL from '../config';
import { getAuthHeaders } from '../utils/authFetch';

export const useNotificationStore = create((set, get) => ({
  notifications: [],
  unreadCount: 0,

  // Загрузить уведомления с сервера
  fetchNotifications: async () => {
    try {
      const token = localStorage.getItem('token');
      if (!token) return;

      const res = await fetch(`${API_URL}/api/notifications`, {
        headers: { ...getAuthHeaders() }, credentials: 'include',
      });
      if (!res.ok) throw new Error('not ok');

      const data = await res.json();
      set({
        notifications: Array.isArray(data) ? data : [],
        unreadCount: Array.isArray(data) ? data.filter((n) => !n.isRead).length : 0,
      });
    } catch {
      // [IMP-5] Не показывать mock данные в production
    }
  },

  // Добавить новое уведомление (из WebSocket)
  addNotification: (notification) => {
    const state = get();
    const now = Date.now();

    // Дедупликация по id
    if (state.notifications.some(n => n.id === notification.id)) return;

    // Rate limit: макс 10 уведомлений за 5 сек
    const recent = state.notifications.filter(n => (now - new Date(n.createdAt).getTime()) < 5000);
    if (recent.length >= 10) return;

    // Макс 50 уведомлений
    let updated = [notification, ...state.notifications];
    if (updated.length > 50) updated = updated.slice(0, 50);

    const newCount = state.unreadCount + 1;
    set({ notifications: updated, unreadCount: newCount });
    // [CRIT-1] Sync badge с taskbar
    window.blesk?.setBadge?.(newCount);
  },

  // Пометить одно как прочитанное
  markAsRead: async (id) => {
    try {
      await fetch(`${API_URL}/api/notifications/${id}/read`, {
        method: 'POST',
        headers: { ...getAuthHeaders() }, credentials: 'include',
      });
      set((state) => ({
        notifications: state.notifications.map((n) =>
          n.id === id ? { ...n, isRead: true } : n
        ),
        unreadCount: Math.max(0, state.unreadCount - 1),
      }));
      // [CRIT-1] Badge sync
      window.blesk?.setBadge?.(Math.max(0, get().unreadCount));
    } catch (err) {
      console.error('markAsRead error:', err);
    }
  },

  // Прочитать все
  markAllAsRead: async () => {
    try {
      await fetch(`${API_URL}/api/notifications/read-all`, {
        method: 'POST',
        headers: { ...getAuthHeaders() }, credentials: 'include',
      });
      set((state) => ({
        notifications: state.notifications.map((n) => ({ ...n, isRead: true })),
        unreadCount: 0,
      }));
      window.blesk?.setBadge?.(0);
    } catch (err) {
      console.error('markAllAsRead error:', err);
    }
  },

  // Очистить все уведомления
  clearAll: async () => {
    try {
      await fetch(`${API_URL}/api/notifications/clear`, {
        method: 'DELETE',
        headers: { ...getAuthHeaders() }, credentials: 'include',
      });
      set({ notifications: [], unreadCount: 0 });
      window.blesk?.setBadge?.(0);
    } catch (err) {
      set({ notifications: [], unreadCount: 0 });
      window.blesk?.setBadge?.(0);
    }
  },

  // Удалить уведомление
  removeNotification: async (id) => {
    try {
      await fetch(`${API_URL}/api/notifications/${id}`, {
        method: 'DELETE',
        headers: { ...getAuthHeaders() }, credentials: 'include',
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
