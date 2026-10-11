import { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import './Tooltip.css';

const GAP = 8;

export default function Tooltip({ children, text, position = 'top', delay = 400 }) {
  const [visible, setVisible] = useState(false);
  const [coords, setCoords] = useState({ x: 0, y: 0 });
  const [placement, setPlacement] = useState(position);
  const triggerRef = useRef(null);
  const timerRef = useRef(null);

  const calcPosition = useCallback(() => {
    const el = triggerRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const cx = r.left + r.width / 2;

    let finalPos = position;
    let x = cx;
    let y;

    if (position === 'top') {
      y = r.top - GAP;
      if (y < 40) { finalPos = 'bottom'; y = r.bottom + GAP; }
    } else {
      y = r.bottom + GAP;
      if (y > window.innerHeight - 40) { finalPos = 'top'; y = r.top - GAP; }
    }

    // Не позволяем выйти за левый/правый край
    x = Math.max(60, Math.min(x, window.innerWidth - 60));

    setCoords({ x, y });
    setPlacement(finalPos);
  }, [position]);

  const handleEnter = useCallback(() => {
    timerRef.current = setTimeout(() => {
      calcPosition();
      setVisible(true);
    }, delay);
  }, [delay, calcPosition]);

  const handleLeave = useCallback(() => {
    clearTimeout(timerRef.current);
    setVisible(false);
  }, []);

  useEffect(() => () => clearTimeout(timerRef.current), []);

  if (!text) return children;

  return (
    <>
      <span
        ref={triggerRef}
        className="blsk-tooltip-trigger"
        onMouseEnter={handleEnter}
        onMouseLeave={handleLeave}
        onFocus={handleEnter}
        onBlur={handleLeave}
      >
        {children}
      </span>
      {visible && createPortal(
        <div
          className={`blsk-tooltip blsk-tooltip--${placement}`}
          style={{ left: coords.x, top: coords.y }}
          role="tooltip"
        >
          <span className="blsk-tooltip__text">{text}</span>
        </div>,
        document.body
      )}
    </>
  );
}
