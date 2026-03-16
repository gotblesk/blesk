import { useEffect, useRef } from 'react';
import { io } from 'socket.io-client';
import { useChatStore } from '../store/chatStore';
import { useCallStore } from '../store/callStore';
import { useNotificationStore } from '../store/notificationStore';
import API_URL from '../config';

export function useSocket() {
  const socketRef = useRef(null);
  const { receiveMessage, setUserOnline, setUserOffline, setTyping, confirmMessage, loadChats } =
    useChatStore();

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) return;

    const socket = io(API_URL, {
      auth: { token },
      reconnection: true,
      reconnectionDelay: 1000,
      reconnectionAttempts: Infinity,
    });

    socketRef.current = socket;

    let userId;
    try {
      userId = JSON.parse(atob(token.split('.')[1])).userId;
    } catch {}

    // ═══ Сообщения ═══
    socket.on('message:new', (msg) => {
      if (msg.userId === userId && msg.tempId) {
        confirmMessage(msg.tempId, msg);
      } else {
        receiveMessage(msg);
      }
    });

    socket.on('message:error', ({ tempId, error }) => {
      console.error('Message error:', tempId, error);
    });

    // ═══ Онлайн/офлайн ═══
    socket.on('user:online', ({ userId: uid }) => setUserOnline(uid));
    socket.on('user:offline', ({ userId: uid }) => setUserOffline(uid));

    // ═══ Набор текста ═══
    socket.on('typing:start', ({ chatId, userId: uid }) => setTyping(chatId, uid, true));
    socket.on('typing:stop', ({ chatId, userId: uid }) => setTyping(chatId, uid, false));

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
    socket.on('group:member-added', () => loadChats());
    socket.on('group:member-removed', ({ userId: uid }) => {
      if (uid === userId) {
        // Нас удалили — перезагрузить
      }
      loadChats();
    });
    socket.on('group:updated', () => loadChats());

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
  }, [receiveMessage, setUserOnline, setUserOffline, setTyping, confirmMessage, loadChats]);

  return socketRef;
}
