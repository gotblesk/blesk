import { useState, useEffect } from 'react';
import API_URL from '../../config';
import './StatusEditor.css';

const PRESETS = [
  { id: 'online', emoji: '🟢', label: 'В сети', color: '#4ade80' },
  { id: 'dnd', emoji: '🔴', label: 'Не беспокоить', color: '#ef4444' },
  { id: 'invisible', emoji: '⚫', label: 'Невидимка', color: '#6b7280' },
];

export default function StatusEditor({ open, onClose, user, onUserUpdate }) {
  const [selected, setSelected] = useState('online');
  const [customText, setCustomText] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open && user) {
      setSelected(user.status || 'online');
      setCustomText(user.customStatus || '');
    }
  }, [open, user]);

  // Escape закрывает
  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (e.key === 'Escape') onClose?.();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open, onClose]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const token = localStorage.getItem('token');
      const res = await fetch(`${API_URL}/api/users/me`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ status: selected, customStatus: customText }),
      });

      if (res.ok) {
        const updated = await res.json();
        onUserUpdate?.(updated);
        onClose?.();
      }
    } catch {
      // тихий фейл
    } finally {
      setSaving(false);
    }
  };

  if (!open) return null;

  return (
    <div className="status-overlay" onClick={onClose}>
      <div
        className="status-card"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="status-card__shine" />

        <div className="status-title">Изменить статус</div>

        {/* Пресеты */}
        <div className="status-presets">
          {PRESETS.map((p) => (
            <button
              key={p.id}
              className={`status-preset ${selected === p.id ? 'status-preset--active' : ''}`}
              onClick={() => setSelected(p.id)}
              style={selected === p.id ? { borderColor: p.color } : undefined}
            >
              <span className="status-preset__emoji">{p.emoji}</span>
              <span className="status-preset__label">{p.label}</span>
            </button>
          ))}
        </div>

        {/* Кастомный текст */}
        <div className="status-custom">
          <input
            type="text"
            className="status-custom__input"
            value={customText}
            onChange={(e) => setCustomText(e.target.value.slice(0, 50))}
            placeholder="Свой статус..."
            maxLength={50}
          />
          <div className="status-custom__counter">{customText.length}/50</div>
        </div>

        {/* Кнопки */}
        <div className="status-actions">
          <button className="status-btn status-btn--cancel" onClick={onClose}>
            Отмена
          </button>
          <button
            className="status-btn status-btn--save"
            onClick={handleSave}
            disabled={saving}
          >
            {saving ? 'Сохраняю...' : 'Сохранить'}
          </button>
        </div>
      </div>
    </div>
  );
}
