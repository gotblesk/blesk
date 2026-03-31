import { useState, useRef } from 'react';
import { LockClosedIcon, EyeIcon, EyeSlashIcon } from '@heroicons/react/24/outline';
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

  // Подсказки о требованиях к паролю (показываем только если пользователь начал вводить)
  const getPasswordHint = () => {
    if (!password) return null;
    if (password.length < 8) return 'Минимум 8 символов';
    if (!/[A-Z]/.test(password)) return 'Добавь заглавную букву';
    if (!/\d/.test(password)) return 'Добавь хотя бы одну цифру';
    return null;
  };
  const passwordHint = getPasswordHint();

  return (
    <GravityCard
      tilt={tilt}
      index={index}
      dimmed={dimmed}
      icon={<span style={{ color: cardError ? 'var(--danger)' : matched ? 'var(--online)' : 'var(--accent)' }}><LockClosedIcon className="w-4 h-4" /></span>}
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
          {showPassword ? <EyeSlashIcon className="w-4 h-4" /> : <EyeIcon className="w-4 h-4" />}
        </button>
      </div>
      <StrengthDots password={password} />
      {/* Inline подсказка о требованиях к паролю */}
      {passwordHint && (
        <div style={{ fontSize: 11, color: 'var(--danger)', marginTop: 4, paddingLeft: 2, opacity: 0.85 }}>
          {passwordHint}
        </div>
      )}

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
