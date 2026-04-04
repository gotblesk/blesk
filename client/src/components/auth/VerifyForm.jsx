import { useState, useEffect, useRef, useCallback } from 'react';
import { motion } from 'framer-motion';
import { Envelope } from '@phosphor-icons/react';
import GravityCard from './GravityCard';
import API_URL from '../../config';

export default function VerifyForm({
  email,
  token,
  refreshToken,
  user,
  onLogin,
  onModeChange,
  pendingVerification,
  onVerified,
}) {
  const [codeDigits, setCodeDigits] = useState(['', '', '', '', '', '']);
  const [resendTimer, setResendTimer] = useState(60);
  const [verifyLoading, setVerifyLoading] = useState(false);
  const [verifyError, setVerifyError] = useState('');
  const [verifyErrorKey, setVerifyErrorKey] = useState(0);
  const codeRefs = useRef([]);

  const maskedEmail = email
    ? email.replace(/^(.{2})(.*)(@.*)$/, (_, a, b, c) => a + '•'.repeat(b.length) + c)
    : '';

  const triggerVerifyError = (msg) => {
    setVerifyError(msg);
    setVerifyErrorKey((k) => k + 1);
  };

  // Таймер повторной отправки
  useEffect(() => {
    if (resendTimer <= 0) return;
    const t = setTimeout(() => setResendTimer((p) => p - 1), 1000);
    return () => clearTimeout(t);
  }, [resendTimer]);

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
          Authorization: `Bearer ${token}`,
        },
        credentials: 'include',
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
          user: { ...user, emailVerified: true },
          token,
          refreshToken,
        });
      }
    } catch {
      triggerVerifyError('Не удалось подключиться к серверу');
    } finally {
      setVerifyLoading(false);
    }
  }, [token, refreshToken, user, onLogin, pendingVerification, onVerified]);

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
          Authorization: `Bearer ${token}`,
        },
        credentials: 'include',
      });
      setResendTimer(60);
      setVerifyError('');
    } catch {
      triggerVerifyError('Не удалось отправить код');
    }
  };

  return (
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
        icon={<span style={{ color: 'var(--online)', display: 'flex' }}><Envelope size={16} weight="regular" /></span>}
        title="Проверь почту"
        subtitle={`Код отправлен на ${maskedEmail}`}
        error={verifyError}
        errorKey={verifyErrorKey}
      >
        <div className="verify-code-grid">
          {codeDigits.map((digit, i) => (
            <input
              key={i}
              ref={(el) => (codeRefs.current[i] = el)}
              className={`verify-code-cell ${digit ? 'filled' : ''}`}
              type="text"
              inputMode="numeric"
              maxLength={6}
              value={digit}
              onChange={(e) => handleCodeInput(i, e.target.value)}
              onKeyDown={(e) => handleCodeKeyDown(i, e)}
              onFocus={(e) => e.target.select()}
              autoFocus={i === 0}
              disabled={verifyLoading}
            />
          ))}
        </div>
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
          onModeChange('login');
        }}>
          ← Назад к входу
        </button>
      </div>
    </motion.div>
  );
}
