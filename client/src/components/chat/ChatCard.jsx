import { useState, useCallback } from 'react';
import { BellSlash, Checks, Trash, UserMinus, PushPin, Prohibit } from '@phosphor-icons/react';
import Avatar from '../ui/Avatar';
import ContextMenu from '../ui/ContextMenu';
import ConfirmDialog from '../ui/ConfirmDialog';
import { getAvatarHue, getAvatarGradient } from '../../utils/avatar';
import { useChatStore } from '../../store/chatStore';
import { getCurrentUserId } from '../../utils/auth';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import './ChatCard.css';

function GroupAvatar({ participants }) {
  const hues = (participants || []).slice(0, 3).map((p) => getAvatarHue(p));
  while (hues.length < 3) hues.push(hues.length * 120);

  return (
    <div className="chat-card__group-avatar">
      {hues.map((h, i) => (
        <div
          key={i}
          className={`chat-card__group-circle chat-card__group-circle--${i}`}
          style={{ background: getAvatarGradient(h) }}
        />
      ))}
    </div>
  );
}

export default function ChatCard({ chat, isOnline, userStatus, isOpen, onClick, cardRef }) {
  const [ctxMenu, setCtxMenu] = useState(null);
  const [dangerConfirm, setDangerConfirm] = useState(false);
  const [blockConfirm, setBlockConfirm] = useState(false);
  const isGroup = chat.type === 'group';
  const user = chat.otherUser;

  const formatTime = (dateStr) => {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now - date;
    const mins = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);

    if (mins < 1) return 'сейчас';
    if (mins < 60) return `${mins} мин`;
    if (hours < 24) return `${hours}ч`;
    if (hours < 48) return 'вчера';
    return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
  };

  const handleContextMenu = useCallback((e) => {
    e.preventDefault();
    setCtxMenu({ x: e.clientX, y: e.clientY });
  }, []);

  const handleMarkRead = useCallback(() => {
    useChatStore.getState().markAsRead(chat.id);
  }, [chat.id]);

  const handleDangerAction = useCallback(async () => {
    const userId = getCurrentUserId();
    if (!userId) return;
    try {
      if (isGroup) {
        await fetch(`${API_URL}/api/chats/${chat.id}/members/${userId}`, {
          method: 'DELETE',
          headers: { ...getAuthHeaders() }, credentials: 'include',
        });
      } else {
        await fetch(`${API_URL}/api/chats/${chat.id}`, {
          method: 'DELETE',
          headers: { ...getAuthHeaders() }, credentials: 'include',
        });
      }
      useChatStore.getState().removeChat(chat.id);
    } catch (err) { console.error('ChatCard deleteChat:', err?.message || err); }
    setDangerConfirm(false);
  }, [chat.id, isGroup]);

  const displayName = isGroup ? chat.name : (user?.username || chat.name);

  const pinnedChats = useChatStore(s => s.pinnedChats);
  const isPinned = pinnedChats.has(chat.id);

  const handleTogglePin = useCallback(() => {
    useChatStore.getState().togglePinChat(chat.id);
  }, [chat.id]);

  const handleMute = useCallback(async (duration) => {
    try {
      await fetch(`${API_URL}/api/chats/${chat.id}/mute`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
        credentials: 'include',
        body: JSON.stringify({ duration }),
      });
    } catch (err) { console.error('Mute error:', err?.message); }
  }, [chat.id]);

  const handleBlock = useCallback(async () => {
    try {
      await fetch(`${API_URL}/api/users/${chat.otherUser?.id}/block`, {
        method: 'POST',
        headers: { ...getAuthHeaders() },
        credentials: 'include',
      });
    } catch (err) { console.error('Block error:', err?.message); }
    setBlockConfirm(false);
  }, [chat.otherUser?.id]);

  const ctxItems = [
    { label: isPinned ? 'Открепить' : 'Закрепить', icon: <PushPin size={14} />, onClick: handleTogglePin },
    { label: 'Отметить прочитанным', icon: <Checks size={14} />, onClick: handleMarkRead },
    { divider: true },
    { label: 'Без звука', icon: <BellSlash size={14} />, onClick: () => handleMute('forever') },
    { divider: true },
    !isGroup && { label: 'Заблокировать', icon: <Prohibit size={14} />, danger: true, onClick: () => setBlockConfirm(true) },
    { label: isGroup ? 'Покинуть группу' : 'Удалить чат', icon: <Trash size={14} />, danger: true, onClick: () => setDangerConfirm(true) },
  ].filter(Boolean);

  return (
    <>
      <div
        className={`chat-row ${isOpen ? 'chat-row--open' : ''}`}
        onClick={onClick}
        onContextMenu={handleContextMenu}
        ref={cardRef}
      >
        <div className="chat-row__avatar-wrap">
          {isGroup ? (
            <GroupAvatar participants={chat.participants} />
          ) : (
            <Avatar user={user} size="md" showOnline isOnline={isOnline} userStatus={userStatus} />
          )}
        </div>

        <div className="chat-row__info">
          <div className="chat-row__name">{displayName}</div>
          <div className="chat-row__preview">
            {chat.lastMessage ? (
              <>
                {isGroup && chat.lastMessage.username && (
                  <span className="chat-row__author">{chat.lastMessage.username}: </span>
                )}
                {chat.lastMessage.text}
              </>
            ) : (
              <span className="chat-row__empty-msg">Нет сообщений</span>
            )}
          </div>
        </div>

        <div className="chat-row__meta">
          {chat.lastMessage && (
            <span className="chat-row__time">{formatTime(chat.lastMessage.createdAt)}</span>
          )}
          {chat.unreadCount > 0 && (
            <span className="chat-row__badge">{chat.unreadCount}</span>
          )}
        </div>
      </div>

      {ctxMenu && (
        <ContextMenu
          x={ctxMenu.x}
          y={ctxMenu.y}
          items={ctxItems}
          onClose={() => setCtxMenu(null)}
        />
      )}
      <ConfirmDialog
        open={dangerConfirm}
        title={isGroup ? 'Покинуть группу?' : 'Удалить чат?'}
        message={isGroup
          ? 'Вы больше не сможете видеть сообщения этой группы'
          : 'Чат будет удалён безвозвратно'}
        confirmText={isGroup ? 'Покинуть' : 'Удалить'}
        danger
        onConfirm={handleDangerAction}
        onCancel={() => setDangerConfirm(false)}
      />
      <ConfirmDialog
        open={blockConfirm}
        title={`Заблокировать ${displayName}?`}
        message="Пользователь не сможет писать вам и звонить"
        confirmText="Заблокировать"
        danger
        onConfirm={handleBlock}
        onCancel={() => setBlockConfirm(false)}
      />
    </>
  );
}
