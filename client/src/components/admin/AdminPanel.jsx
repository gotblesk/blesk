import { useState } from 'react';
import {
  ChartBar, UsersThree, Tag, ShieldWarning, Radio, Scroll,
  ChatDots, Megaphone, Database, Desktop, ArrowLeft,
} from '@phosphor-icons/react';
import Glass from '../ui/Glass';
import AdminOverview from './sections/AdminOverview';
import AdminUsers from './sections/AdminUsers';
import AdminTags from './sections/AdminTags';
import AdminModeration from './sections/AdminModeration';
import AdminChannels from './sections/AdminChannels';
import AdminLogs from './sections/AdminLogs';
import AdminFeedback from './sections/AdminFeedback';
import AdminBroadcast from './sections/AdminBroadcast';
import AdminDatabase from './sections/AdminDatabase';
import AdminServerSettings from './sections/AdminServerSettings';
import './AdminPanel.css';

const SECTIONS = [
  { id: 'overview', icon: <ChartBar size={16} weight="regular" />, label: 'Обзор' },
  { id: 'users', icon: <UsersThree size={16} weight="regular" />, label: 'Пользователи' },
  { id: 'tags', icon: <Tag size={16} weight="regular" />, label: 'Теги' },
  { id: 'moderation', icon: <ShieldWarning size={16} weight="regular" />, label: 'Модерация' },
  { id: 'channels', icon: <Radio size={16} weight="regular" />, label: 'Каналы' },
  { id: 'logs', icon: <Scroll size={16} weight="regular" />, label: 'Логи' },
  { id: 'feedback', icon: <ChatDots size={16} weight="regular" />, label: 'Обратная связь' },
  { id: 'broadcast', icon: <Megaphone size={16} weight="regular" />, label: 'Рассылка' },
  { id: 'database', icon: <Database size={16} weight="regular" />, label: 'База данных' },
  { id: 'server', icon: <Desktop size={16} weight="regular" />, label: 'Сервер' },
];

export default function AdminPanel({ onBack }) {
  const [section, setSection] = useState('overview');

  const renderSection = () => {
    switch (section) {
      case 'overview': return <AdminOverview />;
      case 'users': return <AdminUsers />;
      case 'tags': return <AdminTags />;
      case 'moderation': return <AdminModeration />;
      case 'channels': return <AdminChannels />;
      case 'logs': return <AdminLogs />;
      case 'feedback': return <AdminFeedback />;
      case 'broadcast': return <AdminBroadcast />;
      case 'database': return <AdminDatabase />;
      case 'server': return <AdminServerSettings />;
      default: return <AdminOverview />;
    }
  };

  return (
    <div className="admin-screen section-enter">
      <div className="admin-screen__header">
        <button className="admin-screen__back" onClick={onBack}>
          <ArrowLeft size={14} weight="bold" />
          Назад
        </button>
        <div className="admin-screen__title">Панель управления</div>
      </div>

      <div className="admin-screen__layout">
        <div className="admin-screen__sidebar">
          {SECTIONS.map((s) => (
            <button
              key={s.id}
              className={`admin-nav-item ${section === s.id ? 'admin-nav-item--active' : ''}`}
              onClick={() => setSection(s.id)}
            >
              <span className="admin-nav-item__icon">{s.icon}</span>
              <span className="admin-nav-item__label">{s.label}</span>
            </button>
          ))}
        </div>

        <div className="admin-screen__content">
          {renderSection()}
        </div>
      </div>
    </div>
  );
}
