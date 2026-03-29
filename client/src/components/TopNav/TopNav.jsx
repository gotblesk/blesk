import { memo, useMemo, useState, useCallback } from 'react';
import { List, ChatCircle, Microphone, Megaphone, UsersThree, MagnifyingGlass, Bell, GearSix, ShieldStar } from '@phosphor-icons/react';
import { useChatStore } from '../../store/chatStore';
import { useNotificationStore } from '../../store/notificationStore';
import NotificationsPanel from './NotificationsPanel';
import './TopNav.css';

const BASE_TABS = [
  { id: 'chats', label: 'Чаты', icon: ChatCircle },
  { id: 'voice', label: 'Голос', icon: Microphone },
  { id: 'channels', label: 'Каналы', icon: Megaphone },
  { id: 'friends', label: 'Друзья', icon: UsersThree },
];

const ADMIN_TAB = { id: 'admin', label: 'Админ', icon: ShieldStar };

export default memo(function TopNav({ activeTab, onTabChange, onToggleSidebar, onSearch, onSettings, onOpenChat, isAdmin }) {
  const totalUnread = useChatStore(s => s.chats.reduce((sum, c) => sum + (c.unreadCount || 0), 0));
  const isConnected = useChatStore(s => s.isConnected);
  const unreadNotifs = useNotificationStore(s => s.unreadCount);
  const tabs = useMemo(() => isAdmin ? [...BASE_TABS, ADMIN_TAB] : BASE_TABS, [isAdmin]);
  const [notifOpen, setNotifOpen] = useState(false);
  const handleNotifClose = useCallback(() => setNotifOpen(false), []);

  return (
    <>
    <nav className="top-nav" onDoubleClick={e => e.stopPropagation()}>
      {/* Тонкая полоска состояния соединения под nav */}
      {!isConnected && <div className="top-nav__offline" title="Нет соединения с сервером. Переподключение..." />}
      {/* Drag region */}
      <div className="top-nav__drag" />

      <div className="top-nav__left">
        <button className="top-nav__toggle" onClick={onToggleSidebar} title="Sidebar">
          <List size={20} weight="bold" />
        </button>

        <div className="top-nav__tabs">
          {tabs.map(tab => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            const showBadge = tab.id === 'chats' && totalUnread > 0;
            return (
              <button
                key={tab.id}
                className={`top-nav__tab ${isActive ? 'top-nav__tab--active' : ''}`}
                onClick={() => onTabChange(tab.id)}
              >
                <Icon size={17} weight={isActive ? 'fill' : 'regular'} />
                <span>{tab.label}</span>
                {showBadge && <span className="top-nav__badge">{totalUnread > 99 ? '99+' : totalUnread}</span>}
              </button>
            );
          })}
        </div>
      </div>

      <div className="top-nav__right">
        <button className="top-nav__action" onClick={onSearch} title="Поиск (Ctrl+K)">
          <MagnifyingGlass size={18} />
        </button>
        <button className="top-nav__action top-nav__action--notif" onClick={() => setNotifOpen(prev => !prev)} title="Уведомления">
          <Bell size={18} />
          {unreadNotifs > 0 && <span className="top-nav__notif-dot">{unreadNotifs}</span>}
        </button>
        <button className="top-nav__action" onClick={onSettings} title="Настройки">
          <GearSix size={18} />
        </button>
      </div>
    </nav>

    <NotificationsPanel
      open={notifOpen}
      onClose={handleNotifClose}
      onOpenChat={(chatId) => { setNotifOpen(false); onOpenChat?.(chatId); }}
    />
    </>
  );
});
