import { useState, useEffect } from 'react';
import { useVoiceStore } from '../../store/voiceStore';
import Glass from '../ui/Glass';
import './VoiceRoomList.css';

export default function VoiceRoomList({ onJoinRoom }) {
  const { rooms, loading, loadRooms, createRoom } = useVoiceStore();
  const [creating, setCreating] = useState(false);
  const [newName, setNewName] = useState('');

  useEffect(() => {
    loadRooms();
    // Обновлять каждые 10 сек
    const interval = setInterval(loadRooms, 10000);
    return () => clearInterval(interval);
  }, [loadRooms]);

  const handleCreate = async () => {
    if (!newName.trim()) return;
    const room = await createRoom(newName.trim());
    if (room) {
      setNewName('');
      setCreating(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') handleCreate();
    if (e.key === 'Escape') {
      setCreating(false);
      setNewName('');
    }
  };

  return (
    <div className="voice-list section-enter">
      <div className="voice-list__header">
        <h2 className="voice-list__title">Голосовые комнаты</h2>
        <button
          className="voice-list__create-btn"
          onClick={() => setCreating(!creating)}
        >
          {creating ? '✕' : '+'}
        </button>
      </div>

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
          <div className="voice-list__empty-icon">🎙</div>
          <div className="voice-list__empty-text">Нет голосовых комнат</div>
          <div className="voice-list__empty-hint">Создай первую!</div>
        </div>
      )}

      <div className="voice-list__grid">
        {rooms.map((room) => (
          <Glass
            key={room.id}
            depth={2}
            hover
            className="voice-card"
            onClick={() => onJoinRoom?.(room.id, room.name)}
          >
            <div className="voice-card__icon">🎙</div>
            <div className="voice-card__info">
              <div className="voice-card__name">{room.name}</div>
              <div className="voice-card__meta">
                {room.participantCount > 0 ? (
                  <>
                    <span className="voice-card__live" />
                    {room.participantCount} {room.participantCount === 1 ? 'участник' : 'участников'}
                  </>
                ) : (
                  'Пусто'
                )}
              </div>
            </div>

            {/* Мини-аватары участников */}
            {room.participants && room.participants.length > 0 && (
              <div className="voice-card__avatars">
                {room.participants.slice(0, 4).map((p) => (
                  <div
                    key={p.userId}
                    className="voice-card__mini-av"
                    style={{ background: `hsl(${p.hue || 176}, 70%, 50%)` }}
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

            <button className="voice-card__join">Войти</button>
          </Glass>
        ))}
      </div>
    </div>
  );
}
