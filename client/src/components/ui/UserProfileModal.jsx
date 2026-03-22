import { useState, useEffect, useCallback } from 'react';
import Glass from './Glass';
import Avatar from './Avatar';
import API_URL from '../../config';
import './UserProfileModal.css';

const MONTHS_RU = [
  'январь', 'февраль', 'март', 'апрель', 'май', 'июнь',
  'июль', 'август', 'сентябрь', 'октябрь', 'ноябрь', 'декабрь',
];

function formatDate(iso) {
  const d = new Date(iso);
  return `${MONTHS_RU[d.getMonth()]} ${d.getFullYear()}`;
}

const statusColors = {
  online: '#4ade80',
  dnd: '#ef4444',
  invisible: '#666',
  offline: '#666',
};

export default function UserProfileModal({
  userId,
  open,
  onClose,
  onAddFriend,
  onOpenChat,
}) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(false);
  const [friendReqSent, setFriendReqSent] = useState(false);

  useEffect(() => {
    if (!open || !userId) return;
    setLoading(true);
    setUser(null);
    setFriendReqSent(false);

    const token = localStorage.getItem('token');
    fetch(`${API_URL}/api/users/${userId}`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((r) => r.json())
      .then((data) => setUser(data))
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, [open, userId]);

  // Escape для закрытия
  useEffect(() => {
    if (!open) return;
    const handleEsc = (e) => { if (e.key === 'Escape') onClose?.(); };
    window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [open, onClose]);

  const handleBackdrop = useCallback(
    (e) => {
      if (e.target === e.currentTarget) onClose?.();
    },
    [onClose],
  );

  const handleAddFriend = async () => {
    try {
      await onAddFriend?.(userId);
      setFriendReqSent(true);
    } catch {
      /* ignore */
    }
  };

  if (!open) return null;

  return (
    <div className="upm-overlay" onClick={handleBackdrop}>
      <Glass depth={3} radius={24} className="upm-card">
        <button className="upm-close" onClick={onClose}>
          &times;
        </button>

        {loading && (
          <div className="upm-loading">
            <span className="upm-spinner" />
          </div>
        )}

        {!loading && user && (
          <>
            <Avatar user={user} size="xl" className="upm-avatar" />

            <div className="upm-name">
              {user.username}
              <span className="upm-tag">{user.tag?.startsWith('#') ? user.tag : `#${user.tag}`}</span>
            </div>

            <div className="upm-status-row">
              <span
                className="upm-dot"
                style={{
                  background: statusColors[user.status] || statusColors.offline,
                }}
              />
              {user.customStatus && (
                <span className="upm-custom-status">{user.customStatus}</span>
              )}
            </div>

            {user.bio && <p className="upm-bio">{user.bio}</p>}

            <span className="upm-date">
              На blesk с {formatDate(user.createdAt)}
            </span>

            <div className="upm-actions">
              {user.isFriend ? (
                <button
                  className="upm-btn upm-btn--primary"
                  onClick={async () => {
                    try {
                      const token = localStorage.getItem('token');
                      const res = await fetch(`${API_URL}/api/chats`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
                        body: JSON.stringify({ participantId: userId }),
                      });
                      const data = await res.json();
                      if (data.id) {
                        onOpenChat?.(data.id, null);
                        onClose?.();
                      }
                    } catch {}
                  }}
                >
                  Написать
                </button>
              ) : (
                <button
                  className="upm-btn upm-btn--outline"
                  onClick={handleAddFriend}
                  disabled={friendReqSent}
                >
                  {friendReqSent ? 'Запрос отправлен' : 'Добавить в друзья'}
                </button>
              )}
            </div>
          </>
        )}
      </Glass>
    </div>
  );
}
