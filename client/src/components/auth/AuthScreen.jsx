import { useState, useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import MetaballBackground from '../ui/MetaballBackground';
import OnboardingIntro from './OnboardingIntro';
import LoginForm from './LoginForm';
import RegisterForm from './RegisterForm';
import VerifyForm from './VerifyForm';
import ForgotPasswordFlow from './ForgotPasswordFlow';
import TwoFactorForm from './TwoFactorForm';
import useAppVersion from '../../hooks/useAppVersion';
import './AuthScreen.css';

export default function AuthScreen({ onLogin, collapsing, pendingVerification, onVerified }) {
  const appVersion = useAppVersion();

  // Onboarding — показываем только при первом запуске
  const [onboardingDone, setOnboardingDone] = useState(
    () => !!localStorage.getItem('blesk_onboarded') || !!pendingVerification
  );

  // Phase: intro → autoLogin → form
  const [phase, setPhase] = useState(pendingVerification ? 'form' : 'intro');

  // Mode: login | register | verify | forgot | forgot-code | forgot-reset | 2fa
  const [mode, setMode] = useState(pendingVerification ? 'verify' : 'login');

  // Общее состояние для передачи между формами
  const [tfaTempToken, setTfaTempToken] = useState('');
  const [verifyData, setVerifyData] = useState({
    email: pendingVerification?.user?.email || '',
    token: pendingVerification?.token || '',
    refreshToken: pendingVerification?.refreshToken || '',
    user: pendingVerification?.user || null,
  });

  // Auto-login через refresh token (safeStorage, не localStorage)
  const [autoLoginTried, setAutoLoginTried] = useState(false);
  useEffect(() => {
    if (pendingVerification || autoLoginTried) return;

    (async () => {
      let refreshToken = null;
      try {
        refreshToken = await window.blesk?.auth?.getRefreshToken?.();
      } catch { /* safeStorage недоступен */ }
      if (!refreshToken) { setAutoLoginTried(true); return; }

      setPhase('autoLogin');
      try {
        const API_URL = (await import('../../config')).default;
        const res = await fetch(`${API_URL}/api/auth/refresh`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'include',
          body: JSON.stringify({ refreshToken }),
        });
        if (res.ok) {
          const data = await res.json();
          if (data.token && data.user) {
            localStorage.setItem('blesk_token', data.token);
            if (data.refreshToken) {
              window.blesk?.auth?.saveRefreshToken?.(data.refreshToken).catch(() => {});
            }
            onLogin?.(data);
            return;
          }
        }
      } catch { /* тихий fallback */ }
      setAutoLoginTried(true);
      setPhase('form');
    })();
  }, [pendingVerification, autoLoginTried, onLogin]);

  // Brand intro → form (пропускаем для повторных визитов)
  useEffect(() => {
    if (pendingVerification) return;
    if (phase === 'autoLogin') return;
    if (localStorage.getItem('blesk-auth-seen')) {
      if (phase === 'intro') setPhase('form');
      return;
    }
    const timer = setTimeout(() => {
      setPhase('form');
      localStorage.setItem('blesk-auth-seen', '1');
    }, 2500);
    return () => clearTimeout(timer);
  }, [pendingVerification, phase]);

  const switchMode = (newMode) => {
    setMode(newMode);
  };

  // Callback: логин запросил 2FA
  const handleTfaRequired = (tempToken) => {
    setTfaTempToken(tempToken);
    setMode('2fa');
  };

  // Callback: логин/регистрация требует верификации email
  const handleVerifyRequired = ({ token, refreshToken, user }) => {
    setVerifyData({ email: user.email, token, refreshToken, user });
    setMode('verify');
  };

  // Auto-login loading
  if (phase === 'autoLogin') {
    return (
      <div className="auth-screen">
        <MetaballBackground subtle />
        <div className="auth-center">
          <div className="auth-autoLogin">
            <img className="auth-autoLogin__logo" src="./blesk.png" alt="blesk" onError={(e) => { e.target.style.display = 'none'; }} />
            <div className="auth-autoLogin__spinner" />
            <div className="auth-autoLogin__text">Вход в аккаунт...</div>
          </div>
        </div>
      </div>
    );
  }

  // Brand intro — только после завершения onboarding
  if (onboardingDone && phase === 'intro') {
    return (
      <div className="auth-screen">
        <motion.div
          className="auth-intro"
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          transition={{ duration: 0.8, ease: 'easeOut' }}
          onClick={() => { setPhase('form'); localStorage.setItem('blesk-auth-seen', '1'); }}
          style={{ cursor: 'pointer' }}
        >
          <img src="./blesk.png" alt="blesk" onError={(e) => { e.target.style.display = 'none'; }} />
          <div className="auth-intro-tagline">Твой блеск. Твои правила.</div>
        </motion.div>
      </div>
    );
  }

  const renderForm = () => {
    switch (mode) {
      case 'login':
        return (
          <LoginForm
            onLogin={onLogin}
            onModeChange={switchMode}
            onTfaRequired={handleTfaRequired}
            onVerifyRequired={handleVerifyRequired}
          />
        );
      case 'register':
        return (
          <RegisterForm
            onModeChange={switchMode}
            onVerifyRequired={handleVerifyRequired}
            onLogin={onLogin}
          />
        );
      case 'verify':
        return (
          <VerifyForm
            email={verifyData.email}
            token={verifyData.token}
            refreshToken={verifyData.refreshToken}
            user={verifyData.user}
            onLogin={onLogin}
            onModeChange={switchMode}
            pendingVerification={pendingVerification}
            onVerified={onVerified}
          />
        );
      case 'forgot':
      case 'forgot-code':
      case 'forgot-reset':
        return (
          <ForgotPasswordFlow
            mode={mode}
            onModeChange={switchMode}
          />
        );
      case '2fa':
        return (
          <TwoFactorForm
            tempToken={tfaTempToken}
            onLogin={onLogin}
            onModeChange={switchMode}
          />
        );
      default:
        return null;
    }
  };

  return (
    <div className={`auth-screen ${collapsing ? 'auth-screen--collapsing' : ''}`}>
      {/* Metaball background behind everything */}
      <MetaballBackground subtle />

      {/* Onboarding intro — только при первом запуске */}
      {!onboardingDone && (
        <OnboardingIntro onComplete={() => setOnboardingDone(true)} />
      )}

      <div className="auth-center">
        <div className="auth-content">
          {/* Logo above cards */}
          <div className="auth-logo">
            <img src="./blesk.png" alt="blesk" onError={(e) => { e.target.style.display = 'none'; }} />
            <div className="auth-tagline">твой блеск. твои правила.</div>
            <div className="auth-version">v{appVersion}</div>
          </div>

          <AnimatePresence mode="wait">
            {renderForm()}
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}
