import { useState, useEffect, useRef, memo, useMemo, useCallback } from 'react';
// import { useVirtualizer } from '@tanstack/react-virtual'; // disabled: causes TDZ in minified builds
import { MagnifyingGlass, PushPin, BellSlash, ChatCircle } from '@phosphor-icons/react';
import { useChatStore } from '../../store/chatStore';
import Avatar from '../ui/Avatar';
import './Sidebar.css';

export default memo(function SidebarNormal({ activeTab, activeChatId, onSelectChat, onOpenChat }) {
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [initialLoad, setInitialLoad] = useState(true);

  useEffect(() => {
    const t = setTimeout(() => setSearch(searchInput), 300);
    return () => clearTimeout(t);
  }, [searchInput]);

  useEffect(() => {
    const t = setTimeout(() => setInitialLoad(false), 1500);
    return () => clearTimeout(t);
  }, []);
  const chats = useChatStore(s => s.chats);
  const onlineUsers = useChatStore(s => s.onlineUsers);
  const pinnedChats = useChatStore(s => s.pinnedChats);
  const typingUsers = useChatStore(s => s.typingUsers);
  const drafts = useChatStore(s => s.drafts);
  const listRef = useRef(null);

  const filtered = useMemo(() => {
    if (!search.trim()) return chats;
    const q = search.toLowerCase();
    return chats.filter(c => {
      const name = c.otherUser?.username || c.name || '';
      return name.toLowerCase().includes(q);
    });
  }, [chats, search]);

  const pinned = filtered.filter(c => pinnedChats.has(c.id));
  const unread = filtered.filter(c => !pinnedChats.has(c.id) && c.unreadCount > 0);
  const rest = filtered.filter(c => !pinnedChats.has(c.id) && !(c.unreadCount > 0));

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

  // Собрать плоский список для виртуализации:
  // [{type:'label',text}, {type:'chat',chat}, ...]
  const flatList = useMemo(() => {
    const items = [];
    if (pinned.length > 0) {
      items.push({ type: 'label', text: 'Закреплённые', key: 'label-pinned' });
      pinned.forEach(c => items.push({ type: 'chat', chat: c, key: c.id }));
    }
    if (unread.length > 0) {
      items.push({ type: 'label', text: 'Непрочитанные', key: 'label-unread' });
      unread.forEach(c => items.push({ type: 'chat', chat: c, key: c.id }));
    }
    if (rest.length > 0) {
      if (pinned.length > 0 || unread.length > 0) {
        items.push({ type: 'label', text: 'Все чаты', key: 'label-rest' });
      }
      rest.forEach(c => items.push({ type: 'chat', chat: c, key: c.id }));
    }
    return items;
  }, [pinned, unread, rest]);

  // Virtualization disabled (causes TDZ in minified builds)

  const renderChat = (chat) => {
    const user = chat.otherUser;
    const name = user?.username || chat.name || 'Чат';
    const isActive = activeChatId === chat.id;
    const isTyping = typingUsers[chat.id]?.length > 0;
    const draft = drafts[chat.id];
    const online = isOnline(chat);
    const isPinned = pinnedChats.has(chat.id);

    let previewContent;
    if (isTyping) {
      previewContent = (
        <span className="sn__typing-dots">
          <span className="sn__typing-dot" />
          <span className="sn__typing-dot" />
          <span className="sn__typing-dot" />
        </span>
      );
    } else if (draft) {
      previewContent = (
        <span className="sn__draft">
          <span className="sn__draft-label">Черновик: </span>
          {draft.length > 40 ? draft.slice(0, 40) + '...' : draft}
        </span>
      );
    } else {
      previewContent = chat.lastMessage?.text || 'Нет сообщений';
    }

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
          <div className={`sn__chat-preview ${isTyping ? 'sn__chat-preview--typing' : ''} ${draft && !isTyping ? 'sn__chat-preview--draft' : ''}`}>
            {previewContent}
          </div>
        </div>
        {chat.unreadCount > 0 && (
          <span className="sn__badge">{chat.unreadCount > 99 ? '99+' : chat.unreadCount}</span>
        )}
        <div className="sn__hover-actions" onClick={e => e.stopPropagation()}>
          <button className="sn__hover-btn" title={isPinned ? 'Открепить' : 'Закрепить'} aria-label={isPinned ? 'Открепить чат' : 'Закрепить чат'} onClick={() => useChatStore.getState().togglePinChat(chat.id)}>
            <PushPin size={14} weight={isPinned ? 'fill' : 'regular'} />
          </button>
          <button className="sn__hover-btn" title="Отключить уведомления" aria-label="Отключить уведомления" onClick={() => useChatStore.getState().toggleMuteChat?.(chat.id)}>
            <BellSlash size={14} />
          </button>
        </div>
      </div>
    );
  };

  const renderItem = (item) => {
    if (item.type === 'label') {
      return <div key={item.key} className="sn__group-label">{item.text}</div>;
    }
    return renderChat(item.chat);
  };

  return (
    <div className="sn">
      <div className="sn__search">
        <MagnifyingGlass size={14} weight="bold" className="sn__search-icon" />
        <input
          className="sn__search-input"
          placeholder="Поиск..."
          value={searchInput}
          onChange={e => setSearchInput(e.target.value)}
        />
      </div>

      <div className="sn__list" ref={listRef}>
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
        {flatList.map(renderItem)}
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
