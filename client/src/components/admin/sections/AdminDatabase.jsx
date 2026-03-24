import { useState, useEffect } from 'react';
import { ChevronLeft, ChevronRight, Info } from 'lucide-react';
import Glass from '../../ui/Glass';
import { useAdminStore } from '../../../store/adminStore';

export default function AdminDatabase() {
  const {
    dbTables, dbRows, dbTotal, dbColumns, selectedTable, loadingDb,
    fetchDbTables, fetchDbTable,
  } = useAdminStore();

  const [page, setPage] = useState(1);
  const totalPages = Math.max(1, Math.ceil(dbTotal / 50));

  useEffect(() => { fetchDbTables(); }, [fetchDbTables]);

  const handleSelectTable = (table) => {
    setPage(1);
    fetchDbTable(table, 1);
  };

  useEffect(() => {
    if (selectedTable) fetchDbTable(selectedTable, page);
  }, [page, selectedTable, fetchDbTable]);

  return (
    <div className="admin-section">
      <div className="admin-section__title">База данных</div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
        <Info size={14} style={{ color: 'var(--text-muted)' }} />
        <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
          Только просмотр. Редактирование через другие разделы.
        </span>
      </div>

      <div className="admin-toolbar">
        <select
          className="admin-select"
          value={selectedTable || ''}
          onChange={(e) => e.target.value && handleSelectTable(e.target.value)}
        >
          <option value="">Выберите таблицу</option>
          {dbTables.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
      </div>

      {selectedTable && (
        <Glass depth={1} radius={14}>
          <div className="admin-table-wrap">
            {loadingDb ? (
              <div className="admin-loading">Загрузка...</div>
            ) : dbRows.length === 0 ? (
              <div className="admin-empty">Таблица пуста</div>
            ) : (
              <table className="admin-table">
                <thead>
                  <tr>
                    {dbColumns.map((col) => (
                      <th key={col}>{col}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {dbRows.map((row, i) => (
                    <tr key={i}>
                      {dbColumns.map((col) => (
                        <td key={col} style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 12 }}>
                          {row[col] === null ? <span style={{ color: 'var(--text-muted)', fontStyle: 'italic' }}>null</span> : String(row[col])}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </Glass>
      )}

      {selectedTable && totalPages > 1 && (
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
