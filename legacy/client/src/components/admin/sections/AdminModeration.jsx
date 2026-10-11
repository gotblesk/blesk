import { useState, useEffect, useRef } from 'react';
import { CaretLeft, CaretRight } from '@phosphor-icons/react';
import Glass from '../../ui/Glass';
import { useAdminStore } from '../../../store/adminStore';

const STATUS_OPTIONS = [
  { value: '', label: 'Все' },
  { value: 'new', label: 'Новые' },
  { value: 'reviewed', label: 'На рассмотрении' },
  { value: 'resolved', label: 'Решённые' },
  { value: 'rejected', label: 'Отклонённые' },
];

export default function AdminModeration() {
  const { reports, reportsTotal, loadingReports, fetchReports, updateReport } = useAdminStore();
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(1);
  const totalPages = Math.max(1, Math.ceil(reportsTotal / 50));
  const mountedRef = useRef(false);

  useEffect(() => {
    if (!mountedRef.current) {
      mountedRef.current = true;
      fetchReports(1, statusFilter);
      return;
    }
    setPage(1);
    fetchReports(1, statusFilter);
  }, [statusFilter, fetchReports]);

  useEffect(() => {
    if (!mountedRef.current) return;
    if (page === 1) return;
    fetchReports(page, statusFilter);
  }, [page]);

  const handleStatus = async (id, status) => {
    await updateReport(id, status);
  };

  const statusBadge = (status) => {
    const cls = `admin-badge admin-badge--${status || 'new'}`;
    const labels = { new: 'новая', reviewed: 'рассмотрение', resolved: 'решена', rejected: 'отклонена' };
    return <span className={cls}>{labels[status] || status}</span>;
  };

  return (
    <div className="admin-section">
      <div className="admin-section__title">Модерация</div>

      <div className="admin-toolbar">
        <select className="admin-select" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          {STATUS_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
      </div>

      {loadingReports ? (
        <div className="admin-loading">Загрузка...</div>
      ) : reports.length === 0 ? (
        <div className="admin-empty">Жалоб не найдено</div>
      ) : (
        <div className="admin-card-grid">
          {reports.map((r) => (
            <Glass key={r.id} depth={1} radius={12} className="admin-card">
              <div className="admin-card__header">
                <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                  {r.reporter?.username || `user#${r.reporterId}`}
                </span>
                {statusBadge(r.status)}
              </div>
              <div className="admin-card__body">
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 6 }}>
                  {r.targetType} #{r.targetId} {r.category && `/ ${r.category}`}
                </div>
                <div>{r.text || 'Без описания'}</div>
              </div>
              <div className="admin-card__footer">
                <span className="admin-card__meta">
                  {r.createdAt ? new Date(r.createdAt).toLocaleString('ru') : '---'}
                </span>
                <div style={{ display: 'flex', gap: 6 }}>
                  {r.status !== 'reviewed' && (
                    <button className="admin-btn admin-btn--ghost admin-btn--sm" onClick={() => handleStatus(r.id, 'reviewed')}>
                      Рассмотреть
                    </button>
                  )}
                  {r.status !== 'resolved' && (
                    <button className="admin-btn admin-btn--primary admin-btn--sm" onClick={() => handleStatus(r.id, 'resolved')}>
                      Решена
                    </button>
                  )}
                  {r.status !== 'rejected' && (
                    <button className="admin-btn admin-btn--ghost admin-btn--sm" onClick={() => handleStatus(r.id, 'rejected')}>
                      Отклонить
                    </button>
                  )}
                </div>
              </div>
            </Glass>
          ))}
        </div>
      )}

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
    </div>
  );
}
