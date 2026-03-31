import React, { useEffect, useRef, useState, useCallback, Fragment, useMemo } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useVirtualizer } from '@tanstack/react-virtual';
import { MagnifyingGlass, X, Check } from '@phosphor-icons/react';
import { useChatStore } from '../../store/chatStore';
import { useSettingsStore } from '../../store/settingsStore';
import ChatHeader from './ChatHeader';
import ChatMessage from './ChatMessage';
import ChatInput from './ChatInput';
import TypingBubble from './TypingBubble';
import DateSeparator from './DateSeparator';
import UnreadDivider from './UnreadDivider';
import ImageLightbox from './ImageLightbox';
import CallBanner from '../voice/CallBanner';
import GroupMembersPanel from './GroupMembersPanel';
import { MIN_WIDTH, MIN_HEIGHT } from '../../hooks/useWindowManager';
import { getCurrentUserId } from '../../utils/auth';
import uploadFile from '../../utils/uploadFile';
import { encryptMessage, fetchPublicKey } from '../../utils/cryptoService';
import { shieldEncrypt, isShieldReady } from '../../utils/shieldService';
import { getHueFromString } from '../../utils/hueIdentity';
import UserProfileModal from '../ui/UserProfileModal';
import ConfirmDialog from '../ui/ConfirmDialog';
import './ChatView.css';

// Resize edges
const EDGES = ['n', 'ne', 'e', 'se', 's', 'sw', 'w', 'nw'];

