import { useChatStore } from '../../store/chatStore';
import Avatar from '../ui/Avatar';
import './MiniCardsSidebar.css';

// ═══════ SIDEBAR — Magnetic Edge + Glass Island ═══════
export default function MiniCardsSidebar({ activeChatId, onSelectChat, onBack }) {
  const chats = useChatStore(s => s.chats);
  const onlineUsers = useChatStore(s => s.onlineUsers);

  const getChatName = (chat) => {
    if (chat.type === 'personal' && chat.otherUser) return chat.otherUser.username;
    return chat.name || 'Чат';
  };

  const isOnline = (chat) => {
    if (chat.type === 'personal' && chat.otherUser) {
      return onlineUsers.includes(chat.otherUser.id);
    }
    return false;
  };

  const getLastMessage = (chat) => {
    if (!chat.lastMessage) return '';
    const text = chat.lastMessage.text || '';
    return text.length > 25 ? text.slice(0, 25) + '...' : text;
  };

  const getLastTime = (chat) => {
    if (!chat.lastMessage?.createdAt) return '';
    const d = new Date(chat.lastMessage.createdAt);
    const now = new Date();
    const isToday = d.toDateString() === now.toDateString();
    if (isToday) {
      return d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
    }
    return 'вчера';
  };

  const cardContent = (chat, active) => (
    <>
      <div className="mini-card__avatar">
        <Avatar
          username={getChatName(chat)}
          avatarUrl={chat.otherUser?.avatar}
          size={active ? 36 : 34}
          showOnline={isOnline(chat)}
        />
      </div>
      <div className="mini-card__info">
        <div className="mini-card__name">{getChatName(chat)}</div>
        <div className="mini-card__preview">{getLastMessage(chat)}</div>
      </div>
      <div className="mini-card__meta">
        <span className="mini-card__time">{getLastTime(chat)}</span>
        {chat.unreadCount > 0 && (
          <span className="mini-card__badge">{chat.unreadCount}</span>
        )}
      </div>
    </>
  );

  return (
    <div className="mini-sidebar">
      <button className="mini-sidebar__back" onClick={onBack} title="Назад к Nebula">
        ← назад
      </button>

      <div className="mini-sidebar__list">
        {chats.map(chat => {
          const active = chat.id === activeChatId;
          return (
            <button
              key={chat.id}
              className={`mini-card ${active ? 'mini-card--active' : ''}`}
              onClick={() => onSelectChat(chat.id)}
              title={getChatName(chat)}
            >
              <div className="mini-card__content">
                {active ? (
                  <div className="mini-card__active-inner" style={{ display: 'flex' }}>
                    {cardContent(chat, true)}
                  </div>
                ) : (
                  <div className="mini-card__default-content" style={{ display: 'flex', alignItems: 'center', gap: '10px', width: '100%' }}>
                    {cardContent(chat, false)}
                  </div>
                )}
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
