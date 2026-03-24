import React, { useEffect, useRef, useState, useCallback, Fragment } from 'react';
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
import { getHueFromString } from '../../utils/hueIdentity';
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
  const { messages, chats, onlineUsers, userStatuses, typingUsers, openChat, markAsRead } = useChatStore();
  const showTyping = useSettingsStore((s) => s.showTyping);
  const compactMessages = useSettingsStore((s) => s.compactMessages);
  const chatMessages = messages[chatId] || [];
  const chat = chats.find((c) => c.id === chatId);
  const messagesEndRef = useRef(null);
  const viewRef = useRef(null);
  const [replyTo, setReplyTo] = useState(null);
  const [editingMsg, setEditingMsg] = useState(null);
  const [membersOpen, setMembersOpen] = useState(false);
  const [lightboxSrc, setLightboxSrc] = useState(null);
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

  // Анимация закрытия → потом реальное удаление
  const animateClose = useCallback(() => {
    if (closing) return;
    setClosing(true);
    setTimeout(onClose, 300);
  }, [closing, onClose]);

  // Загрузить сообщения (только при смене chatId)
  useEffect(() => {
    if (chatId) {
      const c = useChatStore.getState().chats.find((ch) => ch.id === chatId);
      if (initialUnreadRef.current === null) {
        initialUnreadRef.current = c?.unreadCount || 0;
      }
      openChat(chatId);
      markAsRead(chatId);
    }
  }, [chatId]); // eslint-disable-line

  // Скролл к последнему сообщению (только если пользователь уже внизу)
  const messagesContainerRef = useRef(null);
  const isNearBottomRef = useRef(true);

  const handleMessagesScroll = useCallback(() => {
    const el = messagesContainerRef.current;
    if (!el) return;
    isNearBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 120;
  }, []);

  useEffect(() => {
    if (isNearBottomRef.current) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [chatMessages.length]);

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
        setTimeout(onClose, 350);
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
        const otherPubKey = await fetchPublicKey(chat.otherUser.id);
        if (otherPubKey) {
          const encrypted = await encryptMessage(text, otherPubKey, chatId);
          if (encrypted) {
            payload.text = encrypted;
            payload.encrypted = true;
          }
        }
      } catch (err) {
        console.error('E2E encrypt error:', err);
      }
    }

    socketRef.current?.emit('message:send', payload);
    setReplyTo(null);
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
      }
    }
    setReplyTo(null);
  }, [chatId, replyTo]);

  const handleTypingStart = () => {
    // Если индикатор набора отключён в настройках приватности — не отправляем
    if (!showTyping) return;
    socketRef.current?.emit('typing:start', { chatId });
  };

  const handleTypingStop = () => {
    if (!showTyping) return;
    socketRef.current?.emit('typing:stop', { chatId });
  };

  // Редактирование сообщения — заполнить input текстом
  const handleEdit = useCallback((msg) => {
    setEditingMsg(msg);
    setReplyTo(null);
  }, []);

  // Отправка отредактированного сообщения
  const handleEditSend = useCallback((newText) => {
    if (!editingMsg || !newText.trim()) return;
    socketRef.current?.emit('message:edit', { messageId: editingMsg.id, chatId, text: newText.trim() });
    setEditingMsg(null);
  }, [editingMsg, chatId, socketRef]);

  // Удаление сообщения
  const handleDelete = useCallback((messageId) => {
    socketRef.current?.emit('message:delete', { messageId, chatId });
  }, [chatId, socketRef]);

  if (!chat) return null;

  const isOnline = chat.otherUser ? onlineUsers.includes(chat.otherUser.id) : false;
  const typingInChat = typingUsers[chatId] || [];

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
          typingUsernames={typingInChat.length ? ['печатает'] : []}
          onCall={onCall}
          onMembers={chat.type === 'group' ? () => setMembersOpen(true) : undefined}
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

      <div
        className={`chat-view__messages ${compactMessages ? 'chat-view__messages--compact' : ''}`}
        ref={messagesContainerRef}
        onScroll={handleMessagesScroll}
      >
        {chatMessages.map((msg, idx) => {
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

          // Разделитель дат
          const showDateSep = !prev ||
            new Date(msg.createdAt).toDateString() !== new Date(prev.createdAt).toDateString();

          // Hue identity
          const hue = getHueFromString(msg.user?.username || msg.userId || '');

          return (
            <Fragment key={msg.id}>
              {showDateSep && <DateSeparator date={msg.createdAt} />}
              {firstUnreadId === msg.id && <UnreadDivider />}
              {showGap && !showDateSep && <div className="chat-view__group-gap" />}
              <ChatMessage
                message={msg}
                isOwn={isOwn}
                groupPosition={groupPosition}
                hue={hue}
                senderName={msg.user?.username || 'Unknown'}
                isRead={msg.readBy?.includes?.(chat.otherUser?.id) || false}
                onReply={() => setReplyTo(msg)}
                onReact={() => { /* TODO: система реакций */ }}
                onEdit={() => handleEdit(msg)}
                onDelete={() => handleDelete(msg.id)}
                onImageClick={setLightboxSrc}
              />
            </Fragment>
          );
        })}

        {/* Typing bubble */}
        {typingInChat.length > 0 && typingInChat[0] && (
          <TypingBubble
            user={typingInChat[0]}
            hue={getHueFromString(typingInChat[0].username || '')}
          />
        )}

        <div ref={messagesEndRef} />
      </div>

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
    </div>
  );
}
