import { useEffect, useRef } from 'react';
import { io } from 'socket.io-client';
import { useChatStore } from '../store/chatStore';
import { useChannelStore } from '../store/channelStore';
import { useCallStore } from '../store/callStore';
import { useNotificationStore } from '../store/notificationStore';
import { useSettingsStore } from '../store/settingsStore';
import { getCurrentUserId } from '../utils/auth';
import { decryptMessage, fetchPublicKey, invalidateUserKeys } from '../utils/cryptoService';
import { shieldDecrypt, replenishOPKs } from '../utils/shieldService';
import { getToken, getRefreshToken, setTokens, clearTokens } from '../utils/authFetch';
import API_URL from '../config';
import { soundReceive, soundNotification, soundRingtoneStart, soundRingtoneStop, soundCallAccepted, soundCallEnded, soundCallDeclined } from '../utils/sounds';

export function useSocket() {
  const socketRef = useRef(null);
  const typingTimersRef = useRef(new Map());

  useEffect(() => {
    const token = getToken();
    if (!token) return;

    const { showOnline } = useSettingsStore.getState();

    let hasConnectedOnce = false;

    // [IMP-2] Exponential backoff для reconnect
    const socket = io(API_URL, {
      auth: { token, showOnline },
      withCredentials: true,
      reconnection: true,
      reconnectionDelay: 1000,
      reconnectionDelayMax: 30000,
      randomizationFactor: 0.5,
      reconnectionAttempts: Infinity,
    });

    socketRef.current = socket;

    const userId = getCurrentUserId();

    // [CRIT-2] Отслеживание состояния подключения
    const handleConnect = () => {
      // Обновить состояние подключения
      useChatStore.setState({ isConnected: true, lastConnectedAt: Date.now() });

      // Загрузить чаты при каждом (пере)подключении
      useChatStore.getState().loadChats?.();

      if (hasConnectedOnce) {
        // Перезагрузить сообщения для всех активных чатов (delta fetch)
        const activeChats = useChatStore.getState().activeChats;
        for (const chatId of activeChats) {
          useChatStore.getState().refreshChat?.(chatId);
        }

        // Повторная отправка неотправленных сообщений (failed + offline)
        const allMessages = useChatStore.getState().messages;
        for (const chatId of Object.keys(allMessages)) {
          const msgs = allMessages[chatId];
          if (!msgs) continue;
          for (const msg of msgs) {
            if (!msg.failed && !msg.offline) continue;
            // Убрать failed/offline, поставить pending
            const matchKey = msg.tempId || msg.id;
            useChatStore.setState((state) => {
              const chatMsgs = state.messages[chatId];
              if (!chatMsgs) return state;
              return {
                messages: {
                  ...state.messages,
                  [chatId]: chatMsgs.map((m) => {
                    const mKey = m.tempId || m.id;
                    return mKey === matchKey ? { ...m, failed: false, offline: false, pending: true } : m;
                  }),
                },
              };
            });
            // Переотправить
            socket.emit('message:send', {
              chatId,
              text: msg.text,
              tempId: msg.tempId || msg.id,
            });
          }
        }
      }
      hasConnectedOnce = true;
    };
    socket.on('connect', handleConnect);

    // [CRIT-2] Офлайн-индикатор + [IMP-5] Очистка звонков
    const handleDisconnect = (reason) => {
      useChatStore.setState({ isConnected: false });
      // [IMP-5] Очистить состояние звонка при дисконнекте
      useCallStore.getState().clearActiveCall();
      useCallStore.getState().clearIncomingCall();
    };
    socket.on('disconnect', handleDisconnect);

    // При ошибке подключения — обновить токен через refresh API и переподключиться
    const handleConnectError = async (err) => {
      console.error('Socket connection error:', err.message);
      // Пробуем обновить токен через refresh API (cookies + in-memory fallback)
      try {
        const refreshToken = getRefreshToken();
        const res = await fetch(`${API_URL}/api/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ refreshToken: refreshToken || undefined }),
        });
        if (res.ok) {
          const data = await res.json();
          setTokens(data.token, data.refreshToken || refreshToken);
          socket.auth.token = data.token;
          return; // socket.io автоматически повторит подключение
        }
      } catch { /* сеть недоступна — socket.io повторит по расписанию */ }
      // Если refresh не удался — подставить актуальный in-memory токен
      const freshToken = getToken();
      if (freshToken) socket.auth.token = freshToken;
    };

    // Heartbeat: отправлять ping каждые 45 сек чтобы Cloudflare не закрыл соединение
    const heartbeatInterval = setInterval(() => {
      if (socket.connected) socket.emit('ping');
    }, 45000);

    // При возврате окна в видимое состояние — обновить токен и переподключиться
    const handleVisibilityChange = () => {
      if (document.visibilityState !== 'visible') return;
      const freshToken = getToken();
      if (freshToken) socket.auth.token = freshToken;
      if (!socket.connected) socket.connect();
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);

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
          // blesk Shield (Double Ratchet) — приоритет
          if (msg.text?.startsWith('SHIELD:1:')) {
            const plaintext = await shieldDecrypt(msg.userId, msg.text, msg.shieldHeader);
            if (plaintext) {
              msg.text = plaintext;
            } else {
              msg.text = 'Не удалось расшифровать (Shield)';
            }
          } else {
            // Legacy decryption
            const senderPubKey = await fetchPublicKey(msg.userId);
            if (senderPubKey) {
              const plaintext = await decryptMessage(msg.text, senderPubKey, msg.chatId);
              if (plaintext) {
                msg.text = plaintext;
              } else {
                msg.text = 'Не удалось расшифровать';
              }
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

        // Пульс фона при входящем сообщении
        if (msg.userId !== userId) {
          window.__bleskBgPulse?.();
        }

        // [CRIT-2, IMP-1] Звук — только если не DND и чат не открыт
        const s = useSettingsStore.getState();
        if (msg.userId !== userId && !s.dnd) {
          const activeChats = useChatStore.getState().activeChats;
          const chatId = msg.chatId || msg.roomId;
          // Не играть звук если пользователь сейчас в этом чате и окно видимо
          if (!activeChats.has(chatId) || document.hidden) {
            soundReceive();
          }
        }

        // Уведомления через main process (работают в трее)
        if (s.notifications && s.notifMessages && !s.dnd && (document.hidden || !document.hasFocus())) {
          const title = msg.user?.username || 'blesk';
          const body = msg.encrypted ? 'Зашифрованное сообщение' : (msg.text?.slice(0, 100) || 'Новое сообщение');
          // [IMP-4] Передать silent флаг
          if (window.blesk?.notify) {
            window.blesk.notify(title, body, msg.chatId || msg.roomId, !s.sounds);
          } else {
            try { new Notification(title, { body, silent: !s.sounds }); } catch (err) { console.error('Notification create:', err?.message || err); }
          }
        }
      }
    };

    const handleMessageError = ({ tempId, error }) => {
      console.error('Message error:', tempId, error);
      useChatStore.getState().failMessage(tempId);
    };

    // ═══ Редактирование/удаление сообщений ═══
    // [E2E] Расшифровать отредактированные E2E сообщения
    const handleMessageEdited = async ({ messageId, chatId, text, encrypted, editedAt }) => {
      let decryptedText = text;
      if (encrypted) {
        try {
          // Найти отправителя сообщения из store
          const msgs = useChatStore.getState().messages[chatId];
          const originalMsg = msgs?.find((m) => m.id === messageId);
          if (originalMsg && originalMsg.userId !== userId) {
            const senderPubKey = await fetchPublicKey(originalMsg.userId);
            if (senderPubKey) {
              const plain = await decryptMessage(text, senderPubKey, chatId);
              if (plain) decryptedText = plain;
              else decryptedText = 'Не удалось расшифровать';
            }
          }
        } catch {
          decryptedText = 'Ошибка расшифровки';
        }
      }
      useChatStore.setState((state) => {
        const msgs = state.messages[chatId];
        if (!msgs) return state;
        return {
          messages: {
            ...state.messages,
            [chatId]: msgs.map((m) => m.id === messageId ? { ...m, text: decryptedText, editedAt, encrypted: encrypted || m.encrypted } : m),
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

    // [E2E] Инвалидация кеша ключей при смене ключа собеседника
    const handleUserKeyChanged = ({ userId: uid }) => {
      if (uid) invalidateUserKeys(uid);
    };

    // Принятие дружбы — перезагрузить чаты
    const handleFriendAccepted = () => {
      useChatStore.getState().loadChats();
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

    // [IMP-8] Read Receipts — O(N) с Set вместо O(N×M)
    const handleMessageReadBy = ({ chatId, messageIds, userId: readerId }) => {
      useChatStore.setState((state) => {
        const msgs = state.messages[chatId];
        if (!msgs) return state;
        const idsSet = new Set(messageIds);
        return {
          messages: {
            ...state.messages,
            [chatId]: msgs.map(m => {
              if (!idsSet.has(m.id)) return m;
              if (m.readBy?.includes(readerId)) return m; // Уже прочитано
              return { ...m, readBy: [...(m.readBy || []), readerId] };
            }),
          },
        };
      });
    };

    // ═══ Уведомления ═══
    const handleNotificationNew = (notification) => {
      useNotificationStore.getState().addNotification(notification);
      const ns = useSettingsStore.getState();
      // [CRIT-3] DND подавляет все звуки уведомлений
      if (ns.dnd) return;
      const type = notification.type;
      if (type === 'friend_request' && !ns.notifFriends) return;
      if (type === 'mention' && !ns.notifMentions) return;
      soundNotification(notification.fromUser?.hue || 0);
    };

    // ═══ Звонки ═══
    const handleCallIncoming = (data) => {
      // [Баг #2] Игнорировать входящий звонок если уже в звонке
      const store = useCallStore.getState();
      if (store.activeCall) return;
      store.setIncomingCall(data);
      soundRingtoneStart();
      // Нативное уведомление когда окно свёрнуто или не в фокусе
      if (document.hidden || !document.hasFocus()) {
        const callerName = data.callerName || 'Неизвестный';
        if (window.blesk?.notify) {
          window.blesk.notify('Входящий звонок', callerName);
        } else {
          try { new Notification('Входящий звонок', { body: callerName }); } catch {}
        }
      }
    };

    // [Баг #2] Сигнал "занято" — собеседник в другом звонке
    const handleCallBusy = ({ chatId }) => {
      soundRingtoneStop();
      const store = useCallStore.getState();
      if (store.activeCall?.chatId === chatId) {
        store.clearActiveCall();
      }
    };

    const handleCallAccepted = ({ chatId, userId: uid, startedAt }) => {
      soundRingtoneStop();
      soundCallAccepted();
      const store = useCallStore.getState();
      if (store.activeCall?.chatId === chatId) {
        store.addCallParticipant(uid);
        // [Баг #18] Синхронизировать startedAt от сервера для обоих клиентов
        if (store.activeCall.status === 'ringing' || startedAt) {
          store.setActiveCall({ ...store.activeCall, status: 'active', startedAt: startedAt || Date.now() });
        }
      }
    };

    const handleCallDeclined = ({ chatId }) => {
      const store = useCallStore.getState();
      if (store.activeCall?.chatId === chatId) {
        soundRingtoneStop();
        soundCallDeclined(); // [IMP-2] Звук отклонённого звонка
        store.clearActiveCall();
      }
    };

    const handleCallMissed = ({ chatId }) => {
      soundRingtoneStop();
      useCallStore.getState().clearActiveCall();
      useCallStore.getState().clearIncomingCall();
    };

    // [Баг #3] call:cancelled очищает входящий звонок + рингтон
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
      useCallStore.getState().clearIncomingCall(); // [Баг #3] Очистить и входящий звонок
    };

    const handleCallError = ({ chatId, error }) => {
      console.error('Call error:', chatId, error);
      soundRingtoneStop();
      useCallStore.getState().clearActiveCall();
    };

    // ═══ Shield: пополнение OPK ═══
    const handleShieldOpkLow = () => {
      replenishOPKs(50).catch(err => console.error('Shield replenishOPKs:', err?.message || err));
    };

    // ═══ Бан аккаунта ═══
    const handleAuthBanned = ({ reason }) => {
      console.warn('Аккаунт заблокирован:', reason);
      clearTokens();
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

    // ═══ Реакции ═══
    const handleMessageReacted = ({ messageId, chatId, reactions }) => {
      useChatStore.setState((state) => ({
        reactions: { ...state.reactions, [messageId]: reactions },
      }));
    };

    const handleReactionsBatch = (data) => {
      // Сервер отправляет объект напрямую, не обёрнутый в { reactions: ... }
      const batch = data?.reactions || data;
      if (!batch || typeof batch !== 'object') return;
      useChatStore.setState((state) => ({
        reactions: { ...state.reactions, ...batch },
      }));
    };

    // ═══ Link Preview (OG) ═══
    const handleMessageOg = ({ messageId, chatId, linkPreview }) => {
      const state = useChatStore.getState();
      const msgs = state.messages[chatId];
      if (!msgs) return;
      const updated = msgs.map(m => m.id === messageId ? { ...m, linkPreview } : m);
      useChatStore.setState({ messages: { ...state.messages, [chatId]: updated } });
    };

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

    // ═══ Browser online/offline ═══
    const handleBrowserOnline = () => {
      if (socket && !socket.connected) socket.connect();
    };
    const handleBrowserOffline = () => {
      useChatStore.setState({ isConnected: false });
    };
    window.addEventListener('online', handleBrowserOnline);
    window.addEventListener('offline', handleBrowserOffline);

    // ═══ Регистрация обработчиков ═══
    socket.on('connect_error', handleConnectError);
    socket.on('message:new', handleMessageNew);
    socket.on('message:error', handleMessageError);
    socket.on('message:edited', handleMessageEdited);
    socket.on('message:deleted', handleMessageDeleted);
    socket.on('message:readBy', handleMessageReadBy);
    socket.on('user:online', handleUserOnline);
    socket.on('user:offline', handleUserOffline);
    socket.on('user:statusChange', handleUserStatusChange);
    socket.on('user:updated', handleUserUpdated);
    socket.on('user:keyChanged', handleUserKeyChanged);
    socket.on('friend:removed', handleFriendRemoved);
    socket.on('friend:accepted', handleFriendAccepted);
    socket.on('channel:deleted', handleChannelDeleted);
    socket.on('typing:start', handleTypingStart);
    socket.on('typing:stop', handleTypingStop);
    socket.on('notification:new', handleNotificationNew);
    socket.on('call:incoming', handleCallIncoming);
    socket.on('call:busy', handleCallBusy);
    socket.on('call:accepted', handleCallAccepted);
    socket.on('call:declined', handleCallDeclined);
    socket.on('call:missed', handleCallMissed);
    socket.on('call:cancelled', handleCallCancelled);
    socket.on('call:user-left', handleCallUserLeft);
    socket.on('call:ended', handleCallEnded);
    socket.on('call:error', handleCallError);
    socket.on('auth:banned', handleAuthBanned);
    socket.on('shield:opk-low', handleShieldOpkLow);
    socket.on('group:member-added', handleGroupMemberAdded);
    socket.on('group:member-removed', handleGroupMemberRemoved);
    socket.on('group:updated', handleGroupUpdated);
    socket.on('message:reacted', handleMessageReacted);
    socket.on('message:reactions:batch', handleReactionsBatch);
    socket.on('message:pinned', handleMessagePinned);
    socket.on('message:og', handleMessageOg);

    return () => {
      // Очистить все таймеры typing auto-clear
      typingTimersRef.current.forEach((timer) => clearTimeout(timer));
      typingTimersRef.current.clear();
      socket.off('connect', handleConnect);
      socket.off('disconnect', handleDisconnect);
      socket.off('connect_error', handleConnectError);
      socket.off('message:new', handleMessageNew);
      socket.off('message:error', handleMessageError);
      socket.off('message:edited', handleMessageEdited);
      socket.off('message:deleted', handleMessageDeleted);
      socket.off('message:readBy', handleMessageReadBy);
      socket.off('user:online', handleUserOnline);
      socket.off('user:offline', handleUserOffline);
      socket.off('user:statusChange', handleUserStatusChange);
      socket.off('user:updated', handleUserUpdated);
      socket.off('user:keyChanged', handleUserKeyChanged);
      socket.off('friend:removed', handleFriendRemoved);
      socket.off('friend:accepted', handleFriendAccepted);
      socket.off('channel:deleted', handleChannelDeleted);
      socket.off('typing:start', handleTypingStart);
      socket.off('typing:stop', handleTypingStop);
      socket.off('notification:new', handleNotificationNew);
      socket.off('call:incoming', handleCallIncoming);
      socket.off('call:busy', handleCallBusy);
      socket.off('call:accepted', handleCallAccepted);
      socket.off('call:declined', handleCallDeclined);
      socket.off('call:missed', handleCallMissed);
      socket.off('call:cancelled', handleCallCancelled);
      socket.off('call:user-left', handleCallUserLeft);
      socket.off('call:ended', handleCallEnded);
      socket.off('call:error', handleCallError);
      socket.off('auth:banned', handleAuthBanned);
      socket.off('shield:opk-low', handleShieldOpkLow);
      socket.off('group:member-added', handleGroupMemberAdded);
      socket.off('group:member-removed', handleGroupMemberRemoved);
      socket.off('group:updated', handleGroupUpdated);
      socket.off('message:reacted', handleMessageReacted);
      socket.off('message:reactions:batch', handleReactionsBatch);
      socket.off('message:pinned', handleMessagePinned);
      socket.off('message:og', handleMessageOg);
      window.removeEventListener('online', handleBrowserOnline);
      window.removeEventListener('offline', handleBrowserOffline);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      clearInterval(heartbeatInterval);
      socket.disconnect();
      socketRef.current = null;
    };
  }, []);

  return socketRef;
}
