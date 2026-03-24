import { useState, useEffect } from 'react';
import { Bug, Lightbulb, HelpCircle, ChevronLeft, ChevronRight } from 'lucide-react';
import Glass from '../../ui/Glass';
import { useAdminStore } from '../../../store/adminStore';

const TYPE_ICONS = {
  bug: <Bug size={14} />,
  suggestion: <Lightbulb size={14} />,
  question: <HelpCircle size={14} />,
};

const TYPE_LABELS = { bug: 'Баг', suggestion: 'Предложение', question: 'Вопрос' };

export default function AdminFeedback() {
  const { feedbacks, feedbacksTotal, loadingFeedbacks, fetchFeedbacks, updateFeedback } = useAdminStore();
  const [typeFilter, setTypeFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(1);
  const totalPages = Math.max(1, Math.ceil(feedbacksTotal / 50));

  useEffect(() => {
    setPage(1);
    const filters = {};
    if (typeFilter) filters.type = typeFilter;
    if (statusFilter) filters.status = statusFilter;
    fetchFeedbacks(1, filters);
  }, [typeFilter, statusFilter, fetchFeedbacks]);

  useEffect(() => {
    const filters = {};
    if (typeFilter) filters.type = typeFilter;
    if (statusFilter) filters.status = statusFilter;
    fetchFeedbacks(page, filters);
  }, [page, typeFilter, statusFilter, fetchFeedbacks]);

  const handleStatus = async (id, status) => {
    await updateFeedback(id, status);
  };

  return (
    <div className="admin-section">
      <div className="admin-section__title">Обратная связь</div>

      <div className="admin-toolbar">
        <select className="admin-select" value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}>
          <option value="">Все типы</option>
          <option value="bug">Баг</option>
          <option value="suggestion">Предложение</option>
          <option value="question">Вопрос</option>
        </select>
        <select className="admin-select" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">Все статусы</option>
          <option value="new">Новые</option>
          <option value="reviewed">Рассмотрено</option>
          <option value="resolved">Решено</option>
        </select>
      </div>

      {loadingFeedbacks ? (
        <div className="admin-loading">Загрузка...</div>
      ) : feedbacks.length === 0 ? (
        <div className="admin-empty">Фидбэков не найдено</div>
      ) : (
        <div className="admin-card-grid">
          {feedbacks.map((fb) => (
            <Glass key={fb.id} depth={1} radius={12} className="admin-card">
              <div className="admin-card__header">
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ color: fb.type === 'bug' ? '#ef4444' : fb.type === 'suggestion' ? '#fbbf24' : '#60a5fa' }}>
                    {TYPE_ICONS[fb.type] || TYPE_ICONS.question}
                  </span>
                  <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text)' }}>
                    {TYPE_LABELS[fb.type] || fb.type}
                  </span>
                </div>
                <span className={`admin-badge admin-badge--${fb.status || 'new'}`}>
                  {fb.status || 'new'}
                </span>
              </div>

              <div className="admin-card__body">
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 6 }}>
                  {fb.user?.username || `user#${fb.userId}`}
                </div>
                <div>{fb.text}</div>
              </div>

              <div className="admin-card__footer">
                <div className="admin-card__meta">
                  {fb.appVersion && <span>v{fb.appVersion}</span>}
                  {fb.osInfo && <span> / {fb.osInfo}</span>}
                  <br />
                  {fb.createdAt ? new Date(fb.createdAt).toLocaleString('ru') : ''}
                </div>
                <div style={{ display: 'flex', gap: 6 }}>
                  {fb.status !== 'reviewed' && (
                    <button className="admin-btn admin-btn--ghost admin-btn--sm" onClick={() => handleStatus(fb.id, 'reviewed')}>
                      Рассмотрено
                    </button>
                  )}
                  {fb.status !== 'resolved' && (
                    <button className="admin-btn admin-btn--primary admin-btn--sm" onClick={() => handleStatus(fb.id, 'resolved')}>
                      Решено
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
            <ChevronLeft size={14} />
          </button>
          <span className="admin-pagination__info">{page} / {totalPages}</span>
          <button className="admin-pagination__btn" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}>
            <ChevronRight size={14} />
          </button>
        </div>
      )}
    </div>
  );
}
