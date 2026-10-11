import { useState, useEffect, useRef } from 'react';
import { CaretLeft, CaretRight } from '@phosphor-icons/react';
import Glass from '../../ui/Glass';
import { useAdminStore } from '../../../store/adminStore';

const ACTION_TYPES = [
  { value: '', label: 'Все действия' },
  { value: 'user.ban', label: 'Бан' },
  { value: 'user.unban', label: 'Разбан' },
  { value: 'user.edit', label: 'Смена роли' },
  { value: 'message.delete', label: 'Удаление сообщения' },
  { value: 'channel.delete', label: 'Удаление канала' },
  { value: 'tag.create', label: 'Создание тега' },
  { value: 'tag.edit', label: 'Редактирование тега' },
  { value: 'broadcast.send', label: 'Рассылка' },
];

const actionBadgeColor = {
  'user.ban': 'admin-badge--banned',
  'user.unban': 'admin-badge--active',
  'user.edit': 'admin-badge--moderator',
  'message.delete': 'admin-badge--rejected',
  'channel.delete': 'admin-badge--banned',
  'tag.create': 'admin-badge--admin',
  'tag.edit': 'admin-badge--new',
  'broadcast.send': 'admin-badge--reviewed',
};

export default function AdminLogs() {
  const { logs, logsTotal, loadingLogs, fetchLogs } = useAdminStore();
  const [actionFilter, setActionFilter] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [page, setPage] = useState(1);
  const totalPages = Math.max(1, Math.ceil(logsTotal / 50));
  const mountedRef = useRef(false);

  useEffect(() => {
    const filters = {};
    if (actionFilter) filters.action = actionFilter;
    if (dateFrom) filters.from = dateFrom;
    if (dateTo) filters.to = dateTo;

    if (!mountedRef.current) {
      mountedRef.current = true;
      fetchLogs(1, filters);
      return;
    }

    setPage(1);
    fetchLogs(1, filters);
  }, [actionFilter, dateFrom, dateTo, fetchLogs]);

  useEffect(() => {
    if (!mountedRef.current) return;
    if (page === 1) return;
    const filters = {};
    if (actionFilter) filters.action = actionFilter;
    if (dateFrom) filters.from = dateFrom;
    if (dateTo) filters.to = dateTo;
    fetchLogs(page, filters);
  }, [page]);

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
