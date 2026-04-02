import { memo, useMemo, useState, useCallback, useRef, useEffect } from 'react';
import { ChatCircleDots, Microphone, Megaphone, UsersThree, MagnifyingGlass, Bell, GearSix, ShieldCheck, User, SignOut, List, Minus, Square, X } from '@phosphor-icons/react';
import { AnimatePresence, motion } from 'framer-motion';
import { useChatStore } from '../../store/chatStore';
import { useNotificationStore } from '../../store/notificationStore';
import Avatar from '../ui/Avatar';
import SegmentedCircle from '../profile/SegmentedCircle';
import NotificationsPanel from './NotificationsPanel';
import './TopNav.css';

const BASE_TABS = [
  { id: 'chats', label: 'Чаты', Icon: ChatCircleDots },
  { id: 'voice', label: 'Голос', Icon: Microphone },
  { id: 'channels', label: 'Каналы', Icon: Megaphone },
  { id: 'friends', label: 'Друзья', Icon: UsersThree },
];

const ADMIN_TAB = { id: 'admin', label: 'Админ', Icon: ShieldCheck };

const STATUS_LABELS = { online: 'В сети', dnd: 'Не беспокоить', invisible: 'Невидимка' };

export default memo(function TopNav({ activeTab, onTabChange, onToggleSidebar, onSearch, onSettings, onOpenChat, isAdmin, user, onLogout, onNavigate, onStatusChange }) {
  const totalUnread = useChatStore(s => s.chats.reduce((sum, c) => sum + (c.unreadCount || 0), 0));
  const isConnected = useChatStore(s => s.isConnected);
  const unreadNotifs = useNotificationStore(s => s.unreadCount);
  const tabs = useMemo(() => isAdmin ? [...BASE_TABS, ADMIN_TAB] : BASE_TABS, [isAdmin]);
  const [notifOpen, setNotifOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [maximized, setMaximized] = useState(false);
  const handleNotifClose = useCallback(() => setNotifOpen(false), []);
  const handleNotifToggle = useCallback(() => setNotifOpen(prev => !prev), []);
  const handleUserMenuToggle = useCallback(() => setUserMenuOpen(prev => !prev), []);
  const userMenuRef = useRef(null);

  const currentStatus = user?.status || 'online';
  const statusLabel = STATUS_LABELS[currentStatus] || 'В сети';

  // Отслеживаем состояние maximize для иконки кнопки
  useEffect(() => {
    window.blesk?.window.onMaximizeChange?.((val) => setMaximized(val));
  }, []);

  useEffect(() => {
    if (!userMenuOpen) return;
    function handleClick(e) {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target)) {
        setUserMenuOpen(false);
      }
    }
    function handleKey(e) {
      if (e.key === 'Escape') setUserMenuOpen(false);
    }
    document.addEventListener('mousedown', handleClick);
    document.addEventListener('keydown', handleKey);
    return () => { document.removeEventListener('mousedown', handleClick); document.removeEventListener('keydown', handleKey); };
  }, [userMenuOpen]);

  return (
    <>
    <nav className="top-nav" onDoubleClick={e => e.stopPropagation()}>
      {/* Тонкая полоска состояния соединения под nav */}
      {!isConnected && <div className="top-nav__offline" title="Нет соединения с сервером. Переподключение..." />}
      {/* Drag region */}
      <div className="top-nav__drag" />

      <div className="top-nav__left">
        <button className="top-nav__toggle" onClick={onToggleSidebar} title="Sidebar" aria-label="Переключить боковую панель">
          <List size={20} />
        </button>

        <div className="top-nav__tabs" role="tablist">
          {tabs.map(tab => {
            const isActive = activeTab === tab.id;
            const showBadge = tab.id === 'chats' && totalUnread > 0;
            return (
              <button
                key={tab.id}
                role="tab"
                aria-selected={isActive}
                tabIndex={isActive ? 0 : -1}
                className={`top-nav__tab ${isActive ? 'top-nav__tab--active' : ''}`}
                onClick={() => onTabChange(tab.id)}
              >
                <tab.Icon size={17} weight={isActive ? 'fill' : 'regular'} />
                <span>{tab.label}</span>
                {showBadge && <span className="top-nav__badge">{totalUnread > 99 ? '99+' : totalUnread}</span>}
              </button>
            );
          })}
        </div>
      </div>

      <div className="top-nav__right">
        <button className="top-nav__action" onClick={onSearch} title="Поиск (Ctrl+K)" aria-label="Поиск">
          <MagnifyingGlass size={18} />
        </button>
        <button className="top-nav__action top-nav__action--notif" onClick={handleNotifToggle} title="Уведомления" aria-label="Уведомления">
          <Bell size={18} />
          {unreadNotifs > 0 && <span className="top-nav__notif-dot" aria-live="polite" role="status">{unreadNotifs}</span>}
        </button>
        <button className="top-nav__action" onClick={onSettings} title="Настройки" aria-label="Настройки">
          <GearSix size={18} />
        </button>

        <div className="top-nav__user" ref={userMenuRef}>
          <button className="top-nav__avatar-btn" onClick={handleUserMenuToggle} title="Профиль" aria-label="Меню профиля">
            <Avatar user={user} size={28} showOnline={false} />
          </button>

          <AnimatePresence>
            {userMenuOpen && (
              <motion.div
                className="top-nav__user-menu"
                initial={{ opacity: 0, scale: 0.95, y: -8 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.95 }}
                transition={{ type: 'spring', damping: 28, stiffness: 450 }}
              >
                {/* User info */}
                <div className="um__header">
                  <Avatar user={user} size={44} showOnline={true} isOnline={currentStatus !== 'invisible'} userStatus={currentStatus} />
                  <div className="um__info">
                    <div className="um__name">{user?.displayName || user?.username}</div>
                    <div className="um__tag">{user?.tag?.startsWith('#') ? user.tag : `#${user?.tag || '0000'}`}</div>
                    <div className="um__status-inline" onClick={e => e.stopPropagation()} onMouseDown={e => e.stopPropagation()}>
                      <SegmentedCircle currentStatus={currentStatus} onStatusChange={(key) => { onStatusChange?.(key); }} />
                      <span className="um__status-text">{statusLabel}</span>
                    </div>
                  </div>
                </div>

                <div className="um__sep" />

                {/* Actions */}
                <div className="um__group">
                  <button className="um__item" onClick={(e) => { setUserMenuOpen(false); onNavigate?.('profile', e.currentTarget); }}>
                    <User size={16} />
                    <span>Профиль</span>
                  </button>
                  <button className="um__item" onClick={() => { setUserMenuOpen(false); onSettings?.(); }}>
                    <GearSix size={16} />
                    <span>Настройки</span>
                  </button>
                </div>

                <div className="um__sep" />

                {/* Logout */}
                <button className="um__item um__item--danger" onClick={() => { setUserMenuOpen(false); onLogout?.(); }}>
                  <SignOut size={16} />
                  <span>Выйти</span>
                </button>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Кнопки управления окном — вместо отдельного TitleBar */}
        <div className="top-nav__win-controls">
          <button
            className="top-nav__win-btn"
            onMouseDown={e => e.stopPropagation()}
            onClick={() => window.blesk?.window.minimize()}
            title="Свернуть"
            aria-label="Свернуть"
          >
            <Minus size={14} />
          </button>
          <button
            className="top-nav__win-btn"
            onMouseDown={e => e.stopPropagation()}
            onClick={() => window.blesk?.window.maximize()}
            title={maximized ? 'Восстановить' : 'Развернуть'}
            aria-label={maximized ? 'Восстановить' : 'Развернуть'}
          >
            <Square size={14} />
          </button>
          <button
            className="top-nav__win-btn top-nav__win-btn--close"
            onMouseDown={e => e.stopPropagation()}
            onClick={() => window.blesk?.window.close()}
            title="Закрыть"
            aria-label="Закрыть"
          >
            <X size={14} />
          </button>
        </div>
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
