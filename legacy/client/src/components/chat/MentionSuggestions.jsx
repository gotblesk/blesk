import { useState, useEffect, useRef } from 'react';
import { At } from '@phosphor-icons/react';
import Avatar from '../ui/Avatar';
import './MentionSuggestions.css';

export default function MentionSuggestions({ query, participants, onSelect, onClose, selectedIndex }) {
  const listRef = useRef(null);
  const activeRef = useRef(null);

  const filtered = participants.filter(p =>
    p.username.toLowerCase().includes(query.toLowerCase())
  ).slice(0, 6);

  // Скроллим к активному элементу при навигации клавиатурой
  useEffect(() => {
    if (activeRef.current) {
      activeRef.current.scrollIntoView({ block: 'nearest' });
    }
  }, [selectedIndex]);

  if (!filtered.length) return null;

  return (
    <div className="mention-suggestions" ref={listRef}>
      <div className="mention-suggestions__header">
        <At size={14} weight="bold" />
        <span>Участники</span>
      </div>
      {filtered.map((user, i) => (
        <button
          key={user.id}
          ref={i === (selectedIndex % filtered.length) ? activeRef : null}
          className={`mention-suggestions__item${i === (selectedIndex % filtered.length) ? ' mention-suggestions__item--active' : ''}`}
          onMouseDown={(e) => { e.preventDefault(); onSelect(user.username); }}
          onMouseEnter={() => {}}
        >
          <Avatar user={user} size="sm" />
          <span className="mention-suggestions__name">{user.username}</span>
        </button>
      ))}
    </div>
  );
}
