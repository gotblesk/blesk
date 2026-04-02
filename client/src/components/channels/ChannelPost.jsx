import React, { useState, useCallback } from 'react';
import { motion } from 'framer-motion';
import { Trash } from '@phosphor-icons/react';
import MediaMessage from '../chat/MediaMessage';
import API_URL from '../../config';
import './ChannelPost.css';

function formatTime(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  const now = new Date();
  const diffMs = now - d;
  const diffMin = Math.floor(diffMs / 60000);
  const diffH = Math.floor(diffMs / 3600000);
  const diffD = Math.floor(diffMs / 86400000);

  if (diffMin < 1) return 'только что';
  if (diffMin < 60) return `${diffMin} мин`;
  if (diffH < 24) return `${diffH}ч`;
  if (diffD < 7) return `${diffD}д`;

  const time = d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
  return `${d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })} ${time}`;
}

const ChannelPost = React.memo(function ChannelPost({ post, index = 0, isOwner, onDelete }) {
  const authorName = post.user?.username || post.username || 'Автор';
  const hue = (authorName.charCodeAt(0) * 37) % 360;
  const isLong = (post.text?.length || 0) > 200;
  const [confirmDelete, setConfirmDelete] = useState(false);

  // Аватар автора
  const avatarUrl = post.user?.avatar ? `${API_URL}/uploads/avatars/${post.user.avatar}` : null;

  const handleDelete = useCallback(() => {
    if (!confirmDelete) {
      setConfirmDelete(true);
      setTimeout(() => setConfirmDelete(false), 3000); // Сбросить через 3 сек
      return;
    }
    onDelete?.(post.id);
  }, [confirmDelete, onDelete, post.id]);

  return (
    <motion.article
      className={`cp ${isLong ? 'cp--long' : ''}`}
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: Math.min(index * 0.05, 0.3), ease: [0.16, 1, 0.3, 1] }}
    >
      <div className="cp__glass-edge" />

      <div className="cp__content">
        {/* Header */}
        <div className="cp__head">
          {/* Реальный аватар или fallback на initials */}
          <div className="cp__ava" style={!avatarUrl ? { background: `linear-gradient(135deg, hsl(${hue}, 70%, 55%), hsl(${(hue + 40) % 360}, 60%, 45%))` } : {}}>
            {avatarUrl ? (
              <img src={avatarUrl} alt="" className="cp__ava-img" onError={(e) => { e.target.style.display = 'none'; }} />
            ) : null}
            <span className="cp__ava-letter">{(authorName || '?')[0].toUpperCase()}</span>
          </div>
          <span className="cp__name">{authorName}</span>
          <span className="cp__time">{formatTime(post.createdAt)}</span>
          {post.editedAt && <span className="cp__edited">ред.</span>}

          {/* Delete action for owner */}
          {isOwner && (
            <button
              className={`cp__delete ${confirmDelete ? 'cp__delete--confirm' : ''}`}
              onClick={handleDelete}
              title={confirmDelete ? 'Нажмите ещё раз для удаления' : 'Удалить пост'}
              aria-label="Удалить пост"
            >
              <Trash size={13} weight="regular" />
              {confirmDelete && <span className="cp__delete-label">Удалить?</span>}
            </button>
          )}
        </div>

        {/* Text */}
        {post.text && <div className="cp__text">{post.text}</div>}

        {/* Attachments */}
        {post.attachments?.length > 0 && (
          <div className="cp__media">
            <MediaMessage attachments={post.attachments} />
          </div>
        )}
      </div>
    </motion.article>
  );
});

export default ChannelPost;
