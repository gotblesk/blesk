import { useEffect, useRef, useCallback } from 'react';
import { Maximize, Minimize } from 'lucide-react';
import { useVoiceStore } from '../../store/voiceStore';
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

  // Разделяем потоки на экран и камеры
  const screenEntries = [];
  const cameraEntries = [];
  for (const [userId, streams] of Object.entries(videoStreams)) {
    if (streams.screen) screenEntries.push({ userId, stream: streams.screen });
    if (streams.camera) cameraEntries.push({ userId, stream: streams.camera });
  }

  const getUserName = (userId) => {
    const p = participants?.find((p) => p.id === userId || p.userId === userId);
    return p?.username || 'Участник';
  };

  return (
    <div className="video-grid">
      {/* Экран — на всю ширину */}
      {screenEntries.map(({ userId, stream }) => (
        <VideoTile
          key={`screen-${userId}`}
          stream={stream}
          label={`${getUserName(userId)} — экран`}
          className="video-grid__screen"
        />
      ))}

      {/* Камеры участников */}
      <div className="video-grid__cameras">
        {cameraEntries.map(({ userId, stream }) => (
          <VideoTile
            key={`cam-${userId}`}
            stream={stream}
            label={getUserName(userId)}
          />
        ))}
      </div>

      {/* Локальная камера — PiP */}
      {localCameraStream && (
        <div className="video-grid__local-pip">
          <video
            ref={localVideoRef}
            autoPlay
            muted
            playsInline
            className="video-grid__video"
          />
          <span className="video-grid__label">Вы</span>
        </div>
      )}
    </div>
  );
}

function VideoTile({ stream, label, className = '' }) {
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

  const toggleFullscreen = useCallback(() => {
    const el = tileRef.current;
    if (!el) return;
    if (document.fullscreenElement === el) {
      document.exitFullscreen().catch(() => {});
    } else {
      el.requestFullscreen().catch(() => {});
    }
  }, []);

  return (
    <div ref={tileRef} className={`video-grid__tile ${className}`}>
      <video
        ref={videoRef}
        autoPlay
        playsInline
        className="video-grid__video"
      />
      <span className="video-grid__label">{label}</span>
      <button className="video-grid__fullscreen" onClick={toggleFullscreen} title="Полный экран">
        <Maximize size={16} strokeWidth={1.5} />
      </button>
    </div>
  );
}
