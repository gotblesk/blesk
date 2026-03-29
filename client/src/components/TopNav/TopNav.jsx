import { memo } from 'react';
import { List, ChatCircle, Microphone, Megaphone, UsersThree, MagnifyingGlass, Bell, GearSix } from '@phosphor-icons/react';
import { useChatStore } from '../../store/chatStore';
import { useNotificationStore } from '../../store/notificationStore';
import './TopNav.css';

const TABS = [
  { id: 'chats', label: 'Чаты', icon: ChatCircle },
  { id: 'voice', label: 'Голос', icon: Microphone },
  { id: 'channels', label: 'Каналы', icon: Megaphone },
  { id: 'friends', label: 'Друзья', icon: UsersThree },
];

export default memo(function TopNav({ activeTab, onTabChange, onToggleSidebar, onSearch, onSettings }) {
  const totalUnread = useChatStore(s => s.chats.reduce((sum, c) => sum + (c.unreadCount || 0), 0));
  const unreadNotifs = useNotificationStore ? useNotificationStore(s => s.unreadCount) : 0;

  return (
    <nav className="top-nav" onDoubleClick={e => e.stopPropagation()}>
      {/* Drag region */}
      <div className="top-nav__drag" />

      <div className="top-nav__left">
        <button className="top-nav__toggle" onClick={onToggleSidebar} title="Sidebar">
          <List size={18} weight="bold" />
        </button>

        <div className="top-nav__tabs">
          {TABS.map(tab => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            const showBadge = tab.id === 'chats' && totalUnread > 0;
            return (
              <button
                key={tab.id}
                className={`top-nav__tab ${isActive ? 'top-nav__tab--active' : ''}`}
                onClick={() => onTabChange(tab.id)}
              >
                <Icon size={15} weight={isActive ? 'fill' : 'regular'} />
                <span>{tab.label}</span>
                {showBadge && <span className="top-nav__badge">{totalUnread > 99 ? '99+' : totalUnread}</span>}
              </button>
            );
          })}
        </div>
      </div>

      <div className="top-nav__right">
        <button className="top-nav__action" onClick={onSearch} title="Поиск (Ctrl+K)">
          <MagnifyingGlass size={16} />
        </button>
        <button className="top-nav__action top-nav__action--notif" onClick={() => {}} title="Уведомления">
          <Bell size={16} />
          {unreadNotifs > 0 && <span className="top-nav__notif-dot">{unreadNotifs}</span>}
        </button>
        <button className="top-nav__action" onClick={onSettings} title="Настройки">
          <GearSix size={16} />
        </button>
      </div>
    </nav>
  );
});
