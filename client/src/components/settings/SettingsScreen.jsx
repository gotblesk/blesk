import React, { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence, LayoutGroup } from 'framer-motion';
import { gsap } from 'gsap';
import {
  X, SpeakerHigh, Globe, Monitor, Palette, Sun, Moon, Bell,
  ChatDots, UsersThree, At, Shield, Clock, GithubLogo, Scales,
  ArrowSquareOut, ChatCircle, Confetti, Eye, Keyboard,
  HardDrive, Wheelchair, ArrowCounterClockwise, Trash, DownloadSimple,
  SignOut, CaretRight, GearSix, Sparkle, MagnifyingGlass, User, Warning,
  Lock, EyeSlash, Desktop, DeviceMobile, Browser, Power
} from '@phosphor-icons/react';
import VoiceSettings from '../voice/VoiceSettings';
import FeedbackScreen from './FeedbackScreen';
import Avatar from '../ui/Avatar';
import { useSettingsStore } from '../../store/settingsStore';
import { useChatStore } from '../../store/chatStore';
import useAppVersion from '../../hooks/useAppVersion';
import API_URL from '../../config';
import { getAuthHeaders } from '../../utils/authFetch';
import ConfirmDialog from '../ui/ConfirmDialog';
import './SettingsScreen.css';

/* ═══════ GROUPED TABS CONFIG ═══════ */
const GROUPED_TABS = [
  {
    label: 'Аккаунт',
    items: [
      { id: 'general', label: 'Профиль', icon: User },
      { id: 'privacy', label: 'Приватность', icon: Shield },
    ],
  },
  {
    label: 'Приложение',
    items: [
      { id: 'appearance', label: 'Внешний вид', icon: Palette },
      { id: 'chat', label: 'Чат', icon: ChatDots },
      { id: 'notifications', label: 'Уведомления', icon: Bell },
      { id: 'voice', label: 'Голос и видео', icon: SpeakerHigh },
      { id: 'hotkeys', label: 'Горячие клавиши', icon: Keyboard },
    ],
  },
  {
    label: 'Другое',
    items: [
      { id: 'accessibility', label: 'Доступность', icon: Wheelchair },
      { id: 'storage', label: 'Хранилище', icon: HardDrive },
      { id: 'about', label: 'О blesk', icon: Sparkle },
    ],
  },
];

const ALL_TAB_IDS = GROUPED_TABS.flatMap(g => g.items.map(t => t.id));

const ACCENT_COLORS = [
  { color: '#c8ff00', label: 'Лайм' },
  { color: '#00d4ff', label: 'Голубой' },
  { color: '#ff6b6b', label: 'Коралл' },
  { color: '#a855f7', label: 'Фиолет' },
  { color: '#ffffff', label: 'Белый' },
];

/* ═══════ ANIMATION VARIANTS ═══════ */
const backdropV = {
  hidden: { opacity: 0 },
  visible: { opacity: 1, transition: { duration: 0.2 } },
  exit: { opacity: 0, transition: { duration: 0.15 } },
};

const modalV = {
  hidden: { scale: 0.98, opacity: 0 },
  visible: {
    scale: 1,
    opacity: 1,
    transition: { type: 'spring', damping: 28, stiffness: 350 },
  },
  exit: {
    scale: 0.98,
    opacity: 0,
    transition: { duration: 0.15, ease: [0.4, 0, 1, 1] },
  },
};

const contentV = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: { duration: 0.15, ease: [0.25, 0.1, 0.25, 1] } },
  exit: { opacity: 0, transition: { duration: 0.1 } },
};

const staggerContainer = {
  animate: { transition: { staggerChildren: 0.04 } },
};

const staggerItem = {
  initial: { opacity: 0, x: -8 },
  animate: { opacity: 1, x: 0, transition: { duration: 0.25, ease: [0.25, 0.1, 0.25, 1] } },
};

