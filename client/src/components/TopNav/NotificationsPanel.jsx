import { useEffect, useRef, memo } from 'react';
import { X, Check, ChatCircle, Phone, UserPlus, Sparkle } from '@phosphor-icons/react';
import { useNotificationStore } from '../../store/notificationStore';

const ICON_MAP = {
  message: ChatCircle,
  call: Phone,
  friend_request: UserPlus,
  system: Sparkle,
};

function formatTime(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  const now = new Date();
  const diff = now - d;
  if (diff < 60000) return 'сейчас';
  if (diff < 3600000) return `${Math.floor(diff / 60000)}м`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}ч`;
  return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
}

export default memo(function NotificationsPanel({ open, onClose, onOpenChat }) {
  const notifications = useNotificationStore(s => s.notifications);
  const markAllAsRead = useNotificationStore(s => s.markAllAsRead);
  const markAsRead = useNotificationStore(s => s.markAsRead);
  const panelRef = useRef(null);

  // Click outside to close
  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (panelRef.current && !panelRef.current.contains(e.target)) onClose();
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open, onClose]);

  // Escape to close
  useEffect(() => {
    if (!open) return;
    const handler = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="notif-panel" ref={panelRef}>
      <div className="notif-panel__head">
        <span className="notif-panel__title">Уведомления</span>
        <div className="notif-panel__actions">
          {notifications.length > 0 && (
            <button className="notif-panel__read-all" onClick={() => markAllAsRead()}>
              <Check size={12} /> Прочитать все
            </button>
          )}
          <button className="notif-panel__close" onClick={onClose}>
            <X size={14} />
          </button>
        </div>
      </div>

      <div className="notif-panel__list">
        {notifications.length === 0 && (
          <div className="notif-panel__empty">Нет уведомлений</div>
        )}
        {notifications.map(notif => {
          const Icon = ICON_MAP[notif.type] || Sparkle;
          return (
            <div
              key={notif.id}
              className={`notif-panel__item ${notif.isRead ? '' : 'notif-panel__item--unread'}`}
              onClick={() => {
                if (!notif.isRead) markAsRead(notif.id);
                if (notif.roomId) onOpenChat?.(notif.roomId);
                onClose();
              }}
            >
              <div className="notif-panel__item-icon">
                <Icon size={14} weight="fill" />
              </div>
              <div className="notif-panel__item-content">
                <span className="notif-panel__item-text">{notif.text || notif.body || 'Уведомление'}</span>
                <span className="notif-panel__item-time">{formatTime(notif.createdAt)}</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
});
