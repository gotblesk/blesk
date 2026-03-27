import { useState, useRef } from 'react';
import { Lock, Eye, EyeOff } from 'lucide-react';
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
  const [showPassword, setShowPassword] = useState(false);
  const confirmRef = useRef(null);
  const score = getPasswordScore(password);

  const matched = confirmPassword && password === confirmPassword;
  const mismatch = confirmPassword && password !== confirmPassword;

  const cardError = mismatch ? 'Пароли не совпадают' : error;

  return (
    <GravityCard
      tilt={tilt}
      index={index}
      dimmed={dimmed}
      icon={<span style={{ color: cardError ? 'var(--danger)' : matched ? 'var(--online)' : 'var(--accent)' }}><Lock size={16} stroke="currentColor" /></span>}
      title="Пароль"
      subtitle="Минимум 8 символов"
      error={cardError}
      errorKey={errorKey}
    >
      {/* Поле пароля */}
      <div className="g-input-wrap">
        <input
          className="g-input"
          type={showPassword ? 'text' : 'password'}
          placeholder="Придумай пароль"
          value={password}
          onChange={(e) => onPasswordChange(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); confirmRef.current?.focus(); } }}
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

      {/* Поле подтверждения — всегда видно */}
      <div className="g-input-wrap" style={{ marginTop: '10px' }}>
        <input
          ref={confirmRef}
          className="g-input"
          type={showPassword ? 'text' : 'password'}
          placeholder="Повтори пароль"
          value={confirmPassword}
          onChange={(e) => onConfirmChange(e.target.value)}
          aria-label="Подтверждение пароля"
          style={matched ? { borderColor: 'color-mix(in srgb, var(--online) 30%, transparent)' } : mismatch ? { borderColor: 'color-mix(in srgb, var(--danger) 30%, transparent)' } : {}}
        />
      </div>
    </GravityCard>
  );
}
