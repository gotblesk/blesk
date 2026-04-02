import React, { useState, useEffect, useRef, Suspense } from 'react';
import { MotionConfig } from 'framer-motion';
import TitleBar from './components/ui/TitleBar';
import AuthScreen from './components/auth/AuthScreen';
import MainScreen from './components/main/MainScreen';
import UpdateToast from './components/ui/UpdateToast';
import ErrorBoundary from './components/ui/ErrorBoundary';
import { ensureKeyPair, clearCache } from './utils/cryptoService';
import { initCsrf, clearCsrf, setTokens, getToken, getRefreshToken, clearTokens, setUserId } from './utils/authFetch';
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
  const loginTimersRef = useRef([]);

  // Cleanup login timers on unmount
  useEffect(() => () => loginTimersRef.current.forEach(clearTimeout), []);

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

  // Извлечь userId из JWT payload и сохранить в memory
  function extractAndStoreUserId(token) {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      if (payload.userId) setUserId(payload.userId);
    } catch { /* невалидный JWT */ }
  }

  // Попытка обновить токен через refresh (cookies + in-memory fallback)
  async function tryRefreshToken() {
    const refreshToken = getRefreshToken();

    try {
      const res = await fetch(`${API_URL}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ refreshToken: refreshToken || undefined }),
      });
      if (res.ok) {
        const data = await res.json();
        setTokens(data.token, data.refreshToken || refreshToken);
        extractAndStoreUserId(data.token);
        return data.token;
      }
    } catch (err) { console.error('App refreshToken:', err?.message || err); }
    return null;
  }

  // Авто-логин: пробуем cookie-based refresh (токены в памяти пусты после рестарта)
  useEffect(() => {
    async function checkAuth() {
      let token = getToken();
      if (!token) {
        // После рестарта приложения in-memory токен пуст — пробуем refresh через cookies
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
        if (data.user?.id) setUserId(data.user.id);
        await initCsrf();
        if (data.user.email && data.user.emailVerified === false) {
          setNeedsVerify({
            user: data.user,
            token: getToken(),
            refreshToken: getRefreshToken(),
          });
        } else {
          setUser(data.user);
        }
      } else {
        clearTokens();
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

    // Дополнительный refresh при возврате окна из фона
    // setInterval throttle-ится браузером когда окно скрыто — этот хендлер компенсирует
    let lastRefreshAt = Date.now();
    const handleVisibilityRefresh = async () => {
      if (document.visibilityState !== 'visible') return;
      // Обновить токен если прошло более 11 минут с последнего refresh
      if (Date.now() - lastRefreshAt >= 11 * 60 * 1000) {
        const newToken = await tryRefreshToken();
        if (!newToken) {
          handleLogout();
        } else {
          lastRefreshAt = Date.now();
        }
      }
    };
    document.addEventListener('visibilitychange', handleVisibilityRefresh);

    return () => {
      clearInterval(interval);
      document.removeEventListener('visibilitychange', handleVisibilityRefresh);
    };
  }, [user]);

  // Генерация E2E ключей при входе (если ещё нет)
  useEffect(() => {
    if (!user) return;
    ensureKeyPair().catch((err) => console.error('E2E key generation error:', err));
  }, [user]);

  const handleLogin = (data) => {
    setTokens(data.token, data.refreshToken);
    extractAndStoreUserId(data.token);

    initCsrf();

    // Запускаем transition: auth collapse → main reveal
    setPendingUser(data.user);
    setTransition('collapsing');

    // После collapse анимации — показываем main
    const t1 = setTimeout(() => {
      setUser(data.user);
      setPendingUser(null);
      setTransition('revealing');

      // Убираем класс reveal после анимации
      const t2 = setTimeout(() => setTransition(null), 800);
      loginTimersRef.current.push(t2);
    }, 700);
    loginTimersRef.current.push(t1);
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
    // Токены (очистка in-memory)
    clearTokens();
  };

  // Пока проверяем токен — ничего не показываем (прелоадер уже скрылся)
  if (checking) {
    return (
      <div className={`app${isMaximized ? ' app--maximized' : ''}`}>
        <UpdateToast />
      </div>
    );
  }

  if (!user) {
    return (
      <ErrorBoundary>
        <div className={`app${isMaximized ? ' app--maximized' : ''}`}>
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
      </ErrorBoundary>
    );
  }

  return (
    <MotionConfig reducedMotion="user">
      <ErrorBoundary>
        <div className={`app${isMaximized ? ' app--maximized' : ''}`}>
          <Suspense fallback={null}><MetaballFilter /></Suspense>
          <div className={transition === 'revealing' ? 'main-reveal' : ''} style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
            <MainScreen user={user} onLogout={handleLogout} isAdmin={user?.role === 'admin'} />
          </div>
          <UpdateToast />
        </div>
      </ErrorBoundary>
    </MotionConfig>
  );
}
