import { useState, useRef, useCallback } from 'react';
import { motion } from 'framer-motion';
import { Key } from '@phosphor-icons/react';
import GravityCard from './GravityCard';
import API_URL from '../../config';

export default function TwoFactorForm({ tempToken, onLogin, onModeChange }) {
  const [tfaCode, setTfaCode] = useState(['', '', '', '', '', '']);
  const [tfaLoading, setTfaLoading] = useState(false);
  const [tfaError, setTfaError] = useState('');
  const [tfaErrorKey, setTfaErrorKey] = useState(0);
  const tfaCodeRefs = useRef([]);

  const triggerTfaError = (msg) => {
    setTfaError(msg);
    setTfaErrorKey((k) => k + 1);
  };

  const submitTfaCode = useCallback(async (digits) => {
    const code = digits.join('');
    if (code.length !== 6) return;

    setTfaLoading(true);
    setTfaError('');
    try {
      const res = await fetch(`${API_URL}/api/auth/2fa/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ tempToken, code }),
      });

      const data = await res.json();

      if (!res.ok) {
        triggerTfaError(data.error || 'Ошибка');
        setTfaCode(['', '', '', '', '', '']);
        setTimeout(() => tfaCodeRefs.current[0]?.focus(), 100);
        return;
      }

      onLogin(data);
    } catch {
      triggerTfaError('Не удалось подключиться к серверу');
    } finally {
      setTfaLoading(false);
    }
  }, [tempToken, onLogin]);

  const handleTfaCodeInput = (index, value) => {
    if (value.length > 1) {
      const digits = value.replace(/\D/g, '').slice(0, 6).split('');
      const newCode = [...tfaCode];
      digits.forEach((d, i) => {
        if (index + i < 6) newCode[index + i] = d;
      });
      setTfaCode(newCode);
      const nextIdx = Math.min(index + digits.length, 5);
      tfaCodeRefs.current[nextIdx]?.focus();
      if (newCode.every((d) => d !== '')) submitTfaCode(newCode);
      return;
    }

    const digit = value.replace(/\D/g, '');
    const newCode = [...tfaCode];
    newCode[index] = digit;
    setTfaCode(newCode);
    if (digit && index < 5) tfaCodeRefs.current[index + 1]?.focus();
    if (newCode.every((d) => d !== '')) submitTfaCode(newCode);
  };

  const handleTfaCodeKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !tfaCode[index] && index > 0) {
      tfaCodeRefs.current[index - 1]?.focus();
    }
  };

  return (
    <motion.div
      key="2fa"
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
        title="Двухфакторная аутентификация"
        subtitle="Введите код из приложения-аутентификатора"
        error={tfaError}
        errorKey={tfaErrorKey}
      >
        <div className="verify-code-grid">
          {tfaCode.map((digit, i) => (
            <input
              key={i}
              ref={(el) => (tfaCodeRefs.current[i] = el)}
              className={`verify-code-cell ${digit ? 'filled' : ''}`}
              type="text"
              inputMode="numeric"
              maxLength={1}
              value={digit}
              onChange={(e) => handleTfaCodeInput(i, e.target.value)}
              onKeyDown={(e) => handleTfaCodeKeyDown(i, e)}
              onFocus={(e) => e.target.select()}
              autoFocus={i === 0}
              disabled={tfaLoading}
            />
          ))}
        </div>
      </GravityCard>

      <div className="auth-footer">
        <button type="button" className="auth-footer-link" onClick={() => {
          setTfaCode(['', '', '', '', '', '']);
          setTfaError('');
          onModeChange('login');
        }}>
          ← Назад к входу
        </button>
      </div>
    </motion.div>
  );
}
