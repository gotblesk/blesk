import { memo } from 'react';
import Avatar from '../ui/Avatar';
import { useChatStore } from '../../store/chatStore';
import './UserBadge.css';

const SIZES = {
  sm: { avatar: 24, name: 13, sub: 0 },
  md: { avatar: 36, name: 15, sub: 13 },
  lg: { avatar: 48, name: 17, sub: 14 },
};

// H1: stable empty object to avoid new {} on every render
const EMPTY = {};

function UserBadge({
  user,
  userId,
  size = 'md',
  showStatus = true,
  showCustomStatus = false,
  clickable = true,
  subtitle,
  onClick,
  className = '',
}) {
  const s = SIZES[size] || SIZES.md;
  const userStatuses = useChatStore((state) => showStatus ? state.userStatuses : EMPTY);
  const customStatuses = useChatStore((state) => showCustomStatus ? state.customStatuses : EMPTY);

  const id = userId || user?.id;
  const isOnline = showStatus && userStatuses[id] && userStatuses[id] !== 'invisible';
  const userStatus = showStatus ? userStatuses[id] : undefined;
  const customStatus = showCustomStatus ? customStatuses[id] : undefined;

  const displayName = user?.displayName || user?.username || 'Unknown';
  const secondLine = subtitle || (showCustomStatus && customStatus) || null;

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onClick?.();
    }
  };

  return (
    <div
      className={`user-badge user-badge--${size} ${clickable ? 'user-badge--clickable' : ''} ${className}`}
      onClick={clickable ? onClick : undefined}
      role={clickable ? 'button' : undefined}
      tabIndex={clickable ? 0 : undefined}
      onKeyDown={clickable ? handleKeyDown : undefined}
      title={displayName}
      data-testid="user-badge"
    >
      <Avatar
        user={user}
        size={s.avatar}
        showOnline={showStatus}
        isOnline={isOnline}
        userStatus={userStatus}
      />
      <div className="user-badge__text">
        <span className="user-badge__name" style={{ fontSize: s.name }}>
          {displayName}
        </span>
        {secondLine && s.sub > 0 && (
          <span className="user-badge__sub" style={{ fontSize: s.sub }}>
            {secondLine}
          </span>
        )}
      </div>
    </div>
  );
}

export default memo(UserBadge);
