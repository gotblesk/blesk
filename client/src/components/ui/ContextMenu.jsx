import { useEffect, useLayoutEffect, useRef } from 'react';
import './ContextMenu.css';

export default function ContextMenu({ x, y, items, onClose }) {
  const menuRef = useRef(null);

  // Закрытие по клику вне или Escape
  useEffect(() => {
    function handleClick(e) {
      if (menuRef.current && !menuRef.current.contains(e.target)) onClose();
    }
    function handleKey(e) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('mousedown', handleClick);
    window.addEventListener('keydown', handleKey);
    return () => {
      window.removeEventListener('mousedown', handleClick);
      window.removeEventListener('keydown', handleKey);
    };
  }, [onClose]);

  // Позиционирование (useLayoutEffect — синхронно до paint, убирает визуальный флэш)
  useLayoutEffect(() => {
    if (!menuRef.current) return;
    const rect = menuRef.current.getBoundingClientRect();
    if (rect.right > window.innerWidth) {
      menuRef.current.style.left = `${window.innerWidth - rect.width - 8}px`;
    }
    if (rect.bottom > window.innerHeight) {
      menuRef.current.style.top = `${window.innerHeight - rect.height - 8}px`;
    }
  }, [x, y]);

  return (
    <div ref={menuRef} className="ctx-menu" style={{ left: x, top: y }}>
      {items.map((item, i) =>
        item.divider ? (
          <div key={i} className="ctx-menu__divider" />
        ) : (
          <button
            key={i}
            className={`ctx-menu__item ${item.danger ? 'ctx-menu__item--danger' : ''}`}
            onClick={() => { item.onClick(); onClose(); }}
          >
            {item.icon && <span className="ctx-menu__icon">{item.icon}</span>}
            <span>{item.label}</span>
            {item.shortcut && <span className="ctx-menu__shortcut">{item.shortcut}</span>}
          </button>
        )
      )}
    </div>
  );
}
