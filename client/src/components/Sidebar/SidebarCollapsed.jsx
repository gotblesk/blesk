import { memo } from 'react';
import { Plus } from '@phosphor-icons/react';
import { useChatStore } from '../../store/chatStore';
import Avatar from '../ui/Avatar';
import './Sidebar.css';

export default memo(function SidebarCollapsed({ activeTab, activeChatId, onSelectChat }) {
  const chats = useChatStore(s => s.chats);
  const onlineUsers = useChatStore(s => s.onlineUsers);
  const pinnedChats = useChatStore(s => s.pinnedChats);

  const pinned = chats.filter(c => pinnedChats.has(c.id));
  const rest = chats.filter(c => !pinnedChats.has(c.id));
  const sorted = [...pinned, ...rest];

  const isOnline = (chat) => {
    if (chat.otherUser) return onlineUsers.includes(chat.otherUser.id);
    return false;
  };

  return (
    <div className="sc">
      <div className="sc__list">
        {pinned.length > 0 && rest.length > 0 && (
          <>
            {pinned.map(chat => (
              <SidebarAvatar
                key={chat.id}
                chat={chat}
                isActive={activeChatId === chat.id}
                isOnline={isOnline(chat)}
                onClick={() => onSelectChat(chat.id)}
              />
            ))}
            <div className="sc__sep" />
          </>
        )}
        {(pinned.length > 0 ? rest : sorted).map(chat => (
          <SidebarAvatar
            key={chat.id}
            chat={chat}
            isActive={activeChatId === chat.id}
            isOnline={isOnline(chat)}
            onClick={() => onSelectChat(chat.id)}
          />
        ))}
      </div>
      <button className="sc__add" title="Новый чат">
        <Plus size={18} weight="bold" />
      </button>
    </div>
  );
});

function SidebarAvatar({ chat, isActive, isOnline, onClick }) {
  const user = chat.otherUser;
  const name = user?.username || chat.name || 'Чат';

  return (
    <div className={`sc__item ${isActive ? 'sc__item--active' : ''}`} onClick={onClick} title={name}>
      {isActive && <div className="sc__active-bar" />}
      <div className="sc__ava-wrap">
        <Avatar user={user || { username: name }} size={44} showOnline={isOnline} />
        {chat.unreadCount > 0 && (
          <span className="sc__badge">{chat.unreadCount > 99 ? '99+' : chat.unreadCount}</span>
        )}
      </div>
    </div>
  );
}
