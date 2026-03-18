import { useState } from 'react';
import { X } from 'lucide-react';
import Glass from '../ui/Glass';
import { useChannelStore } from '../../store/channelStore';
import './CreateChannelModal.css';

const CATEGORIES = [
  { key: 'news', label: 'Новости', color: '#3b82f6' },
  { key: 'gaming', label: 'Игры', color: '#8b5cf6' },
  { key: 'music', label: 'Музыка', color: '#ec4899' },
  { key: 'art', label: 'Арт', color: '#f59e0b' },
  { key: 'tech', label: 'Технологии', color: '#06b6d4' },
  { key: 'other', label: 'Другое', color: '#6b7280' },
];

export default function CreateChannelModal({ onClose, onCreated }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('other');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleCreate = async () => {
    if (!name.trim()) {
      setError('Введите название');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const channel = await useChannelStore.getState().createChannel({
        name: name.trim(),
        description: description.trim(),
        category,
      });
      onCreated?.(channel);
    } catch (err) {
      setError(err.message || 'Ошибка создания');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="create-channel-overlay" onClick={onClose}>
      <Glass
        depth={3}
        radius={20}
        className="create-channel-modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="create-channel-modal__header">
          <h3 className="create-channel-modal__title">Создать канал</h3>
          <button className="create-channel-modal__close" onClick={onClose}>
            <X size={18} strokeWidth={1.5} />
          </button>
        </div>

        <div className="create-channel-modal__field">
          <label className="create-channel-modal__label">Название</label>
          <input
            className="create-channel-modal__input"
            placeholder="Мой канал"
            value={name}
            onChange={(e) => setName(e.target.value.slice(0, 50))}
            maxLength={50}
            autoFocus
          />
          <span className="create-channel-modal__counter">{name.length}/50</span>
        </div>

        <div className="create-channel-modal__field">
          <label className="create-channel-modal__label">Описание</label>
          <textarea
            className="create-channel-modal__textarea"
            placeholder="О чём этот канал..."
            value={description}
            onChange={(e) => setDescription(e.target.value.slice(0, 300))}
            maxLength={300}
            rows={3}
          />
          <span className="create-channel-modal__counter">{description.length}/300</span>
        </div>

        <div className="create-channel-modal__field">
          <label className="create-channel-modal__label">Категория</label>
          <div className="create-channel-modal__categories">
            {CATEGORIES.map((cat) => (
              <button
                key={cat.key}
                className={`create-channel-modal__cat ${category === cat.key ? 'create-channel-modal__cat--active' : ''}`}
                style={{
                  '--cat-color': cat.color,
                }}
                onClick={() => setCategory(cat.key)}
              >
                {cat.label}
              </button>
            ))}
          </div>
        </div>

        {error && <div className="create-channel-modal__error">{error}</div>}

        <button
          className="create-channel-modal__submit"
          onClick={handleCreate}
          disabled={loading || !name.trim()}
        >
          {loading ? 'Создание...' : 'Создать'}
        </button>
      </Glass>
    </div>
  );
}
