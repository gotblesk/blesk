import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ArrowLeft, GearSix, PaperPlaneTilt, Paperclip, UsersThree, Bell, BellSlash, PenNib, Hash, Camera, ImageSquare, Sparkle } from '@phosphor-icons/react';
import ChannelPost from './ChannelPost';
import ChannelMembersModal from './ChannelMembersModal';
import { useChannelStore } from '../../store/channelStore';
import { getCurrentUserId } from '../../utils/auth';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import './ChannelView.css';

const CATEGORY_LABELS = {
  gaming: 'Игры', music: 'Музыка', art: 'Искусство', tech: 'Технологии',
  education: 'Образование', entertainment: 'Развлечения', news: 'Новости',
  sports: 'Спорт', science: 'Наука', other: 'Другое',
};

const CATEGORY_COLORS = {
  gaming: '#8b5cf6', music: '#ec4899', art: '#f59e0b', tech: '#06b6d4',
  education: '#3b82f6', entertainment: '#c8ff00', news: '#3b82f6',
  sports: '#22c55e', science: '#a855f7', other: '#6b7280',
};

// Группировка постов по дате
function getDateGroup(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const postDay = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const diffDays = Math.floor((today - postDay) / 86400000);

  if (diffDays === 0) return 'Сегодня';
  if (diffDays === 1) return 'Вчера';

  const months = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня',
    'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];
  if (d.getFullYear() === now.getFullYear()) {
    return `${d.getDate()} ${months[d.getMonth()]}`;
  }
  return `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
}

export default function ChannelView({ channelId, onBack, user, socketRef }) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [postError, setPostError] = useState(null);
  const [composerOpen, setComposerOpen] = useState(false);
  const [showSticky, setShowSticky] = useState(false);
  const [showMembers, setShowMembers] = useState(false);
  const [isMuted, setIsMuted] = useState(false);
  const feedRef = useRef(null);
  const heroRef = useRef(null);
  const fileRef = useRef(null);
  const textareaRef = useRef(null);
  const avatarFileRef = useRef(null);
  const coverFileRef = useRef(null);
  const errorTimerRef = useRef(null);
  const heroImgRef = useRef(null);

  const { posts, loadPosts, deletePost, loadingPosts, postsError, myChannels, channels } = useChannelStore();
  const channelPosts = posts[channelId] || [];
  const isLoadingPosts = loadingPosts[channelId] || false;
  const loadPostsError = postsError[channelId] || null;

  const userId = getCurrentUserId();
  const channel = myChannels.find((c) => c.id === channelId) || channels.find((c) => c.id === channelId);
  const isOwner = channel?.ownerId === userId;
  const isAdmin = isOwner || channel?.userRole === 'admin';
  const isSubscribed = channel?.isSubscribed || myChannels.some((c) => c.id === channelId);
  const hue = channel ? ((channel.name || '').charCodeAt(0) * 37) % 360 : 0;
  const subCount = channel?.subscribersCount ?? channel?.subscriberCount ?? 0;
  const postCount = channelPosts.length;
  const categoryLabel = CATEGORY_LABELS[channel?.category] || '';
  const categoryColor = CATEGORY_COLORS[channel?.category] || '#6b7280';

  useEffect(() => { loadPosts(channelId); }, [channelId, loadPosts]);

  // Mute state
  useEffect(() => {
    if (!isSubscribed || isOwner) return;
    const stored = localStorage.getItem(`ch_muted_${channelId}`);
    if (stored !== null) setIsMuted(stored === 'true');
  }, [channelId, isSubscribed, isOwner]);

  const handleMuteToggle = useCallback(async () => {
    const next = !isMuted;
    setIsMuted(next);
    localStorage.setItem(`ch_muted_${channelId}`, String(next));
    try {
      await fetch(`${API_URL}/api/channels/${channelId}/mute`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
        credentials: 'include',
        body: JSON.stringify({ isMuted: next }),
      });
    } catch { /* UI уже обновлён */ }
  }, [channelId, isMuted]);

  // Auto-scroll on new posts
  useEffect(() => {
    const el = feedRef.current;
    if (!el) return;
    const isNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 100;
    if (isNearBottom) el.scrollTop = el.scrollHeight;
  }, [channelPosts.length]);

  // Sticky bar on scroll past hero
  useEffect(() => {
    const feed = feedRef.current;
    if (!feed) return;
    const handleScroll = () => {
      const heroHeight = heroRef.current?.offsetHeight || 280;
      setShowSticky(feed.scrollTop > heroHeight - 60);
    };
    feed.addEventListener('scroll', handleScroll, { passive: true });
    return () => feed.removeEventListener('scroll', handleScroll);
  }, []);

  // Hero parallax (subtle, 3px max)
  const prefersReducedMotion = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches;
  const handleHeroMouseMove = useCallback((e) => {
    if (prefersReducedMotion || !heroImgRef.current) return;
    const rect = heroRef.current?.getBoundingClientRect();
    if (!rect) return;
    const x = ((e.clientX - rect.left) / rect.width - 0.5) * 3;
    const y = ((e.clientY - rect.top) / rect.height - 0.5) * 3;
    heroImgRef.current.style.transform = `translate(${x}px, ${y}px) scale(1.05)`;
  }, [prefersReducedMotion]);

  const handleHeroMouseLeave = useCallback(() => {
    if (heroImgRef.current) heroImgRef.current.style.transform = 'translate(0,0) scale(1.05)';
  }, []);

  // Auto-focus textarea
  useEffect(() => {
    if (composerOpen && textareaRef.current) textareaRef.current.focus();
  }, [composerOpen]);

  const showPostError = useCallback((msg) => {
    if (errorTimerRef.current) clearTimeout(errorTimerRef.current);
    setPostError(msg);
    errorTimerRef.current = setTimeout(() => setPostError(null), 4000);
  }, []);

  const handleSend = useCallback(async () => {
    const trimmed = text.trim();
    if (!trimmed || sending) return;
    setSending(true);
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/posts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
        credentials: 'include',
        body: JSON.stringify({ text: trimmed }),
      });
      if (!res.ok) {
        let errData;
        try { errData = await res.json(); } catch { errData = {}; }
        showPostError(errData.error || 'Не удалось отправить');
        return;
      }
      let data;
      try { data = await res.json(); } catch {
        showPostError('Ошибка сервера');
        return;
      }
      if (data.message) useChannelStore.getState().receivePost(data.message);
      setText('');
      setComposerOpen(false);
    } catch {
      showPostError('Ошибка сети');
    } finally { setSending(false); }
  }, [text, channelId, sending, showPostError]);

  const handleKeyDown = useCallback((e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); }
    if (e.key === 'Escape') setComposerOpen(false);
  }, [handleSend]);

  const handleFileUpload = useCallback(async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/upload`, {
        method: 'POST',
        headers: { ...getAuthHeaders() },
        credentials: 'include',
        body: formData,
      });
      if (!res.ok) throw new Error();
      const data = await res.json();
      if (data.message) useChannelStore.getState().receivePost(data.message);
    } catch { /* ignore */ }
    if (fileRef.current) fileRef.current.value = '';
  }, [channelId]);

  const handleAvatarUpload = useCallback(async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('avatar', file);
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/avatar`, {
        method: 'POST', headers: { ...getAuthHeaders() }, credentials: 'include', body: formData,
      });
      if (!res.ok) { showPostError('Не удалось загрузить аватар'); return; }
      const data = await res.json();
      if (data.avatarUrl) useChannelStore.getState().updateChannelLocal(channelId, { avatarUrl: data.avatarUrl });
    } catch { showPostError('Ошибка сети'); }
    if (avatarFileRef.current) avatarFileRef.current.value = '';
  }, [channelId, showPostError]);

  const handleCoverUpload = useCallback(async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('cover', file);
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/cover`, {
        method: 'POST', headers: { ...getAuthHeaders() }, credentials: 'include', body: formData,
      });
      if (!res.ok) { showPostError('Не удалось загрузить обложку'); return; }
      const data = await res.json();
      if (data.coverUrl) useChannelStore.getState().updateChannelLocal(channelId, { coverUrl: data.coverUrl });
    } catch { showPostError('Ошибка сети'); }
    if (coverFileRef.current) coverFileRef.current.value = '';
  }, [channelId, showPostError]);

  const coverUrl = channel?.channelMeta?.coverUrl || channel?.coverUrl;
  const avatarUrl = channel?.channelMeta?.avatarUrl || channel?.avatarUrl;

  // Группировка постов по датам
  const groupedPosts = useMemo(() => {
    const groups = [];
    let lastGroup = null;
    for (const post of channelPosts) {
      const group = getDateGroup(post.createdAt);
      if (group !== lastGroup) {
        groups.push({ type: 'date', label: group, id: `date-${post.id}` });
        lastGroup = group;
      }
      groups.push({ type: 'post', post });
    }
    return groups;
  }, [channelPosts]);

  return (
    <div className="cv">
      {/* ═══ Hero Banner — Editorial Masthead ═══ */}
      <motion.div
        className="cv__hero"
        ref={heroRef}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.5 }}
        onMouseMove={handleHeroMouseMove}
        onMouseLeave={handleHeroMouseLeave}
      >
        {coverUrl ? (
          <img className="cv__hero-bg cv__hero-bg--img" ref={heroImgRef} src={`${API_URL}${coverUrl}`} alt="" />
        ) : (
          <div className="cv__hero-bg" ref={heroImgRef} style={{ background: `linear-gradient(135deg, hsl(${hue}, 65%, 35%) 0%, hsl(${(hue + 60) % 360}, 50%, 20%) 100%)` }} />
        )}
        <div className="cv__hero-fade" />
        <div className="cv__hero-pattern" />

        {/* Cover upload button (owner) */}
        {isOwner && (
          <>
            <input type="file" ref={coverFileRef} hidden accept="image/jpeg,image/png,image/webp" onChange={handleCoverUpload} />
            <motion.button className="cv__cover-edit" onClick={() => coverFileRef.current?.click()} whileHover={{ scale: 1.08 }} whileTap={{ scale: 0.9 }} title="Сменить обложку">
              <ImageSquare size={14} weight="regular" />
            </motion.button>
          </>
        )}

        <motion.button className="cv__back" onClick={onBack} whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }}>
          <ArrowLeft size={18} weight="bold" />
        </motion.button>

        {/* Channel info overlay */}
        <div className="cv__hero-info">
          <div
            className={`cv__hero-ava ${isOwner ? 'cv__hero-ava--editable' : ''}`}
            style={!avatarUrl ? { background: `linear-gradient(135deg, hsl(${hue}, 70%, 55%), hsl(${(hue + 40) % 360}, 60%, 45%))` } : {}}
            onClick={isOwner ? () => avatarFileRef.current?.click() : undefined}
          >
            {avatarUrl ? (
              <img src={`${API_URL}${avatarUrl}`} alt="" className="cv__hero-ava-img" />
            ) : (
              (channel?.name || '?')[0].toUpperCase()
            )}
            {isOwner && <div className="cv__hero-ava-overlay"><Camera size={16} weight="regular" /></div>}
          </div>
          {isOwner && <input type="file" ref={avatarFileRef} hidden accept="image/jpeg,image/png,image/webp" onChange={handleAvatarUpload} />}
          <div className="cv__hero-text">
            <h1 className="cv__hero-name">{channel?.name || 'Канал'}</h1>
            {channel?.description && (
              <p className="cv__hero-desc">{channel.description}</p>
            )}
            <div className="cv__hero-meta">
              {categoryLabel && (
                <span className="cv__hero-cat" style={{ '--cat-color': categoryColor }}>
                  <Sparkle size={10} weight="fill" />
                  {categoryLabel}
                </span>
              )}
              <button className="cv__hero-stat cv__hero-stat--btn" onClick={() => setShowMembers(true)} title="Подписчики">
                <UsersThree size={12} weight="bold" />
                {subCount}
              </button>
              <span className="cv__hero-stat">
                <Hash size={12} weight="bold" />
                {postCount}
              </span>
            </div>
          </div>

          {!isOwner && (
            <div className="cv__hero-actions">
              {isSubscribed && (
                <motion.button
                  className={`cv__mute ${isMuted ? 'cv__mute--active' : ''}`}
                  onClick={handleMuteToggle}
                  whileHover={{ scale: 1.08 }}
                  whileTap={{ scale: 0.9 }}
                  title={isMuted ? 'Включить уведомления' : 'Отключить уведомления'}
                >
                  {isMuted ? <BellSlash size={14} weight="bold" /> : <Bell size={14} weight="bold" />}
                </motion.button>
              )}
              <motion.button
                className={`cv__sub ${isSubscribed ? 'cv__sub--active' : ''}`}
                onClick={isSubscribed ? () => useChannelStore.getState().unsubscribe(channelId) : () => useChannelStore.getState().subscribe(channelId)}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.92 }}
              >
                <span>{isSubscribed ? 'Отписаться' : 'Подписаться'}</span>
              </motion.button>
            </div>
          )}
        </div>
      </motion.div>

      {/* ═══ Post Feed ═══ */}
      <div className="cv__feed" ref={feedRef}>
        {/* Sticky channel bar */}
        <div className={`cv__sticky-bar ${showSticky ? 'cv__sticky-bar--visible' : ''}`}>
          <div className="cv__sticky-ava" style={!avatarUrl ? { background: `linear-gradient(135deg, hsl(${hue}, 70%, 55%), hsl(${(hue + 40) % 360}, 60%, 45%))` } : {}}>
            {avatarUrl ? (
              <img src={`${API_URL}${avatarUrl}`} alt="" className="cv__sticky-ava-img" />
            ) : (
              <span>{(channel?.name || '?')[0].toUpperCase()}</span>
            )}
          </div>
          <span className="cv__sticky-name">{channel?.name}</span>
          <span className="cv__sticky-stats">{subCount} подписчиков</span>
          {!isOwner && (
            <button
              className="cv__sticky-sub"
              onClick={isSubscribed ? () => useChannelStore.getState().unsubscribe(channelId) : () => useChannelStore.getState().subscribe(channelId)}
            >
              {isSubscribed ? 'Отписаться' : 'Подписаться'}
            </button>
          )}
        </div>

        {channelPosts.length === 0 ? (
          <motion.div className="cv__empty" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
            <div className="cv__empty-icon">
              <PenNib size={32} weight="regular" />
            </div>
            <span className="cv__empty-title">Пока нет постов</span>
            <span className="cv__empty-hint">{isOwner ? 'Создайте первую публикацию' : 'Посты скоро появятся'}</span>
          </motion.div>
        ) : (
          groupedPosts.map((item, i) =>
            item.type === 'date' ? (
              <div key={item.id} className="cv__date-group">
                <span className="cv__date-label">{item.label}</span>
              </div>
            ) : (
              <ChannelPost
                key={item.post.id}
                post={item.post}
                index={i}
                isOwner={isAdmin}
                onDelete={(postId) => deletePost(channelId, postId, socketRef)}
              />
            )
          )
        )}

        {/* Loading state */}
        {isLoadingPosts && channelPosts.length === 0 && (
          <div className="cv__loading">
            <div className="cv__spinner" />
          </div>
        )}

        {/* Error state */}
        {loadPostsError && channelPosts.length === 0 && (
          <div className="cv__error">
            {loadPostsError}
            <button className="cv__error-retry" onClick={() => loadPosts(channelId)}>Повторить</button>
          </div>
        )}
      </div>

      {/* ═══ FAB ═══ */}
      {isOwner && !composerOpen && (
        <motion.button
          className="cv__fab"
          onClick={() => setComposerOpen(true)}
          initial={{ scale: 0, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: 'spring', stiffness: 400, damping: 15 }}
          whileHover={{ scale: 1.1 }}
          whileTap={{ scale: 0.9 }}
        >
          <PenNib size={18} weight="bold" />
        </motion.button>
      )}

      {/* ═══ Composer ═══ */}
      <AnimatePresence>
        {isOwner && composerOpen && (
          <motion.div
            className="cv__composer"
            initial={{ y: '100%', opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: '100%', opacity: 0 }}
            transition={{ type: 'spring', damping: 25, stiffness: 300 }}
          >
            <div className="cv__composer-glass">
              <div className="cv__composer-head">
                <span className="cv__composer-title">Новая публикация</span>
                <motion.button className="cv__composer-close" onClick={() => setComposerOpen(false)} whileTap={{ scale: 0.85 }}>
                  Отмена
                </motion.button>
              </div>

              <AnimatePresence>
                {postError && (
                  <motion.div className="cv__composer-error" initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }} exit={{ opacity: 0, height: 0 }}>
                    {postError}
                  </motion.div>
                )}
              </AnimatePresence>

              <textarea
                ref={textareaRef}
                className="cv__composer-textarea"
                placeholder="О чём хотите рассказать?"
                value={text}
                onChange={(e) => setText(e.target.value.slice(0, 4000))}
                onKeyDown={handleKeyDown}
                rows={4}
                maxLength={4000}
              />

              <div className="cv__composer-bar">
                <motion.button className="cv__composer-attach" onClick={() => fileRef.current?.click()} whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }}>
                  <Paperclip size={16} weight="regular" />
                </motion.button>
                <input type="file" ref={fileRef} hidden onChange={handleFileUpload} />

                <span className="cv__composer-count">{text.length}/4000</span>

                <motion.button
                  className="cv__composer-send"
                  onClick={handleSend}
                  disabled={!text.trim() || sending}
                  whileHover={text.trim() ? { scale: 1.05 } : {}}
                  whileTap={text.trim() ? { scale: 0.92 } : {}}
                >
                  {sending ? '...' : 'Опубликовать'}
                  <PaperPlaneTilt size={14} weight="bold" />
                </motion.button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {showMembers && (
        <ChannelMembersModal channelId={channelId} onClose={() => setShowMembers(false)} />
      )}
    </div>
  );
}
