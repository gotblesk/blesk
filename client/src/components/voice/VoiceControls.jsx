import { useVoiceStore } from '../../store/voiceStore';
import { getAvatarHue, getAvatarColor } from '../../utils/avatar';
import { Microphone, MicrophoneSlash, Headphones, SpeakerSlash, VideoCamera, VideoCameraSlash, Monitor, MonitorArrowUp, PhoneDisconnect, UsersThree } from '@phosphor-icons/react';
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
      {/* Left: Room info */}
      <div className="vc__info" onClick={onExpand}>
        <div className="vc__qual">
          <div className="vc__qual-bar" style={{ height: 4, background: qualBars >= 1 ? qualColor : qualOff }} />
          <div className="vc__qual-bar" style={{ height: 7, background: qualBars >= 2 ? qualColor : qualOff }} />
          <div className="vc__qual-bar" style={{ height: 11, background: qualBars >= 3 ? qualColor : qualOff }} />
        </div>
        <div className="vc__room-info">
          <span className="vc__room">{currentRoomName}</span>
          <span className="vc__count">
            <UsersThree size={10} weight="bold" />
            {count}
          </span>
        </div>

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
          {count > 3 && (
            <div className="vc__mini-ava vc__mini-ava--more">
              +{count - 3}
            </div>
          )}
        </div>
      </div>

      <div className="vc__sep" />

      {/* Controls group */}
      <div className="vc__controls">
        {/* Mute */}
        <button
          className={`vc__btn ${isMuted ? 'vc__btn--muted' : 'vc__btn--on'}`}
          onClick={toggleMute}
          title={isMuted ? 'Включить микрофон' : 'Выключить микрофон'}
        >
          {isMuted ? <MicrophoneSlash size={17} /> : <Microphone size={17} />}
        </button>

        {/* Deafen */}
        <button
          className={`vc__btn ${isDeafened ? 'vc__btn--muted' : 'vc__btn--on'}`}
          onClick={toggleDeafen}
          title={isDeafened ? 'Включить звук' : 'Выключить звук'}
        >
          {isDeafened ? <SpeakerSlash size={17} /> : <Headphones size={17} />}
        </button>

        <div className="vc__sep vc__sep--short" />

        {/* Camera */}
        <button
          className={`vc__btn ${cameraOn ? 'vc__btn--active' : 'vc__btn--on'}`}
          onClick={onCameraToggle}
          title={cameraOn ? 'Выключить камеру' : 'Включить камеру'}
        >
          {cameraOn ? <VideoCamera size={17} /> : <VideoCameraSlash size={17} />}
        </button>

        {/* Screen share */}
        <button
          className={`vc__btn ${screenShareOn ? 'vc__btn--active' : 'vc__btn--on'}`}
          onClick={onScreenShareToggle}
          title={screenShareOn ? 'Остановить показ экрана' : 'Показать экран'}
        >
          {screenShareOn ? <MonitorArrowUp size={17} /> : <Monitor size={17} />}
        </button>
      </div>

      <div className="vc__sep" />

      {/* Disconnect */}
      <button
        className="vc__btn vc__btn--end"
        onClick={onLeave}
        title="Отключиться"
      >
        <PhoneDisconnect size={17} />
      </button>
    </div>
  );
}
