import { useState, useRef, useEffect } from 'react';
import { ChatCircle } from '@phosphor-icons/react';
import { useChatStore } from '../../store/chatStore';
import ChatCard from './ChatCard';
import CreateChatModal from './CreateChatModal';
import './ChatHub.css';

export default function ChatHub({ onOpenChat, visible, openChatIds = [] }) {
  const chats = useChatStore((s) => s.chats);
  const loadChats = useChatStore((s) => s.loadChats);
  const onlineUsers = useChatStore((s) => s.onlineUsers);
  const userStatuses = useChatStore((s) => s.userStatuses);
  const [search, setSearch] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const cardRefs = useRef({});

  useEffect(() => {
    loadChats();
  }, [loadChats]);

  // Очищаем stale refs для удалённых чатов
  useEffect(() => {
    const ids = new Set(chats.map((c) => c.id));
    for (const key of Object.keys(cardRefs.current)) {
      if (!ids.has(key)) delete cardRefs.current[key];
    }
  }, [chats]);

  const filtered = search
    ? chats.filter((c) =>
        (c.otherUser?.username || c.name || '').toLowerCase().includes(search.toLowerCase())
      )
    : chats;

  const getCardRef = (chatId) => {
    if (!cardRefs.current[chatId]) {
      cardRefs.current[chatId] = { current: null };
    }
    return (el) => { cardRefs.current[chatId].current = el; };
  };

  const handleOpenChat = (chatId) => {
    const cardEl = cardRefs.current[chatId]?.current;
    const rect = cardEl?.getBoundingClientRect() || null;
    onOpenChat(chatId, rect);
  };

  return (
    <div className={`chat-hub ${visible ? '' : 'chat-hub--hidden'}`}>
      <div className="chat-hub__toolbar">
        <div className="chat-hub__search-wrap">
          <input
            className="chat-hub__search"
            type="text"
            placeholder="Поиск чатов..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <button className="chat-hub__add" onClick={() => setShowCreateModal(true)}>+</button>
      </div>

      {filtered.length > 0 ? (
        <div className="chat-hub__grid">
          {filtered.map((chat) => (
            <ChatCard
              key={chat.id}
              chat={chat}
              isOnline={chat.otherUser ? onlineUsers.includes(chat.otherUser.id) : false}
              userStatus={chat.otherUser ? userStatuses[chat.otherUser.id] : null}
              isOpen={openChatIds.includes(chat.id)}
              onClick={() => handleOpenChat(chat.id)}
              cardRef={getCardRef(chat.id)}
            />
          ))}
        </div>
      ) : (
        <div className="chat-hub__empty">
          <div className="chat-hub__empty-icon"><ChatCircle size={40} /></div>
          <div className="chat-hub__empty-title">Нет чатов</div>
          <div className="chat-hub__empty-text">Добавьте друзей и начните общение</div>
          <button className="chat-hub__empty-cta" onClick={() => setShowCreateModal(true)}>
            Начать чат
          </button>
        </div>
      )}

      {showCreateModal && (
        <CreateChatModal
          onClose={() => setShowCreateModal(false)}
          onCreated={(chatId) => {
            setShowCreateModal(false);
            loadChats().then(() => handleOpenChat(chatId));
          }}
        />
      )}
    </div>
  );
}
