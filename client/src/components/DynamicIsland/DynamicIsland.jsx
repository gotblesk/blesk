import { useRef, useEffect, useState, useCallback } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import {
  Microphone, MicrophoneSlash, Headphones, SpeakerSlash,
  PhoneDisconnect, Phone, X, Sparkle, ArrowClockwise,
  User, GearSix, SignOut, Circle, Moon, EyeSlash, CaretDown,
} from '@phosphor-icons/react';
import { useVoiceStore } from '../../store/voiceStore';
import { useCallStore } from '../../store/callStore';
import Avatar from '../ui/Avatar';
import './island.css';

const spring = { type: 'spring', damping: 25, stiffness: 300, mass: 0.8 };

export default function DynamicIsland({ islandState, user, onAcceptCall, onDeclineCall, onEndCall, onOpenChat, onOpenProfile, onOpenSettings, onLogout, onStatusChange }) {
  const {
    state, messageData, typingData, updateData, updateProgress,
    incomingCall, activeCall, voiceRoomId, voiceRoomName,
    toggleProfile, closeProfile, dismissUpdate, setMessageData,
  } = islandState;

  const isMuted = useVoiceStore(s => s.isMuted);
  const isDeafened = useVoiceStore(s => s.isDeafened);
  const toggleMute = useVoiceStore(s => s.toggleMute);
  const toggleDeafen = useVoiceStore(s => s.toggleDeafen);

  // Call timer
  const [callElapsed, setCallElapsed] = useState(0);
  const callTimerRef = useRef(null);

  useEffect(() => {
    if (state === 'call') {
      const startedAt = activeCall?.startedAt || Date.now();
      setCallElapsed(Math.floor((Date.now() - startedAt) / 1000));
      callTimerRef.current = setInterval(() => {
        setCallElapsed(Math.floor((Date.now() - startedAt) / 1000));
      }, 1000);
    } else {
      if (callTimerRef.current) { clearInterval(callTimerRef.current); callTimerRef.current = null; }
      setCallElapsed(0);
    }
    return () => { if (callTimerRef.current) clearInterval(callTimerRef.current); };
  }, [state, activeCall?.startedAt]);

  const formatTimer = (sec) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  // Click outside to close profile
  const islandRef = useRef(null);
  useEffect(() => {
    if (state !== 'profile') return;
    const handler = (e) => {
      if (islandRef.current && !islandRef.current.contains(e.target)) closeProfile();
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [state, closeProfile]);

  // Escape to close profile
  useEffect(() => {
    if (state !== 'profile') return;
    const handler = (e) => { if (e.key === 'Escape') closeProfile(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [state, closeProfile]);

  // Enter to accept call, Space to toggle mute
  useEffect(() => {
    const handler = (e) => {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;
      if (state === 'incoming' && e.key === 'Enter') onAcceptCall?.();
      if (state === 'call' && e.code === 'Space') { e.preventDefault(); toggleMute(); }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [state, onAcceptCall, toggleMute]);

  const handleIslandClick = () => {
    if (state === 'idle') toggleProfile();
    else if (state === 'profile') closeProfile();
    else if (state === 'message' && messageData) {
      onOpenChat?.(messageData.chatId);
      setMessageData(null);
    }
  };

  const callName = activeCall?.callerName || voiceRoomName || 'Звонок';
  const userStatus = user?.status || 'online';

  return (
    <div
      className={`di di--${state}`}
      ref={islandRef}
      data-state={state}
    >
      <motion.div
        className="di__glass"
        layout
        transition={spring}
        onClick={handleIslandClick}
      >
        {/* Inner glass layer — Double Layer Glass */}
        <div className="di__inner-glass">
          <AnimatePresence mode="wait">
            {/* LOADING / OFFLINE — показываем имя + статус соединения через точку */}
            {state === 'loading' && (
              <motion.div key="loading" className="di__inner" initial={{ opacity: 1 }} animate={{ opacity: 1 }} exit={{ opacity: 0, transition: { duration: 0.1 } }}>
                <div className="di__dot di__dot--reconnecting" title="Переподключение..." />
                <span className="di__nick">{user?.username || 'blesk'}</span>
              </motion.div>
            )}

            {/* IDLE */}
            {state === 'idle' && (
              <motion.div key="idle" className="di__inner" initial={{ opacity: 0 }} animate={{ opacity: 1, transition: { duration: 0.12 } }} exit={{ opacity: 0, transition: { duration: 0.1 } }}>
                <div className={`di__dot di__dot--${userStatus}`} />
                <span className="di__nick">{user?.username || 'blesk'}</span>
              </motion.div>
            )}

            {/* MESSAGE */}
            {state === 'message' && messageData && (
              <motion.div key="message" className="di__inner" initial={{ opacity: 0, y: 3 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
                <Avatar user={messageData.user || { username: messageData.username }} size={34} />
                <div className="di__msg-col">
                  <span className="di__msg-name">{messageData.username}</span>
                  <span className="di__msg-text">{messageData.preview || messageData.text}</span>
                </div>
              </motion.div>
            )}

            {/* TYPING */}
            {state === 'typing' && typingData && (
              <motion.div key="typing" className="di__inner" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <Avatar user={{ username: typingData.username }} size={26} />
                <span className="di__typing-name">{typingData.username}</span>
                <div className="di__typing-dots"><div/><div/><div/></div>
              </motion.div>
            )}

            {/* ACTIVE CALL */}
            {state === 'call' && (
              <motion.div key="call" className="di__inner" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <div className="di__call-pulse" />
                <div className="di__call-info">
                  <span className="di__call-name">{callName.length > 18 ? callName.slice(0, 18) + '\u2026' : callName}</span>
                  <span className="di__call-timer">{formatTimer(callElapsed)}</span>
                </div>
                <div className="di__call-btns">
                  <button className={`di__call-btn di__call-btn--mic ${isMuted ? 'di__call-btn--on' : ''}`} onClick={e => { e.stopPropagation(); toggleMute(); }}>
                    {isMuted ? <MicrophoneSlash size={16} /> : <Microphone size={16} />}
                  </button>
                  <button className={`di__call-btn di__call-btn--deaf ${isDeafened ? 'di__call-btn--on' : ''}`} onClick={e => { e.stopPropagation(); toggleDeafen(); }}>
                    {isDeafened ? <SpeakerSlash size={16} /> : <Headphones size={16} />}
                  </button>
                  <button className="di__call-btn di__call-btn--end" onClick={e => { e.stopPropagation(); onEndCall?.(); }}>
                    <PhoneDisconnect size={16} />
                  </button>
                </div>
              </motion.div>
            )}

            {/* INCOMING CALL */}
            {state === 'incoming' && incomingCall && (
              <motion.div key="incoming" className="di__inner" initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0 }}>
                <Avatar user={{ username: incomingCall.callerName, avatar: incomingCall.callerAvatar }} size={42} className="di__in-ava" />
                <div className="di__in-col">
                  <span className="di__in-label">Входящий звонок</span>
                  <span className="di__in-name">{incomingCall.callerName}</span>
                </div>
                <div className="di__in-btns">
                  <button className="di__in-btn di__in-btn--no" onClick={e => { e.stopPropagation(); onDeclineCall?.(); }}><X size={16} weight="bold" /></button>
                  <button className="di__in-btn di__in-btn--yes" onClick={e => { e.stopPropagation(); onAcceptCall?.(); }}><Phone size={16} weight="fill" /></button>
                </div>
              </motion.div>
            )}

            {/* UPDATE */}
            {state === 'update' && updateData && (
              <motion.div key="update" className="di__inner" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <Sparkle size={14} weight="fill" className="di__upd-icon" />
                <span className="di__upd-text">blesk {updateData.version}</span>
                <button className="di__upd-btn" onClick={e => e.stopPropagation()}>Обновить</button>
                <button className="di__upd-x" onClick={e => { e.stopPropagation(); dismissUpdate(); }}>&#10005;</button>
              </motion.div>
            )}

            {/* UPDATE PROGRESS */}
            {state === 'update_progress' && (
              <motion.div key="update_progress" className="di__inner" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <ArrowClockwise size={14} className="di__upd-spin" />
                <span className="di__upd-text">Загрузка... {updateProgress}%</span>
                <div className="di__upd-bar"><div className="di__upd-fill" style={{ width: `${updateProgress}%` }} /></div>
              </motion.div>
            )}

            {/* PROFILE */}
            {state === 'profile' && (
              <motion.div key="profile" className="di__inner di__inner--profile" initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 4 }} transition={{ duration: 0.25 }}>
                <div className="di__prof-head">
                  <Avatar user={user} size={44} />
                  <div className="di__prof-info">
                    <span className="di__prof-name">{user?.username || 'blesk'}</span>
                    <div className="di__prof-status-row">
                      <div className={`di__dot di__dot--${userStatus}`} style={{ width: 6, height: 6 }} />
                      <span className="di__prof-status-text">
                        {userStatus === 'online' ? 'В сети' : userStatus === 'dnd' ? 'Не беспокоить' : userStatus === 'invisible' ? 'Невидимка' : 'В сети'}
                      </span>
                      <CaretDown size={10} style={{ color: 'rgba(255,255,255,0.2)' }} />
                    </div>
                  </div>
                  <button className="di__prof-x" onClick={e => { e.stopPropagation(); closeProfile(); }}>&#10005;</button>
                </div>

                {/* Status selector */}
                <div className="di__prof-statuses">
                  <button className={`di__prof-status-opt ${userStatus === 'online' ? 'di__prof-status-opt--active' : ''}`} onClick={e => { e.stopPropagation(); onStatusChange?.('online'); }}>
                    <Circle size={8} weight="fill" style={{ color: 'var(--online, #4ade80)' }} /> В сети
                  </button>
                  <button className={`di__prof-status-opt ${userStatus === 'dnd' ? 'di__prof-status-opt--active' : ''}`} onClick={e => { e.stopPropagation(); onStatusChange?.('dnd'); }}>
                    <Moon size={8} weight="fill" style={{ color: '#facc15' }} /> Не беспокоить
                  </button>
                  <button className={`di__prof-status-opt ${userStatus === 'invisible' ? 'di__prof-status-opt--active' : ''}`} onClick={e => { e.stopPropagation(); onStatusChange?.('invisible'); }}>
                    <EyeSlash size={8} style={{ color: 'rgba(255,255,255,0.3)' }} /> Невидимка
                  </button>
                </div>

                <div className="di__prof-sep" />

                <div className="di__prof-grid">
                  <button className="di__prof-btn" onClick={e => { e.stopPropagation(); closeProfile(); onOpenProfile?.(); }}><User size={18} /><span>Профиль</span></button>
                  <button className="di__prof-btn" onClick={e => { e.stopPropagation(); closeProfile(); onOpenSettings?.(); }}><GearSix size={18} /><span>Настройки</span></button>
                </div>

                <button className="di__prof-logout" onClick={e => { e.stopPropagation(); onLogout?.(); }}>
                  <SignOut size={12} /> Выйти
                </button>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </motion.div>
    </div>
  );
}
