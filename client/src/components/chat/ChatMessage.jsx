import './ChatMessage.css';

export default function ChatMessage({ message, isOwn, groupPosition, showTime }) {
  const time = new Date(message.createdAt).toLocaleTimeString('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
  });

  const side = isOwn ? 'chat-message--own' : 'chat-message--other';
  const group = `chat-message--${groupPosition || 'solo'}`;
  const pending = message.pending ? 'chat-message--pending' : '';

  return (
    <div className={`chat-message ${side} ${group}`}>
      <div className={`chat-message__bubble ${pending}`}>
        {message.text}
      </div>
      {showTime !== false && <div className="chat-message__time">{time}</div>}
    </div>
  );
}