/* ═══════ MAIN COMPONENT ═══════ */
export default function SettingsScreen({ open, onClose, onLogout, onFeedback }) {
  const [tab, setTab] = useState(() => localStorage.getItem('blesk_settings_tab') || 'general');
  const [search, setSearch] = useState('');
  const openFeedback = onFeedback || (() => {});
  const [logoClicks, setLogoClicks] = useState(0);
  const [easterEgg, setEasterEgg] = useState(false);
  const [logoutConfirm, setLogoutConfirm] = useState(false);
  const [user, setUser] = useState(null);
  const [checkingUpdate, setCheckingUpdate] = useState(false);
  const clickTimerRef = useRef(null);
  const searchRef = useRef(null);

  const settings = useSettingsStore();
  const { toggle, setValue } = settings;
  const version = useAppVersion();

  // Restore accent color + UI zoom on mount
  useEffect(() => {
    const saved = localStorage.getItem('blesk-accent-color');
    if (saved) {
      document.documentElement.style.setProperty('--accent', saved);
    }
    const zoom = settings.uiZoom;
    if (zoom && zoom !== 1) {
      document.body.style.zoom = zoom;
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Загрузка данных текущего пользователя (re-fetch при открытии и при возврате на профиль)
  const fetchUser = useCallback(async () => {
    try {
      const res = await fetch(`${API_URL}/api/auth/me`, {
        headers: { ...getAuthHeaders() }, credentials: 'include',
      });
      if (res.ok) {
        const data = await res.json();
        if (data.user) setUser(data.user);
      }
    } catch { /* тихий fallback */ }
  }, []);

  useEffect(() => {
    if (!open) return;
    fetchUser();
  }, [open, fetchUser]);

  // Re-fetch user при возврате на вкладку профиля (синхронизация аватара)
  useEffect(() => {
    if (open && tab === 'general') fetchUser();
  }, [tab, open, fetchUser]);

  // Escape закрывает настройки ТОЛЬКО если нет открытых ConfirmDialog
  useEffect(() => {
    if (!open) return;
    const handler = (e) => {
      if (e.key === 'Escape' && !logoutConfirm) onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open, onClose, logoutConfirm]);

  useEffect(() => {
    if (!open) { setLogoClicks(0); setEasterEgg(false); setSearch(''); }
    return () => clearTimeout(clickTimerRef.current);
  }, [open]);

  /* ═══ Theme transition (radial wipe + GSAP) ═══ */
  const themeTransitioning = useRef(false);
  const handleThemeChange = (theme, e) => {
    if (themeTransitioning.current) return;
    const currentTheme = document.documentElement.getAttribute('data-theme') || 'dark';
    if (currentTheme === theme) return;

    const x = e ? e.clientX : window.innerWidth / 2;
    const y = e ? e.clientY : window.innerHeight / 2;
    const xPct = ((x / window.innerWidth) * 100).toFixed(1) + '%';
    const yPct = ((y / window.innerHeight) * 100).toFixed(1) + '%';

    const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
      || document.documentElement.classList.contains('reduced-motion');

    if (prefersReduced) {
      setValue('theme', theme);
      document.documentElement.setAttribute('data-theme', theme);
      return;
    }

    themeTransitioning.current = true;

    const overlay = document.createElement('div');
    overlay.className = 'theme-wipe-overlay';
    overlay.style.setProperty('--wipe-x', xPct);
    overlay.style.setProperty('--wipe-y', yPct);
    overlay.style.background = theme === 'light' ? '#f5f5f7' : '#0a0a0f';
    document.body.appendChild(overlay);

    const tl = gsap.timeline({
      defaults: { ease: 'power2.out' },
      onComplete: () => {
        overlay.remove();
        document.documentElement.classList.remove('theme-transitioning');
        themeTransitioning.current = false;
      }
    });

    tl.add(() => {
      document.documentElement.classList.add('theme-transitioning');
    }, 0);

    tl.add(() => {
      setValue('theme', theme);
      document.documentElement.setAttribute('data-theme', theme);
    }, 0.15);

    tl.fromTo('.chat-card, .voice-room-card, .voice-card',
      { opacity: 1, scale: 1 },
      { opacity: 0.85, scale: 0.98, duration: 0.15, stagger: 0.02, yoyo: true, repeat: 1 },
      0.2
    );

    tl.to(overlay, {
      opacity: 0,
      duration: 0.3,
      ease: 'power2.in',
    }, 0.55);
  };

  const handleLogoClick = () => {
    clearTimeout(clickTimerRef.current);
    const next = logoClicks + 1;
    setLogoClicks(next);
    if (next >= 10) { setEasterEgg(true); setLogoClicks(0); return; }
    clickTimerRef.current = setTimeout(() => setLogoClicks(0), 3000);
  };

  const handleTabChange = (id) => {
    setTab(id);
    localStorage.setItem('blesk_settings_tab', id);
  };

  const handleCheckUpdate = useCallback(() => {
    setCheckingUpdate(true);
    try {
      window.blesk?.checkForUpdates?.();
    } catch { /* fallback */ }
    setTimeout(() => setCheckingUpdate(false), 5000);
  }, []);

  const handleTabKeyDown = useCallback((e) => {
    const tabEls = [...e.currentTarget.querySelectorAll('[role="tab"]')];
    const current = tabEls.indexOf(e.target);
    if (current === -1) return;

    let next;
    if (e.key === 'ArrowDown') {
      next = (current + 1) % tabEls.length;
    } else if (e.key === 'ArrowUp') {
      next = (current - 1 + tabEls.length) % tabEls.length;
    } else if (e.key === 'Home') {
      next = 0;
    } else if (e.key === 'End') {
      next = tabEls.length - 1;
    } else return;

    e.preventDefault();
    tabEls[next].focus();
    tabEls[next].click();
  }, []);

  // Найти label текущего таба
  const currentTabLabel = GROUPED_TABS.flatMap(g => g.items).find(t => t.id === tab)?.label || '';

  // Фильтрация групп по поиску
  const searchLower = search.toLowerCase();
  const filteredGroups = GROUPED_TABS
    .map(group => ({
      ...group,
      items: group.items.filter(t => !search || t.label.toLowerCase().includes(searchLower)),
    }))
    .filter(group => group.items.length > 0);

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            className="stg-backdrop"
            variants={backdropV}
            initial="hidden"
            animate="visible"
            exit="exit"
            onClick={onClose}
          />
          <motion.div
            className="stg"
            variants={modalV}
            initial="hidden"
            animate="visible"
            exit="exit"
            tabIndex={-1}
            onKeyDown={(e) => {
              if (e.key !== 'Tab') return;
              const focusable = e.currentTarget.querySelectorAll(
                'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
              );
              if (!focusable.length) return;
              const first = focusable[0];
              const last = focusable[focusable.length - 1];
              if (e.shiftKey) {
                if (document.activeElement === first) { e.preventDefault(); last.focus(); }
              } else {
                if (document.activeElement === last) { e.preventDefault(); first.focus(); }
              }
            }}
          >
            {/* ═══ LEFT: Sidebar ═══ */}
            <aside className="stg__sidebar">
              {/* Account card */}
              <div className="stg__account">
                <Avatar user={user} size={36} />
                <div className="stg__account-info">
                  <span className="stg__account-name">{user?.username || '...'}</span>
                  <span className="stg__account-tag">#{user?.tag || '0000'}</span>
                </div>
              </div>

              {/* Search */}
              <div className="stg__search">
                <MagnifyingGlass size={14} />
                <input
                  ref={searchRef}
                  placeholder="Поиск настроек..."
                  value={search}
                  onChange={e => setSearch(e.target.value)}
                  onKeyDown={e => e.key === 'Escape' && setSearch('')}
                />
              </div>

              {/* Grouped navigation */}
              <nav className="stg__nav" role="tablist" aria-orientation="vertical" onKeyDown={handleTabKeyDown}>
                {filteredGroups.map(group => (
                  <div key={group.label} className="stg__group">
                    <span className="stg__group-label">{group.label}</span>
                    {group.items.map(t => {
                      const Icon = t.icon;
                      const isActive = tab === t.id;
                      return (
                        <button
                          key={t.id}
                          role="tab"
                          aria-selected={isActive}
                          className={`stg__tab ${isActive ? 'stg__tab--active' : ''}`}
                          onClick={() => handleTabChange(t.id)}
                          tabIndex={isActive ? 0 : -1}
                        >
                          <Icon size={18} />
                          <span>{t.label}</span>
                        </button>
                      );
                    })}
                  </div>
                ))}

                {/* Logout внизу */}
                {onLogout && (
                  <div className="stg__group stg__group--bottom">
                    <button className="stg__tab stg__tab--danger" onClick={() => setLogoutConfirm(true)}>
                      <SignOut size={18} />
                      <span>Выйти</span>
                    </button>
                  </div>
                )}
              </nav>
            </aside>

            {/* ═══ RIGHT: Content Area ═══ */}
            <main className="stg__content">
              <div className="stg__header">
                <h2 className="stg__title">{currentTabLabel}</h2>
                <button className="stg__close" onClick={onClose} aria-label="Закрыть настройки">
                  <X size={20} />
                </button>
              </div>
              <div className="stg__body">
                <AnimatePresence mode="wait">
                  <motion.div
                    key={tab}
                    variants={contentV}
                    initial="initial"
                    animate="animate"
                    exit="exit"
                  >
                    {tab === 'general' && <GeneralTab settings={settings} toggle={toggle} setValue={setValue} />}
                    {tab === 'appearance' && <AppearanceTab settings={settings} toggle={toggle} setValue={setValue} handleThemeChange={handleThemeChange} />}
                    {tab === 'chat' && <ChatTab settings={settings} toggle={toggle} setValue={setValue} />}
                    {tab === 'notifications' && <NotificationsTab settings={settings} toggle={toggle} setValue={setValue} />}
                    {tab === 'voice' && <VoiceTab />}
                    {tab === 'privacy' && <PrivacyTab settings={settings} toggle={toggle} setValue={setValue} />}
                    {tab === 'hotkeys' && <HotkeysTab />}
                    {tab === 'storage' && <StorageTab />}
                    {tab === 'accessibility' && <AccessibilityTab settings={settings} toggle={toggle} />}
                    {tab === 'about' && <AboutTab version={version} logoClicks={logoClicks} easterEgg={easterEgg} handleLogoClick={handleLogoClick} setFeedbackOpen={openFeedback} checkingUpdate={checkingUpdate} onCheckUpdate={handleCheckUpdate} />}
                  </motion.div>
                </AnimatePresence>
              </div>
            </main>
          </motion.div>
          <ConfirmDialog
            open={logoutConfirm}
            title="Выйти из аккаунта?"
            message="Вы будете перенаправлены на экран входа"
            confirmText="Выйти"
            danger
            onConfirm={() => { setLogoutConfirm(false); onLogout?.(); }}
            onCancel={() => setLogoutConfirm(false)}
          />
        </>
      )}
    </AnimatePresence>
  );
}

/* ═══════════════════════════════════════════════
   SHARED COMPONENTS
   ═══════════════════════════════════════════════ */

function SettingGroup({ title, children }) {
  return (
    <motion.div className="stg-group" variants={staggerContainer} initial="initial" animate="animate">
      {title && <div className="stg-group__title">{title}</div>}
      <div className="stg-group__card">
        {children}
      </div>
    </motion.div>
  );
}

function SettingRow({ icon, label, hint, children, onClick, danger }) {
  return (
    <motion.div
      className={`stg-row ${danger ? 'stg-row--danger' : ''}`}
      variants={staggerItem}
      onClick={onClick}
    >
      <div className="stg-row__left">
        {icon && <div className={`stg-row__icon ${danger ? 'stg-row__icon--danger' : ''}`}>{icon}</div>}
        <div className="stg-row__info">
          <span className="stg-row__label">{label}</span>
          {hint && <span className="stg-row__hint">{hint}</span>}
        </div>
      </div>
      <div className="stg-row__right">
        {children}
      </div>
    </motion.div>
  );
}

function Toggle({ value, onChange, accent }) {
  return (
    <motion.div
      className={`stg-toggle ${value ? 'stg-toggle--on' : ''} ${accent ? 'stg-toggle--accent' : ''}`}
      role="switch"
      aria-checked={value}
      tabIndex={0}
      onClick={(e) => { e.stopPropagation(); onChange(); }}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); onChange(); } }}
      whileTap={{ scale: 0.9 }}
    >
      <motion.div
        className="stg-toggle__thumb"
        animate={{ x: value ? 18 : 0 }}
        transition={{ type: 'spring', stiffness: 500, damping: 30 }}
      />
      <div className="stg-toggle__glow" />
    </motion.div>
  );
}

