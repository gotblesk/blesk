import { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { UsersThree, Tray, MagnifyingGlass, Check, X, UserPlus, ChatCircle, Phone, Trash, ProhibitInset, MinusCircle } from '@phosphor-icons/react';
import Avatar from '../ui/Avatar';
import ProfilePopover from '../profile/ProfilePopover';
import { FriendListSkeleton } from '../ui/GlassSkeleton';
import EmptyState from '../ui/EmptyState';
import ConfirmDialog from '../ui/ConfirmDialog';
import { useCallStore } from '../../store/callStore';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import './FriendsScreen.css';

function useDebounce(value, delay) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

const TABS = [
  { id: 'friends', label: 'Друзья' },
  { id: 'requests', label: 'Заявки' },
  { id: 'search', label: 'Найти друзей' },
  { id: 'blocked', label: 'Заблокированные' },
];

const tabV = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: { duration: 0.15 } },
  exit: { opacity: 0, transition: { duration: 0.08 } },
};

const itemV = {
  hidden: { opacity: 0, y: 8 },
  visible: (i) => ({ opacity: 1, y: 0, transition: { delay: i * 0.04, duration: 0.25, ease: [0.16, 1, 0.3, 1] } }),
};

function formatRelativeTime(dateStr) {
  if (!dateStr) return '';
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'только что';
  if (mins < 60) return `${mins} мин назад`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} ч назад`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} д назад`;
  return `${Math.floor(days / 7)} нед назад`;
}

function getStatusLabel(status) {
  switch (status) {
    case 'online': return 'В сети';
    case 'dnd': return 'Не беспокоить';
    case 'invisible': return 'Невидимка';
    default: return 'Не в сети';
  }
}

function getStatusClass(status) {
  switch (status) {
    case 'online': return 'fr__item-status--on';
    case 'dnd': return 'fr__item-status--dnd';
    case 'invisible': return 'fr__item-status--invisible';
    default: return 'fr__item-status--offline';
  }
}

function sortFriends(friends) {
  return [...friends].sort((a, b) => {
    const aOnline = a.status === 'online' || a.status === 'dnd' ? 0 : 1;
    const bOnline = b.status === 'online' || b.status === 'dnd' ? 0 : 1;
    if (aOnline !== bOnline) return aOnline - bOnline;
    return (a.username || '').localeCompare(b.username || '', 'ru');
  });
}

