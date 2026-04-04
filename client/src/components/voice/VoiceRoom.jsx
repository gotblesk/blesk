import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import {
  MicrophoneSlash, Microphone, SpeakerSlash, Headphones,
  UserPlus, UsersThree, MagnifyingGlass, Check, X, ChatCircle,
  ArrowsOutSimple, VideoCamera, VideoCameraSlash, Monitor, MonitorArrowUp,
  PhoneDisconnect, CaretDown, XCircle, Sliders,
} from '@phosphor-icons/react';
import { AnimatePresence } from 'framer-motion';
import { useVoiceStore } from '../../store/voiceStore';
import { useSettingsStore } from '../../store/settingsStore';
import ProfilePopover from '../profile/ProfilePopover';
import Avatar from '../ui/Avatar';
import VoiceChat from './VoiceChat';
import VideoGrid from './VideoGrid';
import ScreenSharePicker from './ScreenSharePicker';
import { getCurrentUserId } from '../../utils/auth';
import { getAvatarHue } from '../../utils/avatar';
import { getAuthHeaders } from '../../utils/authFetch';
import API_URL from '../../config';
import './VoiceRoom.css';

/* ── Quality bars ── */
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

/* ── Invite modal ── */
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
      } catch { /* */ }
      setLoading(false);
    })();
  }, []);

  const filtered = useMemo(() => {
    if (!search.trim()) return friends;
    const q = search.toLowerCase();
    return friends.filter(f => f.username?.toLowerCase().includes(q));
  }, [friends, search]);

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
            <X size={16} weight="bold" />
          </button>
        </div>

        <div className="vr__invite-search">
          <MagnifyingGlass size={14} weight="bold" />
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
                    <Check size={12} weight="bold" /> Отправлено
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

/* ── Screen share tile ── */
function ScreenShareTile({ stream, name, isSelf, onStop, onChangeSource, onQuality }) {
  const videoRef = useRef(null);
  const containerRef = useRef(null);
  const [ctxMenu, setCtxMenu] = useState(null);
  const screenResolution = useSettingsStore((s) => s.screenResolution);
  const screenFps = useSettingsStore((s) => s.screenFps);

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

  const handleCtx = useCallback((e) => {
    if (!isSelf) return;
    e.preventDefault();
    setCtxMenu({ x: e.clientX, y: e.clientY });
  }, [isSelf]);

  useEffect(() => {
    if (!ctxMenu) return;
    const close = () => setCtxMenu(null);
    const esc = (e) => { if (e.key === 'Escape') close(); };
    document.addEventListener('click', close);
    document.addEventListener('keydown', esc);
    return () => { document.removeEventListener('click', close); document.removeEventListener('keydown', esc); };
  }, [ctxMenu]);

  return (
    <div className="vr__screen" ref={containerRef} onContextMenu={handleCtx}>
      <video ref={videoRef} autoPlay playsInline />
      <div className="vr__screen-badge">
        <div className="vr__screen-rec" />
        {name} демонстрирует
      </div>
      <div className="vr__stream-badges">
        <span className="vr__badge vr__badge--live">В ЭФИРЕ</span>
        <span className="vr__badge vr__badge--quality">
          {screenResolution} {screenFps} FPS
        </span>
      </div>
      <button className="vr__screen-fs" onClick={toggleFs} title="Полный экран">
        <ArrowsOutSimple size={14} />
      </button>
      {ctxMenu && (
        <div className="vr__stream-menu" style={{ top: ctxMenu.y, left: ctxMenu.x }} role="menu">
          <button role="menuitem" onClick={() => { onStop?.(); setCtxMenu(null); }}>
            <XCircle size={16} /> Прекратить стрим
          </button>
          <button role="menuitem" onClick={() => { onChangeSource?.(); setCtxMenu(null); }}>
            <MonitorArrowUp size={16} /> Изменить источник
          </button>
          <button role="menuitem" onClick={() => { onQuality?.(); setCtxMenu(null); }}>
            <Sliders size={16} /> Качество передачи
          </button>
        </div>
      )}
    </div>
  );
}

