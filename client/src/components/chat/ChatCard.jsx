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

export default function ChatCard({ chat, isOnline, userStatus, isOpen, onClick, cardRef }) {
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
    <div
      className={`chat-row ${isOpen ? 'chat-row--open' : ''}`}
      onClick={onClick}
      ref={cardRef}
    >
      <div className="chat-row__avatar-wrap">
        {isGroup ? (
          <GroupAvatar participants={chat.participants} />
        ) : (
          <div
            className="chat-row__avatar"
            style={{ background: `linear-gradient(135deg, hsl(${hue}, 70%, 50%), hsl(${hue + 40}, 70%, 60%))` }}
          >
            {(user?.username || '?')[0].toUpperCase()}
          </div>
        )}
        {!isGroup && isOnline && userStatus !== 'invisible' && (
          <div className={`chat-row__online ${userStatus === 'dnd' ? 'chat-row__online--dnd' : ''}`} />
        )}
      </div>

      <div className="chat-row__info">
        <div className="chat-row__name">{displayName}</div>
        <div className="chat-row__preview">
          {chat.lastMessage ? (
            <>
              {isGroup && chat.lastMessage.username && (
                <span className="chat-row__author">{chat.lastMessage.username}: </span>
              )}
              {chat.lastMessage.text}
            </>
          ) : (
            <span className="chat-row__empty-msg">Нет сообщений</span>
          )}
        </div>
      </div>

      <div className="chat-row__meta">
        {chat.lastMessage && (
          <span className="chat-row__time">{formatTime(chat.lastMessage.createdAt)}</span>
        )}
        {chat.unreadCount > 0 && (
          <span className="chat-row__badge">{chat.unreadCount}</span>
        )}
      </div>
    </div>
  );
}
