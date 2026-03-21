import { useState, useRef, useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { Lock, ShieldCheck, Eye, EyeOff, Check } from 'lucide-react';
import GravityCard from './GravityCard';
import StrengthDots, { getPasswordScore } from './StrengthDots';

export default function PasswordCard({
  tilt = -0.7,
  index = 2,
  dimmed = false,
  password,
  confirmPassword,
  onPasswordChange,
  onConfirmChange,
  error,
  errorKey = 0,
}) {
  const [phase, setPhase] = useState('create'); // 'create' | 'confirm'
  const [showPassword, setShowPassword] = useState(false);
  const confirmRef = useRef(null);
  const score = getPasswordScore(password);

  // Reset phase when password is cleared (e.g. mode switch)
  useEffect(() => {
    if (!password) {
      setPhase('create');
    }
  }, [password]);

  // Auto-transition to confirm phase
  function handleKeyDown(e) {
    if ((e.key === 'Tab' || e.key === 'Enter') && password.length >= 8 && score >= 3) {
      e.preventDefault();
      setPhase('confirm');
    }
  }

  // Focus confirm input when phase changes
  useEffect(() => {
    if (phase === 'confirm') {
      setTimeout(() => confirmRef.current?.focus(), 100);
    }
  }, [phase]);

  // Check match when confirming
  const matched = phase === 'confirm' && confirmPassword && password === confirmPassword;
  const mismatch = phase === 'confirm' && confirmPassword && password !== confirmPassword;

  const isCreate = phase === 'create';

  const icon = isCreate
    ? <Lock size={16} stroke={error ? '#ef4444' : '#c8ff00'} />
    : matched
      ? <Check size={16} stroke="#4ade80" />
      : <ShieldCheck size={16} stroke={mismatch ? '#ef4444' : '#c8ff00'} />;

  const title = isCreate ? 'Придумай пароль' : 'Подтверди пароль';
  const subtitle = isCreate
    ? 'Минимум 8 символов'
    : matched
      ? 'Пароли совпадают'
      : mismatch
        ? undefined
        : 'Повтори ввод';

  const cardError = mismatch ? 'Пароли не совпадают' : error;

  return (
    <GravityCard
      tilt={tilt}
      index={index}
      dimmed={dimmed}
      icon={icon}
      title={title}
      subtitle={subtitle}
      error={cardError}
      errorKey={errorKey}
    >
      <AnimatePresence mode="wait">
        {isCreate ? (
          <motion.div
            key="create"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, x: -20 }}
            transition={{ duration: 0.3 }}
          >
            <div className="g-input-wrap">
              <input
                className="g-input"
                type={showPassword ? 'text' : 'password'}
                placeholder="Пароль"
                value={password}
                onChange={(e) => onPasswordChange(e.target.value)}
                onKeyDown={handleKeyDown}
                aria-label="Пароль"
              />
              <button
                type="button"
                className="g-input-action"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            <StrengthDots password={password} />
          </motion.div>
        ) : (
          <motion.div
            key="confirm"
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
          >
            <div className="g-input-wrap">
              <input
                ref={confirmRef}
                className="g-input"
                type={showPassword ? 'text' : 'password'}
                placeholder="Повтори пароль"
                value={confirmPassword}
                onChange={(e) => onConfirmChange(e.target.value)}
                aria-label="Подтверждение пароля"
              />
              <button
                type="button"
                className="g-input-action"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'}
              >
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </div>
            <button
              type="button"
              onClick={() => { setPhase('create'); onConfirmChange(''); }}
              style={{
                background: 'none', border: 'none', color: 'rgba(200,255,0,0.4)',
                fontSize: '11px', marginTop: '8px', cursor: 'pointer',
                fontFamily: 'Manrope', padding: 0,
              }}
            >
              ← Изменить пароль
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </GravityCard>
  );
}