export default function ChatView({
  chatId,
  morphRect,
  windowState,
  isFocused,
  onClose,
  onFocus,
  onMove,
  onResize,
  onMorphEnd,
  socketRef,
  onCall,
  activeCall,
  onJoinCall,
}) {
  // Inline mode — встроен в layout без windowState
  const isInline = !windowState;
  // [CRIT-2] Гранулярные селекторы вместо подписки на весь store
  const EMPTY = [];
  const chatMessages = useChatStore((s) => s.messages[chatId] ?? EMPTY);
  const chat = useChatStore((s) => s.chats.find((c) => c.id === chatId));
  const onlineUsers = useChatStore((s) => s.onlineUsers);
  const userStatuses = useChatStore((s) => s.userStatuses);
  const typingUsers = useChatStore((s) => s.typingUsers);
  const allReactions = useChatStore((s) => s.reactions);
  const showTyping = useSettingsStore((s) => s.showTyping);
  const compactMessages = useSettingsStore((s) => s.compactMessages);
  const messagesEndRef = useRef(null);
  const viewRef = useRef(null);
  const [replyTo, setReplyTo] = useState(null);
  const [editingMsg, setEditingMsg] = useState(null);
  const [membersOpen, setMembersOpen] = useState(false);
  const [lightboxSrc, setLightboxSrc] = useState(null);
  const [profileUserId, setProfileUserId] = useState(null);
  const [deleteConfirm, setDeleteConfirm] = useState(null);
  const [forwardMsg, setForwardMsg] = useState(null);
  const [forwardSuccess, setForwardSuccess] = useState(false);
  const initialUnreadRef = useRef(null);

  // Drag/pull state
  const dragRef = useRef({
    mode: null, // null | 'undecided' | 'drag' | 'pull'
    startX: 0,
    startY: 0,
    startWinX: 0,
    startWinY: 0,
    pointerId: null,
  });
  const [pullY, setPullY] = useState(0);
  const [isPulling, setIsPulling] = useState(false);
  const [closing, setClosing] = useState(false);

  // Resize state
  const resizeRef = useRef({
    active: false,
    edge: null,
    startX: 0,
    startY: 0,
    startWinX: 0,
    startWinY: 0,
    startW: 0,
    startH: 0,
    pointerId: null,
  });

  // Текущий userId
  const userId = useRef(getCurrentUserId());

  // [BUG 1.3] Защита от state update после unmount
  const isMountedRef = useRef(true);
  const ackTimeoutsRef = useRef([]);
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
      // Очистить все pending таймеры
      ackTimeoutsRef.current.forEach(clearTimeout);
      ackTimeoutsRef.current = [];
      if (closeTimerRef.current) clearTimeout(closeTimerRef.current);
    };
  }, []);

  // Счётчик новых сообщений пока пользователь прокрутил вверх
  const [newMsgCount, setNewMsgCount] = useState(0);
  const prevMessageCountRef = useRef(0);

  // Анимация закрытия → потом реальное удаление
  const closeTimerRef = useRef(null);
  const animateClose = useCallback(() => {
    if (closing) return;
    setClosing(true);
    closeTimerRef.current = setTimeout(() => {
      if (isMountedRef.current) onClose();
    }, 300);
  }, [closing, onClose]);

  // Загрузить сообщения (только при смене chatId)
  useEffect(() => {
    if (chatId) {
      const c = useChatStore.getState().chats.find((ch) => ch.id === chatId);
      if (initialUnreadRef.current === null) {
        initialUnreadRef.current = c?.unreadCount || 0;
      }
      useChatStore.getState().openChat(chatId);
      useChatStore.getState().markAsRead(chatId, socketRef);
      // Сбросить состояние скролла при смене чата
      prevMessageCountRef.current = 0;
      isNearBottomRef.current = true;
      setNewMsgCount(0);
      setShowScrollDown(false);
    }
  }, [chatId]); // eslint-disable-line

  // Скролл к последнему сообщению (только если пользователь уже внизу)
  const messagesContainerRef = useRef(null);
  const isNearBottomRef = useRef(true);
  const [showScrollDown, setShowScrollDown] = useState(false);

  // [CRIT-1] Виртуализация списка сообщений
  const messageCount = chatMessages.length;
  const virtualizer = useVirtualizer({
    count: messageCount,
    getScrollElement: () => messagesContainerRef.current,
    estimateSize: () => 52, // Средняя высота сообщения (bubble + padding)
    overscan: 10,
  });

  const handleMessagesScroll = useCallback(() => {
    const el = messagesContainerRef.current;
    if (!el) return;
    const near = el.scrollHeight - el.scrollTop - el.clientHeight < 150;
    isNearBottomRef.current = near;
    setShowScrollDown(!near);
    // Сбросить бейдж когда пользователь долистал до конца
    if (near) setNewMsgCount(0);
  }, []);

  // [BUG 1.1] Автоскролл при новых сообщениях
  useEffect(() => {
    if (!isMountedRef.current || messageCount === 0) return;
    const prevCount = prevMessageCountRef.current;
    prevMessageCountRef.current = messageCount;

    // Первая загрузка — всегда мгновенный скролл к концу (без анимации)
    if (prevCount === 0 && messageCount > 0) {
      // Два rAF: первый — virtualizer обсчитывает размеры, второй — скролл
      requestAnimationFrame(() => {
        if (!isMountedRef.current) return;
        requestAnimationFrame(() => {
          if (!isMountedRef.current) return;
          virtualizer.scrollToIndex(messageCount - 1, { align: 'end', behavior: 'auto' });
        });
      });
      return;
    }

    // Новые сообщения пришли
    if (messageCount > prevCount) {
      const newCount = messageCount - prevCount;
      if (isNearBottomRef.current) {
        // Пользователь внизу — скроллим к новому сообщению
        requestAnimationFrame(() => {
          if (!isMountedRef.current) return;
          virtualizer.scrollToIndex(messageCount - 1, { align: 'end', behavior: 'smooth' });
        });
        setNewMsgCount(0);
      } else {
        // Пользователь читает историю — показать бейдж
        setNewMsgCount((prev) => prev + newCount);
      }
    }
  }, [messageCount]); // eslint-disable-line

  // Escape — закрыть только focused окно
  useEffect(() => {
    if (!isFocused && !isInline) return;
    const handleKey = (e) => {
      if (e.key === 'Escape') animateClose();
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [isFocused, animateClose]);

  // Очистить morph после анимации
  useEffect(() => {
    if (morphRect && onMorphEnd) {
      const t = setTimeout(onMorphEnd, 550);
      return () => clearTimeout(t);
    }
  }, [morphRect, onMorphEnd]);

  // === Drag / Pull-down на хедере ===
  const handleHeaderPointerDown = useCallback((e) => {
    // Inline mode — не перетаскивается
    if (isInline) return;
    // Игнорировать клик на кнопку закрытия
    if (e.target.closest('button') || e.target.closest('.chat-header__actions') || e.target.closest('a') || e.target.closest('input')) return;

    if (onFocus) onFocus();
    dragRef.current = {
      mode: 'undecided',
      startX: e.clientX,
      startY: e.clientY,
      startWinX: windowState.x,
      startWinY: windowState.y,
      pointerId: e.pointerId,
    };
    e.currentTarget.setPointerCapture(e.pointerId);
    e.preventDefault();
  }, [isInline, onFocus, windowState?.x, windowState?.y]);

  const handleHeaderPointerMove = useCallback((e) => {
    const d = dragRef.current;
    if (!d.mode) return;

    const dx = e.clientX - d.startX;
    const dy = e.clientY - d.startY;

    if (d.mode === 'undecided') {
      // Мёртвая зона 5px
      if (Math.abs(dx) < 5 && Math.abs(dy) < 5) return;
      // Определить режим: горизонтальное = drag, вертикальное вниз = pull
      if (Math.abs(dx) >= Math.abs(dy) || dy < 0) {
        d.mode = 'drag';
      } else {
        d.mode = 'pull';
        setIsPulling(true);
      }
    }

    if (d.mode === 'drag') {
      onMove(d.startWinX + dx, d.startWinY + dy);
    } else if (d.mode === 'pull') {
      if (dy > 0) setPullY(dy);
    }
  }, [onMove]);

  const handleHeaderPointerUp = useCallback((e) => {
    const d = dragRef.current;

    if (d.mode === 'pull') {
      const threshold = window.innerHeight * 0.2;
      if (pullY > threshold) {
        viewRef.current?.classList.add('chat-view--collapsing');
        const pullTimer = setTimeout(() => { if (isMountedRef.current) onClose(); }, 350);
        ackTimeoutsRef.current.push(pullTimer);
      } else {
        setPullY(0);
      }
      setIsPulling(false);
    }

    dragRef.current = { mode: null, startX: 0, startY: 0, startWinX: 0, startWinY: 0, pointerId: null };
  }, [pullY, onClose]);

  // === Resize по краям ===
  const handleResizePointerDown = useCallback((e, edge) => {
    e.stopPropagation();
    onFocus();
    resizeRef.current = {
      active: true,
      edge,
      startX: e.clientX,
      startY: e.clientY,
      startWinX: windowState.x,
      startWinY: windowState.y,
      startW: windowState.width,
      startH: windowState.height,
      pointerId: e.pointerId,
    };
    e.currentTarget.setPointerCapture(e.pointerId);
    e.preventDefault();
  }, [onFocus, windowState]);

  const handleResizePointerMove = useCallback((e) => {
    const r = resizeRef.current;
    if (!r.active) return;

    const dx = e.clientX - r.startX;
    const dy = e.clientY - r.startY;
    let { startWinX: x, startWinY: y, startW: w, startH: h } = r;
    const edge = r.edge;

    // Правый край
    if (edge.includes('e')) w = r.startW + dx;
    // Левый край
    if (edge.includes('w')) { w = r.startW - dx; x = r.startWinX + dx; }
    // Нижний край
    if (edge.includes('s')) h = r.startH + dy;
    // Верхний край
    if (edge === 'n' || edge === 'ne' || edge === 'nw') { h = r.startH - dy; y = r.startWinY + dy; }

    // Клампинг к минимуму
    if (w < MIN_WIDTH) {
      if (edge.includes('w')) x = r.startWinX + r.startW - MIN_WIDTH;
      w = MIN_WIDTH;
    }
    if (h < MIN_HEIGHT) {
      if (edge === 'n' || edge === 'ne' || edge === 'nw') y = r.startWinY + r.startH - MIN_HEIGHT;
      h = MIN_HEIGHT;
    }

    onResize(x, y, w, h);
  }, [onResize]);

  const handleResizePointerUp = useCallback(() => {
    resizeRef.current.active = false;
  }, []);

  // Антиспам: клиентский кулдаун — макс 5 сообщений за 3 секунды
  const sendTimestampsRef = useRef([]);

  // Отправка сообщения (с E2E шифрованием для личных чатов)
  const handleSend = async (text) => {
    const now = Date.now();
    const ts = sendTimestampsRef.current;
    // Убрать старые
    while (ts.length > 0 && now - ts[0] > 3000) ts.shift();
    if (ts.length >= 5) return; // Заблокировать на клиенте
    ts.push(now);

    const tempId = crypto.randomUUID();
    useChatStore.getState().sendMessage(chatId, text, tempId);

    const payload = { chatId, text, tempId };
    if (replyTo) {
      payload.replyToId = replyTo.id;
    }

    // E2E шифрование — только для личных чатов (type === 'chat')
    const { e2eEnabled } = useSettingsStore.getState();
    if (e2eEnabled && chat?.type === 'chat' && chat?.otherUser?.id) {
      try {
        // blesk Shield (Double Ratchet) — приоритетный путь
        if (isShieldReady()) {
          const shieldResult = await shieldEncrypt(chat.otherUser.id, text);
          if (shieldResult) {
            payload.text = shieldResult.text;
            payload.encrypted = true;
            if (shieldResult.shieldHeader) payload.shieldHeader = shieldResult.shieldHeader;
          } else {
            // Fallback на legacy если Shield не смог (у собеседника нет Shield)
            const otherPubKey = await fetchPublicKey(chat.otherUser.id);
            if (otherPubKey) {
              const encrypted = await encryptMessage(text, otherPubKey, chatId);
              if (encrypted) {
                payload.text = encrypted;
                payload.encrypted = true;
              }
            }
          }
        } else {
          // Legacy encryption
          const otherPubKey = await fetchPublicKey(chat.otherUser.id);
          if (otherPubKey) {
            const encrypted = await encryptMessage(text, otherPubKey, chatId);
            if (encrypted) {
              payload.text = encrypted;
              payload.encrypted = true;
            }
          }
        }
      } catch (err) {
        console.error('E2E encrypt error:', err);
        return; // Не отправлять при ошибке шифрования
      }
    }

    // [HIGH-1] ACK callback + [IMP-4] timeout для ACK
    const sendTempId = payload.tempId;
    const ackTimeout = setTimeout(() => {
      if (!isMountedRef.current) return;
      const msgs = useChatStore.getState().messages[chatId];
      const pending = msgs?.find(m => m.tempId === sendTempId && m.pending);
      if (pending) useChatStore.getState().failMessage(sendTempId, chatId);
      // Убрать из списка tracked таймеров
      ackTimeoutsRef.current = ackTimeoutsRef.current.filter(t => t !== ackTimeout);
    }, 10000);
    ackTimeoutsRef.current.push(ackTimeout);

    socketRef.current?.emit('message:send', payload, (ack) => {
      clearTimeout(ackTimeout);
      ackTimeoutsRef.current = ackTimeoutsRef.current.filter(t => t !== ackTimeout);
      if (ack?.error) {
        useChatStore.getState().failMessage(sendTempId, chatId);
      }
    });
    setReplyTo(null);

    // [BUG 1.1] Всегда скролл к концу после отправки
    isNearBottomRef.current = true;
    setShowScrollDown(false);
    setNewMsgCount(0);
    requestAnimationFrame(() => {
      if (!isMountedRef.current) return;
      const count = useChatStore.getState().messages[chatId]?.length || 0;
      if (count > 0) {
        virtualizer.scrollToIndex(count - 1, { align: 'end', behavior: 'smooth' });
      }
    });
  };

  // Отправка файлов
  const handleSendFiles = useCallback(async (files, text) => {
    for (const file of files) {
      try {
        await uploadFile(chatId, file, {
          text: text || undefined,
          replyToId: replyTo?.id,
        });
        // Текст отправляем только с первым файлом
        text = undefined;
      } catch (err) {
        console.error('Ошибка загрузки файла:', err);
        // [HIGH-6] Показать ошибку пользователю
        const errMsg = err?.response?.data?.error || err?.message || 'Не удалось загрузить файл';
        window.blesk?.notify?.('blesk', errMsg);
      }
    }
    setReplyTo(null);
  }, [chatId, replyTo]);

  // Дебаунс typing:start — не чаще 1 раза в 2 секунды
  const lastTypingRef = useRef(0);

  const handleTypingStart = useCallback(() => {
    if (!showTyping) return;
    const now = Date.now();
    if (now - lastTypingRef.current < 2000) return;
    lastTypingRef.current = now;
    socketRef.current?.emit('typing:start', { chatId });
  }, [showTyping, chatId, socketRef]);

  const handleTypingStop = useCallback(() => {
    if (!showTyping) return;
    socketRef.current?.emit('typing:stop', { chatId });
  }, [showTyping, chatId, socketRef]);

  // [CRIT-3] Стабильные callbacks для ChatMessage (не ломают React.memo)
  const handleReplyStable = useCallback((msg) => setReplyTo(msg), []);
  const handleReactStable = useCallback((messageId, emoji) => {
    socketRef.current?.emit('message:react', { messageId, emoji });
  }, [socketRef]);
  const handleEditStable = useCallback((msg) => { setEditingMsg(msg); setReplyTo(null); }, []);
  const handleDeleteStable = useCallback((msgId) => setDeleteConfirm(msgId), []);
  const handleForwardStable = useCallback((msg) => setForwardMsg(msg), []);
  const handleRetryStable = useCallback((msg) => {
    const socket = socketRef.current;
    if (socket && msg.text) {
      useChatStore.setState(state => ({
        messages: {
          ...state.messages,
          [chatId]: state.messages[chatId]?.map(m =>
            m.id === msg.id || m.tempId === msg.tempId
              ? { ...m, failed: false, pending: true }
              : m
          ),
        },
      }));
      socket.emit('message:send', {
        chatId,
        text: msg.text,
        tempId: msg.tempId || msg.id,
      });
    }
  }, [chatId, socketRef]);

  // Редактирование сообщения — заполнить input текстом
  const handleEdit = useCallback((msg) => {
    setEditingMsg(msg);
    setReplyTo(null);
  }, []);

  // Отправка отредактированного сообщения (с E2E если включено)
  const handleEditSend = useCallback(async (newText) => {
    if (!editingMsg || !newText.trim()) return;
    const text = newText.trim();
    const payload = { messageId: editingMsg.id, chatId, text };

    // [E2E] Шифровать отредактированное сообщение если оригинал был зашифрован
    const { e2eEnabled } = useSettingsStore.getState();
    if (e2eEnabled && editingMsg.encrypted && chat?.type === 'chat' && chat?.otherUser?.id) {
      try {
        const otherPubKey = await fetchPublicKey(chat.otherUser.id);
        if (otherPubKey) {
          const encrypted = await encryptMessage(text, otherPubKey, chatId);
          if (encrypted) {
            payload.text = encrypted;
            payload.encrypted = true;
          }
        }
      } catch (err) {
        console.error('E2E encrypt edit error:', err);
      }
    }

    socketRef.current?.emit('message:edit', payload);
    setEditingMsg(null);
  }, [editingMsg, chatId, socketRef, chat]);

  // Удаление сообщения (через подтверждение)
  const handleDelete = useCallback((messageId) => {
    setDeleteConfirm(messageId);
  }, []);

  const confirmDelete = useCallback(() => {
    if (deleteConfirm) {
      socketRef.current?.emit('message:delete', { messageId: deleteConfirm, chatId });
      setDeleteConfirm(null);
    }
  }, [deleteConfirm, chatId, socketRef]);

  if (!chat) return null;

  const isOnline = chat.otherUser ? onlineUsers.includes(chat.otherUser.id) : false;
  const typingInChat = typingUsers[chatId] || [];
  // Резолвим userId → username для хедера
  const typingNames = React.useMemo(() => {
    if (!typingInChat.length) return [];
    if (chat?.type !== 'group' && chat?.otherUser) {
      return typingInChat.length ? [chat.otherUser.username] : [];
    }
    // Группа: ищем имена в сообщениях чата
    const nameMap = {};
    for (const msg of chatMessages) {
      if (msg.user?.username) nameMap[msg.userId] = msg.user.username;
    }
    return typingInChat.map(uid => nameMap[uid] || uid).filter(Boolean);
  }, [typingInChat, chat, chatMessages]);

  // Вычисляем ID первого непрочитанного сообщения (для UnreadDivider)
  const savedUnread = initialUnreadRef.current || 0;
  const firstUnreadId = savedUnread > 0 && chatMessages.length >= savedUnread
    ? chatMessages[chatMessages.length - savedUnread]?.id
    : null;

  // Morph animation — пересчитать координаты относительно окна
  const morphStyle = morphRect
    ? {
        '--morph-x': `${morphRect.x - windowState.x}px`,
        '--morph-y': `${morphRect.y - windowState.y}px`,
        '--morph-w': `${morphRect.width}px`,
        '--morph-h': `${morphRect.height}px`,
      }
    : {};

  // Pull-down transform
  const pullProgress = Math.min(pullY / (window.innerHeight * 0.35), 1);
  const pullScale = 1 - pullProgress * 0.15;
  const pullOpacity = 1 - pullProgress * 0.4;
  const pullBlur = pullProgress * 8;
  const pullRadius = pullProgress * 24;

  return (
    <div
      className={`chat-view ${isInline ? 'chat-view--inline' : ''} ${morphRect ? 'chat-view--morph' : ''} ${isFocused ? 'chat-view--focused' : ''} ${closing ? 'chat-view--closing' : ''}`}
      ref={viewRef}
      style={isInline ? {} : {
        top: windowState.y,
        left: windowState.x,
        width: windowState.width,
        height: windowState.height,
        zIndex: windowState.zIndex,
        ...morphStyle,
        transform: pullY > 0
          ? `translateY(${pullY * 0.6}px) scale(${pullScale})`
          : undefined,
        opacity: pullY > 0 ? pullOpacity : undefined,
        filter: pullY > 0 ? `blur(${pullBlur}px)` : undefined,
        borderRadius: pullY > 0 ? `${pullRadius}px` : undefined,
        transition: isPulling
          ? 'none'
          : 'transform 0.35s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.35s, filter 0.35s, border-radius 0.35s',
      }}
      onPointerDown={(e) => {
        // Focus при клике в любое место окна
        if (onFocus && !e.target.closest('.chat-view__resize')) {
          onFocus();
        }
      }}
    >
      {/* Pull-down индикатор */}
      <div
        className="chat-view__pull-indicator"
        style={{
          opacity: pullProgress > 0.1 ? Math.min(pullProgress * 2, 1) : 0,
          transform: `scaleX(${0.3 + pullProgress * 0.7})`,
        }}
      />

      {/* Зона хедера для drag/pull */}
      <div
        className="chat-view__drag-zone"
        onPointerDown={handleHeaderPointerDown}
        onPointerMove={handleHeaderPointerMove}
        onPointerUp={handleHeaderPointerUp}
      >
        <ChatHeader
          chat={chat}
          isOnline={isOnline}
          userStatus={chat.otherUser ? userStatuses[chat.otherUser.id] : null}
          typingUsernames={typingNames}
          onCall={onCall}
          onMembers={chat.type === 'group' ? () => setMembersOpen(true) : undefined}
          onAvatarClick={() => { if (chat.otherUser?.id) setProfileUserId(chat.otherUser.id); }}
        />
        {/* Кнопка закрытия */}
        <button
          className="chat-view__close-btn"
          onClick={(e) => { e.stopPropagation(); initialUnreadRef.current = null; animateClose(); }}
          title="Закрыть"
        >
          ×
        </button>
      </div>

      {/* Баннер звонка */}
      {activeCall && (
        <CallBanner activeCall={activeCall} onJoin={onJoinCall} />
      )}

      {/* Виртуализированный список сообщений */}
      <AnimatePresence mode="wait">
      <motion.div
        key={chatId}
        initial={{ opacity: 0, filter: 'blur(3px)' }}
        animate={{ opacity: 1, filter: 'blur(0px)' }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.15 }}
        style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}
      >
      <div
        className={`chat-view__messages ${compactMessages ? 'chat-view__messages--compact' : ''}`}
        ref={messagesContainerRef}
        onScroll={handleMessagesScroll}
        role="log"
        aria-live="polite"
        aria-relevant="additions"
        aria-label="Сообщения"
      >
        {/* Empty state: нет сообщений */}
        {messageCount === 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 8, opacity: 0.4 }}>
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>
            <span style={{ fontSize: 13 }}>Начните разговор — напишите первое сообщение</span>
          </div>
        )}

        <div style={{ height: virtualizer.getTotalSize(), width: '100%', position: 'relative' }}>
          {virtualizer.getVirtualItems().map((virtualRow) => {
            const idx = virtualRow.index;
            const msg = chatMessages[idx];
            if (!msg) return null;

            const isOwn = msg.userId === userId.current;
            const prev = chatMessages[idx - 1];
            const next = chatMessages[idx + 1];
            const prevSame = prev && prev.userId === msg.userId;
            const nextSame = next && next.userId === msg.userId;

            let groupPosition = 'solo';
            if (prevSame && nextSame) groupPosition = 'mid';
            else if (!prevSame && nextSame) groupPosition = 'first';
            else if (prevSame && !nextSame) groupPosition = 'last';

            const showGap = prev && prev.userId !== msg.userId;
            const showDateSep = !prev ||
              new Date(msg.createdAt).toDateString() !== new Date(prev.createdAt).toDateString();
            const hue = getHueFromString(msg.user?.username || msg.username || msg.userId || '');

            return (
              <div
                key={msg.id || msg.tempId || idx}
                data-index={idx}
                ref={virtualizer.measureElement}
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '100%',
                  transform: `translateY(${virtualRow.start}px)`,
                }}
              >
                {showDateSep && <DateSeparator date={msg.createdAt} />}
                {firstUnreadId === msg.id && <UnreadDivider />}
                {showGap && !showDateSep && <div className="chat-view__group-gap" />}
                <ChatMessage
                  message={msg}
                  isOwn={isOwn}
                  groupPosition={groupPosition}
                  hue={hue}
                  senderName={msg.user?.username || msg.username || 'Unknown'}
                  isRead={msg.readBy?.includes?.(chat?.otherUser?.id) || false}
                  onReply={handleReplyStable}
                  onReact={handleReactStable}
                  onEdit={handleEditStable}
                  onDelete={handleDeleteStable}
                  onForward={handleForwardStable}
                  onRetry={handleRetryStable}
                  onImageClick={setLightboxSrc}
                  reactions={allReactions[msg.id]}
                  currentUserId={userId.current}
                />
              </div>
            );
          })}
        </div>

        {/* Typing bubble — после виртуализированного списка */}
        <AnimatePresence>
          {typingInChat.length > 0 && typingInChat[0] && (
            <motion.div
              key="typing-bubble"
              initial={{ opacity: 0, y: 8, scale: 0.92 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -4, scale: 0.95 }}
              transition={{ type: 'spring', damping: 22, stiffness: 400 }}
            >
              <TypingBubble
                user={typingInChat[0]}
                hue={getHueFromString(typingInChat[0].username || '')}
              />
            </motion.div>
          )}
        </AnimatePresence>

        <div ref={messagesEndRef} />
      </div>
      </motion.div>
      </AnimatePresence>

      {/* Scroll-to-bottom кнопка + бейдж новых сообщений */}
      {showScrollDown && (
        <button
          className="chat-view__scroll-down"
          onClick={() => {
            if (messageCount > 0) {
              virtualizer.scrollToIndex(messageCount - 1, { align: 'end', behavior: 'smooth' });
            }
            isNearBottomRef.current = true;
            setShowScrollDown(false);
            setNewMsgCount(0);
          }}
          aria-label="К последним сообщениям"
        >
          {newMsgCount > 0 && (
            <span className="chat-view__scroll-down-badge">{newMsgCount > 99 ? '99+' : newMsgCount}</span>
          )}
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><polyline points="6 9 12 15 18 9"/></svg>
        </button>
      )}

      <ChatInput
        onSend={editingMsg ? handleEditSend : handleSend}
        onSendFiles={handleSendFiles}
        onTypingStart={handleTypingStart}
        onTypingStop={handleTypingStop}
        replyTo={replyTo}
        onCancelReply={() => setReplyTo(null)}
        editingMsg={editingMsg}
        onCancelEdit={() => setEditingMsg(null)}
      />

      {lightboxSrc && (
        <ImageLightbox src={lightboxSrc} onClose={() => setLightboxSrc(null)} />
      )}

      {/* Resize хендлы — 8 зон по краям и углам (только floating mode) */}
      {!isInline && EDGES.map((edge) => (
        <div
          key={edge}
          className={`chat-view__resize chat-view__resize--${edge}`}
          onPointerDown={(e) => handleResizePointerDown(e, edge)}
          onPointerMove={handleResizePointerMove}
          onPointerUp={handleResizePointerUp}
        />
      ))}

      {/* Панель участников группы */}
      {membersOpen && (
        <GroupMembersPanel
          chatId={chatId}
          isOwner={chat.type === 'group' && chat.ownerId === userId.current}
          onClose={() => setMembersOpen(false)}
          socketRef={socketRef}
        />
      )}
      <UserProfileModal userId={profileUserId} open={!!profileUserId} onClose={() => setProfileUserId(null)} />
      <ConfirmDialog
        open={!!deleteConfirm}
        title="Удалить сообщение?"
        message="Сообщение будет удалено безвозвратно"
        confirmText="Удалить"
        danger
        onConfirm={confirmDelete}
        onCancel={() => setDeleteConfirm(null)}
      />

      {/* Модалка пересылки сообщения */}
      {forwardMsg && (
        <ForwardModal
          message={forwardMsg}
          currentChatId={chatId}
          socketRef={socketRef}
          onClose={() => setForwardMsg(null)}
          onSuccess={() => {
            setForwardMsg(null);
            setForwardSuccess(true);
            setTimeout(() => setForwardSuccess(false), 2000);
          }}
        />
      )}

      {/* Индикатор успешной пересылки */}
      {forwardSuccess && (
        <div className="chat-view__forward-toast">
          <Check size={14} />
          <span>Сообщение переслано</span>
        </div>
      )}
    </div>
  );
}

