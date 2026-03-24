import { useEffect } from 'react';
import { Users, MessageSquare, Radio, AlertTriangle, MessageCircle, Wifi } from 'lucide-react';
import Glass from '../../ui/Glass';
import { useAdminStore } from '../../../store/adminStore';

const STAT_CARDS = [
  { key: 'usersOnline', label: 'Онлайн', icon: <Wifi size={18} /> },
  { key: 'usersTotal', label: 'Пользователи', icon: <Users size={18} /> },
  { key: 'messagesToday', label: 'Сообщения сегодня', icon: <MessageSquare size={18} /> },
  { key: 'channelsTotal', label: 'Каналы', icon: <Radio size={18} /> },
  { key: 'reportsNew', label: 'Жалобы', icon: <AlertTriangle size={18} /> },
  { key: 'feedbackNew', label: 'Обратная связь', icon: <MessageCircle size={18} /> },
];

export default function AdminOverview() {
  const { stats, loadingStats, fetchStats } = useAdminStore();

  useEffect(() => {
    fetchStats();
    const interval = setInterval(fetchStats, 30000);
    return () => clearInterval(interval);
  }, [fetchStats]);

  return (
    <div className="admin-section">
      <div className="admin-section__title">Обзор</div>
      <div className="admin-stats-grid">
        {STAT_CARDS.map((card) => (
          <Glass key={card.key} depth={1} radius={14} className="admin-stat-card">
            <div className="admin-stat-card__icon">{card.icon}</div>
            <div className="admin-stat-card__info">
              {loadingStats || !stats ? (
                <div className="admin-skeleton" style={{ width: 48, height: 24 }} />
              ) : (
                <div className="admin-stat-card__value">
                  {stats[card.key] ?? 0}
                </div>
              )}
              <div className="admin-stat-card__label">{card.label}</div>
            </div>
          </Glass>
        ))}
      </div>
    </div>
  );
}
