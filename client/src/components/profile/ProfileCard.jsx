import { useState, useEffect, useCallback, useRef } from 'react';
import { motion } from 'framer-motion';
import { PencilSimple, ChatCircle, UserPlus, Check, Calendar, Clock, Lock, Warning } from '@phosphor-icons/react';
import Avatar from '../ui/Avatar';
import AvatarLightbox from './AvatarLightbox';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import { getAvatarHue } from '../../utils/avatar';
import { formatJoinDate, formatLastSeen } from '../../utils/months';
import { useChatStore } from '../../store/chatStore';
import './ProfileCard.css';

const statusLabels = { online: 'В сети', dnd: 'Не беспокоить', invisible: 'Невидимый', offline: 'Не в сети' };

const containerV = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.04, delayChildren: 0.1 } },
};
const childV = {
  hidden: { opacity: 0, y: 8 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.25, ease: [0.16, 1, 0.3, 1] } },
};

export default function ProfileCard({ mode = 'other', userId, user: ownUser, onEdit, onOpenChat, onClose, onAddFriend }) {
  const [userData, setUserData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [friendReqSent, setFriendReqSent] = useState(false);
  const [lightboxOpen, setLightboxOpen] = useState(false);
  const cardRef = useRef(null);

  // Определяем данные пользователя
  const user = mode === 'own' ? ownUser : userData;
  const id = userId || user?.id;

  // H4: narrow selector — only subscribe to this user's status
  const onlineStatusFromStore = useChatStore(s => s.userStatuses[id]);

  // C1+C2: fetch с AbortController + retry
  const [fetchKey, setFetchKey] = useState(0);

  useEffect(() => {
    if (mode === 'own' || !userId) return;
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    setUserData(null);
    setFriendReqSent(false);
    fetch(`${API_URL}/api/users/${userId}`, { headers: { ...getAuthHeaders() }, credentials: 'include', signal: controller.signal })
      .then(r => { if (!r.ok) throw new Error(r.status === 404 ? 'not_found' : 'error'); return r.json(); })
      .then(data => setUserData(data))
      .catch(err => { if (err.name !== 'AbortError') setError(err.message === 'not_found' ? 'not_found' : 'network'); })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [mode, userId, fetchKey]);

  // 3D tilt
  const handleMouseMove = useCallback((e) => {
    if (!cardRef.current) return;
    const rect = cardRef.current.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width - 0.5;
    const y = (e.clientY - rect.top) / rect.height - 0.5;
    cardRef.current.style.transform = `perspective(800px) rotateY(${x * 4}deg) rotateX(${-y * 4}deg)`;
  }, []);

  const handleMouseLeave = useCallback(() => {
    if (cardRef.current) cardRef.current.style.transform = 'perspective(800px) rotateY(0deg) rotateX(0deg)';
  }, []);

  // Добавить в друзья
  const handleAddFriend = async () => {
    try {
      if (onAddFriend) {
        await onAddFriend(userId);
      } else {
        await fetch(`${API_URL}/api/friends/request`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
          credentials: 'include',
          body: JSON.stringify({ userId }),
        });
      }
      setFriendReqSent(true);
    } catch (err) {
      console.error('ProfileCard addFriend:', err?.message || err);
    }
  };

  // Написать
  const handleMessage = () => {
    onOpenChat?.(userId);
    onClose?.();
  };

  // Аватар lightbox
  const handleAvatarClick = () => {
    if (user?.avatar) setLightboxOpen(true);
  };

  const hue = user ? getAvatarHue(user) : 200;
  const onlineStatus = onlineStatusFromStore || user?.status || 'offline';
  const isOnline = onlineStatus === 'online';
  const isBanned = user?.banned;

  // Skeleton
  if (loading) {
    return (
      <div className="pcard pcard--loading" data-testid="profile-card">
        <div className="pcard__banner pcard__banner--skeleton" />
        <div className="pcard__avatar-wrap">
          <div className="pcard__avatar-skeleton" />
        </div>
        <div className="pcard__skeleton-line pcard__skeleton-line--lg" />
        <div className="pcard__skeleton-line pcard__skeleton-line--sm" />
        <div className="pcard__skeleton-line pcard__skeleton-line--md" />
      </div>
    );
  }

  // Ошибка
  if (error) {
    return (
      <div className="pcard pcard--error" data-testid="profile-card">
        <div className="pcard__error-content">
          <Warning size={32} weight="regular" />
          <span>{error === 'not_found' ? 'Пользователь не найден' : 'Не удалось загрузить'}</span>
          {error === 'network' && (
            <button className="pcard__retry-btn" onClick={() => setFetchKey(k => k + 1)}>
              Повторить
            </button>
          )}
        </div>
      </div>
    );
  }

  // Забанен
  if (isBanned) {
    return (
      <div className="pcard pcard--banned" data-testid="profile-card">
        <div className="pcard__error-content">
          <Warning size={32} weight="regular" />
          <span>Пользователь заблокирован</span>
        </div>
      </div>
    );
  }

  if (!user) return null;

  const avatarSrc = user.avatar ? `${API_URL}/uploads/avatars/${user.avatar}` : null;
  const joinDate = user.createdAt ? formatJoinDate(user.createdAt) : '';
  const tag = user.tag?.startsWith('#') ? user.tag : `#${user.tag || '0000'}`;

  return (
    <>
      <motion.div
        className="pcard"
        ref={cardRef}
        onMouseMove={handleMouseMove}
        onMouseLeave={handleMouseLeave}
        data-testid="profile-card"
        variants={containerV}
        initial="hidden"
        animate="visible"
      >
        {/* Banner */}
        <motion.div className="pcard__banner" variants={childV} style={{ '--card-hue': hue }}>
          {avatarSrc ? (
            <img className="pcard__banner-img" src={avatarSrc} alt="" draggable={false} />
          ) : (
            <div className="pcard__banner-gradient" style={{ background: `linear-gradient(135deg, hsl(${hue}, 50%, 25%), hsl(${(hue + 60) % 360}, 40%, 15%))` }} />
          )}
          <div className="pcard__banner-overlay" />
        </motion.div>

        {/* Avatar */}
        <motion.div className="pcard__avatar-wrap" variants={childV}>
          <div
            className={`pcard__avatar-ring ${isOnline ? 'pcard__avatar-ring--online' : ''}`}
            style={{ '--ring-hue': hue }}
            onClick={handleAvatarClick}
            role={user.avatar ? 'button' : undefined}
            tabIndex={user.avatar ? 0 : undefined}
            data-testid="profile-card-avatar"
          >
            <motion.div layoutId={`avatar-${id}`}>
              <Avatar user={user} size={96} />
            </motion.div>
          </div>
        </motion.div>

        {/* Identity */}
        <motion.div className="pcard__identity" variants={childV}>
          <span className="pcard__name" data-testid="profile-card-username">{user.username}</span>
          <span className="pcard__tag">{tag}</span>
        </motion.div>

        {/* Status row */}
        <motion.div className="pcard__status-row" variants={childV}>
          <div className="pcard__status">
            <span className={`pcard__dot pcard__dot--${onlineStatus}`} />
            <span className="pcard__status-text">
              {statusLabels[onlineStatus] || statusLabels.offline}
            </span>
            {!isOnline && user.lastSeenAt && user.showLastSeen !== false && (
              <span className="pcard__lastseen">
                <Clock size={10} /> {formatLastSeen(user.lastSeenAt)}
              </span>
            )}
          </div>
          {mode === 'own' && (
            <button className="pcard__edit-btn" onClick={onEdit} title="Редактировать" data-testid="profile-card-edit-btn">
              <PencilSimple size={14} weight="regular" />
            </button>
          )}
        </motion.div>

        {/* Custom status */}
        {user.customStatus && (
          <motion.div className="pcard__custom" variants={childV}>
            "{user.customStatus}"
          </motion.div>
        )}

        {/* Bio */}
        {user.bio && (
          <motion.p className="pcard__bio" variants={childV}>{user.bio}</motion.p>
        )}

        {/* Placeholder sections */}
        <motion.div className="pcard__placeholders" variants={childV}>
          <div className="pcard__placeholder" data-testid="profile-card-tags-placeholder">
            <span className="pcard__placeholder-label">Теги</span>
            <div className="pcard__placeholder-body">
              <Lock size={13} weight="regular" />
              <span>Скоро</span>
            </div>
          </div>
          <div className="pcard__placeholder" data-testid="profile-card-collection-placeholder">
            <span className="pcard__placeholder-label">Коллекция</span>
            <div className="pcard__placeholder-body">
              <Lock size={13} weight="regular" />
              <span>Скоро</span>
            </div>
          </div>
        </motion.div>

        {/* Join date */}
        {joinDate && (
          <motion.div className="pcard__chip" variants={childV}>
            <Calendar size={12} />
            На blesk {joinDate}
          </motion.div>
        )}

        {/* Actions */}
        <motion.div className="pcard__actions" variants={childV}>
          {mode === 'own' ? (
            <motion.button
              className="pcard__btn pcard__btn--primary"
              onClick={onEdit}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.97 }}
              data-testid="profile-card-action-btn"
            >
              <PencilSimple size={15} /> Редактировать
            </motion.button>
          ) : user.isFriend ? (
            <motion.button
              className="pcard__btn pcard__btn--primary"
              onClick={handleMessage}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.97 }}
              data-testid="profile-card-action-btn"
            >
              <ChatCircle size={15} /> Написать
            </motion.button>
          ) : friendReqSent ? (
            <div className="pcard__btn pcard__btn--sent" data-testid="profile-card-action-btn">
              <Check size={15} /> Запрос отправлен
            </div>
          ) : (
            <motion.button
              className="pcard__btn pcard__btn--outline"
              onClick={handleAddFriend}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.97 }}
              data-testid="profile-card-action-btn"
            >
              <UserPlus size={15} /> Добавить в друзья
            </motion.button>
          )}
        </motion.div>
      </motion.div>

      <AvatarLightbox
        avatarFilename={user.avatar}
        userId={id}
        isOpen={lightboxOpen}
        onClose={() => setLightboxOpen(false)}
      />
    </>
  );
}
