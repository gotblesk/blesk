import { useState, useEffect, useRef } from 'react';
import './UnreadDivider.css';

export default function UnreadDivider({ onVisible }) {
  const [fading, setFading] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!ref.current) return;
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setTimeout(() => setFading(true), 5000);
        onVisible?.();
        observer.disconnect();
      }
    }, { threshold: 0.5 });
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, [onVisible]);

  return (
    <div
      ref={ref}
      className={`unread-divider ${fading ? 'unread-divider--fading' : ''}`}
    >
      <div className="unread-divider__line" />
      <span className="unread-divider__text">Новые сообщения</span>
      <div className="unread-divider__line" />
    </div>
  );
}
