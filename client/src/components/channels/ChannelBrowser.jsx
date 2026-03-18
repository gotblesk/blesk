import { useState, useEffect, useCallback } from 'react';
import { Plus, Search, ChevronDown, ChevronUp, Megaphone, Loader } from 'lucide-react';
import Glass from '../ui/Glass';
import ChannelCard from './ChannelCard';
import CreateChannelModal from './CreateChannelModal';
import { useChannelStore } from '../../store/channelStore';
import { getCurrentUserId } from '../../utils/auth';
import './ChannelBrowser.css';

const SORT_TABS = [
  { key: 'popular', label: 'Популярные' },
  { key: 'growing', label: 'Растущие' },
  { key: 'new', label: 'Новые' },
];

const CATEGORIES = [
  { key: 'news', label: 'Новости' },
  { key: 'gaming', label: 'Игры' },
  { key: 'music', label: 'Музыка' },
  { key: 'art', label: 'Арт' },
  { key: 'tech', label: 'Технологии' },
  { key: 'other', label: 'Другое' },
];

export default function ChannelBrowser({ onOpenChannel }) {
  const [sort, setSort] = useState('popular');
  const [category, setCategory] = useState(null);
  const [search, setSearch] = useState('');
  const [myOpen, setMyOpen] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);

  const {
    channels,
    myChannels,
    loadingBrowse,
    loadBrowse,
    loadMyChannels,
    subscribe,
    unsubscribe,
  } = useChannelStore();

  const userId = getCurrentUserId();

  useEffect(() => {
    loadMyChannels();
  }, [loadMyChannels]);

  useEffect(() => {
    const timeout = setTimeout(() => {
      loadBrowse({ sort, category, search: search.trim() || undefined });
    }, search ? 300 : 0);
    return () => clearTimeout(timeout);
  }, [sort, category, search, loadBrowse]);

  const handleOpen = useCallback((id) => {
    onOpenChannel?.(id);
  }, [onOpenChannel]);

  const handleAction = useCallback((channel) => {
    const isOwned = channel.ownerId === userId;
    const isSub = channel.isSubscribed || myChannels.some((c) => c.id === channel.id);
    if (isOwned || isSub) {
      handleOpen(channel.id);
    }
  }, [userId, myChannels, handleOpen]);

  const handleSubscribeToggle = useCallback((channel) => {
    const isSub = channel.isSubscribed || myChannels.some((c) => c.id === channel.id);
    if (isSub) {
      unsubscribe(channel.id);
    } else {
      subscribe(channel.id);
    }
  }, [myChannels, subscribe, unsubscribe]);

  return (
    <div className="channel-browser section-enter">
      {/* Шапка */}
      <div className="channel-browser__header">
        <h2 className="channel-browser__title">Каналы</h2>
        <button className="channel-browser__create" onClick={() => setCreateOpen(true)}>
          <Plus size={18} strokeWidth={2} />
        </button>
      </div>

      {/* Поиск */}
      <div className="channel-browser__search-wrap">
        <Search size={16} strokeWidth={1.5} className="channel-browser__search-icon" />
        <input
          className="channel-browser__search"
          placeholder="Найти канал..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      {/* Сортировка */}
      <div className="channel-browser__tabs">
        {SORT_TABS.map((tab) => (
          <Glass
            key={tab.key}
            depth={sort === tab.key ? 2 : 1}
            radius={10}
            className={`channel-browser__tab ${sort === tab.key ? 'channel-browser__tab--active' : ''}`}
            onClick={() => setSort(tab.key)}
          >
            {tab.label}
          </Glass>
        ))}
      </div>

      {/* Категории */}
      <div className="channel-browser__categories">
        {CATEGORIES.map((cat) => (
          <button
            key={cat.key}
            className={`channel-browser__chip ${category === cat.key ? 'channel-browser__chip--active' : ''}`}
            onClick={() => setCategory(category === cat.key ? null : cat.key)}
          >
            {cat.label}
          </button>
        ))}
      </div>

      <div className="channel-browser__body">
        {/* Мои каналы */}
        {myChannels.length > 0 && (
          <div className="channel-browser__section">
            <button className="channel-browser__section-toggle" onClick={() => setMyOpen(!myOpen)}>
              <span className="channel-browser__section-label">Мои каналы</span>
              {myOpen ? <ChevronUp size={16} strokeWidth={1.5} /> : <ChevronDown size={16} strokeWidth={1.5} />}
            </button>
            {myOpen && (
              <div className="channel-browser__grid">
                {myChannels.map((ch) => (
                  <ChannelCard
                    key={ch.id}
                    channel={ch}
                    onOpen={handleOpen}
                    isSubscribed={true}
                    isOwned={ch.ownerId === userId}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {/* Обзор */}
        <div className="channel-browser__section">
          <div className="channel-browser__section-label">Обзор</div>
          {loadingBrowse ? (
            <div className="channel-browser__loading">
              <Loader size={20} strokeWidth={1.5} className="channel-browser__spinner" />
            </div>
          ) : channels.length === 0 ? (
            <div className="channel-browser__empty">
              <Megaphone size={32} strokeWidth={1.5} />
              <span>Каналы не найдены</span>
            </div>
          ) : (
            <div className="channel-browser__grid">
              {channels.map((ch) => {
                const isOwned = ch.ownerId === userId;
                const isSub = ch.isSubscribed || myChannels.some((c) => c.id === ch.id);
                return (
                  <ChannelCard
                    key={ch.id}
                    channel={ch}
                    onOpen={() => {
                      if (isOwned || isSub) {
                        handleOpen(ch.id);
                      } else {
                        handleSubscribeToggle(ch);
                      }
                    }}
                    isSubscribed={isSub}
                    isOwned={isOwned}
                  />
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* Модалка создания */}
      {createOpen && (
        <CreateChannelModal
          onClose={() => setCreateOpen(false)}
          onCreated={(ch) => {
            setCreateOpen(false);
            handleOpen(ch.id);
          }}
        />
      )}
    </div>
  );
}
