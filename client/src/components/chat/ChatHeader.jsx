import './ChatHeader.css';

function GroupHeaderAvatar({ participants }) {
  const hues = (participants || []).slice(0, 3).map((p) => p.hue ?? 0);
  while (hues.length < 3) hues.push(hues.length * 120);

  return (
    <div className="chat-header__group-avatar">
      {hues.map((h, i) => (
        <div
          key={i}
          className={`chat-header__group-circle chat-header__group-circle--${i}`}
          style={{ background: `linear-gradient(135deg, hsl(${h}, 70%, 50%), hsl(${h + 40}, 70%, 60%))` }}
        />
      ))}
    </div>
  );
}

export default function ChatHeader({ chat, isOnline, userStatus, typingUsernames, onCall, onMembers }) {
  const isGroup = chat.type === 'group';
  const user = chat.otherUser;
  const hue = user?.hue ?? (((user?.username?.charCodeAt(0) || 0) * 37) % 360);

  let statusText;
  if (typingUsernames?.length) {
    statusText = `${typingUsernames.join(', ')} печатает...`;
  } else if (isGroup) {
    statusText = `${chat.memberCount ?? 0} участников`;
  } else {
    statusText = isOnline
      ? (userStatus === 'dnd' ? 'не беспокоить' : 'онлайн')
      : 'офлайн';
  }

  const displayName = isGroup ? chat.name : (user?.username || chat.name);

  return (
    <div className="chat-header">
      {/* Glass grip — подсказка pull-down */}
      <div className="chat-header__grip">
        <div className="chat-header__grip-bar" />
      </div>

      {isGroup ? (
        <GroupHeaderAvatar participants={chat.participants} />
      ) : (
        <div
          className="chat-header__avatar"
          style={{
            background: `linear-gradient(135deg, hsl(${hue}, 70%, 50%), hsl(${hue + 40}, 70%, 60%))`,
            boxShadow: isOnline ? `0 0 12px hsla(${hue}, 70%, 50%, 0.3)` : 'none',
          }}
        >
          {user?.username?.[0]?.toUpperCase() || '?'}
          {isOnline && userStatus !== 'invisible' && (
            <div
              className={`chat-header__online-dot ${userStatus === 'dnd' ? 'chat-header__online-dot--dnd' : ''}`}
            />
          )}
        </div>
      )}

      <div className="chat-header__info">
        <div className="chat-header__name">{displayName}</div>
        <div className={`chat-header__status ${!isGroup && isOnline ? 'chat-header__status--online' : ''} ${typingUsernames?.length ? 'chat-header__status--typing' : ''}`}>
          {statusText}
        </div>
      </div>

      <div className="chat-header__actions">
        {isGroup && onMembers && (
          <button className="chat-header__btn" onClick={onMembers} title="Участники">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
              <path d="M16 3.13a4 4 0 0 1 0 7.75" />
            </svg>
          </button>
        )}
        {onCall && (
          <button className="chat-header__btn" onClick={onCall} title="Позвонить">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
            </svg>
          </button>
        )}
      </div>
    </div>
  );
}
