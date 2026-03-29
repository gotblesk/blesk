import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { Bell, BellSlash, Check, X, Warning, Trash, SignIn, Lightning, User, UserCheck, ChatCircle, At, Sparkle } from '@phosphor-icons/react';
import Avatar from './Avatar';
import { useNotificationStore } from '../../store/notificationStore';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import './NotificationBell.css';

// Decorative icon colors — hardcoded hex intentional (used in inline styles/gradients,
// these are category accent colors that stay consistent across themes)
const TYPES = {
  system:          { icon: Lightning,     color: '#c8ff00' },
  login:           { icon: SignIn,        color: '#60a5fa' },
  mention:         { icon: At,            color: '#f59e0b' },
  friend_request:  { icon: User,          color: '#a78bfa' },
  friend_accepted: { icon: UserCheck,     color: '#4ade80' },
  message:         { icon: ChatCircle,    color: '#38bdf8' },
};
const getT = t => TYPES[t] || TYPES.system;

function timeAgo(d) {
  const m = Math.floor((Date.now() - new Date(d).getTime()) / 60000);
  if (m < 1) return 'сейчас';
  if (m < 60) return `${m}м`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}ч`;
  return `${Math.floor(h / 24)}д`;
}

function smartGroup(list) {
  const out = [];
  for (const n of list) {
    const prev = out[out.length - 1];
    if (prev && prev.type === n.type && prev.title === n.title && n.type !== 'friend_request') {
      prev.count++;
      prev.ids.push(n.id);
      prev.unread = prev.unread || !n.isRead;
    } else {
      out.push({ ...n, count: 1, ids: [n.id], unread: !n.isRead });
    }
  }
  return out;
}

// Card rotation pattern
const ROTATIONS = [-2, 1.2, -0.8, 1.5, -0.5, 1.8, -1.2, 0.6];

// Calculate Y offsets dynamically based on card heights
function calcOffsets(items) {
  const offsets = [0];
  let acc = 0;
  for (let i = 0; i < items.length - 1; i++) {
    const isFriend = items[i].type === 'friend_request' && items[i].unread;
    const isFirst = i === 0;
    const h = isFriend ? 130 : (isFirst ? 78 : 62); // hero taller, first has toolbar
    acc -= (h + 10);
    offsets.push(acc);
  }
  return offsets;
}

export default function NotificationBell({ onOpenChat }) {
  const [open, setOpen] = useState(false);
  const [shaking, setShaking] = useState(false);
  const [expanded, setExpanded] = useState(null);
  const closingDetailRef = useRef(false);
  const prevCnt = useRef(0);
  const bellRef = useRef(null);
  const [anchor, setAnchor] = useState(null);

  const { notifications, unreadCount, fetchNotifications, markAsRead, markAllAsRead, clearAll, removeNotification } = useNotificationStore();

  useEffect(() => { fetchNotifications(); }, [fetchNotifications]);
  useEffect(() => {
    if (unreadCount > prevCnt.current && prevCnt.current >= 0) { setShaking(true); const t = setTimeout(() => setShaking(false), 700); return () => clearTimeout(t); }
    prevCnt.current = unreadCount;
  }, [unreadCount]);
  // [IMP-7 A2] Esc: сначала закрыть expanded card, потом панель
  useEffect(() => {
    if (!open) return;
    const h = e => {
      if (e.key === 'Escape') {
        if (expanded !== null) {
          closeDetail();
        } else {
          setOpen(false);
        }
      }
    };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [open, expanded]);

  const grouped = useMemo(() => smartGroup(notifications), [notifications]);

  const handleOpen = useCallback(() => {
    if (bellRef.current) setAnchor(bellRef.current.getBoundingClientRect());
    setOpen(true);
    if (unreadCount > 0) markAllAsRead();
  }, [unreadCount, markAllAsRead]);

  // Click outside
  useEffect(() => {
    if (!open) return;
    const h = (e) => {
      if (closingDetailRef.current) return;
      if (bellRef.current?.contains(e.target)) return;
      const stack = document.querySelector('.rs-stack');
      if (stack && stack.contains(e.target)) return;
      const detail = document.querySelector('.rs-detail');
      if (detail && detail.contains(e.target)) return;
      setOpen(false);
    };
    const t = setTimeout(() => window.addEventListener('pointerdown', h), 80);
    return () => { clearTimeout(t); window.removeEventListener('pointerdown', h); };
  }, [open]);

  // Friend actions
  const procRef = useRef(new Set());
  const acceptFriend = useCallback(async (n, e) => {
    e.stopPropagation(); if (procRef.current.has(n.id)) return; procRef.current.add(n.id);
    try { const r = await fetch(`${API_URL}/api/friends/requests/pending`, { headers: { ...getAuthHeaders() }, credentials: 'include' }); const reqs = await r.json(); const rq = reqs.find(x => x.senderId === n.fromUserId); if (rq) { const r2 = await fetch(`${API_URL}/api/friends/requests/${rq.id}/accept`, { method: 'POST', headers: { ...getAuthHeaders() }, credentials: 'include' }); const d = await r2.json(); if (d.ok && d.roomId && onOpenChat) { onOpenChat(d.roomId, null); setOpen(false); } } markAsRead(n.id); } catch (err) { console.error('NotificationBell acceptFriend:', err?.message || err); } finally { procRef.current.delete(n.id); }
  }, [markAsRead, onOpenChat]);
  const declineFriend = useCallback(async (n, e) => {
    e.stopPropagation(); if (procRef.current.has(n.id)) return; procRef.current.add(n.id);
    try { const r = await fetch(`${API_URL}/api/friends/requests/pending`, { headers: { ...getAuthHeaders() }, credentials: 'include' }); const reqs = await r.json(); const rq = reqs.find(x => x.senderId === n.fromUserId); if (rq) await fetch(`${API_URL}/api/friends/requests/${rq.id}/decline`, { method: 'POST', headers: { ...getAuthHeaders() }, credentials: 'include' }); markAsRead(n.id); } catch (err) { console.error('NotificationBell declineFriend:', err?.message || err); } finally { procRef.current.delete(n.id); }
  }, [markAsRead]);

  const [confirmClear, setConfirmClear] = useState(false);
  const confirmT = useRef(null);
  const doClear = useCallback(() => { if (!confirmClear) { setConfirmClear(true); confirmT.current = setTimeout(() => setConfirmClear(false), 3000); return; } clearTimeout(confirmT.current); setConfirmClear(false); clearAll(); }, [confirmClear, clearAll]);
  useEffect(() => () => clearTimeout(confirmT.current), []);

  // Stack position
  const cardW = 320;
  const stackX = anchor ? Math.min(Math.max(12, anchor.left + anchor.width / 2 - cardW / 2), window.innerWidth - cardW - 12) : 100;
  const stackBottom = anchor ? (window.innerHeight - anchor.top + 14) : 70;
  const offsets = useMemo(() => calcOffsets(grouped), [grouped]);
  const totalStackH = offsets.length > 0 ? Math.abs(offsets[offsets.length - 1]) + 120 : 120;

  const closeDetail = useCallback(() => {
    closingDetailRef.current = true;
    setExpanded(null);
    setTimeout(() => { closingDetailRef.current = false; }, 400);
  }, []);

  const bellCls = ['rs-bell', open && 'rs-bell--on', shaking && 'rs-bell--shake', !open && unreadCount > 0 && 'rs-bell--glow'].filter(Boolean).join(' ');

  return (
    <>
      <button ref={bellRef} className={bellCls} onClick={() => open ? setOpen(false) : handleOpen()}>
        <Bell size={17} weight="regular" />
        {unreadCount > 0 && (
          <motion.span className="rs-bell__badge" initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: 'spring', stiffness: 500, damping: 20 }}>
            {unreadCount > 99 ? '99+' : unreadCount}
          </motion.span>
        )}
      </button>

      {createPortal(
        <AnimatePresence>
          {open && (
            <>
              {/* Light backdrop */}
              <motion.div className="rs-bg" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={() => { if (expanded || closingDetailRef.current) { closeDetail(); } else { setOpen(false); } }} />

              {/* Card stack */}
              <motion.div
                className={`rs-stack ${expanded ? 'rs-stack--dimmed' : ''}`}
                style={{ left: stackX, bottom: stackBottom, width: cardW }}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0, y: 40, scale: 0.8, filter: 'blur(8px)' }}
                transition={{ duration: 0.25, ease: [0.4, 0, 1, 1] }}
              >
                <AnimatePresence mode="popLayout">
                  {grouped.length === 0 ? (
                    <motion.div
                      key="empty"
                      className="rs-card rs-card--empty"
                      initial={{ opacity: 0, y: 40, scale: 0.8 }}
                      animate={{ opacity: 1, y: 0, scale: 1 }}
                      exit={{ opacity: 0, y: 40, scale: 0.8 }}
                      transition={{ type: 'spring', damping: 22, stiffness: 300 }}
                    >
                      <motion.div animate={{ y: [0, -4, 0] }} transition={{ repeat: Infinity, duration: 2.5, ease: 'easeInOut' }}>
                        <Sparkle size={20} weight="regular" style={{ color: 'var(--accent)' }} />
                      </motion.div>
                      <span className="rs-card__empty-text">Всё спокойно</span>
                    </motion.div>
                  ) : (
                    grouped.map((item, i) => (
                      <RSCard
                        key={item.ids[0]}
                        item={item}
                        index={i}
                        total={grouped.length}
                        yOffset={offsets[i] || 0}
                        rotation={ROTATIONS[i % ROTATIONS.length]}
                        isFirst={i === 0}
                        onItemClick={() => {
                          if (!item.isRead) markAsRead(item.id);
                          if (item.type !== 'friend_request' && item.roomId && onOpenChat) {
                            setOpen(false);
                            onOpenChat(item.roomId, null);
                          } else {
                            setExpanded(item);
                          }
                        }}
                        onAccept={acceptFriend}
                        onDecline={declineFriend}
                        onRemove={() => item.ids.forEach(id => removeNotification(id))}
                        unreadCount={unreadCount}
                        onMarkAll={markAllAsRead}
                        onClearAll={doClear}
                        confirmClear={confirmClear}
                        notifCount={notifications.length}
                      />
                    ))
                  )}
                </AnimatePresence>
              </motion.div>

              {/* Expanded notification detail */}
              <AnimatePresence>
                {expanded && (
                  <motion.div
                    className="rs-detail"
                    initial={{ opacity: 0, scale: 0.8, y: 40 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.85, y: 30 }}
                    transition={{ type: 'spring', damping: 22, stiffness: 280, mass: 0.7 }}
                    onClick={e => e.stopPropagation()}
                  >
                    <ExpandedCard
                      item={expanded}
                      onClose={closeDetail}
                      onOpenChat={(roomId) => { setExpanded(null); setOpen(false); onOpenChat?.(roomId, null); }}
                    />
                  </motion.div>
                )}
              </AnimatePresence>
            </>
          )}
        </AnimatePresence>,
        document.body
      )}
    </>
  );
}

// ═══════ RADIAL STACK CARD ═══════
function RSCard({ item, index, total, yOffset, rotation, isFirst, onItemClick, onAccept, onDecline, onRemove, unreadCount, onMarkAll, onClearAll, confirmClear, notifCount }) {
  const cfg = getT(item.type);
  const Icon = cfg.icon;
  const isFriend = item.type === 'friend_request' && item.unread;

  return (
    <motion.div
      className={`rs-card ${item.unread ? 'rs-card--unread' : ''} ${isFriend ? 'rs-card--hero' : ''}`}
      style={{ '--card-color': cfg.color, zIndex: total - index }}
      initial={{ opacity: 0, y: 80, scale: 0.6, rotate: 0, x: index % 2 === 0 ? -20 : 20 }}
      animate={{ opacity: 1, y: yOffset, scale: 1, rotate: rotation, x: 0 }}
      exit={{ opacity: 0, y: 80, scale: 0.5, rotate: rotation * 3, filter: 'blur(6px)' }}
      transition={{ delay: index * 0.06, type: 'spring', damping: 18, stiffness: 250, mass: 0.8 }}
      layout
      whileHover={{ y: yOffset - 6, scale: 1.03, rotate: 0, zIndex: 50, boxShadow: '0 20px 56px rgba(0,0,0,0.5)' }}
      whileTap={{ scale: 0.97 }}
      onClick={!isFriend ? onItemClick : undefined}
    >
      {/* Actions integrated into first card */}
      {isFirst && (unreadCount > 0 || notifCount > 0) && (
        <div className="rs-card__toolbar">
          {unreadCount > 0 && <span className="rs-card__new-count">{unreadCount} новых</span>}
          <div className="rs-card__toolbar-btns">
            {notifCount > 0 && (
              <motion.button className={`rs-tbtn ${confirmClear ? 'rs-tbtn--red' : ''}`} onClick={e => { e.stopPropagation(); onClearAll(); }} whileTap={{ scale: 0.85 }}>
                {confirmClear ? <><Warning size={10} /> Точно?</> : <><Trash size={10} /> Очистить</>}
              </motion.button>
            )}
            {unreadCount > 0 && (
              <motion.button className="rs-tbtn" onClick={e => { e.stopPropagation(); onMarkAll(); }} whileTap={{ scale: 0.85 }}>
                <Check size={10} /> Прочитать
              </motion.button>
            )}
          </div>
        </div>
      )}
      {/* Accent top edge */}
      {item.unread && (
        <motion.div
          className="rs-card__edge"
          style={{ background: `linear-gradient(90deg, transparent, ${cfg.color}, transparent)` }}
          initial={{ scaleX: 0 }}
          animate={{ scaleX: 1 }}
          transition={{ delay: index * 0.05 + 0.2, duration: 0.4 }}
        />
      )}

      <div className="rs-card__main">
        {/* Avatar / Icon */}
        {item.fromUser ? (
          <div className="rs-card__ava-wrap">
            <Avatar user={item.fromUser} size={isFriend ? 38 : 30} />
            {item.unread && <div className="rs-card__ava-ring" style={{ borderColor: cfg.color }} />}
          </div>
        ) : (
          <div className="rs-card__icon" style={{ color: cfg.color }}>
            <Icon size={14} />
          </div>
        )}

        <div className="rs-card__body">
          <div className="rs-card__top">
            <span className="rs-card__title">
              {item.title}
              {item.count > 1 && <span className="rs-card__mult" style={{ color: cfg.color }}> ×{item.count}</span>}
            </span>
            <span className="rs-card__time">{timeAgo(item.createdAt)}</span>
          </div>
          {item.body && <span className="rs-card__desc">{item.body}</span>}
        </div>

        <motion.button className="rs-card__x" onClick={e => { e.stopPropagation(); onRemove(); }} whileTap={{ scale: 0.7 }}>
          <X size={10} />
        </motion.button>
      </div>

      {/* Friend request buttons */}
      {isFriend && (
        <div className="rs-card__btns">
          <motion.button className="rs-btn rs-btn--yes" onClick={e => onAccept(item, e)} whileTap={{ scale: 0.93 }} whileHover={{ y: -1 }}>
            <Check size={13} /> Принять
          </motion.button>
          <motion.button className="rs-btn rs-btn--no" onClick={e => onDecline(item, e)} whileTap={{ scale: 0.93 }}>
            Нет
          </motion.button>
        </div>
      )}

      {/* Accent glow bar */}
      {item.unread && <div className="rs-card__glow" style={{ background: cfg.color }} />}
    </motion.div>
  );
}

// ═══════ EXPANDED DETAIL CARD ═══════
function ExpandedCard({ item, onClose, onOpenChat }) {
  const cfg = getT(item.type);
  const Icon = cfg.icon;

  const MONTHS = ['января','февраля','марта','апреля','мая','июня','июля','августа','сентября','октября','ноября','декабря'];
  const d = new Date(item.createdAt);
  const fullDate = `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}, ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;

  return (
    <div className="rs-exp">
      {/* Top accent line */}
      <div className="rs-exp__accent" style={{ background: `linear-gradient(90deg, transparent, ${cfg.color}, transparent)` }} />

      {/* Close */}
      <motion.button className="rs-exp__close" onClick={e => { e.stopPropagation(); onClose(); }} whileHover={{ rotate: 90 }} whileTap={{ scale: 0.8 }}>
        <X size={15} />
      </motion.button>

      {/* Header */}
      <div className="rs-exp__header">
        {item.fromUser ? (
          <div className="rs-exp__ava">
            <Avatar user={item.fromUser} size={48} />
            <div className="rs-exp__ava-ring" style={{ borderColor: cfg.color }} />
          </div>
        ) : (
          <div className="rs-exp__type-icon" style={{ color: cfg.color, background: `${cfg.color}12` }}>
            <Icon size={22} />
          </div>
        )}
        <div className="rs-exp__info">
          <span className="rs-exp__title">{item.title}</span>
          <span className="rs-exp__date">{fullDate}</span>
        </div>
      </div>

      {/* Body */}
      {item.body && (
        <div className="rs-exp__body">
          {item.body}
        </div>
      )}

      {/* Type badge */}
      <div className="rs-exp__badge" style={{ color: cfg.color, background: `${cfg.color}10`, borderColor: `${cfg.color}20` }}>
        <Icon size={12} />
        {item.type === 'friend_request' ? 'Заявка в друзья' :
         item.type === 'friend_accepted' ? 'Новый друг' :
         item.type === 'mention' ? 'Упоминание' :
         item.type === 'message' ? 'Сообщение' :
         item.type === 'login' ? 'Безопасность' : 'Система'}
        {item.count > 1 && ` ×${item.count}`}
      </div>

      {/* Action */}
      {item.roomId && (
        <motion.button className="rs-exp__action" onClick={() => onOpenChat(item.roomId)} whileTap={{ scale: 0.95 }} whileHover={{ y: -1 }}>
          <ChatCircle size={14} /> Перейти к чату
        </motion.button>
      )}
    </div>
  );
}
