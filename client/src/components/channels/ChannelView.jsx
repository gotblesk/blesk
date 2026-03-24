import { useState, useEffect, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ArrowLeft, Settings, Send, Paperclip, Users, Bell, BellOff, PenLine, Hash, Camera, ImagePlus } from 'lucide-react';
import ChannelPost from './ChannelPost';
import { useChannelStore } from '../../store/channelStore';
import { getCurrentUserId } from '../../utils/auth';
import API_URL from '../../config';
import './ChannelView.css';

export default function ChannelView({ channelId, onBack, user, socketRef }) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [postError, setPostError] = useState(null);
  const [composerOpen, setComposerOpen] = useState(false);
  const feedRef = useRef(null);
  const fileRef = useRef(null);
  const textareaRef = useRef(null);
  const avatarFileRef = useRef(null);
  const coverFileRef = useRef(null);

  const { posts, loadPosts, myChannels, channels } = useChannelStore();
  const channelPosts = posts[channelId] || [];

  const userId = getCurrentUserId();
  const channel = myChannels.find((c) => c.id === channelId) || channels.find((c) => c.id === channelId);
  const isOwner = channel?.ownerId === userId;
  const isSubscribed = channel?.isSubscribed || myChannels.some((c) => c.id === channelId);
  const hue = channel ? ((channel.name || '').charCodeAt(0) * 37) % 360 : 0;
  const subCount = channel?.subscribersCount ?? channel?.subscriberCount ?? 0;
  const postCount = channelPosts.length;

  useEffect(() => { loadPosts(channelId); }, [channelId, loadPosts]);

  useEffect(() => {
    if (feedRef.current) feedRef.current.scrollTop = feedRef.current.scrollHeight;
  }, [channelPosts.length]);

  // Auto-focus textarea when composer opens
  useEffect(() => {
    if (composerOpen && textareaRef.current) textareaRef.current.focus();
  }, [composerOpen]);

  const handleSend = useCallback(async () => {
    const trimmed = text.trim();
    if (!trimmed || sending) return;
    setSending(true);
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/posts`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('token')}` },
        body: JSON.stringify({ text: trimmed }),
      });
      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        setPostError(errData.error || 'Не удалось отправить');
        setTimeout(() => setPostError(null), 4000);
        return;
      }
      const data = await res.json().catch(() => ({}));
      if (data.message) useChannelStore.getState().receivePost(data.message);
      setText('');
      setComposerOpen(false);
    } catch {
      setPostError('Ошибка сети');
      setTimeout(() => setPostError(null), 4000);
    } finally { setSending(false); }
  }, [text, channelId, sending]);

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
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
        body: formData,
      });
      if (!res.ok) throw new Error();
      const data = await res.json();
      if (data.message) useChannelStore.getState().receivePost(data.message);
    } catch { /* ignore */ }
    if (fileRef.current) fileRef.current.value = '';
  }, [channelId]);

  // Upload channel avatar
  const handleAvatarUpload = useCallback(async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('avatar', file);
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/avatar`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
        body: formData,
      });
      if (!res.ok) return;
      const data = await res.json();
      if (data.avatarUrl) useChannelStore.getState().updateChannelLocal(channelId, { avatarUrl: data.avatarUrl });
    } catch { /* ignore */ }
    if (avatarFileRef.current) avatarFileRef.current.value = '';
  }, [channelId]);

  // Upload channel cover
  const handleCoverUpload = useCallback(async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const formData = new FormData();
    formData.append('cover', file);
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/cover`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
        body: formData,
      });
      if (!res.ok) return;
      const data = await res.json();
      if (data.coverUrl) useChannelStore.getState().updateChannelLocal(channelId, { coverUrl: data.coverUrl });
    } catch { /* ignore */ }
    if (coverFileRef.current) coverFileRef.current.value = '';
  }, [channelId]);

  const coverUrl = channel?.channelMeta?.coverUrl || channel?.coverUrl;
  const avatarUrl = channel?.channelMeta?.avatarUrl || channel?.avatarUrl;

  return (
    <div className="cv">
      {/* ═══ Hero Banner ═══ */}
      <motion.div
        className="cv__hero"
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.5 }}
      >
        {/* Cover: custom image or gradient fallback */}
        {coverUrl ? (
          <img className="cv__hero-bg cv__hero-bg--img" src={`${API_URL}${coverUrl}`} alt="" />
        ) : (
          <div className="cv__hero-bg" style={{ background: `linear-gradient(135deg, hsl(${hue}, 65%, 35%) 0%, hsl(${(hue + 60) % 360}, 50%, 20%) 100%)` }} />
        )}
        <div className="cv__hero-fade" />
        <div className="cv__hero-pattern" />

        {/* Cover upload button (owner) */}
        {isOwner && (
          <>
            <input type="file" ref={coverFileRef} hidden accept="image/jpeg,image/png,image/webp" onChange={handleCoverUpload} />
            <motion.button className="cv__cover-edit" onClick={() => coverFileRef.current?.click()} whileHover={{ scale: 1.08 }} whileTap={{ scale: 0.9 }} title="Сменить обложку">
              <ImagePlus size={14} strokeWidth={1.5} />
            </motion.button>
          </>
        )}

        {/* Back button */}
        <motion.button className="cv__back" onClick={onBack} whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }}>
          <ArrowLeft size={18} strokeWidth={2} />
        </motion.button>

        {/* Settings (owner) */}
        {isOwner && (
          <motion.button className="cv__settings" whileHover={{ scale: 1.1, rotate: 30 }} whileTap={{ scale: 0.9 }}>
            <Settings size={16} strokeWidth={1.5} />
          </motion.button>
        )}

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
            {isOwner && <div className="cv__hero-ava-overlay"><Camera size={14} strokeWidth={1.5} /></div>}
          </div>
          {isOwner && <input type="file" ref={avatarFileRef} hidden accept="image/jpeg,image/png,image/webp" onChange={handleAvatarUpload} />}
          <div className="cv__hero-text">
            <h1 className="cv__hero-name">{channel?.name || 'Канал'}</h1>
            <div className="cv__hero-stats">
              <span className="cv__hero-stat">
                <Users size={12} strokeWidth={2} />
                {subCount} подписчиков
              </span>
              <span className="cv__hero-stat">
                <Hash size={12} strokeWidth={2} />
                {postCount} постов
              </span>
            </div>
          </div>

          {/* Subscribe button */}
          {!isOwner && (
            <motion.button
              className={`cv__sub ${isSubscribed ? 'cv__sub--active' : ''}`}
              onClick={isSubscribed ? () => useChannelStore.getState().unsubscribe(channelId) : () => useChannelStore.getState().subscribe(channelId)}
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.92 }}
            >
              {isSubscribed ? <BellOff size={14} strokeWidth={2} /> : <Bell size={14} strokeWidth={2} />}
              <span>{isSubscribed ? 'Отписаться' : 'Подписаться'}</span>
            </motion.button>
          )}
        </div>
      </motion.div>

      {/* ═══ Post Feed ═══ */}
      <div className="cv__feed" ref={feedRef}>
        {channelPosts.length === 0 ? (
          <motion.div className="cv__empty" initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
            <div className="cv__empty-icon">
              <PenLine size={24} strokeWidth={1.2} />
            </div>
            <span className="cv__empty-title">Пока пусто</span>
            <span className="cv__empty-hint">{isOwner ? 'Напишите первый пост!' : 'Посты скоро появятся'}</span>
          </motion.div>
        ) : (
          channelPosts.map((post, i) => (
            <ChannelPost key={post.id} post={post} index={i} />
          ))
        )}
      </div>

      {/* ═══ Floating Compose Button (owner) ═══ */}
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
          <PenLine size={18} strokeWidth={2} />
        </motion.button>
      )}

      {/* ═══ Composer Panel (owner) ═══ */}
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
                <span className="cv__composer-title">Новый пост</span>
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
                placeholder="О чём расскажете?"
                value={text}
                onChange={(e) => setText(e.target.value)}
                onKeyDown={handleKeyDown}
                rows={3}
              />

              <div className="cv__composer-bar">
                <motion.button className="cv__composer-attach" onClick={() => fileRef.current?.click()} whileHover={{ scale: 1.1 }} whileTap={{ scale: 0.9 }}>
                  <Paperclip size={16} strokeWidth={1.5} />
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
                  <Send size={14} strokeWidth={2} />
                </motion.button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
