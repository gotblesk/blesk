import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Plus, MagnifyingGlass, Radio, Newspaper, GameController, MusicNotes, Palette, Cpu, DotsThree, Sparkle, Link } from '@phosphor-icons/react';
import ChannelCard from './ChannelCard';
import CreateChannelModal from './CreateChannelModal';
import { ChannelGridSkeleton } from '../ui/GlassSkeleton';
import EmptyState from '../ui/EmptyState';
import { useChannelStore } from '../../store/channelStore';
import { getCurrentUserId } from '../../utils/auth';
import './ChannelBrowser.css';

const CATEGORIES = [
  { key: null, label: 'Все', icon: Sparkle, color: '#c8ff00' },
  { key: 'news', label: 'Новости', icon: Newspaper, color: '#3b82f6' },
  { key: 'gaming', label: 'Игры', icon: GameController, color: '#8b5cf6' },
  { key: 'music', label: 'Музыка', icon: MusicNotes, color: '#ec4899' },
  { key: 'art', label: 'Арт', icon: Palette, color: '#f59e0b' },
  { key: 'tech', label: 'Тех', icon: Cpu, color: '#06b6d4' },
  { key: 'other', label: 'Другое', icon: DotsThree, color: '#6b7280' },
];

const cardV = {
  hidden: { opacity: 0, y: 16, scale: 0.96 },
  visible: (i) => ({
    opacity: 1, y: 0, scale: 1,
    transition: { delay: i * 0.05, duration: 0.45, ease: [0.16, 1, 0.3, 1] },
  }),
};

