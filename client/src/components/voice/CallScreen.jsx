import { useState, useEffect, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Mic, MicOff, Volume2, VolumeX, Video, VideoOff, PhoneOff, Maximize2, Minimize2, Monitor, MonitorOff, Settings } from 'lucide-react';
import { useSettingsStore } from '../../store/settingsStore';
import Avatar from '../ui/Avatar';
import { getAvatarHue } from '../../utils/avatar';
import API_URL from '../../config';
import './CallScreen.css';

const COMPACT_W = 280;
const MEDIUM_W = 360;

export default function CallScreen({
  call, user, muted, deafened,
  onToggleMute, onToggleDeafen, onToggleVideo, onToggleScreenShare, onEndCall,
  cameraOn, screenShareOn,
  localVideoStream, remoteVideoStream, localScreenStream, remoteScreenStream,
}) {
  const [elapsed, setElapsed] = useState(0);
  const [size, setSize] = useState({ w: 380, h: 520 });
  const [pos, setPos] = useState({ x: 0, y: 0 });
  const [initialized, setInitialized] = useState(false);
  const [isMax, setIsMax] = useState(false);
  const prevSize = useRef(null);
  const prevPos = useRef(null);
  const winRef = useRef(null);
  const dragRef = useRef({ active: false });
  const resizeRef = useRef({ active: false });
  const [qualityOpen, setQualityOpen] = useState(false);

  // Video refs
  const localVideoRef = useRef(null);
  const remoteVideoRef = useRef(null);
  const screenRef = useRef(null);

  // Timer
  useEffect(() => {
    if (!call?.startedAt) return;
    const iv = setInterval(() => setElapsed(Math.floor((Date.now() - call.startedAt) / 1000)), 1000);
    return () => clearInterval(iv);
  }, [call?.startedAt]);

  // Center on mount
  useEffect(() => {
    if (initialized) return;
    setPos({ x: Math.round((window.innerWidth - size.w) / 2), y: Math.round((window.innerHeight - size.h) / 2) });
    setInitialized(true);
  }, [initialized, size]);

  // Attach video streams
  useEffect(() => {
    if (localVideoRef.current && localVideoStream) {
      localVideoRef.current.srcObject = localVideoStream;
    }
  }, [localVideoStream, cameraOn]);

  useEffect(() => {
    if (remoteVideoRef.current && remoteVideoStream) {
      remoteVideoRef.current.srcObject = remoteVideoStream;
    }
  }, [remoteVideoStream]);

  useEffect(() => {
    if (screenRef.current) {
      const stream = remoteScreenStream || localScreenStream;
      if (stream) screenRef.current.srcObject = stream;
    }
  }, [localScreenStream, remoteScreenStream, screenShareOn]);

  // Maximize/restore
  const toggleMax = useCallback(() => {
    if (!isMax) {
      prevSize.current = { ...size }; prevPos.current = { ...pos };
      setSize({ w: window.innerWidth, h: window.innerHeight }); setPos({ x: 0, y: 0 });
    } else if (prevSize.current) {
      setSize(prevSize.current); setPos(prevPos.current || { x: 100, y: 100 });
    }
    setIsMax(m => !m);
  }, [isMax, size, pos]);

  // Drag
  const dDown = useCallback((e) => {
    if (isMax || e.target.closest('.cs__btn') || e.target.closest('.cs__max')) return;
    dragRef.current = { active: true, sx: e.clientX, sy: e.clientY, wx: pos.x, wy: pos.y };
    e.currentTarget.setPointerCapture(e.pointerId); e.preventDefault();
  }, [pos, isMax]);
  const dMove = useCallback((e) => { if (!dragRef.current.active) return; const d = dragRef.current; setPos({ x: d.wx + e.clientX - d.sx, y: d.wy + e.clientY - d.sy }); }, []);
  const dUp = useCallback(() => { dragRef.current.active = false; }, []);

  // Resize
  const rDown = useCallback((e, edge) => {
    if (isMax) return; e.stopPropagation();
    resizeRef.current = { active: true, edge, sx: e.clientX, sy: e.clientY, wx: pos.x, wy: pos.y, sw: size.w, sh: size.h };
    e.currentTarget.setPointerCapture(e.pointerId); e.preventDefault();
  }, [pos, size, isMax]);
  const rMove = useCallback((e) => {
    const r = resizeRef.current; if (!r.active) return;
    const dx = e.clientX - r.sx, dy = e.clientY - r.sy;
    let { wx: x, wy: y, sw: w, sh: h } = r;
    if (r.edge.includes('e')) w = Math.max(240, r.sw + dx);
    if (r.edge.includes('w')) { w = Math.max(240, r.sw - dx); x = r.wx + (r.sw - w); }
    if (r.edge.includes('s')) h = Math.max(200, r.sh + dy);
    if (r.edge.includes('n')) { h = Math.max(200, r.sh - dy); y = r.wy + (r.sh - h); }
    setPos({ x, y }); setSize({ w, h });
  }, []);
  const rUp = useCallback(() => { resizeRef.current.active = false; }, []);

  if (!call) return null;

  const mins = Math.floor(elapsed / 60);
  const secs = elapsed % 60;
  const timeStr = `${mins}:${secs.toString().padStart(2, '0')}`;
  const callerName = call.callerName || user?.username || 'Звонок';
  const hue = user ? getAvatarHue(user) : getAvatarHue({ username: callerName });
  const isCompact = size.w < COMPACT_W;
  const isFull = size.w >= MEDIUM_W;
  const avatarUrl = user?.avatar ? `${API_URL}/uploads/avatars/${user.avatar}` : null;

  // Determine what's shown in the main area
  const hasScreen = screenShareOn || remoteScreenStream;
  const hasRemoteVideo = !!remoteVideoStream;
  const hasLocalVideo = cameraOn && !!localVideoStream;

  const EDGES = ['n', 'ne', 'e', 'se', 's', 'sw', 'w', 'nw'];

  return (
    <motion.div
      ref={winRef}
      className={`cs ${isMax ? 'cs--max' : ''} ${isCompact ? 'cs--compact' : ''}`}
      style={{ left: pos.x, top: pos.y, width: size.w, height: size.h }}
      initial={{ scale: 0.8, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      exit={{ scale: 0.8, opacity: 0 }}
      transition={{ type: 'spring', damping: 22, stiffness: 300 }}
    >
      {!isMax && EDGES.map(edge => (
        <div key={edge} className={`cs__edge cs__edge--${edge}`} onPointerDown={e => rDown(e, edge)} onPointerMove={rMove} onPointerUp={rUp} />
      ))}

      {/* Background */}
      {!hasScreen && !hasRemoteVideo && (
        <>
          {avatarUrl ? <div className="cs__bg" style={{ backgroundImage: `url(${avatarUrl})` }} /> : <div className="cs__bg cs__bg--gradient" style={{ background: `linear-gradient(135deg, hsl(${hue},70%,30%), hsl(${hue},60%,15%))` }} />}
          <div className="cs__overlay" />
          {isFull && <div className="cs__particles" />}
        </>
      )}

      {/* Header */}
      <div className="cs__header" onPointerDown={dDown} onPointerMove={dMove} onPointerUp={dUp}>
        <div className="cs__status">
          <div className="cs__status-dot" />
          <span>{call.connecting ? 'Соединение...' : hasScreen ? 'Демонстрация' : 'Звонок'}</span>
        </div>
        <div className="cs__header-r">
          <span className="cs__time">{timeStr}</span>
          {!isCompact && (
            <button className="cs__max" onClick={() => setQualityOpen(q => !q)} title="Качество видео">
              <Settings size={13} />
            </button>
          )}
          <button className="cs__max" onClick={toggleMax}>{isMax ? <Minimize2 size={13} /> : <Maximize2 size={13} />}</button>
        </div>
      </div>

      {/* Quality popup */}
      <AnimatePresence>
        {qualityOpen && <QualityPanel onClose={() => setQualityOpen(false)} />}
      </AnimatePresence>

      {/* Main content area */}
      <div className="cs__body">
        {/* Screen share — takes main area */}
        {hasScreen && (
          <div className="cs__screen-zone">
            <video ref={screenRef} className="cs__screen-video" autoPlay playsInline muted={!!localScreenStream} />
          </div>
        )}

        {/* Remote video — takes main area if no screen share */}
        {!hasScreen && hasRemoteVideo && (
          <div className="cs__video-zone">
            <video ref={remoteVideoRef} className="cs__remote-video" autoPlay playsInline />
          </div>
        )}

        {/* Avatar — shown when no video/screen */}
        {!hasScreen && !hasRemoteVideo && (
          <>
            <div className={`cs__ava-zone ${isCompact ? 'cs__ava-zone--sm' : ''}`}>
              {isFull && (
                <>
                  <div className="cs__ring cs__ring--1" style={{ borderColor: `hsla(${hue},60%,50%,0.08)` }} />
                  <div className="cs__ring cs__ring--2" style={{ borderColor: `hsla(${hue},60%,50%,0.05)` }} />
                  <div className="cs__ring cs__ring--3" style={{ borderColor: `hsla(${hue},60%,50%,0.03)` }} />
                </>
              )}
              <div className="cs__ava" style={{ width: isCompact ? 48 : isFull ? 110 : 72, height: isCompact ? 48 : isFull ? 110 : 72 }}>
                <Avatar user={user || { username: callerName }} size={isCompact ? 48 : isFull ? 110 : 72} />
              </div>
            </div>

            {isFull && !call.connecting && (
              <div className="cs__wave">
                {[6, 12, 8, 18, 10, 14, 6].map((_, i) => (
                  <div key={i} className="cs__wave-bar" style={{ animationDelay: `${i * 0.1}s` }} />
                ))}
              </div>
            )}
          </>
        )}

        {/* Name + label — always visible */}
        <div className="cs__name">{callerName}</div>
        {!isCompact && <div className="cs__label">{hasScreen ? 'Демонстрация экрана' : call.videoEnabled ? 'Видеозвонок' : 'Голосовой звонок'}</div>}

        {/* PiP — local camera when screen is shared or remote video is shown */}
        {hasLocalVideo && (hasScreen || hasRemoteVideo) && (
          <div className="cs__pip">
            <video ref={localVideoRef} className="cs__pip-video" autoPlay playsInline muted />
          </div>
        )}

        {/* PiP — local camera as main video (no remote) */}
        {hasLocalVideo && !hasScreen && !hasRemoteVideo && (
          <div className="cs__local-preview">
            <video ref={localVideoRef} className="cs__local-video" autoPlay playsInline muted />
          </div>
        )}
      </div>

      {/* Actions */}
      <div className={`cs__actions ${isCompact ? 'cs__actions--compact' : ''}`}>
        <motion.button className={`cs__btn ${muted ? 'cs__btn--active' : ''}`} onClick={onToggleMute} whileTap={{ scale: 0.88 }} title={muted ? 'Вкл. микрофон' : 'Выкл. микрофон'}>
          {muted ? <MicOff size={18} /> : <Mic size={18} />}
          {!isCompact && <span className="cs__btn-label">{muted ? 'Вкл.' : 'Микро'}</span>}
        </motion.button>

        {!isCompact && (
          <motion.button className={`cs__btn ${deafened ? 'cs__btn--active' : ''}`} onClick={onToggleDeafen} whileTap={{ scale: 0.88 }}>
            {deafened ? <VolumeX size={18} /> : <Volume2 size={18} />}
            <span className="cs__btn-label">Динамик</span>
          </motion.button>
        )}

        <motion.button className="cs__btn cs__btn--end" onClick={onEndCall} whileTap={{ scale: 0.88 }}>
          <PhoneOff size={isCompact ? 16 : 20} />
        </motion.button>

        {!isCompact && (
          <>
            <motion.button className={`cs__btn ${cameraOn ? 'cs__btn--active' : ''}`} onClick={onToggleVideo} whileTap={{ scale: 0.88 }} title="Видео">
              {cameraOn ? <VideoOff size={18} /> : <Video size={18} />}
              <span className="cs__btn-label">Видео</span>
            </motion.button>

            <motion.button className={`cs__btn ${screenShareOn ? 'cs__btn--active' : ''}`} onClick={onToggleScreenShare} whileTap={{ scale: 0.88 }} title="Демонстрация">
              {screenShareOn ? <MonitorOff size={18} /> : <Monitor size={18} />}
              <span className="cs__btn-label">Экран</span>
            </motion.button>
          </>
        )}
      </div>
    </motion.div>
  );
}

// ═══════ QUALITY PANEL ═══════
const RESOLUTIONS = ['480p', '720p', '1080p', '1440p'];
const FPS_OPTIONS = [30, 60];

function QualityPanel({ onClose }) {
  const cameraResolution = useSettingsStore(s => s.cameraResolution);
  const cameraFps = useSettingsStore(s => s.cameraFps);
  const screenResolution = useSettingsStore(s => s.screenResolution);
  const screenFps = useSettingsStore(s => s.screenFps);

  const setQ = (key, val) => {
    useSettingsStore.setState({ [key]: val });
    try {
      const saved = JSON.parse(localStorage.getItem('blesk-settings') || '{}');
      saved[key] = val;
      localStorage.setItem('blesk-settings', JSON.stringify(saved));
    } catch {}
  };

  return (
    <motion.div
      className="cs__quality"
      initial={{ opacity: 0, y: -8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -8 }}
      transition={{ duration: 0.15 }}
      onClick={e => e.stopPropagation()}
    >
      <div className="cs__quality-head">
        <span>Качество видео</span>
        <button className="cs__quality-x" onClick={onClose}>&times;</button>
      </div>

      <div className="cs__quality-sec">
        <span className="cs__quality-label">Камера</span>
        <div className="cs__quality-row">
          {RESOLUTIONS.map(r => (
            <button key={r} className={`cs__qo ${cameraResolution === r ? 'cs__qo--on' : ''}`} onClick={() => setQ('cameraResolution', r)}>{r}</button>
          ))}
        </div>
        <div className="cs__quality-row">
          {FPS_OPTIONS.map(f => (
            <button key={f} className={`cs__qo ${cameraFps === f ? 'cs__qo--on' : ''}`} onClick={() => setQ('cameraFps', f)}>{f}fps</button>
          ))}
        </div>
      </div>

      <div className="cs__quality-sec">
        <span className="cs__quality-label">Демонстрация</span>
        <div className="cs__quality-row">
          {RESOLUTIONS.filter(r => r !== '480p').map(r => (
            <button key={r} className={`cs__qo ${screenResolution === r ? 'cs__qo--on' : ''}`} onClick={() => setQ('screenResolution', r)}>{r}</button>
          ))}
        </div>
        <div className="cs__quality-row">
          {FPS_OPTIONS.map(f => (
            <button key={f} className={`cs__qo ${screenFps === f ? 'cs__qo--on' : ''}`} onClick={() => setQ('screenFps', f)}>{f}fps</button>
          ))}
        </div>
      </div>
    </motion.div>
  );
}
