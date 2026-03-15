import { useState, useEffect } from 'react';
import TitleBar from './components/ui/TitleBar';
import AuthScreen from './components/auth/AuthScreen';
import MainScreen from './components/main/MainScreen';

export default function App() {
  const [user, setUser] = useState(null);
  const [transition, setTransition] = useState(null); // 'collapsing' | 'revealing'
  const [pendingUser, setPendingUser] = useState(null);
  const [checking, setChecking] = useState(true);
  const [isMaximized, setIsMaximized] = useState(false);

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

    fetch('http://localhost:3000/api/auth/me', {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((data) => {
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
