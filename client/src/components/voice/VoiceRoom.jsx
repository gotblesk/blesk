import { useState, useEffect, useRef, useCallback } from 'react';
import { useVoiceStore } from '../../store/voiceStore';
import UserProfileModal from '../ui/UserProfileModal';
import VoiceChat from './VoiceChat';
import { getCurrentUserId } from '../../utils/auth';
import './VoiceRoom.css';

export default function VoiceRoom({ socketRef }) {
  const { currentRoomId, currentRoomName, participants, audioLevels, userVolumes, setUserVolume } =
    useVoiceStore();

  const [volumePopup, setVolumePopup] = useState(null); // userId открытого попапа
  const [profileUserId, setProfileUserId] = useState(null); // для модалки профиля
  const popupRef = useRef(null);
  const participantRefs = useRef({});
  const currentUserId = useRef(getCurrentUserId());

  // Клик по участнику — открыть/закрыть попап громкости
  const handleParticipantClick = useCallback(
    (userId) => {
      // Нельзя менять громкость себе
      if (userId === currentUserId.current) return;
      setVolumePopup((prev) => (prev === userId ? null : userId));
    },
    []
  );

  // Закрыть попап при клике за его пределами
  useEffect(() => {
    if (!volumePopup) return;

    const handleClickOutside = (e) => {
      const popupEl = popupRef.current;
      const participantEl = participantRefs.current[volumePopup];
      if (
        popupEl &&
        !popupEl.contains(e.target) &&
        participantEl &&
        !participantEl.contains(e.target)
      ) {
        setVolumePopup(null);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [volumePopup]);

  const participantList = Object.entries(participants);

  return (
    <div className="voice-room">
      <div className="voice-room__title">{currentRoomName || 'Голосовая комната'}</div>

      <div className="voice-room__grid">
        {participantList.map(([userId, peer]) => {
          const level = audioLevels[userId] || 0;
          const isSpeaking = peer.speaking && !peer.muted;
          const isSelf = userId === currentUserId.current;
          const volume = userVolumes[userId] ?? 100;

          return (
            <div
              key={userId}
              className={`voice-participant ${!isSelf ? 'voice-participant--clickable' : ''}`}
              ref={(el) => { participantRefs.current[userId] = el; }}
              onClick={() => handleParticipantClick(userId)}
              onDoubleClick={() => { if (!isSelf) setProfileUserId(userId); }}
            >
              {/* Попап громкости */}
              {volumePopup === userId && (
                <div className="volume-popup" ref={popupRef}>
                  <div className="volume-popup__name">{peer.username}</div>
                  <div className="volume-popup__slider-row">
                    <input
                      type="range"
                      className="volume-popup__slider"
                      min={0}
                      max={200}
                      value={volume}
                      onChange={(e) => setUserVolume(userId, Number(e.target.value))}
                      onClick={(e) => e.stopPropagation()}
                    />
                    <span className="volume-popup__value">{volume}%</span>
                  </div>
                </div>
              )}

              <div
                className={`voice-participant__avatar ${isSpeaking ? 'voice-participant__avatar--speaking' : ''}`}
                style={{
                  background: `hsl(${peer.hue || 176}, 70%, 50%)`,
                  boxShadow: isSpeaking
                    ? `0 0 ${12 + level * 0.3}px hsl(${peer.hue || 176}, 70%, 50%)`
                    : 'none',
                }}
              >
                <span className="voice-participant__letter">
                  {(peer.username || 'U')[0].toUpperCase()}
                </span>

                {/* Иконка мута */}
                {peer.muted && (
                  <div className="voice-participant__mute-icon">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <line x1="1" y1="1" x2="23" y2="23" />
                      <path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V4a3 3 0 0 0-5.94-.6" />
                      <path d="M17 16.95A7 7 0 0 1 5 12v-2m14 0v2c0 .76-.13 1.49-.36 2.18" />
                      <line x1="12" y1="19" x2="12" y2="23" />
                      <line x1="8" y1="23" x2="16" y2="23" />
                    </svg>
                  </div>
                )}

                {/* Иконка деафен */}
                {peer.deafened && (
                  <div className="voice-participant__deafen-icon">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <line x1="1" y1="1" x2="23" y2="23" />
                      <path d="M18 16.36V12a6 6 0 0 0-6-6 5.92 5.92 0 0 0-3.08.87" />
                      <path d="M6 12v4.36a6 6 0 0 0 10.26 4.24" />
                    </svg>
                  </div>
                )}
              </div>

              <div className="voice-participant__name">{peer.username}</div>

              {/* Индикатор громкости */}
              <div className="voice-participant__level">
                <div
                  className="voice-participant__level-bar"
                  style={{
                    width: `${Math.min(100, level)}%`,
                    background: isSpeaking ? '#4ade80' : 'rgba(255,255,255,0.1)',
                  }}
                />
              </div>
            </div>
          );
        })}

        {participantList.length === 0 && (
          <div className="voice-room__empty">Ожидание участников...</div>
        )}
      </div>

      {/* Модалка профиля по двойному клику */}
      <UserProfileModal
        userId={profileUserId}
        open={!!profileUserId}
        onClose={() => setProfileUserId(null)}
      />

      {/* Чат голосовой комнаты */}
      {currentRoomId && socketRef && (
        <VoiceChat roomId={currentRoomId} socketRef={socketRef} />
      )}
    </div>
  );
}
