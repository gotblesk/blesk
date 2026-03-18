import MediaMessage from '../chat/MediaMessage';
import './ChannelPost.css';

function formatTime(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  const now = new Date();
  const isToday = d.toDateString() === now.toDateString();
  const time = d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
  if (isToday) return time;
  return `${d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })} ${time}`;
}

function getInitial(name) {
  return (name || '?')[0].toUpperCase();
}

export default function ChannelPost({ post }) {
  const authorName = post.user?.username || post.username || 'Автор';
  const hue = (authorName.charCodeAt(0) * 37) % 360;

  return (
    <div className="channel-post">
      <div className="channel-post__author">
        <div
          className="channel-post__author-avatar"
          style={{
            background: `linear-gradient(135deg, hsl(${hue}, 70%, 55%), hsl(${(hue + 40) % 360}, 60%, 45%))`,
          }}
        >
          {getInitial(authorName)}
        </div>
        <span className="channel-post__author-name">{authorName}</span>
      </div>

      {post.text && <div className="channel-post__text">{post.text}</div>}

      {post.attachments?.length > 0 && (
        <MediaMessage attachments={post.attachments} />
      )}

      <div className="channel-post__time">{formatTime(post.createdAt)}</div>
    </div>
  );
}
