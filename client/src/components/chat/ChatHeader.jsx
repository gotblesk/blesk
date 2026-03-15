import './ChatHeader.css';

export default function ChatHeader({ chat, isOnline, typingUsernames }) {
  const user = chat.otherUser;
  const hue = user?.hue ?? (((user?.username?.charCodeAt(0) || 0) * 37) % 360);

  let statusText = isOnline ? 'онлайн' : 'офлайн';
  if (typingUsernames?.length) {
    statusText = `${typingUsernames.join(', ')} печатает...`;
  }

  return (
    <div className="chat-header">
      {/* Glass grip — подсказка pull-down */}
      <div className="chat-header__grip">
        <div className="chat-header__grip-bar" />
      </div>

      <div
        className="chat-header__avatar"
        style={{
          background: `linear-gradient(135deg, hsl(${hue}, 70%, 50%), hsl(${hue + 40}, 70%, 60%))`,
          boxShadow: isOnline ? `0 0 12px hsla(${hue}, 70%, 50%, 0.3)` : 'none',
        }}
      >
        {user?.username?.[0]?.toUpperCase() || '?'}
        {isOnline && <div className="chat-header__online-dot" />}
      </div>
      <div className="chat-header__info">
        <div className="chat-header__name">{user?.username || chat.name}</div>
        <div className={`chat-header__status ${isOnline ? 'chat-header__status--online' : ''} ${typingUsernames?.length ? 'chat-header__status--typing' : ''}`}>
          {statusText}
        </div>
      </div>
    </div>
  );
}
