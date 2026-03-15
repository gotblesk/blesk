import { useEffect, useRef } from 'react';
import { io } from 'socket.io-client';
import { useChatStore } from '../store/chatStore';
import { useNotificationStore } from '../store/notificationStore';

export function useSocket() {
  const socketRef = useRef(null);
  const { receiveMessage, setUserOnline, setUserOffline, setTyping, confirmMessage } =
    useChatStore();

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) return;

    const socket = io('http://localhost:3000', {
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

    socket.on('user:online', ({ userId: uid }) => setUserOnline(uid));
    socket.on('user:offline', ({ userId: uid }) => setUserOffline(uid));

    socket.on('typing:start', ({ chatId, userId: uid }) => setTyping(chatId, uid, true));
    socket.on('typing:stop', ({ chatId, userId: uid }) => setTyping(chatId, uid, false));

    // Уведомления в реальном времени
    socket.on('notification:new', (notification) => {
      useNotificationStore.getState().addNotification(notification);
    });

    socket.on('connect_error', (err) => {
      console.error('Socket connection error:', err.message);
    });

    return () => {
      socket.disconnect();
      socketRef.current = null;
    };
  }, [receiveMessage, setUserOnline, setUserOffline, setTyping, confirmMessage]);

  return socketRef;
}
