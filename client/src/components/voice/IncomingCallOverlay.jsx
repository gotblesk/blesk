import { useState, useEffect, useRef } from 'react';
import { getAvatarHue, getAvatarGradient } from '../../utils/avatar';
import { Video, Phone } from 'lucide-react';
import API_URL from '../../config';
import './IncomingCallOverlay.css';

export default function IncomingCallOverlay({ call, onAccept, onDecline }) {
  const timerRef = useRef(null);
  const onDeclineRef = useRef(onDecline);
  onDeclineRef.current = onDecline;

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
  const [imgError, setImgError] = useState(false);

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
            <Video size={18} strokeWidth={1.5} className="incoming-call-overlay__video-icon" />
          )}
          {!call.videoEnabled && (
            <Phone size={18} strokeWidth={1.5} className="incoming-call-overlay__video-icon" />
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
          <button className="incoming-call-overlay__btn incoming-call-overlay__btn--decline" onClick={onDecline}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M10.68 13.31a16 16 0 0 0 3.41 2.6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7 2 2 0 0 1 1.72 2v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91" />
              <line x1="23" y1="1" x2="1" y2="23" />
            </svg>
          </button>
          <button className="incoming-call-overlay__btn incoming-call-overlay__btn--accept" onClick={onAccept}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  );
}
