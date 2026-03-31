import { motion } from 'framer-motion';
import {
  ChatCircleDots,
  UsersThree,
  Broadcast,
  ChatText,
  MagnifyingGlass,
} from '@phosphor-icons/react';
import './EmptyState.css';

const ICONS = {
  'no-chats': ChatCircleDots,
  'no-friends': UsersThree,
  'no-channels': Broadcast,
  'no-messages': ChatText,
  'no-results': MagnifyingGlass,
};

function getIcon(type) {
  const Icon = ICONS[type];
  if (!Icon) return null;
  return <Icon size={32} weight="regular" />;
}

export default function EmptyState({ type, icon, title, subtitle, text, hint, action }) {
  // Поддержка двух API: {type, title, subtitle, action} и {icon, text, hint}
  const resolvedIcon = icon ?? (type ? getIcon(type) : null);
  const resolvedTitle = title ?? text;
  const resolvedSubtitle = subtitle ?? hint;

  return (
    <motion.div
      className="empty-state"
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
    >
      {resolvedIcon && (
        <div className="empty-state__icon-wrap">
          {resolvedIcon}
        </div>
      )}
      {resolvedTitle && (
        <h3 className="empty-state__title">{resolvedTitle}</h3>
      )}
      {resolvedSubtitle && (
        <p className="empty-state__subtitle">{resolvedSubtitle}</p>
      )}
      {action && (
        <button className="empty-state__action" onClick={action.onClick}>
          {action.label}
        </button>
      )}
    </motion.div>
  );
}
