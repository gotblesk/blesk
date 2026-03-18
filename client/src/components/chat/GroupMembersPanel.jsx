import { useState, useEffect } from 'react';
import { Crown } from 'lucide-react';
import Glass from '../ui/Glass';
import UserProfileModal from '../ui/UserProfileModal';
import API_URL from '../../config';
import { getCurrentUserId } from '../../utils/auth';
import { getAvatarHue, getAvatarGradient } from '../../utils/avatar';
import './GroupMembersPanel.css';

export default function GroupMembersPanel({ chatId, isOwner, onClose, onAddMember, socketRef }) {
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  // id пользователя для модалки профиля
  const [profileUserId, setProfileUserId] = useState(null);

  const loadMembers = async () => {
    try {
      const res = await fetch(`${API_URL}/api/chats/${chatId}/members`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
      });
      if (res.ok) setMembers(await res.json());
    } catch {} finally { setLoading(false); }
  };

  useEffect(() => {
    loadMembers();
  }, [chatId]);

  const handleRemove = async (userId) => {
    try {
      const res = await fetch(`${API_URL}/api/chats/${chatId}/members/${userId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
      });
      if (res.ok) {
        setMembers((prev) => prev.filter((m) => m.userId !== userId));
      }
    } catch {}
  };

  const handleLeave = async () => {
    const userId = getCurrentUserId();
    if (!userId) return;

    try {
      const res = await fetch(`${API_URL}/api/chats/${chatId}/members/${userId}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
      });
      if (res.ok) onClose?.();
    } catch {}
  };

  return (
    <div className="group-members-overlay" onClick={onClose}>
      <Glass
        depth={3}
        radius={20}
        className="group-members-panel"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="group-members-panel__header">
          <span className="group-members-panel__title">Участники</span>
          <button className="group-members-panel__close" onClick={onClose}>&times;</button>
        </div>

        {loading ? (
          <div className="group-members-panel__loading">Загрузка...</div>
        ) : (
          <div className="group-members-panel__list">
            {members.map((m) => {
              const hue = getAvatarHue(m.user);
              const isMe = getCurrentUserId() === m.userId;

              return (
                <div key={m.userId} className="group-members-panel__member">
                  {/* Клик по аватару/имени открывает профиль участника */}
                  <div
                    style={{ display: 'contents', cursor: 'pointer' }}
                    onClick={() => setProfileUserId(m.userId)}
                  >
                    <div
                      className="group-members-panel__avatar"
                      style={{ background: getAvatarGradient(hue) }}
                    >
                      {(m.user?.username || '?')[0].toUpperCase()}
                    </div>
                    <div className="group-members-panel__info">
                      <span className="group-members-panel__name">
                        {m.user?.username || 'Неизвестный'}
                        {m.role === 'owner' && <span className="group-members-panel__crown"><Crown size={12} strokeWidth={1.5} /></span>}
                        {isMe && <span className="group-members-panel__you"> (вы)</span>}
                      </span>
                      <span className="group-members-panel__role">
                        {m.role === 'owner' ? 'Владелец' : m.role === 'admin' ? 'Админ' : 'Участник'}
                      </span>
                    </div>
                  </div>
                  {isOwner && m.role !== 'owner' && !isMe && (
                    <button
                      className="group-members-panel__remove"
                      onClick={() => handleRemove(m.userId)}
                    >
                      Удалить
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        )}

        {!isOwner && (
          <button className="group-members-panel__leave" onClick={handleLeave}>
            Покинуть группу
          </button>
        )}
      </Glass>

      {/* Модалка профиля участника */}
      <UserProfileModal
        userId={profileUserId}
        open={!!profileUserId}
        onClose={() => setProfileUserId(null)}
      />
    </div>
  );
}