export default function FriendsScreen({ onBack, onOpenChat, socketRef }) {
  const [tab, setTab] = useState('friends');

  const handleTabKeyDown = useCallback((e) => {
    const tabEls = [...e.currentTarget.querySelectorAll('[role="tab"]')];
    const current = tabEls.indexOf(e.target);
    if (current === -1) return;

    let next;
    if (e.key === 'ArrowRight') {
      next = (current + 1) % tabEls.length;
    } else if (e.key === 'ArrowLeft') {
      next = (current - 1 + tabEls.length) % tabEls.length;
    } else if (e.key === 'Home') {
      next = 0;
    } else if (e.key === 'End') {
      next = tabEls.length - 1;
    } else return;

    e.preventDefault();
    tabEls[next].focus();
    tabEls[next].click();
  }, []);

  const [friends, setFriends] = useState([]);
  const [pending, setPending] = useState([]);
  const [blocked, setBlocked] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [sentRequests, setSentRequests] = useState(new Set());
  const [loading, setLoading] = useState(false);
  const [friendsLoading, setFriendsLoading] = useState(true);
  const [error, setError] = useState('');
  const [profilePopover, setProfilePopover] = useState({ open: false, userId: null, anchorRef: null });
  const [filter, setFilter] = useState('all');
  const [removeConfirm, setRemoveConfirm] = useState(null);

  const debouncedQuery = useDebounce(searchQuery, 300);

  const getHeaders = () => ({
    'Content-Type': 'application/json',
    ...getAuthHeaders(),
  });

  const loadFriends = useCallback(async () => {
    try {
      const res = await fetch(`${API_URL}/api/friends`, { headers: getHeaders(), credentials: 'include' });
      if (res.ok) setFriends(await res.json());
    } catch { setError('Не удалось загрузить друзей'); }
    finally { setFriendsLoading(false); }
  }, []);

  const loadPending = useCallback(async () => {
    try {
      const res = await fetch(`${API_URL}/api/friends/requests/pending`, { headers: getHeaders(), credentials: 'include' });
      if (res.ok) setPending(await res.json());
    } catch { setError('Не удалось загрузить заявки'); }
  }, []);

  const loadBlocked = useCallback(async () => {
    try {
      const res = await fetch(`${API_URL}/api/users/blocked`, { headers: getHeaders(), credentials: 'include' });
      if (res.ok) {
        const data = await res.json();
        setBlocked(Array.isArray(data) ? data : []);
      }
    } catch { /* тихий fallback */ }
  }, []);

  useEffect(() => { loadFriends(); loadPending(); }, [loadFriends, loadPending]);

  useEffect(() => {
    if (tab === 'blocked') loadBlocked();
  }, [tab, loadBlocked]);

  // Socket listeners for real-time friend updates
  useEffect(() => {
    const socket = socketRef?.current;
    if (!socket) return;

    const handleFriendRequest = () => {
      loadPending();
    };

    const handleFriendAccepted = () => {
      loadFriends();
      loadPending();
    };

    const handleFriendRemoved = (data) => {
      if (data?.userId) {
        setFriends(prev => prev.filter(f => f.id !== data.userId));
      } else {
        loadFriends();
      }
    };

    socket.on('friend:request', handleFriendRequest);
    socket.on('friend:accepted', handleFriendAccepted);
    socket.on('friend:removed', handleFriendRemoved);

    return () => {
      socket.off('friend:request', handleFriendRequest);
      socket.off('friend:accepted', handleFriendAccepted);
      socket.off('friend:removed', handleFriendRemoved);
    };
  }, [socketRef, loadFriends, loadPending]);

  useEffect(() => {
    if (tab !== 'search' || debouncedQuery.length < 2) { setSearchResults([]); return; }
    let cancelled = false;
    setLoading(true);
    fetch(`${API_URL}/api/users/search?q=${encodeURIComponent(debouncedQuery)}`, { headers: getHeaders(), credentials: 'include' })
      .then(r => r.json())
      .then(data => { if (!cancelled) setSearchResults(Array.isArray(data) ? data : []); })
      .catch(() => { if (!cancelled) setError('Ошибка поиска'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [debouncedQuery, tab]);

  const sendRequest = async (userId) => {
    try {
      const res = await fetch(`${API_URL}/api/friends/request`, { method: 'POST', headers: getHeaders(), credentials: 'include', body: JSON.stringify({ userId }) });
      if (res.ok) setSentRequests(prev => new Set(prev).add(userId));
      else { const d = await res.json(); setError(d.error || 'Ошибка'); }
    } catch { setError('Ошибка соединения'); }
  };

  const acceptRequest = async (id) => {
    try {
      const res = await fetch(`${API_URL}/api/friends/requests/${id}/accept`, { method: 'POST', headers: getHeaders(), credentials: 'include' });
      if (res.ok) { setPending(p => p.filter(r => r.id !== id)); loadFriends(); }
    } catch { setError('Ошибка'); }
  };

  const declineRequest = async (id) => {
    try {
      const res = await fetch(`${API_URL}/api/friends/requests/${id}/decline`, { method: 'POST', headers: getHeaders(), credentials: 'include' });
      if (res.ok) setPending(p => p.filter(r => r.id !== id));
    } catch { setError('Ошибка'); }
  };

  const removeFriend = async (friendId) => {
    try {
      const res = await fetch(`${API_URL}/api/friends/${friendId}`, { method: 'DELETE', headers: getHeaders(), credentials: 'include' });
      if (res.ok) setFriends(prev => prev.filter(f => f.id !== friendId));
      else setError('Не удалось удалить из друзей');
    } catch { setError('Ошибка соединения'); }
    setRemoveConfirm(null);
  };

  const unblockUser = async (userId) => {
    try {
      const res = await fetch(`${API_URL}/api/friends/${userId}/block`, { method: 'DELETE', headers: getHeaders(), credentials: 'include' });
      if (res.ok) setBlocked(prev => prev.filter(u => u.id !== userId));
      else setError('Не удалось разблокировать');
    } catch { setError('Ошибка соединения'); }
  };

  const handleCall = (friendId) => {
    const socket = socketRef?.current;
    if (!socket) return;
    useCallStore.getState().initiateCall(friendId);
    socket.emit('call:initiate', { chatId: friendId, type: 'personal' });
  };

  const isFriend = (userId) => friends.some(f => f.id === userId);

  const onlineFriends = friends.filter(f => f.status === 'online' || f.status === 'dnd');
  const sortedFriends = sortFriends(friends);
  const filteredFriends = filter === 'online'
    ? sortedFriends.filter(f => f.status === 'online' || f.status === 'dnd')
    : sortedFriends;

  return (
    <div className="fr">
      {/* Header */}
      <div className="fr__header">
        <div className="fr__title">Друзья</div>
        <div className="fr__count">{friends.length}</div>
      </div>

      {/* Tabs */}
      <div className="fr__tabs" role="tablist" onKeyDown={handleTabKeyDown}>
        {TABS.map(t => (
          <button key={t.id} className={`fr__tab ${tab === t.id ? 'fr__tab--active' : ''}`} onClick={() => setTab(t.id)} role="tab" aria-selected={tab === t.id} tabIndex={tab === t.id ? 0 : -1}>
            {t.label}
            {t.id === 'requests' && pending.length > 0 && <span className="fr__badge">{pending.length}</span>}
            {t.id === 'blocked' && blocked.length > 0 && <span className="fr__badge fr__badge--muted">{blocked.length}</span>}
            {tab === t.id && <motion.div className="fr__tab-pill" layoutId="frTabPill" transition={{ type: 'spring', stiffness: 400, damping: 30 }} />}
          </button>
        ))}
      </div>

      {/* Error */}
      <AnimatePresence>
        {error && (
          <motion.div className="fr__error" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }}>
            {error}
            <button className="fr__error-close" onClick={() => setError('')} aria-label="Закрыть ошибку"><X size={14} weight="regular" /></button>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Content */}
      <div className="fr__body">
        <AnimatePresence mode="wait">
          {tab === 'friends' && (
            <motion.div key="friends" variants={tabV} initial="initial" animate="animate" exit="exit">
              {/* Skeleton при загрузке */}
              {friendsLoading && friends.length === 0 && <FriendListSkeleton count={5} />}

              {/* Online friends horizontal scroll */}
              {!friendsLoading && onlineFriends.length > 0 && filter === 'all' && (
                <div className="fr__online-section">
                  <div className="fr__section-label">Сейчас онлайн — {onlineFriends.length}</div>
                  <div className="fr__online-scroll">
                    {onlineFriends.map(f => (
                      <button key={f.id} className="fr__online-item" onClick={() => onOpenChat?.(f.id, null)} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onOpenChat?.(f.id, null); } }}>
                        <Avatar user={f} size={44} showOnline />
                        <span className="fr__online-name">{f.username}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* Online filter */}
              <div className="fr__filter">
                <button className={`fr__filter-btn ${filter === 'all' ? 'fr__filter-btn--active' : ''}`} onClick={() => setFilter('all')} aria-pressed={filter === 'all'}>Все</button>
                <button className={`fr__filter-btn ${filter === 'online' ? 'fr__filter-btn--active' : ''}`} onClick={() => setFilter('online')} aria-pressed={filter === 'online'}>
                  Онлайн
                  <span className="fr__filter-dot" />
                </button>
              </div>

              {filteredFriends.length === 0 ? (
                <EmptyState
                  icon={<UsersThree size={40} weight="regular" />}
                  text={filter === 'online' ? 'Нет друзей онлайн' : 'Пока нет друзей'}
                  hint={filter === 'online' ? 'Когда друзья появятся в сети — ты увидишь их здесь' : 'Перейди во вкладку «Найти друзей» и начни общение'}
                />
              ) : (
                <div className="fr__list">
                  <div className="fr__section-label">Все друзья — {filteredFriends.length}</div>
                  {filteredFriends.map((friend, i) => (
                    <motion.div key={friend.id} className="fr__item" custom={i} variants={itemV} initial="hidden" animate="visible" onClick={() => setProfilePopover({ open: true, userId: friend.id, anchorRef: { current: null } })} role="button" tabIndex={0} onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && setProfilePopover({ open: true, userId: friend.id, anchorRef: { current: null } })}>
                      <Avatar user={friend} size={36} showOnline={friend.status === 'online' || friend.status === 'dnd'} />
                      <div className="fr__item-info">
                        <div className="fr__item-name">{friend.username}<span className="fr__item-tag">{friend.tag}</span></div>
                        <div className={`fr__item-status ${getStatusClass(friend.status)}`}>
                          {getStatusLabel(friend.status)}
                          {friend.status !== 'online' && friend.status !== 'dnd' && friend.lastSeenAt && (
                            <span className="fr__item-lastseen"> — {formatRelativeTime(friend.lastSeenAt)}</span>
                          )}
                        </div>
                      </div>
                      <div className="fr__hover-actions">
                        <button className="fr__quick-action" onClick={(e) => { e.stopPropagation(); onOpenChat?.(friend.id, null); }} title="Чат" aria-label="Написать сообщение">
                          <ChatCircle size={16} weight="regular" />
                        </button>
                        <button className="fr__quick-action" onClick={(e) => { e.stopPropagation(); handleCall(friend.id); }} title="Позвонить" aria-label="Позвонить">
                          <Phone size={16} weight="regular" />
                        </button>
                        <button className="fr__quick-action fr__quick-action--danger" onClick={(e) => { e.stopPropagation(); setRemoveConfirm(friend); }} title="Удалить из друзей" aria-label="Удалить из друзей">
                          <Trash size={16} weight="regular" />
                        </button>
                      </div>
                    </motion.div>
                  ))}
                </div>
              )}
            </motion.div>
          )}

          {tab === 'requests' && (
            <motion.div key="requests" variants={tabV} initial="initial" animate="animate" exit="exit">
              {pending.length === 0 ? (
                <EmptyState icon={<Tray size={40} weight="regular" />} text="Нет входящих заявок" hint="Когда кто-то добавит тебя в друзья — заявка появится здесь" />
              ) : (
                <div className="fr__list">
                  {pending.map((req, i) => (
                    <motion.div key={req.id} className="fr__item" custom={i} variants={itemV} initial="hidden" animate="visible">
                      <div className="fr__item-click" onClick={() => setProfilePopover({ open: true, userId: req.sender.id, anchorRef: { current: null } })}>
                        <Avatar user={req.sender} size={36} />
                        <div className="fr__item-info">
                          <div className="fr__item-name">{req.sender.username}</div>
                          <div className="fr__item-hint">Хочет добавить вас в друзья</div>
                        </div>
                      </div>
                      <div className="fr__item-actions">
                        <motion.button className="fr__act fr__act--accept" onClick={() => acceptRequest(req.id)} whileTap={{ scale: 0.9 }} title="Принять" aria-label="Принять заявку в друзья">
                          <Check size={16} weight="regular" />
                        </motion.button>
                        <motion.button className="fr__act fr__act--decline" onClick={() => declineRequest(req.id)} whileTap={{ scale: 0.9 }} title="Отклонить" aria-label="Отклонить заявку в друзья">
                          <X size={16} weight="regular" />
                        </motion.button>
                      </div>
                    </motion.div>
                  ))}
                </div>
              )}
            </motion.div>
          )}

          {tab === 'search' && (
            <motion.div key="search" variants={tabV} initial="initial" animate="animate" exit="exit">
              <div className="fr__search-wrap">
                <MagnifyingGlass size={15} className="fr__search-icon" weight="regular" />
                <input className="fr__search" placeholder="Поиск по имени пользователя..." value={searchQuery} onChange={e => setSearchQuery(e.target.value)} autoFocus spellCheck={false} />
                {loading && <div className="fr__spinner" />}
              </div>

              {debouncedQuery.length >= 2 && !loading && searchResults.length === 0 && (
                <EmptyState icon={<MagnifyingGlass size={40} weight="regular" />} text="Никого не найдено" hint="Попробуй ввести точный никнейм" />
              )}

              <div className="fr__list">
                {searchResults.map((user, i) => (
                  <motion.div key={user.id} className="fr__item" custom={i} variants={itemV} initial="hidden" animate="visible">
                    <div className="fr__item-click" onClick={() => setProfilePopover({ open: true, userId: user.id, anchorRef: { current: null } })}>
                      <Avatar user={user} size={36} showOnline={user.status === 'online'} />
                      <div className="fr__item-info">
                        <div className="fr__item-name">{user.username}<span className="fr__item-tag">{user.tag}</span></div>
                      </div>
                    </div>
                    <div className="fr__item-actions">
                      {isFriend(user.id) ? (
                        <span className="fr__label fr__label--friend">Друг</span>
                      ) : sentRequests.has(user.id) ? (
                        <span className="fr__label fr__label--sent">Отправлено</span>
                      ) : (
                        <motion.button className="fr__act fr__act--add" onClick={() => sendRequest(user.id)} whileTap={{ scale: 0.9 }}>
                          <UserPlus size={14} weight="regular" /> Добавить
                        </motion.button>
                      )}
                    </div>
                  </motion.div>
                ))}
              </div>
            </motion.div>
          )}

          {tab === 'blocked' && (
            <motion.div key="blocked" variants={tabV} initial="initial" animate="animate" exit="exit">
              {blocked.length === 0 ? (
                <EmptyState icon={<ProhibitInset size={40} weight="regular" />} text="Нет заблокированных" hint="Заблокированные пользователи будут показаны здесь" />
              ) : (
                <div className="fr__list">
                  <div className="fr__section-label">Заблокированные — {blocked.length}</div>
                  {blocked.map((user, i) => (
                    <motion.div key={user.id} className="fr__item" custom={i} variants={itemV} initial="hidden" animate="visible">
                      <Avatar user={user} size={36} />
                      <div className="fr__item-info">
                        <div className="fr__item-name">{user.username}<span className="fr__item-tag">{user.tag}</span></div>
                      </div>
                      <div className="fr__item-actions">
                        <motion.button className="fr__act fr__act--unblock" onClick={() => unblockUser(user.id)} whileTap={{ scale: 0.9 }}>
                          <MinusCircle size={14} weight="regular" /> Разблокировать
                        </motion.button>
                      </div>
                    </motion.div>
                  ))}
                </div>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <ProfilePopover anchorRef={profilePopover.anchorRef} userId={profilePopover.userId} isOpen={profilePopover.open} onClose={() => setProfilePopover({ open: false, userId: null, anchorRef: null })} onAddFriend={sendRequest} onOpenChat={(userId) => { setProfilePopover({ open: false, userId: null, anchorRef: null }); onOpenChat?.(null, userId); }} />

      <ConfirmDialog
        open={!!removeConfirm}
        title="Удалить из друзей?"
        message={`${removeConfirm?.username || 'Пользователь'} будет удалён из вашего списка друзей`}
        confirmText="Удалить"
        danger
        onConfirm={() => removeConfirm && removeFriend(removeConfirm.id)}
        onCancel={() => setRemoveConfirm(null)}
      />
    </div>
  );
}
