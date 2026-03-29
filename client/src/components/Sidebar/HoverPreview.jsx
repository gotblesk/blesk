import { useState, useRef, useEffect, useCallback } from 'react';
import { PaperPlaneTilt } from '@phosphor-icons/react';
import { useChatStore } from '../../store/chatStore';
import Avatar from '../ui/Avatar';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';

export default function HoverPreview({ chatId, position, onClose, onOpenChat }) {
  const [messages, setMessages] = useState([]);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const ref = useRef(null);
  const chat = useChatStore(s => s.chats.find(c => c.id === chatId));

  useEffect(() => {
    if (!chatId) return;
    (async () => {
      try {
        const res = await fetch(`${API_URL}/api/chats/${chatId}/messages?limit=5`, {
          headers: { ...getAuthHeaders() },
          credentials: 'include',
        });
        if (res.ok) {
          const data = await res.json();
          setMessages((data.messages || data).reverse());
        }
      } catch {}
    })();
  }, [chatId]);

  useEffect(() => {
    if (!ref.current) return;
    const rect = ref.current.getBoundingClientRect();
    if (rect.bottom > window.innerHeight - 80) {
      ref.current.style.top = `${window.innerHeight - 80 - rect.height}px`;
    }
  }, [messages]);

  const handleSend = useCallback(async () => {
    if (!text.trim() || sending) return;
    setSending(true);
    try {
      useChatStore.getState().sendMessage(chatId, text.trim());
      setText('');
      onClose?.();
    } catch {}
    setSending(false);
  }, [text, chatId, sending, onClose]);

  if (!chatId || !chat) return null;

  const name = chat.otherUser?.username || chat.name || 'Чат';

  return (
    <div
      ref={ref}
      className="hover-preview"
      style={{ top: position?.top || 100 }}
      onMouseLeave={() => setTimeout(onClose, 200)}
    >
      <div className="hover-preview__head">
        <span className="hover-preview__name">{name}</span>
      </div>
      <div className="hover-preview__messages">
        {messages.length === 0 && (
          <div className="hover-preview__empty">Нет сообщений</div>
        )}
        {messages.map(msg => (
          <div key={msg.id} className={`hover-preview__msg ${msg.userId === chat.otherUser?.id ? '' : 'hover-preview__msg--own'}`}>
            <span className="hover-preview__msg-text">{msg.text || 'Файл'}</span>
          </div>
        ))}
      </div>
      <div className="hover-preview__input-row">
        <input
          className="hover-preview__input"
          placeholder="Ответить..."
          value={text}
          onChange={e => setText(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') handleSend(); }}
          autoFocus
        />
        <button className="hover-preview__send" onClick={handleSend} disabled={!text.trim()}>
          <PaperPlaneTilt size={14} weight="fill" />
        </button>
      </div>
    </div>
  );
}
