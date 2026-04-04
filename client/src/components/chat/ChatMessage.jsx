import React, { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { PushPin, Lock, Checks, PhoneIncoming, PhoneDisconnect, PhoneX, Clock, ArrowClockwise, Plus } from '@phosphor-icons/react';
import Avatar from '../ui/Avatar';
import MediaMessage from './MediaMessage';
import LinkPreviewCard from './LinkPreviewCard';
import useMessageActions from './MessageActionsPill';
import { getHueStyles } from '../../utils/hueIdentity';
import { parseMarkdown } from '../../utils/markdown';
import { useSettingsStore } from '../../store/settingsStore';
import './ChatMessage.css';

const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🔥'];

const EMOJI_ONLY_RE = /^[\p{Emoji_Presentation}\p{Extended_Pictographic}\s]{1,3}$/u;
const URL_RE = /https?:\/\/[^\s<>"']+/;
const MEDIA_EXT_RE = /\.(png|jpe?g|gif|webp|svg|mp4|webm|mov|avi|mkv|bmp|ico|tiff?)(\?|#|$)/i;

function extractPreviewUrl(text) {
  if (!text) return null;
  const match = text.match(URL_RE);
  if (!match) return null;
  const url = match[0];
  if (MEDIA_EXT_RE.test(url)) return null;
  return url;
}

function countEmojis(text) {
  const matches = text.match(/[\p{Emoji_Presentation}\p{Extended_Pictographic}]/gu);
  return matches ? matches.length : 0;
}

const ChatMessage = React.memo(function ChatMessage({
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
  onForward,
  onPin,
  onImageClick,
  onRetry,
  reactions,
  currentUserId,
}) {
  const [showReactionBar, setShowReactionBar] = useState(false);

  const { handleContextMenu, menu: actionsMenu } = useMessageActions({
    isOwn,
    onReply,
    onReact: (emoji) => onReact?.(message.id, emoji),
    onEdit: () => onEdit?.(message),
    onDelete: () => onDelete?.(message.id),
    onForward: () => onForward?.(message),
    onPin: () => onPin?.(message),
  });

  // Настройки из store
  const bubbleStyle = useSettingsStore((s) => s.chatBubbleStyle);
  const showAvatarsSetting = useSettingsStore((s) => s.showAvatarsInChat);
  const timeFormat = useSettingsStore((s) => s.timeFormat);

  const time = new Date(message.createdAt).toLocaleTimeString('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: timeFormat === '12h',
  });

  // Системные сообщения — простой текст по центру, иконки для звонков
  if (message.type === 'system') {
    let callIcon = null;
    const t = message.text || '';
    if (t.includes('завершён')) {
      callIcon = <PhoneIncoming size={14} style={{ color: '#4ade80' }} />;
    } else if (t.includes('Сброшенный')) {
      callIcon = <PhoneDisconnect size={14} style={{ color: '#f59e0b' }} />;
    } else if (t.includes('Пропущенный')) {
      callIcon = <PhoneX size={14} style={{ color: '#ef4444' }} />;
    } else if (t.includes('Отменённый')) {
      callIcon = <PhoneDisconnect size={14} style={{ color: 'rgba(255,255,255,0.35)' }} />;
    }
    return (
      <div id={`msg-${message.id}`} className="chat-message chat-message--system">
        {callIcon}
        {message.text}
      </div>
    );
  }

  const text = message.text || '';
  const isEmojiOnly = text.length > 0 && EMOJI_ONLY_RE.test(text.trim());
  const emojiCount = isEmojiOnly ? countEmojis(text.trim()) : 0;

  const showAvatar = showAvatarsSetting && !isOwn && (groupPosition === 'last' || groupPosition === 'solo');
  const showName = !isOwn && (groupPosition === 'first' || groupPosition === 'solo');
  const showTime = groupPosition === 'last' || groupPosition === 'solo';

  const hueStyles = !isOwn && hue != null ? getHueStyles(hue) : {};

  const classes = [
    'chat-message',
    isOwn ? 'chat-message--own' : 'chat-message--other',
    `chat-message--${groupPosition}`,
    `chat-message--style-${bubbleStyle || 'bubbles'}`,
    !showAvatarsSetting ? 'chat-message--no-avatars' : '',
    message.pending ? 'chat-message--pending' : '',
    message.failed ? 'chat-message--failed' : '',
    message.pinned ? 'chat-message--pinned' : '',
    isEmojiOnly ? `chat-message--emoji-only chat-message--emoji-${emojiCount}` : '',
  ].filter(Boolean).join(' ');

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
      onMouseEnter={() => setShowReactionBar(true)}
      onMouseLeave={() => setShowReactionBar(false)}
    >
      {/* Floating reaction bar */}
      <AnimatePresence>
        {showReactionBar && (
          <motion.div
            className={`reaction-bar ${isOwn ? 'reaction-bar--own' : ''}`}
            initial={{ opacity: 0, y: 8, scale: 0.9 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 4, scale: 0.95 }}
            transition={{ type: 'spring', stiffness: 500, damping: 30 }}
          >
            {QUICK_REACTIONS.map(emoji => (
              <button
                key={emoji}
                className="reaction-bar__btn"
                onClick={(e) => { e.stopPropagation(); onReact?.(message.id, emoji); }}
                title={emoji}
              >
                {emoji}
              </button>
            ))}
            <button
              className="reaction-bar__btn reaction-bar__more"
              onClick={(e) => { e.stopPropagation(); onReact?.(message.id, '+'); }}
              title="Другая реакция"
            >
              <Plus size={13} weight="bold" />
            </button>
          </motion.div>
        )}
      </AnimatePresence>
      {/* Аватар — только для last/solo чужих сообщений (если включены) */}
      {!isOwn && showAvatarsSetting && showAvatar && (
        <Avatar user={{ username: senderName, avatar: message.user?.avatar }} size="sm" className="chat-message__avatar" />
      )}
      {!isOwn && showAvatarsSetting && !showAvatar && (
        <div className="chat-message__avatar chat-message__avatar--hidden" />
      )}

      <div className="chat-message__col" style={{ position: 'relative', overflow: 'visible' }} onContextMenu={handleContextMenu}>
        {showName && (
          <div className="chat-message__name">{senderName}</div>
        )}

        {isEmojiOnly ? (
          <div className="chat-message__emoji">{text}</div>
        ) : (
          <div className="chat-message__bubble-outer">
            <div className="chat-message__bubble-inner">
              {replyTo && (
                <div
                  className="chat-message__reply-quote"
                  onClick={handleReplyClick}
                  onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && handleReplyClick()}
                  role="button"
                  tabIndex={0}
                  aria-label={`Перейти к сообщению от ${replyTo.user?.username || 'пользователя'}`}
                >
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

              {parseMarkdown(text)}

              {message.editedAt && (
                <span className="chat-message__edited"> (ред.)</span>
              )}

              {message.encrypted && (
                <Lock size={10} className="chat-message__e2e" title="Зашифровано" />
              )}

              {message.pinned && (
                <span className="chat-message__pin-icon">
                  <PushPin size={12} />
                </span>
              )}

              {(() => {
                const preview = message.linkPreview;
                const previewUrl = !preview ? extractPreviewUrl(text) : null;
                if (preview) return <LinkPreviewCard preview={preview} />;
                if (previewUrl) return <LinkPreviewCard preview={{ url: previewUrl, domain: new URL(previewUrl).hostname }} />;
                return null;
              })()}

              {message.attachments?.length > 0 && (
                <MediaMessage attachments={message.attachments} onImageClick={onImageClick} />
              )}

              {isOwn && isRead && (
                <Checks size={12} className="chat-message__read-icon" />
              )}
            </div>
          </div>
        )}

        {reactions && Object.keys(reactions).length > 0 && (
          <div className="chat-message__reactions">
            {Object.entries(reactions).map(([emoji, data]) => {
              const hasOwn = data.userIds?.includes(currentUserId);
              return (
                <button
                  key={emoji}
                  className={`chat-message__reaction ${hasOwn ? 'chat-message__reaction--own' : ''}`}
                  onClick={() => onReact?.(message.id, emoji)}
                  title={data.users?.join(', ') || ''}
                >
                  <span className="chat-message__reaction-emoji">{emoji}</span>
                  <span className="chat-message__reaction-count">{data.count}</span>
                </button>
              );
            })}
          </div>
        )}

        {showTime && (
          <div className="chat-message__time">
            {message.pending && (
              <Clock size={10} className="chat-message__pending-icon" title={message.offline ? 'Будет отправлено при подключении' : 'Отправка...'} />
            )}
            {message.failed && (
              <ArrowClockwise
                size={10}
                className="chat-message__failed-icon"
                title="Не удалось отправить. Нажмите для повтора"
                onClick={(e) => {
                  e.stopPropagation();
                  onRetry?.(message);
                }}
                style={{ cursor: 'pointer' }}
              />
            )}
            {time}
          </div>
        )}

        {actionsMenu}
      </div>
    </div>
  );
});

export default ChatMessage;
