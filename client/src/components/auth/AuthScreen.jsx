import { useState, useEffect, useRef } from 'react';
import Glass from '../ui/Glass';
import API_URL from '../../config';
import './AuthScreen.css';

export default function AuthScreen({ onLogin, collapsing }) {
  const [phase, setPhase] = useState('intro'); // 'intro' | 'exiting' | 'form'
  const [tab, setTab] = useState('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [shaking, setShaking] = useState(false);
  const cardRef = useRef(null);
  const indicatorRef = useRef(null);
  const tabsRef = useRef(null);

  // Бренд-интро → exit → форма
  useEffect(() => {
    // Интро показывается 2.5с, потом начинает исчезать
    const exitTimer = setTimeout(() => setPhase('exiting'), 2500);
    // Через 0.8с после начала exit — показываем форму
    const formTimer = setTimeout(() => setPhase('form'), 3300);
    return () => {
      clearTimeout(exitTimer);
      clearTimeout(formTimer);
    };
  }, []);

  // Сила пароля (0-3)
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
    if (tab === 'register' && password !== confirmPassword) {
      setError('Пароли не совпадают');
      triggerShake();
      return false;
    }
    return true;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!validate()) return;

    setLoading(true);
    try {
      const endpoint = tab === 'login' ? '/api/auth/login' : '/api/auth/register';
      const res = await fetch(`${API_URL}${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });

      const data = await res.json();

      if (!res.ok) {
        setError(data.error || 'Ошибка');
        triggerShake();
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

  const switchTab = (newTab) => {
    setTab(newTab);
    setError('');
    setPassword('');
    setConfirmPassword('');
  };

  return (
    <div className={`auth-screen ${collapsing ? 'auth-screen--collapsing' : ''}`}>
      {/* Бренд-интро (только во время intro и exiting) */}
      {phase !== 'form' && (
        <div className={`brand-intro ${phase === 'exiting' ? 'brand-intro--exit' : ''}`}>
          <div className="brand-intro__logo">
            b<span>l</span>
          </div>
          <div className="brand-intro__name">
            ble<span>sk</span>
          </div>
          <div className="brand-intro__tagline">Твой блеск. Твои правила.</div>
        </div>
      )}

      {/* Стеклянная карточка (только когда форма) */}
      {phase === 'form' && (
        <div className="auth-container">
          <Glass
            depth={3}
            radius={28}
            className={`auth-card ${shaking ? 'auth-card--shake' : ''}`}
            ref={cardRef}
          >
            {/* Specular highlight */}
            <div className="auth-card__highlight" />

            {/* Логотип в карточке */}
            <div className="auth-logo">
              <span className="auth-logo__icon">
                b<span>l</span>
              </span>
              <span className="auth-logo__name">
                ble<span>sk</span>
              </span>
            </div>
            <div className="auth-tagline">Твой блеск. Твои правила.</div>

            {/* Табы с liquid индикатором */}
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

              <div className="auth-field auth-field--animated" style={{ animationDelay: '0.1s' }}>
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
                {/* Индикатор силы пароля */}
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
            </form>

            <div className="auth-footer">blesk v0.1.0-alpha</div>
          </Glass>
        </div>
      )}
    </div>
  );
}
