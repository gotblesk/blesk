import { useState, useEffect, useRef } from 'react';
import { ExternalLink, Heart, X, PartyPopper } from 'lucide-react';
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
          <X size={18} strokeWidth={2} />
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
            <div className="about-easter__emoji"><PartyPopper size={32} strokeWidth={1.5} /></div>
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

        {/* Ссылка GitHub */}
        <a
          className="about-link"
          href="https://github.com/gotblesk/blesk"
          target="_blank"
          rel="noopener noreferrer"
        >
          <span className="about-link__icon"><ExternalLink size={14} strokeWidth={1.5} /></span>
          <span className="about-link__text">github.com/gotblesk/blesk</span>
          <span className="about-link__arrow">→</span>
        </a>

        <div className="about-footer">
          Сделано с <Heart size={14} strokeWidth={1.5} fill="currentColor" style={{ color: 'var(--online)', verticalAlign: 'middle', margin: '0 2px' }} /> и бессонными ночами
        </div>
      </div>
    </div>
  );
}
