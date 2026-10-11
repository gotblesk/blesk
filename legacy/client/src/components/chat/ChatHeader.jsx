import { useState, useRef } from 'react';
import { AnimatePresence } from 'framer-motion';
import { Phone, VideoCamera, MagnifyingGlass, DotsThreeOutline, User, BellSlash, PushPin, Trash } from '@phosphor-icons/react';
import Avatar from '../ui/Avatar';
import { ShieldBadge } from '../ui/ShieldFingerprint';
import ContextMenu from '../ui/ContextMenu';
import { formatLastSeen } from '../../utils/time';
import { useChatStore } from '../../store/chatStore';
import './ChatHeader.css';

export default function ChatHeader({ chat, isOnline, userStatus, typingUsernames, onCall, onVideoCall, onSearch, onMembers, onAvatarClick, shieldActive }) {
  const isSocketConnected = useChatStore((s) => s.isConnected);
  const isGroup = chat.type === 'group';
  const otherUser = chat.otherUser;

  // Context menu state for "..." button
  const [menuPos, setMenuPos] = useState(null);
  const moreBtnRef = useRef(null);

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
            {shieldActive && !isGroup && <ShieldBadge active title="Сквозное шифрование активно" />}
          </div>
          <span className={`chat-header__status${isTyping ? ' chat-header__status--typing' : ''}`}>{statusText}</span>
        </div>

        <div className="chat-header__actions">
          {/* Поиск по чату */}
          {onSearch && (
            <button className="chat-header__btn" onClick={onSearch} title="Поиск" aria-label="Поиск по чату">
              <MagnifyingGlass size={18} weight="regular" />
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
              <Phone size={18} weight="regular" />
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
              <VideoCamera size={18} weight="regular" />
            </button>
          )}
          <button
            ref={moreBtnRef}
            className="chat-header__btn"
            onClick={(e) => {
              e.stopPropagation();
              const rect = moreBtnRef.current.getBoundingClientRect();
              setMenuPos({ x: rect.right - 180, y: rect.bottom + 6 });
            }}
            title="Ещё"
            aria-label="Дополнительные действия"
            aria-haspopup="menu"
            aria-expanded={!!menuPos}
          >
            <DotsThreeOutline size={18} weight="regular" />
          </button>
        </div>

        {/* Context menu for "..." button */}
        <AnimatePresence>
          {menuPos && (
            <ContextMenu
              x={menuPos.x}
              y={menuPos.y}
              onClose={() => setMenuPos(null)}
              items={[
                {
                  icon: <User size={16} weight="regular" />,
                  label: isGroup ? 'Участники' : 'Профиль пользователя',
                  onClick: () => { isGroup ? onMembers?.() : onAvatarClick?.(); },
                },
                ...(onSearch ? [{
                  icon: <MagnifyingGlass size={16} weight="regular" />,
                  label: 'Поиск в чате',
                  onClick: () => onSearch?.(),
                }] : []),
                { divider: true },
                {
                  icon: <BellSlash size={16} weight="regular" />,
                  label: 'Мут уведомлений',
                  onClick: () => {},
                },
                {
                  icon: <PushPin size={16} weight="regular" />,
                  label: 'Закрепить чат',
                  onClick: () => {},
                },
                { divider: true },
                {
                  icon: <Trash size={16} weight="regular" />,
                  label: 'Очистить чат',
                  onClick: () => {},
                  danger: true,
                },
              ]}
            />
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
