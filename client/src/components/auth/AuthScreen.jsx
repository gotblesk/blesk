import { useState, useEffect, useRef, useCallback } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { User, Lock, Mail, KeyRound, Eye, EyeOff } from 'lucide-react';
import gsap from 'gsap';
import GravityCard from './GravityCard';
import PasswordCard from './PasswordCard';
import MetaballBackground from '../ui/MetaballBackground';
import { getPasswordScore } from './StrengthDots';
import useAppVersion from '../../hooks/useAppVersion';
import API_URL from '../../config';
import './AuthScreen.css';

export default function AuthScreen({ onLogin, collapsing, pendingVerification, onVerified }) {
  const appVersion = useAppVersion();

  // Phase: intro → form
  const [phase, setPhase] = useState(pendingVerification ? 'form' : 'intro');

  // Mode: login | register | verify | forgot | forgot-code | forgot-reset
  const [mode, setMode] = useState(pendingVerification ? 'verify' : 'login');

  // Form state
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [errorKey, setErrorKey] = useState(0);
  const [loading, setLoading] = useState(false);

  // Password visibility
  const [showLoginPw, setShowLoginPw] = useState(false);

  // Forgot password state
  const [forgotEmail, setForgotEmail] = useState('');
  const [forgotCode, setForgotCode] = useState('');
  const [forgotNewPassword, setForgotNewPassword] = useState('');
  const [forgotConfirm, setForgotConfirm] = useState('');
  const [forgotError, setForgotError] = useState('');
  const [forgotErrorKey, setForgotErrorKey] = useState(0);
  const [forgotLoading, setForgotLoading] = useState(false);
  const [forgotSuccess, setForgotSuccess] = useState(false);
  const [forgotCodeDigits, setForgotCodeDigits] = useState(['', '', '', '', '', '']);
  const forgotCodeRefs = useRef([]);

  // Verify state
  const [verifyEmail, setVerifyEmail] = useState(pendingVerification?.user?.email || '');
  const [verifyToken, setVerifyToken] = useState(pendingVerification?.token || '');
  const [verifyRefresh, setVerifyRefresh] = useState(pendingVerification?.refreshToken || '');
  const [verifyUser, setVerifyUser] = useState(pendingVerification?.user || null);
  const [codeDigits, setCodeDigits] = useState(['', '', '', '', '', '']);
  const [resendTimer, setResendTimer] = useState(60);
  const [verifyLoading, setVerifyLoading] = useState(false);
  const [verifyError, setVerifyError] = useState('');
  const [verifyErrorKey, setVerifyErrorKey] = useState(0);
  const codeRefs = useRef([]);

  // Brand intro → form
  useEffect(() => {
    if (pendingVerification) return;
    const timer = setTimeout(() => setPhase('form'), 2500);
    return () => clearTimeout(timer);
  }, [pendingVerification]);

  // Resend timer
  useEffect(() => {
    if (mode !== 'verify' || resendTimer <= 0) return;
    const t = setTimeout(() => setResendTimer((p) => p - 1), 1000);
    return () => clearTimeout(t);
  }, [mode, resendTimer]);

  // Trigger error with key increment for re-shake
  const triggerError = (msg) => {
    setError(msg);
    setErrorKey((k) => k + 1);
  };

  const triggerForgotError = (msg) => {
    setForgotError(msg);
    setForgotErrorKey((k) => k + 1);
  };

  const triggerVerifyError = (msg) => {
    setVerifyError(msg);
    setVerifyErrorKey((k) => k + 1);
  };

  // ═══════ VALIDATION ═══════
  const validate = () => {
    if (username.length < 3) {
      triggerError('Имя пользователя — минимум 3 символа');
      return false;
    }
    if (password.length < 8) {
      triggerError('Пароль — минимум 8 символов');
      return false;
    }
    if (mode === 'register') {
      if (!email) {
        triggerError('Email обязателен');
        return false;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        triggerError('Некорректный email');
        return false;
      }
      if (getPasswordScore(password) < 3) {
        triggerError('Пароль слишком простой');
        return false;
      }
      if (password !== confirmPassword) {
        triggerError('Пароли не совпадают');
        return false;
      }
    }
    return true;
  };

  // ═══════ LOGIN / REGISTER ═══════
  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!validate()) return;

    setLoading(true);
    try {
      const isRegister = mode === 'register';
      const endpoint = isRegister ? '/api/auth/register' : '/api/auth/login';
      const body = isRegister
        ? { username, password, email }
        : { username, password };

      const res = await fetch(`${API_URL}${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });

      const data = await res.json();

      if (!res.ok) {
        triggerError(data.error || 'Ошибка');
        return;
      }

      // Email not verified → verify screen
      if (data.user.email && data.user.emailVerified === false) {
        setVerifyToken(data.token || data.accessToken);
        setVerifyUser(data.user);
        setVerifyEmail(data.user.email);
        setVerifyRefresh(data.refreshToken || '');
        setCodeDigits(['', '', '', '', '', '']);
        setVerifyError('');
        setResendTimer(60);
        setMode('verify');
        return;
      }

      onLogin(data);
    } catch {
      triggerError('Не удалось подключиться к серверу');
    } finally {
      setLoading(false);
    }
  };

  // ═══════ VERIFY CODE ═══════
  const submitCode = useCallback(async (digits) => {
    const code = digits.join('');
    if (code.length !== 6) return;

    setVerifyLoading(true);
    setVerifyError('');
    try {
      const res = await fetch(`${API_URL}/api/auth/verify-email`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${verifyToken}`,
        },
        body: JSON.stringify({ code }),
      });

      const data = await res.json();

      if (!res.ok) {
        triggerVerifyError(data.error || 'Ошибка');
        setCodeDigits(['', '', '', '', '', '']);
        setTimeout(() => codeRefs.current[0]?.focus(), 100);
        return;
      }

      if (pendingVerification && onVerified) {
        onVerified();
      } else {
        onLogin({
          user: { ...verifyUser, emailVerified: true },
          token: verifyToken,
          refreshToken: verifyRefresh,
        });
      }
    } catch {
      triggerVerifyError('Не удалось подключиться к серверу');
    } finally {
      setVerifyLoading(false);
    }
  }, [verifyToken, verifyRefresh, verifyUser, onLogin, pendingVerification, onVerified]);

  const handleCodeInput = (index, value) => {
    if (value.length > 1) {
      const digits = value.replace(/\D/g, '').slice(0, 6).split('');
      const newCode = [...codeDigits];
      digits.forEach((d, i) => {
        if (index + i < 6) newCode[index + i] = d;
      });
      setCodeDigits(newCode);
      const nextIdx = Math.min(index + digits.length, 5);
      codeRefs.current[nextIdx]?.focus();
      if (newCode.every((d) => d !== '')) submitCode(newCode);
      return;
    }

    const digit = value.replace(/\D/g, '');
    const newCode = [...codeDigits];
    newCode[index] = digit;
    setCodeDigits(newCode);
    if (digit && index < 5) codeRefs.current[index + 1]?.focus();
    if (newCode.every((d) => d !== '')) submitCode(newCode);
  };

  const handleCodeKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !codeDigits[index] && index > 0) {
      codeRefs.current[index - 1]?.focus();
    }
  };

  const handleResend = async () => {
    if (resendTimer > 0) return;
    try {
      await fetch(`${API_URL}/api/auth/resend-code`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${verifyToken}`,
        },
      });
      setResendTimer(60);
      setVerifyError('');
    } catch {
      triggerVerifyError('Не удалось отправить код');
    }
  };

  // ═══════ FORGOT PASSWORD ═══════
  const handleForgotSend = async () => {
    if (!forgotEmail) {
      triggerForgotError('Введите email');
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(forgotEmail)) {
      triggerForgotError('Некорректный email');
      return;
    }
    setForgotLoading(true);
    setForgotError('');
    try {
      const res = await fetch(`${API_URL}/api/auth/forgot-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: forgotEmail }),
      });
      const data = await res.json();
      if (!res.ok) {
        triggerForgotError(data.error || 'Ошибка');
        return;
      }
      setForgotError('');
      setMode('forgot-code');
      setForgotCodeDigits(['', '', '', '', '', '']);
    } catch {
      triggerForgotError('Не удалось подключиться к серверу');
    } finally {
      setForgotLoading(false);
    }
  };

  const handleForgotCodeInput = (index, value) => {
    if (value.length > 1) {
      const digits = value.replace(/\D/g, '').slice(0, 6).split('');
      const newCode = [...forgotCodeDigits];
      digits.forEach((d, i) => {
        if (index + i < 6) newCode[index + i] = d;
      });
      setForgotCodeDigits(newCode);
      const nextIdx = Math.min(index + digits.length, 5);
      forgotCodeRefs.current[nextIdx]?.focus();
      if (newCode.every((d) => d !== '')) {
        setForgotCode(newCode.join(''));
        setMode('forgot-reset');
      }
      return;
    }
    const digit = value.replace(/\D/g, '');
    const newCode = [...forgotCodeDigits];
    newCode[index] = digit;
    setForgotCodeDigits(newCode);
    if (digit && index < 5) forgotCodeRefs.current[index + 1]?.focus();
    if (newCode.every((d) => d !== '')) {
      setForgotCode(newCode.join(''));
      setMode('forgot-reset');
    }
  };

  const handleForgotCodeKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !forgotCodeDigits[index] && index > 0) {
      forgotCodeRefs.current[index - 1]?.focus();
    }
  };

  const handleForgotReset = async (e) => {
    e.preventDefault();
    if (forgotNewPassword.length < 8) {
      triggerForgotError('Пароль — минимум 8 символов');
      return;
    }
    if (forgotNewPassword !== forgotConfirm) {
      triggerForgotError('Пароли не совпадают');
      return;
    }
    setForgotLoading(true);
    setForgotError('');
    try {
      const res = await fetch(`${API_URL}/api/auth/reset-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: forgotEmail,
          code: forgotCode,
          newPassword: forgotNewPassword,
        }),
      });
      const data = await res.json();
      if (!res.ok) {
        triggerForgotError(data.error || 'Ошибка');
        return;
      }
      setForgotSuccess(true);
      setTimeout(() => {
        switchMode('login');
        setForgotEmail('');
        setForgotCode('');
        setForgotNewPassword('');
        setForgotConfirm('');
        setForgotError('');
        setForgotSuccess(false);
        setForgotCodeDigits(['', '', '', '', '', '']);
      }, 2000);
    } catch {
      triggerForgotError('Не удалось подключиться к серверу');
    } finally {
      setForgotLoading(false);
    }
  };

  // ═══════ MODE SWITCHING ═══════
  const switchMode = (newMode) => {
    setMode(newMode);
    setError('');
    setErrorKey(0);
    if (newMode === 'login' || newMode === 'register') {
      setPassword('');
      setConfirmPassword('');
      setEmail('');
    }
  };

  // ═══════ BUTTON RIPPLE ═══════
  const handleRipple = (e) => {
    const btn = e.currentTarget;
    const rect = btn.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    const ripple = document.createElement('div');
    ripple.style.cssText = `
      position: absolute; border-radius: 50%;
      background: radial-gradient(circle, rgba(255,255,255,0.25), transparent);
      width: 0; height: 0; left: ${x}px; top: ${y}px;
      transform: translate(-50%, -50%); pointer-events: none;
    `;
    btn.appendChild(ripple);

    gsap.to(ripple, {
      width: rect.width * 2.5,
      height: rect.width * 2.5,
      opacity: 0,
      duration: 0.5,
      ease: 'power2.out',
      onComplete: () => ripple.remove(),
    });
  };

  // ═══════ MASKED EMAIL ═══════
  const maskedEmail = verifyEmail
    ? verifyEmail.replace(/^(.{2})(.*)(@.*)$/, (_, a, b, c) => a + '•'.repeat(b.length) + c)
    : '';

  // ═══════ CODE GRID RENDERER ═══════
  const renderCodeGrid = (digits, refs, onInput, onKeyDown, disabled = false) => (
    <div className="verify-code-grid">
      {digits.map((digit, i) => (
        <input
          key={i}
          ref={(el) => (refs.current[i] = el)}
          className={`verify-code-cell ${digit ? 'filled' : ''}`}
          type="text"
          inputMode="numeric"
          maxLength={6}
          value={digit}
          onChange={(e) => onInput(i, e.target.value)}
          onKeyDown={(e) => onKeyDown(i, e)}
          onFocus={(e) => e.target.select()}
          autoFocus={i === 0}
          disabled={disabled}
        />
      ))}
    </div>
  );

  // ═══════ RENDER ═══════

  // Brand intro
  if (phase === 'intro') {
    return (
      <div className="auth-screen">
        <motion.div
          className="auth-intro"
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          transition={{ duration: 0.8, ease: 'easeOut' }}
        >
          <img src="./blesk.png" alt="blesk" onError={(e) => { e.target.style.display = 'none'; }} />
          <div className="auth-intro-tagline">Твой блеск. Твои правила.</div>
        </motion.div>
      </div>
    );
  }

  return (
    <div className={`auth-screen ${collapsing ? 'auth-screen--collapsing' : ''}`}>
      {/* Metaball background behind everything */}
      <MetaballBackground subtle />

      <div className="auth-center">
        {/* Gravity Cards */}
        <div className="auth-content">
          {/* Logo above cards */}
          <div className="auth-logo">
            <img src="./blesk.png" alt="blesk" onError={(e) => { e.target.style.display = 'none'; }} />
            <div className="auth-tagline">твой блеск. твои правила.</div>
            <div className="auth-version">v{appVersion}</div>
          </div>

          <AnimatePresence mode="wait">
            {/* ═══════ LOGIN ═══════ */}
            {mode === 'login' && (
              <motion.form
                key="login"
                onSubmit={handleSubmit}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                transition={{ duration: 0.3 }}
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}
              >
                <GravityCard
                  tilt={-1.5}
                  index={0}
                  icon={<span style={{ color: 'var(--accent)' }}><User size={16} stroke="currentColor" /></span>}
                  title="Кто ты?"
                  subtitle="Имя в мире blesk"
                  error={error && error.includes('Имя') ? error : null}
                  errorKey={errorKey}
                >
                  <div className="g-input-wrap">
                    <input
                      className="g-input"
                      type="text"
                      placeholder="имя пользователя"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      autoComplete="off"
                      spellCheck="false"
                      autoFocus
                      aria-label="Имя пользователя"
                    />
                  </div>
                </GravityCard>

                <GravityCard
                  tilt={1}
                  index={1}
                  icon={<span style={{ color: 'var(--accent)' }}><Lock size={16} stroke="currentColor" /></span>}
                  title="Пароль"
                  subtitle="Введите пароль"
                  error={error && !error.includes('Имя') ? error : null}
                  errorKey={errorKey}
                >
                  <div className="g-input-wrap">
                    <input
                      className="g-input"
                      type={showLoginPw ? 'text' : 'password'}
                      placeholder="••••••••"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      aria-label="Пароль"
                    />
                    <button
                      type="button"
                      className="g-input-action"
                      onClick={() => setShowLoginPw(!showLoginPw)}
                      aria-label={showLoginPw ? 'Скрыть' : 'Показать'}
                    >
                      {showLoginPw ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                </GravityCard>

                <div className="g-action">
                  <button
                    type="submit"
                    className={`g-btn ${loading ? 'g-btn--loading' : ''}`}
                    disabled={loading}
                    onClick={handleRipple}
                  >
                    {loading ? <>Входим<span className="g-spinner" /></> : 'Войти'}
                  </button>
                </div>

                <div className="auth-footer">
                  <button type="button" className="auth-footer-link" onClick={() => {
                    setForgotEmail('');
                    setForgotError('');
                    setForgotSuccess(false);
                    setForgotCodeDigits(['', '', '', '', '', '']);
                    switchMode('forgot');
                  }}>
                    Забыли пароль?
                  </button>
                  <div className="auth-footer-sep" />
                  <button type="button" className="auth-footer-link" onClick={() => switchMode('register')}>
                    Создать аккаунт
                  </button>
                </div>
              </motion.form>
            )}

            {/* ═══════ REGISTER ═══════ */}
            {mode === 'register' && (
              <motion.form
                key="register"
                onSubmit={handleSubmit}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                transition={{ duration: 0.3 }}
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '14px' }}
              >
                <GravityCard
                  tilt={-1.5}
                  index={0}
                  icon={<span style={{ color: 'var(--accent)' }}><User size={16} stroke="currentColor" /></span>}
                  title="Придумай имя"
                  subtitle="Твой ник в blesk"
                  error={error && error.includes('Имя') ? error : null}
                  errorKey={errorKey}
                >
                  <div className="g-input-wrap">
                    <input
                      className="g-input"
                      type="text"
                      placeholder="имя пользователя"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      autoComplete="off"
                      spellCheck="false"
                      autoFocus
                      aria-label="Имя пользователя"
                    />
                  </div>
                </GravityCard>

                <GravityCard
                  tilt={1}
                  index={1}
                  icon={<span style={{ color: 'var(--accent)' }}><Mail size={16} stroke="currentColor" /></span>}
                  title="Куда писать?"
                  subtitle="Для подтверждения"
                  error={error && error.includes('email') ? error : null}
                  errorKey={errorKey}
                >
                  <div className="g-input-wrap">
                    <input
                      className="g-input"
                      type="email"
                      placeholder="email@example.com"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      autoComplete="off"
                      aria-label="Email"
                    />
                  </div>
                </GravityCard>

                <PasswordCard
                  tilt={-0.7}
                  index={2}
                  password={password}
                  confirmPassword={confirmPassword}
                  onPasswordChange={setPassword}
                  onConfirmChange={setConfirmPassword}
                  error={error && (error.includes('Пароль') || error.includes('совпад')) ? error : null}
                  errorKey={errorKey}
                />

                <div className="g-action">
                  <button
                    type="submit"
                    className={`g-btn ${loading ? 'g-btn--loading' : ''}`}
                    disabled={loading}
                    onClick={handleRipple}
                  >
                    {loading ? <>Создаём<span className="g-spinner" /></> : 'Создать аккаунт'}
                  </button>
                </div>

                <div className="auth-footer">
                  <button type="button" className="auth-footer-link" onClick={() => switchMode('login')}>
                    Уже есть аккаунт? <span className="auth-footer-accent">Войти</span>
                  </button>
                </div>
              </motion.form>
            )}

            {/* ═══════ VERIFY ═══════ */}
            {mode === 'verify' && (
              <motion.div
                key="verify"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                transition={{ duration: 0.3 }}
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}
              >
                <GravityCard
                  tilt={0}
                  index={0}
                  icon={<span style={{ color: 'var(--online)' }}><Mail size={16} stroke="currentColor" /></span>}
                  title="Проверь почту"
                  subtitle={`Код отправлен на ${maskedEmail}`}
                  error={verifyError}
                  errorKey={verifyErrorKey}
                >
                  {renderCodeGrid(codeDigits, codeRefs, handleCodeInput, handleCodeKeyDown, verifyLoading)}
                  <div className="verify-resend">
                    {resendTimer > 0 ? (
                      <>Отправить повторно через <span className="verify-resend-timer">{resendTimer}с</span></>
                    ) : (
                      <button type="button" className="verify-resend-btn" onClick={handleResend}>
                        Отправить код повторно
                      </button>
                    )}
                  </div>
                </GravityCard>

                <div className="auth-footer">
                  <button type="button" className="auth-footer-link" onClick={() => {
                    setCodeDigits(['', '', '', '', '', '']);
                    setVerifyError('');
                    setResendTimer(60);
                    switchMode('login');
                  }}>
                    ← Назад к входу
                  </button>
                </div>
              </motion.div>
            )}

            {/* ═══════ FORGOT ═══════ */}
            {mode === 'forgot' && (
              <motion.form
                key="forgot"
                onSubmit={(e) => { e.preventDefault(); handleForgotSend(); }}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                transition={{ duration: 0.3 }}
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}
              >
                <GravityCard
                  tilt={0}
                  index={0}
                  icon={<span style={{ color: 'var(--info)' }}><Mail size={16} stroke="currentColor" /></span>}
                  title="Вспомним?"
                  subtitle="Email для восстановления"
                  error={forgotError}
                  errorKey={forgotErrorKey}
                >
                  <div className="g-input-wrap">
                    <input
                      className="g-input"
                      type="email"
                      placeholder="email@example.com"
                      value={forgotEmail}
                      onChange={(e) => setForgotEmail(e.target.value)}
                      autoComplete="off"
                      autoFocus
                      aria-label="Email"
                    />
                  </div>
                </GravityCard>

                <div className="g-action">
                  <button
                    type="submit"
                    className={`g-btn ${forgotLoading ? 'g-btn--loading' : ''}`}
                    disabled={forgotLoading}
                    onClick={handleRipple}
                  >
                    {forgotLoading ? <>Отправляем<span className="g-spinner" /></> : 'Отправить код'}
                  </button>
                </div>

                <div className="auth-footer">
                  <button type="button" className="auth-footer-link" onClick={() => switchMode('login')}>
                    Вспомнили? <span className="auth-footer-accent">Войти</span>
                  </button>
                </div>
              </motion.form>
            )}

            {/* ═══════ FORGOT-CODE ═══════ */}
            {mode === 'forgot-code' && (
              <motion.div
                key="forgot-code"
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                transition={{ duration: 0.3 }}
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}
              >
                <GravityCard
                  tilt={0}
                  index={0}
                  icon={<span style={{ color: 'var(--accent)' }}><KeyRound size={16} stroke="currentColor" /></span>}
                  title="Проверь почту"
                  subtitle={`Код отправлен на ${forgotEmail}`}
                  error={forgotError}
                  errorKey={forgotErrorKey}
                >
                  {renderCodeGrid(forgotCodeDigits, forgotCodeRefs, handleForgotCodeInput, handleForgotCodeKeyDown)}
                </GravityCard>

                <div className="auth-footer">
                  <button type="button" className="auth-footer-link" onClick={() => {
                    setForgotError('');
                    switchMode('forgot');
                  }}>
                    ← Назад
                  </button>
                </div>
              </motion.div>
            )}

            {/* ═══════ FORGOT-RESET ═══════ */}
            {mode === 'forgot-reset' && (
              <motion.form
                key="forgot-reset"
                onSubmit={handleForgotReset}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -20 }}
                transition={{ duration: 0.3 }}
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}
              >
                {forgotSuccess ? (
                  <GravityCard
                    tilt={0}
                    index={0}
                    icon={<span style={{ color: 'var(--online)' }}><KeyRound size={16} stroke="currentColor" /></span>}
                    title="Готово!"
                    subtitle="Пароль успешно изменён. Перенаправляем..."
                  >
                    <div />
                  </GravityCard>
                ) : (
                  <>
                    <PasswordCard
                      tilt={0}
                      index={0}
                      password={forgotNewPassword}
                      confirmPassword={forgotConfirm}
                      onPasswordChange={setForgotNewPassword}
                      onConfirmChange={setForgotConfirm}
                      error={forgotError}
                      errorKey={forgotErrorKey}
                    />

                    <div className="g-action">
                      <button
                        type="submit"
                        className={`g-btn ${forgotLoading ? 'g-btn--loading' : ''}`}
                        disabled={forgotLoading}
                        onClick={handleRipple}
                      >
                        {forgotLoading ? <>Меняем<span className="g-spinner" /></> : 'Сменить пароль'}
                      </button>
                    </div>

                    <div className="auth-footer">
                      <button type="button" className="auth-footer-link" onClick={() => {
                        setForgotError('');
                        setForgotCodeDigits(['', '', '', '', '', '']);
                        switchMode('forgot-code');
                      }}>
                        ← Назад
                      </button>
                    </div>
                  </>
                )}
              </motion.form>
            )}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}
