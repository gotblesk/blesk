import { useState, useEffect, useRef } from 'react';
import Glass from '../ui/Glass';
import './CreateChatModal.css';

const API = 'http://localhost:3000';

export default function CreateChatModal({ onClose, onCreated }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const inputRef = useRef(null);
  const debounceRef = useRef(null);

  useEffect(() => {
    inputRef.current?.focus();
    const handleKey = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [onClose]);

  useEffect(() => {
    clearTimeout(debounceRef.current);
    if (query.length < 2) { setResults([]); return; }

    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await fetch(`${API}/api/users/search?q=${encodeURIComponent(query)}`, {
          headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
        });
        if (res.ok) setResults(await res.json());
      } catch {} finally { setLoading(false); }
    }, 300);
  }, [query]);

  const handleSelect = async (userId) => {
    try {
      const res = await fetch(`${API}/api/chats`, {
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
          ref={inputRef}
          className="create-chat-modal__input"
          placeholder="Поиск по username..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <div className="create-chat-modal__results">
          {loading && <div className="create-chat-modal__loading">Поиск...</div>}
          {results.map((user) => (
            <div key={user.id} className="create-chat-modal__user" onClick={() => handleSelect(user.id)}>
              <div
                className="create-chat-modal__avatar"
                style={{ background: `linear-gradient(135deg, hsl(${user.hue}, 70%, 50%), hsl(${user.hue + 40}, 70%, 60%))` }}
              />
              <div className="create-chat-modal__name">
                {user.username}<span className="create-chat-modal__tag">{user.tag}</span>
              </div>
            </div>
          ))}
          {query.length >= 2 && !loading && results.length === 0 && (
            <div className="create-chat-modal__empty">Не найдено</div>
          )}
        </div>
      </Glass>
    </div>
  );
}
