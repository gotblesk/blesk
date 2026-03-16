import Glass from '../ui/Glass';
import './ChatCard.css';

function GroupAvatar({ participants }) {
  const hues = (participants || []).slice(0, 3).map((p) => p.hue ?? 0);
  while (hues.length < 3) hues.push(hues.length * 120);

  return (
    <div className="chat-card__group-avatar">
      {hues.map((h, i) => (
        <div
          key={i}
          className={`chat-card__group-circle chat-card__group-circle--${i}`}
          style={{ background: `linear-gradient(135deg, hsl(${h}, 70%, 50%), hsl(${h + 40}, 70%, 60%))` }}
        />
      ))}
    </div>
  );
}

export default function ChatCard({ chat, isOnline, isOpen, onClick, cardRef }) {
  const isGroup = chat.type === 'group';
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

  const displayName = isGroup ? chat.name : (user?.username || chat.name);

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
        {isGroup ? (
          <GroupAvatar participants={chat.participants} />
        ) : (
          <div
            className="chat-card__avatar"
            style={{ background: `linear-gradient(135deg, hsl(${hue}, 70%, 50%), hsl(${hue + 40}, 70%, 60%))` }}
          />
        )}
        {!isGroup && isOnline && <div className="chat-card__online" />}
      </div>

      <div className="chat-card__name">{displayName}</div>

      {isGroup && chat.memberCount != null && (
        <div className="chat-card__members">{chat.memberCount} уч.</div>
      )}

      {chat.lastMessage && (
        <div className="chat-card__preview">
          {isGroup && chat.lastMessage.username && (
            <span className="chat-card__preview-author">{chat.lastMessage.username}: </span>
          )}
          {chat.lastMessage.text}
        </div>
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
