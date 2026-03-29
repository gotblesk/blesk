import { useState, useEffect } from 'react';
import Glass from '../ui/Glass';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import './CreateChatModal.css';

export default function CreateChatModal({ onClose, onCreated }) {
  const [friends, setFriends] = useState([]);
  const [filter, setFilter] = useState('');
  const [loading, setLoading] = useState(true);
  const [mode, setMode] = useState('personal'); // 'personal' | 'group'
  const [selected, setSelected] = useState([]);  // id выбранных друзей
  const [groupName, setGroupName] = useState('');
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');

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
          headers: { ...getAuthHeaders() }, credentials: 'include',
        });
        if (res.ok) setFriends(await res.json());
        else setError('Не удалось загрузить друзей');
      } catch {
        setError('Нет соединения с сервером');
      } finally { setLoading(false); }
    })();
  }, []);

  const filtered = friends.filter((f) =>
    f.username.toLowerCase().includes(filter.toLowerCase())
  );

  // Сброс выбранных при смене режима
  const handleModeChange = (newMode) => {
    setMode(newMode);
    setSelected([]);
    setGroupName('');
  };

  // Переключение выбора друга (групповой режим)
  const toggleSelect = (userId) => {
    setSelected((prev) =>
      prev.includes(userId)
        ? prev.filter((id) => id !== userId)
        : [...prev, userId]
    );
  };

  // Создание личного чата (1-на-1)
  const handleSelectPersonal = async (userId) => {
    try {
      const res = await fetch(`${API_URL}/api/chats`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...getAuthHeaders(),
        },
        credentials: 'include',
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

  // Создание группового чата
  const handleCreateGroup = async () => {
    if (!groupName.trim() || selected.length === 0) return;
    setCreating(true);
    try {
      const res = await fetch(`${API_URL}/api/chats`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...getAuthHeaders(),
        },
        credentials: 'include',
        body: JSON.stringify({
          participantIds: selected,
          name: groupName.trim(),
        }),
      });
      if (res.ok) {
        const data = await res.json();
        onCreated(data.id);
      } else {
        const err = await res.json();
        console.error(err.error);
      }
    } catch (err) {
      console.error('Ошибка создания группы:', err);
    } finally {
      setCreating(false);
    }
  };

  // Данные выбранных друзей для чипов
  const selectedFriends = friends.filter((f) => selected.includes(f.id));
  const canCreateGroup = groupName.trim().length > 0 && selected.length > 0;

  return (
    <div className="create-chat-overlay" onClick={onClose}>
      <Glass
        depth={3}
        radius={20}
        className="create-chat-modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="create-chat-modal__title">Новый чат</div>

        {/* Переключатель режима */}
        <div className="create-chat-modal__mode-toggle">
          <button
            className={`create-chat-modal__mode-pill ${mode === 'personal' ? 'create-chat-modal__mode-pill--active' : ''}`}
            onClick={() => handleModeChange('personal')}
          >
            Личный
          </button>
          <button
            className={`create-chat-modal__mode-pill ${mode === 'group' ? 'create-chat-modal__mode-pill--active' : ''}`}
            onClick={() => handleModeChange('group')}
          >
            Групповой
          </button>
        </div>

        {/* Название группы (только в групповом режиме) */}
        {mode === 'group' && (
          <input
            className="create-chat-modal__input"
            placeholder="Название группы..."
            value={groupName}
            onChange={(e) => setGroupName(e.target.value.slice(0, 50))}
            maxLength={50}
          />
        )}

        {/* Чипы выбранных участников (групповой режим) */}
        {mode === 'group' && selectedFriends.length > 0 && (
          <div className="create-chat-modal__chips">
            {selectedFriends.map((f) => (
              <div key={f.id} className="create-chat-modal__chip" onClick={() => toggleSelect(f.id)}>
                <span>{f.username}</span>
                <span className="create-chat-modal__chip-remove">&times;</span>
              </div>
            ))}
          </div>
        )}

        {/* Поиск */}
        <input
          className="create-chat-modal__input"
          placeholder="Фильтр по имени..."
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          autoFocus
        />

        {/* Список друзей */}
        <div className="create-chat-modal__results">
          {loading && <div className="create-chat-modal__loading">Загрузка...</div>}
          {error && <div className="create-chat-modal__empty" style={{ color: 'var(--danger)' }}>{error}</div>}
          {filtered.map((user) => (
            <div
              key={user.id}
              className={`create-chat-modal__user ${mode === 'group' && selected.includes(user.id) ? 'create-chat-modal__user--selected' : ''}`}
              onClick={() => mode === 'personal' ? handleSelectPersonal(user.id) : toggleSelect(user.id)}
            >
              {mode === 'group' && (
                <div className={`create-chat-modal__checkbox ${selected.includes(user.id) ? 'create-chat-modal__checkbox--checked' : ''}`}>
                  {selected.includes(user.id) && <span>&#10003;</span>}
                </div>
              )}
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

        {/* Кнопка создания группы */}
        {mode === 'group' && (
          <button
            className="create-chat-modal__create-btn"
            disabled={!canCreateGroup || creating}
            onClick={handleCreateGroup}
          >
            {creating ? 'Создание...' : `Создать группу${selected.length > 0 ? ` (${selected.length})` : ''}`}
          </button>
        )}
      </Glass>
    </div>
  );
}
