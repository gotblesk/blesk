import { useState, useEffect, useRef, useCallback } from 'react';
import './TitleBar.css';

export default function TitleBar() {
  const [maximized, setMaximized] = useState(false);
  const isDragging = useRef(false);
  const dragRef = useRef(null);

  useEffect(() => {
    window.blesk?.window.onMaximizeChange?.((val) => setMaximized(val));
  }, []);

  // Ручное перетаскивание окна
  const handleMouseDown = useCallback((e) => {
    // Только левая кнопка мыши, только на drag-зоне
    if (e.button !== 0) return;
    isDragging.current = true;
    window.blesk?.window.startDrag(e.screenX - window.screenX, e.screenY - window.screenY);

    let rafId = null;
    const handleMouseMove = (ev) => {
      if (!isDragging.current) return;
      const sx = ev.screenX, sy = ev.screenY;
      if (rafId) return; // throttle to 1 per frame
      rafId = requestAnimationFrame(() => {
        rafId = null;
        if (isDragging.current) window.blesk?.window.dragging(sx, sy);
      });
    };

    const handleMouseUp = () => {
      isDragging.current = false;
      if (rafId) { cancelAnimationFrame(rafId); rafId = null; }
      window.blesk?.window.stopDrag();
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
    };

    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
  }, []);

  // Двойной клик = maximize/unmaximize
  const handleDoubleClick = useCallback(() => {
    window.blesk?.window.maximize();
  }, []);

  return (
    <div className="titlebar" ref={dragRef} onMouseDown={handleMouseDown} onDoubleClick={handleDoubleClick}>
      <span className="titlebar__title">blesk</span>
      <div className="titlebar__controls">
        <button className="titlebar__btn" onMouseDown={e => e.stopPropagation()} onClick={() => window.blesk?.window.minimize()} title="Свернуть" aria-label="Свернуть">
          <svg width="10" height="1" viewBox="0 0 10 1">
            <rect width="10" height="1" fill="currentColor" rx="0.5"/>
          </svg>
        </button>
        <button className="titlebar__btn" onMouseDown={e => e.stopPropagation()} onClick={() => window.blesk?.window.maximize()} title={maximized ? 'Восстановить' : 'Развернуть'} aria-label={maximized ? 'Восстановить' : 'Развернуть'}>
          {maximized ? (
            <svg width="11" height="11" viewBox="0 0 11 11" fill="none">
              <rect x="3" y="0.5" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1"/>
              <rect x="0.5" y="3" width="7" height="7" rx="1.5" stroke="currentColor" strokeWidth="1"/>
            </svg>
          ) : (
            <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
              <rect x="0.5" y="0.5" width="9" height="9" rx="1.5" stroke="currentColor" strokeWidth="1"/>
            </svg>
          )}
        </button>
        <button className="titlebar__btn titlebar__btn--close" onMouseDown={e => e.stopPropagation()} onClick={() => window.blesk?.window.close()} title="Закрыть" aria-label="Закрыть">
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
            <path d="M1 1L9 9M9 1L1 9" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"/>
          </svg>
        </button>
      </div>
    </div>
  );
}
