import { motion } from 'framer-motion';
import {
  ChatCircleDots,
  UsersThree,
  Broadcast,
  ChatText,
  MagnifyingGlass,
} from '@phosphor-icons/react';
import './EmptyState.css';

const TYPE_MAP = {
  'no-chats': {
    icon: <ChatCircleDots size={40} weight="duotone" />,
    title: 'Нет чатов',
    subtitle: 'Начните общение с друзьями',
  },
  'no-friends': {
    icon: <UsersThree size={40} weight="duotone" />,
    title: 'Нет друзей',
    subtitle: 'Добавьте кого-нибудь в друзья',
  },
  'no-channels': {
    icon: <Broadcast size={40} weight="duotone" />,
    title: 'Нет каналов',
    subtitle: 'Создайте или найдите канал',
  },
  'no-messages': {
    icon: <ChatText size={40} weight="duotone" />,
    title: 'Нет сообщений',
    subtitle: 'Напишите первое сообщение',
  },
  'no-results': {
    icon: <MagnifyingGlass size={40} weight="duotone" />,
    title: 'Ничего не найдено',
    subtitle: 'Попробуйте другой запрос',
  },
};

export default function EmptyState({ type, icon, title, subtitle, text, hint, action }) {
  const config = TYPE_MAP[type] || {};
  const finalIcon = icon ?? config.icon;
  const finalTitle = title ?? text ?? config.title;
  const finalSubtitle = subtitle ?? hint ?? config.subtitle;

  return (
    <motion.div
      className="empty-state"
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
    >
      {finalIcon && (
        <div className="empty-state__icon-wrap">
          {finalIcon}
        </div>
      )}
      {finalTitle && (
        <h3 className="empty-state__title">{finalTitle}</h3>
      )}
      {finalSubtitle && (
        <p className="empty-state__subtitle">{finalSubtitle}</p>
      )}
      {action && (
        <button className="empty-state__action" onClick={action.onClick}>
          {action.label}
        </button>
      )}
    </motion.div>
  );
}
