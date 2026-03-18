import { useState, useEffect } from 'react';
import { Video } from 'lucide-react';
import './CallBanner.css';

export default function CallBanner({ activeCall, onJoin }) {
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    if (!activeCall?.startedAt) return;
    const interval = setInterval(() => {
      setElapsed(Math.floor((Date.now() - activeCall.startedAt) / 1000));
    }, 1000);
    return () => clearInterval(interval);
  }, [activeCall?.startedAt]);

  if (!activeCall) return null;

  const mins = Math.floor(elapsed / 60);
  const secs = elapsed % 60;
  const timeStr = `${mins}:${secs.toString().padStart(2, '0')}`;
  const count = activeCall.participants?.length || 0;

  return (
    <div className="call-banner" onClick={onJoin}>
      <div className="call-banner__indicator" />
      <div className="call-banner__info">
        <span className="call-banner__text">
          {activeCall.videoEnabled && <Video size={14} strokeWidth={1.5} className="call-banner__video-icon" />}
          Звонок в процессе
        </span>
        <span className="call-banner__meta">
          {count > 0 && `${count} участн. · `}{timeStr}
        </span>
      </div>
      <button className="call-banner__join">Присоединиться</button>
    </div>
  );
}
