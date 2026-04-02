import { useState, useEffect, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ArrowLeft, Camera, User, Envelope, DeviceMobile, Lock, Eye, EyeSlash, CaretRight, Check } from '@phosphor-icons/react';
import Avatar from '../ui/Avatar';
import AvatarCropModal from './AvatarCropModal';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import { formatJoinDate } from '../../utils/months';
import './ProfileEditor.css';

const STATUS_PRESETS = [
  { id: 'online', color: '#4ade80', label: 'В сети' },
  { id: 'dnd', color: '#f59e0b', label: 'Не беспокоить' },
  { id: 'invisible', color: '#6b7280', label: 'Невидимка' },
];

export default function ProfileEditor({ open, onClose, user, onUserUpdate }) {
  // Form state
  const [nickname, setNickname] = useState('');
  const [bio, setBio] = useState('');
  const [status, setStatus] = useState('online');
  const [customStatus, setCustomStatus] = useState('');

  // UI state
  const [nickError, setNickError] = useState('');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [dirty, setDirty] = useState(false);

  // Email
  const [emailStep, setEmailStep] = useState('display');
  const [emailCode, setEmailCode] = useState('');
  const [emailSending, setEmailSending] = useState(false);
  const [emailError, setEmailError] = useState('');

  // Password
  const [openCell, setOpenCell] = useState(null);
  const [pwStep, setPwStep] = useState('idle');
  const [pwCurrent, setPwCurrent] = useState('');
  const [pwNew, setPwNew] = useState('');
  const [pwConfirm, setPwConfirm] = useState('');
  const [pwCode, setPwCode] = useState('');
  const [pwEmail, setPwEmail] = useState('');
  const [pwLoading, setPwLoading] = useState(false);
  const [pwError, setPwError] = useState('');
  const [pwSuccess, setPwSuccess] = useState(false);
  const [showPwCurrent, setShowPwCurrent] = useState(false);
  const [showPwNew, setShowPwNew] = useState(false);
  const [showPwConfirm, setShowPwConfirm] = useState(false);

  // Avatar
  const fileInputRef = useRef(null);
  const [cropImage, setCropImage] = useState(null);

  // C3: ref for handleSave to avoid stale closure in keydown effect
  const handleSaveRef = useRef(null);

  // Init form from user
  useEffect(() => {
    if (open && user) {
      setNickname(user.username || '');
      setBio(user.bio || '');
      setStatus(user.status || 'online');
      setCustomStatus(user.customStatus || '');
      setDirty(false);
      setSaved(false);
      setSaveError('');
      setNickError('');
      setEmailStep('display');
      setEmailCode('');
      setEmailError('');
      setPwStep('idle');
      setPwCurrent('');
      setPwNew('');
      setPwConfirm('');
      setPwCode('');
      setPwError('');
      setPwSuccess(false);
      setShowPwCurrent(false);
      setShowPwNew(false);
      setShowPwConfirm(false);
      setOpenCell(null);
    }
  }, [open, user]);

  // Track dirty
  useEffect(() => {
    if (!user) return;
    const isDirty =
      nickname.trim() !== (user.username || '') ||
      bio !== (user.bio || '') ||
      status !== (user.status || 'online') ||
      customStatus !== (user.customStatus || '');
    setDirty(isDirty);
  }, [nickname, bio, status, customStatus, user]);

  // Keyboard — uses handleSaveRef to avoid stale closure (C3 fix)
  useEffect(() => {
    if (!open) return;
    const handleKey = (e) => {
      if (e.key === 'Escape') {
        if (dirty) {
          if (window.confirm('Есть несохранённые изменения. Выйти?')) onClose?.();
        } else {
          onClose?.();
        }
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        if (dirty && !saving) handleSaveRef.current?.();
      }
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [open, dirty, saving, onClose]);

  const getH = () => ({ 'Content-Type': 'application/json', ...getAuthHeaders() });

  // Save main fields
  const handleSave = async () => {
    setSaving(true);
    setSaveError('');
    setNickError('');

    const body = { bio, status, customStatus };

    if (nickname.trim() && nickname.trim() !== user?.username) {
      if (nickname.trim().length < 2) { setNickError('Минимум 2 символа'); setSaving(false); return; }
      if (nickname.trim().length > 32) { setNickError('Максимум 32 символа'); setSaving(false); return; }
      body.username = nickname.trim();
    }

    try {
      const res = await fetch(`${API_URL}/api/users/me`, {
        method: 'PUT', headers: getH(), credentials: 'include', body: JSON.stringify(body),
      });
      if (res.ok) {
        const data = await res.json();
        onUserUpdate?.(data);
        setSaved(true);
        setDirty(false);
        setTimeout(() => setSaved(false), 2000);
      } else {
        const err = await res.json().catch(() => ({}));
        if (err.error?.includes('ник') || err.error?.includes('username')) setNickError(err.error);
        else setSaveError(err.error || 'Ошибка');
      }
    } catch { setSaveError('Нет подключения'); }
    finally { setSaving(false); }
  };

  // C3: keep ref in sync so keydown effect always calls latest handleSave
  useEffect(() => { handleSaveRef.current = handleSave; });

  // Email verification
  const handleSendEmailCode = async () => {
    setEmailError(''); setEmailSending(true);
    try {
      const res = await fetch(`${API_URL}/api/auth/resend-code`, { method: 'POST', headers: getH(), credentials: 'include' });
      if (res.ok) setEmailStep('verify');
      else setEmailError((await res.json()).error || 'Ошибка');
    } catch { setEmailError('Ошибка'); }
    finally { setEmailSending(false); }
  };

  const handleVerifyEmail = async () => {
    setEmailError(''); setEmailSending(true);
    try {
      const res = await fetch(`${API_URL}/api/auth/verify-email`, {
        method: 'POST', headers: getH(), credentials: 'include', body: JSON.stringify({ code: emailCode }),
      });
      const d = await res.json();
      if (res.ok || d.success) {
        onUserUpdate?.({ emailVerified: true });
        setEmailStep('display'); setEmailCode('');
      } else setEmailError(d.error || 'Неверный код');
    } catch { setEmailError('Ошибка'); }
    finally { setEmailSending(false); }
  };

  // Password change
  const handlePwRequest = async () => {
    setPwError(''); setPwLoading(true);
    try {
      const res = await fetch(`${API_URL}/api/auth/change-password/request`, {
        method: 'POST', headers: getH(), credentials: 'include',
      });
      const d = await res.json();
      if (res.ok) { setPwEmail(d.email || ''); setPwStep('code'); }
      else setPwError(d.error || 'Ошибка');
    } catch { setPwError('Ошибка'); }
    finally { setPwLoading(false); }
  };

  const handlePwConfirm = async () => {
    setPwError('');
    if (!pwCurrent) { setPwError('Введите текущий пароль'); return; }
    if (pwNew.length < 8) { setPwError('Минимум 8 символов'); return; }
    if (pwNew !== pwConfirm) { setPwError('Пароли не совпадают'); return; }
    if (pwCode.length < 6) { setPwError('Введите код'); return; }
    setPwLoading(true);
    try {
      const res = await fetch(`${API_URL}/api/auth/change-password/confirm`, {
        method: 'POST', headers: getH(), credentials: 'include',
        body: JSON.stringify({ code: pwCode, currentPassword: pwCurrent, newPassword: pwNew }),
      });
      if (res.ok) {
        setPwSuccess(true); setPwStep('idle');
        setPwCurrent(''); setPwNew(''); setPwConfirm(''); setPwCode('');
        setTimeout(() => setPwSuccess(false), 3000);
      } else setPwError((await res.json()).error || 'Ошибка');
    } catch { setPwError('Ошибка'); }
    finally { setPwLoading(false); }
  };

  // Avatar
  const handleFileSelect = (e) => {
    const f = e.target.files?.[0]; if (!f) return;
    if (f.size > 15 * 1024 * 1024) { setSaveError('Макс. 15 МБ'); return; }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(f.type)) { setSaveError('JPG, PNG, WebP'); return; }
    if (cropImage) URL.revokeObjectURL(cropImage);
    setCropImage(URL.createObjectURL(f)); e.target.value = '';
  };

  const handleAvatarSave = async (blob) => {
    try {
      const fd = new FormData();
      fd.append('avatar', blob, 'avatar.jpg');
      const res = await fetch(`${API_URL}/api/users/me/avatar`, {
        method: 'POST', headers: { ...getAuthHeaders() }, credentials: 'include', body: fd,
      });
      if (res.ok) {
        const data = await res.json();
        onUserUpdate?.({ avatar: data.avatar });
      } else {
        const err = await res.json().catch(() => ({}));
        setSaveError(err.error || 'Ошибка загрузки аватара');
      }
    } catch { setSaveError('Нет подключения к серверу'); }
    finally { if (cropImage) URL.revokeObjectURL(cropImage); setCropImage(null); }
  };

  const handleBack = () => {
    if (dirty) {
      if (window.confirm('Есть несохранённые изменения. Выйти?')) onClose?.();
    } else {
      onClose?.();
    }
  };

  const joinDate = user?.createdAt ? formatJoinDate(user.createdAt) : '';
  const tag = user?.tag?.startsWith('#') ? user.tag : `#${user?.tag || '0000'}`;

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="peditor"
          initial={{ x: '100%', opacity: 0 }}
          animate={{ x: 0, opacity: 1 }}
          exit={{ x: '100%', opacity: 0 }}
          transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
          data-testid="profile-editor"
        >
          {/* Header */}
          <div className="peditor__header">
            <button className="peditor__back" onClick={handleBack} data-testid="profile-editor-back-btn">
              <ArrowLeft size={18} weight="regular" />
            </button>
            <span className="peditor__title">Профиль</span>
          </div>

          <div className="peditor__scroll">
            {/* Avatar */}
            <div className="peditor__avatar-section">
              <div className="peditor__avatar" onClick={() => fileInputRef.current?.click()} data-testid="profile-editor-avatar">
                <Avatar user={user} size={96} />
                <div className="peditor__avatar-overlay">
                  <Camera size={20} weight="regular" />
                </div>
              </div>
              <input ref={fileInputRef} type="file" accept="image/jpeg,image/png,image/webp" onChange={handleFileSelect} hidden />
              <div className="peditor__avatar-info">
                <span className="peditor__avatar-name">{user?.username}</span>
                <span className="peditor__avatar-sub">{tag} · {joinDate}</span>
              </div>
            </div>

            {/* Section: Основное */}
            <div className="peditor__section-label">Основное</div>

            {/* Nickname */}
            <div className="peditor__field" data-testid="profile-editor-nickname-input">
              <div className="peditor__field-label"><User size={13} weight="regular" /> Никнейм</div>
              <input
                className="peditor__input"
                value={nickname}
                onChange={e => setNickname(e.target.value.slice(0, 32))}
                placeholder="Никнейм"
                maxLength={32}
              />
              {nickError && <span className="peditor__err">{nickError}</span>}
            </div>

            {/* Email */}
            <EditorCell
              icon={<Envelope size={15} weight="regular" />}
              iconBg="color-mix(in srgb, var(--online) 8%, transparent)"
              iconColor="var(--online)"
              label="Email"
              value={user?.email || 'Не указан'}
              dot={user?.emailVerified ? 'var(--online)' : null}
              isOpen={openCell === 'email'}
              onToggle={() => setOpenCell(openCell === 'email' ? null : 'email')}
              testId="profile-editor-email-cell"
            >
              {user?.emailVerified ? (
                <span className="peditor__hint">Email подтверждён</span>
              ) : emailStep === 'display' ? (
                <motion.button className="peditor__cell-action" onClick={handleSendEmailCode} disabled={emailSending} whileTap={{ scale: 0.95 }}>
                  {emailSending ? '...' : 'Отправить код подтверждения'}
                </motion.button>
              ) : (
                <>
                  <span className="peditor__hint">Код отправлен на {user?.email}</span>
                  <div className="peditor__cell-row">
                    <input className="peditor__cell-input peditor__cell-input--sm" value={emailCode} onChange={e => setEmailCode(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="Код" maxLength={6} autoFocus />
                    <motion.button className="peditor__cell-action" onClick={handleVerifyEmail} disabled={emailSending || emailCode.length < 4} whileTap={{ scale: 0.95 }}>ОК</motion.button>
                  </div>
                  {emailError && <span className="peditor__err">{emailError}</span>}
                </>
              )}
            </EditorCell>

            {/* Phone */}
            <EditorCell
              icon={<DeviceMobile size={15} weight="regular" />}
              iconBg="color-mix(in srgb, var(--edit-color, #888) 6%, transparent)"
              iconColor="var(--edit-color, #888)"
              label="Телефон"
              value={user?.phone || 'Не привязан'}
              muted={!user?.phone}
              isOpen={openCell === 'phone'}
              onToggle={() => setOpenCell(openCell === 'phone' ? null : 'phone')}
              testId="profile-editor-phone-cell"
            >
              <span className="peditor__hint">Привязка скоро будет доступна</span>
            </EditorCell>

            {/* Password */}
            <EditorCell
              icon={<Lock size={15} weight="regular" />}
              iconBg="color-mix(in srgb, var(--accent) 5%, transparent)"
              iconColor="var(--accent)"
              label="Пароль"
              value="●●●●●●●●"
              isOpen={openCell === 'pass'}
              onToggle={() => { setOpenCell(openCell === 'pass' ? null : 'pass'); if (pwStep === 'idle' && !pwSuccess) handlePwRequest(); }}
              testId="profile-editor-password-cell"
            >
              {pwSuccess ? (
                <span className="peditor__ok"><Check size={13} weight="regular" /> Пароль изменён</span>
              ) : pwStep === 'code' ? (
                <>
                  <span className="peditor__hint">Код на {pwEmail}</span>
                  <PwInput value={pwCurrent} onChange={setPwCurrent} show={showPwCurrent} toggle={() => setShowPwCurrent(p => !p)} placeholder="Текущий пароль" />
                  <PwInput value={pwNew} onChange={setPwNew} show={showPwNew} toggle={() => setShowPwNew(p => !p)} placeholder="Новый (мин. 8)" />
                  <PwInput value={pwConfirm} onChange={setPwConfirm} show={showPwConfirm} toggle={() => setShowPwConfirm(p => !p)} placeholder="Повторите" />
                  <input className="peditor__cell-input" value={pwCode} onChange={e => setPwCode(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="Код из email" maxLength={6} />
                  {pwError && <span className="peditor__err">{pwError}</span>}
                  <div className="peditor__cell-row">
                    <motion.button className="peditor__cell-action" onClick={handlePwConfirm} disabled={pwLoading} whileTap={{ scale: 0.95 }}>{pwLoading ? '...' : 'Подтвердить'}</motion.button>
                    <motion.button className="peditor__cell-action peditor__cell-action--ghost" onClick={() => { setPwStep('idle'); setPwError(''); setOpenCell(null); }} whileTap={{ scale: 0.95 }}>Отмена</motion.button>
                  </div>
                </>
              ) : (
                <span className="peditor__hint">{pwLoading ? 'Отправляем код...' : 'Нажмите чтобы сменить пароль'}</span>
              )}
            </EditorCell>

            {/* Section: О себе */}
            <div className="peditor__section-label">О себе</div>

            <div className="peditor__field" data-testid="profile-editor-bio-input">
              <textarea
                className="peditor__textarea"
                value={bio}
                onChange={e => setBio(e.target.value.slice(0, 200))}
                placeholder="О себе..."
                rows={3}
                maxLength={200}
              />
              <div className="peditor__counter">
                <span className={bio.length > 180 ? 'peditor__counter--warn' : ''}>{bio.length}/200</span>
              </div>
            </div>

            {/* Section: Статус */}
            <div className="peditor__section-label">Статус</div>

            <div className="peditor__status-presets" data-testid="profile-editor-status-preset">
              {STATUS_PRESETS.map(s => (
                <button
                  key={s.id}
                  className={`peditor__preset ${status === s.id ? 'peditor__preset--active' : ''}`}
                  onClick={() => setStatus(s.id)}
                  data-status={s.id}
                >
                  <span className="peditor__preset-dot" style={{ background: s.color }} />
                  {s.label}
                </button>
              ))}
            </div>

            <div className="peditor__field" data-testid="profile-editor-custom-status">
              <input
                className="peditor__input"
                value={customStatus}
                onChange={e => setCustomStatus(e.target.value.slice(0, 50))}
                placeholder="Кастомный статус..."
                maxLength={50}
              />
              <div className="peditor__counter">
                <span>{customStatus.length}/50</span>
              </div>
            </div>

            {saveError && <span className="peditor__err peditor__err--global">{saveError}</span>}

            {/* Save */}
            <motion.button
              className="peditor__save"
              onClick={handleSave}
              disabled={saving || !dirty}
              whileTap={dirty ? { scale: 0.97 } : {}}
              data-testid="profile-editor-save-btn"
            >
              {saving ? '...' : saved ? <><Check size={13} weight="regular" /> Сохранено</> : 'Сохранить'}
            </motion.button>
          </div>

          {cropImage && <AvatarCropModal imageSrc={cropImage} onClose={() => setCropImage(null)} onSave={handleAvatarSave} />}
        </motion.div>
      )}
    </AnimatePresence>
  );
}

// ═══════ Expandable Cell ═══════
function EditorCell({ icon, iconBg, iconColor, label, value, dot, muted, isOpen, onToggle, children, testId }) {
  return (
    <div className={`peditor__cell ${isOpen ? 'peditor__cell--open' : ''}`} onClick={onToggle} data-testid={testId}>
      <div className="peditor__cell-main">
        <div className="peditor__cell-ico" style={{ background: iconBg, color: iconColor }}>{icon}</div>
        <div className="peditor__cell-info">
          <div className="peditor__cell-key">{label}</div>
          <div className={`peditor__cell-val ${muted ? 'peditor__cell-val--muted' : ''}`}>
            {value}
            {dot && <span className="peditor__cell-dot" style={{ background: dot, boxShadow: `0 0 6px ${dot}` }} />}
          </div>
        </div>
        <motion.div className="peditor__cell-chev" animate={{ rotate: isOpen ? 90 : 0 }} transition={{ duration: 0.2 }}>
          <CaretRight size={14} weight="regular" />
        </motion.div>
      </div>
      <AnimatePresence>
        {isOpen && (
          <motion.div
            className="peditor__cell-drawer"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
          >
            <div className="peditor__cell-drawer-inner" onClick={e => e.stopPropagation()}>{children}</div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// ═══════ Password Input ═══════
function PwInput({ value, onChange, show, toggle, placeholder }) {
  return (
    <div className="peditor__pw-wrap">
      <input className="peditor__cell-input" type={show ? 'text' : 'password'} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} onClick={e => e.stopPropagation()} />
      <button className="peditor__eye" onClick={e => { e.stopPropagation(); toggle(); }} type="button" tabIndex={-1}>
        {show ? <EyeSlash size={13} weight="regular" /> : <Eye size={13} weight="regular" />}
      </button>
    </div>
  );
}
