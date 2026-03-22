import { Phone, MoreHorizontal } from 'lucide-react';
import Avatar from '../ui/Avatar';
import { formatLastSeen } from '../../utils/time';
import './ChatHeader.css';

export default function ChatHeader({ chat, isOnline, userStatus, typingUsernames, onCall, onMembers }) {
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

  const onMore = isGroup ? onMembers : undefined;

  return (
    <div className="chat-header-zone">
      <div className="chat-header-island">
        <Avatar user={otherUser || chat} size={28} showOnline={isOnline} />
        <span className="chat-header__name">{chatName}</span>
        <span className="chat-header__dot" />
        <span className="chat-header__status">{statusText}</span>
        {onCall && (
          <button className="chat-header__btn" onClick={onCall} title="Позвонить">
            <Phone />
          </button>
        )}
        <button className="chat-header__btn" onClick={onMore} title={isGroup ? 'Участники' : 'Подробнее'}>
          <MoreHorizontal />
        </button>
      </div>
    </div>
  );
}
