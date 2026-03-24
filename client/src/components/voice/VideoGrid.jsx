import { useEffect, useRef, useCallback } from 'react';
import { Maximize } from 'lucide-react';
import { useVoiceStore } from '../../store/voiceStore';
import { getAvatarHue, getAvatarColor } from '../../utils/avatar';
import './VideoGrid.css';

export default function VideoGrid({ participants }) {
  const videoStreams = useVoiceStore((s) => s.videoStreams);
  const localCameraStream = useVoiceStore((s) => s.localCameraStream);
  const localVideoRef = useRef(null);

  // Локальная камера (PiP)
  useEffect(() => {
    if (localVideoRef.current) {
      localVideoRef.current.srcObject = localCameraStream || null;
    }
  }, [localCameraStream]);

  // Камеры участников (без screen share — screen share теперь в VoiceRoom)
  const cameraEntries = [];
  for (const [userId, streams] of Object.entries(videoStreams)) {
    if (streams.camera) cameraEntries.push({ userId, stream: streams.camera });
  }

  const getUserName = (userId) => {
    const p = participants?.find((p) => p.id === userId || p.userId === userId);
    return p?.username || 'Участник';
  };

  const getPeer = (userId) => {
    return participants?.find((p) => p.id === userId || p.userId === userId);
  };

  // Не рендерим если нет камер
  if (cameraEntries.length === 0 && !localCameraStream) return null;

  const colClass = cameraEntries.length <= 1 ? 'vg__cameras--1'
    : cameraEntries.length === 2 ? 'vg__cameras--2'
    : 'vg__cameras--3';

  return (
    <div className="vg" style={{ position: 'relative' }}>
      {/* Камеры участников */}
      {cameraEntries.length > 0 && (
        <div className={`vg__cameras ${colClass}`}>
          {cameraEntries.map(({ userId, stream }) => (
            <CameraTile
              key={`cam-${userId}`}
              stream={stream}
              name={getUserName(userId)}
              peer={getPeer(userId)}
            />
          ))}
        </div>
      )}

      {/* Локальная камера — PiP */}
      {localCameraStream && (
        <div className="vg__pip">
          <video
            ref={localVideoRef}
            autoPlay
            muted
            playsInline
          />
          <span className="vg__pip-label">Вы</span>
        </div>
      )}
    </div>
  );
}

function CameraTile({ stream, name, peer }) {
  const videoRef = useRef(null);
  const tileRef = useRef(null);

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
    const el = tileRef.current;
    if (!el) return;
    if (document.fullscreenElement === el) {
      document.exitFullscreen().catch(() => {});
    } else {
      el.requestFullscreen().catch(() => {});
    }
  }, []);

  return (
    <div ref={tileRef} className="vg__cam-tile">
      <video ref={videoRef} autoPlay playsInline />
      <span className="vg__tile-label">{name}</span>
      <button className="vg__tile-fs" onClick={toggleFs} title="Полный экран">
        <Maximize size={12} strokeWidth={1.5} />
      </button>
    </div>
  );
}
