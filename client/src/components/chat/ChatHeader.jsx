import { Phone, MoreHorizontal } from 'lucide-react';
import Avatar from '../ui/Avatar';
import { formatLastSeen } from '../../utils/time';
import './ChatHeader.css';

export default function ChatHeader({ chat, isOnline, userStatus, typingUsernames, onCall, onMembers, onAvatarClick }) {
  const isGroup = chat.type === 'group';
  const otherUser = chat.otherUser;

  let statusText;
  if (typingUsernames?.length) {
    statusText = `${typingUsernames.join(', ')} печатает...`;
  } else if (isGroup) {
    statusText = `${chat.memberCount ?? 0} участников`;
  } else {
    if (isOnline) {
      statusText = userStatus === 'dnd' ? 'не беспокоить' : 'онлайн';
    } else if (otherUser?.lastSeenAt) {
      statusText = `был(а) ${formatLastSeen(otherUser.lastSeenAt)}`;
    } else {
      statusText = 'офлайн';
    }
  }

  const chatName = isGroup ? chat.name : (otherUser?.username || chat.name);

  return (
    <div className="chat-header-zone">
      <div className="chat-header-island">
        {/* Avatar — clickable, visually separated */}
        <div
          className="chat-header__ava"
          onClick={e => { e.stopPropagation(); onAvatarClick?.(); }}
          title="Посмотреть профиль"
        >
          <Avatar user={otherUser || chat} size={36} showOnline={isOnline} isOnline={isOnline} />
        </div>

        <div className="chat-header__info">
          <span className="chat-header__name">{chatName}</span>
          <span className="chat-header__status">{statusText}</span>
        </div>

        <div className="chat-header__actions">
          {onCall && (
            <button className="chat-header__btn" onClick={onCall} title="Позвонить">
              <Phone />
            </button>
          )}
          <button className="chat-header__btn" onClick={isGroup ? onMembers : onAvatarClick} title={isGroup ? 'Участники' : 'Подробнее'}>
            <MoreHorizontal />
          </button>
        </div>
      </div>
    </div>
  );
}
