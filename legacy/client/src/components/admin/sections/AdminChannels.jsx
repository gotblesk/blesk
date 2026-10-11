import { useState, useEffect } from 'react';
import { Trash, CaretLeft, CaretRight } from '@phosphor-icons/react';
import Glass from '../../ui/Glass';
import { useAdminStore } from '../../../store/adminStore';

export default function AdminChannels() {
  const { channels, channelsTotal, loadingChannels, fetchChannels, deleteChannel } = useAdminStore();
  const [confirmDelete, setConfirmDelete] = useState(null);
  const [page, setPage] = useState(1);
  const totalPages = Math.max(1, Math.ceil((channelsTotal || 0) / 20));

  useEffect(() => { fetchChannels(page); }, [page, fetchChannels]);

  const handleDelete = async () => {
    if (!confirmDelete) return;
    const ok = await deleteChannel(confirmDelete);
    if (ok) setConfirmDelete(null);
  };

  return (
    <div className="admin-section">
      <div className="admin-section__title">Каналы</div>

      <Glass depth={1} radius={14}>
        <div className="admin-table-wrap">
          {loadingChannels ? (
            <div className="admin-loading">Загрузка...</div>
          ) : channels.length === 0 ? (
            <div className="admin-empty">Каналов нет</div>
          ) : (
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Название</th>
                  <th>Владелец</th>
                  <th>Категория</th>
                  <th>Подписчики</th>
                  <th>Посты</th>
                  <th>Дата</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {channels.map((ch) => (
                  <tr key={ch.id}>
                    <td style={{ fontWeight: 600 }}>{ch.name}</td>
                    <td style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                      {ch.owner?.username || `#${ch.ownerId}`}
                    </td>
                    <td>
                      {ch.category && <span className="admin-badge admin-badge--user">{ch.category}</span>}
                    </td>
                    <td>{ch.subscribersCount ?? ch._count?.subscribers ?? 0}</td>
                    <td>{ch.postsCount ?? ch._count?.messages ?? 0}</td>
                    <td style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                      {ch.createdAt ? new Date(ch.createdAt).toLocaleDateString('ru') : '---'}
                    </td>
                    <td>
                      <button
                        className="admin-btn admin-btn--danger admin-btn--sm"
                        onClick={() => setConfirmDelete(ch.id)}
                      >
                        <Trash size={12} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </Glass>

      {totalPages > 1 && (
        <div className="admin-pagination">
          <button className="admin-pagination__btn" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
            <CaretLeft size={14} />
          </button>
          <span className="admin-pagination__info">{page} / {totalPages}</span>
          <button className="admin-pagination__btn" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}>
            <CaretRight size={14} />
          </button>
        </div>
      )}

      {confirmDelete && (
        <div className="admin-modal" onClick={() => setConfirmDelete(null)}>
          <Glass depth={3} radius={16} className="admin-modal__card" onClick={(e) => e.stopPropagation()}>
            <div className="admin-modal__title">Удалить канал?</div>
            <p style={{ color: 'var(--text-muted)', fontSize: 13, marginBottom: 0 }}>
              Канал и все его данные будут удалены. Это действие нельзя отменить.
            </p>
            <div className="admin-modal__actions">
              <button className="admin-btn admin-btn--ghost" onClick={() => setConfirmDelete(null)}>Отмена</button>
              <button className="admin-btn admin-btn--danger" onClick={handleDelete}>Удалить</button>
            </div>
          </Glass>
        </div>
      )}
    </div>
  );
}
