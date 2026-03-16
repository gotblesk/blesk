import { useEffect, useRef } from 'react';
import { io } from 'socket.io-client';
import { useChatStore } from '../store/chatStore';
import { useCallStore } from '../store/callStore';
import { useNotificationStore } from '../store/notificationStore';
import { useSettingsStore } from '../store/settingsStore';
import { getCurrentUserId } from '../utils/auth';
import API_URL from '../config';

export function useSocket() {
  const socketRef = useRef(null);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) return;

    const { showOnline } = useSettingsStore.getState();

    const socket = io(API_URL, {
      auth: { token, showOnline },
      reconnection: true,
      reconnectionDelay: 1000,
      reconnectionAttempts: Infinity,
    });

    socketRef.current = socket;

    const userId = getCurrentUserId();

    // ═══ Сообщения ═══
    socket.on('message:new', (msg) => {
      if (msg.userId === userId && msg.tempId) {
        useChatStore.getState().confirmMessage(msg.tempId, msg);
      } else {
        useChatStore.getState().receiveMessage(msg);

        // Уведомления и звуки для входящих сообщений
        const s = useSettingsStore.getState();
        if (s.notifications && s.notifMessages && document.hidden) {
          try {
            new Notification(msg.user?.username || 'blesk', {
              body: msg.text?.slice(0, 100) || 'Новое сообщение',
              silent: !s.sounds,
            });
          } catch { /* Notification API недоступен */ }
        }
      }
    });

    socket.on('message:error', ({ tempId, error }) => {
      console.error('Message error:', tempId, error);
      // Убрать неотправленное сообщение из UI
      useChatStore.getState().failMessage(tempId);
    });

    // ═══ Онлайн/офлайн/статус ═══
    socket.on('user:online', ({ userId: uid, status }) => useChatStore.getState().setUserOnline(uid, status));
    socket.on('user:offline', ({ userId: uid }) => useChatStore.getState().setUserOffline(uid));
    socket.on('user:statusChange', ({ userId: uid, status, customStatus }) => {
      useChatStore.getState().setUserStatus(uid, status, customStatus);
    });

    // ═══ Набор текста ═══
    socket.on('typing:start', ({ chatId, userId: uid }) => useChatStore.getState().setTyping(chatId, uid, true));
    socket.on('typing:stop', ({ chatId, userId: uid }) => useChatStore.getState().setTyping(chatId, uid, false));

    // ═══ Уведомления ═══
    socket.on('notification:new', (notification) => {
      useNotificationStore.getState().addNotification(notification);
    });

    // ═══ Звонки ═══
    socket.on('call:incoming', (data) => {
      useCallStore.getState().setIncomingCall(data);
    });

    socket.on('call:accepted', ({ chatId, userId: uid }) => {
      const store = useCallStore.getState();
      if (store.activeCall?.chatId === chatId) {
        store.addCallParticipant(uid);
        if (store.activeCall.status === 'ringing') {
          store.setActiveCall({ ...store.activeCall, status: 'active' });
        }
      }
    });

    socket.on('call:declined', ({ chatId }) => {
      const store = useCallStore.getState();
      if (store.activeCall?.chatId === chatId) {
        store.clearActiveCall();
      }
    });

    socket.on('call:missed', ({ chatId }) => {
      useCallStore.getState().clearActiveCall();
      useCallStore.getState().clearIncomingCall();
    });

    socket.on('call:cancelled', () => {
      useCallStore.getState().clearIncomingCall();
    });

    socket.on('call:user-left', ({ chatId, userId: uid }) => {
      useCallStore.getState().removeCallParticipant(uid);
    });

    socket.on('call:ended', () => {
      useCallStore.getState().clearActiveCall();
    });

    socket.on('call:error', ({ chatId, error }) => {
      console.error('Call error:', chatId, error);
      useCallStore.getState().clearActiveCall();
    });

    // ═══ Групповые события ═══
    socket.on('group:member-added', () => useChatStore.getState().loadChats());
    socket.on('group:member-removed', ({ userId: uid }) => {
      if (uid === userId) {
        // Нас удалили — перезагрузить
      }
      useChatStore.getState().loadChats();
    });
    socket.on('group:updated', () => useChatStore.getState().loadChats());

    // ═══ Закреплённые сообщения ═══
    socket.on('message:pinned', ({ messageId, chatId, pinned }) => {
      const state = useChatStore.getState();
      const msgs = state.messages[chatId];
      if (msgs) {
        const updated = msgs.map((m) =>
          m.id === messageId ? { ...m, pinned } : m
        );
        useChatStore.setState({
          messages: { ...state.messages, [chatId]: updated },
        });
      }
    });

    socket.on('connect_error', (err) => {
      console.error('Socket connection error:', err.message);
    });

    return () => {
      socket.disconnect();
      socketRef.current = null;
    };
  }, []);

  return socketRef;
}
