import { useState, useEffect } from 'react';
import TitleBar from './components/ui/TitleBar';
import AuthScreen from './components/auth/AuthScreen';
import MainScreen from './components/main/MainScreen';
import UpdateToast from './components/ui/UpdateToast';
import API_URL from './config';

export default function App() {
  const [user, setUser] = useState(null);
  const [transition, setTransition] = useState(null); // 'collapsing' | 'revealing'
  const [pendingUser, setPendingUser] = useState(null);
  const [checking, setChecking] = useState(true);
  const [isMaximized, setIsMaximized] = useState(false);
  const [needsVerify, setNeedsVerify] = useState(null); // { user, token, refreshToken }

  // Слушаем maximize/unmaximize от Electron
  useEffect(() => {
    window.blesk?.window.onMaximizeChange?.((maximized) => {
      setIsMaximized(maximized);
    });
  }, []);

  // Попытка обновить токен через refresh token
  async function tryRefreshToken() {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) return null;

    try {
      const res = await fetch(`${API_URL}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (res.ok) {
        const data = await res.json();
        localStorage.setItem('token', data.token);
        if (data.refreshToken) localStorage.setItem('refreshToken', data.refreshToken);
        return data.token;
      }
    } catch {}
    return null;
  }

  // Авто-логин: проверяем сохранённый токен, при неудаче пробуем refresh
  useEffect(() => {
    async function checkAuth() {
      let token = localStorage.getItem('token');
      if (!token) {
        // Попробуем refresh даже без access token
        token = await tryRefreshToken();
        if (!token) { setChecking(false); return; }
      }

      let res = await fetch(`${API_URL}/api/auth/me`, {
        headers: { Authorization: `Bearer ${token}` },
      }).catch(() => null);

      // Если токен истёк — пробуем обновить
      if (!res || !res.ok) {
        const newToken = await tryRefreshToken();
        if (newToken) {
          res = await fetch(`${API_URL}/api/auth/me`, {
            headers: { Authorization: `Bearer ${newToken}` },
          }).catch(() => null);
        }
      }

      if (res && res.ok) {
        const data = await res.json();
        if (data.user.email && data.user.emailVerified === false) {
          setNeedsVerify({
            user: data.user,
            token: localStorage.getItem('token'),
            refreshToken: localStorage.getItem('refreshToken'),
          });
        } else {
          setUser(data.user);
        }
      } else {
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
      }
      setChecking(false);
    }

    checkAuth();
  }, []);

  // Автоматическое обновление токена каждые 12 минут (JWT живёт 15 мин)
  useEffect(() => {
    if (!user) return;
    const interval = setInterval(async () => {
      await tryRefreshToken();
    }, 12 * 60 * 1000);
    return () => clearInterval(interval);
  }, [user]);

  const handleLogin = (data) => {
    localStorage.setItem('token', data.token);
    if (data.refreshToken) {
      localStorage.setItem('refreshToken', data.refreshToken);
    }

    // Запускаем transition: auth collapse → main reveal
    setPendingUser(data.user);
    setTransition('collapsing');

    // После collapse анимации — показываем main
    setTimeout(() => {
      setUser(data.user);
      setPendingUser(null);
      setTransition('revealing');

      // Убираем класс reveal после анимации
      setTimeout(() => setTransition(null), 800);
    }, 700);
  };

  const handleLogout = () => {
    setUser(null);
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
  };

  // Пока проверяем токен — ничего не показываем (прелоадер уже скрылся)
  if (checking) {
    return (
      <div className={`app${isMaximized ? ' app--maximized' : ''}`}>
        <TitleBar />
        <UpdateToast />
      </div>
    );
  }

  if (!user) {
    return (
      <div className={`app${isMaximized ? ' app--maximized' : ''}`}>
        <TitleBar />
        <AuthScreen
          onLogin={handleLogin}
          collapsing={transition === 'collapsing'}
          pendingVerification={needsVerify}
          onVerified={() => {
            if (needsVerify) {
              setUser({ ...needsVerify.user, emailVerified: true });
              setNeedsVerify(null);
            }
          }}
        />
        <UpdateToast />
      </div>
    );
  }

  return (
    <div className={`app${isMaximized ? ' app--maximized' : ''}`}>
      <TitleBar />
      <div className={transition === 'revealing' ? 'main-reveal' : ''} style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
        <MainScreen user={user} onLogout={handleLogout} />
      </div>
      <UpdateToast />
    </div>
  );
}
