import { useState, useEffect, useRef, useCallback } from 'react';
import { Maximize, MicOff, HeadphoneOff } from 'lucide-react';
import { useVoiceStore } from '../../store/voiceStore';
import UserProfileModal from '../ui/UserProfileModal';
import VoiceChat from './VoiceChat';
import VideoGrid from './VideoGrid';
import { getCurrentUserId } from '../../utils/auth';
import { getAvatarHue, getAvatarColor } from '../../utils/avatar';
import './VoiceRoom.css';

// Иконки качества (3 полоски)
function QualityBars({ quality }) {
  const colors = { good: 'var(--online)', fair: '#facc15', poor: 'var(--danger)' };
  const color = colors[quality] || 'rgba(255,255,255,0.12)';
  const bars = quality === 'good' ? 3 : quality === 'fair' ? 2 : quality === 'poor' ? 1 : 0;
  const off = 'rgba(255,255,255,0.08)';

  return (
    <div className="vr__qual" title={quality ? `Соединение: ${quality}` : 'Соединение...'}>
      <div className="vr__qual-bar" style={{ height: 4, background: bars >= 1 ? color : off }} />
      <div className="vr__qual-bar" style={{ height: 8, background: bars >= 2 ? color : off }} />
      <div className="vr__qual-bar" style={{ height: 12, background: bars >= 3 ? color : off }} />
    </div>
  );
}

