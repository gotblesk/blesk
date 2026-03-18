import { useState, useEffect, useCallback, useRef } from 'react';
import { User, Zap, UserCheck, MessageCircle, Bell, BellOff, Trash2, Check, X, AlertTriangle } from 'lucide-react';
import { useNotificationStore } from '../../store/notificationStore';
import API_URL from '../../config';
import { getAvatarHue, getAvatarColor } from '../../utils/avatar';
import './NotificationBell.css';

const TABS = [
  { id: 'all', label: 'Все' },
  { id: 'mention', label: '@' },
  { id: 'friend', label: <User size={14} strokeWidth={1.5} /> },
  { id: 'system', label: <Zap size={14} strokeWidth={1.5} /> },
];

const MIN_W = 320;
const MIN_H = 300;
const DEFAULT_W = 420;
const DEFAULT_H = 520;
const SNAP_DIST = 20;
const TOP_BOUND = 72;
const EDGE_PAD = 8;

const EDGES = ['n', 'ne', 'e', 'se', 's', 'sw', 'w', 'nw'];

function typeIcon(type) {
  switch (type) {
    case 'mention': return '@';
    case 'friend_request': return <User size={14} strokeWidth={1.5} />;
    case 'friend_accepted': return <UserCheck size={14} strokeWidth={1.5} />;
    case 'system': return <Zap size={14} strokeWidth={1.5} />;
    case 'message': return <MessageCircle size={14} strokeWidth={1.5} />;
    default: return <Bell size={14} strokeWidth={1.5} />;
  }
}