function Chip({ active, children, onClick }) {
  return (
    <motion.button
      className={`stg-chip ${active ? 'stg-chip--active' : ''}`}
      onClick={onClick}
      whileHover={{ scale: 1.04 }}
      whileTap={{ scale: 0.96 }}
    >
      {active && (
        <motion.div
          className="stg-chip__bg"
          layoutId="chipBg"
          transition={{ type: 'spring', stiffness: 400, damping: 28 }}
        />
      )}
      <span className="stg-chip__text">{children}</span>
    </motion.button>
  );
}

function Select({ value, options, onChange }) {
  return (
    <select className="stg-select" value={value} onChange={(e) => onChange(e.target.value)}>
      {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  );
}

/* ═══════════════════════════════════════════════
   TAB CONTENT
   ═══════════════════════════════════════════════ */

function GeneralTab({ settings, toggle, setValue }) {
  const [autoStart, setAutoStart] = useState(false);
  useEffect(() => {
    window.blesk?.getAutoStart?.().then((v) => setAutoStart(!!v)).catch(() => {});
  }, []);
  const handleAutoStart = () => {
    const next = !autoStart;
    setAutoStart(next);
    window.blesk?.setAutoStart?.(next);
  };

  return (
    <div className="stg-tab">
      <SettingGroup title="Основные">
        <SettingRow icon={<SpeakerHigh size={16} />} label="Звуки" hint="Звуки уведомлений и интерфейса">
          <Toggle value={settings.sounds} onChange={() => toggle('sounds')} />
        </SettingRow>
        <SettingRow icon={<Monitor size={16} />} label="Анимированный фон" hint="Цветные шары на фоне">
          <Toggle value={settings.animatedBg} onChange={() => toggle('animatedBg')} />
        </SettingRow>
        <SettingRow icon={<Power size={16} />} label="Запускать при старте системы" hint="Открывать blesk при включении ПК">
          <Toggle value={autoStart} onChange={handleAutoStart} />
        </SettingRow>
      </SettingGroup>
      <SettingGroup title="Язык">
        <SettingRow icon={<Globe size={16} />} label="Язык интерфейса" hint="Другие языки скоро">
          <Select value="ru" options={[{ value: 'ru', label: 'Русский' }]} onChange={() => {}} />
        </SettingRow>
      </SettingGroup>
    </div>
  );
}

function AppearanceTab({ settings, toggle, setValue, handleThemeChange }) {
  return (
    <div className="stg-tab">
      <SettingGroup title="Тема">
        <div className="stg-theme-switcher">
          <LayoutGroup>
            <motion.button
              className={`stg-theme-card ${settings.theme === 'dark' ? 'stg-theme-card--active' : ''}`}
              onClick={(e) => handleThemeChange('dark', e)}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              {settings.theme === 'dark' && (
                <motion.div className="stg-theme-card__ring" layoutId="themeRing" transition={{ type: 'spring', stiffness: 350, damping: 25 }} />
              )}
              <div className="stg-theme-card__preview stg-theme-card__preview--dark">
                <div className="stg-theme-card__bar" />
                <div className="stg-theme-card__lines">
                  <div /><div /><div />
                </div>
              </div>
              <div className="stg-theme-card__label">
                <Moon size={12} /> Тёмная
              </div>
            </motion.button>
            <motion.button
              className={`stg-theme-card ${settings.theme === 'light' ? 'stg-theme-card--active' : ''}`}
              onClick={(e) => handleThemeChange('light', e)}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
            >
              {settings.theme === 'light' && (
                <motion.div className="stg-theme-card__ring" layoutId="themeRing" transition={{ type: 'spring', stiffness: 350, damping: 25 }} />
              )}
              <div className="stg-theme-card__preview stg-theme-card__preview--light">
                <div className="stg-theme-card__bar" />
                <div className="stg-theme-card__lines">
                  <div /><div /><div />
                </div>
              </div>
              <div className="stg-theme-card__label">
                <Sun size={12} /> Светлая
              </div>
            </motion.button>
          </LayoutGroup>
        </div>
      </SettingGroup>

      <SettingGroup title="Акцент">
        <div className="stg-accent-row">
          {ACCENT_COLORS.map((c) => (
            <motion.button
              key={c.color}
              className={`stg-accent-dot ${settings.accentColor === c.color ? 'stg-accent-dot--active' : ''}`}
              style={{ '--dot': c.color }}
              onClick={() => {
                setValue('accentColor', c.color);
                document.documentElement.style.setProperty('--accent', c.color);
                localStorage.setItem('blesk-accent-color', c.color);
              }}
              title={c.label}
              whileHover={{ scale: 1.15, y: -2 }}
              whileTap={{ scale: 0.9 }}
            >
              {settings.accentColor === c.color && (
                <motion.div
                  className="stg-accent-dot__ring"
                  layoutId="accentRing"
                  transition={{ type: 'spring', stiffness: 400, damping: 25 }}
                />
              )}
            </motion.button>
          ))}
          <label className="stg-accent-custom" title="Свой цвет">
            <input
              type="color"
              value={settings.accentColor || '#c8ff00'}
              onChange={(e) => {
                setValue('accentColor', e.target.value);
                document.documentElement.style.setProperty('--accent', e.target.value);
                localStorage.setItem('blesk-accent-color', e.target.value);
              }}
              className="stg-accent-custom__input"
            />
            <span className="stg-accent-custom__label">Свой</span>
          </label>
        </div>
      </SettingGroup>

      <SettingGroup title="Отображение">
        <SettingRow icon={<ChatDots size={16} />} label="Компактные сообщения" hint="Меньше отступов">
          <Toggle value={settings.compactMessages} onChange={() => toggle('compactMessages')} />
        </SettingRow>
        <SettingRow icon={<span style={{ fontSize: 14, fontWeight: 800 }}>A</span>} label="Размер шрифта" hint={`${settings.fontSize}px`}>
          <input
            type="range"
            className="stg-slider"
            min={12} max={18} step={1}
            value={settings.fontSize}
            onChange={(e) => setValue('fontSize', Number(e.target.value))}
          />
        </SettingRow>
        <SettingRow icon={<Scales size={16} />} label="Масштаб интерфейса" hint={`${Math.round((settings.uiZoom ?? 1) * 100)}%`}>
          <input
            type="range"
            className="stg-slider"
            min={0.8} max={1.2} step={0.05}
            value={settings.uiZoom ?? 1}
            onChange={(e) => {
              const v = Number(e.target.value);
              setValue('uiZoom', v);
              document.body.style.zoom = v;
            }}
          />
        </SettingRow>
      </SettingGroup>
    </div>
  );
}

function ChatTab({ settings, toggle, setValue }) {
  return (
    <div className="stg-tab">
      <SettingGroup title="Внешний вид">
        <SettingRow icon={<ChatDots size={16} />} label="Стиль сообщений" hint="Внешний вид сообщений">
          <div className="stg-chips">
            <LayoutGroup>
              {[
                { value: 'bubbles', label: 'Пузыри' },
                { value: 'minimal', label: 'Минимал' },
                { value: 'classic', label: 'Классик' },
              ].map((o) => (
                <Chip key={o.value} active={settings.chatBubbleStyle === o.value} onClick={() => setValue('chatBubbleStyle', o.value)}>
                  {o.label}
                </Chip>
              ))}
            </LayoutGroup>
          </div>
        </SettingRow>
        <SettingRow icon={<UsersThree size={16} />} label="Аватары в чате" hint="Показывать аватары рядом с сообщениями">
          <Toggle value={settings.showAvatarsInChat} onChange={() => toggle('showAvatarsInChat')} />
        </SettingRow>
      </SettingGroup>
      <SettingGroup title="Ввод">
        <SettingRow icon={<Clock size={16} />} label="Формат времени" hint="Отображение времени">
          <Select value={settings.timeFormat} options={[{ value: '24h', label: '24 часа' }, { value: '12h', label: '12 часов' }]} onChange={(v) => setValue('timeFormat', v)} />
        </SettingRow>
        <SettingRow icon={<ChatDots size={16} />} label="Enter = отправка" hint="Shift+Enter — новая строка">
          <Toggle value={settings.enterToSend} onChange={() => toggle('enterToSend')} />
        </SettingRow>
      </SettingGroup>
    </div>
  );
}

function NotificationsTab({ settings, toggle, setValue }) {
  const mutedChats = useChatStore((s) => s.mutedChats);
  const clearAllMuted = useChatStore((s) => s.toggleMuteChat);
  const mutedCount = mutedChats?.size || 0;

  const handleUnmuteAll = () => {
    if (!mutedChats || mutedChats.size === 0) return;
    for (const chatId of [...mutedChats]) {
      clearAllMuted(chatId);
    }
  };

  return (
    <div className="stg-tab">
      <SettingGroup title="Режим">
        <SettingRow icon={<Bell size={16} />} label="Не беспокоить" hint="Отключить все уведомления и звуки">
          <Toggle value={settings.dnd} onChange={() => toggle('dnd')} accent />
        </SettingRow>
        {settings.dnd && (
          <SettingRow icon={<Clock size={16} />} label="Расписание тишины" hint="Автоматический режим по времени">
            <div className="stg-dnd-schedule">
              <input type="time" value={settings.dndStart || '23:00'} onChange={e => setValue('dndStart', e.target.value)} className="stg-dnd-schedule__input" />
              <span className="stg-dnd-schedule__sep">&mdash;</span>
              <input type="time" value={settings.dndEnd || '08:00'} onChange={e => setValue('dndEnd', e.target.value)} className="stg-dnd-schedule__input" />
            </div>
          </SettingRow>
        )}
      </SettingGroup>
      <SettingGroup title="Уведомления">
        <SettingRow icon={<Bell size={16} />} label="Уведомления" hint="Показывать уведомления">
          <Toggle value={settings.notifications} onChange={() => toggle('notifications')} accent />
        </SettingRow>
        <SettingRow icon={<SpeakerHigh size={16} />} label="Звук уведомлений" hint="Воспроизводить звук">
          <Toggle value={settings.sounds} onChange={() => toggle('sounds')} />
        </SettingRow>
        {settings.sounds && (
          <SettingRow icon={<SpeakerHigh size={16} />} label="Тип звука" hint="Выберите стиль звука">
            <select value={settings.notifSound || 'default'} onChange={e => setValue('notifSound', e.target.value)} className="stg-select">
              <option value="default">По умолчанию</option>
              <option value="soft">Мягкий</option>
              <option value="bright">Яркий</option>
              <option value="none">Без звука</option>
            </select>
          </SettingRow>
        )}
      </SettingGroup>
      <SettingGroup title="Каналы">
        <SettingRow icon={<ChatDots size={16} />} label="Сообщения" hint="Новые сообщения в чатах">
          <Toggle value={settings.notifMessages} onChange={() => toggle('notifMessages')} />
        </SettingRow>
        <SettingRow icon={<UsersThree size={16} />} label="Друзья" hint="Заявки в друзья">
          <Toggle value={settings.notifFriends} onChange={() => toggle('notifFriends')} />
        </SettingRow>
        <SettingRow icon={<At size={16} />} label="Упоминания" hint="Когда вас @упоминают">
          <Toggle value={settings.notifMentions} onChange={() => toggle('notifMentions')} />
        </SettingRow>
      </SettingGroup>
      <SettingGroup title="Чаты без звука">
        <SettingRow icon={<Bell size={16} />} label="Замьюченные чаты" hint={`${mutedCount} ${mutedCount === 1 ? 'чат' : mutedCount < 5 ? 'чата' : 'чатов'}`}>
          <button
            className="stg-unmute-btn"
            onClick={handleUnmuteAll}
            disabled={mutedCount === 0}
          >
            Включить все
          </button>
        </SettingRow>
      </SettingGroup>
    </div>
  );
}

function VoiceTab() {
  return (
    <div className="stg-tab">
      <SettingGroup>
        <VoiceSettings />
      </SettingGroup>
    </div>
  );
}

function TwoFactorSection() {
  const [status, setStatus] = useState('idle');
  const [qrUrl, setQrUrl] = useState('');
  const [secret, setSecret] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [is2FAEnabled, setIs2FAEnabled] = useState(false);
  const [checking, setChecking] = useState(true);
  const [recoveryCodes, setRecoveryCodes] = useState(null);
  const [codesCopied, setCodesCopied] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(`${API_URL}/api/auth/me`, {
          headers: { ...getAuthHeaders() }, credentials: 'include',
        });
        if (res.ok) {
          const data = await res.json();
          setIs2FAEnabled(!!data.user?.twoFactorEnabled);
        }
      } catch { /* игнорируем */ }
      setChecking(false);
    })();
  }, []);

  const handleSetup = async () => {
    setStatus('loading');
    setError('');
    try {
      const res = await fetch(`${API_URL}/api/auth/2fa/setup`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
        credentials: 'include',
      });
      const data = await res.json();
      if (!res.ok) { setError(data.error || 'Ошибка'); setStatus('idle'); return; }
      setQrUrl(data.qr);
      setSecret(data.secret);
      setStatus('setup');
    } catch {
      setError('Не удалось подключиться к серверу');
      setStatus('idle');
    }
  };

  const handleVerify = async () => {
    if (!code || code.length !== 6) { setError('Введите 6-значный код'); return; }
    setError('');
    setStatus('loading');
    try {
      const res = await fetch(`${API_URL}/api/auth/2fa/verify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
        credentials: 'include',
        body: JSON.stringify({ code }),
      });
      const data = await res.json();
      if (!res.ok) { setError(data.error || 'Ошибка'); setStatus('setup'); return; }
      setIs2FAEnabled(true);
      setCode('');
      setQrUrl('');
      setSecret('');
      if (data.recoveryCodes) {
        setRecoveryCodes(data.recoveryCodes);
        setCodesCopied(false);
        setStatus('recovery');
      } else {
        setStatus('idle');
      }
    } catch {
      setError('Не удалось подключиться к серверу');
      setStatus('setup');
    }
  };

  const handleDisable = async () => {
    if (!code || code.length !== 6) { setError('Введите 6-значный код'); return; }
    setError('');
    setStatus('loading');
    try {
      const res = await fetch(`${API_URL}/api/auth/2fa/disable`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
        credentials: 'include',
        body: JSON.stringify({ code }),
      });
      const data = await res.json();
      if (!res.ok) { setError(data.error || 'Ошибка'); setStatus('disabling'); return; }
      setIs2FAEnabled(false);
      setStatus('idle');
      setCode('');
    } catch {
      setError('Не удалось подключиться к серверу');
      setStatus('disabling');
    }
  };

  if (checking) return null;

  return (
    <SettingGroup title="Двухфакторная аутентификация">
      <div className="tfa">
        <div className="tfa__header">
          <Shield size={16} className={is2FAEnabled ? 'tfa__icon--active' : 'tfa__icon--inactive'} />
          <span className="tfa__label">
            {is2FAEnabled ? '2FA включена' : '2FA отключена'}
          </span>
          <span className={`tfa__badge ${is2FAEnabled ? 'tfa__badge--active' : ''}`}>
            {is2FAEnabled ? 'Активна' : 'Не настроена'}
          </span>
        </div>

        {status === 'setup' && (
          <div className="tfa__setup">
            {qrUrl && <img src={qrUrl} alt="QR" className="tfa__qr" />}
            <div className="tfa__secret-hint">
              Ручной ввод: <span className="tfa__secret-code">{secret}</span>
            </div>
            <div className="tfa__input-row">
              <input
                type="text"
                inputMode="numeric"
                maxLength={6}
                placeholder="000000"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                className="tfa__code-input"
              />
              <button
                onClick={handleVerify}
                disabled={status === 'loading'}
                className={`tfa__btn tfa__btn--confirm ${status === 'loading' ? 'tfa__btn--loading' : ''}`}
              >
                {status === 'loading' ? '...' : 'Подтвердить'}
              </button>
            </div>
            <button
              onClick={() => { setStatus('idle'); setCode(''); setError(''); }}
              className="tfa__cancel"
            >
              Отмена
            </button>
          </div>
        )}

        {status === 'recovery' && recoveryCodes && (
          <div className="tfa__recovery">
            <div className="tfa__recovery-warning">
              <Warning size={16} weight="bold" style={{ color: 'var(--warning, #f59e0b)', flexShrink: 0 }} />
              <span>Сохраните эти коды в безопасном месте. Каждый код можно использовать только один раз.</span>
            </div>
            <div className="tfa__recovery-grid">
              {recoveryCodes.map((c, i) => (
                <span key={i} className="tfa__recovery-code">{c}</span>
              ))}
            </div>
            <div className="tfa__input-row">
              <button
                onClick={() => {
                  navigator.clipboard.writeText(recoveryCodes.join('\n'));
                  setCodesCopied(true);
                }}
                className="tfa__btn tfa__btn--confirm"
              >
                {codesCopied ? 'Скопировано' : 'Копировать все'}
              </button>
              <button
                onClick={() => { setRecoveryCodes(null); setStatus('idle'); }}
                className="tfa__btn tfa__btn--confirm"
              >
                Готово
              </button>
            </div>
          </div>
        )}

        {status === 'disabling' && (
          <div className="tfa__disable">
            <div className="tfa__disable-hint">
              Введите код из приложения-аутентификатора
            </div>
            <div className="tfa__input-row">
              <input
                type="text"
                inputMode="numeric"
                maxLength={6}
                placeholder="000000"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                className="tfa__code-input"
              />
              <button
                onClick={handleDisable}
                className="tfa__btn tfa__btn--danger"
              >
                Отключить
              </button>
            </div>
            <button
              onClick={() => { setStatus('idle'); setCode(''); setError(''); }}
              className="tfa__cancel"
            >
              Отмена
            </button>
          </div>
        )}

        {status === 'idle' && !is2FAEnabled && (
          <button onClick={handleSetup} className="tfa__btn tfa__btn--confirm">
            Включить 2FA
          </button>
        )}

        {status === 'idle' && is2FAEnabled && (
          <button
            onClick={() => { setStatus('disabling'); setCode(''); setError(''); }}
            className="tfa__btn tfa__btn--danger-ghost"
          >
            Отключить 2FA
          </button>
        )}

        {error && (
          <div className="tfa__error">{error}</div>
        )}
      </div>
    </SettingGroup>
  );
}

function SessionsSection() {
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [revoking, setRevoking] = useState(null);
  const [revokingAll, setRevokingAll] = useState(false);

  const loadSessions = useCallback(async () => {
    try {
      const res = await fetch(`${API_URL}/api/auth/sessions`, {
        headers: { ...getAuthHeaders() }, credentials: 'include',
      });
      if (res.ok) {
        const data = await res.json();
        setSessions(Array.isArray(data.sessions) ? data.sessions : Array.isArray(data) ? data : []);
      }
    } catch { /* тихий fallback */ }
    setLoading(false);
  }, []);

  useEffect(() => { loadSessions(); }, [loadSessions]);

  const parseUA = (ua) => {
    if (!ua) return { device: 'Неизвестно', browser: '' };
    const isMobile = /mobile|android|iphone/i.test(ua);
    const isElectron = /electron/i.test(ua);
    let browser = 'Браузер';
    if (isElectron) browser = 'blesk Desktop';
    else if (/firefox/i.test(ua)) browser = 'Firefox';
    else if (/edg/i.test(ua)) browser = 'Edge';
    else if (/chrome/i.test(ua)) browser = 'Chrome';
    else if (/safari/i.test(ua)) browser = 'Safari';
    const device = isElectron ? 'Desktop' : isMobile ? 'Мобильное' : 'Desktop';
    return { device, browser };
  };

  const handleRevoke = async (sessionId) => {
    setRevoking(sessionId);
    try {
      const res = await fetch(`${API_URL}/api/auth/sessions/${sessionId}`, {
        method: 'DELETE',
        headers: { ...getAuthHeaders() }, credentials: 'include',
      });
      if (res.ok) {
        setSessions(prev => prev.filter(s => s.id !== sessionId));
      }
    } catch { /* */ }
    setRevoking(null);
  };

  const handleRevokeAll = async () => {
    setRevokingAll(true);
    try {
      const res = await fetch(`${API_URL}/api/auth/sessions`, {
        method: 'DELETE',
        headers: { ...getAuthHeaders() }, credentials: 'include',
      });
      if (res.ok) {
        setSessions(prev => prev.filter(s => s.current));
      }
    } catch { /* */ }
    setRevokingAll(false);
  };

  const otherSessions = sessions.filter(s => !s.current);

  return (
    <SettingGroup title="Активные сессии">
      <div className="stg-sessions">
        {otherSessions.length > 0 && (
          <button
            className="stg-sessions__revoke-all"
            onClick={handleRevokeAll}
            disabled={revokingAll}
          >
            <SignOut size={13} weight="bold" />
            {revokingAll ? 'Завершение...' : 'Завершить все другие'}
          </button>
        )}

        {loading && (
          <div className="stg-sessions__loading">Загрузка...</div>
        )}

        {!loading && sessions.length === 0 && (
          <div className="stg-sessions__empty">Нет данных о сессиях</div>
        )}

        {sessions.map((session) => {
          const { device, browser } = parseUA(session.userAgent);
          const isMobile = device === 'Мобильное';
          const created = session.createdAt ? new Date(session.createdAt).toLocaleDateString('ru-RU', {
            day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
          }) : '';

          return (
            <div key={session.id} className={`stg-sessions__item ${session.current ? 'stg-sessions__item--current' : ''}`}>
              <div className="stg-sessions__item-icon">
                {isMobile ? <DeviceMobile size={16} /> : <Desktop size={16} />}
              </div>
              <div className="stg-sessions__item-info">
                <div className="stg-sessions__item-name">
                  {browser}
                  {session.current && <span className="stg-sessions__badge">Текущая</span>}
                </div>
                <div className="stg-sessions__item-meta">
                  {device}{created ? ` \u00b7 ${created}` : ''}
                </div>
              </div>
              {!session.current && (
                <motion.button
                  className="stg-sessions__item-revoke"
                  onClick={() => handleRevoke(session.id)}
                  disabled={revoking === session.id}
                  whileHover={{ scale: 1.08 }}
                  whileTap={{ scale: 0.9 }}
                  title="Завершить сессию"
                >
                  <SignOut size={14} weight="bold" />
                </motion.button>
              )}
            </div>
          );
        })}
      </div>
    </SettingGroup>
  );
}

function PrivacyTab({ settings, toggle, setValue }) {
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteShowPw, setDeleteShowPw] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const privacyDebounce = useRef({});

  const handleDeleteAccount = async () => {
    if (!deletePassword) { setDeleteError('Введите пароль'); return; }
    setDeleteLoading(true);
    setDeleteError('');
    try {
      const res = await fetch(`${API_URL}/api/auth/account`, {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
        credentials: 'include',
        body: JSON.stringify({ password: deletePassword }),
      });
      if (res.ok) {
        localStorage.clear();
        sessionStorage.clear();
        window.blesk?.clearCache?.();
        window.location.reload();
      } else {
        const data = await res.json().catch(() => ({}));
        setDeleteError(data.error || 'Неверный пароль');
      }
    } catch {
      setDeleteError('Не удалось подключиться к серверу');
    } finally {
      setDeleteLoading(false);
    }
  };

  return (
    <div className="stg-tab">
      <SettingGroup title="Видимость">
        <SettingRow icon={<Eye size={16} />} label="Показывать онлайн" hint="Другие видят что вы в сети">
          <Toggle value={settings.showOnline} onChange={() => {
            const next = !settings.showOnline;
            toggle('showOnline');
            clearTimeout(privacyDebounce.current.showOnline);
            privacyDebounce.current.showOnline = setTimeout(() => {
              fetch(`${API_URL}/api/users/me`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json', ...getAuthHeaders() }, credentials: 'include',
                body: JSON.stringify({ showOnline: next }),
              }).then(r => { if (!r.ok) toggle('showOnline'); }).catch(() => { toggle('showOnline'); });
            }, 500);
          }} />
        </SettingRow>
        <SettingRow icon={<ChatDots size={16} />} label="Индикатор набора" hint="Другие видят когда вы печатаете">
          <Toggle value={settings.showTyping} onChange={() => {
            const next = !settings.showTyping;
            toggle('showTyping');
            clearTimeout(privacyDebounce.current.showTyping);
            privacyDebounce.current.showTyping = setTimeout(() => {
              fetch(`${API_URL}/api/users/me`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json', ...getAuthHeaders() }, credentials: 'include',
                body: JSON.stringify({ showTyping: next }),
              }).then(r => { if (!r.ok) toggle('showTyping'); }).catch(() => { toggle('showTyping'); });
            }, 500);
          }} />
        </SettingRow>
        <SettingRow icon={<Clock size={16} />} label="Время последнего визита" hint="Другие видят когда вы были в сети">
          <Toggle value={settings.showLastSeen} onChange={() => {
            const next = !settings.showLastSeen;
            toggle('showLastSeen');
            clearTimeout(privacyDebounce.current.showLastSeen);
            privacyDebounce.current.showLastSeen = setTimeout(() => {
              fetch(`${API_URL}/api/users/me`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json', ...getAuthHeaders() }, credentials: 'include',
                body: JSON.stringify({ showLastSeen: next }),
              }).then(r => { if (!r.ok) toggle('showLastSeen'); }).catch(() => { toggle('showLastSeen'); });
            }, 500);
          }} />
        </SettingRow>
      </SettingGroup>
      <SettingGroup title="Контакты">
        <SettingRow icon={<ChatDots size={16} />} label="Кто может писать" hint="Ограничить входящие сообщения">
          <select value={settings.whoCanMessage || 'everyone'} onChange={e => setValue('whoCanMessage', e.target.value)} className="stg-select">
            <option value="everyone">Все</option>
            <option value="friends">Только друзья</option>
          </select>
        </SettingRow>
        <SettingRow icon={<SpeakerHigh size={16} />} label="Кто может звонить" hint="Ограничить входящие звонки">
          <select value={settings.whoCanCall || 'everyone'} onChange={e => setValue('whoCanCall', e.target.value)} className="stg-select">
            <option value="everyone">Все</option>
            <option value="friends">Только друзья</option>
            <option value="nobody">Никто</option>
          </select>
        </SettingRow>
      </SettingGroup>
      <SettingGroup title="Шифрование">
        <SettingRow icon={<Shield size={16} />} label="Сквозное шифрование" hint="Личные сообщения шифруются на устройстве">
          <Toggle value={settings.e2eEnabled} onChange={() => toggle('e2eEnabled')} accent />
        </SettingRow>
      </SettingGroup>
      <TwoFactorSection />
      <SessionsSection />

      <SettingGroup title="Опасная зона">
        <SettingRow
          icon={<Trash size={16} />}
          label="Удалить аккаунт"
          hint="Навсегда удалить аккаунт и все данные"
          danger
          onClick={() => { setDeleteOpen(true); setDeletePassword(''); setDeleteError(''); setDeleteShowPw(false); }}
        />
      </SettingGroup>

      <AnimatePresence>
        {deleteOpen && (
          <motion.div
            className="stg-delete-overlay"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={() => setDeleteOpen(false)}
          >
            <motion.div
              className="stg-delete-dialog"
              initial={{ opacity: 0, scale: 0.85, y: 12 }}
              animate={{ opacity: 1, scale: 1, y: 0, transition: { type: 'spring', stiffness: 500, damping: 30 } }}
              exit={{ opacity: 0, scale: 0.9, y: 8, transition: { duration: 0.15 } }}
              onClick={(e) => e.stopPropagation()}
            >
              <div className="stg-delete-dialog__icon">
                <Warning size={28} weight="fill" />
              </div>
              <h3 className="stg-delete-dialog__title">Удалить аккаунт?</h3>
              <p className="stg-delete-dialog__text">
                Это действие необратимо. Все ваши данные будут удалены.
              </p>
              <div className="stg-delete-dialog__field">
                <label className="stg-delete-dialog__label">Подтвердите паролем</label>
                <div className="stg-delete-dialog__pw-wrap">
                  <input
                    type={deleteShowPw ? 'text' : 'password'}
                    className="stg-delete-dialog__input"
                    value={deletePassword}
                    onChange={(e) => setDeletePassword(e.target.value)}
                    placeholder="Ваш пароль"
                    autoFocus
                    onKeyDown={(e) => { if (e.key === 'Enter') handleDeleteAccount(); }}
                  />
                  <button
                    className="stg-delete-dialog__eye"
                    onClick={() => setDeleteShowPw(p => !p)}
                    type="button"
                    tabIndex={-1}
                  >
                    {deleteShowPw ? <EyeSlash size={14} /> : <Eye size={14} />}
                  </button>
                </div>
              </div>
              {deleteError && <div className="stg-delete-dialog__error">{deleteError}</div>}
              <div className="stg-delete-dialog__actions">
                <button
                  className="stg-delete-dialog__btn stg-delete-dialog__btn--cancel"
                  onClick={() => setDeleteOpen(false)}
                >
                  Отмена
                </button>
                <button
                  className="stg-delete-dialog__btn stg-delete-dialog__btn--danger"
                  onClick={handleDeleteAccount}
                  disabled={deleteLoading || !deletePassword}
                >
                  {deleteLoading ? '...' : 'Удалить навсегда'}
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function HotkeysTab() {
  const hotkeys = useSettingsStore((s) => s.hotkeys);
  const setHotkey = useSettingsStore((s) => s.setHotkey);
  const resetHotkeys = useSettingsStore((s) => s.resetHotkeys);
  const [editing, setEditing] = useState(null);
  const [conflict, setConflict] = useState(null);

  const HOTKEY_LABELS = {
    search: { label: 'Поиск (Spotlight)' },
    tabChats: { label: 'Вкладка: Чаты' },
    tabVoice: { label: 'Вкладка: Голос' },
    tabChannels: { label: 'Вкладка: Каналы' },
    tabFriends: { label: 'Вкладка: Друзья' },
    toggleMute: { label: 'Мут микрофона' },
    settings: { label: 'Настройки' },
    escape: { label: 'Закрыть', editable: false, key: 'Escape' },
    chatSearch: { label: 'Поиск в чате', editable: false, key: 'Ctrl+F' },
    focusMode: { label: 'Focus Mode', editable: false, key: 'Ctrl+Shift+F' },
    editLast: { label: 'Редактировать последнее', editable: false, key: 'ArrowUp' },
  };

  useEffect(() => {
    if (!editing) return;
    const handler = (e) => {
      e.preventDefault();
      e.stopPropagation();
      const parts = [];
      if (e.ctrlKey) parts.push('Ctrl');
      if (e.shiftKey) parts.push('Shift');
      if (e.altKey) parts.push('Alt');
      const key = e.key;
      if (!['Control', 'Shift', 'Alt', 'Meta'].includes(key)) {
        const combo = parts.join('+') + (parts.length ? '+' : '') + (key.length === 1 ? key.toUpperCase() : key);

        // Проверка конфликтов
        const conflictEntry = Object.entries(hotkeys).find(
          ([act, val]) => act !== editing && val === combo
        );
        if (conflictEntry) {
          setHotkey(conflictEntry[0], '');
          setConflict({ combo, existing: HOTKEY_LABELS[conflictEntry[0]]?.label || conflictEntry[0] });
          setTimeout(() => setConflict(null), 3000);
        } else {
          setConflict(null);
        }

        setHotkey(editing, combo);
        setEditing(null);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [editing, setHotkey, hotkeys]);

  return (
    <div className="stg-tab">
      <SettingGroup title="Горячие клавиши">
        {Object.entries(HOTKEY_LABELS).map(([action, meta]) => {
          const isFixed = meta.editable === false;
          return (
            <SettingRow
              key={action}
              icon={<Keyboard size={16} />}
              label={meta.label}
              onClick={isFixed ? undefined : () => { setEditing(editing === action ? null : action); setConflict(null); }}
            >
              <motion.div
                className={`stg-hotkey ${editing === action ? 'stg-hotkey--editing' : ''} ${isFixed ? 'stg-hotkey--fixed' : ''}`}
                animate={editing === action ? { scale: [1, 1.05, 1] } : {}}
                transition={{ duration: 0.8, repeat: Infinity }}
              >
                {isFixed ? meta.key : editing === action ? 'Нажмите...' : hotkeys[action] || '—'}
              </motion.div>
            </SettingRow>
          );
        })}
      </SettingGroup>

      <AnimatePresence>
        {conflict && (
          <motion.div
            className="stg-hotkey-conflict"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
          >
            <Warning size={14} weight="bold" />
            <span>Клавиша «{conflict.combo}» уже используется для «{conflict.existing}»</span>
          </motion.div>
        )}
      </AnimatePresence>

      <SettingGroup>
        <SettingRow icon={<ArrowCounterClockwise size={16} />} label="Сбросить горячие клавиши" hint="Вернуть стандартные" onClick={resetHotkeys} />
      </SettingGroup>
    </div>
  );
}

function StorageTab() {
  const [cleared, setCleared] = useState(null);
  const [storageUsage, setStorageUsage] = useState(null);
  const chats = useChatStore((s) => s.chats);
  const messages = useChatStore((s) => s.messages);
  const msgCount = Object.values(messages).reduce((sum, arr) => sum + arr.length, 0);

  useEffect(() => {
    if (navigator.storage?.estimate) {
      navigator.storage.estimate().then(({ usage, quota }) => {
        setStorageUsage({ usage: usage || 0, quota: quota || 0 });
      }).catch(() => {});
    }
  }, []);

  const formatBytes = (bytes) => {
    if (bytes === 0) return '0 Б';
    const units = ['Б', 'КБ', 'МБ', 'ГБ'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + units[i];
  };

  const clearMessages = () => {
    useChatStore.setState({ messages: {} });
    setCleared('messages');
    setTimeout(() => setCleared(null), 2000);
  };
  const clearNebula = () => {
    localStorage.removeItem('blesk_nebula');
    setCleared('nebula');
    setTimeout(() => setCleared(null), 2000);
  };
  const clearBrowserCache = () => {
    window.blesk?.clearCache?.();
    setCleared('browser');
    setTimeout(() => setCleared(null), 2000);
  };
  const [resetConfirm, setResetConfirm] = useState(false);
  const resetSettings = () => {
    setResetConfirm(true);
  };
  const doReset = () => {
    localStorage.removeItem('blesk-settings');
    localStorage.removeItem('blesk-hotkeys');
    window.location.reload();
  };

  return (
    <div className="stg-tab">
      {storageUsage && (
        <SettingGroup title="Использование">
          <SettingRow icon={<HardDrive size={16} />} label="Занято" hint={`${formatBytes(storageUsage.usage)} из ${formatBytes(storageUsage.quota)}`}>
            <div className="stg-storage-bar">
              <div
                className="stg-storage-bar__fill"
                style={{ width: `${Math.min((storageUsage.usage / storageUsage.quota) * 100, 100).toFixed(1)}%` }}
              />
            </div>
          </SettingRow>
        </SettingGroup>
      )}
      <SettingGroup title="Кеш">
        <SettingRow icon={<HardDrive size={16} />} label="Кеш сообщений" hint={`${chats.length} чатов, ${msgCount} сообщений`}>
          <motion.button className="stg-btn" onClick={clearMessages} whileTap={{ scale: 0.95 }}>
            {cleared === 'messages' ? 'Очищено' : 'Очистить'}
          </motion.button>
        </SettingRow>
        <SettingRow icon={<DownloadSimple size={16} />} label="Позиции карточек" hint="Сохранённые позиции в Nebula">
          <motion.button className="stg-btn" onClick={clearNebula} whileTap={{ scale: 0.95 }}>
            {cleared === 'nebula' ? 'Очищено' : 'Сбросить'}
          </motion.button>
        </SettingRow>
        <SettingRow icon={<Trash size={16} />} label="Кеш браузера" hint="Очистить сессионный кеш Electron">
          <motion.button className="stg-btn" onClick={clearBrowserCache} whileTap={{ scale: 0.95 }}>
            {cleared === 'browser' ? 'Очищено' : 'Очистить'}
          </motion.button>
        </SettingRow>
      </SettingGroup>
      <SettingGroup title="Опасная зона">
        <SettingRow icon={<Trash size={16} />} label="Сбросить все настройки" hint="Перезагрузит приложение" danger onClick={resetSettings} />
      </SettingGroup>

      <ConfirmDialog
        open={resetConfirm}
        title="Сбросить все настройки?"
        message="Все настройки будут возвращены к значениям по умолчанию. Приложение перезагрузится."
        confirmText="Сбросить"
        cancelText="Отмена"
        danger
        onConfirm={doReset}
        onCancel={() => setResetConfirm(false)}
      />
    </div>
  );
}

function AccessibilityTab({ settings, toggle }) {
  return (
    <div className="stg-tab">
      <SettingGroup title="Восприятие">
        <SettingRow icon={<Wheelchair size={16} />} label="Уменьшить движение" hint="Отключить все анимации и переходы">
          <Toggle value={settings.reducedMotion} onChange={() => toggle('reducedMotion')} />
        </SettingRow>
        <SettingRow icon={<Eye size={16} />} label="Высокий контраст" hint="Увеличить контрастность текста и границ">
          <Toggle value={settings.highContrast} onChange={() => toggle('highContrast')} />
        </SettingRow>
        <SettingRow icon={<Wheelchair size={16} />} label="Увеличенные элементы" hint="Кнопки и текст крупнее">
          <Toggle value={settings.largeControls} onChange={() => toggle('largeControls')} />
        </SettingRow>
      </SettingGroup>
    </div>
  );
}

function AboutTab({ version, logoClicks, easterEgg, handleLogoClick, setFeedbackOpen, checkingUpdate, onCheckUpdate }) {
  return (
    <div className="stg-tab stg-about">
      <div className="stg-about__hero">
        <motion.div
          className={`stg-about__logo ${easterEgg ? 'stg-about__logo--party' : ''}`}
          onClick={handleLogoClick}
          whileHover={{ scale: 1.06 }}
          whileTap={{ scale: 0.92, rotate: -4 }}
          title={logoClicks > 5 ? `${10 - logoClicks}...` : undefined}
        >
          <img className="stg-about__logo-img" src="./blesk.png" alt="blesk" onError={(e) => { e.target.style.display = 'none'; if (e.target.nextSibling) e.target.nextSibling.style.display = 'flex'; }} />
          <span className="stg-about__logo-fallback" style={{ display: 'none' }}>bl</span>
          {easterEgg && (
            <div className="stg-about__sparks">
              {Array.from({ length: 12 }).map((_, i) => (
                <div key={i} className="stg-about__spark" style={{ '--angle': `${i * 30}deg`, '--delay': `${i * 0.05}s` }} />
              ))}
            </div>
          )}
        </motion.div>

        {logoClicks > 0 && !easterEgg && (
          <div className="stg-about__progress">
            {Array.from({ length: 10 }).map((_, i) => (
              <motion.div
                key={i}
                className={`stg-about__dot ${i < logoClicks ? 'stg-about__dot--filled' : ''}`}
                animate={i < logoClicks ? { scale: [1, 1.4, 1] } : {}}
                transition={{ duration: 0.2 }}
              />
            ))}
          </div>
        )}

        <div className="stg-about__name">blesk</div>
        <div className="stg-about__ver">v{version}</div>
        <div className="stg-about__slogan">Твой блеск. Твои правила.</div>

        <motion.button
          className="stg-about__update-btn"
          onClick={onCheckUpdate}
          disabled={checkingUpdate}
          whileHover={{ scale: 1.03 }}
          whileTap={{ scale: 0.96 }}
        >
          <DownloadSimple size={14} weight="bold" />
          {checkingUpdate ? 'Проверка...' : 'Проверить обновления'}
        </motion.button>

        <AnimatePresence>
          {easterEgg && (
            <motion.div
              className="stg-about__easter"
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ type: 'spring', damping: 15 }}
            >
              <Confetti size={24} weight="regular" />
              <span>Ты нашёл секрет! Ты — настоящий блеск.</span>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <SettingGroup>
        <a className="stg-about__link" href="https://github.com/gotblesk/blesk" target="_blank" rel="noopener noreferrer" onClick={(e) => { e.preventDefault(); window.blesk?.openExternal?.('https://github.com/gotblesk/blesk') || window.open('https://github.com/gotblesk/blesk', '_blank'); }}>
          <GithubLogo size={16} />
          <span>GitHub</span>
          <CaretRight size={14} className="stg-about__link-arrow" />
        </a>
        <a className="stg-about__link" href="https://blesk.fun" target="_blank" rel="noopener noreferrer" onClick={(e) => { e.preventDefault(); window.blesk?.openExternal?.('https://blesk.fun') || window.open('https://blesk.fun', '_blank'); }}>
          <Globe size={16} />
          <span>blesk.fun</span>
          <CaretRight size={14} className="stg-about__link-arrow" />
        </a>
        <a className="stg-about__link" href="https://github.com/gotblesk/blesk/blob/master/LICENSE" target="_blank" rel="noopener noreferrer" onClick={(e) => { e.preventDefault(); window.blesk?.openExternal?.('https://github.com/gotblesk/blesk/blob/master/LICENSE') || window.open('https://github.com/gotblesk/blesk/blob/master/LICENSE', '_blank'); }}>
          <Scales size={16} />
          <span>Лицензия AGPL-3.0</span>
          <CaretRight size={14} className="stg-about__link-arrow" />
        </a>
        <div className="stg-about__link" onClick={() => setFeedbackOpen(true)} role="button" tabIndex={0}>
          <ChatCircle size={16} />
          <span>Обратная связь</span>
          <CaretRight size={14} className="stg-about__link-arrow" />
        </div>
      </SettingGroup>

      <div className="stg-about__license">AGPL-3.0 License</div>
    </div>
  );
}
