import { useState, useEffect, useCallback, useRef } from 'react';
import { MagnifyingGlass, X, CaretLeft, CaretRight } from '@phosphor-icons/react';
import Glass from '../../ui/Glass';
import Avatar from '../../ui/Avatar';
import { useAdminStore } from '../../../store/adminStore';

export default function AdminUsers() {
  const {
    users, usersTotal, usersPage, loadingUsers, selectedUser,
    fetchUsers, fetchUser, updateUser, banUser, unbanUser,
    grantTag, revokeTag, tags, fetchTags,
  } = useAdminStore();

  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(1);
  const [detailUser, setDetailUser] = useState(null);
  const [editData, setEditData] = useState({});
  const [banModal, setBanModal] = useState(false);
  const [banReason, setBanReason] = useState('');
  const [grantTagId, setGrantTagId] = useState('');
  const debounceRef = useRef(null);

  const totalPages = Math.max(1, Math.ceil(usersTotal / 50));

  const doFetch = useCallback((p = page) => {
    const filters = {};
    if (roleFilter) filters.role = roleFilter;
    if (statusFilter === 'banned') filters.banned = 'true';
    if (statusFilter === 'active') filters.banned = 'false';
    fetchUsers(p, search, filters);
  }, [page, search, roleFilter, statusFilter, fetchUsers]);

  useEffect(() => {
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setPage(1);
      doFetch(1);
      setDetailUser(null);
    }, 300);
    return () => clearTimeout(debounceRef.current);
  }, [search, roleFilter, statusFilter]);

  // Начальная загрузка + пагинация
  const prevPageRef = useRef(page);
  useEffect(() => {
    if (prevPageRef.current !== page) {
      prevPageRef.current = page;
      doFetch(page);
    }
  }, [page]);

  useEffect(() => {
    if (tags.length === 0) fetchTags();
  }, [tags.length, fetchTags]);

  const openDetail = async (user) => {
    const full = await fetchUser(user.id);
    if (full) {
      setDetailUser(full);
      setEditData({
        tag: full.tag || '',
        role: full.role || 'user',
        verifiedLevel: full.verifiedLevel ?? 0,
        bio: full.bio || '',
      });
    }
  };

  const closeDetail = () => {
    setDetailUser(null);
    setEditData({});
    useAdminStore.setState({ selectedUser: null });
  };

  const handleSave = async () => {
    if (!detailUser) return;
    const ok = await updateUser(detailUser.id, editData);
    if (ok) {
      setDetailUser((prev) => ({ ...prev, ...editData }));
    }
  };

  const handleBan = async () => {
    if (!detailUser) return;
    const ok = await banUser(detailUser.id, banReason);
    if (ok) {
      setDetailUser((prev) => ({ ...prev, banned: true, bannedReason: banReason }));
      setBanModal(false);
      setBanReason('');
    }
  };

  const handleUnban = async () => {
    if (!detailUser) return;
    const ok = await unbanUser(detailUser.id);
    if (ok) {
      setDetailUser((prev) => ({ ...prev, banned: false, bannedReason: null }));
    }
  };

  const handleGrantTag = async () => {
    if (!detailUser || !grantTagId) return;
    const ok = await grantTag(detailUser.id, grantTagId);
    if (ok) {
      const tagObj = tags.find((t) => t.id === grantTagId);
      setDetailUser((prev) => ({
        ...prev,
        tags: [...(prev.tags || []), tagObj].filter(Boolean),
      }));
      setGrantTagId('');
    }
  };

  const handleRevokeTag = async (tagId) => {
    if (!detailUser) return;
    const ok = await revokeTag(detailUser.id, tagId);
    if (ok) {
      setDetailUser((prev) => ({
        ...prev,
        tags: (prev.tags || []).filter((t) => t.id !== tagId),
      }));
    }
  };

  const roleBadge = (role) => {
    if (role === 'admin') return <span className="admin-badge admin-badge--admin">admin</span>;
    if (role === 'moderator') return <span className="admin-badge admin-badge--moderator">mod</span>;
    return <span className="admin-badge admin-badge--user">user</span>;
  };

  return (
    <div className="admin-section">
      <div className="admin-section__title">Пользователи</div>

      <div className="admin-toolbar">
        <input
          className="admin-search"
          type="text"
          placeholder="Поиск по имени или email..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select className="admin-select" value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)}>
          <option value="">Все роли</option>
          <option value="user">User</option>
          <option value="moderator">Moderator</option>
          <option value="admin">Admin</option>
        </select>
        <select className="admin-select" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">Все статусы</option>
          <option value="banned">Забанен</option>
          <option value="active">Активен</option>
        </select>
      </div>

      <Glass depth={1} radius={14}>
        <div className="admin-table-wrap">
          {loadingUsers ? (
            <div className="admin-loading">Загрузка...</div>
          ) : users.length === 0 ? (
            <div className="admin-empty">Пользователи не найдены</div>
          ) : (
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Пользователь</th>
                  <th>Email</th>
                  <th>Роль</th>
                  <th>Статус</th>
                  <th>Дата</th>
                  <th>Монеты</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id} className="admin-table__row--clickable" onClick={() => openDetail(u)}>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <Avatar user={u} size="sm" />
                        <span>{u.username}<span style={{ color: 'var(--text-muted)' }}>#{u.tag || '0000'}</span></span>
                      </div>
                    </td>
                    <td style={{ color: 'var(--text-muted)', fontSize: 12 }}>{u.email || '---'}</td>
                    <td>{roleBadge(u.role)}</td>
                    <td>
                      {u.banned
                        ? <span className="admin-badge admin-badge--banned">забанен</span>
                        : <span className="admin-badge admin-badge--active">активен</span>}
                    </td>
                    <td style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                      {u.createdAt ? new Date(u.createdAt).toLocaleDateString('ru') : '---'}
                    </td>
                    <td>{u.bleskCoins ?? 0}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </Glass>

      {totalPages > 1 && (
        <div className="admin-pagination">
          <button
            className="admin-pagination__btn"
            disabled={page <= 1}
            onClick={() => setPage((p) => p - 1)}
          >
            <CaretLeft size={14} />
          </button>
          <span className="admin-pagination__info">{page} / {totalPages}</span>
          <button
            className="admin-pagination__btn"
            disabled={page >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            <CaretRight size={14} />
          </button>
        </div>
      )}

      {/* Detail panel */}
      {detailUser && (
        <Glass depth={2} radius={14} className="admin-detail-panel">
          <div className="admin-detail-panel__header">
            <div className="admin-detail-panel__title">
              {detailUser.username}#{detailUser.tag || '0000'}
            </div>
            <button className="admin-btn admin-btn--ghost admin-btn--sm" onClick={closeDetail}>
              <X size={14} />
            </button>
          </div>

          <div className="admin-detail-row">
            <div className="admin-detail-row__label">Тег (число)</div>
            <div className="admin-detail-row__value">
              <input
                className="admin-input"
                value={editData.tag}
                onChange={(e) => setEditData((d) => ({ ...d, tag: e.target.value }))}
                placeholder="#0000"
              />
            </div>
          </div>

          <div className="admin-detail-row">
            <div className="admin-detail-row__label">Роль</div>
            <div className="admin-detail-row__value">
              <select className="admin-select" value={editData.role} onChange={(e) => setEditData((d) => ({ ...d, role: e.target.value }))}>
                <option value="user">User</option>
                <option value="moderator">Moderator</option>
                <option value="admin">Admin</option>
              </select>
            </div>
          </div>

          <div className="admin-detail-row">
            <div className="admin-detail-row__label">Верификация</div>
            <div className="admin-detail-row__value">
              <select
                className="admin-select"
                value={editData.verifiedLevel}
                onChange={(e) => setEditData((d) => ({ ...d, verifiedLevel: Number(e.target.value) }))}
              >
                <option value={0}>0 — нет</option>
                <option value={1}>1 — базовая</option>
                <option value={2}>2 — полная</option>
                <option value={3}>3 — особая</option>
              </select>
            </div>
          </div>

          <div className="admin-detail-row">
            <div className="admin-detail-row__label">Bio</div>
            <div className="admin-detail-row__value">
              <textarea
                className="admin-textarea"
                value={editData.bio}
                onChange={(e) => setEditData((d) => ({ ...d, bio: e.target.value }))}
                rows={3}
              />
            </div>
          </div>

          {/* Tags */}
          <div className="admin-detail-row">
            <div className="admin-detail-row__label">Теги</div>
            <div className="admin-detail-row__value">
              <div className="admin-tag-list">
                {(detailUser.tags || []).map((t) => (
                  <span key={t.id} className="admin-tag-chip" style={t.color ? { borderLeft: `3px solid ${t.color}` } : {}}>
                    {t.name}
                    <span className="admin-tag-chip__remove" onClick={() => handleRevokeTag(t.id)}>
                      <X size={10} />
                    </span>
                  </span>
                ))}
              </div>
              <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                <select className="admin-select" value={grantTagId} onChange={(e) => setGrantTagId(e.target.value)}>
                  <option value="">Выбрать тег</option>
                  {tags.map((t) => (
                    <option key={t.id} value={t.id}>{t.name}</option>
                  ))}
                </select>
                <button className="admin-btn admin-btn--primary admin-btn--sm" onClick={handleGrantTag} disabled={!grantTagId}>
                  Выдать
                </button>
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: 10, marginTop: 16 }}>
            <button className="admin-btn admin-btn--primary" onClick={handleSave}>Сохранить</button>
            {detailUser.banned ? (
              <button className="admin-btn admin-btn--ghost" onClick={handleUnban}>Разбанить</button>
            ) : (
              <button className="admin-btn admin-btn--danger" onClick={() => setBanModal(true)}>Забанить</button>
            )}
          </div>
        </Glass>
      )}

      {/* Ban modal */}
      {banModal && (
        <div className="admin-modal" onClick={() => setBanModal(false)}>
          <Glass depth={3} radius={16} className="admin-modal__card" onClick={(e) => e.stopPropagation()}>
            <div className="admin-modal__title">Забанить {detailUser?.username}</div>
            <div className="admin-form-group">
              <label className="admin-form-label">Причина</label>
              <textarea
                className="admin-textarea"
                value={banReason}
                onChange={(e) => setBanReason(e.target.value)}
                placeholder="Укажите причину бана..."
              />
            </div>
            <div className="admin-modal__actions">
              <button className="admin-btn admin-btn--ghost" onClick={() => setBanModal(false)}>Отмена</button>
              <button className="admin-btn admin-btn--danger" onClick={handleBan}>Забанить</button>
            </div>
          </Glass>
        </div>
      )}
    </div>
  );
}
