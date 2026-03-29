import { useState, useEffect, useRef, useCallback } from 'react';
import { ArrowBendUpLeft, SmileySticker, PencilSimple, Trash, ShareNetwork } from '@phosphor-icons/react';
import './MessageActionsPill.css';

const QUICK_EMOJIS = ['👍', '❤️', '😂', '😮', '😢', '🔥'];

export default function useMessageActions({ isOwn, onReply, onReact, onEdit, onDelete, onForward }) {
  const [open, setOpen] = useState(false);
  const [emojiOpen, setEmojiOpen] = useState(false);
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
    setEmojiOpen(false);
  }, []);

  // Закрыть при клике вне
  useEffect(() => {
    if (!open) return;
    const close = () => setTimeout(() => { setOpen(false); setEmojiOpen(false); }, 0);
    window.addEventListener('click', close);
    window.addEventListener('scroll', close, true);
    return () => {
      window.removeEventListener('click', close);
      window.removeEventListener('scroll', close, true);
    };
  }, [open]);

  const handleAction = (fn) => (e) => {
    e.stopPropagation();
    setOpen(false);
    setEmojiOpen(false);
    fn?.();
  };

  const handleEmojiToggle = (e) => {
    e.stopPropagation();
    setEmojiOpen((prev) => !prev);
  };

  const handleEmojiPick = (emoji) => (e) => {
    e.stopPropagation();
    setOpen(false);
    setEmojiOpen(false);
    onReact?.(emoji);
  };

  return {
    handleContextMenu,
    menu: open ? (
      <div
        ref={pillRef}
        className={`msg-actions-pill msg-actions-pill--open ${emojiOpen ? 'msg-actions-pill--with-emoji' : ''}`}
        style={{ left: pos.x, top: pos.y }}
        onClick={(e) => e.stopPropagation()}
      >
        {emojiOpen && (
          <div className="msg-actions-pill__emoji-row">
            {QUICK_EMOJIS.map((em) => (
              <button key={em} className="msg-actions-pill__emoji-btn" onClick={handleEmojiPick(em)}>
                {em}
              </button>
            ))}
          </div>
        )}
        <div className="msg-actions-pill__actions">
          <button className="msg-actions-pill__btn" onClick={handleAction(onReply)} title="Ответить">
            <ArrowBendUpLeft />
          </button>
          <button className="msg-actions-pill__btn" onClick={handleAction(onForward)} title="Переслать">
            <ShareNetwork />
          </button>
          <button className="msg-actions-pill__btn" onClick={handleEmojiToggle} title="Реакция">
            <SmileySticker />
          </button>
          {isOwn && (
            <>
              <button className="msg-actions-pill__btn" onClick={handleAction(onEdit)} title="Редактировать">
                <PencilSimple />
              </button>
              <button className="msg-actions-pill__btn msg-actions-pill__btn--danger" onClick={handleAction(onDelete)} title="Удалить">
                <Trash />
              </button>
            </>
          )}
        </div>
      </div>
    ) : null,
  };
}
