import { create } from 'zustand';
import API_URL from '../config';

function getHeaders() {
  const token = localStorage.getItem('token');
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
}

export const useAdminStore = create((set, get) => ({
  // Статистика
  stats: null,
  loadingStats: false,

  // Пользователи
  users: [],
  usersTotal: 0,
  usersPage: 1,
  loadingUsers: false,
  selectedUser: null,

  // Теги
  tags: [],
  loadingTags: false,

  // Жалобы
  reports: [],
  reportsTotal: 0,
  loadingReports: false,

  // Логи
  logs: [],
  logsTotal: 0,
  loadingLogs: false,

  // Feedback
  feedbacks: [],
  feedbacksTotal: 0,
  loadingFeedbacks: false,

  // Каналы
  channels: [],
  loadingChannels: false,

  // БД
  dbTables: [],
  dbRows: [],
  dbTotal: 0,
  dbColumns: [],
  selectedTable: null,
  loadingDb: false,

  // Сервер
  serverConfig: null,

  // ═══════════════════════════════════════
  // Статистика
  // ═══════════════════════════════════════

  fetchStats: async () => {
    set({ loadingStats: true });
    try {
      const res = await fetch(`${API_URL}/api/admin/stats`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ stats: data });
    } catch { /* ignore */ }
    set({ loadingStats: false });
  },

  // ═══════════════════════════════════════
  // Пользователи
  // ═══════════════════════════════════════

  fetchUsers: async (page = 1, search = '', filters = {}) => {
    set({ loadingUsers: true });
    try {
      const params = new URLSearchParams({ page, limit: 50, ...(search && { search }), ...filters });
      const res = await fetch(`${API_URL}/api/admin/users?${params}`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ users: data.users, usersTotal: data.total, usersPage: data.page });
    } catch { /* ignore */ }
    set({ loadingUsers: false });
  },

  fetchUser: async (id) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/users/${id}`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ selectedUser: data });
      return data;
    } catch { return null; }
  },

  updateUser: async (id, data) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/users/${id}`, {
        method: 'PATCH', headers: getHeaders(), body: JSON.stringify(data),
      });
      if (!res.ok) throw new Error();
      const updated = await res.json();
      set((s) => ({
        users: s.users.map((u) => (u.id === id ? { ...u, ...updated.user } : u)),
        selectedUser: s.selectedUser?.id === id ? { ...s.selectedUser, ...updated.user } : s.selectedUser,
      }));
      return true;
    } catch { return false; }
  },

  banUser: async (id, reason) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/users/${id}/ban`, {
        method: 'POST', headers: getHeaders(), body: JSON.stringify({ reason }),
      });
      if (!res.ok) throw new Error();
      set((s) => ({
        users: s.users.map((u) => (u.id === id ? { ...u, banned: true, bannedReason: reason } : u)),
        selectedUser: s.selectedUser?.id === id ? { ...s.selectedUser, banned: true, bannedReason: reason } : s.selectedUser,
      }));
      return true;
    } catch { return false; }
  },

  unbanUser: async (id) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/users/${id}/unban`, {
        method: 'POST', headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      set((s) => ({
        users: s.users.map((u) => (u.id === id ? { ...u, banned: false, bannedReason: null } : u)),
        selectedUser: s.selectedUser?.id === id ? { ...s.selectedUser, banned: false, bannedReason: null } : s.selectedUser,
      }));
      return true;
    } catch { return false; }
  },

  grantTag: async (userId, tagId) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/users/${userId}/tags`, {
        method: 'POST', headers: getHeaders(), body: JSON.stringify({ tagId }),
      });
      if (!res.ok) throw new Error();
      return true;
    } catch { return false; }
  },

  revokeTag: async (userId, tagId) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/users/${userId}/tags/${tagId}`, {
        method: 'DELETE', headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      return true;
    } catch { return false; }
  },

  // ═══════════════════════════════════════
  // Теги
  // ═══════════════════════════════════════

  fetchTags: async () => {
    set({ loadingTags: true });
    try {
      const res = await fetch(`${API_URL}/api/admin/tags`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ tags: data.tags || data });
    } catch { /* ignore */ }
    set({ loadingTags: false });
  },

  createTag: async (data) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/tags`, {
        method: 'POST', headers: getHeaders(), body: JSON.stringify(data),
      });
      if (!res.ok) throw new Error();
      const tag = await res.json();
      set((s) => ({ tags: [...s.tags, tag.tag || tag] }));
      return true;
    } catch { return false; }
  },

  updateTag: async (id, data) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/tags/${id}`, {
        method: 'PATCH', headers: getHeaders(), body: JSON.stringify(data),
      });
      if (!res.ok) throw new Error();
      const updated = await res.json();
      set((s) => ({ tags: s.tags.map((t) => (t.id === id ? { ...t, ...updated.tag } : t)) }));
      return true;
    } catch { return false; }
  },

  deleteTag: async (id) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/tags/${id}`, {
        method: 'DELETE', headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      set((s) => ({ tags: s.tags.filter((t) => t.id !== id) }));
      return true;
    } catch { return false; }
  },

  // ═══════════════════════════════════════
  // Жалобы
  // ═══════════════════════════════════════

  fetchReports: async (page = 1, status = '') => {
    set({ loadingReports: true });
    try {
      const params = new URLSearchParams({ page, limit: 50, ...(status && { status }) });
      const res = await fetch(`${API_URL}/api/admin/reports?${params}`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ reports: data.reports, reportsTotal: data.total });
    } catch { /* ignore */ }
    set({ loadingReports: false });
  },

  updateReport: async (id, status) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/reports/${id}`, {
        method: 'PATCH', headers: getHeaders(), body: JSON.stringify({ status }),
      });
      if (!res.ok) throw new Error();
      set((s) => ({ reports: s.reports.map((r) => (r.id === id ? { ...r, status } : r)) }));
      return true;
    } catch { return false; }
  },

  deleteMessage: async (id) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/messages/${id}`, {
        method: 'DELETE', headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      return true;
    } catch { return false; }
  },

  // ═══════════════════════════════════════
  // Логи
  // ═══════════════════════════════════════

  fetchLogs: async (page = 1, filters = {}) => {
    set({ loadingLogs: true });
    try {
      const params = new URLSearchParams({ page, limit: 50, ...filters });
      const res = await fetch(`${API_URL}/api/admin/logs?${params}`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ logs: data.logs, logsTotal: data.total });
    } catch { /* ignore */ }
    set({ loadingLogs: false });
  },

  // ═══════════════════════════════════════
  // Feedback
  // ═══════════════════════════════════════

  fetchFeedbacks: async (page = 1, filters = {}) => {
    set({ loadingFeedbacks: true });
    try {
      const params = new URLSearchParams({ page, limit: 50, ...filters });
      const res = await fetch(`${API_URL}/api/admin/feedback?${params}`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ feedbacks: data.feedbacks, feedbacksTotal: data.total });
    } catch { /* ignore */ }
    set({ loadingFeedbacks: false });
  },

  updateFeedback: async (id, status) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/feedback/${id}`, {
        method: 'PATCH', headers: getHeaders(), body: JSON.stringify({ status }),
      });
      if (!res.ok) throw new Error();
      set((s) => ({ feedbacks: s.feedbacks.map((f) => (f.id === id ? { ...f, status } : f)) }));
      return true;
    } catch { return false; }
  },

  // ═══════════════════════════════════════
  // Каналы
  // ═══════════════════════════════════════

  fetchChannels: async () => {
    set({ loadingChannels: true });
    try {
      const res = await fetch(`${API_URL}/api/admin/channels`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ channels: data.channels || data });
    } catch { /* ignore */ }
    set({ loadingChannels: false });
  },

  deleteChannel: async (id) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/channels/${id}`, {
        method: 'DELETE', headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      set((s) => ({ channels: s.channels.filter((c) => c.id !== id) }));
      return true;
    } catch { return false; }
  },

  // ═══════════════════════════════════════
  // БД (read-only)
  // ═══════════════════════════════════════

  fetchDbTables: async () => {
    try {
      const res = await fetch(`${API_URL}/api/admin/db/tables`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ dbTables: data.tables || data });
    } catch { /* ignore */ }
  },

  fetchDbTable: async (table, page = 1) => {
    set({ loadingDb: true, selectedTable: table });
    try {
      const params = new URLSearchParams({ page, limit: 50 });
      const res = await fetch(`${API_URL}/api/admin/db/${table}?${params}`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ dbRows: data.rows, dbTotal: data.total, dbColumns: data.columns });
    } catch { /* ignore */ }
    set({ loadingDb: false });
  },

  // ═══════════════════════════════════════
  // Сервер
  // ═══════════════════════════════════════

  fetchServerConfig: async () => {
    try {
      const res = await fetch(`${API_URL}/api/admin/server/config`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ serverConfig: data });
    } catch { /* ignore */ }
  },

  // ═══════════════════════════════════════
  // Broadcast
  // ═══════════════════════════════════════

  broadcastUpdate: async (version, changelog) => {
    try {
      const res = await fetch(`${API_URL}/api/admin/broadcast-update`, {
        method: 'POST', headers: getHeaders(), body: JSON.stringify({ version, changelog }),
      });
      if (!res.ok) throw new Error();
      const data = await res.json();
      return data;
    } catch { return null; }
  },
}));
