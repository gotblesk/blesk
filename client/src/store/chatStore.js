import { create } from 'zustand';
import { getCurrentUserId } from '../utils/auth';
import { decryptMessage, fetchPublicKey } from '../utils/cryptoService';
import { getCachedMessages, setCachedMessages, appendCachedMessage, updateCachedMessage, removeCachedMessage } from '../utils/messageCache';
import API_URL from '../config';
import { getAuthHeaders } from '../utils/authFetch';

function getHeaders() {
  return {
    'Content-Type': 'application/json',
    ...getAuthHeaders(),
  };
}

async function fetchWithAuth(url, options = {}) {
  const res = await fetch(url, { ...options, credentials: 'include', headers: { ...getHeaders(), ...options.headers } });
  if (res.status === 401) {
    const refreshToken = localStorage.getItem('refreshToken');
    if (refreshToken) {
      const refreshRes = await fetch(`${API_URL}/api/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (refreshRes.ok) {
        const data = await refreshRes.json();
        localStorage.setItem('token', data.token);
        if (data.refreshToken) localStorage.setItem('refreshToken', data.refreshToken);
        return fetch(url, { ...options, credentials: 'include', headers: { ...getHeaders(), ...options.headers } });
      }
    }
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    window.location.reload();
  }
  return res;
}

function loadPinnedChats() {
  try {
    const raw = localStorage.getItem('blesk-pinned-chats');
    return raw ? new Set(JSON.parse(raw)) : new Set();
  } catch { return new Set(); }
}

function savePinnedChats(pinned) {
  localStorage.setItem('blesk-pinned-chats', JSON.stringify([...pinned]));
}

export const useChatStore = create((set, get) => ({
  chats: [],
  activeChats: new Set(),
  pinnedChats: loadPinnedChats(),
  messages: {},
  loadingChats: new Set(),
  // [CRIT-2] Состояние подключения (false до первого connect)
  isConnected: false,
  lastConnectedAt: null,
  // [MED-4] Version counter для предотвращения stale fetch overwrite
  _chatLoadVersions: {},
  loadingChatList: false,
  chatsInitialized: false,
  onlineUsers: [],
  userStatuses: {}, // { [userId]: 'online' | 'dnd' | 'invisible' }
  customStatuses: {}, // { [userId]: 'текст статуса' }
  typingUsers: {},
  reactions: {}, // { [messageId]: { [emoji]: { count, users, userIds } } }

  loadChats: async () => {
    // Защита от параллельных вызовов (всегда возвращаем Promise)
    if (get().loadingChatList) return Promise.resolve();
    set({ loadingChatList: true });
    try {
      const res = await fetchWithAuth(`${API_URL}/api/chats`);
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

    // [CRIT-3] Мгновенно загрузить из IndexedDB кеша (показать пока ждём сервер)
    try {
      const cached = await getCachedMessages(chatId);
      if (cached && cached.length > 0 && !get().messages[chatId]) {
        set((state) => ({ messages: { ...state.messages, [chatId]: cached } }));
      }
    } catch (err) { console.error('chatStore IndexedDB cache read:', err?.message || err); }

    // [MED-4] Version counter — обнаруживать stale ответы
    const versions = get()._chatLoadVersions;
    const loadVersion = (versions[chatId] || 0) + 1;
    set((state) => ({
      loadingChats: new Set([...state.loadingChats, chatId]),
      _chatLoadVersions: { ...state._chatLoadVersions, [chatId]: loadVersion },
    }));

    try {
      const res = await fetch(`${API_URL}/api/chats/${chatId}/messages?limit=200`, { headers: getHeaders(), credentials: 'include' });
      if (!res.ok) throw new Error();
      const msgs = await res.json();

      // [MED-4] Проверить что это актуальный запрос (не stale)
      if (get()._chatLoadVersions[chatId] !== loadVersion) return;

      // Расшифровать E2E сообщения из истории
      const myId = getCurrentUserId();
      const hasEncrypted = msgs.some((m) => m.encrypted);
      let otherPubKey = null;

      if (hasEncrypted) {
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

      // [MED-4] Финальная проверка перед записью
      if (get()._chatLoadVersions[chatId] !== loadVersion) return;

      set((state) => ({
        messages: { ...state.messages, [chatId]: msgs },
      }));

      // [CRIT-3] Сохранить в IndexedDB кеш
      setCachedMessages(chatId, msgs).catch(err => console.error('chatStore setCachedMessages:', err?.message || err));
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

  // [CRIT-4] Обновить сообщения в уже открытом чате (после reconnect)
  refreshChat: async (chatId) => {
    try {
      const existing = get().messages[chatId];
      if (!existing) return;
      const res = await fetch(`${API_URL}/api/chats/${chatId}/messages?limit=200`, { headers: getHeaders(), credentials: 'include' });
      if (!res.ok) return;
      const msgs = await res.json();
      // Merge: добавить только новые сообщения (без дублирования)
      const existingIds = new Set(existing.map(m => m.id).filter(Boolean));
      const newMsgs = msgs.filter(m => !existingIds.has(m.id));
      if (newMsgs.length === 0) return;
      set((state) => ({
        messages: {
          ...state.messages,
          [chatId]: [...(state.messages[chatId] || []), ...newMsgs]
            .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt)),
        },
      }));
    } catch (err) { console.error('chatStore refreshChat:', err?.message || err); }
  },

  closeChat: (chatId) => {
    set((state) => {
      const next = new Set(state.activeChats);
      next.delete(chatId);
      // Очистить сообщения неактивных чатов для экономии памяти
      const newMessages = { ...state.messages };
      delete newMessages[chatId];
      return { activeChats: next, messages: newMessages };
    });
  },

  sendMessage: (chatId, text, tempId) => {
    const userId = getCurrentUserId();
    if (!userId) return;

    const isOffline = !get().isConnected;
    const tempMsg = {
      id: tempId,
      tempId,
      chatId,
      userId,
      text,
      createdAt: new Date().toISOString(),
      pending: true,
      offline: isOffline,
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

  // [CRIT-1] Пометить сообщение как failed (не удалять — пользователь может retry)
  failMessage: (tempId, chatId) => {
    set((state) => {
      const markFailed = (msgs) =>
        msgs.map((m) => m.tempId === tempId ? { ...m, pending: false, failed: true } : m);

      if (chatId && state.messages[chatId]) {
        return {
          messages: { ...state.messages, [chatId]: markFailed(state.messages[chatId]) },
        };
      }
      const newMessages = {};
      for (const [cid, msgs] of Object.entries(state.messages)) {
        newMessages[cid] = markFailed(msgs);
      }
      return { messages: newMessages };
    });
  },

  // Удалить failed сообщение вручную
  removeFailedMessage: (tempId, chatId) => {
    set((state) => {
      if (!chatId || !state.messages[chatId]) return state;
      return {
        messages: {
          ...state.messages,
          [chatId]: state.messages[chatId].filter((m) => m.tempId !== tempId),
        },
      };
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

      // Поднимаем чат с новым сообщением наверх (O(N) вместо O(N log N))
      const idx = chats.findIndex((c) => c.id === chatId);
      if (idx > 0) {
        const [chat] = chats.splice(idx, 1);
        chats.unshift(chat);
      }

      // [CRIT-3] Кешировать новое сообщение в IndexedDB
      appendCachedMessage(chatId, message).catch(err => console.error('chatStore appendCachedMessage:', err?.message || err));

      // [CRIT-4] Ограничить массив в памяти — не более 500 сообщений
      let updatedMsgs = [...existing, message];
      if (updatedMsgs.length > 500) updatedMsgs = updatedMsgs.slice(-400);

      return {
        messages: { ...state.messages, [chatId]: updatedMsgs },
        chats,
      };
    });
  },

  markAsRead: async (chatId, socketRef) => {
    try {
      await fetch(`${API_URL}/api/chats/${chatId}/read`, {
        method: 'POST',
        headers: getHeaders(),
        credentials: 'include',
      });

      // [Read Receipts] Собрать ID непрочитанных сообщений и отправить read receipt
      const msgs = get().messages[chatId] || [];
      const myId = getCurrentUserId();
      const unreadIds = msgs
        .filter(m => m.id && m.userId !== myId && !m.readBy?.includes(myId))
        .map(m => m.id);

      if (unreadIds.length > 0 && socketRef?.current) {
        socketRef.current.emit('message:read', { chatId, messageIds: unreadIds });
      }

      // Обновить локальный readBy
      set((state) => ({
        chats: state.chats.map((c) => (c.id === chatId ? { ...c, unreadCount: 0 } : c)),
        messages: {
          ...state.messages,
          [chatId]: (state.messages[chatId] || []).map(m =>
            unreadIds.includes(m.id) ? { ...m, readBy: [...(m.readBy || []), myId] } : m
          ),
        },
      }));
    } catch (err) { console.error('chatStore markMessagesRead:', err?.message || err); }
  },

  setUserOnline: (userId, status) => {
    const state = get();
    const resolvedStatus = status || 'online';
    if (state.onlineUsers.includes(userId) && state.userStatuses[userId] === resolvedStatus) return;
    set({
      onlineUsers: state.onlineUsers.includes(userId)
        ? state.onlineUsers
        : [...state.onlineUsers, userId],
      userStatuses: { ...state.userStatuses, [userId]: resolvedStatus },
    });
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

  // Обновить аватар пользователя во всех чатах
  updateUserAvatar: (userId, avatar) => {
    set((state) => ({
      chats: state.chats.map((chat) => {
        if (chat.otherUser?.id === userId) {
          return { ...chat, otherUser: { ...chat.otherUser, avatar } };
        }
        return chat;
      }),
    }));
  },

  togglePinChat: (chatId) => {
    set((state) => {
      const next = new Set(state.pinnedChats);
      if (next.has(chatId)) next.delete(chatId);
      else next.add(chatId);
      savePinnedChats(next);
      return { pinnedChats: next };
    });
  },
}));
