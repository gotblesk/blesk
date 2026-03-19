import { useState, useEffect, useRef, useCallback } from 'react';
import { ArrowLeft, Settings, Send, Paperclip, Users } from 'lucide-react';
import Glass from '../ui/Glass';
import ChannelPost from './ChannelPost';
import { useChannelStore } from '../../store/channelStore';
import { getCurrentUserId } from '../../utils/auth';
import API_URL from '../../config';
import './ChannelView.css';

export default function ChannelView({ channelId, onBack, user, socketRef }) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const feedRef = useRef(null);
  const fileRef = useRef(null);

  const { posts, loadPosts, myChannels, channels } = useChannelStore();
  const channelPosts = posts[channelId] || [];

  const userId = getCurrentUserId();
  const channel = myChannels.find((c) => c.id === channelId) ||
    channels.find((c) => c.id === channelId);

  const isOwner = channel?.ownerId === userId;

  useEffect(() => {
    loadPosts(channelId);
  }, [channelId, loadPosts]);

  // Прокрутка вниз при новых постах
  useEffect(() => {
    if (feedRef.current) {
      feedRef.current.scrollTop = feedRef.current.scrollHeight;
    }
  }, [channelPosts.length]);

  const handleSend = useCallback(async () => {
    const trimmed = text.trim();
    if (!trimmed || sending) return;

    setSending(true);
    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/posts`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('token')}`,
        },
        body: JSON.stringify({ text: trimmed }),
      });
      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        alert(errData.error || 'Не удалось отправить пост');
        return;
      }
      // Не вызываем receivePost — сокет message:new с isChannel придёт автоматически
      setText('');
    } catch (err) {
      console.error('Ошибка отправки поста:', err);
      alert('Ошибка сети');
    } finally {
      setSending(false);
    }
  }, [text, channelId, sending]);

  const handleKeyDown = useCallback((e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }, [handleSend]);

  const handleFileUpload = useCallback(async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const formData = new FormData();
    formData.append('file', file);

    try {
      const res = await fetch(`${API_URL}/api/channels/${channelId}/upload`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token')}`,
        },
        body: formData,
      });
      if (!res.ok) throw new Error();
      const data = await res.json();
      if (data.message) {
        useChannelStore.getState().receivePost(data.message);
      }
    } catch (err) {
      console.error('Ошибка загрузки файла:', err);
    }

    if (fileRef.current) fileRef.current.value = '';
  }, [channelId]);

  const handleSubscribe = useCallback(() => {
    useChannelStore.getState().subscribe(channelId);
  }, [channelId]);

  const handleUnsubscribe = useCallback(() => {
    useChannelStore.getState().unsubscribe(channelId);
  }, [channelId]);

  const isSubscribed = channel?.isSubscribed ||
    myChannels.some((c) => c.id === channelId);

  const hue = channel ? ((channel.name || '').charCodeAt(0) * 37) % 360 : 0;

  return (
    <div className="channel-view section-enter">
      {/* Шапка */}
      <Glass depth={2} radius={16} className="channel-view__header">
        <button className="channel-view__back" onClick={onBack}>
          <ArrowLeft size={20} strokeWidth={1.5} />
        </button>

        <div
          className="channel-view__avatar"
          style={{
            background: `linear-gradient(135deg, hsl(${hue}, 70%, 55%), hsl(${(hue + 40) % 360}, 60%, 45%))`,
          }}
        >
          {(channel?.name || '?')[0].toUpperCase()}
        </div>

        <div className="channel-view__info">
          <div className="channel-view__name">{channel?.name || 'Канал'}</div>
          <div className="channel-view__meta">
            <Users size={12} strokeWidth={1.5} />
            <span>{channel?.subscribersCount ?? 0}</span>
          </div>
        </div>

        <div className="channel-view__actions">
          {!isOwner && (
            <button
              className={`channel-view__sub-btn ${isSubscribed ? 'channel-view__sub-btn--active' : ''}`}
              onClick={isSubscribed ? handleUnsubscribe : handleSubscribe}
            >
              {isSubscribed ? 'Отписаться' : 'Подписаться'}
            </button>
          )}
          {isOwner && (
            <button className="channel-view__settings">
              <Settings size={18} strokeWidth={1.5} />
            </button>
          )}
        </div>
      </Glass>

      {/* Лента постов */}
      <div className="channel-view__feed" ref={feedRef}>
        {channelPosts.length === 0 ? (
          <div className="channel-view__empty">
            <span>Постов пока нет</span>
          </div>
        ) : (
          channelPosts.map((post) => (
            <ChannelPost key={post.id} post={post} />
          ))
        )}
      </div>

      {/* Ввод поста (только для владельца) */}
      {isOwner && (
        <Glass depth={2} radius={16} className="channel-view__input-bar">
          <button className="channel-view__attach" onClick={() => fileRef.current?.click()}>
            <Paperclip size={18} strokeWidth={1.5} />
          </button>
          <input type="file" ref={fileRef} hidden onChange={handleFileUpload} />
          <textarea
            className="channel-view__textarea"
            placeholder="Написать пост..."
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={1}
          />
          <button
            className="channel-view__send"
            onClick={handleSend}
            disabled={!text.trim() || sending}
          >
            <Send size={18} strokeWidth={1.5} />
          </button>
        </Glass>
      )}
    </div>
  );
}
