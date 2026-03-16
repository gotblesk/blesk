import { useState, useRef } from 'react';
import './ChatInput.css';

export default function ChatInput({ onSend, onTypingStart, onTypingStop, replyTo, onCancelReply }) {
  const [text, setText] = useState('');
  const typingRef = useRef(false);
  const typingTimeoutRef = useRef(null);
  const inputRef = useRef(null);

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
    if (e.key === 'Escape' && replyTo) {
      onCancelReply?.();
    }
  };

  return (
    <div className="chat-input">
      {/* Превью ответа */}
      {replyTo && (
        <div className="chat-input__reply-preview">
          <div className="chat-input__reply-bar" />
          <div className="chat-input__reply-info">
            <span className="chat-input__reply-author">
              {replyTo.user?.username || replyTo.username || 'Сообщение'}
            </span>
            <span className="chat-input__reply-text">
              {replyTo.text?.slice(0, 60)}
              {replyTo.text?.length > 60 ? '...' : ''}
            </span>
          </div>
          <button className="chat-input__reply-cancel" onClick={onCancelReply}>&times;</button>
        </div>
      )}

      <div className="chat-input__row">
        <textarea
          ref={inputRef}
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
    </div>
  );
}
