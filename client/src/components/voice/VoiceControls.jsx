import { useVoiceStore } from '../../store/voiceStore';
import { getAvatarHue, getAvatarColor } from '../../utils/avatar';
import './VoiceControls.css';

export default function VoiceControls({ onLeave, onExpand }) {
  const {
    currentRoomName,
    participants,
    isMuted,
    isDeafened,
    toggleMute,
    toggleDeafen,
  } = useVoiceStore();

  const participantList = Object.entries(participants);
  const count = participantList.length;

  return (
    <div className="voice-controls">
      <div className="voice-controls__info" onClick={onExpand}>
        <div className="voice-controls__signal">
          <div className="voice-controls__signal-bars">
            <div className="voice-controls__bar voice-controls__bar--1" />
            <div className="voice-controls__bar voice-controls__bar--2" />
            <div className="voice-controls__bar voice-controls__bar--3" />
          </div>
        </div>
        <div className="voice-controls__room">
          <div className="voice-controls__room-name">{currentRoomName}</div>
          <div className="voice-controls__room-count">
            {count} {count === 1 ? 'участник' : 'участников'}
          </div>
        </div>

        {/* Мини-аватары */}
        <div className="voice-controls__avatars">
          {participantList.slice(0, 3).map(([userId, peer]) => (
            <div
              key={userId}
              className={`voice-controls__mini-av ${peer.speaking ? 'voice-controls__mini-av--speaking' : ''}`}
              style={{ background: getAvatarColor(getAvatarHue(peer)) }}
            >
              {(peer.username || 'U')[0].toUpperCase()}
            </div>
          ))}
        </div>
      </div>

      <div className="voice-controls__buttons">
        {/* Микрофон */}
        <button
          className={`voice-controls__btn ${isMuted ? 'voice-controls__btn--off' : 'voice-controls__btn--on'}`}
          onClick={toggleMute}
          title={isMuted ? 'Включить микрофон' : 'Выключить микрофон'}
        >
          {isMuted ? (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="1" y1="1" x2="23" y2="23" />
              <path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V4a3 3 0 0 0-5.94-.6" />
              <path d="M17 16.95A7 7 0 0 1 5 12v-2m14 0v2c0 .76-.13 1.49-.36 2.18" />
              <line x1="12" y1="19" x2="12" y2="23" />
              <line x1="8" y1="23" x2="16" y2="23" />
            </svg>
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
              <path d="M19 10v2a7 7 0 0 1-14 0v-2" />
              <line x1="12" y1="19" x2="12" y2="23" />
              <line x1="8" y1="23" x2="16" y2="23" />
            </svg>
          )}
        </button>

        {/* Деафен */}
        <button
          className={`voice-controls__btn ${isDeafened ? 'voice-controls__btn--off' : 'voice-controls__btn--on'}`}
          onClick={toggleDeafen}
          title={isDeafened ? 'Включить звук' : 'Выключить звук'}
        >
          {isDeafened ? (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="1" y1="1" x2="23" y2="23" />
              <path d="M18 16.36V12a6 6 0 0 0-6-6 5.92 5.92 0 0 0-3.08.87" />
              <path d="M6 12v4.36a6 6 0 0 0 10.26 4.24" />
            </svg>
          ) : (
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M3 18v-6a9 9 0 0 1 18 0v6" />
              <path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z" />
            </svg>
          )}
        </button>

        {/* Отключиться */}
        <button
          className="voice-controls__btn voice-controls__btn--disconnect"
          onClick={onLeave}
          title="Отключиться"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M10.68 13.31a16 16 0 0 0 3.41 2.6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7 2 2 0 0 1 1.72 2v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91" />
            <line x1="23" y1="1" x2="1" y2="23" />
          </svg>
        </button>
      </div>
    </div>
  );
}
