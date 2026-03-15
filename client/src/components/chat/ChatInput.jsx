import { useState, useRef } from 'react';
import './ChatInput.css';

export default function ChatInput({ onSend, onTypingStart, onTypingStop }) {
  const [text, setText] = useState('');
  const typingRef = useRef(false);
  const typingTimeoutRef = useRef(null);

  const handleChange = (e) => {
    setText(e.target.value);

    if (!typingRef.current && e.target.value) {
      typingRef.current = true;
      onTypingStart?.();
    }

    clearTimeout(typingTimeoutRef.current);
    typingTimeoutRef.current = setTimeout(() => {
      typingRef.current = false;
      onTypingStop?.();
    }, 2000);
  };

  const handleSend = () => {
    const trimmed = text.trim();
    if (!trimmed) return;
    onSend(trimmed);
    setText('');
    typingRef.current = false;
    onTypingStop?.();
    clearTimeout(typingTimeoutRef.current);
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="chat-input">
      <textarea
        className="chat-input__field"
        placeholder="Написать сообщение..."
        value={text}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        rows={1}
      />
      {text.trim() && (
        <button className="chat-input__send" onClick={handleSend}>
          &rarr;
        </button>
      )}
    </div>
  );
}