// Инлайн-компонент модалки пересылки
function ForwardModal({ message, currentChatId, socketRef, onClose, onSuccess }) {
  const chats = useChatStore((s) => s.chats);
  const [search, setSearch] = useState('');
  const [sending, setSending] = useState(false);
  const overlayRef = useRef(null);
  const inputRef = useRef(null);

  // Фокус на поле поиска при открытии
  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  // Закрыть по Escape
  useEffect(() => {
    const handleKey = (e) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [onClose]);

  // Фильтрация: исключить текущий чат, искать по имени
  const filtered = useMemo(() => {
    const list = chats.filter((c) => c.id !== currentChatId);
    if (!search.trim()) return list;
    const q = search.toLowerCase();
    return list.filter((c) => {
      const name = c.name || c.otherUser?.username || '';
      return name.toLowerCase().includes(q);
    });
  }, [chats, currentChatId, search]);

  const handleForward = async (targetChatId) => {
    if (sending) return;
    setSending(true);
    socketRef.current?.emit('message:forward', {
      messageId: message.id,
      targetChatId,
    }, (res) => {
      setSending(false);
      if (res?.ok) {
        onSuccess();
      } else {
        console.error('Forward failed:', res?.error);
        // Показать ошибку через blesk notify если доступно
        window.blesk?.notify?.('blesk', res?.error || 'Не удалось переслать');
      }
    });
  };

  const handleOverlayClick = (e) => {
    if (e.target === overlayRef.current) onClose();
  };

  // Превью текста сообщения (обрезать длинные)
  const previewText = message.text?.length > 80
    ? message.text.slice(0, 80) + '...'
    : message.text;

  return (
    <div className="forward-modal__overlay" ref={overlayRef} onClick={handleOverlayClick}>
      <div className="forward-modal">
        <div className="forward-modal__header">
          <span className="forward-modal__title">Переслать сообщение</span>
          <button className="forward-modal__close" onClick={onClose}>
            <X size={16} />
          </button>
        </div>

        {/* Превью пересылаемого сообщения */}
        <div className="forward-modal__preview">
          <span className="forward-modal__preview-author">{message.user?.username || message.username || 'Unknown'}</span>
          <span className="forward-modal__preview-text">{previewText}</span>
        </div>

        {/* Поиск */}
        <div className="forward-modal__search">
          <MagnifyingGlass size={14} />
          <input
            ref={inputRef}
            type="text"
            placeholder="Поиск чата..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        {/* Список чатов */}
        <div className="forward-modal__list">
          {filtered.length === 0 && (
            <div className="forward-modal__empty">Нет доступных чатов</div>
          )}
          {filtered.map((c) => {
            const name = c.name || c.otherUser?.username || 'Чат';
            const hue = getHueFromString(name);
            return (
              <button
                key={c.id}
                className="forward-modal__item"
                onClick={() => handleForward(c.id)}
                disabled={sending}
              >
                <div
                  className="forward-modal__item-avatar"
                  style={{ background: `hsl(${hue}, 60%, 45%)` }}
                >
                  {name.charAt(0).toUpperCase()}
                </div>
                <span className="forward-modal__item-name">{name}</span>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
