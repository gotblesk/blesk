import { useState, useEffect } from 'react';
import API_URL from '../../config';
import './ProfileScreen.css';

export default function ProfileScreen({ open, onClose, user, onUserUpdate }) {
  const [bio, setBio] = useState('');
  const [hue, setHue] = useState(176);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState('');

  // Email verification
  const [emailCode, setEmailCode] = useState('');
  const [emailStep, setEmailStep] = useState('display'); // display | verify
  const [emailSending, setEmailSending] = useState(false);
  const [emailError, setEmailError] = useState('');

  // Password change
  const [pwStep, setPwStep] = useState('idle'); // idle | code | confirm
  const [pwCurrent, setPwCurrent] = useState('');
  const [pwNew, setPwNew] = useState('');
  const [pwConfirm, setPwConfirm] = useState('');
  const [pwCode, setPwCode] = useState('');
  const [pwEmail, setPwEmail] = useState('');
  const [pwLoading, setPwLoading] = useState(false);
  const [pwError, setPwError] = useState('');
  const [pwSuccess, setPwSuccess] = useState(false);

  // Загрузить текущие данные при открытии
  useEffect(() => {
    if (open && user) {
      setBio(user.bio || '');
      setHue(user.hue ?? 176);
      setSaved(false);
      setEmailStep('display');
      setEmailCode('');
      setEmailError('');
      setPwStep('idle');
      setPwCurrent('');
      setPwNew('');
      setPwConfirm('');
      setPwCode('');
      setPwEmail('');
      setPwError('');
      setPwSuccess(false);
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
    setSaveError('');
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
      } else {
        setSaveError('Не удалось сохранить');
      }
    } catch {
      setSaveError('Нет подключения к серверу');
    } finally {
      setSaving(false);
    }
  };

  // Отправить код подтверждения на email
  const handleSendEmailCode = async () => {
    setEmailError('');
    setEmailSending(true);
    try {
      const token = localStorage.getItem('token');
      const res = await fetch(`${API_URL}/api/auth/resend-code`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
      });
      const data = await res.json();
      if (res.ok) {
        setEmailStep('verify');
      } else {
        setEmailError(data.error || 'Ошибка');
      }
    } catch {
      setEmailError('Не удалось отправить код');
    } finally {
      setEmailSending(false);
    }
  };

  // Верифицировать email
  const handleVerifyEmail = async () => {
    setEmailError('');
    setEmailSending(true);
    try {
      const token = localStorage.getItem('token');
      const res = await fetch(`${API_URL}/api/auth/verify-email`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ code: emailCode }),
      });
      const data = await res.json();
      if (res.ok || data.success) {
        onUserUpdate?.({ emailVerified: true });
        setEmailStep('display');
        setEmailCode('');
      } else {
        setEmailError(data.error || 'Неверный код');
      }
    } catch {
      setEmailError('Ошибка верификации');
    } finally {
      setEmailSending(false);
    }
  };

  // Запросить код для смены пароля
  const handlePwRequest = async () => {
    setPwError('');
    setPwLoading(true);
    try {
      const token = localStorage.getItem('token');
      const res = await fetch(`${API_URL}/api/auth/change-password/request`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
      });
      const data = await res.json();
      if (res.ok) {
        setPwEmail(data.email || '');
        setPwStep('code');
      } else {
        setPwError(data.error || 'Ошибка');
      }
    } catch {
      setPwError('Не удалось отправить код');
    } finally {
      setPwLoading(false);
    }
  };

  // Подтвердить смену пароля
  const handlePwConfirm = async () => {
    setPwError('');
    if (!pwCurrent) { setPwError('Введите текущий пароль'); return; }
    if (pwNew.length < 8) { setPwError('Новый пароль — минимум 8 символов'); return; }
    if (pwNew !== pwConfirm) { setPwError('Пароли не совпадают'); return; }
    if (pwCode.length < 6) { setPwError('Введите код'); return; }

    setPwLoading(true);
    try {
      const token = localStorage.getItem('token');
      const res = await fetch(`${API_URL}/api/auth/change-password/confirm`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          code: pwCode,
          currentPassword: pwCurrent,
          newPassword: pwNew,
        }),
      });
      const data = await res.json();
      if (res.ok) {
        setPwSuccess(true);
        setPwStep('idle');
        setPwCurrent('');
        setPwNew('');
        setPwConfirm('');
        setPwCode('');
        setTimeout(() => setPwSuccess(false), 3000);
      } else {
        setPwError(data.error || 'Ошибка');
      }
    } catch {
      setPwError('Ошибка подключения');
    } finally {
      setPwLoading(false);
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

        {/* Email */}
        <div className="profile-field">
          <label className="profile-field__label">📧 Email</label>
          {emailStep === 'display' && (
            <div className="profile-email-row">
              <span className="profile-email-value">
                {user?.email || 'Не указан'}
              </span>
              {user?.email && !user?.emailVerified && (
                <button
                  className="profile-email-btn"
                  onClick={handleSendEmailCode}
                  disabled={emailSending}
                >
                  {emailSending ? '...' : 'Подтвердить'}
                </button>
              )}
              {user?.email && user?.emailVerified && (
                <span className="profile-email-verified">✓ Подтверждён</span>
              )}
            </div>
          )}

          {emailStep === 'verify' && (
            <div className="profile-email-verify">
              <div className="profile-email-hint">
                Код отправлен на {user?.email}
              </div>
              <div className="profile-email-code-row">
                <input
                  type="text"
                  className="profile-email-code-input"
                  value={emailCode}
                  onChange={(e) => setEmailCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="Код..."
                  maxLength={6}
                  autoFocus
                />
                <button
                  className="profile-email-btn"
                  onClick={handleVerifyEmail}
                  disabled={emailSending || emailCode.length < 4}
                >
                  {emailSending ? '...' : 'ОК'}
                </button>
                <button
                  className="profile-email-btn profile-email-btn--cancel"
                  onClick={() => { setEmailStep('display'); setEmailCode(''); setEmailError(''); }}
                >
                  ✕
                </button>
              </div>
              {emailError && <div className="profile-email-error">{emailError}</div>}
            </div>
          )}
        </div>

        {/* Телефон (пока только отображение) */}
        <div className="profile-field">
          <label className="profile-field__label">📱 Телефон</label>
          <div className="profile-email-row">
            <span className="profile-email-value">
              {user?.phone || 'Не привязан'}
            </span>
            {!user?.phone && (
              <span className="profile-email-hint-text">Скоро</span>
            )}
          </div>
        </div>

        {/* Смена пароля */}
        <div className="profile-field">
          <label className="profile-field__label">🔒 Пароль</label>

          {pwStep === 'idle' && !pwSuccess && (
            <button
              className="profile-email-btn"
              onClick={handlePwRequest}
              disabled={pwLoading}
              style={{ marginTop: 4 }}
            >
              {pwLoading ? '...' : 'Сменить пароль'}
            </button>
          )}

          {pwSuccess && (
            <div className="profile-pw-success">✓ Пароль изменён</div>
          )}

          {pwStep === 'code' && (
            <div className="profile-pw-form">
              <div className="profile-email-hint">
                Код отправлен на {pwEmail}
              </div>

              <input
                type="password"
                className="profile-pw-input"
                value={pwCurrent}
                onChange={(e) => setPwCurrent(e.target.value)}
                placeholder="Текущий пароль"
              />
              <input
                type="password"
                className="profile-pw-input"
                value={pwNew}
                onChange={(e) => setPwNew(e.target.value)}
                placeholder="Новый пароль (мин. 8)"
              />
              <input
                type="password"
                className="profile-pw-input"
                value={pwConfirm}
                onChange={(e) => setPwConfirm(e.target.value)}
                placeholder="Повторите новый"
              />
              <input
                type="text"
                className="profile-email-code-input"
                value={pwCode}
                onChange={(e) => setPwCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="Код из email"
                maxLength={6}
                style={{ marginTop: 8 }}
              />

              {pwError && <div className="profile-email-error">{pwError}</div>}

              <div className="profile-pw-actions">
                <button
                  className="profile-email-btn"
                  onClick={handlePwConfirm}
                  disabled={pwLoading}
                >
                  {pwLoading ? '...' : 'Подтвердить'}
                </button>
                <button
                  className="profile-email-btn profile-email-btn--cancel"
                  onClick={() => {
                    setPwStep('idle');
                    setPwError('');
                    setPwCurrent('');
                    setPwNew('');
                    setPwConfirm('');
                    setPwCode('');
                  }}
                >
                  Отмена
                </button>
              </div>
            </div>
          )}
        </div>

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

        {/* Ошибка сохранения */}
        {saveError && (
          <div className="profile-save-error">{saveError}</div>
        )}

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
