import { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, ChatCircle, UserPlus, Check, Calendar, Clock } from '@phosphor-icons/react';
import Avatar from './Avatar';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import { getAvatarHue } from '../../utils/avatar';
import './UserProfileModal.css';

const MONTHS_RU = [
  'январь', 'февраль', 'март', 'апрель', 'май', 'июнь',
  'июль', 'август', 'сентябрь', 'октябрь', 'ноябрь', 'декабрь',
];

function formatDate(iso) {
  const d = new Date(iso);
  return `${MONTHS_RU[d.getMonth()]} ${d.getFullYear()}`;
}

function formatLastSeen(iso) {
  if (!iso) return null;
  const d = new Date(iso);
  const now = new Date();
  const diff = Math.floor((now - d) / 60000);
  if (diff < 1) return 'только что';
  if (diff < 60) return `${diff} мин. назад`;
  if (diff < 1440) return `${Math.floor(diff / 60)} ч. назад`;
  return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
}

const statusLabels = { online: 'В сети', dnd: 'Не беспокоить', invisible: 'Невидимый', offline: 'Не в сети' };

// Staggered children
const containerV = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.06, delayChildren: 0.15 } },
};
const childV = {
  hidden: { opacity: 0, y: 10 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.3, ease: [0.16, 1, 0.3, 1] } },
};

export default function UserProfileModal({ userId, open, onClose, onAddFriend, onOpenChat }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(false);
  const [friendReqSent, setFriendReqSent] = useState(false);
  const cardRef = useRef(null);

  useEffect(() => {
    if (!open || !userId) return;
    setLoading(true);
    setUser(null);
    setFriendReqSent(false);
    fetch(`${API_URL}/api/users/${userId}`, { headers: { ...getAuthHeaders() }, credentials: 'include' })
      .then(r => r.json())
      .then(data => setUser(data))
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, [open, userId]);

  useEffect(() => {
    if (!open) return;
    const handleEsc = (e) => { if (e.key === 'Escape') onClose?.(); };
    window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [open, onClose]);

  const handleBackdrop = useCallback((e) => {
    if (e.target === e.currentTarget) onClose?.();
  }, [onClose]);

  const handleAddFriend = async () => {
    try { await onAddFriend?.(userId); setFriendReqSent(true); } catch (err) { console.error('UserProfileModal addFriend:', err?.message || err); }
  };

  const handleMessage = async () => {
    try {
      const res = await fetch(`${API_URL}/api/chats`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
        credentials: 'include',
        body: JSON.stringify({ participantId: userId }),
      });
      const data = await res.json();
      if (data.id) { onOpenChat?.(data.id, null); onClose?.(); }
    } catch (err) { console.error('UserProfileModal openChat:', err?.message || err); }
  };

  // Subtle tilt on mouse move
  const handleMouseMove = useCallback((e) => {
    if (!cardRef.current) return;
    const rect = cardRef.current.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width - 0.5;
    const y = (e.clientY - rect.top) / rect.height - 0.5;
    cardRef.current.style.transform = `perspective(600px) rotateY(${x * 4}deg) rotateX(${-y * 4}deg)`;
  }, []);

  const handleMouseLeave = useCallback(() => {
    if (cardRef.current) cardRef.current.style.transform = 'perspective(600px) rotateY(0deg) rotateX(0deg)';
  }, []);

  const hue = user ? getAvatarHue(user) : 200;
  const isOnline = user?.status === 'online';

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="uprof-overlay"
          onClick={handleBackdrop}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
        >
          <motion.div
            className="uprof"
            ref={cardRef}
            onMouseMove={handleMouseMove}
            onMouseLeave={handleMouseLeave}
            initial={{ opacity: 0, scale: 0.92, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 10 }}
            transition={{ type: 'spring', stiffness: 400, damping: 28 }}
          >
            {/* Hue glow behind card */}
            <div className="uprof__glow" style={{ background: `radial-gradient(ellipse at 50% 0%, hsla(${hue}, 70%, 50%, 0.15), transparent 70%)` }} />

            {/* Close */}
            <motion.button className="uprof__close" onClick={onClose} whileHover={{ rotate: 90 }} whileTap={{ scale: 0.85 }} transition={{ duration: 0.2 }}>
              <X size={16} weight="bold" />
            </motion.button>

            {loading && (
              <div className="uprof__loading">
                <motion.div className="uprof__spinner" animate={{ rotate: 360 }} transition={{ repeat: Infinity, duration: 0.8, ease: 'linear' }} />
              </div>
            )}

            {!loading && user && (
              <motion.div className="uprof__content" variants={containerV} initial="hidden" animate="visible">
                {/* Avatar with status ring */}
                <motion.div className="uprof__avatar-wrap" variants={childV}>
                  <div className={`uprof__ring ${isOnline ? 'uprof__ring--online' : ''}`} style={{ '--ring-hue': hue }}>
                    <Avatar user={user} size={72} />
                  </div>
                </motion.div>

                {/* Name + tag */}
                <motion.div className="uprof__identity" variants={childV}>
                  <span className="uprof__name">{user.username}</span>
                  <span className="uprof__tag">{user.tag?.startsWith('#') ? user.tag : `#${user.tag}`}</span>
                </motion.div>

                {/* Status */}
                <motion.div className="uprof__status" variants={childV}>
                  <span className={`uprof__dot uprof__dot--${user.status || 'offline'}`} />
                  <span className="uprof__status-text">
                    {statusLabels[user.status] || statusLabels.offline}
                  </span>
                  {!isOnline && user.lastSeenAt && (
                    <span className="uprof__lastseen">
                      <Clock size={10} /> {formatLastSeen(user.lastSeenAt)}
                    </span>
                  )}
                </motion.div>

                {/* Custom status */}
                {user.customStatus && (
                  <motion.div className="uprof__custom" variants={childV}>
                    "{user.customStatus}"
                  </motion.div>
                )}

                {/* Bio */}
                {user.bio && (
                  <motion.p className="uprof__bio" variants={childV}>{user.bio}</motion.p>
                )}

                {/* Info chips */}
                <motion.div className="uprof__chips" variants={childV}>
                  <div className="uprof__chip">
                    <Calendar size={12} />
                    На blesk с {formatDate(user.createdAt)}
                  </div>
                </motion.div>

                {/* Divider */}
                <motion.div className="uprof__divider" variants={childV} />

                {/* Actions */}
                <motion.div className="uprof__actions" variants={childV}>
                  {user.isFriend ? (
                    <motion.button className="uprof__btn uprof__btn--primary" onClick={handleMessage} whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.97 }}>
                      <ChatCircle size={15} /> Написать
                    </motion.button>
                  ) : friendReqSent ? (
                    <div className="uprof__btn uprof__btn--sent">
                      <Check size={15} /> Запрос отправлен
                    </div>
                  ) : (
                    <motion.button className="uprof__btn uprof__btn--outline" onClick={handleAddFriend} whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.97 }}>
                      <UserPlus size={15} /> Добавить в друзья
                    </motion.button>
                  )}
                </motion.div>
              </motion.div>
            )}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
