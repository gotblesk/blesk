import { useState, useEffect, useRef } from 'react';
import { Mail, Smartphone, Lock, Camera, X, Check } from 'lucide-react';
import API_URL from '../../config';
import Avatar from '../ui/Avatar';
import AvatarCropModal from './AvatarCropModal';
import './ProfileScreen.css';

export default function ProfileScreen({ open, onClose, user, onUserUpdate }) {
  const [bio, setBio] = useState('');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState('');

  // Email verification
  const [emailCode, setEmailCode] = useState('');
  const [emailStep, setEmailStep] = useState('display'); // display | verify
  const [emailSending, setEmailSending] = useState(false);
  const [emailError, setEmailError] = useState('');

  // Password visibility
  const [showPwCurrent, setShowPwCurrent] = useState(false);
  const [showPwNew, setShowPwNew] = useState(false);
  const [showPwConfirm, setShowPwConfirm] = useState(false);

  // Avatar upload
  const fileInputRef = useRef(null);
  const [cropImage, setCropImage] = useState(null);
  const [avatarUploading, setAvatarUploading] = useState(false);

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
      setShowPwCurrent(false);
      setShowPwNew(false);
      setShowPwConfirm(false);
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
        body: JSON.stringify({ bio }),
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

  // Обработка выбора файла для аватара
  const handleFileSelect = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) {
      setSaveError('Файл слишком большой (макс. 5 МБ)');
      return;
    }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      setSaveError('Формат не поддерживается (JPG, PNG, WebP)');
      return;
    }
    setCropImage(URL.createObjectURL(file));
    e.target.value = '';
  };

  // Загрузить обрезанный аватар
  const handleAvatarSave = async (blob) => {
    setAvatarUploading(true);
    try {
      const token = localStorage.getItem('token');
      const formData = new FormData();
      formData.append('avatar', blob, 'avatar.jpg');
      const res = await fetch(`${API_URL}/api/users/me/avatar`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      });
      if (res.ok) {
        const data = await res.json();
        onUserUpdate?.({ avatar: data.avatar });
        setCropImage(null);
      } else {
        setCropImage(null);
        setSaveError('Ошибка загрузки аватара');
      }
    } catch {
      setCropImage(null);
      setSaveError('Нет подключения к серверу');
    } finally {
      setAvatarUploading(false);
    }
  };

  if (!open) return null;

  return (
    <div className="profile-overlay" onClick={onClose}>
      <div
        className="profile-card"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="profile-card__shine" />

        <button className="profile-close" onClick={onClose}>
          <X size={16} strokeWidth={2} />
        </button>

        {/* Аватар */}
        <div className="profile-avatar-area">
          <Avatar user={user} size="xl" className="profile-avatar" onClick={() => fileInputRef.current?.click()}>
            <div className="profile-avatar__camera"><Camera size={20} strokeWidth={1.5} /></div>
          </Avatar>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            onChange={handleFileSelect}
            style={{ display: 'none' }}
          />
        </div>

        {/* Модальное окно кропа */}
        {cropImage && (
          <AvatarCropModal
            imageSrc={cropImage}
            onClose={() => setCropImage(null)}
            onSave={handleAvatarSave}
          />
        )}

        {/* Имя и тег */}
        <div className="profile-username">{user?.username}</div>
        <div className="profile-tag">{user?.tag}</div>

        <div className="profile-sep" />

        {/* Email */}
        <div className="profile-field">
          <label className="profile-field__label"><Mail size={14} strokeWidth={1.5} style={{ verticalAlign: 'middle', marginRight: 4 }} /> Email</label>
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
                <span className="profile-email-verified"><Check size={14} strokeWidth={2} /> Подтверждён</span>
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
                  <X size={14} strokeWidth={2} />
                </button>
              </div>
              {emailError && <div className="profile-email-error">{emailError}</div>}
            </div>
          )}
        </div>

        {/* Телефон (пока только отображение) */}
        <div className="profile-field">
          <label className="profile-field__label"><Smartphone size={14} strokeWidth={1.5} style={{ verticalAlign: 'middle', marginRight: 4 }} /> Телефон</label>
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
          <label className="profile-field__label"><Lock size={14} strokeWidth={1.5} style={{ verticalAlign: 'middle', marginRight: 4 }} /> Пароль</label>

          {pwStep === 'idle' && !pwSuccess && (
            <button
              className="profile-pw-link"
              onClick={handlePwRequest}
              disabled={pwLoading}
            >
              {pwLoading ? '...' : 'Сменить пароль →'}
            </button>
          )}

          {pwSuccess && (
            <div className="profile-pw-success"><Check size={14} strokeWidth={2} /> Пароль изменён</div>
          )}

          {pwStep === 'code' && (
            <div className="profile-pw-form">
              <div className="profile-email-hint">
                Код отправлен на {pwEmail}
              </div>

              <div className="profile-pw-input-wrap">
                <input
                  type={showPwCurrent ? 'text' : 'password'}
                  className="profile-pw-input"
                  value={pwCurrent}
                  onChange={(e) => setPwCurrent(e.target.value)}
                  placeholder="Текущий пароль"
                />
                <button
                  type="button"
                  className="profile-eye-toggle"
                  onClick={() => setShowPwCurrent(!showPwCurrent)}
                  tabIndex={-1}
                >
                  {showPwCurrent ? (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                      <line x1="1" y1="1" x2="23" y2="23"/>
                    </svg>
                  ) : (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                  )}
                </button>
              </div>
              <div className="profile-pw-input-wrap">
                <input
                  type={showPwNew ? 'text' : 'password'}
                  className="profile-pw-input"
                  value={pwNew}
                  onChange={(e) => setPwNew(e.target.value)}
                  placeholder="Новый пароль (мин. 8)"
                />
                <button
                  type="button"
                  className="profile-eye-toggle"
                  onClick={() => setShowPwNew(!showPwNew)}
                  tabIndex={-1}
                >
                  {showPwNew ? (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                      <line x1="1" y1="1" x2="23" y2="23"/>
                    </svg>
                  ) : (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                  )}
                </button>
              </div>
              <div className="profile-pw-input-wrap">
                <input
                  type={showPwConfirm ? 'text' : 'password'}
                  className="profile-pw-input"
                  value={pwConfirm}
                  onChange={(e) => setPwConfirm(e.target.value)}
                  placeholder="Повторите новый"
                />
                <button
                  type="button"
                  className="profile-eye-toggle"
                  onClick={() => setShowPwConfirm(!showPwConfirm)}
                  tabIndex={-1}
                >
                  {showPwConfirm ? (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                      <line x1="1" y1="1" x2="23" y2="23"/>
                    </svg>
                  ) : (
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                  )}
                </button>
              </div>
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
          {saving ? 'Сохраняю...' : saved ? <><Check size={14} strokeWidth={2} /> Сохранено</> : 'Сохранить'}
        </button>
      </div>
    </div>
  );
}
