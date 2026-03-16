import { create } from 'zustand';
import { getCurrentUserId } from '../utils/auth';
import API_URL from '../config';

function getHeaders() {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${localStorage.getItem('token')}`,
  };
}

export const useChatStore = create((set, get) => ({
  chats: [],
  activeChats: new Set(),
  messages: {},
  onlineUsers: [],
  userStatuses: {}, // { [userId]: 'online' | 'dnd' | 'invisible' }
  typingUsers: {},

  loadChats: async () => {
    try {
      const res = await fetch(`${API_URL}/api/chats`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const chats = await res.json();
      set({ chats });
    } catch (err) {
      console.error('Ошибка загрузки чатов:', err);
    }
  },

  openChat: async (chatId) => {
    set((state) => ({ activeChats: new Set([...state.activeChats, chatId]) }));

    const { messages } = get();
    if (messages[chatId]) return;

    try {
      const res = await fetch(`${API_URL}/api/chats/${chatId}/messages`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const msgs = await res.json();
      set((state) => ({
        messages: { ...state.messages, [chatId]: msgs },
      }));
    } catch (err) {
      console.error('Ошибка загрузки сообщений:', err);
    }
  },

  closeChat: (chatId) => {
    set((state) => {
      const next = new Set(state.activeChats);
      next.delete(chatId);
      return { activeChats: next };
    });
  },

  sendMessage: (chatId, text, tempId) => {
    const userId = getCurrentUserId();
    if (!userId) return;

    const tempMsg = {
      id: tempId,
      tempId,
      chatId,
      userId,
      text,
      createdAt: new Date().toISOString(),
      pending: true,
    };

    set((state) => ({
      messages: {
        ...state.messages,
        [chatId]: [...(state.messages[chatId] || []), tempMsg],
      },
    }));
  },

  confirmMessage: (tempId, serverMsg) => {
    const chatId = serverMsg.chatId;
    set((state) => ({
      messages: {
        ...state.messages,
        [chatId]: (state.messages[chatId] || []).map((m) =>
          m.tempId === tempId ? { ...serverMsg, pending: false } : m
        ),
      },
    }));
  },

  // Пометить сообщение как ошибочное (убрать из списка)
  failMessage: (tempId) => {
    set((state) => {
      const newMessages = {};
      for (const [chatId, msgs] of Object.entries(state.messages)) {
        newMessages[chatId] = msgs.filter((m) => m.tempId !== tempId);
      }
      return { messages: newMessages };
    });
  },

  receiveMessage: (message) => {
    set((state) => {
      const chatId = message.chatId;
      const existing = state.messages[chatId] || [];

      if (existing.some((m) => m.id === message.id)) return state;

      const chats = state.chats.map((c) =>
        c.id === chatId
          ? {
              ...c,
              lastMessage: {
                text: message.text,
                username: message.username,
                createdAt: message.createdAt,
              },
              unreadCount: state.activeChats.has(chatId) ? c.unreadCount : c.unreadCount + 1,
            }
          : c
      );

      return {
        messages: { ...state.messages, [chatId]: [...existing, message] },
        chats,
      };
    });
  },

  markAsRead: async (chatId) => {
    try {
      await fetch(`${API_URL}/api/chats/${chatId}/read`, {
        method: 'POST',
        headers: getHeaders(),
      });
      set((state) => ({
        chats: state.chats.map((c) => (c.id === chatId ? { ...c, unreadCount: 0 } : c)),
      }));
    } catch {}
  },

  setUserOnline: (userId, status) => {
    set((state) => ({
      onlineUsers: state.onlineUsers.includes(userId)
        ? state.onlineUsers
        : [...state.onlineUsers, userId],
      userStatuses: { ...state.userStatuses, [userId]: status || 'online' },
    }));
  },

  setUserOffline: (userId) => {
    set((state) => {
      const { [userId]: _, ...rest } = state.userStatuses;
      return {
        onlineUsers: state.onlineUsers.filter((id) => id !== userId),
        userStatuses: rest,
      };
    });
  },

  setUserStatus: (userId, status, customStatus) => {
    set((state) => ({
      userStatuses: { ...state.userStatuses, [userId]: status },
    }));
  },

  setTyping: (chatId, userId, isTyping) => {
    set((state) => ({
      typingUsers: {
        ...state.typingUsers,
        [chatId]: isTyping
          ? [...(state.typingUsers[chatId] || []).filter((id) => id !== userId), userId]
          : (state.typingUsers[chatId] || []).filter((id) => id !== userId),
      },
    }));
  },

  // ═══ Управление чатами ═══
  addChat: (chat) => {
    set((state) => {
      if (state.chats.some((c) => c.id === chat.id)) return state;
      return { chats: [chat, ...state.chats] };
    });
  },

  updateChat: (chatId, updates) => {
    set((state) => ({
      chats: state.chats.map((c) => (c.id === chatId ? { ...c, ...updates } : c)),
    }));
  },

  removeChat: (chatId) => {
    set((state) => ({
      chats: state.chats.filter((c) => c.id !== chatId),
      activeChats: new Set([...state.activeChats].filter((id) => id !== chatId)),
    }));
  },
}));