function timeAgo(date) {
  const now = Date.now();
  const diff = now - new Date(date).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return 'только что';
  if (minutes < 60) return `${minutes} мин`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} ч`;
  const days = Math.floor(hours / 24);
  return `${days} д`;
}

function clampPos(x, y, w, h) {
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  return {
    x: Math.min(Math.max(x, EDGE_PAD), vw - w - EDGE_PAD),
    y: Math.min(Math.max(y, TOP_BOUND), vh - h - EDGE_PAD),
  };
}

function snapPos(x, y, w, h) {
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  let sx = x, sy = y;
  if (Math.abs(x - EDGE_PAD) < SNAP_DIST) sx = EDGE_PAD;
  if (Math.abs(x + w - (vw - EDGE_PAD)) < SNAP_DIST) sx = vw - EDGE_PAD - w;
  if (Math.abs(y - TOP_BOUND) < SNAP_DIST) sy = TOP_BOUND;
  if (Math.abs(y + h - (vh - EDGE_PAD)) < SNAP_DIST) sy = vh - EDGE_PAD - h;
  return { x: sx, y: sy };
}

export default function NotificationBell({ onOpenChat }) {
  const [activeTab, setActiveTab] = useState('all');
  const [shaking, setShaking] = useState(false);
  const prevCountRef = useRef(0);

  // open / closed — простое состояние
  const [isOpen, setIsOpen] = useState(false);
  // CSS-класс для анимации: '' → 'entering' → 'open' → 'leaving' → ''
  const [animClass, setAnimClass] = useState('');
  // transform-origin для эффекта «вырастания из колокольчика»
  const [originStyle, setOriginStyle] = useState({});

  // Позиция и размер окна
  const [winPos, setWinPos] = useState({ x: 0, y: 0 });
  const [winSize, setWinSize] = useState({ w: DEFAULT_W, h: DEFAULT_H });

  // Блокировка drag сразу после открытия
  const dragLockRef = useRef(false);

  const bellRef = useRef(null);
  const windowRef = useRef(null);
  const dragRef = useRef({ active: false });
  const resizeRef = useRef({ active: false });
  const closingTimerRef = useRef(null);

  const {
    notifications,
    unreadCount,
    fetchNotifications,
    markAsRead,
    markAllAsRead,
    clearAll,
    removeNotification,
  } = useNotificationStore();

  useEffect(() => { fetchNotifications(); }, [fetchNotifications]);

  // Shake при новом уведомлении
  useEffect(() => {
    if (unreadCount > prevCountRef.current && prevCountRef.current >= 0) {
      setShaking(true);
      const t = setTimeout(() => setShaking(false), 700);
      return () => clearTimeout(t);
    }
    prevCountRef.current = unreadCount;
  }, [unreadCount]);

  // Escape закрывает (через ref чтобы избежать temporal dead zone)
  const handleCloseRef = useRef(null);
  useEffect(() => {
    if (!isOpen) return;
    const handler = (e) => { if (e.key === 'Escape') handleCloseRef.current?.(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [isOpen]);

  // Клик вне окна закрывает
  useEffect(() => {
    if (!isOpen || animClass !== 'open') return;
    const handler = (e) => {
      if (
        windowRef.current && !windowRef.current.contains(e.target) &&
        bellRef.current && !bellRef.current.contains(e.target)
      ) {
        handleClose();
      }
    };
    // Задержка чтобы текущий клик не сработал
    const t = setTimeout(() => window.addEventListener('pointerdown', handler), 50);
    return () => { clearTimeout(t); window.removeEventListener('pointerdown', handler); };
  }, [isOpen, animClass]);

  // Вычислить transform-origin относительно окна
  const calcOrigin = useCallback((winX, winY) => {
    if (!bellRef.current) return 'top right';
    const bell = bellRef.current.getBoundingClientRect();
    // Центр колокольчика относительно окна
    const ox = bell.left + bell.width / 2 - winX;
    const oy = bell.top + bell.height / 2 - winY;
    return `${ox}px ${oy}px`;
  }, []);

  // Открытие
  const handleOpen = useCallback(() => {
    // Очистить таймер закрытия если есть
    if (closingTimerRef.current) {
      clearTimeout(closingTimerRef.current);
      closingTimerRef.current = null;
    }

    const finalPos = (() => {
      if (!bellRef.current) return { x: 100, y: TOP_BOUND + 8 };
      const rect = bellRef.current.getBoundingClientRect();
      return clampPos(rect.right - DEFAULT_W, rect.bottom + 8, DEFAULT_W, DEFAULT_H);
    })();

    setWinPos(finalPos);
    setWinSize({ w: DEFAULT_W, h: DEFAULT_H });
    setOriginStyle({ transformOrigin: calcOrigin(finalPos.x, finalPos.y) });

    // Заблокировать drag на 300ms после открытия
    dragLockRef.current = true;
    setTimeout(() => { dragLockRef.current = false; }, 300);

    setIsOpen(true);
    setAnimClass('entering');
  }, [calcOrigin]);

  // entering → open: ждём пока DOM отрисует entering, потом переключаем
  useEffect(() => {
    if (animClass !== 'entering') return;
    // Форсируем reflow
    if (windowRef.current) void windowRef.current.offsetHeight;
    const t = setTimeout(() => setAnimClass('open'), 20);
    return () => clearTimeout(t);
  }, [animClass]);

  // Закрытие (+ обновляем ref для Escape handler)
  const handleClose = useCallback(() => {
    if (!isOpen) return;

    // Пересчитать origin для обратной анимации
    const origin = calcOrigin(winPos.x, winPos.y);
    setOriginStyle({ transformOrigin: origin });

    setAnimClass('leaving');
    closingTimerRef.current = setTimeout(() => {
      setIsOpen(false);
      setAnimClass('');
      closingTimerRef.current = null;
    }, 300);
  }, [isOpen, calcOrigin, winPos]);
  handleCloseRef.current = handleClose;

  // Клик по колокольчику — toggle
  const handleBellClick = useCallback(() => {
    if (!isOpen) {
      handleOpen();
    } else if (animClass === 'open') {
      handleClose();
    }
    // Во время entering/leaving — игнорируем
  }, [isOpen, animClass, handleOpen, handleClose]);

  // === DRAG по хедеру ===
  const handleDragDown = useCallback((e) => {
    if (dragLockRef.current) return;
    if (e.target.closest('.bell-window__close') || e.target.closest('.bell-header__read-all')) return;
    dragRef.current = {
      active: true,
      startX: e.clientX, startY: e.clientY,
      startWinX: winPos.x, startWinY: winPos.y,
    };
    e.currentTarget.setPointerCapture(e.pointerId);
    e.preventDefault();
  }, [winPos]);

  const handleDragMove = useCallback((e) => {
    if (!dragRef.current.active) return;
    const d = dragRef.current;
    const clamped = clampPos(d.startWinX + e.clientX - d.startX, d.startWinY + e.clientY - d.startY, winSize.w, winSize.h);
    const snapped = snapPos(clamped.x, clamped.y, winSize.w, winSize.h);
    setWinPos(snapped);
  }, [winSize]);

  const handleDragUp = useCallback(() => { dragRef.current.active = false; }, []);

  // === RESIZE по краям ===
  const handleResizeDown = useCallback((e, edge) => {
    e.stopPropagation();
    resizeRef.current = {
      active: true, edge,
      startX: e.clientX, startY: e.clientY,
      startWinX: winPos.x, startWinY: winPos.y,
      startW: winSize.w, startH: winSize.h,
    };
    e.currentTarget.setPointerCapture(e.pointerId);
    e.preventDefault();
  }, [winPos, winSize]);

  const handleResizeMove = useCallback((e) => {
    const r = resizeRef.current;
    if (!r.active) return;
    const dx = e.clientX - r.startX;
    const dy = e.clientY - r.startY;
    let { startWinX: x, startWinY: y, startW: w, startH: h } = r;
    const edge = r.edge;

    if (edge.includes('e')) w = r.startW + dx;
    if (edge.includes('w')) { w = r.startW - dx; x = r.startWinX + dx; }
    if (edge.includes('s')) h = r.startH + dy;
    if (edge === 'n' || edge === 'ne' || edge === 'nw') { h = r.startH - dy; y = r.startWinY + dy; }

    if (w < MIN_W) { if (edge.includes('w')) x = r.startWinX + r.startW - MIN_W; w = MIN_W; }
    if (h < MIN_H) { if (edge === 'n' || edge === 'ne' || edge === 'nw') y = r.startWinY + r.startH - MIN_H; h = MIN_H; }

    const clamped = clampPos(x, y, w, h);
    setWinPos(clamped);
    setWinSize({ w, h });
  }, []);

  const handleResizeUp = useCallback(() => { resizeRef.current.active = false; }, []);

  // Фильтрация
  const filtered = activeTab === 'all'
    ? notifications
    : notifications.filter((n) => {
        if (activeTab === 'friend') return n.type === 'friend_request' || n.type === 'friend_accepted';
        return n.type === activeTab;
      });

  const handleItemClick = useCallback((notification) => {
    if (!notification.isRead) markAsRead(notification.id);
    if (notification.roomId && onOpenChat) {
      onOpenChat(notification.roomId, null);
      handleClose();
    }
  }, [markAsRead, onOpenChat, handleClose]);

  // Предотвращаем двойные клики на accept/decline
  const processingFriendRef = useRef(new Set());

  const handleAcceptFriend = useCallback(async (notification, e) => {
    e.stopPropagation();
    if (processingFriendRef.current.has(notification.id)) return;
    processingFriendRef.current.add(notification.id);
    try {
      const token = localStorage.getItem('token');
      const res = await fetch(`${API_URL}/api/friends/requests/pending`, { headers: { Authorization: `Bearer ${token}` } });
      const requests = await res.json();
      const request = requests.find((r) => r.senderId === notification.fromUserId);
      if (request) {
        const acceptRes = await fetch(`${API_URL}/api/friends/requests/${request.id}/accept`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } });
        const data = await acceptRes.json();
        if (data.ok && data.roomId && onOpenChat) { onOpenChat(data.roomId, null); handleClose(); }
      }
      markAsRead(notification.id);
    } catch (err) { console.error('Accept friend error:', err); }
    finally { processingFriendRef.current.delete(notification.id); }
  }, [markAsRead, onOpenChat, handleClose]);

  const handleDeclineFriend = useCallback(async (notification, e) => {
    e.stopPropagation();
    if (processingFriendRef.current.has(notification.id)) return;
    processingFriendRef.current.add(notification.id);
    try {
      const token = localStorage.getItem('token');
      const res = await fetch(`${API_URL}/api/friends/requests/pending`, { headers: { Authorization: `Bearer ${token}` } });
      const requests = await res.json();
      const request = requests.find((r) => r.senderId === notification.fromUserId);
      if (request) {
        await fetch(`${API_URL}/api/friends/requests/${request.id}/decline`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } });
      }
      markAsRead(notification.id);
    } catch (err) { console.error('Decline friend error:', err); }
    finally { processingFriendRef.current.delete(notification.id); }
  }, [markAsRead]);

  // Подтверждение очистки
  const [confirmClear, setConfirmClear] = useState(false);
  const confirmTimerRef = useRef(null);

  const handleClearAll = useCallback(() => {
    if (!confirmClear) {
      setConfirmClear(true);
      // Автосброс через 3 секунды
      confirmTimerRef.current = setTimeout(() => setConfirmClear(false), 3000);
      return;
    }
    clearTimeout(confirmTimerRef.current);
    setConfirmClear(false);
    clearAll();
  }, [confirmClear, clearAll]);

  // Cleanup таймера
  useEffect(() => () => clearTimeout(confirmTimerRef.current), []);

  const bellClasses = [
    'bell-trigger',
    isOpen && 'bell-trigger--active',
    shaking && 'bell-trigger--shake',
    !isOpen && unreadCount > 0 && 'bell-trigger--has-unread',
  ].filter(Boolean).join(' ');

  return (
    <>
      <button ref={bellRef} className={bellClasses} onClick={handleBellClick}>
        <Bell size={18} strokeWidth={1.5} />
        {unreadCount > 0 && (
          <span className="bell-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>
        )}
      </button>

      {isOpen && (
        <div
          ref={windowRef}
          className={`bell-window bell-window--${animClass}`}
          style={{
            left: winPos.x,
            top: winPos.y,
            width: winSize.w,
            height: winSize.h,
            ...originStyle,
          }}
        >
          {/* Resize edges */}
          {animClass === 'open' && EDGES.map((edge) => (
            <div
              key={edge}
              className={`bell-resize bell-resize--${edge}`}
              onPointerDown={(e) => handleResizeDown(e, edge)}
              onPointerMove={handleResizeMove}
              onPointerUp={handleResizeUp}
            />
          ))}

          {/* Specular highlight */}
          <div className="bell-window__shine" />

          {/* Хедер — drag zone */}
          <div
            className="bell-header"
            onPointerDown={handleDragDown}
            onPointerMove={handleDragMove}
            onPointerUp={handleDragUp}
          >
            <span className="bell-header__title">Уведомления</span>
            <div className="bell-header__actions">
              {notifications.length > 0 && (
                <button
                  className={`bell-header__read-all ${confirmClear ? 'bell-header__read-all--confirm' : ''}`}
                  onClick={(e) => { e.stopPropagation(); handleClearAll(); }}
                  title={confirmClear ? 'Нажмите ещё раз для подтверждения' : 'Очистить все'}
                >
                  {confirmClear ? <><AlertTriangle size={14} strokeWidth={1.5} /> Точно?</> : <Trash2 size={14} strokeWidth={1.5} />}
                </button>
              )}
              {unreadCount > 0 && (
                <button className="bell-header__read-all" onClick={(e) => { e.stopPropagation(); markAllAsRead(); }}>
                  <Check size={14} strokeWidth={2} /> Все
                </button>
              )}
              <button className="bell-window__close" onClick={(e) => { e.stopPropagation(); handleClose(); }}>
                <X size={14} strokeWidth={2} />
              </button>
            </div>
          </div>

          {/* Контент */}
          <div className={`bell-content ${animClass === 'open' ? 'bell-content--visible' : ''}`}>
            <div className="bell-tabs">
              {TABS.map((tab) => (
                <button
                  key={tab.id}
                  className={`bell-tab ${activeTab === tab.id ? 'bell-tab--active' : ''}`}
                  onClick={() => setActiveTab(tab.id)}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            <div className="bell-list">
              {filtered.length === 0 ? (
                <div className="bell-empty">
                  <div className="bell-empty__icon"><BellOff size={18} strokeWidth={1.5} /></div>
                  <div className="bell-empty__text">Нет уведомлений</div>
                </div>
              ) : (
                filtered.map((n) => (
                  <div
                    key={n.id}
                    className={`bell-item ${!n.isRead ? 'bell-item--unread' : ''}`}
                    onClick={() => handleItemClick(n)}
                  >
                    {n.fromUser ? (
                      <div className="bell-item__avatar" style={{ background: getAvatarColor(getAvatarHue(n.fromUser)) }}>
                        {(n.fromUser.username || '?')[0].toUpperCase()}
                      </div>
                    ) : (
                      <div className="bell-item__avatar bell-item__avatar--system">{typeIcon(n.type)}</div>
                    )}
                    <div className="bell-item__content">
                      <div className="bell-item__title">{n.title}</div>
                      {n.body && <div className="bell-item__body">{n.body}</div>}
                      {n.type === 'friend_request' && !n.isRead && (
                        <div className="bell-item__actions">
                          <button className="bell-item__action bell-item__action--accept" onClick={(e) => handleAcceptFriend(n, e)}>Принять</button>
                          <button className="bell-item__action bell-item__action--decline" onClick={(e) => handleDeclineFriend(n, e)}>Отклонить</button>
                        </div>
                      )}
                      <div className="bell-item__time">{timeAgo(n.createdAt)}</div>
                    </div>
                    <button
                      className="bell-item__delete"
                      onClick={(e) => {
                        e.stopPropagation();
                        removeNotification(n.id);
                      }}
                      title="Удалить"
                    >
                      <X size={14} strokeWidth={2} />
                    </button>
                    {!n.isRead && <div className="bell-item__dot" />}
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
