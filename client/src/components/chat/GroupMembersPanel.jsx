import { useState, useEffect } from 'react';
import Glass from '../ui/Glass';
import API_URL from '../../config';
import './GroupMembersPanel.css';

export default function GroupMembersPanel({ chatId, isOwner, onClose, onAddMember, socketRef }) {
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);

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
    let userId;
    try {
      userId = JSON.parse(atob(localStorage.getItem('token').split('.')[1])).userId;
    } catch { return; }

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
              const hue = m.user?.hue ?? 0;
              const isMe = (() => {
                try {
                  return JSON.parse(atob(localStorage.getItem('token').split('.')[1])).userId === m.userId;
                } catch { return false; }
              })();

              return (
                <div key={m.userId} className="group-members-panel__member">
                  <div
                    className="group-members-panel__avatar"
                    style={{ background: `linear-gradient(135deg, hsl(${hue}, 70%, 50%), hsl(${hue + 40}, 70%, 60%))` }}
                  >
                    {(m.user?.username || '?')[0].toUpperCase()}
                  </div>
                  <div className="group-members-panel__info">
                    <span className="group-members-panel__name">
                      {m.user?.username || 'Неизвестный'}
                      {m.role === 'owner' && <span className="group-members-panel__crown">👑</span>}
                      {isMe && <span className="group-members-panel__you"> (вы)</span>}
                    </span>
                    <span className="group-members-panel__role">
                      {m.role === 'owner' ? 'Владелец' : m.role === 'admin' ? 'Админ' : 'Участник'}
                    </span>
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
    </div>
  );
}
