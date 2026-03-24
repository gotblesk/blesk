import { useState, useEffect, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Mail, Smartphone, Lock, Camera, X, Check, Eye, EyeOff, ChevronRight } from 'lucide-react';
import API_URL from '../../config';
import Avatar from '../ui/Avatar';
import AvatarCropModal from './AvatarCropModal';
import { getAvatarHue } from '../../utils/avatar';
import './ProfileScreen.css';

export default function ProfileScreen({ open, onClose, user, onUserUpdate }) {
  const [bio, setBio] = useState('');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [emailCode, setEmailCode] = useState('');
  const [emailStep, setEmailStep] = useState('display');
  const [emailSending, setEmailSending] = useState(false);
  const [emailError, setEmailError] = useState('');
  const [showPwCurrent, setShowPwCurrent] = useState(false);
  const [showPwNew, setShowPwNew] = useState(false);
  const [showPwConfirm, setShowPwConfirm] = useState(false);
  const fileInputRef = useRef(null);
  const [cropImage, setCropImage] = useState(null);
  const [pwStep, setPwStep] = useState('idle');
  const [pwCurrent, setPwCurrent] = useState('');
  const [pwNew, setPwNew] = useState('');
  const [pwConfirm, setPwConfirm] = useState('');
  const [pwCode, setPwCode] = useState('');
  const [pwEmail, setPwEmail] = useState('');
  const [pwLoading, setPwLoading] = useState(false);
  const [pwError, setPwError] = useState('');
  const [pwSuccess, setPwSuccess] = useState(false);

  // Flip state
  const [flipped, setFlipped] = useState(false);
  const [flipAnim, setFlipAnim] = useState(false);
  // 3D tilt
  const [tilt, setTilt] = useState({ x: 0, y: 0 });
  const sceneRef = useRef(null);
  // Expanded cells
  const [openCell, setOpenCell] = useState(null);

  useEffect(() => {
    if (open && user) {
      setBio(user.bio || ''); setSaved(false); setSaveError('');
      setEmailStep('display'); setEmailCode(''); setEmailError('');
      setPwStep('idle'); setPwCurrent(''); setPwNew(''); setPwConfirm('');
      setPwCode(''); setPwEmail(''); setPwError(''); setPwSuccess(false);
      setShowPwCurrent(false); setShowPwNew(false); setShowPwConfirm(false);
      setFlipped(false); setOpenCell(null);
    }
  }, [open, user]);

  useEffect(() => {
    if (!open) return;
    const h = e => { if (e.key === 'Escape') onClose?.(); };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [open, onClose]);

  // 3D tilt
  const handleMouseMove = useCallback((e) => {
    if (!sceneRef.current) return;
    const r = sceneRef.current.getBoundingClientRect();
    setTilt({
      x: ((e.clientX - r.left) / r.width - 0.5) * 7,
      y: -((e.clientY - r.top) / r.height - 0.5) * 4,
    });
  }, []);
  const handleMouseLeave = useCallback(() => setTilt({ x: 0, y: 0 }), []);

  // Flip
  const doFlip = useCallback(() => {
    setFlipped(f => !f);
    setFlipAnim(true);
    setTimeout(() => setFlipAnim(false), 800);
  }, []);

  // Handlers
  const getH = () => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('token')}` });

  const handleSave = async () => {
    setSaving(true); setSaveError('');
    try { const res = await fetch(`${API_URL}/api/users/me`, { method: 'PUT', headers: getH(), body: JSON.stringify({ bio }) }); if (res.ok) { onUserUpdate?.(await res.json()); setSaved(true); setTimeout(() => setSaved(false), 2000); } else setSaveError('Ошибка'); } catch { setSaveError('Нет подключения'); } finally { setSaving(false); }
  };
  const handleSendEmailCode = async () => {
    setEmailError(''); setEmailSending(true);
    try { const res = await fetch(`${API_URL}/api/auth/resend-code`, { method: 'POST', headers: getH() }); if (res.ok) setEmailStep('verify'); else setEmailError((await res.json()).error || 'Ошибка'); } catch { setEmailError('Ошибка'); } finally { setEmailSending(false); }
  };
  const handleVerifyEmail = async () => {
    setEmailError(''); setEmailSending(true);
    try { const res = await fetch(`${API_URL}/api/auth/verify-email`, { method: 'POST', headers: getH(), body: JSON.stringify({ code: emailCode }) }); const d = await res.json(); if (res.ok || d.success) { onUserUpdate?.({ emailVerified: true }); setEmailStep('display'); setEmailCode(''); } else setEmailError(d.error || 'Неверный код'); } catch { setEmailError('Ошибка'); } finally { setEmailSending(false); }
  };
  const handlePwRequest = async () => {
    setPwError(''); setPwLoading(true);
    try { const res = await fetch(`${API_URL}/api/auth/change-password/request`, { method: 'POST', headers: getH() }); const d = await res.json(); if (res.ok) { setPwEmail(d.email || ''); setPwStep('code'); } else setPwError(d.error || 'Ошибка'); } catch { setPwError('Ошибка'); } finally { setPwLoading(false); }
  };
  const handlePwConfirm = async () => {
    setPwError('');
    if (!pwCurrent) { setPwError('Введите текущий пароль'); return; }
    if (pwNew.length < 8) { setPwError('Минимум 8 символов'); return; }
    if (pwNew !== pwConfirm) { setPwError('Пароли не совпадают'); return; }
    if (pwCode.length < 6) { setPwError('Введите код'); return; }
    setPwLoading(true);
    try { const res = await fetch(`${API_URL}/api/auth/change-password/confirm`, { method: 'POST', headers: getH(), body: JSON.stringify({ code: pwCode, currentPassword: pwCurrent, newPassword: pwNew }) }); if (res.ok) { setPwSuccess(true); setPwStep('idle'); setPwCurrent(''); setPwNew(''); setPwConfirm(''); setPwCode(''); setTimeout(() => setPwSuccess(false), 3000); } else setPwError((await res.json()).error || 'Ошибка'); } catch { setPwError('Ошибка'); } finally { setPwLoading(false); }
  };
  const handleFileSelect = (e) => {
    const f = e.target.files?.[0]; if (!f) return;
    if (f.size > 5 * 1024 * 1024) { setSaveError('Макс. 5 МБ'); return; }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(f.type)) { setSaveError('JPG, PNG, WebP'); return; }
    setCropImage(URL.createObjectURL(f)); e.target.value = '';
  };
  const handleAvatarSave = async (blob) => {
    try { const fd = new FormData(); fd.append('avatar', blob, 'avatar.jpg'); const res = await fetch(`${API_URL}/api/users/me/avatar`, { method: 'POST', headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }, body: fd }); if (res.ok) onUserUpdate?.({ avatar: (await res.json()).avatar }); } catch {} finally { setCropImage(null); }
  };

  const hue = user ? getAvatarHue(user) : 200;
  const flipDeg = (flipped ? 180 : 0) + tilt.x;
  const cardStyle = {
    transform: `rotateY(${flipDeg}deg) rotateX(${tilt.y}deg)`,
    transition: flipAnim ? 'transform 0.75s cubic-bezier(0.16, 1, 0.3, 1)' : 'transform 0.12s ease-out',
  };

  const MONTHS = ['января','февраля','марта','апреля','мая','июня','июля','августа','сентября','октября','ноября','декабря'];
  const since = user?.createdAt ? (() => { const d = new Date(user.createdAt); return `с ${MONTHS[d.getMonth()]} ${d.getFullYear()}`; })() : '';

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="pf-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose}>
          <div className="pf-scene" ref={sceneRef} onMouseMove={handleMouseMove} onMouseLeave={handleMouseLeave} onClick={e => e.stopPropagation()}>
            <div className="pf-card" style={cardStyle}>

              {/* ═══════ FRONT ═══════ */}
              <div className="pf-face pf-front">
                {/* Blurred avatar background */}
                {user?.avatar && (
                  <div className="pf-front__bg" style={{ backgroundImage: `url(${API_URL}/uploads/avatars/${user.avatar})` }} />
                )}
                <div className="pf-front__grain" />
                <div className="pf-front__holo" />
                <div className="pf-front__shimmer" />
                <div className="pf-front__wm">{(user?.username || 'G')[0].toUpperCase()}</div>

                <div className="pf-front__top">
                  <span className="pf-front__logo">blesk</span>
                  <span className="pf-front__issue">ISSUE {user?.tag || '#0000'}<br/>{since.toUpperCase()}</span>
                </div>

                <div className="pf-front__body">
                  <div className="pf-front__ava" onClick={() => fileInputRef.current?.click()}>
                    <Avatar user={user} size={110} />
                    <div className="pf-front__ava-ring" />
                    <div className="pf-front__ava-hover"><Camera size={16} /> Фото</div>
                  </div>
                  <input ref={fileInputRef} type="file" accept="image/jpeg,image/png,image/webp" onChange={handleFileSelect} hidden />
                  <div className="pf-front__name">{user?.username}</div>
                  <div className="pf-front__tag">{user?.tag}</div>
                  {user?.status === 'online' && (
                    <div className="pf-front__status"><div className="pf-front__dot" /> В сети</div>
                  )}
                </div>

                <SlideToFlip label="потяни → редактировать" direction="right" onFlip={doFlip} />
              </div>

              {/* ═══════ BACK ═══════ */}
              <div className="pf-face pf-back">
                <div className="pf-back__inner">
                <div className="pf-back__head">
                  <div className="pf-back__ava"><Avatar user={user} size={48} /></div>
                  <div>
                    <div className="pf-back__name">{user?.username}</div>
                    <div className="pf-back__sub">{user?.tag} · {since}</div>
                  </div>
                </div>

                <div className="pf-back__scroll">
                  {/* Email */}
                  <ProfileCell
                    icon={<Mail size={15} />}
                    iconBg="rgba(74,222,128,0.08)"
                    iconColor="#4ade80"
                    label="Email"
                    value={user?.email || 'Не указан'}
                    dot={user?.emailVerified ? '#4ade80' : null}
                    isOpen={openCell === 'email'}
                    onToggle={() => setOpenCell(openCell === 'email' ? null : 'email')}
                  >
                    {user?.emailVerified ? (
                      <span className="pf-cell__hint">Email подтверждён</span>
                    ) : emailStep === 'display' ? (
                      <motion.button className="pf-cell__action" onClick={handleSendEmailCode} disabled={emailSending} whileTap={{ scale: 0.95 }}>
                        {emailSending ? '...' : 'Отправить код подтверждения'}
                      </motion.button>
                    ) : (
                      <>
                        <span className="pf-cell__hint">Код отправлен на {user?.email}</span>
                        <div className="pf-cell__row">
                          <input className="pf-cell__input pf-cell__input--sm" value={emailCode} onChange={e => setEmailCode(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="Код" maxLength={6} autoFocus onClick={e => e.stopPropagation()} />
                          <motion.button className="pf-cell__action" onClick={handleVerifyEmail} disabled={emailSending || emailCode.length < 4} whileTap={{ scale: 0.95 }}>ОК</motion.button>
                        </div>
                        {emailError && <span className="pf-cell__err">{emailError}</span>}
                      </>
                    )}
                  </ProfileCell>

                  {/* Phone */}
                  <ProfileCell
                    icon={<Smartphone size={15} />}
                    iconBg="rgba(245,158,11,0.06)"
                    iconColor="#f59e0b"
                    label="Телефон"
                    value={user?.phone || 'Не привязан'}
                    muted={!user?.phone}
                    isOpen={openCell === 'phone'}
                    onToggle={() => setOpenCell(openCell === 'phone' ? null : 'phone')}
                  >
                    <span className="pf-cell__hint">Привязка скоро будет доступна</span>
                  </ProfileCell>

                  {/* Password */}
                  <ProfileCell
                    icon={<Lock size={15} />}
                    iconBg="rgba(200,255,0,0.05)"
                    iconColor="#c8ff00"
                    label="Пароль"
                    value="●●●●●●●●"
                    isOpen={openCell === 'pass'}
                    onToggle={() => { setOpenCell(openCell === 'pass' ? null : 'pass'); if (pwStep === 'idle' && !pwSuccess) handlePwRequest(); }}
                  >
                    {pwSuccess ? (
                      <span className="pf-cell__ok"><Check size={13} /> Пароль изменён</span>
                    ) : pwStep === 'code' ? (
                      <>
                        <span className="pf-cell__hint">Код на {pwEmail}</span>
                        <PwInput value={pwCurrent} onChange={setPwCurrent} show={showPwCurrent} toggle={() => setShowPwCurrent(p => !p)} placeholder="Текущий пароль" />
                        <PwInput value={pwNew} onChange={setPwNew} show={showPwNew} toggle={() => setShowPwNew(p => !p)} placeholder="Новый (мин. 8)" />
                        <PwInput value={pwConfirm} onChange={setPwConfirm} show={showPwConfirm} toggle={() => setShowPwConfirm(p => !p)} placeholder="Повторите" />
                        <input className="pf-cell__input" value={pwCode} onChange={e => setPwCode(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="Код из email" maxLength={6} onClick={e => e.stopPropagation()} />
                        {pwError && <span className="pf-cell__err">{pwError}</span>}
                        <div className="pf-cell__row">
                          <motion.button className="pf-cell__action" onClick={handlePwConfirm} disabled={pwLoading} whileTap={{ scale: 0.95 }}>{pwLoading ? '...' : 'Подтвердить'}</motion.button>
                          <motion.button className="pf-cell__action pf-cell__action--ghost" onClick={() => { setPwStep('idle'); setPwError(''); setOpenCell(null); }} whileTap={{ scale: 0.95 }}>Отмена</motion.button>
                        </div>
                      </>
                    ) : (
                      <span className="pf-cell__hint">{pwLoading ? 'Отправляем код...' : 'Нажмите чтобы сменить пароль'}</span>
                    )}
                  </ProfileCell>

                  {/* Bio */}
                  <div className="pf-back__bio">
                    <textarea className="pf-back__bio-ta" value={bio} onChange={e => setBio(e.target.value.slice(0, 200))} placeholder="О себе..." rows={2} onClick={e => e.stopPropagation()} />
                    <div className="pf-back__bio-bar">
                      <span className="pf-back__bio-n">{bio.length}/200</span>
                    </div>
                  </div>

                  {saveError && <span className="pf-cell__err">{saveError}</span>}

                  <motion.button className="pf-back__save" onClick={handleSave} disabled={saving} whileTap={{ scale: 0.97 }}>
                    {saving ? '...' : saved ? <><Check size={13} /> Сохранено</> : 'Сохранить'}
                  </motion.button>
                </div>

                </div>{/* close pf-back__inner */}
                <SlideToFlip label="← потяни назад" direction="left" onFlip={doFlip} />
              </div>
            </div>
          </div>

          {cropImage && <AvatarCropModal imageSrc={cropImage} onClose={() => setCropImage(null)} onSave={handleAvatarSave} />}
        </motion.div>
      )}
    </AnimatePresence>
  );
}

// ═══════ SLIDE TO FLIP ═══════
function SlideToFlip({ label, direction, onFlip }) {
  const thumbRef = useRef(null);
  const trackRef = useRef(null);
  const [dragging, setDragging] = useState(false);
  const startXRef = useRef(0);
  const [thumbX, setThumbX] = useState(0);
  const [glow, setGlow] = useState(0);

  const maxX = 164; // track width - thumb

  const handleDown = (e) => {
    e.stopPropagation();
    setDragging(true);
    startXRef.current = e.clientX || e.touches?.[0]?.clientX || 0;
    thumbRef.current?.setPointerCapture?.(e.pointerId);
  };

  const handleMove = (e) => {
    if (!dragging) return;
    e.stopPropagation();
    const clientX = e.clientX || e.touches?.[0]?.clientX || 0;
    const dx = clientX - startXRef.current;
    const val = direction === 'right' ? Math.max(0, Math.min(dx, maxX)) : Math.max(0, Math.min(-dx, maxX));
    setThumbX(direction === 'right' ? val : -val);
    setGlow(Math.abs(val) / maxX);
  };

  const handleUp = (e) => {
    if (!dragging) return;
    e.stopPropagation();
    setDragging(false);
    if (glow > 0.6) onFlip();
    setThumbX(0);
    setGlow(0);
  };

  return (
    <div className="pf-slider" onClick={e => e.stopPropagation()}>
      <div className="pf-slider__track" ref={trackRef}>
        <div className="pf-slider__hint-bg" />
      </div>
      <span className="pf-slider__text">{label}</span>
      <div
        ref={thumbRef}
        className={`pf-slider__thumb ${direction === 'left' ? 'pf-slider__thumb--right' : ''}`}
        style={{
          transform: `translateX(${thumbX}px)`,
          boxShadow: glow > 0 ? `0 0 ${4 + glow * 16}px rgba(200,255,0,${0.1 + glow * 0.3})` : 'none',
          borderColor: `rgba(200,255,0,${0.3 + glow * 0.5})`,
        }}
        onPointerDown={handleDown}
        onPointerMove={handleMove}
        onPointerUp={handleUp}
      >
        <span className="pf-slider__arrow">{direction === 'right' ? '→' : '←'}</span>
      </div>
    </div>
  );
}

// ═══════ CELL ═══════
function ProfileCell({ icon, iconBg, iconColor, label, value, dot, muted, isOpen, onToggle, children }) {
  return (
    <div className={`pf-cell ${isOpen ? 'pf-cell--open' : ''}`}>
      <div className="pf-cell__main" onClick={onToggle}>
        <div className="pf-cell__ico" style={{ background: iconBg, color: iconColor }}>{icon}</div>
        <div className="pf-cell__info">
          <div className="pf-cell__key">{label}</div>
          <div className={`pf-cell__val ${muted ? 'pf-cell__val--muted' : ''}`}>
            {value}
            {dot && <span className="pf-cell__dot" style={{ background: dot, boxShadow: `0 0 6px ${dot}` }} />}
          </div>
        </div>
        <motion.div className="pf-cell__chev" animate={{ rotate: isOpen ? 90 : 0 }} transition={{ duration: 0.2 }}>
          <ChevronRight size={14} />
        </motion.div>
      </div>
      <AnimatePresence>
        {isOpen && (
          <motion.div className="pf-cell__drawer" initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} exit={{ height: 0, opacity: 0 }} transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}>
            <div className="pf-cell__drawer-inner">{children}</div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// ═══════ PW INPUT ═══════
function PwInput({ value, onChange, show, toggle, placeholder }) {
  return (
    <div className="pf-cell__pw-wrap">
      <input className="pf-cell__input" type={show ? 'text' : 'password'} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} onClick={e => e.stopPropagation()} />
      <button className="pf-cell__eye" onClick={e => { e.stopPropagation(); toggle(); }} type="button" tabIndex={-1}>{show ? <EyeOff size={13} /> : <Eye size={13} />}</button>
    </div>
  );
}
