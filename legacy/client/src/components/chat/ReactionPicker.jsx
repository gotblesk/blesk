import { useState, useEffect, useRef } from 'react';
import Picker from '@emoji-mart/react';
import data from '@emoji-mart/data';
import './ReactionPicker.css';

const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🔥'];

export default function ReactionPicker({ onReact, onClose }) {
  const ref = useRef(null);
  const [showFullPicker, setShowFullPicker] = useState(false);

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

  const handleFullPickerSelect = (emoji) => {
    onReact?.(emoji.native);
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
      <button
        className="reaction-picker__btn"
        onClick={(e) => { e.stopPropagation(); setShowFullPicker(true); }}
        title="Ещё"
      >
        +
      </button>
      {showFullPicker && (
        <div className="reaction-picker__full" onClick={(e) => e.stopPropagation()}>
          <Picker data={data} onEmojiSelect={handleFullPickerSelect} theme="dark" locale="ru" previewPosition="none" skinTonePosition="none" />
        </div>
      )}
    </div>
  );
}
