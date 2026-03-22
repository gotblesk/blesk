import { useState, useEffect, useMemo, useRef } from 'react';
import { Mic, Users, Trash2, Check, X } from 'lucide-react';
import { useVoiceStore } from '../../store/voiceStore';
import Glass from '../ui/Glass';
import API_URL from '../../config';
import { getCurrentUserId } from '../../utils/auth';
import { getAvatarHue, getAvatarColor } from '../../utils/avatar';
import './VoiceRoomList.css';

export default function VoiceRoomList({ onJoinRoom }) {
  const { rooms, loading, loadRooms, createRoom, deleteRoom, inviteToRoom, kickFromRoom, currentRoomId } = useVoiceStore();
  const [creating, setCreating] = useState(false);
  const [newName, setNewName] = useState('');
  const [error, setError] = useState('');
  const [deleting, setDeleting] = useState(null);
  const [inviting, setInviting] = useState(null); // roomId для которого показываем список друзей
  const [friends, setFriends] = useState([]);
  const [friendsLoading, setFriendsLoading] = useState(false);

  const currentUserId = useMemo(getCurrentUserId, []);
  const deleteTimerRef = useRef(null);

  useEffect(() => () => clearTimeout(deleteTimerRef.current), []);

  useEffect(() => {
    loadRooms();
    const interval = setInterval(loadRooms, 10000);
    return () => clearInterval(interval);
  }, [loadRooms]);

  // Загрузить список друзей когда открывается панель приглашения
  useEffect(() => {
    if (!inviting) return;
    setFriendsLoading(true);
    const token = localStorage.getItem('token');
    fetch(`${API_URL}/api/friends`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => res.json())
      .then((data) => {
        if (Array.isArray(data)) setFriends(data);
      })
      .catch(() => {})
      .finally(() => setFriendsLoading(false));
  }, [inviting]);

  const handleCreate = async () => {
    if (!newName.trim()) return;
    setError('');
    const result = await createRoom(newName.trim());
    if (result?.room) {
      setNewName('');
      setCreating(false);
    } else if (result?.error) {
      setError(result.error);
    }
  };

  const handleDelete = async (e, roomId) => {
    e.stopPropagation();
    if (deleting === roomId) {
      const result = await deleteRoom(roomId);
      if (result?.error) setError(result.error);
      setDeleting(null);
    } else {
      setDeleting(roomId);
      deleteTimerRef.current = setTimeout(() => setDeleting((prev) => (prev === roomId ? null : prev)), 3000);
    }
  };

  const handleInvite = async (roomId, userId) => {
    const result = await inviteToRoom(roomId, userId);
    if (result?.error) {
      setError(result.error);
      setTimeout(() => setError(''), 3000);
    }
  };

  const handleKick = async (e, roomId, userId) => {
    e.stopPropagation();
    await kickFromRoom(roomId, userId);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') handleCreate();
    if (e.key === 'Escape') {
      setCreating(false);
      setNewName('');
    }
  };

  // Проверить приглашён ли друг уже
  const isInvited = (roomId, userId) => {
    const room = rooms.find((r) => r.id === roomId);
    if (!room?.invited) return false;
    return room.invited.some((i) => i.userId === userId);
  };

  return (
    <div className="voice-list section-enter">
      <div className="voice-list__header">
        <h2 className="voice-list__title">Голосовые комнаты</h2>
        <button
          className="voice-list__create-btn"
          onClick={() => setCreating(!creating)}
        >
          {creating ? <X size={16} strokeWidth={2} /> : '+'}
        </button>
      </div>

      {error && <div className="voice-list__error voice-list__error--top">{error}</div>}

      {/* Форма создания */}
      {creating && (
        <div className="voice-list__create-form">
          <input
            className="voice-list__create-input"
            type="text"
            value={newName}
            onChange={(e) => setNewName(e.target.value.slice(0, 50))}
            onKeyDown={handleKeyDown}
            placeholder="Название комнаты..."
            autoFocus
            maxLength={50}
          />
          <button
            className="voice-list__create-submit"
            onClick={handleCreate}
            disabled={!newName.trim()}
          >
            Создать
          </button>
        </div>
      )}

      {/* Список */}
      {loading && rooms.length === 0 && (
        <div className="voice-list__empty">Загрузка...</div>
      )}

      {!loading && rooms.length === 0 && (
        <div className="voice-list__empty">
          <div className="voice-list__empty-icon"><Mic size={18} strokeWidth={1.5} /></div>
          <div className="voice-list__empty-text">Нет голосовых комнат</div>
          <div className="voice-list__empty-hint">Создай первую!</div>
        </div>
      )}

      <div className="voice-list__grid">
        {rooms.map((room) => {
          const isOwner = currentUserId && room.ownerId === currentUserId;
          const showInvitePanel = inviting === room.id;

          return (
            <div key={room.id} className="voice-card-wrapper">
              <Glass
                depth={2}
                hover
                className="voice-card"
              >
                <div className="voice-card__top">
                  <div className="voice-card__icon"><Mic size={18} strokeWidth={1.5} /></div>
                  <div className="voice-card__info">
                    <div className="voice-card__name">
                      {room.name}
                      {isOwner && <span className="voice-card__owner-badge">мой</span>}
                    </div>
                    <div className="voice-card__meta">
                      {room.participantCount > 0 ? (
                        <>
                          <span className="voice-card__live" />
                          {room.participantCount} {room.participantCount === 1 ? 'участник' : 'участников'}
                        </>
                      ) : (
                        'Пусто'
                      )}
                      {room.invited && room.invited.length > 0 && (
                        <span className="voice-card__invited-count">
                          · {room.invited.length} приглашён{room.invited.length === 1 ? '' : 'о'}
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                {/* Мини-аватары участников */}
                {room.participants && room.participants.length > 0 && (
                  <div className="voice-card__avatars">
                    {room.participants.slice(0, 4).map((p) => (
                      <div
                        key={p.userId}
                        className="voice-card__mini-av"
                        style={{ background: getAvatarColor(getAvatarHue(p)) }}
                        title={p.username}
                      >
                        {p.username[0].toUpperCase()}
                      </div>
                    ))}
                    {room.participants.length > 4 && (
                      <div className="voice-card__mini-av voice-card__mini-av--more">
                        +{room.participants.length - 4}
                      </div>
                    )}
                  </div>
                )}

                <div className="voice-card__bottom">
                  {isOwner && (
                    <div className="voice-card__owner-actions" onClick={(e) => e.stopPropagation()}>
                      <button
                        className="voice-card__invite-btn"
                        onClick={(e) => {
                          e.stopPropagation();
                          setInviting(showInvitePanel ? null : room.id);
                        }}
                        title="Пригласить друга"
                      >
                        <Users size={14} strokeWidth={1.5} />
                      </button>
                      <button
                        className={`voice-card__delete ${deleting === room.id ? 'voice-card__delete--confirm' : ''}`}
                        onClick={(e) => handleDelete(e, room.id)}
                        title={deleting === room.id ? 'Нажми ещё раз' : 'Удалить'}
                      >
                        {deleting === room.id ? <Check size={14} strokeWidth={2} /> : <Trash2 size={14} strokeWidth={1.5} />}
                      </button>
                    </div>
                  )}

                  {room.id === currentRoomId ? (
                    <span className="voice-card__here">Вы здесь</span>
                  ) : (
                    <button className="voice-card__join" onClick={(e) => { e.stopPropagation(); onJoinRoom?.(room.id, room.name); }}>
                      Войти
                    </button>
                  )}
                </div>
              </Glass>

              {/* Панель приглашений */}
              {showInvitePanel && isOwner && (
                <div className="voice-invite-panel">
                  <div className="voice-invite-panel__title">Пригласить друзей</div>

                  {/* Уже приглашённые */}
                  {room.invited && room.invited.filter((i) => i.userId !== currentUserId).length > 0 && (
                    <div className="voice-invite-panel__section">
                      <div className="voice-invite-panel__subtitle">Приглашены:</div>
                      {room.invited.filter((i) => i.userId !== currentUserId).map((inv) => (
                        <div key={inv.userId} className="voice-invite-panel__user">
                          <div
                            className="voice-invite-panel__av"
                            style={{ background: getAvatarColor(getAvatarHue(inv)) }}
                          >
                            {(inv.username || '?')[0].toUpperCase()}
                          </div>
                          <span className="voice-invite-panel__name">{inv.username}</span>
                          <button
                            className="voice-invite-panel__kick"
                            onClick={(e) => handleKick(e, room.id, inv.userId)}
                            title="Убрать"
                          >
                            <X size={14} strokeWidth={2} />
                          </button>
                        </div>
                      ))}
                    </div>
                  )}

                  {/* Список друзей для приглашения */}
                  <div className="voice-invite-panel__section">
                    <div className="voice-invite-panel__subtitle">Друзья:</div>
                    {friendsLoading && <div className="voice-invite-panel__loading">Загрузка...</div>}
                    {!friendsLoading && friends.length === 0 && (
                      <div className="voice-invite-panel__empty">Нет друзей для приглашения</div>
                    )}
                    {friends.map((friend) => {
                      const alreadyInvited = isInvited(room.id, friend.id);
                      return (
                        <div key={friend.id} className="voice-invite-panel__user">
                          <div
                            className="voice-invite-panel__av"
                            style={{ background: getAvatarColor(getAvatarHue(friend)) }}
                          >
                            {friend.username[0].toUpperCase()}
                          </div>
                          <span className="voice-invite-panel__name">{friend.username}</span>
                          {alreadyInvited ? (
                            <span className="voice-invite-panel__invited"><Check size={14} strokeWidth={2} /></span>
                          ) : (
                            <button
                              className="voice-invite-panel__add"
                              onClick={() => handleInvite(room.id, friend.id)}
                            >
                              +
                            </button>
                          )}
                        </div>
                      );
                    })}
                  </div>

                  <button
                    className="voice-invite-panel__close"
                    onClick={() => setInviting(null)}
                  >
                    Готово
                  </button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
