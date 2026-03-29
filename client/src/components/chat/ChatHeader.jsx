import { Phone, Video, Search, MoreHorizontal } from 'lucide-react';
import Avatar from '../ui/Avatar';
import { ShieldBadge } from '../ui/ShieldFingerprint';
import { formatLastSeen } from '../../utils/time';
import { useChatStore } from '../../store/chatStore';
import './ChatHeader.css';

export default function ChatHeader({ chat, isOnline, userStatus, typingUsernames, onCall, onVideoCall, onSearch, onMembers, onAvatarClick, shieldActive }) {
  const isSocketConnected = useChatStore((s) => s.isConnected);
  const isGroup = chat.type === 'group';
  const otherUser = chat.otherUser;

  const isTyping = typingUsernames?.length > 0;
  let statusText;
  if (isTyping) {
    if (isGroup) {
      const names = typingUsernames.join(', ');
      statusText = typingUsernames.length > 1
        ? `${names} печатают...`
        : `${names} печатает...`;
    } else {
      statusText = 'печатает...';
    }
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
        {/* Avatar */}
        <div
          className="chat-header__ava"
          onClick={e => { e.stopPropagation(); onAvatarClick?.(); }}
          title="Посмотреть профиль"
        >
          <Avatar user={otherUser || chat} size={36} showOnline={isOnline} isOnline={isOnline} />
        </div>

        <div className="chat-header__info">
          <div className="chat-header__name-row">
            <span className="chat-header__name">{chatName}</span>
            {/* Shield badge для E2E чатов */}
            {shieldActive && !isGroup && <ShieldBadge active onClick={() => {}} />}
          </div>
          <span className={`chat-header__status${isTyping ? ' chat-header__status--typing' : ''}`}>{statusText}</span>
        </div>

        <div className="chat-header__actions">
          {/* Поиск по чату */}
          {onSearch && (
            <button className="chat-header__btn" onClick={onSearch} title="Поиск" aria-label="Поиск по чату">
              <Search size={18} strokeWidth={1.5} />
            </button>
          )}
          {/* Голосовой звонок */}
          {onCall && (
            <button
              className="chat-header__btn"
              onClick={isSocketConnected ? onCall : undefined}
              disabled={!isSocketConnected}
              title={isSocketConnected ? 'Голосовой звонок' : 'Нет соединения'}
              aria-label="Позвонить"
            >
              <Phone size={18} strokeWidth={1.5} />
            </button>
          )}
          {/* Видеозвонок */}
          {onVideoCall && (
            <button
              className="chat-header__btn"
              onClick={isSocketConnected ? onVideoCall : undefined}
              disabled={!isSocketConnected}
              title={isSocketConnected ? 'Видеозвонок' : 'Нет соединения'}
              aria-label="Видеозвонок"
            >
              <Video size={18} strokeWidth={1.5} />
            </button>
          )}
          <button className="chat-header__btn" onClick={isGroup ? onMembers : onAvatarClick} title={isGroup ? 'Участники' : 'Подробнее'} aria-label={isGroup ? 'Участники группы' : 'Информация о чате'}>
            <MoreHorizontal size={18} strokeWidth={1.5} />
          </button>
        </div>
      </div>
    </div>
  );
}
