import { useState, useRef } from 'react';
import { motion } from 'framer-motion';
import { Envelope, Key } from '@phosphor-icons/react';
import gsap from 'gsap';
import GravityCard from './GravityCard';
import PasswordCard from './PasswordCard';
import API_URL from '../../config';

export default function ForgotPasswordFlow({ mode, onModeChange }) {
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

  const triggerForgotError = (msg) => {
    setForgotError(msg);
    setForgotErrorKey((k) => k + 1);
  };

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

  // ═══ ШАГ 1: отправка email ═══
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
        credentials: 'include',
        body: JSON.stringify({ email: forgotEmail }),
      });
      const data = await res.json();
      if (!res.ok) {
        triggerForgotError(data.error || 'Ошибка');
        return;
      }
      setForgotError('');
      setForgotCodeDigits(['', '', '', '', '', '']);
      onModeChange('forgot-code');
    } catch {
      triggerForgotError('Не удалось подключиться к серверу');
    } finally {
      setForgotLoading(false);
    }
  };

  // ═══ ШАГ 2: ввод кода ═══
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
        onModeChange('forgot-reset');
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
      onModeChange('forgot-reset');
    }
  };

  const handleForgotCodeKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !forgotCodeDigits[index] && index > 0) {
      forgotCodeRefs.current[index - 1]?.focus();
    }
  };

  // ═══ ШАГ 3: сброс пароля ═══
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
        credentials: 'include',
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
        onModeChange('login');
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

  // ═══ ШАГ 1: forgot ═══
  if (mode === 'forgot') {
    return (
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
          icon={<span style={{ color: 'var(--info)', display: 'flex' }}><Envelope size={16} weight="regular" /></span>}
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
          <button type="button" className="auth-footer-link" onClick={() => onModeChange('login')}>
            Вспомнили? <span className="auth-footer-accent">Войти</span>
          </button>
        </div>
      </motion.form>
    );
  }

  // ═══ ШАГ 2: forgot-code ═══
  if (mode === 'forgot-code') {
    return (
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
          icon={<span style={{ color: 'var(--accent)', display: 'flex' }}><Key size={16} weight="regular" /></span>}
          title="Проверь почту"
          subtitle={`Код отправлен на ${forgotEmail}`}
          error={forgotError}
          errorKey={forgotErrorKey}
        >
          <div className="verify-code-grid">
            {forgotCodeDigits.map((digit, i) => (
              <input
                key={i}
                ref={(el) => (forgotCodeRefs.current[i] = el)}
                className={`verify-code-cell ${digit ? 'filled' : ''}`}
                type="text"
                inputMode="numeric"
                maxLength={6}
                value={digit}
                onChange={(e) => handleForgotCodeInput(i, e.target.value)}
                onKeyDown={(e) => handleForgotCodeKeyDown(i, e)}
                onFocus={(e) => e.target.select()}
                autoFocus={i === 0}
              />
            ))}
          </div>
        </GravityCard>

        <div className="auth-footer">
          <button type="button" className="auth-footer-link" onClick={() => {
            setForgotError('');
            onModeChange('forgot');
          }}>
            ← Назад
          </button>
        </div>
      </motion.div>
    );
  }

  // ═══ ШАГ 3: forgot-reset ═══
  if (mode === 'forgot-reset') {
    return (
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
            icon={<span style={{ color: 'var(--online)', display: 'flex' }}><Key size={16} weight="regular" /></span>}
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
                onModeChange('forgot-code');
              }}>
                ← Назад
              </button>
            </div>
          </>
        )}
      </motion.form>
    );
  }

  return null;
}
