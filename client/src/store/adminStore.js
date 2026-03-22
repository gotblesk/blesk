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
  stats: null,
  loadingStats: false,

  users: [],
  usersTotal: 0,
  usersPage: 1,
  loadingUsers: false,
  selectedUser: null,

  tags: [],
  loadingTags: false,

  reports: [],
  reportsTotal: 0,
  loadingReports: false,

  logs: [],
  logsTotal: 0,
  loadingLogs: false,

  feedbacks: [],
  feedbacksTotal: 0,
  loadingFeedbacks: false,

  channels: [],
  loadingChannels: false,

  dbTables: [],
  dbRows: [],
  dbTotal: 0,
  dbColumns: [],
  selectedTable: null,
  loadingDb: false,

  serverConfig: null,

  fetchStats: async () => {
    set({ loadingStats: true });
    try {
      const res = await fetch(`${API_URL}/api/internal/stats`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ stats: data });
    } catch { /* ignore */ }
    set({ loadingStats: false });
  },

  fetchUsers: async (page = 1, search = '', filters = {}) => {
    set({ loadingUsers: true });
    try {
      const params = new URLSearchParams({ page, limit: 50, ...(search && { search }), ...filters });
      const res = await fetch(`${API_URL}/api/internal/users?${params}`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ users: data.users, usersTotal: data.total, usersPage: data.page });
    } catch { /* ignore */ }
    set({ loadingUsers: false });
  },

  fetchUser: async (id) => {
    try {
      const res = await fetch(`${API_URL}/api/internal/users/${id}`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ selectedUser: data });
      return data;
    } catch { return null; }
  },

  updateUser: async (id, data) => {
    try {
      const res = await fetch(`${API_URL}/api/internal/users/${id}`, {
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
      const res = await fetch(`${API_URL}/api/internal/users/${id}/ban`, {
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
      const res = await fetch(`${API_URL}/api/internal/users/${id}/unban`, {
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
      const res = await fetch(`${API_URL}/api/internal/users/${userId}/tags`, {
        method: 'POST', headers: getHeaders(), body: JSON.stringify({ tagId }),
      });
      if (!res.ok) throw new Error();
      return true;
    } catch { return false; }
  },

  revokeTag: async (userId, tagId) => {
    try {
      const res = await fetch(`${API_URL}/api/internal/users/${userId}/tags/${tagId}`, {
        method: 'DELETE', headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      return true;
    } catch { return false; }
  },

  fetchTags: async () => {
    set({ loadingTags: true });
    try {
      const res = await fetch(`${API_URL}/api/internal/tags`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ tags: data.tags || data });
    } catch { /* ignore */ }
    set({ loadingTags: false });
  },

  createTag: async (data) => {
    try {
      const res = await fetch(`${API_URL}/api/internal/tags`, {
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
      const res = await fetch(`${API_URL}/api/internal/tags/${id}`, {
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
      const res = await fetch(`${API_URL}/api/internal/tags/${id}`, {
        method: 'DELETE', headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      set((s) => ({ tags: s.tags.filter((t) => t.id !== id) }));
      return true;
    } catch { return false; }
  },

  fetchReports: async (page = 1, status = '') => {
    set({ loadingReports: true });
    try {
      const params = new URLSearchParams({ page, limit: 50, ...(status && { status }) });
      const res = await fetch(`${API_URL}/api/internal/reports?${params}`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ reports: data.reports, reportsTotal: data.total });
    } catch { /* ignore */ }
    set({ loadingReports: false });
  },

  updateReport: async (id, status) => {
    try {
      const res = await fetch(`${API_URL}/api/internal/reports/${id}`, {
        method: 'PATCH', headers: getHeaders(), body: JSON.stringify({ status }),
      });
      if (!res.ok) throw new Error();
      set((s) => ({ reports: s.reports.map((r) => (r.id === id ? { ...r, status } : r)) }));
      return true;
    } catch { return false; }
  },

  deleteMessage: async (id) => {
    try {
      const res = await fetch(`${API_URL}/api/internal/messages/${id}`, {
        method: 'DELETE', headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      return true;
    } catch { return false; }
  },

  fetchLogs: async (page = 1, filters = {}) => {
    set({ loadingLogs: true });
    try {
      const params = new URLSearchParams({ page, limit: 50, ...filters });
      const res = await fetch(`${API_URL}/api/internal/logs?${params}`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ logs: data.logs, logsTotal: data.total });
    } catch { /* ignore */ }
    set({ loadingLogs: false });
  },

  fetchFeedbacks: async (page = 1, filters = {}) => {
    set({ loadingFeedbacks: true });
    try {
      const params = new URLSearchParams({ page, limit: 50, ...filters });
      const res = await fetch(`${API_URL}/api/internal/feedback?${params}`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ feedbacks: data.feedbacks, feedbacksTotal: data.total });
    } catch { /* ignore */ }
    set({ loadingFeedbacks: false });
  },

  updateFeedback: async (id, status) => {
    try {
      const res = await fetch(`${API_URL}/api/internal/feedback/${id}`, {
        method: 'PATCH', headers: getHeaders(), body: JSON.stringify({ status }),
      });
      if (!res.ok) throw new Error();
      set((s) => ({ feedbacks: s.feedbacks.map((f) => (f.id === id ? { ...f, status } : f)) }));
      return true;
    } catch { return false; }
  },

  fetchChannels: async () => {
    set({ loadingChannels: true });
    try {
      const res = await fetch(`${API_URL}/api/internal/channels`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ channels: data.channels || data });
    } catch { /* ignore */ }
    set({ loadingChannels: false });
  },

  deleteChannel: async (id) => {
    try {
      const res = await fetch(`${API_URL}/api/internal/channels/${id}`, {
        method: 'DELETE', headers: getHeaders(),
      });
      if (!res.ok) throw new Error();
      set((s) => ({ channels: s.channels.filter((c) => c.id !== id) }));
      return true;
    } catch { return false; }
  },

  fetchDbTables: async () => {
    try {
      const res = await fetch(`${API_URL}/api/internal/db/tables`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ dbTables: data.tables || data });
    } catch { /* ignore */ }
  },

  fetchDbTable: async (table, page = 1) => {
    set({ loadingDb: true, selectedTable: table });
    try {
      const params = new URLSearchParams({ page, limit: 50 });
      const res = await fetch(`${API_URL}/api/internal/db/${table}?${params}`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ dbRows: data.rows, dbTotal: data.total, dbColumns: data.columns });
    } catch { /* ignore */ }
    set({ loadingDb: false });
  },

  fetchServerConfig: async () => {
    try {
      const res = await fetch(`${API_URL}/api/internal/server/config`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const data = await res.json();
      set({ serverConfig: data });
    } catch { /* ignore */ }
  },

  broadcastUpdate: async (version, changelog) => {
    try {
      const res = await fetch(`${API_URL}/api/internal/broadcast-update`, {
        method: 'POST', headers: getHeaders(), body: JSON.stringify({ version, changelog }),
      });
      if (!res.ok) throw new Error();
      const data = await res.json();
      return data;
    } catch { return null; }
  },
}));
