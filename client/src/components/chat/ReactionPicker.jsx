import { useEffect, useRef } from 'react';
import './ReactionPicker.css';

const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🔥'];

export default function ReactionPicker({ onReact, onClose }) {
  const ref = useRef(null);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) {
        onClose?.();
      }
    };
    // Defer listener to avoid closing on the same click that opened it
    const timer = setTimeout(() => {
      window.addEventListener('mousedown', handleClickOutside);
    }, 0);
    return () => {
      clearTimeout(timer);
      window.removeEventListener('mousedown', handleClickOutside);
    };
  }, [onClose]);

  const handlePick = (emoji) => (e) => {
    e.stopPropagation();
    onReact?.(emoji);
    onClose?.();
  };

  return (
    <div ref={ref} className="reaction-picker reaction-picker--entering" onClick={(e) => e.stopPropagation()}>
      {QUICK_REACTIONS.map((emoji) => (
        <button
          key={emoji}
          className="reaction-picker__btn"
          onClick={handlePick(emoji)}
          title={emoji}
        >
          {emoji}
        </button>
      ))}
    </div>
  );
}
