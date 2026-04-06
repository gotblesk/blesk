import { useState, useRef, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { Envelope, Key } from '@phosphor-icons/react';
import GravityCard from './GravityCard';
import PasswordCard from './PasswordCard';
import { getPasswordScore } from './StrengthDots';
import useRipple from '../../hooks/useRipple';
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
  const [resendCooldown, setResendCooldown] = useState(0);
  const resendTimerRef = useRef(null);
  const handleRipple = useRipple();

  // Таймер обратного отсчёта для повторной отправки
  useEffect(() => {
    if (resendCooldown <= 0) return;
    resendTimerRef.current = setTimeout(() => {
      setResendCooldown((c) => c - 1);
    }, 1000);
    return () => clearTimeout(resendTimerRef.current);
  }, [resendCooldown]);

  const handleResendCode = useCallback(async () => {
    if (resendCooldown > 0 || !forgotEmail) return;
    try {
      await fetch(`${API_URL}/api/auth/forgot-password`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email: forgotEmail }),
      });
    } catch { /* ignore */ }
    setResendCooldown(60);
  }, [resendCooldown, forgotEmail]);

  const triggerForgotError = (msg) => {
    setForgotError(msg);
    setForgotErrorKey((k) => k + 1);
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
      setResendCooldown(60);
      onModeChange('forgot-code');
    } catch {
      triggerForgotError('Не удалось подключиться к серверу');
    } finally {
      setForgotLoading(false);
    }
  };

  // Проверка кода на сервере перед переходом к сбросу пароля
  const verifyResetCode = useCallback(async (fullCode) => {
    setForgotLoading(true);
    setForgotError('');
    try {
      const res = await fetch(`${API_URL}/api/auth/verify-reset-code`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email: forgotEmail, code: fullCode }),
      });
      const data = await res.json();
      if (!res.ok) {
        triggerForgotError(data.error || 'Неверный код');
        setForgotCodeDigits(['', '', '', '', '', '']);
        setTimeout(() => forgotCodeRefs.current[0]?.focus(), 50);
        return;
      }
      setForgotCode(fullCode);
      onModeChange('forgot-reset');
    } catch {
      triggerForgotError('Не удалось подключиться к серверу');
    } finally {
      setForgotLoading(false);
    }
  }, [forgotEmail, onModeChange]);

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
        verifyResetCode(newCode.join(''));
      }
      return;
    }
    const digit = value.replace(/\D/g, '');
    const newCode = [...forgotCodeDigits];
    newCode[index] = digit;
    setForgotCodeDigits(newCode);
    if (digit && index < 5) forgotCodeRefs.current[index + 1]?.focus();
    if (newCode.every((d) => d !== '')) {
      verifyResetCode(newCode.join(''));
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
    if (getPasswordScore(forgotNewPassword) < 3) {
      triggerForgotError('Пароль слишком простой');
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
          <div className="verify-code-grid" role="group" aria-label="Код подтверждения">
            {forgotCodeDigits.map((digit, i) => (
              <input
                key={i}
                ref={(el) => (forgotCodeRefs.current[i] = el)}
                className={`verify-code-cell ${digit ? 'filled' : ''}`}
                type="text"
                inputMode="numeric"
                maxLength={1}
                value={digit}
                onChange={(e) => handleForgotCodeInput(i, e.target.value)}
                onKeyDown={(e) => handleForgotCodeKeyDown(i, e)}
                onFocus={(e) => e.target.select()}
                autoFocus={i === 0}
                aria-label={`Цифра ${i + 1} из 6`}
              />
            ))}
          </div>
        </GravityCard>

        <div className="verify-resend">
          {resendCooldown > 0 ? (
            <span className="verify-resend-timer">
              Отправить повторно ({resendCooldown}с)
            </span>
          ) : (
            <button
              type="button"
              className="verify-resend-btn"
              onClick={handleResendCode}
            >
              Отправить повторно
            </button>
          )}
        </div>

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
