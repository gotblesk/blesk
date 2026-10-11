import { useRef, useEffect, useState, useCallback } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import {
  Microphone, MicrophoneSlash, Headphones, SpeakerSlash,
  PhoneDisconnect, Phone, X, Sparkle, ArrowClockwise,
  Record, UploadSimple,
} from '@phosphor-icons/react';
import { useVoiceStore } from '../../store/voiceStore';
import { useCallStore } from '../../store/callStore';
import Avatar from '../ui/Avatar';
import './island.css';

const morphSpring = { type: 'spring', damping: 30, stiffness: 400, mass: 0.6 };

const stateStyles = {
  loading:         { borderRadius: 100, padding: '4px' },
  idle:            { borderRadius: 100, padding: '4px' },
  message:         { borderRadius: 22,  padding: '10px 14px' },
  typing:          { borderRadius: 100, padding: '6px 12px' },
  call:            { borderRadius: 22,  padding: '10px 14px' },
  incoming:        { borderRadius: 24,  padding: '14px 18px' },
  update:          { borderRadius: 22,  padding: '10px 14px' },
  update_progress: { borderRadius: 22,  padding: '10px 14px' },
  recording:       { borderRadius: 22,  padding: '10px 14px' },
  uploading:       { borderRadius: 22,  padding: '10px 14px' },
};

const stateMinWidths = {
  loading: 0, idle: 0, message: 240, typing: 0,
  call: 260, incoming: 280, update: 200, update_progress: 200,
  recording: 220, uploading: 260,
};