export default function ChannelBrowser({ onOpenChannel }) {
  const [category, setCategory] = useState(null);
  const [search, setSearch] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [inviteLink, setInviteLink] = useState('');

  const { channels, myChannels, loadingBrowse, browseError, subscribe, unsubscribe } = useChannelStore();
  const userId = getCurrentUserId();

  // getState() — стабильная ссылка, не вызывает бесконечный цикл
  useEffect(() => { useChannelStore.getState().loadMyChannels(); }, []);

  useEffect(() => {
    const timeout = setTimeout(() => {
      useChannelStore.getState().loadBrowse({ sort: 'popular', category, search: search.trim() || undefined });
    }, search ? 300 : 0);
    return () => clearTimeout(timeout);
  }, [category, search]);

  const handleTabKeyDown = useCallback((e) => {
    const tabEls = [...e.currentTarget.querySelectorAll('[role="tab"]')];
    const current = tabEls.indexOf(e.target);
    if (current === -1) return;

    let next;
    if (e.key === 'ArrowRight') {
      next = (current + 1) % tabEls.length;
    } else if (e.key === 'ArrowLeft') {
      next = (current - 1 + tabEls.length) % tabEls.length;
    } else if (e.key === 'Home') {
      next = 0;
    } else if (e.key === 'End') {
      next = tabEls.length - 1;
    } else return;

    e.preventDefault();
    tabEls[next].focus();
    tabEls[next].click();
  }, []);

  const handleOpen = useCallback((id) => { onOpenChannel?.(id); }, [onOpenChannel]);

  const handleSubscribeToggle = useCallback((channel) => {
    const isSub = channel.isSubscribed || myChannels.some((c) => c.id === channel.id);
    if (isSub) unsubscribe(channel.id);
    else subscribe(channel.id);
  }, [myChannels, subscribe, unsubscribe]);

  const handleJoinByLink = useCallback(() => {
    if (!inviteLink.trim()) return;
    // TODO: POST /api/channels/join-by-link
    setInviteLink('');
  }, [inviteLink]);

  const allChannels = channels.map((ch) => {
    const isOwned = ch.ownerId === userId;
    const isSub = ch.isSubscribed || myChannels.some((c) => c.id === ch.id);
    return { ...ch, isOwned, isSub };
  });

  // Card variant for masonry height variation
  const getVariant = (ch, i) => {
    if (ch.isOwned) return 'featured';
    if (ch.isSub && i % 3 === 0) return 'tall';
    if (ch.subscribersCount > 50) return 'tall';
    if (ch.description) return 'medium';
    return 'compact';
  };

  return (
    <div className="mo">
      <div className="mo__head">
        <h2 className="mo__title">Каналы</h2>
        <motion.button className="mo__create" onClick={() => setCreateOpen(true)} whileHover={{ scale: 1.06 }} whileTap={{ scale: 0.9 }}>
          <span className="mo__create-bg" />
          <span className="mo__create-ray" />
          <span className="mo__create-icon"><Plus size={13} weight="bold" /></span>
          <span className="mo__create-text">Создать</span>
        </motion.button>
      </div>

      <div className="mo__search-wrap">
        <MagnifyingGlass size={15} weight="regular" className="mo__search-icon" />
        <input className="mo__search" placeholder="Найти канал..." value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Поиск каналов" onKeyDown={(e) => e.key === 'Escape' && setSearch('')} />
      </div>

      <div className="mo__cats" role="tablist" onKeyDown={handleTabKeyDown}>
        {CATEGORIES.map((cat) => {
          const isActive = category === cat.key;
          return (
            <motion.button
              key={cat.key ?? 'all'}
              className={`mo__cat ${isActive ? 'mo__cat--active' : ''}`}
              style={{ '--cat-color': cat.color }}
              onClick={() => setCategory(cat.key)}
              whileHover={{ y: -2 }}
              whileTap={{ scale: 0.9 }}
              layout
              role="tab"
              aria-selected={isActive}
              tabIndex={isActive ? 0 : -1}
            >
              <span className="mo__cat-icon"><cat.icon size={12} weight="bold" /></span>
              <span className="mo__cat-label">{cat.label}</span>
              {isActive && <motion.div className="mo__cat-bar" layoutId="catBar" />}
            </motion.button>
          );
        })}
      </div>

      {loadingBrowse && allChannels.length === 0 && (
        <ChannelGridSkeleton count={6} />
      )}

      {/* Error state */}
      {browseError && !loadingBrowse && allChannels.length === 0 && (
        <div style={{ textAlign: 'center', padding: 24, color: 'var(--danger)', fontSize: 13 }}>
          {browseError}
          <button onClick={() => useChannelStore.getState().loadBrowse({ category, search })} style={{ display: 'block', margin: '8px auto', background: 'none', border: '1px solid var(--danger)', borderRadius: 8, padding: '4px 12px', color: 'var(--danger)', cursor: 'pointer', fontSize: 12 }}>Повторить</button>
        </div>
      )}

      {/* Empty states */}
      {!loadingBrowse && !browseError && allChannels.length === 0 && (
        search.trim() ? (
          <EmptyState
            type="no-results"
            title={`Ничего не найдено по «${search.trim().slice(0, 30)}»`}
            subtitle="Попробуй другой запрос или создай свой канал"
          />
        ) : (
          <EmptyState
            type="no-channels"
            title="Каналов пока нет"
            subtitle="Создай первый канал и начни вещание"
            action={{ label: 'Создать канал', onClick: () => setCreateOpen(true) }}
          />
        )
      )}

      <div className="mo__grid">
        {allChannels.map((ch, i) => (
          <motion.div key={ch.id} custom={i} variants={cardV} initial="hidden" animate="visible" className="mo__grid-item">
            <ChannelCard
              channel={ch}
              variant={getVariant(ch, i)}
              isSubscribed={ch.isSub}
              isOwned={ch.isOwned}
              onOpen={() => handleOpen(ch.id)}
              onSubscribe={() => handleSubscribeToggle(ch)}
            />
          </motion.div>
        ))}
      </div>

      {/* Присоединиться по ссылке */}
      <div className="mo__invite">
        <Link size={16} className="mo__invite-icon" />
        <input
          className="mo__invite-input"
          placeholder="Вставьте ссылку-приглашение..."
          value={inviteLink}
          onChange={e => setInviteLink(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleJoinByLink()}
        />
        <button
          className="mo__invite-btn"
          onClick={handleJoinByLink}
          disabled={!inviteLink.trim()}
        >
          Присоединиться
        </button>
      </div>

      <AnimatePresence>
        {createOpen && (
          <CreateChannelModal
            onClose={() => setCreateOpen(false)}
            onCreated={(ch) => { setCreateOpen(false); handleOpen(ch.id); }}
          />
        )}
      </AnimatePresence>
    </div>
  );
}
