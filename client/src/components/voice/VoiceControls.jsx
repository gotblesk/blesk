import { useVoiceStore } from '../../store/voiceStore';
import { getAvatarHue, getAvatarColor } from '../../utils/avatar';
import { Mic, MicOff, Headphones, HeadphoneOff, Video, VideoOff, Monitor, MonitorOff, PhoneOff } from 'lucide-react';
import './VoiceControls.css';

export default function VoiceControls({ onLeave, onExpand, cameraOn, screenShareOn, onCameraToggle, onScreenShareToggle }) {
  const {
    currentRoomName,
    participants,
    isMuted,
    isDeafened,
    connectionQuality,
    toggleMute,
    toggleDeafen,
  } = useVoiceStore();

  const participantList = Object.entries(participants);
  const count = participantList.length;

  // Quality bars
  const qualColors = { good: 'var(--online)', fair: '#facc15', poor: 'var(--danger)' };
  const qualColor = qualColors[connectionQuality] || 'rgba(255,255,255,0.12)';
  const qualBars = connectionQuality === 'good' ? 3 : connectionQuality === 'fair' ? 2 : connectionQuality === 'poor' ? 1 : 0;
  const qualOff = 'rgba(255,255,255,0.08)';

  return (
    <div className="vc">
      {/* Left: Info */}
      <div className="vc__info" onClick={onExpand}>
        <div className="vc__qual">
          <div className="vc__qual-bar" style={{ height: 3, background: qualBars >= 1 ? qualColor : qualOff }} />
          <div className="vc__qual-bar" style={{ height: 6, background: qualBars >= 2 ? qualColor : qualOff }} />
          <div className="vc__qual-bar" style={{ height: 10, background: qualBars >= 3 ? qualColor : qualOff }} />
        </div>
        <span className="vc__room">{currentRoomName}</span>
        <span className="vc__count">&middot; {count}</span>

        {/* Mini avatars */}
        <div className="vc__avas">
          {participantList.slice(0, 3).map(([userId, peer]) => (
            <div
              key={userId}
              className={`vc__mini-ava ${peer.speaking && !peer.muted ? 'vc__mini-ava--speaking' : ''}`}
              style={{ background: getAvatarColor(getAvatarHue(peer)) }}
            >
              {(peer.username || 'U')[0].toUpperCase()}
            </div>
          ))}
        </div>
      </div>

      <div className="vc__sep" />

      {/* Mute */}
      <button
        className={`vc__btn ${isMuted ? 'vc__btn--muted' : 'vc__btn--on'}`}
        onClick={toggleMute}
        title={isMuted ? 'Включить микрофон' : 'Выключить микрофон'}
      >
        {isMuted ? <MicOff size={15} strokeWidth={1.8} /> : <Mic size={15} strokeWidth={1.8} />}
      </button>

      {/* Deafen */}
      <button
        className={`vc__btn ${isDeafened ? 'vc__btn--muted' : 'vc__btn--on'}`}
        onClick={toggleDeafen}
        title={isDeafened ? 'Включить звук' : 'Выключить звук'}
      >
        {isDeafened ? <HeadphoneOff size={15} strokeWidth={1.8} /> : <Headphones size={15} strokeWidth={1.8} />}
      </button>

      {/* Camera */}
      <button
        className={`vc__btn ${cameraOn ? 'vc__btn--active' : 'vc__btn--on'}`}
        onClick={onCameraToggle}
        title={cameraOn ? 'Выключить камеру' : 'Включить камеру'}
      >
        {cameraOn ? <Video size={15} strokeWidth={1.8} /> : <VideoOff size={15} strokeWidth={1.8} />}
      </button>

      {/* Screen share */}
      <button
        className={`vc__btn ${screenShareOn ? 'vc__btn--active' : 'vc__btn--on'}`}
        onClick={onScreenShareToggle}
        title={screenShareOn ? 'Остановить показ экрана' : 'Показать экран'}
      >
        {screenShareOn ? <MonitorOff size={15} strokeWidth={1.8} /> : <Monitor size={15} strokeWidth={1.8} />}
      </button>

      {/* Disconnect */}
      <button
        className="vc__btn vc__btn--end"
        onClick={onLeave}
        title="Отключиться"
      >
        <PhoneOff size={15} strokeWidth={1.8} />
      </button>
    </div>
  );
}
