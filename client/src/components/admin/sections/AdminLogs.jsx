import { useState, useEffect } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import Glass from '../../ui/Glass';
import { useAdminStore } from '../../../store/adminStore';

const ACTION_TYPES = [
  { value: '', label: 'Все действия' },
  { value: 'ban', label: 'Бан' },
  { value: 'unban', label: 'Разбан' },
  { value: 'role_change', label: 'Смена роли' },
  { value: 'delete_message', label: 'Удаление сообщения' },
  { value: 'delete_channel', label: 'Удаление канала' },
  { value: 'tag_grant', label: 'Выдача тега' },
  { value: 'tag_revoke', label: 'Отзыв тега' },
  { value: 'broadcast', label: 'Рассылка' },
];

const actionBadgeColor = {
  ban: 'admin-badge--banned',
  unban: 'admin-badge--active',
  role_change: 'admin-badge--moderator',
  delete_message: 'admin-badge--rejected',
  delete_channel: 'admin-badge--banned',
  tag_grant: 'admin-badge--admin',
  tag_revoke: 'admin-badge--new',
  broadcast: 'admin-badge--reviewed',
};

export default function AdminLogs() {
  const { logs, logsTotal, loadingLogs, fetchLogs } = useAdminStore();
  const [actionFilter, setActionFilter] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [page, setPage] = useState(1);
  const totalPages = Math.max(1, Math.ceil(logsTotal / 50));

  useEffect(() => {
    setPage(1);
    const filters = {};
    if (actionFilter) filters.action = actionFilter;
    if (dateFrom) filters.from = dateFrom;
    if (dateTo) filters.to = dateTo;
    fetchLogs(1, filters);
  }, [actionFilter, dateFrom, dateTo, fetchLogs]);

  useEffect(() => {
    const filters = {};
    if (actionFilter) filters.action = actionFilter;
    if (dateFrom) filters.from = dateFrom;
    if (dateTo) filters.to = dateTo;
    fetchLogs(page, filters);
  }, [page, actionFilter, dateFrom, dateTo, fetchLogs]);

  return (
    <div className="admin-section">
      <div className="admin-section__title">Логи</div>

      <div className="admin-toolbar">
        <select className="admin-select" value={actionFilter} onChange={(e) => setActionFilter(e.target.value)}>
          {ACTION_TYPES.map((a) => (
            <option key={a.value} value={a.value}>{a.label}</option>
          ))}
        </select>
        <input
          className="admin-input"
          type="date"
          value={dateFrom}
          onChange={(e) => setDateFrom(e.target.value)}
          style={{ width: 150 }}
          title="От"
        />
        <input
          className="admin-input"
          type="date"
          value={dateTo}
          onChange={(e) => setDateTo(e.target.value)}
          style={{ width: 150 }}
          title="До"
        />
      </div>

      <Glass depth={1} radius={14}>
        <div className="admin-table-wrap">
          {loadingLogs ? (
            <div className="admin-loading">Загрузка...</div>
          ) : logs.length === 0 ? (
            <div className="admin-empty">Логов не найдено</div>
          ) : (
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Дата</th>
                  <th>Админ</th>
                  <th>Действие</th>
                  <th>Цель</th>
                  <th>Детали</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                  <tr key={log.id}>
                    <td style={{ color: 'var(--text-muted)', fontSize: 12, whiteSpace: 'nowrap' }}>
                      {log.createdAt ? new Date(log.createdAt).toLocaleString('ru') : '---'}
                    </td>
                    <td style={{ fontSize: 13 }}>
                      {log.admin?.username || log.adminUsername || '---'}
                    </td>
                    <td>
                      <span className={`admin-badge ${actionBadgeColor[log.action] || 'admin-badge--user'}`}>
                        {log.action}
                      </span>
                    </td>
                    <td style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                      {log.targetType ? `${String(log.targetType)} #${String(log.targetId || '')}` : '---'}
                    </td>
                    <td style={{ color: 'var(--text-muted)', fontSize: 12, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {typeof log.details === 'string' ? log.details : (log.details ? JSON.stringify(log.details) : '---')}
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
