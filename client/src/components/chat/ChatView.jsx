import React, { useEffect, useRef, useState, useCallback, Fragment } from 'react';
import { useChatStore } from '../../store/chatStore';
import ChatHeader from './ChatHeader';
import ChatMessage from './ChatMessage';
import ChatInput from './ChatInput';
import { MIN_WIDTH, MIN_HEIGHT } from '../../hooks/useWindowManager';
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
}) {
  const { messages, chats, onlineUsers, typingUsers, openChat, markAsRead } = useChatStore();
  const chatMessages = messages[chatId] || [];
  const chat = chats.find((c) => c.id === chatId);
  const messagesEndRef = useRef(null);
  const viewRef = useRef(null);

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
  const userId = useRef(null);
  try {
    userId.current = JSON.parse(atob(localStorage.getItem('token').split('.')[1])).userId;
  } catch {}

  // Анимация закрытия → потом реальное удаление
  const animateClose = useCallback(() => {
    if (closing) return;
    setClosing(true);
    setTimeout(onClose, 300);
  }, [closing, onClose]);

  // Загрузить сообщения
  useEffect(() => {
    if (chatId) {
      openChat(chatId);
      markAsRead(chatId);
    }
  }, [chatId, openChat, markAsRead]);

  // Скролл к последнему сообщению
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chatMessages.length]);

  // Escape — закрыть только focused окно
  useEffect(() => {
    if (!isFocused) return;
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
    // Игнорировать клик на кнопку закрытия
    if (e.target.closest('.chat-view__close-btn')) return;

    onFocus();
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
  }, [onFocus, windowState.x, windowState.y]);

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

  // Отправка сообщения
  const handleSend = (text) => {
    const tempId = crypto.randomUUID();
    useChatStore.getState().sendMessage(chatId, text, tempId);
    socketRef.current?.emit('message:send', { chatId, text, tempId });
  };

  const handleTypingStart = () => {
    socketRef.current?.emit('typing:start', { chatId });
  };

  const handleTypingStop = () => {
    socketRef.current?.emit('typing:stop', { chatId });
  };

  if (!chat) return null;

  const isOnline = chat.otherUser ? onlineUsers.includes(chat.otherUser.id) : false;
  const typingInChat = typingUsers[chatId] || [];

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
      className={`chat-view ${morphRect ? 'chat-view--morph' : ''} ${isFocused ? 'chat-view--focused' : ''} ${closing ? 'chat-view--closing' : ''}`}
      ref={viewRef}
      style={{
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
        if (!e.target.closest('.chat-view__resize')) {
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
          typingUsernames={typingInChat.length ? ['печатает'] : []}
        />
        {/* Кнопка закрытия */}
        <button
          className="chat-view__close-btn"
          onClick={(e) => { e.stopPropagation(); animateClose(); }}
          title="Закрыть"
        >
          ×
        </button>
      </div>

      <div className="chat-view__messages">
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

          const showTime = !nextSame;
          const showGap = prev && prev.userId !== msg.userId;

          return (
            <Fragment key={msg.id}>
              {showGap && <div className="chat-view__group-gap" />}
              <ChatMessage
                message={msg}
                isOwn={isOwn}
                groupPosition={groupPosition}
                showTime={showTime}
              />
            </Fragment>
          );
        })}
        <div ref={messagesEndRef} />
      </div>

      <ChatInput
        onSend={handleSend}
        onTypingStart={handleTypingStart}
        onTypingStop={handleTypingStop}
      />

      {/* Resize хендлы — 8 зон по краям и углам */}
      {EDGES.map((edge) => (
        <div
          key={edge}
          className={`chat-view__resize chat-view__resize--${edge}`}
          onPointerDown={(e) => handleResizePointerDown(e, edge)}
          onPointerMove={handleResizePointerMove}
          onPointerUp={handleResizePointerUp}
        />
      ))}
    </div>
  );
}
