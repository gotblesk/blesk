import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Phone, Video, PhoneOff } from 'lucide-react';
import './CallBanner.css';

export default function CallBanner({ activeCall, onJoin }) {
  const [elapsed, setElapsed] = useState(0);
  const [expanded, setExpanded] = useState(false);

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
  const isVideo = activeCall.videoEnabled;

  return (
    <div className="cb-zone">
      <motion.div
        className="cb-pill"
        onClick={() => setExpanded(e => !e)}
        animate={{ width: expanded ? 280 : 'auto' }}
        transition={{ type: 'spring', damping: 25, stiffness: 300 }}
        whileHover={{ scale: 1.02 }}
      >
        {/* Pulsing indicator */}
        <div className="cb-pill__pulse" />
        <div className="cb-pill__dot" />

        {/* Icon */}
        <div className="cb-pill__icon">
          {isVideo ? <Video size={13} /> : <Phone size={13} />}
        </div>

        {/* Timer — always visible */}
        <span className="cb-pill__time">{timeStr}</span>

        {/* Expanded content */}
        <AnimatePresence>
          {expanded && (
            <motion.div
              className="cb-pill__expand"
              initial={{ opacity: 0, width: 0 }}
              animate={{ opacity: 1, width: 'auto' }}
              exit={{ opacity: 0, width: 0 }}
              transition={{ duration: 0.2 }}
            >
              {count > 0 && <span className="cb-pill__count">{count}</span>}
              <motion.button className="cb-pill__join" onClick={e => { e.stopPropagation(); onJoin?.(); }} whileTap={{ scale: 0.9 }}>
                Присоединиться
              </motion.button>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>
    </div>
  );
}
