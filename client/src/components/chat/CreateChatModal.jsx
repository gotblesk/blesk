import { useState, useEffect } from 'react';
import Glass from '../ui/Glass';
import API_URL from '../../config';
import './CreateChatModal.css';

export default function CreateChatModal({ onClose, onCreated }) {
  const [friends, setFriends] = useState([]);
  const [filter, setFilter] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const handleKey = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [onClose]);

  // Загружаем список друзей
  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(`${API_URL}/api/friends`, {
          headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
        });
        if (res.ok) setFriends(await res.json());
      } catch {} finally { setLoading(false); }
    })();
  }, []);

  const filtered = friends.filter((f) =>
    f.username.toLowerCase().includes(filter.toLowerCase())
  );

  const handleSelect = async (userId) => {
    try {
      const res = await fetch(`${API_URL}/api/chats`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('token')}`,
        },
        body: JSON.stringify({ participantId: userId }),
      });
      if (res.ok) {
        const data = await res.json();
        onCreated(data.id);
      } else {
        const err = await res.json();
        console.error(err.error);
      }
    } catch (err) {
      console.error('Ошибка создания чата:', err);
    }
  };

  return (
    <div className="create-chat-overlay" onClick={onClose}>
      <Glass
        depth={3}
        radius={20}
        className="create-chat-modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="create-chat-modal__title">Новый чат</div>
        <input
          className="create-chat-modal__input"
          placeholder="Фильтр по имени..."
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          autoFocus
        />
        <div className="create-chat-modal__results">
          {loading && <div className="create-chat-modal__loading">Загрузка...</div>}
          {filtered.map((user) => (
            <div key={user.id} className="create-chat-modal__user" onClick={() => handleSelect(user.id)}>
              <div
                className="create-chat-modal__avatar"
                style={{ background: `linear-gradient(135deg, hsl(${user.hue}, 70%, 50%), hsl(${user.hue + 40}, 70%, 60%))` }}
              />
              <div className="create-chat-modal__name">
                {user.username}
                <span className="create-chat-modal__tag">{user.tag}</span>
                {user.status === 'online' && <span className="create-chat-modal__online" />}
              </div>
            </div>
          ))}
          {!loading && friends.length === 0 && (
            <div className="create-chat-modal__empty">Нет друзей. Добавьте кого-нибудь!</div>
          )}
          {!loading && friends.length > 0 && filtered.length === 0 && (
            <div className="create-chat-modal__empty">Не найдено</div>
          )}
        </div>
      </Glass>
    </div>
  );
}
