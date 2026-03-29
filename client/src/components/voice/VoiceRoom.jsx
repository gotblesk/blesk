import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Maximize, MicOff, HeadphoneOff, UserPlus, Users, Search, Check, X } from 'lucide-react';
import { useVoiceStore } from '../../store/voiceStore';
import UserProfileModal from '../ui/UserProfileModal';
import Avatar from '../ui/Avatar';
import VoiceChat from './VoiceChat';
import VideoGrid from './VideoGrid';
import { getCurrentUserId } from '../../utils/auth';
import { getAvatarHue, getAvatarColor } from '../../utils/avatar';
import { getAuthHeaders } from '../../utils/authFetch';
import API_URL from '../../config';
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

// Модалка приглашения друзей
function InviteModal({ roomId, onClose }) {
  const [friends, setFriends] = useState([]);
  const [search, setSearch] = useState('');
  const [invited, setInvited] = useState(new Set());
  const [loading, setLoading] = useState(true);
  const inviteToRoom = useVoiceStore((s) => s.inviteToRoom);
  const participants = useVoiceStore((s) => s.participants);

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(`${API_URL}/api/friends`, {
          headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
          credentials: 'include',
        });
        if (res.ok) setFriends(await res.json());
      } catch { /* загрузка не удалась */ }
      setLoading(false);
    })();
  }, []);

  const filtered = useMemo(() => {
    if (!search.trim()) return friends;
    const q = search.toLowerCase();
    return friends.filter(f => f.username?.toLowerCase().includes(q));
  }, [friends, search]);

  // Исключить тех, кто уже в комнате
  const participantIds = new Set(Object.keys(participants));

  const handleInvite = async (userId) => {
    if (invited.has(userId)) return;
    await inviteToRoom(roomId, userId);
    setInvited(prev => new Set(prev).add(userId));
  };

  return (
    <div className="vr__invite-overlay" onClick={onClose}>
      <div className="vr__invite-modal" onClick={e => e.stopPropagation()}>
        <div className="vr__invite-head">
          <span className="vr__invite-title">Пригласить в комнату</span>
          <button className="vr__invite-close" onClick={onClose}>
            <X size={16} strokeWidth={2} />
          </button>
        </div>

        <div className="vr__invite-search">
          <Search size={14} strokeWidth={2} />
          <input
            type="text"
            placeholder="Найти друга..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            autoFocus
          />
        </div>

        <div className="vr__invite-list">
          {loading && (
            <div className="vr__invite-empty">Загрузка...</div>
          )}
          {!loading && filtered.length === 0 && (
            <div className="vr__invite-empty">
              {search ? 'Никого не найдено' : 'Нет друзей для приглашения'}
            </div>
          )}
          {filtered.map(friend => {
            const inRoom = participantIds.has(String(friend.id));
            const alreadyInvited = invited.has(friend.id);
            return (
              <div key={friend.id} className="vr__invite-item">
                <div className="vr__invite-user">
                  <Avatar user={friend} size={36} />
                  <div className="vr__invite-info">
                    <span className="vr__invite-name">{friend.username}</span>
                    {friend.status === 'online' && (
                      <span className="vr__invite-status">в сети</span>
                    )}
                  </div>
                </div>
                {inRoom ? (
                  <span className="vr__invite-badge">В комнате</span>
                ) : alreadyInvited ? (
                  <span className="vr__invite-badge vr__invite-badge--sent">
                    <Check size={12} strokeWidth={2.5} /> Отправлено
                  </span>
                ) : (
                  <button
                    className="vr__invite-btn"
                    onClick={() => handleInvite(friend.id)}
                  >
                    Пригласить
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </div>
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
  const [showInvite, setShowInvite] = useState(false);
  const popupRef = useRef(null);
  const participantRefs = useRef({});
  const currentUserId = useRef(getCurrentUserId());

  // Клик по участнику -- попап громкости (не для себя)
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

  // Определяем кто говорит для ambient-градиента
  const speakingUser = useMemo(() => {
    for (const [userId, peer] of participantList) {
      if (peer.speaking && !peer.muted) return { userId, peer };
    }
    return null;
  }, [participantList]);

  const ambientHue = speakingUser
    ? getAvatarHue(speakingUser.peer)
    : 260;

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

  // Адаптивный класс сетки по количеству участников
  const gridClass = count <= 1
    ? 'vr__grid--solo'
    : count <= 2
      ? 'vr__grid--duo'
      : count <= 4
        ? 'vr__grid--quad'
        : 'vr__grid--many';

  return (
    <div className="vr" style={{ '--ambient-hue': ambientHue }}>
      {/* Ambient gradient фон */}
      <div className={`vr__ambient ${speakingUser ? 'vr__ambient--active' : ''}`} />

      {/* Header */}
      <div className="vr__head">
        <div className="vr__head-left">
          <span className="vr__head-name">{currentRoomName || 'Голосовая комната'}</span>
          <span className="vr__head-count">
            <Users size={12} strokeWidth={2} />
            {count}
          </span>
        </div>
        <div className="vr__head-right">
          <QualityBars quality={connectionQuality} />
          <button
            className="vr__invite-trigger"
            onClick={() => setShowInvite(true)}
            title="Пригласить друга"
          >
            <UserPlus size={16} strokeWidth={2} />
            <span>Пригласить</span>
          </button>
        </div>
      </div>

      {/* Screen share -- full width 16:9 */}
      {screenEntries.map(({ userId, stream }) => (
        <ScreenShareTile
          key={`screen-${userId}`}
          stream={stream}
          name={getUserName(userId)}
        />
      ))}

      {/* Video Grid (камеры) -- если есть видео */}
      {hasVideo && <VideoGrid participants={peersArray} />}

      {/* Participants Grid */}
      <div className={`vr__grid ${gridClass}`}>
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
                    ? `0 0 ${20 + level * 0.4}px color-mix(in srgb, var(--online) 35%, transparent)`
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
          <div className="vr__empty">
            <div className="vr__empty-pulse" />
            <span>Ожидание участников...</span>
          </div>
        )}

        {count === 1 && (
          <div className="vr__waiting">
            <span>Пригласите друзей в комнату</span>
            <button className="vr__waiting-btn" onClick={() => setShowInvite(true)}>
              <UserPlus size={14} strokeWidth={2} />
              Пригласить
            </button>
          </div>
        )}
      </div>

      {/* Invite modal */}
      {showInvite && (
        <InviteModal
          roomId={currentRoomId}
          onClose={() => setShowInvite(false)}
        />
      )}

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
      videoRef.current.play().catch(() => {} /* autoplay blocked */);
    }
    return () => {
      if (videoRef.current) videoRef.current.srcObject = null;
    };
  }, [stream]);

  const toggleFs = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    if (document.fullscreenElement === el) {
      document.exitFullscreen().catch(err => console.error('exitFullscreen:', err?.message || err));
    } else {
      el.requestFullscreen().catch(err => console.error('requestFullscreen:', err?.message || err));
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
