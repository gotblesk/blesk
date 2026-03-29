import { useState, useEffect, useRef, useCallback } from 'react';
import { Search, MessageCircle, Mic, Radio, Users, Settings, X, FileText, Loader2 } from 'lucide-react';
import { useChatStore } from '../../store/chatStore';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import './SpotlightSearch.css';

const COMMANDS = [
  { id: 'tab:chats', label: 'Чаты', icon: <MessageCircle size={16} strokeWidth={1.5} />, action: 'tab', tab: 'chats' },
  { id: 'tab:voice', label: 'Голос', icon: <Mic size={16} strokeWidth={1.5} />, action: 'tab', tab: 'voice' },
  { id: 'tab:channels', label: 'Каналы', icon: <Radio size={16} strokeWidth={1.5} />, action: 'tab', tab: 'channels' },
  { id: 'tab:friends', label: 'Друзья', icon: <Users size={16} strokeWidth={1.5} />, action: 'tab', tab: 'friends' },
  { id: 'tab:settings', label: 'Настройки', icon: <Settings size={16} strokeWidth={1.5} />, action: 'tab', tab: 'settings' },
];

/** Форматирует дату сообщения в короткий вид */
function formatMsgTime(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  const now = new Date();
  const diffMs = now - d;
  const diffDays = Math.floor(diffMs / 86400000);
  if (diffDays === 0) {
    return d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
  }
  if (diffDays === 1) return 'вчера';
  if (diffDays < 7) {
    return d.toLocaleDateString('ru-RU', { weekday: 'short' });
  }
  return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
}

/** Обрезает текст до maxLen символов */
function truncate(text, maxLen = 60) {
  if (!text) return '';
  return text.length > maxLen ? text.slice(0, maxLen) + '...' : text;
}

const MSG_SEARCH_LIMIT = 5;
const DEBOUNCE_MS = 300;

