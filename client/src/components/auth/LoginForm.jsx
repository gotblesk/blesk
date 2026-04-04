import { useState } from 'react';
import { motion } from 'framer-motion';
import { User, Lock, Eye, EyeSlash } from '@phosphor-icons/react';
import gsap from 'gsap';
import GravityCard from './GravityCard';
import API_URL from '../../config';

export default function LoginForm({ onLogin, onModeChange, onTfaRequired, onVerifyRequired }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showLoginPw, setShowLoginPw] = useState(false);
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
      const res = await fetch(`${API_URL}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ username, password }),
      });

      const data = await res.json();

      if (!res.ok) {
        triggerError(data.error || 'Ошибка');
        return;
      }

      // 2FA required
      if (data.requires2FA) {
        onTfaRequired(data.tempToken);
        return;
      }

      // Email not verified
      if (data.user.email && data.user.emailVerified === false) {
        onVerifyRequired({
          token: data.token || data.accessToken,
          refreshToken: data.refreshToken || '',
          user: data.user,
        });
        return;
      }

      onLogin(data);
    } catch {
      triggerError('Не удалось подключиться к серверу');
    } finally {
      setLoading(false);
    }
  };

  const handleForgotClick = () => {
    onModeChange('forgot');
  };

  return (
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
        icon={<span style={{ color: 'var(--accent)', display: 'flex' }}><User size={16} weight="regular" /></span>}
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
        icon={<span style={{ color: 'var(--accent)', display: 'flex' }}><Lock size={16} weight="regular" /></span>}
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
            {showLoginPw ? <EyeSlash size={16} weight="regular" /> : <Eye size={16} weight="regular" />}
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
        <button type="button" className="auth-footer-link" onClick={handleForgotClick}>
          Забыли пароль?
        </button>
        <div className="auth-footer-sep" />
        <button type="button" className="auth-footer-link" onClick={() => onModeChange('register')}>
          Создать аккаунт
        </button>
      </div>
    </motion.form>
  );
}
