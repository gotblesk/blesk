import { memo, useMemo, useState, useCallback, useRef, useEffect } from 'react';
import { ChatBubbleLeftRightIcon, MicrophoneIcon, MegaphoneIcon, UserGroupIcon, MagnifyingGlassIcon, BellIcon, Cog6ToothIcon, ShieldCheckIcon, UserIcon, ArrowRightOnRectangleIcon, Bars3Icon } from '@heroicons/react/24/outline';
import { ChatBubbleLeftRightIcon as ChatBubbleLeftRightSolid, MicrophoneIcon as MicrophoneSolid, MegaphoneIcon as MegaphoneSolid, UserGroupIcon as UserGroupSolid, ShieldCheckIcon as ShieldCheckSolid } from '@heroicons/react/24/solid';
import { AnimatePresence, motion } from 'framer-motion';
import { useChatStore } from '../../store/chatStore';
import { useNotificationStore } from '../../store/notificationStore';
import Avatar from '../ui/Avatar';
import NotificationsPanel from './NotificationsPanel';
import './TopNav.css';

const BASE_TABS = [
  { id: 'chats', label: 'Чаты', icon: ChatBubbleLeftRightIcon, iconSolid: ChatBubbleLeftRightSolid },
  { id: 'voice', label: 'Голос', icon: MicrophoneIcon, iconSolid: MicrophoneSolid },
  { id: 'channels', label: 'Каналы', icon: MegaphoneIcon, iconSolid: MegaphoneSolid },
  { id: 'friends', label: 'Друзья', icon: UserGroupIcon, iconSolid: UserGroupSolid },
];

const ADMIN_TAB = { id: 'admin', label: 'Админ', icon: ShieldCheckIcon, iconSolid: ShieldCheckSolid };

const STATUS_OPTIONS = [
  { key: 'online', label: 'В сети', color: '#4ade80' },
  { key: 'dnd', label: 'Не беспокоить', color: '#f59e0b' },
  { key: 'invisible', label: 'Невидимка', color: '#6b7280' },
];

export default memo(function TopNav({ activeTab, onTabChange, onToggleSidebar, onSearch, onSettings, onOpenChat, isAdmin, user, onLogout, onNavigate, onStatusChange }) {
  const totalUnread = useChatStore(s => s.chats.reduce((sum, c) => sum + (c.unreadCount || 0), 0));
  const isConnected = useChatStore(s => s.isConnected);
  const unreadNotifs = useNotificationStore(s => s.unreadCount);
  const tabs = useMemo(() => isAdmin ? [...BASE_TABS, ADMIN_TAB] : BASE_TABS, [isAdmin]);
  const [notifOpen, setNotifOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const handleNotifClose = useCallback(() => setNotifOpen(false), []);
  const handleNotifToggle = useCallback(() => setNotifOpen(prev => !prev), []);
  const handleUserMenuToggle = useCallback(() => setUserMenuOpen(prev => !prev), []);
  const userMenuRef = useRef(null);

  const currentStatus = user?.status || 'online';
  const statusLabel = STATUS_OPTIONS.find(s => s.key === currentStatus)?.label ?? 'В сети';

  useEffect(() => {
    if (!userMenuOpen) return;
    function handleClick(e) {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target)) {
        setUserMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
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
          <Bars3Icon className="w-5 h-5" />
        </button>

        <div className="top-nav__tabs">
          {tabs.map(tab => {
            const isActive = activeTab === tab.id;
            const Icon = isActive ? tab.iconSolid : tab.icon;
            const showBadge = tab.id === 'chats' && totalUnread > 0;
            return (
              <button
                key={tab.id}
                className={`top-nav__tab ${isActive ? 'top-nav__tab--active' : ''}`}
                onClick={() => onTabChange(tab.id)}
              >
                <Icon className="w-[17px] h-[17px]" />
                <span>{tab.label}</span>
                {showBadge && <span className="top-nav__badge">{totalUnread > 99 ? '99+' : totalUnread}</span>}
              </button>
            );
          })}
        </div>
      </div>

      <div className="top-nav__right">
        <button className="top-nav__action" onClick={onSearch} title="Поиск (Ctrl+K)" aria-label="Поиск">
          <MagnifyingGlassIcon className="w-[18px] h-[18px]" />
        </button>
        <button className="top-nav__action top-nav__action--notif" onClick={handleNotifToggle} title="Уведомления" aria-label="Уведомления">
          <BellIcon className="w-[18px] h-[18px]" />
          {unreadNotifs > 0 && <span className="top-nav__notif-dot" aria-live="polite" role="status">{unreadNotifs}</span>}
        </button>
        <button className="top-nav__action" onClick={onSettings} title="Настройки" aria-label="Настройки">
          <Cog6ToothIcon className="w-[18px] h-[18px]" />
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
                  <Avatar user={user} size={36} showOnline={true} isOnline={currentStatus !== 'invisible'} userStatus={currentStatus} />
                  <div className="um__info">
                    <div className="um__name">{user?.displayName || user?.username}</div>
                    <div className="um__status-text">{statusLabel}</div>
                  </div>
                </div>

                <div className="um__sep" />

                {/* Status options */}
                <div className="um__group">
                  {STATUS_OPTIONS.map(s => (
                    <button
                      key={s.key}
                      className={`um__item${currentStatus === s.key ? ' um__item--active' : ''}`}
                      onClick={() => { onStatusChange?.(s.key); setUserMenuOpen(false); }}
                    >
                      <span className="um__dot" style={{ background: s.color }} />
                      <span>{s.label}</span>
                      {currentStatus === s.key && <span className="um__check">✓</span>}
                    </button>
                  ))}
                </div>

                <div className="um__sep" />

                {/* Actions */}
                <div className="um__group">
                  <button className="um__item" onClick={() => { setUserMenuOpen(false); onNavigate?.('profile'); }}>
                    <UserIcon className="w-4 h-4" />
                    <span>Профиль</span>
                  </button>
                  <button className="um__item" onClick={() => { setUserMenuOpen(false); onSettings?.(); }}>
                    <Cog6ToothIcon className="w-4 h-4" />
                    <span>Настройки</span>
                  </button>
                </div>

                <div className="um__sep" />

                {/* Logout */}
                <button className="um__item um__item--danger" onClick={() => { setUserMenuOpen(false); onLogout?.(); }}>
                  <ArrowRightOnRectangleIcon className="w-4 h-4" />
                  <span>Выйти</span>
                </button>
              </motion.div>
            )}
          </AnimatePresence>
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
