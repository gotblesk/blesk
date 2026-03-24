import { useEffect, useRef } from 'react';
import { io } from 'socket.io-client';
import { useChatStore } from '../store/chatStore';
import { useChannelStore } from '../store/channelStore';
import { useCallStore } from '../store/callStore';
import { useNotificationStore } from '../store/notificationStore';
import { useSettingsStore } from '../store/settingsStore';
import { getCurrentUserId } from '../utils/auth';
import { decryptMessage, fetchPublicKey } from '../utils/cryptoService';
import API_URL from '../config';
import { soundReceive, soundNotification, soundRingtoneStart, soundRingtoneStop, soundCallAccepted, soundCallEnded } from '../utils/sounds';

export function useSocket() {
  const socketRef = useRef(null);
  const typingTimersRef = useRef(new Map());

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) return;

    const { showOnline } = useSettingsStore.getState();

    let hasConnectedOnce = false;

    const socket = io(API_URL, {
      auth: { token, showOnline },
      reconnection: true,
      reconnectionDelay: 1000,
      reconnectionAttempts: Infinity,
    });

    socketRef.current = socket;

    const userId = getCurrentUserId();

    // Ресинк состояния при переподключении (не первый коннект)
    socket.on('connect', () => {
      if (hasConnectedOnce) {
        useChatStore.getState().loadChats?.();
      }
      hasConnectedOnce = true;
    });

    // При ошибке подключения — обновить токен и переподключиться
    const handleConnectError = (err) => {
      console.error('Socket connection error:', err.message);
      const freshToken = localStorage.getItem('token');
      if (freshToken && freshToken !== socket.auth.token) {
        socket.auth.token = freshToken;
        socket.connect();
      }
    };

    // ═══ Сообщения ═══
    const handleMessageNew = async (msg) => {
      // Посты каналов — в channelStore
      if (msg.isChannel) {
        useChannelStore.getState().receivePost(msg);
        return;
      }

      // Расшифровать E2E сообщение от другого пользователя
      if (msg.encrypted && msg.userId !== userId) {
        try {
          const senderPubKey = await fetchPublicKey(msg.userId);
          if (senderPubKey) {
            const plaintext = await decryptMessage(msg.text, senderPubKey, msg.chatId);
            if (plaintext) {
              msg.text = plaintext;
            } else {
              msg.text = 'Не удалось расшифровать';
            }
          }
        } catch {
          msg.text = 'Ошибка расшифровки';
        }
      }

      // Свои зашифрованные сообщения — восстановить оригинальный текст из tempMsg
      if (msg.userId === userId && msg.tempId) {
        useChatStore.getState().confirmMessage(msg.tempId, msg);
      } else {
        useChatStore.getState().receiveMessage(msg);

        // Звук входящего сообщения (не для своих с другого устройства)
        if (msg.userId !== userId) {
          soundReceive();
        }

        // Уведомления для входящих сообщений
        const s = useSettingsStore.getState();
        if (s.notifications && s.notifMessages && document.hidden) {
          try {
            new Notification(msg.user?.username || 'blesk', {
              body: msg.encrypted ? 'Зашифрованное сообщение' : (msg.text?.slice(0, 100) || 'Новое сообщение'),
              silent: !s.sounds,
            });
          } catch { /* Notification API недоступен */ }
        }
      }
    };

    const handleMessageError = ({ tempId, error }) => {
      console.error('Message error:', tempId, error);
      useChatStore.getState().failMessage(tempId);
    };

    // ═══ Редактирование/удаление сообщений ═══
    const handleMessageEdited = ({ messageId, chatId, text, editedAt }) => {
      useChatStore.setState((state) => {
        const msgs = state.messages[chatId];
        if (!msgs) return state;
        return {
          messages: {
            ...state.messages,
            [chatId]: msgs.map((m) => m.id === messageId ? { ...m, text, editedAt } : m),
          },
        };
      });
    };

    const handleMessageDeleted = ({ messageId, chatId }) => {
      useChatStore.setState((state) => {
        const msgs = state.messages[chatId];
        if (!msgs) return state;
        return {
          messages: {
            ...state.messages,
            [chatId]: msgs.filter((m) => m.id !== messageId),
          },
        };
      });
    };

    // ═══ Онлайн/офлайн/статус ═══
    const handleUserOnline = ({ userId: uid, status }) => useChatStore.getState().setUserOnline(uid, status);
    const handleUserOffline = ({ userId: uid }) => useChatStore.getState().setUserOffline(uid);
    const handleUserStatusChange = ({ userId: uid, status, customStatus }) => {
      useChatStore.getState().setUserStatus(uid, status, customStatus);
    };

    // Обновление аватара пользователя (профиль изменён)
    const handleUserUpdated = (data) => {
      const { userId: uid, avatar } = data;
      if (uid && avatar) {
        useChatStore.getState().updateUserAvatar(uid, avatar);
      }
    };

    // Удаление из друзей — перезагрузить чаты
    const handleFriendRemoved = (data) => {
      const { userId: uid, friendId } = data;
      const myId = getCurrentUserId();
      if (uid === myId || friendId === myId) {
        useChatStore.getState().loadChats();
      }
    };

    // Удаление канала — убрать из списка
    const handleChannelDeleted = (data) => {
      const { channelId } = data;
      if (channelId) {
        const channelState = useChannelStore.getState();
        if (channelState.removeChannel) {
          channelState.removeChannel(channelId);
        }
      }
    };

    // ═══ Набор текста ═══
    const handleTypingStart = ({ chatId, userId: uid }) => {
      useChatStore.getState().setTyping(chatId, uid, true);
      // Авто-очистка: если typing:stop не пришёл за 8 сек — сбросить
      const timerKey = `${chatId}:${uid}`;
      const prev = typingTimersRef.current.get(timerKey);
      if (prev) clearTimeout(prev);
      typingTimersRef.current.set(timerKey, setTimeout(() => {
        useChatStore.getState().setTyping(chatId, uid, false);
        typingTimersRef.current.delete(timerKey);
      }, 8000));
    };
    const handleTypingStop = ({ chatId, userId: uid }) => {
      useChatStore.getState().setTyping(chatId, uid, false);
      const timerKey = `${chatId}:${uid}`;
      const prev = typingTimersRef.current.get(timerKey);
      if (prev) {
        clearTimeout(prev);
        typingTimersRef.current.delete(timerKey);
      }
    };

    // ═══ Уведомления ═══
    const handleNotificationNew = (notification) => {
      useNotificationStore.getState().addNotification(notification);
      const ns = useSettingsStore.getState();
      const type = notification.type;
      if (type === 'friend_request' && !ns.notifFriends) return;
      if (type === 'mention' && !ns.notifMentions) return;
      soundNotification(notification.fromUser?.hue || 0);
    };

    // ═══ Звонки ═══
    const handleCallIncoming = (data) => {
      useCallStore.getState().setIncomingCall(data);
      soundRingtoneStart();
    };

    const handleCallAccepted = ({ chatId, userId: uid }) => {
      soundRingtoneStop();
      soundCallAccepted();
      const store = useCallStore.getState();
      if (store.activeCall?.chatId === chatId) {
        store.addCallParticipant(uid);
        if (store.activeCall.status === 'ringing') {
          store.setActiveCall({ ...store.activeCall, status: 'active' });
        }
      }
    };

    const handleCallDeclined = ({ chatId }) => {
      const store = useCallStore.getState();
      if (store.activeCall?.chatId === chatId) {
        store.clearActiveCall();
      }
    };

    const handleCallMissed = ({ chatId }) => {
      soundRingtoneStop();
      useCallStore.getState().clearActiveCall();
      useCallStore.getState().clearIncomingCall();
    };

    const handleCallCancelled = () => {
      soundRingtoneStop();
      useCallStore.getState().clearIncomingCall();
    };

    const handleCallUserLeft = ({ chatId, userId: uid }) => {
      useCallStore.getState().removeCallParticipant(uid);
    };

    const handleCallEnded = () => {
      soundRingtoneStop();
      soundCallEnded();
      useCallStore.getState().clearActiveCall();
    };

    const handleCallError = ({ chatId, error }) => {
      console.error('Call error:', chatId, error);
      useCallStore.getState().clearActiveCall();
    };

    // ═══ Бан аккаунта ═══
    const handleAuthBanned = ({ reason }) => {
      console.warn('Аккаунт заблокирован:', reason);
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      window.location.reload();
    };

    // ═══ Групповые события ═══
    const handleGroupMemberAdded = () => useChatStore.getState().loadChats();
    const handleGroupMemberRemoved = ({ userId: uid }) => {
      if (uid === userId) {
        // Нас удалили — перезагрузить
      }
      useChatStore.getState().loadChats();
    };
    const handleGroupUpdated = () => useChatStore.getState().loadChats();

    // ═══ Закреплённые сообщения ═══
    const handleMessagePinned = ({ messageId, chatId, pinned }) => {
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
    };

    // ═══ Регистрация обработчиков ═══
    socket.on('connect_error', handleConnectError);
    socket.on('message:new', handleMessageNew);
    socket.on('message:error', handleMessageError);
    socket.on('message:edited', handleMessageEdited);
    socket.on('message:deleted', handleMessageDeleted);
    socket.on('user:online', handleUserOnline);
    socket.on('user:offline', handleUserOffline);
    socket.on('user:statusChange', handleUserStatusChange);
    socket.on('user:updated', handleUserUpdated);
    socket.on('friend:removed', handleFriendRemoved);
    socket.on('channel:deleted', handleChannelDeleted);
    socket.on('typing:start', handleTypingStart);
    socket.on('typing:stop', handleTypingStop);
    socket.on('notification:new', handleNotificationNew);
    socket.on('call:incoming', handleCallIncoming);
    socket.on('call:accepted', handleCallAccepted);
    socket.on('call:declined', handleCallDeclined);
    socket.on('call:missed', handleCallMissed);
    socket.on('call:cancelled', handleCallCancelled);
    socket.on('call:user-left', handleCallUserLeft);
    socket.on('call:ended', handleCallEnded);
    socket.on('call:error', handleCallError);
    socket.on('auth:banned', handleAuthBanned);
    socket.on('group:member-added', handleGroupMemberAdded);
    socket.on('group:member-removed', handleGroupMemberRemoved);
    socket.on('group:updated', handleGroupUpdated);
    socket.on('message:pinned', handleMessagePinned);

    return () => {
      // Очистить все таймеры typing auto-clear
      typingTimersRef.current.forEach((timer) => clearTimeout(timer));
      typingTimersRef.current.clear();
      socket.off('connect_error', handleConnectError);
      socket.off('message:new', handleMessageNew);
      socket.off('message:error', handleMessageError);
      socket.off('message:edited', handleMessageEdited);
      socket.off('message:deleted', handleMessageDeleted);
      socket.off('user:online', handleUserOnline);
      socket.off('user:offline', handleUserOffline);
      socket.off('user:statusChange', handleUserStatusChange);
      socket.off('user:updated', handleUserUpdated);
      socket.off('friend:removed', handleFriendRemoved);
      socket.off('channel:deleted', handleChannelDeleted);
      socket.off('typing:start', handleTypingStart);
      socket.off('typing:stop', handleTypingStop);
      socket.off('notification:new', handleNotificationNew);
      socket.off('call:incoming', handleCallIncoming);
      socket.off('call:accepted', handleCallAccepted);
      socket.off('call:declined', handleCallDeclined);
      socket.off('call:missed', handleCallMissed);
      socket.off('call:cancelled', handleCallCancelled);
      socket.off('call:user-left', handleCallUserLeft);
      socket.off('call:ended', handleCallEnded);
      socket.off('call:error', handleCallError);
      socket.off('auth:banned', handleAuthBanned);
      socket.off('group:member-added', handleGroupMemberAdded);
      socket.off('group:member-removed', handleGroupMemberRemoved);
      socket.off('group:updated', handleGroupUpdated);
      socket.off('message:pinned', handleMessagePinned);
      socket.disconnect();
      socketRef.current = null;
    };
  }, []);

  return socketRef;
}
