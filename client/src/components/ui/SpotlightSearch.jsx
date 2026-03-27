import { useState, useEffect, useRef, useCallback } from 'react';
import { Search, MessageCircle, Mic, Radio, Users, Settings, X } from 'lucide-react';
import { useChatStore } from '../../store/chatStore';
import './SpotlightSearch.css';

const COMMANDS = [
  { id: 'tab:chats', label: 'Чаты', icon: <MessageCircle size={16} strokeWidth={1.5} />, action: 'tab', tab: 'chats' },
  { id: 'tab:voice', label: 'Голос', icon: <Mic size={16} strokeWidth={1.5} />, action: 'tab', tab: 'voice' },
  { id: 'tab:channels', label: 'Каналы', icon: <Radio size={16} strokeWidth={1.5} />, action: 'tab', tab: 'channels' },
  { id: 'tab:friends', label: 'Друзья', icon: <Users size={16} strokeWidth={1.5} />, action: 'tab', tab: 'friends' },
  { id: 'tab:settings', label: 'Настройки', icon: <Settings size={16} strokeWidth={1.5} />, action: 'tab', tab: 'settings' },
];

export default function SpotlightSearch({ open, onClose, onNavigate, onOpenChat }) {
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(0);
  const inputRef = useRef(null);
  const chats = useChatStore((s) => s.chats);

  useEffect(() => {
    if (open) {
      setQuery('');
      setSelected(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [open]);

  // Результаты: чаты + команды
  const results = [];
  const q = query.toLowerCase().trim();

  if (q) {
    // Поиск чатов
    chats.forEach((c) => {
      const name = (c.otherUser?.username || c.name || '').toLowerCase();
      if (name.includes(q)) {
        results.push({
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

  // Команды (всегда показываем, фильтруем по запросу)
  COMMANDS.forEach((cmd) => {
    if (!q || cmd.label.toLowerCase().includes(q)) {
      results.push(cmd);
    }
  });

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
      setSelected((s) => Math.min(s + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelected((s) => Math.max(s - 1, 0));
    } else if (e.key === 'Enter' && results[selected]) {
      e.preventDefault();
      handleSelect(results[selected]);
    } else if (e.key === 'Escape') {
      onClose();
    }
  };

  if (!open) return null;

  return (
    <div className="spotlight-backdrop" onClick={onClose}>
      <div className="spotlight" onClick={(e) => e.stopPropagation()}>
        <div className="spotlight__input-row">
          <Search size={18} strokeWidth={1.5} className="spotlight__search-icon" />
          <input
            ref={inputRef}
            className="spotlight__input"
            placeholder="Поиск чатов, людей, команд..."
            value={query}
            onChange={(e) => { setQuery(e.target.value); setSelected(0); }}
            onKeyDown={handleKeyDown}
          />
          <button className="spotlight__close" onClick={onClose}>
            <X size={14} strokeWidth={2} />
          </button>
        </div>

        {results.length > 0 && (
          <div className="spotlight__results">
            {results.map((item, i) => (
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
          </div>
        )}

        {/* [EMPTY-1] Empty state для поиска */}
        {results.length === 0 && query?.trim().length > 0 && (
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
