import { useEffect } from 'react';
import { Server, Cpu, Database, Clock, Wifi } from 'lucide-react';
import Glass from '../../ui/Glass';
import { useAdminStore } from '../../../store/adminStore';

function formatUptime(seconds) {
  if (!seconds && seconds !== 0) return '---';
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  const parts = [];
  if (d > 0) parts.push(`${d}д`);
  if (h > 0) parts.push(`${h}ч`);
  if (m > 0) parts.push(`${m}м`);
  parts.push(`${s}с`);
  return parts.join(' ');
}

export default function AdminServerSettings() {
  const { serverConfig, fetchServerConfig } = useAdminStore();

  useEffect(() => { fetchServerConfig(); }, [fetchServerConfig]);

  const items = [
    {
      icon: <Server size={18} />,
      label: 'Версия blesk',
      value: serverConfig?.version || '---',
    },
    {
      icon: <Cpu size={18} />,
      label: 'Node.js',
      value: serverConfig?.nodeVersion || '---',
    },
    {
      icon: <Clock size={18} />,
      label: 'Uptime',
      value: formatUptime(serverConfig?.uptime),
    },
    {
      icon: <Wifi size={18} />,
      label: 'WebSocket-подключения',
      value: serverConfig?.socketConnections ?? '---',
    },
    {
      icon: <Database size={18} />,
      label: 'Статус БД',
      value: serverConfig?.dbStatus || '---',
    },
  ];

  return (
    <div className="admin-section">
      <div className="admin-section__title">Сервер</div>

      <div className="admin-stats-grid">
        {items.map((item, i) => (
          <Glass key={i} depth={1} radius={14} className="admin-stat-card">
            <div className="admin-stat-card__icon">{item.icon}</div>
            <div className="admin-stat-card__info">
              <div className="admin-stat-card__value" style={{ fontSize: 16 }}>{item.value}</div>
              <div className="admin-stat-card__label">{item.label}</div>
            </div>
          </Glass>
        ))}
      </div>
    </div>
  );
}
