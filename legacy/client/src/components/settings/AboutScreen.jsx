import { useState, useEffect, useRef } from 'react';
import { ArrowSquareOut, Heart, X, Confetti, GithubLogo, Globe, Scales } from '@phosphor-icons/react';
import useAppVersion from '../../hooks/useAppVersion';
import './AboutScreen.css';
const SLOGAN = 'Твой блеск. Твои правила.';

export default function AboutScreen({ open, onClose }) {
  const VERSION = 'v' + useAppVersion();
  const [logoClicks, setLogoClicks] = useState(0);
  const [easterEgg, setEasterEgg] = useState(false);
  const clickTimerRef = useRef(null);

  // Escape закрывает
  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (e.key === 'Escape') onClose?.();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open, onClose]);

  // Сброс при закрытии + cleanup таймера
  useEffect(() => {
    if (!open) {
      setLogoClicks(0);
      setEasterEgg(false);
    }
    return () => clearTimeout(clickTimerRef.current);
  }, [open]);

  // Пасхалка: 10 кликов на логотип
  const handleLogoClick = () => {
    clearTimeout(clickTimerRef.current);

    const next = logoClicks + 1;
    setLogoClicks(next);

    if (next >= 10) {
      setEasterEgg(true);
      setLogoClicks(0);
      return;
    }

    // Сброс счётчика через 3 сек бездействия
    clickTimerRef.current = setTimeout(() => setLogoClicks(0), 3000);
  };

  if (!open) return null;

  return (
    <div className="about-overlay" onClick={onClose}>
      <div
        className={`about-card ${open ? 'about-card--open' : ''}`}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Specular highlight */}
        <div className="about-card__shine" />

        {/* Кнопка закрытия */}
        <button className="about-close" onClick={onClose}>
          <X size={18} weight="regular" />
        </button>

        {/* Логотип */}
        <div className="about-logo-area">
          <div
            className={`about-logo ${easterEgg ? 'about-logo--party' : ''} ${
              logoClicks > 0 ? 'about-logo--clicking' : ''
            }`}
            onClick={handleLogoClick}
            title={logoClicks > 5 ? `${10 - logoClicks}...` : undefined}
          >
            <img className="about-logo__img" src="./blesk.png" alt="blesk" onError={(e) => { e.target.style.display = 'none'; e.target.nextSibling && (e.target.nextSibling.style.display = 'flex'); }} />
            <span className="about-logo__fallback" style={{ display: 'none' }}>bl</span>
            {easterEgg && (
              <div className="about-logo__sparks">
                {Array.from({ length: 12 }).map((_, i) => (
                  <div
                    key={i}
                    className="about-logo__spark"
                    style={{
                      '--angle': `${i * 30}deg`,
                      '--delay': `${i * 0.05}s`,
                    }}
                  />
                ))}
              </div>
            )}
          </div>

          {/* Прогресс кликов */}
          {logoClicks > 0 && !easterEgg && (
            <div className="about-logo-progress">
              {Array.from({ length: 10 }).map((_, i) => (
                <div
                  key={i}
                  className={`about-logo-progress__dot ${
                    i < logoClicks ? 'about-logo-progress__dot--filled' : ''
                  }`}
                />
              ))}
            </div>
          )}
        </div>

        {/* Название */}
        <div className="about-title">blesk</div>
        <div className="about-version">{VERSION}</div>
        <div className="about-slogan">{SLOGAN}</div>

        {easterEgg && (
          <div className="about-easter">
            <div className="about-easter__emoji"><Confetti size={32} weight="regular" /></div>
            <div className="about-easter__text">
              Ты нашёл секрет! Ты — настоящий блеск.
            </div>
          </div>
        )}

        <div className="about-sep" />

        {/* Инфо */}
        <div className="about-info">
          <div className="about-info__row">
            <span className="about-info__label">Основатель</span>
            <span className="about-info__value about-info__value--accent">gotblesk</span>
          </div>
          <div className="about-info__row">
            <span className="about-info__label">Лицензия</span>
            <span className="about-info__value">AGPL-3.0</span>
          </div>
          <div className="about-info__row">
            <span className="about-info__label">Платформа</span>
            <span className="about-info__value">Electron + React</span>
          </div>
        </div>

        <div className="about-sep" />

        {/* Ссылки */}
        <div className="about-links">
          <a
            className="about-link"
            href="https://github.com/gotblesk/blesk"
            target="_blank"
            rel="noopener noreferrer"
            onClick={(e) => { e.preventDefault(); window.blesk?.openExternal?.('https://github.com/gotblesk/blesk') || window.open('https://github.com/gotblesk/blesk', '_blank'); }}
          >
            <span className="about-link__icon"><GithubLogo size={14} weight="regular" /></span>
            <span className="about-link__text">GitHub</span>
            <span className="about-link__arrow">→</span>
          </a>
          <a
            className="about-link"
            href="https://blesk.fun"
            target="_blank"
            rel="noopener noreferrer"
            onClick={(e) => { e.preventDefault(); window.blesk?.openExternal?.('https://blesk.fun') || window.open('https://blesk.fun', '_blank'); }}
          >
            <span className="about-link__icon"><Globe size={14} weight="regular" /></span>
            <span className="about-link__text">blesk.fun</span>
            <span className="about-link__arrow">→</span>
          </a>
          <a
            className="about-link"
            href="https://github.com/gotblesk/blesk/blob/master/LICENSE"
            target="_blank"
            rel="noopener noreferrer"
            onClick={(e) => { e.preventDefault(); window.blesk?.openExternal?.('https://github.com/gotblesk/blesk/blob/master/LICENSE') || window.open('https://github.com/gotblesk/blesk/blob/master/LICENSE', '_blank'); }}
          >
            <span className="about-link__icon"><Scales size={14} weight="regular" /></span>
            <span className="about-link__text">AGPL-3.0</span>
            <span className="about-link__arrow">→</span>
          </a>
        </div>

        <div className="about-footer">
          Сделано с <Heart size={14} weight="fill" style={{ color: 'var(--online)', verticalAlign: 'middle', margin: '0 2px' }} /> и бессонными ночами
        </div>
      </div>
    </div>
  );
}
