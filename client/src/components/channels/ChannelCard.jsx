import { Users } from 'lucide-react';
import Glass from '../ui/Glass';
import './ChannelCard.css';

const CATEGORY_COLORS = {
  news: '#3b82f6',
  gaming: '#8b5cf6',
  music: '#ec4899',
  art: '#f59e0b',
  tech: '#06b6d4',
  other: '#6b7280',
};

const CATEGORY_LABELS = {
  news: 'Новости',
  gaming: 'Игры',
  music: 'Музыка',
  art: 'Арт',
  tech: 'Технологии',
  other: 'Другое',
};

function getInitial(name) {
  return (name || '?')[0].toUpperCase();
}

export default function ChannelCard({ channel, onOpen, isSubscribed, isOwned }) {
  const catColor = CATEGORY_COLORS[channel.category] || CATEGORY_COLORS.other;
  const catLabel = CATEGORY_LABELS[channel.category] || CATEGORY_LABELS.other;
  const hue = channel.hue ?? ((channel.name || '').charCodeAt(0) * 37) % 360;

  return (
    <Glass depth={2} hover className="channel-card" onClick={() => onOpen?.(channel.id)}>
      <div className="channel-card__header">
        <div
          className="channel-card__avatar"
          style={{
            background: `linear-gradient(135deg, hsl(${hue}, 70%, 55%), hsl(${(hue + 40) % 360}, 60%, 45%))`,
          }}
        >
          {getInitial(channel.name)}
        </div>
        <div className="channel-card__info">
          <div className="channel-card__name">{channel.name}</div>
          <span
            className="channel-card__category"
            style={{ background: `${catColor}22`, color: catColor }}
          >
            {catLabel}
          </span>
        </div>
      </div>

      {channel.description && (
        <div className="channel-card__desc">{channel.description}</div>
      )}

      <div className="channel-card__footer">
        <span className="channel-card__subs">
          <Users size={14} strokeWidth={1.5} />
          {channel.subscribersCount ?? 0}
        </span>

        <button
          className={`channel-card__btn ${isSubscribed ? 'channel-card__btn--unsub' : ''} ${isOwned ? 'channel-card__btn--owned' : ''}`}
          onClick={(e) => {
            e.stopPropagation();
            onOpen?.(channel.id);
          }}
        >
          {isOwned ? 'Открыть' : isSubscribed ? 'Отписаться' : 'Подписаться'}
        </button>
      </div>
    </Glass>
  );
}
