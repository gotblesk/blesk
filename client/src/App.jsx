import { useState, useEffect } from 'react';
import TitleBar from './components/ui/TitleBar';
import AuthScreen from './components/auth/AuthScreen';
import MainScreen from './components/main/MainScreen';
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

  // Авто-логин: проверяем сохранённый токен
  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) {
      setChecking(false);
      return;
    }

    fetch(`${API_URL}/api/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((data) => {
        // Если email есть, но не подтверждён — показать верификацию
        if (data.user.email && data.user.emailVerified === false) {
          setNeedsVerify({
            user: data.user,
            token,
            refreshToken: localStorage.getItem('refreshToken'),
          });
          setChecking(false);
          return;
        }
        setUser(data.user);
        setChecking(false);
      })
      .catch(() => {
        localStorage.removeItem('token');
        localStorage.removeItem('refreshToken');
        setChecking(false);
      });
  }, []);

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
      </div>
    );
  }

  return (
    <div className={`app${isMaximized ? ' app--maximized' : ''}`}>
      <TitleBar />
      <div className={transition === 'revealing' ? 'main-reveal' : ''} style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
        <MainScreen user={user} onLogout={handleLogout} />
      </div>
    </div>
  );
}
