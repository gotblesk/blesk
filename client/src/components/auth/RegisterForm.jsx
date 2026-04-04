import { useState } from 'react';
import { motion } from 'framer-motion';
import { User, Envelope } from '@phosphor-icons/react';
import gsap from 'gsap';
import GravityCard from './GravityCard';
import PasswordCard from './PasswordCard';
import { getPasswordScore } from './StrengthDots';
import API_URL from '../../config';

export default function RegisterForm({ onModeChange, onVerifyRequired }) {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [errorKey, setErrorKey] = useState(0);
  const [loading, setLoading] = useState(false);

  const triggerError = (msg) => {
    setError(msg);
    setErrorKey((k) => k + 1);
  };

  const validate = () => {
    if (username.length < 3) {
      triggerError('Имя пользователя — минимум 3 символа');
      return false;
    }
    if (password.length < 8) {
      triggerError('Пароль — минимум 8 символов');
      return false;
    }
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
    return true;
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

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!validate()) return;

    setLoading(true);
    try {
      const res = await fetch(`${API_URL}/api/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ username, password, email }),
      });

      const data = await res.json();

      if (!res.ok) {
        triggerError(data.error || 'Ошибка');
        return;
      }

      // Email not verified → переходим на верификацию
      if (data.user.email && data.user.emailVerified === false) {
        onVerifyRequired({
          token: data.token || data.accessToken,
          refreshToken: data.refreshToken || '',
          user: data.user,
        });
        return;
      }
    } catch {
      triggerError('Не удалось подключиться к серверу');
    } finally {
      setLoading(false);
    }
  };

  return (
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
        icon={<span style={{ color: 'var(--accent)', display: 'flex' }}><User size={16} weight="regular" /></span>}
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
        icon={<span style={{ color: 'var(--accent)', display: 'flex' }}><Envelope size={16} weight="regular" /></span>}
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
        <button type="button" className="auth-footer-link" onClick={() => onModeChange('login')}>
          Уже есть аккаунт? <span className="auth-footer-accent">Войти</span>
        </button>
      </div>
    </motion.form>
  );
}
