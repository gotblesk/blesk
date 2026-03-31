import { useState, useEffect, memo, useMemo } from 'react';
import { MagnifyingGlass, PushPin, ChatCircle } from '@phosphor-icons/react';
import { useChatStore } from '../../store/chatStore';
import Avatar from '../ui/Avatar';
import './Sidebar.css';

export default memo(function SidebarNormal({ activeTab, activeChatId, onSelectChat, onOpenChat }) {
  const [search, setSearch] = useState('');
  const [initialLoad, setInitialLoad] = useState(true);

  useEffect(() => {
    const t = setTimeout(() => setInitialLoad(false), 1500);
    return () => clearTimeout(t);
  }, []);
  const chats = useChatStore(s => s.chats);
  const onlineUsers = useChatStore(s => s.onlineUsers);
  const pinnedChats = useChatStore(s => s.pinnedChats);
  const typingUsers = useChatStore(s => s.typingUsers);

  const filtered = useMemo(() => {
    if (!search.trim()) return chats;
    const q = search.toLowerCase();
    return chats.filter(c => {
      const name = c.otherUser?.username || c.name || '';
      return name.toLowerCase().includes(q);
    });
  }, [chats, search]);

  const pinned = filtered.filter(c => pinnedChats.has(c.id));
  const rest = filtered.filter(c => !pinnedChats.has(c.id));

  const isOnline = (chat) => {
    if (chat.otherUser) return onlineUsers.includes(chat.otherUser.id);
    return false;
  };

  const formatTime = (dateStr) => {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    const now = new Date();
    const diff = now - d;
    if (diff < 60000) return 'сейчас';
    if (diff < 3600000) return `${Math.floor(diff / 60000)}м`;
    if (diff < 86400000) return d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
    if (diff < 172800000) return 'вчера';
    return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
  };

  const renderChat = (chat) => {
    const user = chat.otherUser;
    const name = user?.username || chat.name || 'Чат';
    const isActive = activeChatId === chat.id;
    const isTyping = typingUsers[chat.id]?.length > 0;
    const preview = isTyping ? null : (chat.lastMessage?.text || 'Нет сообщений');
    const online = isOnline(chat);

    return (
      <div
        key={chat.id}
        className={`sn__chat ${isActive ? 'sn__chat--active' : ''} ${!online && chat.otherUser ? 'sn__chat--offline' : ''}`}
        onClick={() => onSelectChat(chat.id)}
      >
        <Avatar user={user || { username: name }} size={42} showOnline={isOnline(chat)} />
        <div className="sn__chat-meta">
          <div className="sn__chat-top">
            <span className="sn__chat-name">{name}</span>
            <span className="sn__chat-time">{formatTime(chat.lastMessage?.createdAt)}</span>
          </div>
          <div className={`sn__chat-preview ${isTyping ? 'sn__chat-preview--typing' : ''}`}>
            {isTyping ? (
              <span className="sn__typing-dots">
                <span className="sn__typing-dot" />
                <span className="sn__typing-dot" />
                <span className="sn__typing-dot" />
              </span>
            ) : preview}
          </div>
        </div>
        {chat.unreadCount > 0 && (
          <span className="sn__badge">{chat.unreadCount > 99 ? '99+' : chat.unreadCount}</span>
        )}
      </div>
    );
  };

  return (
    <div className="sn">
      <div className="sn__search">
        <MagnifyingGlass size={14} weight="bold" className="sn__search-icon" />
        <input
          className="sn__search-input"
          placeholder="Поиск..."
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>

      <div className="sn__list">
        {initialLoad && chats.length === 0 && (
          <div className="sn__skeleton">
            {[1,2,3,4].map(i => (
              <div key={i} className="sn__skeleton-item">
                <div className="sn__skeleton-ava" />
                <div className="sn__skeleton-lines">
                  <div className="sn__skeleton-line sn__skeleton-line--w70" />
                  <div className="sn__skeleton-line sn__skeleton-line--w50" />
                </div>
              </div>
            ))}
          </div>
        )}
        {pinned.length > 0 && (
          <div className="sn__section">
            <div className="sn__section-label">
              <PushPin size={10} weight="fill" />
              <span>Закреплённые</span>
            </div>
            {pinned.map(renderChat)}
          </div>
        )}
        {rest.map(renderChat)}
        {filtered.length === 0 && search.trim() && (
          <div className="sn__empty">Ничего не найдено</div>
        )}
        {chats.length === 0 && !search.trim() && !initialLoad && (
          <div className="sn__empty-state">
            <ChatCircle size={40} weight="duotone" style={{ opacity: 0.12 }} />
            <span>Нет чатов</span>
            <span className="sn__empty-hint">Найдите друзей и начните переписку</span>
          </div>
        )}
      </div>
    </div>
  );
});
