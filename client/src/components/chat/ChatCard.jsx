import Glass from '../ui/Glass';
import './ChatCard.css';

export default function ChatCard({ chat, isOnline, isOpen, onClick, cardRef }) {
  const user = chat.otherUser;
  const hue = user?.hue || 0;

  const formatTime = (dateStr) => {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now - date;
    const mins = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);

    if (mins < 1) return 'сейчас';
    if (mins < 60) return `${mins} мин`;
    if (hours < 24) return `${hours}ч`;
    if (hours < 48) return 'вчера';
    return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
  };

  return (
    <Glass
      depth={2}
      radius={16}
      hover
      className={`chat-card ${isOpen ? 'chat-card--open' : ''}`}
      onClick={onClick}
      ref={cardRef}
    >
      <div className="chat-card__avatar-wrap">
        <div
          className="chat-card__avatar"
          style={{ background: `linear-gradient(135deg, hsl(${hue}, 70%, 50%), hsl(${hue + 40}, 70%, 60%))` }}
        />
        {isOnline && <div className="chat-card__online" />}
      </div>
      <div className="chat-card__name">{user?.username || chat.name}</div>
      {chat.lastMessage && (
        <div className="chat-card__preview">{chat.lastMessage.text}</div>
      )}
      <div className="chat-card__meta">
        {chat.lastMessage && (
          <span className="chat-card__time">{formatTime(chat.lastMessage.createdAt)}</span>
        )}
        {chat.unreadCount > 0 && (
          <span className="chat-card__badge">{chat.unreadCount}</span>
        )}
      </div>
    </Glass>
  );
}