export default function SpotlightSearch({ open, onClose, onNavigate, onOpenChat }) {
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(0);
  const [msgResults, setMsgResults] = useState([]);
  const [msgLoading, setMsgLoading] = useState(false);
  const inputRef = useRef(null);
  const debounceRef = useRef(null);
  const abortRef = useRef(null);
  const chats = useChatStore((s) => s.chats);

  // Сброс при открытии
  useEffect(() => {
    if (open) {
      setQuery('');
      setSelected(0);
      setMsgResults([]);
      setMsgLoading(false);
      setTimeout(() => inputRef.current?.focus(), 50);
    } else {
      // При закрытии отменяем запросы
      if (abortRef.current) abortRef.current.abort();
      if (debounceRef.current) clearTimeout(debounceRef.current);
    }
  }, [open]);

  // Поиск сообщений через API с дебаунсом
  useEffect(() => {
    const q = query.trim();
    if (q.length < 2) {
      setMsgResults([]);
      setMsgLoading(false);
      return;
    }

    setMsgLoading(true);

    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (abortRef.current) abortRef.current.abort();

    debounceRef.current = setTimeout(async () => {
      const controller = new AbortController();
      abortRef.current = controller;

      try {
        const res = await fetch(
          `${API_URL}/api/chats/search?q=${encodeURIComponent(q)}`,
          {
            headers: { ...getAuthHeaders() },
            credentials: 'include',
            signal: controller.signal,
          }
        );
        if (!res.ok) throw new Error(res.status);
        const data = await res.json();
        const messages = Array.isArray(data) ? data : (data.messages ?? []);
        setMsgResults(messages.slice(0, MSG_SEARCH_LIMIT));
      } catch (err) {
        if (err.name !== 'AbortError') {
          setMsgResults([]);
        }
      } finally {
        if (!controller.signal.aborted) {
          setMsgLoading(false);
        }
      }
    }, DEBOUNCE_MS);

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query]);

  // Локальные результаты: чаты + команды
  const localResults = [];
  const q = query.toLowerCase().trim();

  if (q) {
    chats.forEach((c) => {
      const name = (c.otherUser?.username || c.name || '').toLowerCase();
      if (name.includes(q)) {
        localResults.push({
          id: `chat:${c.id}`,
          label: c.otherUser?.username || c.name,
          hint: c.type === 'group' ? 'Группа' : 'Чат',
          icon: <MessageCircle size={16} strokeWidth={1.5} />,
          action: 'chat',
          chatId: c.id,
        });
      }
    });
  }

  COMMANDS.forEach((cmd) => {
    if (!q || cmd.label.toLowerCase().includes(q)) {
      localResults.push(cmd);
    }
  });

  // Результаты сообщений (маппим в единый формат)
  const msgItems = msgResults.map((msg) => ({
    id: `msg:${msg.id}`,
    action: 'chat',
    chatId: msg.roomId,
    isMessage: true,
    senderName: msg.user?.username || msg.senderName || 'Пользователь',
    preview: truncate(msg.text),
    time: formatMsgTime(msg.createdAt),
    chatName: msg.room?.name || msg.chatName || '',
    icon: <FileText size={16} strokeWidth={1.5} />,
  }));

  // Все результаты для навигации клавиатурой
  const allResults = [...localResults, ...msgItems];

  const handleSelect = useCallback((item) => {
    if (item.action === 'tab') {
      onNavigate(item.tab);
    } else if (item.action === 'chat') {
      onOpenChat(item.chatId);
    }
    onClose();
  }, [onNavigate, onOpenChat, onClose]);

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelected((s) => Math.min(s + 1, allResults.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelected((s) => Math.max(s - 1, 0));
    } else if (e.key === 'Enter' && allResults[selected]) {
      e.preventDefault();
      handleSelect(allResults[selected]);
    } else if (e.key === 'Escape') {
      onClose();
    }
  };

  if (!open) return null;

  const hasMessages = msgItems.length > 0;
  const hasLocal = localResults.length > 0;
  const noResults = !hasLocal && !hasMessages && !msgLoading && q.length > 0;

  return (
    <div className="spotlight-backdrop" onClick={onClose}>
      <div className="spotlight" onClick={(e) => e.stopPropagation()}>
        <div className="spotlight__input-row">
          <Search size={18} strokeWidth={1.5} className="spotlight__search-icon" />
          <input
            ref={inputRef}
            className="spotlight__input"
            placeholder="Поиск чатов, людей, сообщений..."
            value={query}
            onChange={(e) => { setQuery(e.target.value); setSelected(0); }}
            onKeyDown={handleKeyDown}
          />
          {msgLoading && (
            <Loader2 size={16} strokeWidth={2} className="spotlight__loader" />
          )}
          <button className="spotlight__close" onClick={onClose}>
            <X size={14} strokeWidth={2} />
          </button>
        </div>

        {(hasLocal || hasMessages || msgLoading) && (
          <div className="spotlight__results">
            {/* Чаты и команды */}
            {localResults.map((item, i) => (
              <button
                key={item.id}
                className={`spotlight__item ${i === selected ? 'spotlight__item--selected' : ''}`}
                onClick={() => handleSelect(item)}
                onMouseEnter={() => setSelected(i)}
              >
                <span className="spotlight__item-icon">{item.icon}</span>
                <span className="spotlight__item-label">{item.label}</span>
                {item.hint && <span className="spotlight__item-hint">{item.hint}</span>}
              </button>
            ))}

            {/* Разделитель + секция сообщений */}
            {hasMessages && (
              <>
                <div className="spotlight__section-header">Сообщения</div>
                {msgItems.map((item, i) => {
                  const globalIdx = localResults.length + i;
                  return (
                    <button
                      key={item.id}
                      className={`spotlight__item spotlight__item--msg ${globalIdx === selected ? 'spotlight__item--selected' : ''}`}
                      onClick={() => handleSelect(item)}
                      onMouseEnter={() => setSelected(globalIdx)}
                    >
                      <span className="spotlight__item-icon">{item.icon}</span>
                      <div className="spotlight__msg-content">
                        <div className="spotlight__msg-top">
                          <span className="spotlight__msg-sender">{item.senderName}</span>
                          {item.chatName && (
                            <span className="spotlight__msg-chat">{item.chatName}</span>
                          )}
                          <span className="spotlight__msg-time">{item.time}</span>
                        </div>
                        <div className="spotlight__msg-preview">{item.preview}</div>
                      </div>
                    </button>
                  );
                })}
              </>
            )}

            {/* Индикатор загрузки сообщений */}
            {msgLoading && !hasMessages && q.length >= 2 && (
              <div className="spotlight__msg-loading">Поиск сообщений...</div>
            )}
          </div>
        )}

        {noResults && (
          <div className="spotlight__empty">Ничего не найдено</div>
        )}

        <div className="spotlight__footer">
          <span className="spotlight__hint-key">Enter</span> выбрать
          <span className="spotlight__hint-key">Esc</span> закрыть
        </div>
      </div>
    </div>
  );
}
