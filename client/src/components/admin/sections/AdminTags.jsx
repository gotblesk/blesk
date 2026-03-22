import { useState, useEffect } from 'react';
import { Plus, Pencil, Trash2, X } from 'lucide-react';
import Glass from '../../ui/Glass';
import { useAdminStore } from '../../../store/adminStore';

const TAG_TYPES = ['system', 'achievement', 'seasonal', 'community'];
const RARITIES = ['common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic'];

const emptyForm = { name: '', color: '#c8ff00', type: 'community', rarity: 'common', description: '', icon: '' };

export default function AdminTags() {
  const { tags, loadingTags, fetchTags, createTag, updateTag, deleteTag } = useAdminStore();
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState({ ...emptyForm });
  const [confirmDelete, setConfirmDelete] = useState(null);

  useEffect(() => { fetchTags(); }, [fetchTags]);

  const handleSubmit = async () => {
    if (!form.name.trim()) return;
    let ok;
    if (editingId) {
      ok = await updateTag(editingId, form);
    } else {
      ok = await createTag(form);
    }
    if (ok) {
      setForm({ ...emptyForm });
      setShowForm(false);
      setEditingId(null);
    }
  };

  const startEdit = (tag) => {
    setEditingId(tag.id);
    setForm({
      name: tag.name || '',
      color: tag.color || '#c8ff00',
      type: tag.type || 'community',
      rarity: tag.rarity || 'common',
      description: tag.description || '',
      icon: tag.icon || '',
    });
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    const ok = await deleteTag(id);
    if (ok) setConfirmDelete(null);
  };

  const cancelForm = () => {
    setShowForm(false);
    setEditingId(null);
    setForm({ ...emptyForm });
  };

  const rarityBadge = (rarity) => {
    const colors = {
      common: '#9ca3af', uncommon: '#4ade80', rare: '#60a5fa',
      epic: '#a78bfa', legendary: '#fbbf24', mythic: '#ef4444',
    };
    return (
      <span className="admin-badge" style={{ background: `${colors[rarity] || '#9ca3af'}22`, color: colors[rarity] || '#9ca3af' }}>
        {rarity}
      </span>
    );
  };

  return (
    <div className="admin-section">
      <div className="admin-section__title">Теги</div>

      <div style={{ marginBottom: 16 }}>
        <button className="admin-btn admin-btn--primary" onClick={() => { setShowForm(true); setEditingId(null); setForm({ ...emptyForm }); }}>
          <Plus size={14} /> Создать тег
        </button>
      </div>

      {showForm && (
        <Glass depth={2} radius={14} style={{ padding: 20, marginBottom: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
            <div className="admin-detail-panel__title">{editingId ? 'Редактировать тег' : 'Новый тег'}</div>
            <button className="admin-btn admin-btn--ghost admin-btn--sm" onClick={cancelForm}><X size={14} /></button>
          </div>

          <div className="admin-form-group">
            <label className="admin-form-label">Название</label>
            <input className="admin-input" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
          </div>

          <div style={{ display: 'flex', gap: 12 }}>
            <div className="admin-form-group" style={{ flex: 1 }}>
              <label className="admin-form-label">Цвет</label>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <input type="color" value={form.color} onChange={(e) => setForm((f) => ({ ...f, color: e.target.value }))} style={{ width: 36, height: 32, border: 'none', background: 'none', cursor: 'pointer' }} />
                <input className="admin-input" value={form.color} onChange={(e) => setForm((f) => ({ ...f, color: e.target.value }))} style={{ flex: 1 }} />
              </div>
            </div>
            <div className="admin-form-group" style={{ flex: 1 }}>
              <label className="admin-form-label">Тип</label>
              <select className="admin-select" value={form.type} onChange={(e) => setForm((f) => ({ ...f, type: e.target.value }))} style={{ width: '100%' }}>
                {TAG_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
            <div className="admin-form-group" style={{ flex: 1 }}>
              <label className="admin-form-label">Редкость</label>
              <select className="admin-select" value={form.rarity} onChange={(e) => setForm((f) => ({ ...f, rarity: e.target.value }))} style={{ width: '100%' }}>
                {RARITIES.map((r) => <option key={r} value={r}>{r}</option>)}
              </select>
            </div>
          </div>

          <div className="admin-form-group">
            <label className="admin-form-label">Иконка (текст)</label>
            <input className="admin-input" value={form.icon} onChange={(e) => setForm((f) => ({ ...f, icon: e.target.value }))} placeholder="star, crown, flame..." />
          </div>

          <div className="admin-form-group">
            <label className="admin-form-label">Описание</label>
            <textarea className="admin-textarea" value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} rows={2} />
          </div>

          <button className="admin-btn admin-btn--primary" onClick={handleSubmit}>
            {editingId ? 'Сохранить' : 'Создать'}
          </button>
        </Glass>
      )}

      {loadingTags ? (
        <div className="admin-loading">Загрузка...</div>
      ) : tags.length === 0 ? (
        <div className="admin-empty">Тегов пока нет</div>
      ) : (
        <div className="admin-card-grid">
          {tags.map((tag) => (
            <Glass key={tag.id} depth={1} radius={12} className="admin-card">
              <div className="admin-card__header">
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <div style={{ width: 14, height: 14, borderRadius: '50%', background: tag.color || '#c8ff00', flexShrink: 0 }} />
                  <span style={{ fontWeight: 600, fontSize: 14, color: 'var(--text)' }}>{tag.name}</span>
                </div>
                <div style={{ display: 'flex', gap: 4 }}>
                  <button className="admin-btn admin-btn--ghost admin-btn--sm" onClick={() => startEdit(tag)}>
                    <Pencil size={12} />
                  </button>
                  <button className="admin-btn admin-btn--ghost admin-btn--sm" onClick={() => setConfirmDelete(tag.id)}>
                    <Trash2 size={12} />
                  </button>
                </div>
              </div>
              <div className="admin-card__body" style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <span className="admin-badge admin-badge--user">{tag.type}</span>
                {rarityBadge(tag.rarity)}
              </div>
              {tag.description && (
                <div className="admin-card__meta" style={{ marginTop: 8 }}>{tag.description}</div>
              )}
              <div className="admin-card__meta" style={{ marginTop: 6 }}>
                Пользователей: {tag._count?.users ?? tag.usersCount ?? '---'}
              </div>
            </Glass>
          ))}
        </div>
      )}

      {/* Delete confirmation */}
      {confirmDelete && (
        <div className="admin-modal" onClick={() => setConfirmDelete(null)}>
          <Glass depth={3} radius={16} className="admin-modal__card" onClick={(e) => e.stopPropagation()}>
            <div className="admin-modal__title">Удалить тег?</div>
            <p style={{ color: 'var(--text-muted)', fontSize: 13, marginBottom: 0 }}>
              Тег будет удалён у всех пользователей. Это действие нельзя отменить.
            </p>
            <div className="admin-modal__actions">
              <button className="admin-btn admin-btn--ghost" onClick={() => setConfirmDelete(null)}>Отмена</button>
              <button className="admin-btn admin-btn--danger" onClick={() => handleDelete(confirmDelete)}>Удалить</button>
            </div>
          </Glass>
        </div>
      )}
    </div>
  );
}