export default function VoiceRoom({ socketRef }) {
  const currentRoomId = useVoiceStore((s) => s.currentRoomId);
  const currentRoomName = useVoiceStore((s) => s.currentRoomName);
  const participants = useVoiceStore((s) => s.participants);
  const audioLevels = useVoiceStore((s) => s.audioLevels);
  const userVolumes = useVoiceStore((s) => s.userVolumes);
  const setUserVolume = useVoiceStore((s) => s.setUserVolume);
  const videoStreams = useVoiceStore((s) => s.videoStreams);
  const localCameraStream = useVoiceStore((s) => s.localCameraStream);
  const connectionQuality = useVoiceStore((s) => s.connectionQuality);

  const [volumePopup, setVolumePopup] = useState(null);
  const [profileUserId, setProfileUserId] = useState(null);
  const popupRef = useRef(null);
  const participantRefs = useRef({});
  const currentUserId = useRef(getCurrentUserId());

  // Клик по участнику — попап громкости (не для себя)
  const handleClick = useCallback((userId) => {
    if (userId === currentUserId.current) return;
    setVolumePopup((prev) => (prev === userId ? null : userId));
  }, []);

  // Закрыть попап при клике вне
  useEffect(() => {
    if (!volumePopup) return;
    const handler = (e) => {
      const popup = popupRef.current;
      const tile = participantRefs.current[volumePopup];
      if (popup && !popup.contains(e.target) && tile && !tile.contains(e.target)) {
        setVolumePopup(null);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [volumePopup]);

  const participantList = Object.entries(participants);
  const count = participantList.length;

  // Screen share потоки
  const screenEntries = [];
  for (const [userId, streams] of Object.entries(videoStreams)) {
    if (streams.screen) screenEntries.push({ userId, stream: streams.screen });
  }

  const hasVideo = Object.keys(videoStreams).length > 0 || !!localCameraStream;

  // Массив для VideoGrid
  const peersArray = participantList.map(([userId, peer]) => ({
    id: userId,
    ...peer,
  }));

  const getUserName = (userId) => {
    const p = participants[userId];
    return p?.username || 'Участник';
  };

  // [Баг #23] Fullscreen теперь внутри каждого ScreenShareTile (свой ref)

  return (
    <div className="vr">
      {/* Header */}
      <div className="vr__head">
        <div className="vr__head-left">
          <span className="vr__head-name">{currentRoomName || 'Голосовая комната'}</span>
          <span className="vr__head-count">{count}</span>
        </div>
        <div className="vr__head-right">
          <QualityBars quality={connectionQuality} />
        </div>
      </div>

      {/* Screen share — full width 16:9 */}
      {/* [Баг #23] Каждый ScreenShareTile имеет свой внутренний ref */}
      {screenEntries.map(({ userId, stream }) => (
        <ScreenShareTile
          key={`screen-${userId}`}
          stream={stream}
          name={getUserName(userId)}
        />
      ))}

      {/* Video Grid (камеры) — если есть видео */}
      {hasVideo && <VideoGrid participants={peersArray} />}

      {/* Participants Grid */}
      <div className="vr__grid">
        {participantList.map(([userId, peer], i) => {
          const level = audioLevels[userId] || 0;
          const isSpeaking = peer.speaking && !peer.muted;
          const isSelf = userId === currentUserId.current;
          const volume = userVolumes[userId] ?? 100;
          const hasCam = videoStreams[userId]?.camera;

          return (
            <div
              key={userId}
              className={`vr__user vr__user-enter ${isSpeaking ? 'vr__user--speaking' : ''} ${isSelf ? 'vr__user--self' : ''}`}
              style={{ animationDelay: `${i * 0.04}s` }}
              ref={(el) => { participantRefs.current[userId] = el; }}
              onClick={() => handleClick(userId)}
              onDoubleClick={() => { if (!isSelf) setProfileUserId(userId); }}
            >
              {/* Volume popup */}
              {volumePopup === userId && (
                <div className="vr__vol-popup" ref={popupRef}>
                  <div className="vr__vol-name">{peer.username}</div>
                  <div className="vr__vol-row">
                    <input
                      type="range"
                      className="vr__vol-slider"
                      min={0}
                      max={200}
                      value={volume}
                      onChange={(e) => setUserVolume(userId, Number(e.target.value))}
                      onClick={(e) => e.stopPropagation()}
                    />
                    <span className="vr__vol-value">{volume}%</span>
                  </div>
                </div>
              )}

              {/* Avatar */}
              <div
                className="vr__user-ava"
                style={{
                  background: getAvatarColor(getAvatarHue(peer)),
                  boxShadow: isSpeaking
                    ? `0 0 ${14 + level * 0.3}px color-mix(in srgb, var(--online) 30%, transparent)`
                    : 'none',
                }}
              >
                <div className="vr__user-level" />
                {(peer.username || 'U')[0].toUpperCase()}

                {/* Status icons */}
                <div className="vr__user-icons">
                  {hasCam && (
                    <div className="vr__user-ico vr__user-ico--cam">
                      <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3"><path d="M23 7l-7 5 7 5V7z"/><rect x="1" y="5" width="15" height="14" rx="2"/></svg>
                    </div>
                  )}
                  {peer.muted && (
                    <div className="vr__user-ico vr__user-ico--muted">
                      <MicOff size={8} strokeWidth={3} />
                    </div>
                  )}
                  {peer.deafened && (
                    <div className="vr__user-ico vr__user-ico--deaf">
                      <HeadphoneOff size={8} strokeWidth={3} />
                    </div>
                  )}
                </div>
              </div>

              {/* Name */}
              <div className="vr__user-name">
                {isSelf ? 'Вы' : peer.username}
              </div>
            </div>
          );
        })}

        {count === 0 && (
          <div className="vr__empty">Ожидание участников...</div>
        )}
      </div>

      {/* Profile modal */}
      <UserProfileModal
        userId={profileUserId}
        open={!!profileUserId}
        onClose={() => setProfileUserId(null)}
      />

      {/* Voice chat sidebar */}
      {currentRoomId && socketRef && (
        <VoiceChat roomId={currentRoomId} socketRef={socketRef} />
      )}
    </div>
  );
}

// [Баг #23] Screen share tile с собственным внутренним ref для fullscreen
function ScreenShareTile({ stream, name }) {
  const videoRef = useRef(null);
  const containerRef = useRef(null);

  useEffect(() => {
    if (videoRef.current && stream) {
      videoRef.current.srcObject = stream;
      videoRef.current.play().catch(() => {});
    }
    return () => {
      if (videoRef.current) videoRef.current.srcObject = null;
    };
  }, [stream]);

  const toggleFs = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    if (document.fullscreenElement === el) {
      document.exitFullscreen().catch(() => {});
    } else {
      el.requestFullscreen().catch(() => {});
    }
  }, []);

  return (
    <div className="vr__screen" ref={containerRef}>
      <video ref={videoRef} autoPlay playsInline />
      <div className="vr__screen-badge">
        <div className="vr__screen-rec" />
        {name} демонстрирует
      </div>
      <div className="vr__screen-name">{name} — экран</div>
      <button className="vr__screen-fs" onClick={toggleFs} title="Полный экран">
        <Maximize size={14} strokeWidth={1.5} />
      </button>
    </div>
  );
}
