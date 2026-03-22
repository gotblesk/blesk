import { useState } from 'react';
import { Send } from 'lucide-react';
import Glass from '../../ui/Glass';
import { useAdminStore } from '../../../store/adminStore';

export default function AdminBroadcast() {
  const { broadcastUpdate, stats } = useAdminStore();
  const [version, setVersion] = useState('');
  const [changelog, setChangelog] = useState('');
  const [confirmModal, setConfirmModal] = useState(false);
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState(null);

  const handleSend = async () => {
    if (!version.trim() || !changelog.trim()) return;
    setSending(true);
    const data = await broadcastUpdate(version.trim(), changelog.trim());
    setSending(false);
    setConfirmModal(false);
    if (data) {
      setResult({ ok: true, msg: `Рассылка отправлена: ${data.sentTo ?? '?'} пользователей` });
      setVersion('');
      setChangelog('');
    } else {
      setResult({ ok: false, msg: 'Ошибка отправки' });
    }
    setTimeout(() => setResult(null), 5000);
  };

  const onlineCount = stats?.onlineCount ?? '---';

  return (
    <div className="admin-section">
      <div className="admin-section__title">Рассылка обновлений</div>

      <Glass depth={1} radius={14} style={{ padding: 20, maxWidth: 560 }}>
        <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 16 }}>
          Онлайн сейчас: <strong style={{ color: 'var(--accent)' }}>{onlineCount}</strong>
        </div>

        <div className="admin-form-group">
          <label className="admin-form-label">Версия</label>
          <input
            className="admin-input"
            value={version}
            onChange={(e) => setVersion(e.target.value)}
            placeholder="0.5.8-beta"
          />
        </div>

        <div className="admin-form-group">
          <label className="admin-form-label">Changelog</label>
          <textarea
            className="admin-textarea"
            value={changelog}
            onChange={(e) => setChangelog(e.target.value)}
            placeholder="- Новая фича&#10;- Исправлен баг&#10;- Улучшена производительность"
            rows={6}
          />
        </div>

        <button
          className="admin-btn admin-btn--primary"
          onClick={() => setConfirmModal(true)}
          disabled={!version.trim() || !changelog.trim()}
        >
          <Send size={14} /> Отправить
        </button>

        {result && (
          <div style={{
            marginTop: 12,
            padding: '8px 12px',
            borderRadius: 8,
            fontSize: 13,
            background: result.ok ? 'rgba(74, 222, 128, 0.1)' : 'rgba(239, 68, 68, 0.1)',
            color: result.ok ? '#4ade80' : '#ef4444',
          }}>
            {result.msg}
          </div>
        )}
      </Glass>

      {confirmModal && (
        <div className="admin-modal" onClick={() => setConfirmModal(false)}>
          <Glass depth={3} radius={16} className="admin-modal__card" onClick={(e) => e.stopPropagation()}>
            <div className="admin-modal__title">Подтвердить рассылку</div>
            <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>
              Уведомление о версии <strong style={{ color: 'var(--text)' }}>{version}</strong> будет
              отправлено всем онлайн пользователям ({onlineCount}).
            </p>
            <div className="admin-modal__actions">
              <button className="admin-btn admin-btn--ghost" onClick={() => setConfirmModal(false)}>Отмена</button>
              <button className="admin-btn admin-btn--primary" onClick={handleSend} disabled={sending}>
                {sending ? 'Отправка...' : 'Отправить'}
              </button>
            </div>
          </Glass>
        </div>
      )}
    </div>
  );
}
