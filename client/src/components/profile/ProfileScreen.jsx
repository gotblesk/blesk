import { useState, useEffect } from 'react';
import API_URL from '../../config';
import './ProfileScreen.css';

export default function ProfileScreen({ open, onClose, user, onUserUpdate }) {
  const [bio, setBio] = useState('');
  const [hue, setHue] = useState(176);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  // Загрузить текущие данные при открытии
  useEffect(() => {
    if (open && user) {
      setBio(user.bio || '');
      setHue(user.hue ?? 176);
      setSaved(false);
    }
  }, [open, user]);

  // Escape закрывает
  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (e.key === 'Escape') onClose?.();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open, onClose]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const token = localStorage.getItem('token');
      const res = await fetch(`${API_URL}/api/users/me`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ bio, hue }),
      });

      if (res.ok) {
        const updated = await res.json();
        onUserUpdate?.(updated);
        setSaved(true);
        setTimeout(() => setSaved(false), 2000);
      }
    } catch {
      // тихий фейл
    } finally {
      setSaving(false);
    }
  };

  if (!open) return null;

  const initial = (user?.username || 'U')[0].toUpperCase();

  return (
    <div className="profile-overlay" onClick={onClose}>
      <div
        className="profile-card"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="profile-card__shine" />

        <button className="profile-close" onClick={onClose}>
          <span>✕</span>
        </button>

        {/* Аватар */}
        <div className="profile-avatar-area">
          <div
            className="profile-avatar"
            style={{ background: `hsl(${hue}, 70%, 50%)` }}
          >
            <span className="profile-avatar__letter">{initial}</span>
            <div className="profile-avatar__glow" style={{ background: `hsl(${hue}, 70%, 50%)` }} />
          </div>
        </div>

        {/* Имя и тег */}
        <div className="profile-username">{user?.username}</div>
        <div className="profile-tag">{user?.tag}</div>

        <div className="profile-sep" />

        {/* Bio */}
        <div className="profile-field">
          <label className="profile-field__label">О себе</label>
          <textarea
            className="profile-field__textarea"
            value={bio}
            onChange={(e) => setBio(e.target.value.slice(0, 200))}
            placeholder="Расскажи о себе..."
            rows={3}
          />
          <div className="profile-field__counter">{bio.length}/200</div>
        </div>

        {/* Hue слайдер */}
        <div className="profile-field">
          <label className="profile-field__label">Цвет профиля</label>
          <div className="profile-hue">
            <input
              type="range"
              min="0"
              max="360"
              value={hue}
              onChange={(e) => setHue(Number(e.target.value))}
              className="profile-hue__slider"
              style={{
                '--hue': hue,
              }}
            />
            <div
              className="profile-hue__preview"
              style={{ background: `hsl(${hue}, 70%, 50%)` }}
            />
          </div>
        </div>

        <div className="profile-sep" />

        {/* Кнопка сохранения */}
        <button
          className={`profile-save ${saved ? 'profile-save--done' : ''}`}
          onClick={handleSave}
          disabled={saving}
        >
          {saving ? 'Сохраняю...' : saved ? '✓ Сохранено' : 'Сохранить'}
        </button>
      </div>
    </div>
  );
}
