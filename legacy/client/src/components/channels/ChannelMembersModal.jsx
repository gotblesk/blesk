import { useState, useEffect } from 'react';
import { X } from '@phosphor-icons/react';
import Glass from '../ui/Glass';
import Avatar from '../ui/Avatar';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import './ChannelMembersModal.css';

export default function ChannelMembersModal({ channelId, onClose }) {
  const [subscribers, setSubscribers] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);

  useEffect(() => {
    setLoading(true);
    fetch(
      `${API_URL}/api/channels/${channelId}/subscribers?page=${page}&limit=50`,
      { headers: getAuthHeaders(), credentials: 'include' }
    )
      .then((r) => r.json())
      .then((data) => {
        if (!Array.isArray(data.subscribers)) return;
        setSubscribers((prev) => (page === 1 ? data.subscribers : [...prev, ...data.subscribers]));
        setTotal(data.total || 0);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [channelId, page]);

  // Закрытие по Escape
  useEffect(() => {
    const handler = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [onClose]);

  return (
    <div className="ch-members-overlay" onClick={onClose}>
      <Glass depth={3} className="ch-members-modal" onClick={(e) => e.stopPropagation()}>
        <div className="ch-members-modal__header">
          <span className="ch-members-modal__title">Подписчики ({total})</span>
          <button className="ch-members-modal__close" onClick={onClose} aria-label="Закрыть">
            <X size={16} weight="bold" />
          </button>
        </div>

        <div className="ch-members-modal__list">
          {subscribers.map((u) => (
            <div key={u.id} className="ch-members-modal__item">
              <Avatar user={u} size={36} showOnline />
              <span className="ch-members-modal__name">{u.username}</span>
            </div>
          ))}

          {loading && (
            <div className="ch-members-modal__loading">
              <div className="ch-members-modal__spinner" />
            </div>
          )}

          {!loading && subscribers.length < total && (
            <button
              className="ch-members-modal__more"
              onClick={() => setPage((p) => p + 1)}
            >
              Показать ещё
            </button>
          )}

          {!loading && subscribers.length === 0 && (
            <div className="ch-members-modal__empty">Нет подписчиков</div>
          )}
        </div>
      </Glass>
    </div>
  );
}
