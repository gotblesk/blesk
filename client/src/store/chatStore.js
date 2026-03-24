import { create } from 'zustand';
import { getCurrentUserId } from '../utils/auth';
import { decryptMessage, fetchPublicKey } from '../utils/cryptoService';
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
  loadingChats: new Set(),
  loadingChatList: false,
  chatsInitialized: false,
  onlineUsers: [],
  userStatuses: {}, // { [userId]: 'online' | 'dnd' | 'invisible' }
  customStatuses: {}, // { [userId]: 'текст статуса' }
  typingUsers: {},

  loadChats: async () => {
    // Защита от параллельных вызовов (всегда возвращаем Promise)
    if (get().loadingChatList) return Promise.resolve();
    set({ loadingChatList: true });
    try {
      const res = await fetch(`${API_URL}/api/chats`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const chats = await res.json();
      set({ chats, chatsInitialized: true });
    } catch (err) {
      console.error('Ошибка загрузки чатов:', err);
      set({ chatsInitialized: true });
    } finally {
      set({ loadingChatList: false });
    }
  },

  openChat: async (chatId) => {
    set((state) => ({ activeChats: new Set([...state.activeChats, chatId]) }));

    const { messages, loadingChats } = get();
    if (messages[chatId] || loadingChats.has(chatId)) return;

    set((state) => ({ loadingChats: new Set([...state.loadingChats, chatId]) }));

    try {
      const res = await fetch(`${API_URL}/api/chats/${chatId}/messages`, { headers: getHeaders() });
      if (!res.ok) throw new Error();
      const msgs = await res.json();

      // Расшифровать E2E сообщения из истории
      // Для личного чата shared key одинаковый в обе стороны,
      // но нужен публичный ключ собеседника (не свой)
      const myId = getCurrentUserId();
      const hasEncrypted = msgs.some((m) => m.encrypted);
      let otherPubKey = null;

      if (hasEncrypted) {
        // Найти ID собеседника: сначала из сообщений, потом из списка чатов
        let otherUserId = msgs.find((m) => m.userId !== myId)?.userId;
        if (!otherUserId) {
          const chat = get().chats.find((c) => c.id === chatId);
          otherUserId = chat?.otherUser?.id;
        }
        if (otherUserId) {
          otherPubKey = await fetchPublicKey(otherUserId);
        }
      }

      for (const msg of msgs) {
        if (msg.encrypted && otherPubKey) {
          try {
            const plain = await decryptMessage(msg.text, otherPubKey, chatId);
            if (plain) msg.text = plain;
            else msg.text = 'Не удалось расшифровать';
          } catch {
            msg.text = 'Ошибка расшифровки';
          }
        }
      }

      set((state) => ({
        messages: { ...state.messages, [chatId]: msgs },
      }));
    } catch (err) {
      console.error('Ошибка загрузки сообщений:', err);
    } finally {
      set((state) => {
        const next = new Set(state.loadingChats);
        next.delete(chatId);
        return { loadingChats: next };
      });
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
  // chatId — если передан, ищем только в этом чате (оптимизация)
  failMessage: (tempId, chatId) => {
    set((state) => {
      if (chatId && state.messages[chatId]) {
        return {
          messages: {
            ...state.messages,
            [chatId]: state.messages[chatId].filter((m) => m.tempId !== tempId),
          },
        };
      }
      // Fallback: поиск по всем чатам
      const newMessages = {};
      for (const [cid, msgs] of Object.entries(state.messages)) {
        newMessages[cid] = msgs.filter((m) => m.tempId !== tempId);
      }
      return { messages: newMessages };
    });
  },

  receiveMessage: (message) => {
    set((state) => {
      const chatId = message.chatId;
      const existing = state.messages[chatId] || [];

      if (existing.some((m) => m.id === message.id)) return state;

      // Превью для медиа-сообщений
      let previewText = message.text;
      if (!previewText && message.type === 'media' && message.attachments?.length) {
        const a = message.attachments[0];
        if (a.mimeType?.startsWith('image/')) previewText = 'Фото';
        else if (a.mimeType?.startsWith('video/')) previewText = 'Видео';
        else if (a.mimeType?.startsWith('audio/')) previewText = 'Аудио';
        else previewText = a.filename || 'Файл';
      }

      const chats = state.chats.map((c) =>
        c.id === chatId
          ? {
              ...c,
              lastMessage: {
                text: previewText,
                username: message.username,
                createdAt: message.createdAt,
              },
              unreadCount: state.activeChats.has(chatId) ? c.unreadCount : c.unreadCount + 1,
            }
          : c
      );

      // Сортируем чаты: новые сообщения наверху
      chats.sort((a, b) => {
        const tA = a.lastMessage?.createdAt ? new Date(a.lastMessage.createdAt).getTime() : 0;
        const tB = b.lastMessage?.createdAt ? new Date(b.lastMessage.createdAt).getTime() : 0;
        return tB - tA;
      });

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
      userStatuses: { ...state.userStatuses, [userId]: status || 'online' },
      customStatuses: { ...state.customStatuses, [userId]: customStatus || '' },
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
