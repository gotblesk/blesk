import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Plus, Search, Loader, Radio, Newspaper, Gamepad2, Music, Palette, Cpu, MoreHorizontal, Sparkles } from 'lucide-react';
import ChannelCard from './ChannelCard';
import CreateChannelModal from './CreateChannelModal';
import { useChannelStore } from '../../store/channelStore';
import { getCurrentUserId } from '../../utils/auth';
import './ChannelBrowser.css';

const CATEGORIES = [
  { key: null, label: 'Все', icon: Sparkles, color: '#c8ff00' },
  { key: 'news', label: 'Новости', icon: Newspaper, color: '#3b82f6' },
  { key: 'gaming', label: 'Игры', icon: Gamepad2, color: '#8b5cf6' },
  { key: 'music', label: 'Музыка', icon: Music, color: '#ec4899' },
  { key: 'art', label: 'Арт', icon: Palette, color: '#f59e0b' },
  { key: 'tech', label: 'Тех', icon: Cpu, color: '#06b6d4' },
  { key: 'other', label: 'Другое', icon: MoreHorizontal, color: '#6b7280' },
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

  const { channels, myChannels, loadingBrowse, browseError, loadBrowse, loadMyChannels, subscribe, unsubscribe } = useChannelStore();
  const userId = getCurrentUserId();

  useEffect(() => { loadMyChannels(); }, [loadMyChannels]);

  useEffect(() => {
    const timeout = setTimeout(() => {
      loadBrowse({ sort: 'popular', category, search: search.trim() || undefined });
    }, search ? 300 : 0);
    return () => clearTimeout(timeout);
  }, [category, search, loadBrowse]);

  const handleOpen = useCallback((id) => { onOpenChannel?.(id); }, [onOpenChannel]);

  const handleSubscribeToggle = useCallback((channel) => {
    const isSub = channel.isSubscribed || myChannels.some((c) => c.id === channel.id);
    if (isSub) unsubscribe(channel.id);
    else subscribe(channel.id);
  }, [myChannels, subscribe, unsubscribe]);

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
          <span className="mo__create-icon"><Plus size={13} strokeWidth={3} /></span>
          <span className="mo__create-text">Создать</span>
        </motion.button>
      </div>

      <div className="mo__search-wrap">
        <Search size={15} strokeWidth={1.5} className="mo__search-icon" />
        <input className="mo__search" placeholder="Найти канал..." value={search} onChange={(e) => setSearch(e.target.value)} />
      </div>

      <div className="mo__cats">
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
            >
              <span className="mo__cat-icon"><cat.icon size={12} strokeWidth={2} /></span>
              <span className="mo__cat-label">{cat.label}</span>
              {isActive && <motion.div className="mo__cat-bar" layoutId="catBar" />}
            </motion.button>
          );
        })}
      </div>

      {loadingBrowse && allChannels.length === 0 && (
        <div className="mo__loader">
          <motion.div animate={{ rotate: 360 }} transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}>
            <Loader size={20} strokeWidth={1.5} />
          </motion.div>
        </div>
      )}

      {/* Error state */}
      {browseError && !loadingBrowse && allChannels.length === 0 && (
        <div style={{ textAlign: 'center', padding: 24, color: 'var(--danger)', fontSize: 13 }}>
          {browseError}
          <button onClick={() => loadBrowse({ category, search })} style={{ display: 'block', margin: '8px auto', background: 'none', border: '1px solid var(--danger)', borderRadius: 8, padding: '4px 12px', color: 'var(--danger)', cursor: 'pointer', fontSize: 12 }}>Повторить</button>
        </div>
      )}

      {/* Empty states */}
      {!loadingBrowse && !browseError && allChannels.length === 0 && (
        <motion.div className="mo__empty" initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} onClick={search.trim() ? undefined : () => setCreateOpen(true)} style={{ cursor: search.trim() ? 'default' : 'pointer' }}>
          <div className="mo__empty-icon"><Radio size={28} strokeWidth={1.2} /></div>
          <span>{search.trim() ? `Ничего не найдено по запросу «${search.trim().slice(0, 30)}»` : 'Каналы не найдены'}</span>
          {!search.trim() && <span className="mo__empty-hint">Создай первый!</span>}
        </motion.div>
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
