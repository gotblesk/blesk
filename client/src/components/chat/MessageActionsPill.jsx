import { useState, useEffect, useRef, useCallback } from 'react';
import { CornerUpLeft, SmilePlus, Pencil, Trash2 } from 'lucide-react';
import './MessageActionsPill.css';

export default function useMessageActions({ isOwn, onReply, onReact, onEdit, onDelete }) {
  const [open, setOpen] = useState(false);
  const [pos, setPos] = useState({ x: 0, y: 0 });
  const pillRef = useRef(null);

  const handleContextMenu = useCallback((e) => {
    e.preventDefault();
    e.stopPropagation();

    const rect = e.currentTarget.getBoundingClientRect();
    setPos({
      x: e.clientX - rect.left,
      y: e.clientY - rect.top,
    });
    setOpen(true);
  }, []);

  // Закрыть при клике вне
  useEffect(() => {
    if (!open) return;
    const close = () => setOpen(false);
    window.addEventListener('click', close);
    window.addEventListener('contextmenu', close);
    window.addEventListener('scroll', close, true);
    return () => {
      window.removeEventListener('click', close);
      window.removeEventListener('contextmenu', close);
      window.removeEventListener('scroll', close, true);
    };
  }, [open]);

  const handleAction = (fn) => (e) => {
    e.stopPropagation();
    setOpen(false);
    fn?.();
  };

  return {
    handleContextMenu,
    menu: open ? (
      <div
        ref={pillRef}
        className="msg-actions-pill msg-actions-pill--open"
        style={{ left: pos.x, top: pos.y }}
        onClick={(e) => e.stopPropagation()}
      >
        <button className="msg-actions-pill__btn" onClick={handleAction(onReply)} title="Ответить">
          <CornerUpLeft />
        </button>
        <button className="msg-actions-pill__btn" onClick={handleAction(onReact)} title="Реакция">
          <SmilePlus />
        </button>
        {isOwn && (
          <>
            <button className="msg-actions-pill__btn" onClick={handleAction(onEdit)} title="Редактировать">
              <Pencil />
            </button>
            <button className="msg-actions-pill__btn msg-actions-pill__btn--danger" onClick={handleAction(onDelete)} title="Удалить">
              <Trash2 />
            </button>
          </>
        )}
      </div>
    ) : null,
  };
}