/* ── Participant card (voice mode) ── */
function ParticipantCard({ userId, peer, isSelf, isSpeaking, level, hasCam, onVolumeClick, onProfileOpen }) {
  return (
    <div
      className={`vr__card ${isSpeaking ? 'vr__card--speaking' : ''} ${isSelf ? 'vr__card--self' : ''}`}
      onClick={() => onVolumeClick(userId)}
      onDoubleClick={() => { if (!isSelf) onProfileOpen(userId); }}
    >
      <div
        className="vr__card-ava"
        style={{
          boxShadow: isSpeaking
            ? `0 0 ${20 + level * 0.4}px rgba(80,220,100,0.4)`
            : 'none',
        }}
      >
        <div className="vr__card-ring" />
        <Avatar user={peer} size={72} className="vr__card-avatar" />
      </div>

      <div className="vr__card-name">
        {isSelf ? 'Вы' : peer.username}
      </div>

      <div className="vr__card-status">
        {peer.muted ? (
          <MicrophoneSlash size={14} weight="bold" className="vr__card-ico vr__card-ico--muted" />
        ) : (
          <Microphone size={14} weight="bold" className={`vr__card-ico ${isSpeaking ? 'vr__card-ico--speaking' : ''}`} />
        )}
        {peer.deafened && (
          <SpeakerSlash size={14} weight="bold" className="vr__card-ico vr__card-ico--deaf" />
        )}
        {hasCam && (
          <VideoCamera size={14} weight="bold" className="vr__card-ico vr__card-ico--cam" />
        )}
      </div>
    </div>
  );
}

/* ── Participant strip (small, for stream mode) ── */
function ParticipantStrip({ participantList, videoStreams, currentUserId, audioLevels }) {
  return (
    <div className="vr__strip">
      {participantList.map(([userId, peer]) => {
        const isSpeaking = peer.speaking && !peer.muted;
        const isSelf = userId === currentUserId;
        return (
          <div
            key={userId}
            className={`vr__strip-item ${isSpeaking ? 'vr__strip-item--speaking' : ''}`}
            title={isSelf ? 'Вы' : peer.username}
          >
            <Avatar user={peer} size={48} className="vr__strip-ava" />
            <span className="vr__strip-name">{isSelf ? 'Вы' : peer.username}</span>
          </div>
        );
      })}
    </div>
  );
}

/* ════════════════════════════════════
   VoiceRoom -- 5-state redesign
   ════════════════════════════════════ */
