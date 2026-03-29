import { useState, useEffect, useRef, useCallback } from 'react';
import { ChatCircle, X, PaperPlaneTilt } from '@phosphor-icons/react';
import { useVoiceStore } from '../../store/voiceStore';
import { getAvatarHue, getAvatarColor } from '../../utils/avatar';
import './VoiceChat.css';

export default function VoiceChat({ roomId, socketRef, inline = false, onClose }) {
  const [messages, setMessages] = useState([]);
  const [text, setText] = useState('');
  const [open, setOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const listRef = useRef(null);
  const openRef = useRef(inline || open);
  openRef.current = inline || open;

  // Слушаем сообщения (ref вместо open в deps — нет потери сообщений)
  useEffect(() => {
    const socket = socketRef?.current;
    if (!socket) return;

    const handler = ({ roomId: rid, message }) => {
      if (rid !== roomId) return;
      setMessages((prev) => [...prev, message]);
      if (!openRef.current) {
        setUnread((prev) => prev + 1);
      }
    };

    socket.on('voice:chat:message', handler);
    return () => socket.off('voice:chat:message', handler);
  }, [roomId, socketRef]);

  // Автоскролл
  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [messages]);

  // Сбросить непрочитанные при открытии
  useEffect(() => {
    if (open) setUnread(0);
  }, [open]);

  // Очистить сообщения при смене комнаты
  useEffect(() => {
    setMessages([]);
    setUnread(0);
  }, [roomId]);

  const handleSend = useCallback(() => {
    if (!text.trim()) return;
    const socket = socketRef?.current;
    if (!socket) return;

    socket.emit('voice:chat', { roomId, text: text.trim() });
    setText('');
  }, [text, roomId, socketRef]);

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const formatTime = (ts) => {
    const d = new Date(ts);
    return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`;
  };

  // Общий рендер сообщений и инпута
  const renderMessages = () => (
    <div className="voice-chat__messages" ref={listRef}>
      {messages.length === 0 && (
        <div className="voice-chat__empty">
          Напишите первое сообщение
        </div>
      )}
      {messages.map((msg) => (
        <div key={msg.id} className="voice-chat__msg">
          <div
            className="voice-chat__msg-av"
            style={{ background: getAvatarColor(getAvatarHue(msg)) }}
          >
            {(msg.username || '?')[0].toUpperCase()}
          </div>
          <div className="voice-chat__msg-body">
            <div className="voice-chat__msg-header">
              <span className="voice-chat__msg-name" style={{ color: `hsl(${getAvatarHue(msg)}, 70%, 65%)` }}>
                {msg.username}
              </span>
              <span className="voice-chat__msg-time">{formatTime(msg.timestamp)}</span>
            </div>
            <div className="voice-chat__msg-text">{msg.text}</div>
          </div>
        </div>
      ))}
    </div>
  );

  const renderInput = () => (
    <div className="voice-chat__input-row">
      <input
        className="voice-chat__input"
        value={text}
        onChange={(e) => setText(e.target.value.slice(0, 500))}
        onKeyDown={handleKeyDown}
        placeholder="Сообщение..."
        maxLength={500}
      />
      <button
        className="voice-chat__send"
        onClick={handleSend}
        disabled={!text.trim()}
      >
        <PaperPlaneTilt size={16} />
      </button>
    </div>
  );

  // Inline режим -- встроен в VoiceRoom как боковая панель
  if (inline) {
    return (
      <div className="voice-chat voice-chat--inline">
        <div className="voice-chat__header">
          <span>Чат комнаты</span>
          {onClose && (
            <button className="voice-chat__close" onClick={onClose}>
              <X size={16} weight="bold" />
            </button>
          )}
        </div>
        {renderMessages()}
        {renderInput()}
      </div>
    );
  }

  // Floating режим -- legacy, кнопка + popup
  return (
    <>
      <button
        className={`voice-chat-toggle ${open ? 'voice-chat-toggle--active' : ''}`}
        onClick={() => setOpen(!open)}
        title="Чат комнаты"
      >
        <ChatCircle size={16} />
        {unread > 0 && <span className="voice-chat-toggle__badge">{unread}</span>}
      </button>

      {open && (
        <div className="voice-chat">
          <div className="voice-chat__header">
            <span>Чат комнаты</span>
            <button className="voice-chat__close" onClick={() => setOpen(false)}><X size={16} weight="bold" /></button>
          </div>
          {renderMessages()}
          {renderInput()}
        </div>
      )}
    </>
  );
}
