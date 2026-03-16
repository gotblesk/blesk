import { useState, useEffect } from 'react';
import useAppVersion from '../../hooks/useAppVersion';
import { useSettingsStore } from '../../store/settingsStore';
import API_URL from '../../config';
import './SpotlightProfile.css';

export default function SpotlightProfile({ user, onNavigate, onLogout }) {
  const appVersion = useAppVersion();
  const currentTheme = useSettingsStore((s) => s.theme);
  const [open, setOpen] = useState(false);

  // Закрытие по Escape
  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (e.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open]);

  const hue = user?.hue || 176;
  const initial = (user?.username || 'U')[0].toUpperCase();

  const handleAction = (action) => {
    setOpen(false);
    if (action === 'logout') {
      onLogout?.();
    } else {
      onNavigate?.(action);
    }
  };

  return (
    <>
      <div className="user-pill" onClick={() => setOpen(true)}>
        <div className="user-pill__av-wrap">
          <div
            className="user-pill__av"
            style={{ background: user?.avatar ? 'none' : `hsl(${hue}, 70%, 50%)` }}
          >
            {user?.avatar
              ? <img src={`${API_URL}/uploads/avatars/${user.avatar}`} alt="" />
              : initial}
          </div>
          {user?.status !== 'invisible' && (
            <div
              className="user-pill__dot"
              style={{
                background: user?.status === 'dnd' ? 'var(--danger)' : 'var(--online)',
              }}
            />
          )}
        </div>
        <span className="user-pill__name">{user?.username || 'user'}</span>
      </div>

      <div
        className={`spotlight-overlay ${open ? 'spotlight-overlay--open' : ''}`}
        onClick={() => setOpen(false)}
      >
        <div
          className={`spotlight-card ${open ? 'spotlight-card--open' : ''}`}
          onClick={(e) => e.stopPropagation()}
        >
          <div className="spotlight-header">
            <div
              className="spotlight-avatar"
              style={{ background: user?.avatar ? 'none' : `hsl(${hue}, 70%, 50%)` }}
            >
              {user?.avatar
                ? <img src={`${API_URL}/uploads/avatars/${user.avatar}`} alt="" />
                : initial}
              <div className="spotlight-avatar__dot" />
            </div>
            <div className="spotlight-name">{user?.username}</div>
            <div className="spotlight-tag">{user?.tag}</div>
            <div className="spotlight-status">
              <div
                className="spotlight-status__dot"
                style={{
                  background: user?.status === 'dnd' ? '#ef4444'
                    : user?.status === 'invisible' ? '#6b7280'
                    : '#4ade80',
                }}
              />
              {user?.customStatus || (user?.status === 'dnd' ? 'Не беспокоить' : user?.status === 'invisible' ? 'Невидимка' : 'В сети')}
            </div>
          </div>

          <div className="spotlight-menu">
            <button
              className={`spotlight-item ${open ? 'spotlight-item--visible' : ''}`}
              style={{ transitionDelay: '0.1s' }}
              onClick={() => handleAction('profile')}
            >
              <div className="spotlight-item__icon">👤</div>
              <div className="spotlight-item__info">
                <div className="spotlight-item__label">Мой профиль</div>
                <div className="spotlight-item__hint">Аватар, био, теги</div>
              </div>
            </button>

            <button
              className={`spotlight-item ${open ? 'spotlight-item--visible' : ''}`}
              style={{ transitionDelay: '0.13s' }}
              onClick={() => handleAction('status')}
            >
              <div className="spotlight-item__icon">{user?.status === 'dnd' ? '🔴' : user?.status === 'invisible' ? '⚫' : '🟢'}</div>
              <div className="spotlight-item__info">
                <div className="spotlight-item__label">Изменить статус</div>
                <div className="spotlight-item__hint">{user?.customStatus || (user?.status === 'dnd' ? 'Не беспокоить' : user?.status === 'invisible' ? 'Невидимка' : 'В сети')}</div>
              </div>
            </button>

            <div className="spotlight-sep" />

            <button
              className={`spotlight-item ${open ? 'spotlight-item--visible' : ''}`}
              style={{ transitionDelay: '0.16s' }}
              onClick={() => handleAction('settings')}
            >
              <div className="spotlight-item__icon">⚙️</div>
              <div className="spotlight-item__info">
                <div className="spotlight-item__label">Настройки</div>
              </div>
            </button>

            <button
              className={`spotlight-item ${open ? 'spotlight-item--visible' : ''}`}
              style={{ transitionDelay: '0.19s' }}
              onClick={() => handleAction('theme')}
            >
              <div className="spotlight-item__icon">🎨</div>
              <div className="spotlight-item__info">
                <div className="spotlight-item__label">Тема</div>
                <div className="spotlight-item__hint">{currentTheme === 'light' ? 'Светлая' : 'Тёмная'}</div>
              </div>
            </button>

            <button
              className={`spotlight-item ${open ? 'spotlight-item--visible' : ''}`}
              style={{ transitionDelay: '0.22s' }}
              onClick={() => handleAction('feedback')}
            >
              <div className="spotlight-item__icon">📝</div>
              <div className="spotlight-item__info">
                <div className="spotlight-item__label">Обратная связь</div>
                <div className="spotlight-item__hint">Баг, идея, вопрос</div>
              </div>
            </button>

            <button
              className={`spotlight-item ${open ? 'spotlight-item--visible' : ''}`}
              style={{ transitionDelay: '0.25s' }}
              onClick={() => handleAction('about')}
            >
              <div className="spotlight-item__icon">ℹ️</div>
              <div className="spotlight-item__info">
                <div className="spotlight-item__label">О программе</div>
              </div>
            </button>

            <div className="spotlight-sep" />

            <button
              className={`spotlight-item spotlight-item--danger ${open ? 'spotlight-item--visible' : ''}`}
              style={{ transitionDelay: '0.28s' }}
              onClick={() => handleAction('logout')}
            >
              <div className="spotlight-item__icon">🚪</div>
              <div className="spotlight-item__info">
                <div className="spotlight-item__label">Выйти</div>
              </div>
            </button>
          </div>

          <div className="spotlight-footer">blesk v{appVersion}</div>
        </div>
      </div>
    </>
  );
}