export default function VoiceRoom({ socketRef, onToggleCamera, onToggleScreenShare, onDisableScreenShare, onSwitchScreenSource, onLeave }) {
  const currentRoomId = useVoiceStore((s) => s.currentRoomId);
  const currentRoomName = useVoiceStore((s) => s.currentRoomName);
  const participants = useVoiceStore((s) => s.participants);
  const audioLevels = useVoiceStore((s) => s.audioLevels);
  const userVolumes = useVoiceStore((s) => s.userVolumes);
  const setUserVolume = useVoiceStore((s) => s.setUserVolume);
  const videoStreams = useVoiceStore((s) => s.videoStreams);
  const localCameraStream = useVoiceStore((s) => s.localCameraStream);
  const connectionQuality = useVoiceStore((s) => s.connectionQuality);
  const isMuted = useVoiceStore((s) => s.isMuted);
  const isDeafened = useVoiceStore((s) => s.isDeafened);
  const cameraOn = useVoiceStore((s) => s.cameraOn);
  const screenShareOn = useVoiceStore((s) => s.screenShareOn);
  const toggleMute = useVoiceStore((s) => s.toggleMute);
  const toggleDeafen = useVoiceStore((s) => s.toggleDeafen);

  const [volumePopup, setVolumePopup] = useState(null);
  const [profilePopover, setProfilePopover] = useState({ open: false, userId: null, anchorRef: null });
  const [showInvite, setShowInvite] = useState(false);
  const [showScreenPicker, setShowScreenPicker] = useState(false);
  const [chatOpen, setChatOpen] = useState(true);
  const [micDropdown, setMicDropdown] = useState(false);
  const [camDropdown, setCamDropdown] = useState(false);
  const [audioInputDevices, setAudioInputDevices] = useState([]);
  const [videoInputDevices, setVideoInputDevices] = useState([]);
  const popupRef = useRef(null);
  const participantRefs = useRef({});
  const currentUserId = useRef(getCurrentUserId());

  // Volume popup click
  const handleVolumeClick = useCallback((userId) => {
    if (userId === currentUserId.current) return;
    setVolumePopup((prev) => (prev === userId ? null : userId));
  }, []);

  // Close popup on outside click
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

  // Загрузка устройств
  useEffect(() => {
    const load = () => {
      navigator.mediaDevices.enumerateDevices().then(devices => {
        setAudioInputDevices(devices.filter(d => d.kind === 'audioinput'));
        setVideoInputDevices(devices.filter(d => d.kind === 'videoinput'));
      }).catch(() => {});
    };
    load();
    navigator.mediaDevices.addEventListener('devicechange', load);
    return () => navigator.mediaDevices.removeEventListener('devicechange', load);
  }, []);

  // Закрытие dropdown по клику снаружи и Escape
  useEffect(() => {
    if (!micDropdown && !camDropdown) return;
    const handleClick = (e) => {
      if (!e.target.closest('.vr__split-btn')) {
        setMicDropdown(false);
        setCamDropdown(false);
      }
    };
    const handleKey = (e) => {
      if (e.key === 'Escape') {
        setMicDropdown(false);
        setCamDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClick);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('mousedown', handleClick);
      document.removeEventListener('keydown', handleKey);
    };
  }, [micDropdown, camDropdown]);

  const inputDeviceId = useVoiceStore((s) => s.inputDeviceId);

  const switchMic = useCallback((deviceId) => {
    useVoiceStore.setState({ inputDeviceId: deviceId });
    try { localStorage.setItem('blesk-input-device', deviceId); } catch {}
  }, []);

  const currentCamId = (() => {
    try { return localStorage.getItem('blesk-camera-device') || ''; } catch { return ''; }
  })();

  const switchCam = useCallback((deviceId) => {
    try { localStorage.setItem('blesk-camera-device', deviceId); } catch {}
  }, []);

  const participantList = Object.entries(participants);
  const count = participantList.length;

  // Ambient hue from speaking user
  const speakingUser = useMemo(() => {
    for (const [userId, peer] of participantList) {
      if (peer.speaking && !peer.muted) return { userId, peer };
    }
    return null;
  }, [participantList]);

  const ambientHue = speakingUser ? getAvatarHue(speakingUser.peer) : 260;

  // Screen share entries
  const screenEntries = [];
  for (const [userId, streams] of Object.entries(videoStreams)) {
    if (streams.screen) screenEntries.push({ userId, stream: streams.screen });
  }

  // Has any video (camera)
  const cameraEntries = [];
  for (const [userId, streams] of Object.entries(videoStreams)) {
    if (streams.camera) cameraEntries.push({ userId, stream: streams.camera });
  }
  const hasVideo = cameraEntries.length > 0 || !!localCameraStream;

  // Determine stage mode
  const stageMode = useMemo(() => {
    if (screenEntries.length > 0) return 'stream';
    if (hasVideo) return 'video';
    if (count === 0) return 'waiting';
    return 'voice';
  }, [screenEntries.length, hasVideo, count]);

  // Peers array for VideoGrid
  const peersArray = participantList.map(([userId, peer]) => ({
    id: userId,
    ...peer,
  }));

  const getUserName = (userId) => {
    const p = participants[userId];
    return p?.username || 'Участник';
  };

  // Streamer name for header
  const streamerName = screenEntries.length > 0 ? getUserName(screenEntries[0].userId) : null;

  // Participants without video (for video mode -- shown as cards below)
  const nonVideoParticipants = participantList.filter(([userId]) => {
    const hasCamera = videoStreams[userId]?.camera;
    const isSelfWithCamera = userId === currentUserId.current && localCameraStream;
    return !hasCamera && !isSelfWithCamera;
  });

  return (
    <div className="vr" style={{ '--ambient-hue': ambientHue }}>
      {/* Ambient gradient */}
      <div className={`vr__ambient ${speakingUser ? 'vr__ambient--active' : ''}`} />

      {/* Header */}
      <div className="vr__head">
        <div className="vr__head-left">
          <span className="vr__head-name">{currentRoomName || 'Голосовая комната'}</span>
          <span className="vr__head-count">
            <UsersThree size={12} weight="bold" />
            {count}
          </span>
          {stageMode === 'stream' && streamerName && (
            <span className="vr__head-stream-info">
              <Monitor size={12} weight="bold" />
              {streamerName} демонстрирует экран
            </span>
          )}
        </div>
        <div className="vr__head-right">
          <QualityBars quality={connectionQuality} />
          <button
            className={`vr__head-btn ${chatOpen ? 'vr__head-btn--active' : ''}`}
            onClick={() => setChatOpen((v) => !v)}
            title={chatOpen ? 'Скрыть чат' : 'Показать чат'}
          >
            <ChatCircle size={16} weight="bold" />
          </button>
          <button
            className="vr__head-btn"
            onClick={() => setShowInvite(true)}
            title="Пригласить друга"
          >
            <UserPlus size={16} weight="bold" />
          </button>
        </div>
      </div>

      {/* Body */}
      <div className="vr__body">
        {/* Stage */}
        <div className={`vr__stage vr__stage--${stageMode}`}>

          {/* WAITING */}
          {stageMode === 'waiting' && (
            <div className="vr__waiting">
              <div className="vr__waiting-ava-wrap">
                <div className="vr__waiting-ring" />
                <Avatar user={participantList[0]?.[1] || {}} size={96} className="vr__waiting-ava" />
              </div>
              <div className="vr__waiting-name">
                {participantList[0]?.[1]?.username || 'Вы'}
              </div>
              <div className="vr__waiting-mic">
                {isMuted
                  ? <MicrophoneSlash size={16} weight="bold" />
                  : <Microphone size={16} weight="bold" />
                }
              </div>
              <div className="vr__waiting-text">Ожидание участников...</div>
              <button className="vr__waiting-invite" onClick={() => setShowInvite(true)}>
                <UserPlus size={14} weight="bold" />
                Пригласите друзей
              </button>
            </div>
          )}

          {/* VOICE */}
          {stageMode === 'voice' && (
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
                    ref={(el) => { participantRefs.current[userId] = el; }}
                    style={{ animationDelay: `${i * 0.04}s`, position: 'relative' }}
                    className="vr__card-wrap vr__card-enter"
                  >
                    <ParticipantCard
                      userId={userId}
                      peer={peer}
                      isSelf={isSelf}
                      isSpeaking={isSpeaking}
                      level={level}
                      hasCam={!!hasCam}
                      onVolumeClick={handleVolumeClick}
                      onProfileOpen={(id) => setProfilePopover({ open: true, userId: id, anchorRef: { current: null } })}
                    />

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
                  </div>
                );
              })}
            </div>
          )}

          {/* VIDEO */}
          {stageMode === 'video' && (
            <div className="vr__video-layout">
              <div className="vr__video-main">
                <VideoGrid participants={peersArray} />
              </div>
              {nonVideoParticipants.length > 0 && (
                <div className="vr__video-cards">
                  {nonVideoParticipants.map(([userId, peer]) => {
                    const level = audioLevels[userId] || 0;
                    const isSpeaking = peer.speaking && !peer.muted;
                    const isSelf = userId === currentUserId.current;
                    return (
                      <ParticipantCard
                        key={userId}
                        userId={userId}
                        peer={peer}
                        isSelf={isSelf}
                        isSpeaking={isSpeaking}
                        level={level}
                        hasCam={false}
                        onVolumeClick={handleVolumeClick}
                        onProfileOpen={(id) => setProfilePopover({ open: true, userId: id, anchorRef: { current: null } })}
                      />
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* STREAM */}
          {stageMode === 'stream' && (
            <div className="vr__stream-layout">
              <div className="vr__stream-main">
                {screenEntries.map(({ userId, stream }) => (
                  <ScreenShareTile
                    key={`screen-${userId}`}
                    stream={stream}
                    name={getUserName(userId)}
                    isSelf={userId === currentUserId.current}
                    onStop={onDisableScreenShare}
                    onChangeSource={() => setShowScreenPicker(true)}
                    onQuality={() => {}}
                  />
                ))}
              </div>
              <ParticipantStrip
                participantList={participantList}
                videoStreams={videoStreams}
                currentUserId={currentUserId.current}
                audioLevels={audioLevels}
              />
            </div>
          )}
        </div>

        {/* Chat panel */}
        {chatOpen && currentRoomId && socketRef && (
          <div className="vr__chat-panel">
            <VoiceChat
              roomId={currentRoomId}
              socketRef={socketRef}
              inline
              onClose={() => setChatOpen(false)}
            />
          </div>
        )}
      </div>

      {/* Controls bar */}
      <div className="vr__controls">
        {/* Mic split-button */}
        <div className="vr__split-btn">
          <button
            className={`vr__ctrl ${isMuted ? 'vr__ctrl--danger' : ''}`}
            onClick={toggleMute}
            title={isMuted ? 'Включить микрофон' : 'Выключить микрофон'}
          >
            {isMuted ? <MicrophoneSlash size={20} weight="bold" /> : <Microphone size={20} weight="bold" />}
          </button>
          <button
            className="vr__ctrl-arrow"
            onClick={() => { setCamDropdown(false); setMicDropdown(prev => !prev); }}
            aria-label="Выбрать микрофон"
          >
            <CaretDown size={10} />
          </button>
          {micDropdown && (
            <div className="vr__dropdown">
              <div className="vr__dropdown-label">Микрофон</div>
              {audioInputDevices.map(d => (
                <button
                  key={d.deviceId}
                  className={`vr__dropdown-item ${d.deviceId === inputDeviceId ? 'vr__dropdown-item--active' : ''}`}
                  onClick={() => { switchMic(d.deviceId); setMicDropdown(false); }}
                >
                  <span className="vr__dropdown-text">{d.label || 'Микрофон'}</span>
                  {d.deviceId === inputDeviceId && <Check size={14} />}
                </button>
              ))}
              {audioInputDevices.length === 0 && (
                <div className="vr__dropdown-empty">Устройства не найдены</div>
              )}
            </div>
          )}
        </div>

        <button
          className={`vr__ctrl ${isDeafened ? 'vr__ctrl--danger' : ''}`}
          onClick={toggleDeafen}
          title={isDeafened ? 'Включить звук' : 'Отключить звук'}
        >
          {isDeafened ? <SpeakerSlash size={20} weight="bold" /> : <Headphones size={20} weight="bold" />}
        </button>

        {/* Camera split-button */}
        {onToggleCamera && (
          <div className="vr__split-btn">
            <button
              className={`vr__ctrl ${cameraOn ? 'vr__ctrl--active' : ''}`}
              onClick={onToggleCamera}
              title={cameraOn ? 'Выключить камеру' : 'Включить камеру'}
            >
              {cameraOn ? <VideoCamera size={20} weight="bold" /> : <VideoCameraSlash size={20} weight="bold" />}
            </button>
            <button
              className="vr__ctrl-arrow"
              onClick={() => { setMicDropdown(false); setCamDropdown(prev => !prev); }}
              aria-label="Выбрать камеру"
            >
              <CaretDown size={10} />
            </button>
            {camDropdown && (
              <div className="vr__dropdown">
                <div className="vr__dropdown-label">Камера</div>
                {videoInputDevices.map(d => (
                  <button
                    key={d.deviceId}
                    className={`vr__dropdown-item ${d.deviceId === currentCamId ? 'vr__dropdown-item--active' : ''}`}
                    onClick={() => { switchCam(d.deviceId); setCamDropdown(false); }}
                  >
                    <span className="vr__dropdown-text">{d.label || 'Камера'}</span>
                    {d.deviceId === currentCamId && <Check size={14} />}
                  </button>
                ))}
                {videoInputDevices.length === 0 && (
                  <div className="vr__dropdown-empty">Устройства не найдены</div>
                )}
              </div>
            )}
          </div>
        )}

        {onToggleScreenShare && (
          <button
            className={`vr__ctrl ${screenShareOn ? 'vr__ctrl--active' : ''}`}
            onClick={onToggleScreenShare}
            title={screenShareOn ? 'Остановить демонстрацию' : 'Демонстрация экрана'}
          >
            <Monitor size={20} weight="bold" />
          </button>
        )}

        <button
          className={`vr__ctrl ${chatOpen ? 'vr__ctrl--active' : ''}`}
          onClick={() => setChatOpen((v) => !v)}
          title={chatOpen ? 'Скрыть чат' : 'Показать чат'}
        >
          <ChatCircle size={20} weight="bold" />
        </button>

        {onLeave && (
          <button
            className="vr__ctrl vr__ctrl--leave"
            onClick={onLeave}
            title="Покинуть комнату"
          >
            <PhoneDisconnect size={20} weight="bold" />
          </button>
        )}
      </div>

      {/* Invite modal */}
      {showInvite && (
        <InviteModal
          roomId={currentRoomId}
          onClose={() => setShowInvite(false)}
        />
      )}

      {/* Screen source picker */}
      <AnimatePresence>
        {showScreenPicker && screenShareOn && (
          <ScreenSharePicker
            onSelect={(sourceId, hint) => {
              onSwitchScreenSource?.(sourceId, hint);
              setShowScreenPicker(false);
            }}
            onCancel={() => setShowScreenPicker(false)}
          />
        )}
      </AnimatePresence>

      {/* Profile modal */}
      <ProfilePopover
        anchorRef={profilePopover.anchorRef}
        userId={profilePopover.userId}
        isOpen={profilePopover.open}
        onClose={() => setProfilePopover({ open: false, userId: null, anchorRef: null })}
      />
    </div>
  );
}
