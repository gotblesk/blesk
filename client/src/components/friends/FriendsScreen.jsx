import { useState, useEffect, useRef, useCallback } from 'react';
import Glass from '../ui/Glass';
import UserProfileModal from '../ui/UserProfileModal';
import API_URL from '../../config';
import { getAvatarHue, getAvatarColor } from '../../utils/avatar';
import './FriendsScreen.css';

// Хук для дебаунса поискового запроса
function useDebounce(value, delay) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

// Аватар на основе hue пользователя
function UserAvatar({ username, hue, online }) {
  const letter = username ? username[0].toUpperCase() : '?';
  const computedHue = getAvatarHue({ username, hue });
  const bg = getAvatarColor(computedHue);

  return (
    <div className="friends-avatar" style={{ background: bg }}>
      <span className="friends-avatar__letter">{letter}</span>
      {online && <span className="friends-avatar__online" />}
    </div>
  );
}

export default function FriendsScreen({ onBack, onOpenChat }) {
  const [tab, setTab] = useState('friends');
  const [friends, setFriends] = useState([]);
  const [pending, setPending] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [sentRequests, setSentRequests] = useState(new Set());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  // id пользователя для модалки профиля
  const [profileUserId, setProfileUserId] = useState(null);
  const indicatorRef = useRef(null);
  const tabsRef = useRef(null);

  const debouncedQuery = useDebounce(searchQuery, 300);

  // Всегда свежий токен (не кешировать в замыкании)
  const getHeaders = () => ({
    'Content-Type': 'application/json',
    Authorization: `Bearer ${localStorage.getItem('token')}`,
  });

  // Позиция индикатора табов
  useEffect(() => {
    if (!tabsRef.current || !indicatorRef.current) return;
    const tabs = tabsRef.current.querySelectorAll('.friends-tab');
    const idx = tab === 'friends' ? 0 : tab === 'requests' ? 1 : 2;
    const activeTab = tabs[idx];
    if (activeTab) {
      indicatorRef.current.style.left = `${activeTab.offsetLeft}px`;
      indicatorRef.current.style.width = `${activeTab.offsetWidth}px`;
    }
  }, [tab]);

  // Загрузка друзей
  const loadFriends = useCallback(async () => {
    try {
      const res = await fetch(`${API_URL}/api/friends`, { headers: getHeaders() });
      if (res.ok) {
        const data = await res.json();
        setFriends(data);
      }
    } catch {
      setError('Не удалось загрузить друзей');
    }
  }, []);

  // Загрузка входящих заявок
  const loadPending = useCallback(async () => {
    try {
      const res = await fetch(`${API_URL}/api/friends/requests/pending`, { headers: getHeaders() });
      if (res.ok) {
        const data = await res.json();
        setPending(data);
      }
    } catch {
      setError('Не удалось загрузить заявки');
    }
  }, []);

  // При смене таба — загружаем данные
  useEffect(() => {
    let isCancelled = false;

    setError('');
    if (tab === 'friends') {
      loadFriends().catch(() => { if (!isCancelled) setError('Не удалось загрузить друзей'); });
    }
    if (tab === 'requests') {
      loadPending().catch(() => { if (!isCancelled) setError('Не удалось загрузить заявки'); });
    }

    return () => { isCancelled = true; };
  }, [tab]);

  // Загружаем заявки при монтировании (для badge)
  useEffect(() => {
    let isCancelled = false;

    loadPending().catch(() => { if (!isCancelled) setError('Не удалось загрузить заявки'); });
    loadFriends().catch(() => { if (!isCancelled) setError('Не удалось загрузить друзей'); });

    return () => { isCancelled = true; };
  }, []);

  // Поиск пользователей
  useEffect(() => {
    if (tab !== 'search') return;
    if (debouncedQuery.length < 2) {
      setSearchResults([]);
      return;
    }

    let cancelled = false;
    setLoading(true);

    fetch(`${API_URL}/api/users/search?q=${encodeURIComponent(debouncedQuery)}`, { headers: getHeaders() })
      .then((res) => res.json())
      .then((data) => {
        if (!cancelled) setSearchResults(data);
      })
      .catch(() => {
        if (!cancelled) setError('Ошибка поиска');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [debouncedQuery, tab]);

  // Отправить заявку в друзья
  const sendRequest = async (userId) => {
    try {
      const res = await fetch(`${API_URL}/api/friends/request`, {
        method: 'POST',
        headers: getHeaders(),
        body: JSON.stringify({ userId }),
      });
      if (res.ok) {
        setSentRequests((prev) => new Set(prev).add(userId));
      } else {
        const data = await res.json();
        setError(data.error || 'Не удалось отправить заявку');
      }
    } catch {
      setError('Ошибка соединения');
    }
  };

  // Принять заявку
  const acceptRequest = async (requestId) => {
    try {
      const res = await fetch(`${API_URL}/api/friends/requests/${requestId}/accept`, {
        method: 'POST',
        headers: getHeaders(),
      });
      if (res.ok) {
        setPending((prev) => prev.filter((r) => r.id !== requestId));
        loadFriends();
      }
    } catch {
      setError('Не удалось принять заявку');
    }
  };

  // Отклонить заявку
  const declineRequest = async (requestId) => {
    try {
      const res = await fetch(`${API_URL}/api/friends/requests/${requestId}/decline`, {
        method: 'POST',
        headers: getHeaders(),
      });
      if (res.ok) {
        setPending((prev) => prev.filter((r) => r.id !== requestId));
      }
    } catch {
      setError('Не удалось отклонить заявку');
    }
  };

  // Проверка — уже в друзьях?
  const isFriend = (userId) => friends.some((f) => f.id === userId);

  return (
    <div className="friends-screen section-enter">
      <div className="friends-screen__header">
        <button className="friends-screen__back" onClick={onBack}>
          ← Назад
        </button>
        <div className="friends-screen__title">Друзья</div>
      </div>

      {/* Табы */}
      <div className="friends-tabs" ref={tabsRef}>
        <div className="friends-tab-indicator" ref={indicatorRef} />
        <button
          className={`friends-tab ${tab === 'friends' ? 'friends-tab--active' : ''}`}
          onClick={() => setTab('friends')}
        >
          Друзья
        </button>
        <button
          className={`friends-tab ${tab === 'requests' ? 'friends-tab--active' : ''}`}
          onClick={() => setTab('requests')}
        >
          Заявки
          {pending.length > 0 && (
            <span className="friends-tab__badge">{pending.length}</span>
          )}
        </button>
        <button
          className={`friends-tab ${tab === 'search' ? 'friends-tab--active' : ''}`}
          onClick={() => setTab('search')}
        >
          Поиск
        </button>
      </div>

      {/* Ошибка */}
      {error && (
        <div className="friends-error">
          <span className="friends-error__icon">!</span>
          {error}
        </div>
      )}

      {/* Контент */}
      <div className="friends-content">
        {/* Список друзей */}
        {tab === 'friends' && (
          <div className="friends-list">
            {friends.length === 0 ? (
              <div className="friends-empty">
                <div className="friends-empty__icon">👥</div>
                <div className="friends-empty__text">Пока нет друзей</div>
                <div className="friends-empty__hint">
                  Найди людей во вкладке «Поиск»
                </div>
              </div>
            ) : (
              friends.map((friend, i) => (
                <Glass
                  key={friend.id}
                  depth={1}
                  radius={14}
                  hover
                  className="friends-card"
                  style={{ animationDelay: `${i * 0.04}s`, cursor: 'pointer' }}
                  onClick={() => setProfileUserId(friend.id)}
                >
                  <UserAvatar
                    username={friend.username}
                    hue={friend.hue}
                    online={friend.status === 'online'}
                  />
                  <div className="friends-card__info">
                    <div className="friends-card__name">
                      {friend.username}
                      <span className="friends-card__tag">{friend.tag?.startsWith('#') ? friend.tag : `#${friend.tag}`}</span>
                    </div>
                    <div className={`friends-card__status friends-card__status--${friend.status === 'online' ? 'online' : 'offline'}`}>
                      {friend.status === 'online' ? 'В сети' : 'Не в сети'}
                    </div>
                  </div>
                </Glass>
              ))
            )}
          </div>
        )}

        {/* Входящие заявки */}
        {tab === 'requests' && (
          <div className="friends-list">
            {pending.length === 0 ? (
              <div className="friends-empty">
                <div className="friends-empty__icon">📬</div>
                <div className="friends-empty__text">Нет входящих заявок</div>
                <div className="friends-empty__hint">
                  Когда кто-то добавит вас — заявка появится здесь
                </div>
              </div>
            ) : (
              pending.map((req, i) => (
                <Glass
                  key={req.id}
                  depth={1}
                  radius={14}
                  className="friends-card"
                  style={{ animationDelay: `${i * 0.04}s` }}
                >
                  {/* Клик по аватару/имени открывает профиль отправителя */}
                  <div
                    style={{ display: 'contents', cursor: 'pointer' }}
                    onClick={() => setProfileUserId(req.sender.id)}
                  >
                    <UserAvatar
                      username={req.sender.username}
                      hue={req.sender.hue}
                    />
                    <div className="friends-card__info">
                      <div className="friends-card__name">
                        {req.sender.username}
                        <span className="friends-card__tag">{req.sender.tag?.startsWith('#') ? req.sender.tag : `#${req.sender.tag}`}</span>
                      </div>
                      <div className="friends-card__hint">Хочет добавить вас в друзья</div>
                    </div>
                  </div>
                  <div className="friends-card__actions">
                    <button
                      className="friends-action friends-action--accept"
                      onClick={() => acceptRequest(req.id)}
                      title="Принять"
                    >
                      ✓
                    </button>
                    <button
                      className="friends-action friends-action--decline"
                      onClick={() => declineRequest(req.id)}
                      title="Отклонить"
                    >
                      ✕
                    </button>
                  </div>
                </Glass>
              ))
            )}
          </div>
        )}

        {/* Поиск */}
        {tab === 'search' && (
          <div className="friends-search">
            <div className="friends-search__wrap">
              <input
                className="friends-search__input"
                type="text"
                placeholder="Введите имя пользователя..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                autoFocus
                spellCheck="false"
              />
              {loading && <div className="friends-search__spinner" />}
            </div>

            <div className="friends-list">
              {searchQuery.length > 0 && searchQuery.length < 2 && (
                <div className="friends-empty">
                  <div className="friends-empty__hint">Минимум 2 символа для поиска</div>
                </div>
              )}

              {debouncedQuery.length >= 2 && !loading && searchResults.length === 0 && (
                <div className="friends-empty">
                  <div className="friends-empty__icon">🔍</div>
                  <div className="friends-empty__text">Никого не найдено</div>
                  <div className="friends-empty__hint">
                    Попробуйте другой запрос
                  </div>
                </div>
              )}

              {searchResults.map((user, i) => (
                <Glass
                  key={user.id}
                  depth={1}
                  radius={14}
                  className="friends-card"
                  style={{ animationDelay: `${i * 0.04}s` }}
                >
                  {/* Клик по аватару/имени открывает профиль */}
                  <div
                    style={{ display: 'contents', cursor: 'pointer' }}
                    onClick={() => setProfileUserId(user.id)}
                  >
                    <UserAvatar
                      username={user.username}
                      hue={user.hue}
                      online={user.status === 'online'}
                    />
                    <div className="friends-card__info">
                      <div className="friends-card__name">
                        {user.username}
                        <span className="friends-card__tag">{user.tag?.startsWith('#') ? user.tag : `#${user.tag}`}</span>
                      </div>
                    </div>
                  </div>
                  <div className="friends-card__actions">
                    {isFriend(user.id) ? (
                      <span className="friends-badge friends-badge--friend">Уже в друзьях</span>
                    ) : sentRequests.has(user.id) ? (
                      <span className="friends-badge friends-badge--sent">Отправлено</span>
                    ) : (
                      <button
                        className="friends-action friends-action--add"
                        onClick={() => sendRequest(user.id)}
                      >
                        Добавить
                      </button>
                    )}
                  </div>
                </Glass>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Модалка профиля пользователя */}
      <UserProfileModal
        userId={profileUserId}
        open={!!profileUserId}
        onClose={() => setProfileUserId(null)}
        onAddFriend={(id) => sendRequest(id)}
        onOpenChat={(chatId) => { setProfileUserId(null); onOpenChat?.(chatId, null); }}
      />
    </div>
  );
}
