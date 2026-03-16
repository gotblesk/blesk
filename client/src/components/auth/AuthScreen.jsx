import { useState, useEffect, useRef, useCallback } from 'react';
import Glass from '../ui/Glass';
import useAppVersion from '../../hooks/useAppVersion';
import API_URL from '../../config';
import './AuthScreen.css';

export default function AuthScreen({ onLogin, collapsing, pendingVerification, onVerified }) {
  const appVersion = useAppVersion();
  const [phase, setPhase] = useState(pendingVerification ? 'verify' : 'intro');
  const [tab, setTab] = useState('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [shaking, setShaking] = useState(false);
  const cardRef = useRef(null);
  const indicatorRef = useRef(null);
  const tabsRef = useRef(null);

  // Для экрана восстановления пароля
  const [forgotEmail, setForgotEmail] = useState('');
  const [forgotStep, setForgotStep] = useState('email'); // 'email' | 'code' | 'newpass'
  const [forgotCode, setForgotCode] = useState('');
  const [forgotNewPassword, setForgotNewPassword] = useState('');
  const [forgotConfirm, setForgotConfirm] = useState('');
  const [forgotError, setForgotError] = useState('');
  const [forgotLoading, setForgotLoading] = useState(false);
  const [forgotSuccess, setForgotSuccess] = useState(false);
  const [forgotCodeDigits, setForgotCodeDigits] = useState(['', '', '', '', '', '']);
  const forgotCodeRefs = useRef([]);

  // Для экрана верификации
  const [verifyEmail, setVerifyEmail] = useState(pendingVerification?.user?.email || '');
  const [verifyToken, setVerifyToken] = useState(pendingVerification?.token || '');
  const [verifyRefresh, setVerifyRefresh] = useState(pendingVerification?.refreshToken || '');
  const [verifyUser, setVerifyUser] = useState(pendingVerification?.user || null);
  const [codeDigits, setCodeDigits] = useState(['', '', '', '', '', '']);
  const [resendTimer, setResendTimer] = useState(60);
  const [verifyLoading, setVerifyLoading] = useState(false);
  const [verifyError, setVerifyError] = useState('');
  const codeRefs = useRef([]);

  // Бренд-интро → exit → форма (пропускаем если уже на verify)
  useEffect(() => {
    if (pendingVerification) return;
    const exitTimer = setTimeout(() => setPhase('exiting'), 2500);
    const formTimer = setTimeout(() => setPhase('form'), 3300);
    return () => {
      clearTimeout(exitTimer);
      clearTimeout(formTimer);
    };
  }, [pendingVerification]);

  // Таймер повторной отправки
  useEffect(() => {
    if (phase !== 'verify' || resendTimer <= 0) return;
    const t = setTimeout(() => setResendTimer((p) => p - 1), 1000);
    return () => clearTimeout(t);
  }, [phase, resendTimer]);

  // Сила пароля
  const getPasswordStrength = (pass) => {
    if (!pass) return 0;
    let score = 0;
    if (pass.length >= 8) score++;
    if (/[A-Z]/.test(pass) && /[a-z]/.test(pass)) score++;
    if (/\d/.test(pass) && /[^a-zA-Z0-9]/.test(pass)) score++;
    return score;
  };

  const strengthLevel = getPasswordStrength(password);
  const strengthColors = ['var(--danger)', '#febc2e', 'var(--accent)'];
  const strengthLabels = ['Слабый', 'Средний', 'Надёжный'];

  // Позиция индикатора табов
  useEffect(() => {
    if (!tabsRef.current || !indicatorRef.current) return;
    const tabs = tabsRef.current.querySelectorAll('.auth-tab');
    const activeIdx = tab === 'login' ? 0 : 1;
    const activeTab = tabs[activeIdx];
    if (activeTab) {
      indicatorRef.current.style.left = `${activeTab.offsetLeft}px`;
      indicatorRef.current.style.width = `${activeTab.offsetWidth}px`;
    }
  }, [tab, phase]);

  const triggerShake = () => {
    setShaking(true);
    setTimeout(() => setShaking(false), 500);
  };

  const validate = () => {
    if (username.length < 3) {
      setError('Имя пользователя — минимум 3 символа');
      triggerShake();
      return false;
    }
    if (password.length < 8) {
      setError('Пароль — минимум 8 символов');
      triggerShake();
      return false;
    }
    if (tab === 'register') {
      if (!email) {
        setError('Email обязателен');
        triggerShake();
        return false;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        setError('Некорректный email');
        triggerShake();
        return false;
      }
      if (password !== confirmPassword) {
        setError('Пароли не совпадают');
        triggerShake();
        return false;
      }
    }
    return true;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!validate()) return;

    setLoading(true);
    try {
      const isRegister = tab === 'register';
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
        setError(data.error || 'Ошибка');
        triggerShake();
        return;
      }

      // Если email не подтверждён — экран верификации
      if (data.user.email && data.user.emailVerified === false) {
        setVerifyEmail(data.user.email);
        setVerifyToken(data.token);
        setVerifyRefresh(data.refreshToken);
        setVerifyUser(data.user);
        setResendTimer(60);
        setCodeDigits(['', '', '', '', '', '']);
        setVerifyError('');
        setPhase('verify');
        return;
      }

      onLogin(data);
    } catch {
      setError('Не удалось подключиться к серверу');
      triggerShake();
    } finally {
      setLoading(false);
    }
  };

  // Верификация кода
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
        setVerifyError(data.error || 'Ошибка');
        setShaking(true);
        setTimeout(() => setShaking(false), 500);
        setCodeDigits(['', '', '', '', '', '']);
        setTimeout(() => codeRefs.current[0]?.focus(), 100);
        return;
      }

      // Верификация прошла — входим
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
      setVerifyError('Не удалось подключиться к серверу');
    } finally {
      setVerifyLoading(false);
    }
  }, [verifyToken, verifyRefresh, verifyUser, onLogin]);

  // Обработка ввода цифр кода
  const handleCodeInput = (index, value) => {
    // Если вставляют 6 цифр сразу (paste)
    if (value.length > 1) {
      const digits = value.replace(/\D/g, '').slice(0, 6).split('');
      const newCode = [...codeDigits];
      digits.forEach((d, i) => {
        if (index + i < 6) newCode[index + i] = d;
      });
      setCodeDigits(newCode);
      const nextIdx = Math.min(index + digits.length, 5);
      codeRefs.current[nextIdx]?.focus();
      if (newCode.every((d) => d !== '')) {
        submitCode(newCode);
      }
      return;
    }

    const digit = value.replace(/\D/g, '');
    const newCode = [...codeDigits];
    newCode[index] = digit;
    setCodeDigits(newCode);

    if (digit && index < 5) {
      codeRefs.current[index + 1]?.focus();
    }

    if (newCode.every((d) => d !== '')) {
      submitCode(newCode);
    }
  };

  const handleCodeKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !codeDigits[index] && index > 0) {
      codeRefs.current[index - 1]?.focus();
    }
  };

  // Повторная отправка
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
      setVerifyError('Не удалось отправить код');
    }
  };

  // Восстановление пароля — отправка кода
  const handleForgotSend = async () => {
    if (!forgotEmail) {
      setForgotError('Введите email');
      triggerShake();
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(forgotEmail)) {
      setForgotError('Некорректный email');
      triggerShake();
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
        setForgotError(data.error || 'Ошибка');
        triggerShake();
        return;
      }
      setForgotStep('code');
      setForgotCodeDigits(['', '', '', '', '', '']);
    } catch {
      setForgotError('Не удалось подключиться к серверу');
      triggerShake();
    } finally {
      setForgotLoading(false);
    }
  };

  // Восстановление пароля — ввод кода (просто сохраняем и переходим к newpass)
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
        setForgotStep('newpass');
      }
      return;
    }
    const digit = value.replace(/\D/g, '');
    const newCode = [...forgotCodeDigits];
    newCode[index] = digit;
    setForgotCodeDigits(newCode);
    if (digit && index < 5) {
      forgotCodeRefs.current[index + 1]?.focus();
    }
    if (newCode.every((d) => d !== '')) {
      setForgotCode(newCode.join(''));
      setForgotStep('newpass');
    }
  };

  const handleForgotCodeKeyDown = (index, e) => {
    if (e.key === 'Backspace' && !forgotCodeDigits[index] && index > 0) {
      forgotCodeRefs.current[index - 1]?.focus();
    }
  };

  // Восстановление пароля — сброс
  const handleForgotReset = async (e) => {
    e.preventDefault();
    if (forgotNewPassword.length < 8) {
      setForgotError('Пароль — минимум 8 символов');
      triggerShake();
      return;
    }
    if (forgotNewPassword !== forgotConfirm) {
      setForgotError('Пароли не совпадают');
      triggerShake();
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
        setForgotError(data.error || 'Ошибка');
        triggerShake();
        return;
      }
      setForgotSuccess(true);
      setTimeout(() => {
        setPhase('form');
        setTab('login');
        setForgotEmail('');
        setForgotStep('email');
        setForgotCode('');
        setForgotNewPassword('');
        setForgotConfirm('');
        setForgotError('');
        setForgotSuccess(false);
        setForgotCodeDigits(['', '', '', '', '', '']);
      }, 2000);
    } catch {
      setForgotError('Не удалось подключиться к серверу');
      triggerShake();
    } finally {
      setForgotLoading(false);
    }
  };

  const switchTab = (newTab) => {
    setTab(newTab);
    setError('');
    setPassword('');
    setConfirmPassword('');
    setEmail('');
  };

  // Маскировка email
  const maskedEmail = verifyEmail
    ? verifyEmail.replace(/^(.{2})(.*)(@.*)$/, (_, a, b, c) => a + '•'.repeat(b.length) + c)
    : '';

  return (
    <div className={`auth-screen ${collapsing ? 'auth-screen--collapsing' : ''}`}>
      {/* Бренд-интро */}
      {phase !== 'form' && phase !== 'verify' && phase !== 'forgot' && (
        <div className={`brand-intro ${phase === 'exiting' ? 'brand-intro--exit' : ''}`}>
          <img className="brand-intro__logo-img" src="/blesk.png" alt="blesk" />
          <div className="brand-intro__tagline">Твой блеск. Твои правила.</div>
        </div>
      )}

      {/* Форма входа/регистрации */}
      {phase === 'form' && (
        <div className="auth-container">
          <Glass
            depth={3}
            radius={28}
            className={`auth-card ${shaking ? 'auth-card--shake' : ''}`}
            ref={cardRef}
          >
            <div className="auth-card__highlight" />

            <div className="auth-logo">
              <img className="auth-logo__img" src="/blesk.png" alt="blesk" />
            </div>
            <div className="auth-tagline">Твой блеск. Твои правила.</div>

            <div className="auth-tabs" ref={tabsRef}>
              <div className="auth-tab-indicator" ref={indicatorRef} />
              <button
                className={`auth-tab ${tab === 'login' ? 'auth-tab--active' : ''}`}
                onClick={() => switchTab('login')}
              >
                Вход
              </button>
              <button
                className={`auth-tab ${tab === 'register' ? 'auth-tab--active' : ''}`}
                onClick={() => switchTab('register')}
              >
                Регистрация
              </button>
            </div>

            <form className="auth-form" onSubmit={handleSubmit}>
              <div className="auth-field auth-field--animated">
                <label className="auth-label">Имя пользователя</label>
                <div className="auth-input-wrap">
                  <input
                    className="auth-input"
                    type="text"
                    placeholder="username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    autoComplete="off"
                    spellCheck="false"
                    autoFocus
                  />
                </div>
              </div>

              {tab === 'register' && (
                <div className="auth-field auth-field--animated" style={{ animationDelay: '0.05s' }}>
                  <label className="auth-label">Email</label>
                  <div className="auth-input-wrap">
                    <input
                      className="auth-input"
                      type="email"
                      placeholder="you@example.com"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      autoComplete="off"
                    />
                  </div>
                </div>
              )}

              <div className="auth-field auth-field--animated" style={{ animationDelay: tab === 'register' ? '0.1s' : '0.1s' }}>
                <label className="auth-label">Пароль</label>
                <div className="auth-input-wrap">
                  <input
                    className="auth-input"
                    type="password"
                    placeholder={tab === 'register' ? 'Минимум 8 символов' : '••••••••'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                </div>
                {tab === 'register' && password.length > 0 && (
                  <div className="auth-strength">
                    <div className="auth-strength__track">
                      <div
                        className="auth-strength__fill"
                        style={{
                          width: `${(strengthLevel / 3) * 100}%`,
                          background: strengthColors[strengthLevel - 1] || 'var(--danger)',
                        }}
                      />
                    </div>
                    <span
                      className="auth-strength__label"
                      style={{ color: strengthColors[strengthLevel - 1] || 'var(--danger)' }}
                    >
                      {strengthLabels[strengthLevel - 1] || 'Слабый'}
                    </span>
                  </div>
                )}
              </div>

              {tab === 'register' && (
                <div className="auth-field auth-field--animated" style={{ animationDelay: '0.2s' }}>
                  <label className="auth-label">Повторите пароль</label>
                  <div className="auth-input-wrap">
                    <input
                      className="auth-input"
                      type="password"
                      placeholder="••••••••"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                    />
                  </div>
                </div>
              )}

              {error && (
                <div className="auth-error">
                  <span className="auth-error__icon">!</span>
                  {error}
                </div>
              )}

              <button
                className="auth-btn"
                type="submit"
                disabled={loading}
              >
                {loading ? '...' : tab === 'login' ? 'Войти' : 'Создать аккаунт'}
              </button>

              {tab === 'login' && (
                <div
                  className="auth-forgot-link"
                  onClick={() => {
                    setForgotEmail('');
                    setForgotStep('email');
                    setForgotCode('');
                    setForgotNewPassword('');
                    setForgotConfirm('');
                    setForgotError('');
                    setForgotSuccess(false);
                    setForgotCodeDigits(['', '', '', '', '', '']);
                    setPhase('forgot');
                  }}
                >
                  Забыли пароль?
                </div>
              )}
            </form>

            <div className="auth-footer">blesk v{appVersion}</div>
          </Glass>
        </div>
      )}

      {/* Экран верификации email */}
      {phase === 'verify' && (
        <div className="auth-container">
          <Glass
            depth={3}
            radius={28}
            className={`auth-card auth-card--verify ${shaking ? 'auth-card--shake' : ''}`}
          >
            <div className="auth-card__highlight" />

            <div className="auth-logo">
              <img className="auth-logo__img" src="/blesk.png" alt="blesk" />
            </div>

            <div className="auth-verify">
              <div className="auth-verify__icon">✉</div>
              <div className="auth-verify__title">Подтвердите email</div>
              <div className="auth-verify__subtitle">
                Код отправлен на <span className="auth-verify__email">{maskedEmail}</span>
              </div>

              <div className="auth-code-inputs">
                {codeDigits.map((digit, i) => (
                  <input
                    key={i}
                    ref={(el) => (codeRefs.current[i] = el)}
                    className="auth-code-input"
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

              {verifyError && (
                <div className="auth-error" style={{ marginTop: 12 }}>
                  <span className="auth-error__icon">!</span>
                  {verifyError}
                </div>
              )}

              {verifyLoading && (
                <div className="auth-verify__loading">Проверяем...</div>
              )}

              <button
                className={`auth-verify__resend ${resendTimer > 0 ? 'auth-verify__resend--disabled' : ''}`}
                onClick={handleResend}
                disabled={resendTimer > 0}
              >
                {resendTimer > 0
                  ? `Отправить повторно (${resendTimer}с)`
                  : 'Отправить код повторно'}
              </button>

              <button
                className="auth-verify__back"
                onClick={() => {
                  setPhase('form');
                  setCodeDigits(['', '', '', '', '', '']);
                  setVerifyError('');
                  setResendTimer(60);
                }}
              >
                ← Назад к входу
              </button>
            </div>

            <div className="auth-footer">blesk v{appVersion}</div>
          </Glass>
        </div>
      )}

      {/* Экран восстановления пароля */}
      {phase === 'forgot' && (
        <div className="auth-container">
          <Glass
            depth={3}
            radius={28}
            className={`auth-card auth-card--verify ${shaking ? 'auth-card--shake' : ''}`}
          >
            <div className="auth-card__highlight" />

            <div className="auth-logo">
              <img className="auth-logo__img" src="/blesk.png" alt="blesk" />
            </div>

            <div className="auth-verify">
              <div className="auth-verify__icon">🔑</div>
              <div className="auth-verify__title">Восстановление пароля</div>

              {forgotSuccess && (
                <div className="auth-forgot__success">
                  Пароль успешно изменён! Перенаправляем...
                </div>
              )}

              {!forgotSuccess && forgotStep === 'email' && (
                <>
                  <div className="auth-verify__subtitle">
                    Введите email, привязанный к аккаунту
                  </div>

                  <form
                    className="auth-form"
                    onSubmit={(e) => {
                      e.preventDefault();
                      handleForgotSend();
                    }}
                    style={{ width: '100%' }}
                  >
                    <div className="auth-field">
                      <label className="auth-label">Email</label>
                      <div className="auth-input-wrap">
                        <input
                          className="auth-input"
                          type="email"
                          placeholder="you@example.com"
                          value={forgotEmail}
                          onChange={(e) => setForgotEmail(e.target.value)}
                          autoComplete="off"
                          autoFocus
                        />
                      </div>
                    </div>

                    {forgotError && (
                      <div className="auth-error">
                        <span className="auth-error__icon">!</span>
                        {forgotError}
                      </div>
                    )}

                    <button
                      className="auth-btn"
                      type="submit"
                      disabled={forgotLoading}
                    >
                      {forgotLoading ? '...' : 'Отправить код'}
                    </button>
                  </form>
                </>
              )}

              {!forgotSuccess && forgotStep === 'code' && (
                <>
                  <div className="auth-verify__subtitle">
                    Код отправлен на <span className="auth-verify__email">{forgotEmail}</span>
                  </div>

                  <div className="auth-code-inputs">
                    {forgotCodeDigits.map((digit, i) => (
                      <input
                        key={i}
                        ref={(el) => (forgotCodeRefs.current[i] = el)}
                        className="auth-code-input"
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

                  {forgotError && (
                    <div className="auth-error" style={{ marginTop: 12 }}>
                      <span className="auth-error__icon">!</span>
                      {forgotError}
                    </div>
                  )}
                </>
              )}

              {!forgotSuccess && forgotStep === 'newpass' && (
                <>
                  <div className="auth-verify__subtitle">
                    Придумайте новый пароль
                  </div>

                  <form
                    className="auth-form"
                    onSubmit={handleForgotReset}
                    style={{ width: '100%' }}
                  >
                    <div className="auth-field">
                      <label className="auth-label">Новый пароль</label>
                      <div className="auth-input-wrap">
                        <input
                          className="auth-input"
                          type="password"
                          placeholder="Минимум 8 символов"
                          value={forgotNewPassword}
                          onChange={(e) => setForgotNewPassword(e.target.value)}
                          autoFocus
                        />
                      </div>
                    </div>

                    <div className="auth-field">
                      <label className="auth-label">Повторите пароль</label>
                      <div className="auth-input-wrap">
                        <input
                          className="auth-input"
                          type="password"
                          placeholder="••••••••"
                          value={forgotConfirm}
                          onChange={(e) => setForgotConfirm(e.target.value)}
                        />
                      </div>
                    </div>

                    {forgotError && (
                      <div className="auth-error">
                        <span className="auth-error__icon">!</span>
                        {forgotError}
                      </div>
                    )}

                    <button
                      className="auth-btn"
                      type="submit"
                      disabled={forgotLoading}
                    >
                      {forgotLoading ? '...' : 'Сменить пароль'}
                    </button>
                  </form>
                </>
              )}

              {!forgotSuccess && (
                <button
                  className="auth-verify__back"
                  onClick={() => {
                    if (forgotStep === 'code') {
                      setForgotStep('email');
                      setForgotError('');
                    } else if (forgotStep === 'newpass') {
                      setForgotStep('code');
                      setForgotCodeDigits(['', '', '', '', '', '']);
                      setForgotError('');
                    } else {
                      setPhase('form');
                    }
                  }}
                >
                  ← Назад
                </button>
              )}
            </div>

            <div className="auth-footer">blesk v{appVersion}</div>
          </Glass>
        </div>
      )}
    </div>
  );
}