export default function DynamicIsland({ islandState, user, onAcceptCall, onDeclineCall, onEndCall, onOpenChat }) {
  const {
    state, messageData, typingData, updateData, updateProgress,
    recordingData, uploadingData,
    incomingCall, activeCall, voiceRoomId, voiceRoomName,
    dismissUpdate, setMessageData,
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

  // Recording timer
  const [recElapsed, setRecElapsed] = useState(0);
  const recTimerRef = useRef(null);

  useEffect(() => {
    if (state === 'recording' && recordingData?.startedAt) {
      setRecElapsed(Math.floor((Date.now() - recordingData.startedAt) / 1000));
      recTimerRef.current = setInterval(() => {
        setRecElapsed(Math.floor((Date.now() - recordingData.startedAt) / 1000));
      }, 1000);
    } else {
      if (recTimerRef.current) { clearInterval(recTimerRef.current); recTimerRef.current = null; }
      setRecElapsed(0);
    }
    return () => { if (recTimerRef.current) clearInterval(recTimerRef.current); };
  }, [state, recordingData?.startedAt]);

  const formatTimer = (sec) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

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
    if (state === 'message' && messageData) {
      onOpenChat?.(messageData.chatId);
      setMessageData(null);
    }
  };

  const callName = activeCall?.callerName || voiceRoomName || 'Звонок';
  const userStatus = user?.status || 'online';

  const currentStyle = stateStyles[state] || stateStyles.idle;

  // Content enter/exit animations
  const contentEnter = { opacity: 0, y: 4, scale: 0.97 };
  const contentAnimate = { opacity: 1, y: 0, scale: 1 };
  const contentExit = { opacity: 0, scale: 0.95 };
  const contentEnterTransition = { duration: 0.15, delay: 0.03 };
  const contentExitTransition = { duration: 0.08 };

  return (
    <div
      className={`di di--${state}`}
      data-state={state}
    >
      <motion.div
        className="di__glass"
        layout
        animate={{
          borderRadius: currentStyle.borderRadius,
          padding: currentStyle.padding,
        }}
        transition={morphSpring}
        style={{
          minWidth: stateMinWidths[state] || 0,
        }}
        onClick={handleIslandClick}
      >
        {/* Inner glass layer — Double Layer Glass */}
        <div className="di__inner-glass">
          <AnimatePresence mode="popLayout">
            {/* LOADING */}
            {state === 'loading' && (
              <motion.div key="loading" className="di__inner" initial={{ opacity: 1 }} animate={{ opacity: 1 }} exit={contentExit} transition={contentExitTransition}>
                <div className={`di__dot di__dot--${userStatus}`} />
                <span className="di__nick">{user?.username || 'blesk'}</span>
              </motion.div>
            )}

            {/* IDLE */}
            {state === 'idle' && (
              <motion.div key="idle" className="di__inner" initial={contentEnter} animate={contentAnimate} exit={contentExit} transition={contentEnterTransition}>
                <div className={`di__dot di__dot--${userStatus}`} />
                <span className="di__nick">{user?.username || 'blesk'}</span>
              </motion.div>
            )}

            {/* MESSAGE */}
            {state === 'message' && messageData && (
              <motion.div key="message" className="di__inner" initial={contentEnter} animate={contentAnimate} exit={contentExit} transition={contentEnterTransition}>
                <Avatar user={messageData.user || { username: messageData.username }} size={34} />
                <div className="di__msg-col">
                  <span className="di__msg-name">{messageData.username}</span>
                  <span className="di__msg-text">{messageData.preview || messageData.text}</span>
                </div>
              </motion.div>
            )}

            {/* TYPING */}
            {state === 'typing' && typingData && (
              <motion.div key="typing" className="di__inner" initial={contentEnter} animate={contentAnimate} exit={contentExit} transition={contentEnterTransition}>
                <Avatar user={{ username: typingData.username }} size={26} />
                <span className="di__typing-name">{typingData.username}</span>
                <div className="di__typing-dots"><div/><div/><div/></div>
              </motion.div>
            )}

            {/* ACTIVE CALL */}
            {state === 'call' && (
              <motion.div key="call" className="di__inner" initial={contentEnter} animate={contentAnimate} exit={contentExit} transition={contentEnterTransition}>
                <div className="di__call-pulse" />
                <div className="di__call-info">
                  <span className="di__call-name">{callName.length > 18 ? callName.slice(0, 18) + '\u2026' : callName}</span>
                  <span className="di__call-timer">{formatTimer(callElapsed)}</span>
                </div>
                <div className="di__call-btns">
                  <button className={`di__call-btn di__call-btn--mic ${isMuted ? 'di__call-btn--on' : ''}`} title={isMuted ? 'Включить микрофон' : 'Выключить микрофон (Пробел)'} onClick={e => { e.stopPropagation(); toggleMute(); }}>
                    {isMuted ? <MicrophoneSlash size={16} /> : <Microphone size={16} />}
                  </button>
                  <button className={`di__call-btn di__call-btn--deaf ${isDeafened ? 'di__call-btn--on' : ''}`} title={isDeafened ? 'Включить звук' : 'Выключить звук'} onClick={e => { e.stopPropagation(); toggleDeafen(); }}>
                    {isDeafened ? <SpeakerSlash size={16} /> : <Headphones size={16} />}
                  </button>
                  <button className="di__call-btn di__call-btn--end" title="Завершить звонок" onClick={e => { e.stopPropagation(); onEndCall?.(); }}>
                    <PhoneDisconnect size={16} />
                  </button>
                </div>
              </motion.div>
            )}

            {/* INCOMING CALL */}
            {state === 'incoming' && incomingCall && (
              <motion.div key="incoming" className="di__inner" initial={contentEnter} animate={contentAnimate} exit={contentExit} transition={contentEnterTransition}>
                <Avatar user={{ username: incomingCall.callerName, avatar: incomingCall.callerAvatar }} size={42} className="di__in-ava" />
                <div className="di__in-col">
                  <span className="di__in-label">Входящий звонок</span>
                  <span className="di__in-name">{incomingCall.callerName}</span>
                </div>
                <div className="di__in-btns">
                  <button className="di__in-btn di__in-btn--no" title="Отклонить" onClick={e => { e.stopPropagation(); onDeclineCall?.(); }}><X size={16} weight="bold" /></button>
                  <button className="di__in-btn di__in-btn--yes" title="Принять звонок (Enter)" onClick={e => { e.stopPropagation(); onAcceptCall?.(); }}><Phone size={16} weight="fill" /></button>
                </div>
              </motion.div>
            )}

            {/* UPDATE */}
            {state === 'update' && updateData && (
              <motion.div key="update" className="di__inner" initial={contentEnter} animate={contentAnimate} exit={contentExit} transition={contentEnterTransition}>
                <Sparkle size={14} weight="fill" className="di__upd-icon" />
                <span className="di__upd-text">blesk {updateData.version}</span>
                <button className="di__upd-btn" onClick={e => e.stopPropagation()}>Обновить</button>
                <button className="di__upd-x" onClick={e => { e.stopPropagation(); dismissUpdate(); }}>&#10005;</button>
              </motion.div>
            )}

            {/* UPDATE PROGRESS */}
            {state === 'update_progress' && (
              <motion.div key="update_progress" className="di__inner" initial={contentEnter} animate={contentAnimate} exit={contentExit} transition={contentEnterTransition}>
                <ArrowClockwise size={14} className="di__upd-spin" />
                <span className="di__upd-text">Загрузка... {updateProgress}%</span>
                <div className="di__upd-bar"><div className="di__upd-fill" style={{ width: `${updateProgress}%` }} /></div>
              </motion.div>
            )}

            {/* RECORDING */}
            {state === 'recording' && (
              <motion.div key="recording" className="di__inner" initial={contentEnter} animate={contentAnimate} exit={contentExit} transition={contentEnterTransition}>
                <div className="di__rec-dot" />
                <div className="di__rec-bars">
                  {Array.from({ length: 5 }, (_, i) => (
                    <div key={i} className="di__rec-bar" style={{ animationDelay: `${i * 0.12}s` }} />
                  ))}
                </div>
                <span className="di__rec-timer">{formatTimer(recElapsed)}</span>
              </motion.div>
            )}

            {/* UPLOADING */}
            {state === 'uploading' && uploadingData && (
              <motion.div key="uploading" className="di__inner" initial={contentEnter} animate={contentAnimate} exit={contentExit} transition={contentEnterTransition}>
                <UploadSimple size={14} className="di__upload-icon" />
                <div className="di__upload-info">
                  <span className="di__upload-name">
                    {uploadingData.filename
                      ? (uploadingData.filename.length > 20
                        ? uploadingData.filename.slice(0, 18) + '\u2026'
                        : uploadingData.filename)
                      : 'Файл'}
                  </span>
                  <div className="di__upd-bar di__upload-bar">
                    <div className="di__upd-fill" style={{ width: `${uploadingData.percent ?? 0}%` }} />
                  </div>
                </div>
                <span className="di__upload-pct">{uploadingData.percent ?? 0}%</span>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </motion.div>
    </div>
  );
}
