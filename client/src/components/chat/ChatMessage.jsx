import { useState, useRef } from 'react';
import { Pin, Lock, CheckCheck } from 'lucide-react';
import Avatar from '../ui/Avatar';
import MediaMessage from './MediaMessage';
import useMessageActions from './MessageActionsPill';
import { getHueStyles } from '../../utils/hueIdentity';
import './ChatMessage.css';

const EMOJI_ONLY_RE = /^[\p{Emoji_Presentation}\p{Extended_Pictographic}\s]{1,3}$/u;

function countEmojis(text) {
  const matches = text.match(/[\p{Emoji_Presentation}\p{Extended_Pictographic}]/gu);
  return matches ? matches.length : 0;
}

export default function ChatMessage({
  message,
  isOwn,
  groupPosition = 'solo',
  hue,
  senderName,
  isRead,
  onReply,
  onReact,
  onEdit,
  onDelete,
  onImageClick,
}) {
  const [readAnimated, setReadAnimated] = useState(false);
  const prevReadRef = useRef(isRead);

  const { handleContextMenu, menu: actionsMenu } = useMessageActions({
    isOwn,
    onReply,
    onReact,
    onEdit: () => onEdit?.(message),
    onDelete: () => onDelete?.(message),
  });

  // Анимация при переходе в "прочитано"
  if (isRead && !prevReadRef.current) {
    prevReadRef.current = true;
    if (!readAnimated) setReadAnimated(true);
  }

  const time = new Date(message.createdAt).toLocaleTimeString('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
  });

  // Системные сообщения — простой текст по центру
  if (message.type === 'system') {
    return (
      <div id={`msg-${message.id}`} className="chat-message chat-message--system">
        {message.text}
      </div>
    );
  }

  const text = message.text || '';
  const isEmojiOnly = text.length > 0 && EMOJI_ONLY_RE.test(text.trim());
  const emojiCount = isEmojiOnly ? countEmojis(text.trim()) : 0;

  const showAvatar = !isOwn && (groupPosition === 'last' || groupPosition === 'solo');
  const showName = !isOwn && (groupPosition === 'first' || groupPosition === 'solo');
  const showTime = groupPosition === 'last' || groupPosition === 'solo';

  const hueStyles = !isOwn && hue != null ? getHueStyles(hue) : {};

  const classes = [
    'chat-message',
    isOwn ? 'chat-message--own' : 'chat-message--other',
    `chat-message--${groupPosition}`,
    message.pending ? 'chat-message--pending' : '',
    message.pinned ? 'chat-message--pinned' : '',
    isEmojiOnly ? `chat-message--emoji-only chat-message--emoji-${emojiCount}` : '',
  ].filter(Boolean).join(' ');

  const readGlowClass = isOwn && isRead && readAnimated
    ? 'chat-message__bubble-outer--read-glow'
    : '';

  const readIconAnimClass = readAnimated ? 'chat-message__read-icon--animate' : '';

  const handleReplyClick = () => {
    if (!message.replyTo?.id) return;
    const el = document.getElementById(`msg-${message.replyTo.id}`);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      el.classList.add('chat-message--highlighted');
      setTimeout(() => el.classList.remove('chat-message--highlighted'), 1500);
    }
  };

  const replyTo = message.replyTo;

  return (
    <div
      id={`msg-${message.id}`}
      className={classes}
      style={isOwn ? undefined : hueStyles}
    >
      {/* Аватар — только для last/solo чужих сообщений */}
      {!isOwn && showAvatar && (
        <Avatar user={{ username: senderName, avatar: message.avatar }} size="sm" className="chat-message__avatar" />
      )}
      {!isOwn && !showAvatar && (
        <div className="chat-message__avatar chat-message__avatar--hidden" />
      )}

      <div className="chat-message__col" style={{ position: 'relative', overflow: 'visible' }} onContextMenu={handleContextMenu}>
        {showName && (
          <div className="chat-message__name">{senderName}</div>
        )}

        {isEmojiOnly ? (
          <div style={{ position: 'relative', display: 'inline-block' }}>
            <div className="chat-message__emoji">{text}</div>
            <div className="chat-message__emoji-particles">
              {[...Array(6)].map((_, i) => <div key={i} className="chat-message__particle" />)}
            </div>
          </div>
        ) : (
          <div className={`chat-message__bubble-outer ${readGlowClass}`}>
            <div className="chat-message__bubble-inner">
              {replyTo && (
                <div className="chat-message__reply-quote" onClick={handleReplyClick}>
                  <div className="chat-message__reply-bar" />
                  <div>
                    <div className="chat-message__reply-name">
                      {replyTo.user?.username || replyTo.username || 'Сообщение'}
                    </div>
                    <div className="chat-message__reply-text">
                      {replyTo.text?.slice(0, 80)}
                      {replyTo.text?.length > 80 ? '…' : ''}
                    </div>
                  </div>
                </div>
              )}

              {text}

              {message.editedAt && (
                <span className="chat-message__edited"> (ред.)</span>
              )}

              {message.encrypted && (
                <Lock size={10} strokeWidth={1.5} className="chat-message__e2e" title="Зашифровано" />
              )}

              {message.pinned && (
                <span className="chat-message__pin-icon">
                  <Pin size={12} strokeWidth={1.5} />
                </span>
              )}

              {message.attachments?.length > 0 && (
                <MediaMessage attachments={message.attachments} onImageClick={onImageClick} />
              )}

              {isOwn && isRead && (
                <CheckCheck size={12} strokeWidth={1.5} className={`chat-message__read-icon ${readIconAnimClass}`} />
              )}
            </div>
          </div>
        )}

        {showTime && (
          <div className="chat-message__time">{time}</div>
        )}

        {actionsMenu}
      </div>
    </div>
  );
}
