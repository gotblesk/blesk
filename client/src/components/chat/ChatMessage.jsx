import { useState } from 'react';
import { Pin, Lock } from 'lucide-react';
import MediaMessage from './MediaMessage';
import './ChatMessage.css';

export default function ChatMessage({ message, isOwn, groupPosition, showTime, onReply, onImageClick }) {
  const [showActions, setShowActions] = useState(false);

  const time = new Date(message.createdAt).toLocaleTimeString('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
  });

  const side = isOwn ? 'chat-message--own' : 'chat-message--other';
  const group = `chat-message--${groupPosition || 'solo'}`;
  const pending = message.pending ? 'chat-message--pending' : '';
  const pinned = message.pinned ? 'chat-message--pinned' : '';

  return (
    <div
      className={`chat-message ${side} ${group} ${pinned}`}
      onMouseEnter={() => setShowActions(true)}
      onMouseLeave={() => setShowActions(false)}
    >
      {/* Цитата ответа */}
      {message.replyTo && (
        <div className="chat-message__reply-quote">
          <div className="chat-message__reply-bar" />
          <div className="chat-message__reply-content">
            <span className="chat-message__reply-author">
              {message.replyTo.user?.username || 'Сообщение'}
            </span>
            <span className="chat-message__reply-text">
              {message.replyTo.text?.slice(0, 80)}
              {message.replyTo.text?.length > 80 ? '...' : ''}
            </span>
          </div>
        </div>
      )}

      <div className={`chat-message__bubble ${pending}`}>
        {message.type === 'system' ? (
          <span className="chat-message__system">{message.text}</span>
        ) : (
          message.text
        )}
        {message.pinned && <span className="chat-message__pin-icon"><Pin size={12} strokeWidth={1.5} /></span>}
        {message.attachments?.length > 0 && (
          <MediaMessage attachments={message.attachments} onImageClick={onImageClick} />
        )}
      </div>

      {showTime !== false && (
        <div className="chat-message__time">
          {time}
          {message.encrypted && <Lock size={10} strokeWidth={1.5} className="chat-message__e2e" title="Зашифровано" />}
        </div>
      )}

      {/* Кнопка ответа при hover */}
      {showActions && message.type !== 'system' && (
        <div className="chat-message__actions">
          <button className="chat-message__action-btn" onClick={onReply} title="Ответить">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="9 17 4 12 9 7" />
              <path d="M20 18v-2a4 4 0 0 0-4-4H4" />
            </svg>
          </button>
        </div>
      )}
    </div>
  );
}
