import { motion } from 'framer-motion';
import MediaMessage from '../chat/MediaMessage';
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

export default function ChannelPost({ post, index = 0 }) {
  const authorName = post.user?.username || post.username || 'Автор';
  const hue = (authorName.charCodeAt(0) * 37) % 360;
  const isLong = (post.text?.length || 0) > 200;

  return (
    <motion.article
      className={`cp ${isLong ? 'cp--long' : ''}`}
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: Math.min(index * 0.05, 0.3), ease: [0.16, 1, 0.3, 1] }}
    >
      {/* Left accent line from author hue */}
      <div className="cp__accent" style={{ background: `linear-gradient(180deg, hsl(${hue}, 60%, 50%), hsl(${(hue + 30) % 360}, 50%, 35%))` }} />

      <div className="cp__content">
        {/* Header: avatar + name + time */}
        <div className="cp__head">
          <div className="cp__ava" style={{ background: `linear-gradient(135deg, hsl(${hue}, 70%, 55%), hsl(${(hue + 40) % 360}, 60%, 45%))` }}>
            {(authorName || '?')[0].toUpperCase()}
          </div>
          <span className="cp__name">{authorName}</span>
          <span className="cp__time">{formatTime(post.createdAt)}</span>
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
}
