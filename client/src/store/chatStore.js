import { create } from 'zustand';

const API = 'http://localhost:3000';

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
  typingUsers: {},

  loadChats: async () => {
    try {
      const res = await fetch(`${API}/api/chats`, { headers: getHeaders() });
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
      const res = await fetch(`${API}/api/chats/${chatId}/messages`, { headers: getHeaders() });
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
    let userId;
    try {
      userId = JSON.parse(atob(localStorage.getItem('token').split('.')[1])).userId;
    } catch { return; }

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
      await fetch(`${API}/api/chats/${chatId}/read`, {
        method: 'POST',
        headers: getHeaders(),
      });
      set((state) => ({
        chats: state.chats.map((c) => (c.id === chatId ? { ...c, unreadCount: 0 } : c)),
      }));
    } catch {}
  },

  setUserOnline: (userId) => {
    set((state) => ({
      onlineUsers: state.onlineUsers.includes(userId)
        ? state.onlineUsers
        : [...state.onlineUsers, userId],
    }));
  },

  setUserOffline: (userId) => {
    set((state) => ({
      onlineUsers: state.onlineUsers.filter((id) => id !== userId),
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
}));
