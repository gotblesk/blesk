import { useState, useRef, useCallback } from 'react';
import { UsersThree, ArrowRight, Bell, BellSlash } from '@phosphor-icons/react';
import { motion } from 'framer-motion';
import API_URL from '../../config';
import './ChannelCard.css';

const CATEGORY_COLORS = { news: '#3b82f6', gaming: '#8b5cf6', music: '#ec4899', art: '#f59e0b', tech: '#06b6d4', other: '#6b7280' };
const CATEGORY_LABELS = { news: 'Новости', gaming: 'Игры', music: 'Музыка', art: 'Арт', tech: 'Технологии', other: 'Другое' };

function getHue(name) {
  return ((name || '').charCodeAt(0) * 37) % 360;
}

function formatSubs(n) {
  if (!n) return '0';
  if (n >= 1000) return `${(n / 1000).toFixed(1).replace('.0', '')}K`;
  return String(n);
}

export default function ChannelCard({ channel, variant = 'compact', isSubscribed, isOwned, onOpen, onSubscribe }) {
  const hue = channel.hue ?? getHue(channel.name);
  const catColor = CATEGORY_COLORS[channel.category] || CATEGORY_COLORS.other;
  const catLabel = CATEGORY_LABELS[channel.category] || CATEGORY_LABELS.other;
  const customCover = channel.channelMeta?.coverUrl || channel.coverUrl;
  const customAvatar = channel.channelMeta?.avatarUrl || channel.avatarUrl;
  const hasCover = variant === 'featured' || variant === 'tall' || !!customCover;
  const initial = (channel.name || '?')[0].toUpperCase();
  const subCount = channel.subscribersCount ?? channel.subscriberCount ?? 0;

  // 3D tilt parallax
  const [tilt, setTilt] = useState({ x: 0, y: 0 });
  const [mousePos, setMousePos] = useState({ x: 50, y: 50 });
  const cardRef = useRef(null);

  const handleMouseMove = useCallback((e) => {
    const rect = cardRef.current?.getBoundingClientRect();
    if (!rect) return;
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;
    setTilt({ x: (y - 0.5) * -8, y: (x - 0.5) * 8 });
    setMousePos({ x: x * 100, y: y * 100 });
  }, []);

  const handleMouseLeave = useCallback(() => {
    setTilt({ x: 0, y: 0 });
    setMousePos({ x: 50, y: 50 });
  }, []);

  return (
    <motion.div
      ref={cardRef}
      className={`mc mc--${variant}`}
      onClick={() => onOpen?.(channel.id)}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
      animate={{ rotateX: tilt.x, rotateY: tilt.y }}
      transition={{ type: 'spring', stiffness: 400, damping: 30 }}
      whileTap={{ scale: 0.98 }}
      style={{
        '--card-hue': hue,
        '--cat-color': catColor,
        '--mouse-x': `${mousePos.x}%`,
        '--mouse-y': `${mousePos.y}%`,
        cursor: (isSubscribed || isOwned) ? 'pointer' : 'default',
        transformPerspective: 800,
        transformStyle: 'preserve-3d',
      }}
    >
      {(isOwned || variant === 'featured') && <div className="mc__accent" />}
      {isOwned && <span className="mc__owner-badge">Ваш канал</span>}
      <div className="mc__glow" />

      {/* Cover */}
      {hasCover && (
        <div className={`mc__cover ${variant === 'featured' ? 'mc__cover--tall' : 'mc__cover--short'}`}>
          {customCover ? (
            <img className="mc__cover-bg mc__cover-bg--img" src={`${API_URL}${customCover}`} alt="" />
          ) : (
            <div className="mc__cover-bg" style={{ background: `linear-gradient(135deg, hsl(${hue}, 70%, 45%), hsl(${(hue + 50) % 360}, 60%, 30%))` }} />
          )}
          <div className="mc__cover-fade" />
          {!customCover && <div className="mc__cover-pattern" />}
          <div className="mc__subs-badge">
            <UsersThree size={10} weight="bold" />
            <span>{formatSubs(subCount)}</span>
          </div>
        </div>
      )}

      {/* Body */}
      <div className="mc__body">
        <div className="mc__head">
          <div className="mc__ava" style={!customAvatar ? { background: `linear-gradient(135deg, hsl(${hue}, 70%, 55%), hsl(${(hue + 40) % 360}, 60%, 45%))` } : {}}>
            {customAvatar ? (
              <img className="mc__ava-img" src={`${API_URL}${customAvatar}`} alt="" />
            ) : initial}
          </div>
          <div className="mc__info">
            <div className="mc__name">{channel.name}</div>
            <div className="mc__meta-row">
              <span className="mc__cat"><span className="mc__cat-dot" />{catLabel}</span>
              {/* Subscribers inline for non-cover cards */}
              {!hasCover && (
                <span className="mc__subs-inline">
                  <UsersThree size={10} weight="bold" />
                  {formatSubs(subCount)}
                </span>
              )}
            </div>
          </div>
        </div>

        {channel.description && (variant === 'medium' || variant === 'featured') && (
          <div className="mc__desc">{channel.description}</div>
        )}

        {variant === 'featured' && channel.lastPost && (
          <div className="mc__preview">
            <div className="mc__preview-label">Последний пост</div>
            <div className="mc__preview-text">{channel.lastPost}</div>
          </div>
        )}

        {/* Action row — icon-driven, not text buttons */}
        <div className="mc__actions">
          {isOwned ? (
            <motion.button
              className="mc__act mc__act--enter"
              onClick={(e) => { e.stopPropagation(); onOpen?.(channel.id); }}
              whileHover={{ scale: 1.05, x: 2 }}
              whileTap={{ scale: 0.92 }}
            >
              <ArrowRight size={14} weight="bold" />
              <span>Открыть</span>
            </motion.button>
          ) : isSubscribed ? (
            <motion.button
              className="mc__act mc__act--following"
              onClick={(e) => { e.stopPropagation(); onSubscribe?.(); }}
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.92 }}
            >
              <BellSlash size={13} weight="bold" className="mc__act-icon-hover" />
              <Bell size={13} weight="bold" className="mc__act-icon-default" />
              <span className="mc__act-text-default">Подписан</span>
              <span className="mc__act-text-hover">Отписаться</span>
            </motion.button>
          ) : (
            <motion.button
              className="mc__act mc__act--subscribe"
              onClick={(e) => { e.stopPropagation(); onSubscribe?.(); }}
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.92 }}
            >
              <Bell size={13} weight="bold" />
              <span>Подписаться</span>
            </motion.button>
          )}
        </div>
      </div>
    </motion.div>
  );
}
