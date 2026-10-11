import { useState, useEffect, useRef } from 'react';
import { getAvatarHue, getAvatarGradient } from '../../utils/avatar';
import { VideoCamera, Phone, PhoneDisconnect } from '@phosphor-icons/react';
import { soundRingtoneStart, soundRingtoneStop } from '../../utils/sounds';
import API_URL from '../../config';
import './IncomingCallOverlay.css';

export default function IncomingCallOverlay({ call, onAccept, onDecline }) {
  const timerRef = useRef(null);
  const onDeclineRef = useRef(onDecline);
  onDeclineRef.current = onDecline;

  // [Баг #17] useState ПЕРЕД любыми условными return — Rules of Hooks
  const [imgError, setImgError] = useState(false);

  // Рингтон при входящем звонке
  useEffect(() => {
    soundRingtoneStart();
    return () => soundRingtoneStop();
  }, []);

  // Авто-dismiss через 30 сек (ref — таймер не перезапускается при смене колбэка)
  useEffect(() => {
    timerRef.current = setTimeout(() => {
      onDeclineRef.current?.();
    }, 30000);
    return () => clearTimeout(timerRef.current);
  }, []);

  if (!call) return null;

  const hue = getAvatarHue({ hue: call.callerHue, username: call.callerName });
  const isGroup = call.type === 'group';
  const displayName = isGroup ? call.chatName : call.callerName;
  const callerAvatar = call.callerAvatar;

  return (
    <div className="incoming-call-overlay">
      <div className="incoming-call-overlay__backdrop" />

      <div className="incoming-call-overlay__card">
        {/* Аватар с пульсацией */}
        <div className="incoming-call-overlay__avatar-wrap">
          <div className="incoming-call-overlay__pulse" style={{ background: `hsla(${hue}, 70%, 50%, 0.3)` }} />
          <div className="incoming-call-overlay__pulse incoming-call-overlay__pulse--delayed" style={{ background: `hsla(${hue}, 70%, 50%, 0.2)` }} />
          <div
            className="incoming-call-overlay__avatar"
            style={(!callerAvatar || imgError) ? { background: getAvatarGradient(hue) } : {}}
          >
            {callerAvatar && !imgError ? (
              <img
                src={`${API_URL}/uploads/avatars/${callerAvatar}`}
                alt=""
                onError={() => setImgError(true)}
                style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }}
              />
            ) : (
              (call.callerName || '?')[0].toUpperCase()
            )}
          </div>
        </div>

        <div className="incoming-call-overlay__name">
          {call.videoEnabled && (
            <VideoCamera size={18} weight="regular" className="incoming-call-overlay__video-icon" />
          )}
          {!call.videoEnabled && (
            <Phone size={18} weight="regular" className="incoming-call-overlay__video-icon" />
          )}
          {displayName}
        </div>
        <div className="incoming-call-overlay__label">
          {call.videoEnabled
            ? (isGroup ? 'Групповой видеозвонок' : 'Входящий видеозвонок')
            : (isGroup ? 'Групповой звонок' : 'Входящий звонок')
          }
        </div>

        <div className="incoming-call-overlay__actions">
          <button className="incoming-call-overlay__btn incoming-call-overlay__btn--decline" onClick={onDecline} aria-label="Отклонить звонок">
            <PhoneDisconnect size={24} weight="fill" />
          </button>
          <button className="incoming-call-overlay__btn incoming-call-overlay__btn--accept" onClick={onAccept} aria-label="Принять звонок">
            <Phone size={24} weight="fill" />
          </button>
        </div>
      </div>
    </div>
  );
}
