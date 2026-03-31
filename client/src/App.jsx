import React, { useState, useEffect, Suspense } from 'react';
import TitleBar from './components/ui/TitleBar';
import AuthScreen from './components/auth/AuthScreen';
import MainScreen from './components/main/MainScreen';
import UpdateToast from './components/ui/UpdateToast';
import ErrorBoundary from './components/ui/ErrorBoundary';
import { ensureKeyPair, clearCache } from './utils/cryptoService';
import { initCsrf, clearCsrf } from './utils/authFetch';
import { useSettingsStore } from './store/settingsStore';
import { useChatStore } from './store/chatStore';
import { useNotificationStore } from './store/notificationStore';
import { useVoiceStore } from './store/voiceStore';
import { useCallStore } from './store/callStore';
import { useChannelStore } from './store/channelStore';
import API_URL from './config';

const MetaballFilter = React.lazy(() => import('./components/ui/MetaballFilter'));

export default function App() {
  const [user, setUser] = useState(null);
  const [transition, setTransition] = useState(null); // 'collapsing' | 'revealing'
  const [pendingUser, setPendingUser] = useState(null);
  const [checking, setChecking] = useState(true);
  const [isMaximized, setIsMaximized] = useState(false);
  const [needsVerify, setNeedsVerify] = useState(null); // { user, token, refreshToken }

  // Применить настройки к CSS custom properties
  const accentColor = useSettingsStore((s) => s.accentColor);
  const fontSize = useSettingsStore((s) => s.fontSize);
  const reducedMotion = useSettingsStore((s) => s.reducedMotion);
  const highContrast = useSettingsStore((s) => s.highContrast);
  const largeControls = useSettingsStore((s) => s.largeControls);

  const theme = useSettingsStore((s) => s.theme);

  useEffect(() => {
    const root = document.documentElement;
    if (accentColor) {
      if (theme === 'light') {
        // В светлой теме маппим яркие цвета в контрастные версии
        const lightMappings = {
          '#c8ff00': { accent: '#6b8e00', text: '#ffffff' },
          '#00d4ff': { accent: '#0284c7', text: '#ffffff' },
          '#ff6b6b': { accent: '#dc2626', text: '#ffffff' },
          '#a855f7': { accent: '#7c3aed', text: '#ffffff' },
          '#ffffff': { accent: '#6c757d', text: '#ffffff' },
        };
        const mapped = lightMappings[accentColor] || { accent: accentColor, text: '#ffffff' };
        root.style.setProperty('--accent', mapped.accent);
        root.style.setProperty('--accent-text', mapped.text);
      } else {
        // Тёмная тема — оригинальный акцент
        root.style.setProperty('--accent', accentColor);
        // Текст на акценте: для лайма и светлых цветов — тёмный
        const darkTextColors = {
          '#c8ff00': '#08060f',
          '#00d4ff': '#08060f',
          '#ff6b6b': '#ffffff',
          '#a855f7': '#ffffff',
          '#ffffff': '#08060f',
        };
        root.style.setProperty('--accent-text', darkTextColors[accentColor] || '#08060f');
      }
    }
    if (fontSize) root.style.setProperty('--font-size-base', fontSize + 'px');
    root.classList.toggle('reduced-motion', !!reducedMotion);
    root.classList.toggle('high-contrast', !!highContrast);
    root.classList.toggle('large-controls', !!largeControls);
  }, [accentColor, fontSize, reducedMotion, highContrast, largeControls, theme]);

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
        credentials: 'include',
        body: JSON.stringify({ refreshToken }),
      });
      if (res.ok) {
        const data = await res.json();
        localStorage.setItem('token', data.token);
        if (data.refreshToken) localStorage.setItem('refreshToken', data.refreshToken);
        return data.token;
      }
    } catch (err) { console.error('App refreshToken:', err?.message || err); }
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
        credentials: 'include',
      }).catch(() => null);

      // Если токен истёк — пробуем обновить
      if (!res || !res.ok) {
        const newToken = await tryRefreshToken();
        if (newToken) {
          res = await fetch(`${API_URL}/api/auth/me`, {
            headers: { Authorization: `Bearer ${newToken}` },
            credentials: 'include',
          }).catch(() => null);
        }
      }

      if (res && res.ok) {
        const data = await res.json();
        await initCsrf();
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
      const newToken = await tryRefreshToken();
      if (!newToken) {
        // Refresh не удался — сессия истекла, разлогинить
        handleLogout();
      }
    }, 12 * 60 * 1000);
    return () => clearInterval(interval);
  }, [user]);

  // Генерация E2E ключей при входе (если ещё нет)
  useEffect(() => {
    if (!user) return;
    ensureKeyPair().catch((err) => console.error('E2E key generation error:', err));
  }, [user]);

  const handleLogin = (data) => {
    localStorage.setItem('token', data.token);
    if (data.refreshToken) {
      localStorage.setItem('refreshToken', data.refreshToken);
    }

    initCsrf();

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

  const handleLogout = async () => {
    // Удалить E2E ключевые файлы с диска
    await window.blesk?.crypto?.clearAll?.();
    setUser(null);
    // Очистить CSRF-токен
    clearCsrf();
    // Очистить E2E ключи и кеши
    clearCache();
    // Полная очистка всех Zustand stores
    useChatStore.setState({ chats: [], messages: {}, activeChats: new Set(), onlineUsers: [], userStatuses: {}, typingUsers: {}, chatsInitialized: false, loadingChatList: false, loadingChats: new Set(), customStatuses: {} });
    useNotificationStore.setState({ notifications: [], unreadCount: 0 });
    useVoiceStore.getState().clearCurrentRoom();
    useVoiceStore.setState({ rooms: [], loading: false, userVolumes: {} });
    useCallStore.setState({ incomingCall: null, activeCall: null });
    useChannelStore.setState({ channels: [], myChannels: [], posts: {} });
    // Токены
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
    <ErrorBoundary>
      <div className={`app${isMaximized ? ' app--maximized' : ''}`}>
        <Suspense fallback={null}><MetaballFilter /></Suspense>
        <TitleBar />
        <div className={transition === 'revealing' ? 'main-reveal' : ''} style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
          <MainScreen user={user} onLogout={handleLogout} isAdmin={user?.role === 'admin'} />
        </div>
        <UpdateToast />
      </div>
    </ErrorBoundary>
  );
}
