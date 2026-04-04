import { useState, useEffect, useCallback } from 'react';
import { useVoiceStore } from '../../store/voiceStore';
import { getAvatarHue, getAvatarColor } from '../../utils/avatar';
import { Microphone, MicrophoneSlash, Headphones, SpeakerSlash, VideoCamera, VideoCameraSlash, Monitor, MonitorArrowUp, PhoneDisconnect, UsersThree, CaretDown, Check } from '@phosphor-icons/react';
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
    inputDeviceId,
  } = useVoiceStore();

  const [micDropdown, setMicDropdown] = useState(false);
  const [camDropdown, setCamDropdown] = useState(false);
  const [audioInputDevices, setAudioInputDevices] = useState([]);
  const [videoInputDevices, setVideoInputDevices] = useState([]);

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
      if (!e.target.closest('.vc__split-btn')) {
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
        {/* Mic split-button */}
        <div className="vc__split-btn">
          <button
            className={`vc__btn ${isMuted ? 'vc__btn--muted' : 'vc__btn--on'}`}
            onClick={toggleMute}
            title={isMuted ? 'Включить микрофон' : 'Выключить микрофон'}
            aria-label={isMuted ? 'Включить микрофон' : 'Выключить микрофон'}
            aria-pressed={isMuted}
          >
            {isMuted ? <MicrophoneSlash size={17} /> : <Microphone size={17} />}
          </button>
          <button
            className="vc__btn-arrow"
            onClick={() => { setCamDropdown(false); setMicDropdown(prev => !prev); }}
            aria-label="Выбрать микрофон"
          >
            <CaretDown size={10} />
          </button>
          {micDropdown && (
            <div className="vc__dropdown">
              <div className="vc__dropdown-label">Микрофон</div>
              {audioInputDevices.map(d => (
                <button
                  key={d.deviceId}
                  className={`vc__dropdown-item ${d.deviceId === inputDeviceId ? 'vc__dropdown-item--active' : ''}`}
                  onClick={() => { switchMic(d.deviceId); setMicDropdown(false); }}
                >
                  <span className="vc__dropdown-text">{d.label || 'Микрофон'}</span>
                  {d.deviceId === inputDeviceId && <Check size={14} />}
                </button>
              ))}
              {audioInputDevices.length === 0 && (
                <div className="vc__dropdown-empty">Устройства не найдены</div>
              )}
            </div>
          )}
        </div>

        {/* Deafen */}
        <button
          className={`vc__btn ${isDeafened ? 'vc__btn--muted' : 'vc__btn--on'}`}
          onClick={toggleDeafen}
          title={isDeafened ? 'Включить звук' : 'Выключить звук'}
          aria-label={isDeafened ? 'Включить звук' : 'Выключить звук'}
          aria-pressed={isDeafened}
        >
          {isDeafened ? <SpeakerSlash size={17} /> : <Headphones size={17} />}
        </button>

        <div className="vc__sep vc__sep--short" />

        {/* Camera split-button */}
        <div className="vc__split-btn">
          <button
            className={`vc__btn ${cameraOn ? 'vc__btn--active' : 'vc__btn--on'}`}
            onClick={onCameraToggle}
            title={cameraOn ? 'Выключить камеру' : 'Включить камеру'}
            aria-label={cameraOn ? 'Выключить камеру' : 'Включить камеру'}
            aria-pressed={cameraOn}
          >
            {cameraOn ? <VideoCamera size={17} /> : <VideoCameraSlash size={17} />}
          </button>
          <button
            className="vc__btn-arrow"
            onClick={() => { setMicDropdown(false); setCamDropdown(prev => !prev); }}
            aria-label="Выбрать камеру"
          >
            <CaretDown size={10} />
          </button>
          {camDropdown && (
            <div className="vc__dropdown">
              <div className="vc__dropdown-label">Камера</div>
              {videoInputDevices.map(d => (
                <button
                  key={d.deviceId}
                  className={`vc__dropdown-item ${d.deviceId === currentCamId ? 'vc__dropdown-item--active' : ''}`}
                  onClick={() => { switchCam(d.deviceId); setCamDropdown(false); }}
                >
                  <span className="vc__dropdown-text">{d.label || 'Камера'}</span>
                  {d.deviceId === currentCamId && <Check size={14} />}
                </button>
              ))}
              {videoInputDevices.length === 0 && (
                <div className="vc__dropdown-empty">Устройства не найдены</div>
              )}
            </div>
          )}
        </div>

        {/* Screen share */}
        <button
          className={`vc__btn ${screenShareOn ? 'vc__btn--active' : 'vc__btn--on'}`}
          onClick={onScreenShareToggle}
          title={screenShareOn ? 'Остановить показ экрана' : 'Показать экран'}
          aria-label={screenShareOn ? 'Остановить показ экрана' : 'Показать экран'}
          aria-pressed={screenShareOn}
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
        aria-label="Отключиться от голосового чата"
      >
        <PhoneDisconnect size={17} />
      </button>
    </div>
  );
}
