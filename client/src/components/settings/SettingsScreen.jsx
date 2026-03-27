import { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence, LayoutGroup } from 'framer-motion';
import { gsap } from 'gsap';
import {
  X, Volume2, Globe, Monitor, Palette, Sun, Moon, Bell,
  MessageSquare, Users, AtSign, Shield, Clock, Github,
  ExternalLink, MessageCircle, PartyPopper, Eye, Keyboard,
  HardDrive, Accessibility, RotateCcw, Trash2, Download,
  LogOut, ChevronRight, Settings, Sparkles
} from 'lucide-react';
import VoiceSettings from '../voice/VoiceSettings';
import FeedbackScreen from './FeedbackScreen';
import { useSettingsStore } from '../../store/settingsStore';
import { useChatStore } from '../../store/chatStore';
import useAppVersion from '../../hooks/useAppVersion';
import API_URL from '../../config';
import ConfirmDialog from '../ui/ConfirmDialog';
import './SettingsScreen.css';

/* ═══════ TABS CONFIG ═══════ */
const TABS = [
  { id: 'general',       label: 'Общие',        icon: Settings },
  { id: 'appearance',    label: 'Вид',           icon: Palette },
  { id: 'chat',          label: 'Чат',           icon: MessageSquare },
  { id: 'notifications', label: 'Уведомления',   icon: Bell },
  { id: 'voice',         label: 'Голос',          icon: Volume2 },
  { id: 'privacy',       label: 'Приватность',    icon: Shield },
  { id: 'hotkeys',       label: 'Клавиши',        icon: Keyboard },
  { id: 'storage',       label: 'Данные',         icon: HardDrive },
  { id: 'accessibility', label: 'Доступность',    icon: Accessibility },
  { id: 'about',         label: 'О blesk',        icon: Sparkles },
];

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
  visible: { opacity: 1, transition: { duration: 0.25 } },
  exit: { opacity: 0, transition: { duration: 0.2 } },
};

const panelV = {
  hidden: { x: '100%', opacity: 0.5 },
  visible: {
    x: 0,
    opacity: 1,
    transition: { type: 'spring', damping: 32, stiffness: 300, mass: 0.8 },
  },
  exit: {
    x: '100%',
    opacity: 0,
    transition: { duration: 0.25, ease: [0.4, 0, 1, 1] },
  },
};

const contentV = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.2, ease: [0.25, 0.1, 0.25, 1] } },
  exit: { opacity: 0, y: -6, transition: { duration: 0.12 } },
};

const staggerContainer = {
  animate: { transition: { staggerChildren: 0.04 } },
};

const staggerItem = {
  initial: { opacity: 0, x: -8 },
  animate: { opacity: 1, x: 0, transition: { duration: 0.25, ease: [0.25, 0.1, 0.25, 1] } },
};

/* ═══════ MAIN COMPONENT ═══════ */
export default function SettingsScreen({ open, onClose, onLogout }) {
  const [tab, setTab] = useState(() => localStorage.getItem('blesk_settings_tab') || 'general');
  const [feedbackOpen, setFeedbackOpen] = useState(false);
  const [logoClicks, setLogoClicks] = useState(0);
  const [easterEgg, setEasterEgg] = useState(false);
  const [logoutConfirm, setLogoutConfirm] = useState(false);
  const clickTimerRef = useRef(null);

  const settings = useSettingsStore();
  const { toggle, setValue } = settings;
  const version = useAppVersion();

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
    if (!open) { setLogoClicks(0); setEasterEgg(false); }
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
    overlay.style.background = theme === 'light' ? '#f0f2f5' : '#08060f';
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

  const activeTabData = TABS.find(t => t.id === tab);

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
            className="stg-panel"
            variants={panelV}
            initial="hidden"
            animate="visible"
            exit="exit"
          >
            {/* ═══ LEFT: Navigation Rail ═══ */}
            <nav className="stg-rail">
              <div className="stg-rail__top">
                {TABS.map((t) => {
                  const Icon = t.icon;
                  const isActive = tab === t.id;
                  return (
                    <motion.button
                      key={t.id}
                      className={`stg-rail__item ${isActive ? 'stg-rail__item--active' : ''}`}
                      onClick={() => handleTabChange(t.id)}
                      whileHover={{ scale: 1.08 }}
                      whileTap={{ scale: 0.92 }}
                      title={t.label}
                    >
                      {isActive && (
                        <motion.div
                          className="stg-rail__indicator"
                          layoutId="railIndicator"
                          transition={{ type: 'spring', stiffness: 380, damping: 28 }}
                        />
                      )}
                      <Icon size={18} strokeWidth={isActive ? 2 : 1.5} />
                    </motion.button>
                  );
                })}
              </div>

              {/* Logout at bottom of rail */}
              {onLogout && (
                <motion.button
                  className="stg-rail__item stg-rail__item--danger"
                  onClick={() => setLogoutConfirm(true)}
                  whileHover={{ scale: 1.08 }}
                  whileTap={{ scale: 0.92 }}
                  title="Выйти"
                >
                  <LogOut size={18} strokeWidth={1.5} />
                </motion.button>
              )}
            </nav>

            {/* ═══ RIGHT: Content Area ═══ */}
            <div className="stg-content">
              {/* Header */}
              <div className="stg-header">
                <div className="stg-header__left">
                  <motion.div
                    className="stg-header__icon"
                    key={tab}
                    initial={{ scale: 0.5, opacity: 0, rotate: -30 }}
                    animate={{ scale: 1, opacity: 1, rotate: 0 }}
                    transition={{ type: 'spring', stiffness: 400, damping: 20 }}
                  >
                    {activeTabData && <activeTabData.icon size={20} strokeWidth={1.8} />}
                  </motion.div>
                  <div>
                    <AnimatePresence mode="wait">
                      <motion.h2
                        key={tab}
                        className="stg-header__title"
                        initial={{ opacity: 0, y: 6 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -4 }}
                        transition={{ duration: 0.15 }}
                      >
                        {activeTabData?.label}
                      </motion.h2>
                    </AnimatePresence>
                    <div className="stg-header__subtitle">Настройки</div>
                  </div>
                </div>
                <motion.button
                  className="stg-header__close"
                  onClick={onClose}
                  whileHover={{ scale: 1.1, rotate: 90 }}
                  whileTap={{ scale: 0.85 }}
                  transition={{ type: 'spring', stiffness: 400, damping: 20 }}
                >
                  <X size={16} strokeWidth={2} />
                </motion.button>
              </div>

              {/* Scrollable Body */}
              <div className="stg-body">
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
                    {tab === 'notifications' && <NotificationsTab settings={settings} toggle={toggle} />}
                    {tab === 'voice' && <VoiceTab />}
                    {tab === 'privacy' && <PrivacyTab settings={settings} toggle={toggle} />}
                    {tab === 'hotkeys' && <HotkeysTab />}
                    {tab === 'storage' && <StorageTab />}
                    {tab === 'accessibility' && <AccessibilityTab settings={settings} toggle={toggle} />}
                    {tab === 'about' && <AboutTab version={version} logoClicks={logoClicks} easterEgg={easterEgg} handleLogoClick={handleLogoClick} setFeedbackOpen={setFeedbackOpen} />}
                  </motion.div>
                </AnimatePresence>
              </div>
            </div>
          </motion.div>
          <FeedbackScreen open={feedbackOpen} onClose={() => setFeedbackOpen(false)} />
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
      onClick={(e) => { e.stopPropagation(); onChange(); }}
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
  return (
    <div className="stg-tab">
      <SettingGroup title="Основные">
        <SettingRow icon={<Volume2 size={16} />} label="Звуки" hint="Звуки уведомлений и интерфейса">
          <Toggle value={settings.sounds} onChange={() => toggle('sounds')} />
        </SettingRow>
        <SettingRow icon={<Monitor size={16} />} label="Анимированный фон" hint="Цветные шары на фоне">
          <Toggle value={settings.animatedBg} onChange={() => toggle('animatedBg')} />
        </SettingRow>
      </SettingGroup>
      <SettingGroup title="Язык">
        <SettingRow icon={<Globe size={16} />} label="Язык интерфейса" hint="Перезапуск не требуется">
          <Select value={settings.language} options={[{ value: 'ru', label: 'Русский' }, { value: 'en', label: 'English' }]} onChange={(v) => setValue('language', v)} />
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
              onClick={() => setValue('accentColor', c.color)}
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
        </div>
      </SettingGroup>

      <SettingGroup title="Отображение">
        <SettingRow icon={<MessageSquare size={16} />} label="Компактные сообщения" hint="Меньше отступов">
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
      </SettingGroup>
    </div>
  );
}

function ChatTab({ settings, toggle, setValue }) {
  return (
    <div className="stg-tab">
      <SettingGroup title="Внешний вид">
        <SettingRow icon={<MessageSquare size={16} />} label="Стиль сообщений" hint="Внешний вид сообщений">
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
        <SettingRow icon={<Users size={16} />} label="Аватары в чате" hint="Показывать аватары рядом с сообщениями">
          <Toggle value={settings.showAvatarsInChat} onChange={() => toggle('showAvatarsInChat')} />
        </SettingRow>
      </SettingGroup>
      <SettingGroup title="Ввод">
        <SettingRow icon={<Clock size={16} />} label="Формат времени" hint="Отображение времени">
          <Select value={settings.timeFormat} options={[{ value: '24h', label: '24 часа' }, { value: '12h', label: '12 часов' }]} onChange={(v) => setValue('timeFormat', v)} />
        </SettingRow>
        <SettingRow icon={<MessageSquare size={16} />} label="Enter = отправка" hint="Shift+Enter — новая строка">
          <Toggle value={settings.enterToSend} onChange={() => toggle('enterToSend')} />
        </SettingRow>
      </SettingGroup>
    </div>
  );
}

function NotificationsTab({ settings, toggle }) {
  return (
    <div className="stg-tab">
      <SettingGroup title="Уведомления">
        <SettingRow icon={<Bell size={16} />} label="Уведомления" hint="Показывать уведомления">
          <Toggle value={settings.notifications} onChange={() => toggle('notifications')} accent />
        </SettingRow>
        <SettingRow icon={<Volume2 size={16} />} label="Звук уведомлений" hint="Воспроизводить звук">
          <Toggle value={settings.sounds} onChange={() => toggle('sounds')} />
        </SettingRow>
      </SettingGroup>
      <SettingGroup title="Каналы">
        <SettingRow icon={<MessageSquare size={16} />} label="Сообщения" hint="Новые сообщения в чатах">
          <Toggle value={settings.notifMessages} onChange={() => toggle('notifMessages')} />
        </SettingRow>
        <SettingRow icon={<Users size={16} />} label="Друзья" hint="Заявки в друзья">
          <Toggle value={settings.notifFriends} onChange={() => toggle('notifFriends')} />
        </SettingRow>
        <SettingRow icon={<AtSign size={16} />} label="Упоминания" hint="Когда вас @упоминают">
          <Toggle value={settings.notifMentions} onChange={() => toggle('notifMentions')} />
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

function PrivacyTab({ settings, toggle }) {
  return (
    <div className="stg-tab">
      <SettingGroup title="Видимость">
        <SettingRow icon={<Eye size={16} />} label="Показывать онлайн" hint="Другие видят что вы в сети">
          <Toggle value={settings.showOnline} onChange={() => toggle('showOnline')} />
        </SettingRow>
        <SettingRow icon={<MessageSquare size={16} />} label="Индикатор набора" hint="Другие видят когда вы печатаете">
          <Toggle value={settings.showTyping} onChange={() => toggle('showTyping')} />
        </SettingRow>
        <SettingRow icon={<Clock size={16} />} label="Время последнего визита" hint="Другие видят когда вы были в сети">
          <Toggle value={settings.showLastSeen} onChange={() => {
            const next = !settings.showLastSeen;
            toggle('showLastSeen');
            fetch(`${API_URL}/api/users/me`, {
              method: 'PUT',
              headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('token')}` },
              body: JSON.stringify({ showLastSeen: next }),
            }).then(r => { if (!r.ok) toggle('showLastSeen'); }).catch(() => { toggle('showLastSeen'); });
          }} />
        </SettingRow>
      </SettingGroup>
      <SettingGroup title="Шифрование">
        <SettingRow icon={<Shield size={16} />} label="Сквозное шифрование" hint="Личные сообщения шифруются на устройстве">
          <Toggle value={settings.e2eEnabled} onChange={() => toggle('e2eEnabled')} accent />
        </SettingRow>
      </SettingGroup>
    </div>
  );
}

function HotkeysTab() {
  const hotkeys = useSettingsStore((s) => s.hotkeys);
  const setHotkey = useSettingsStore((s) => s.setHotkey);
  const resetHotkeys = useSettingsStore((s) => s.resetHotkeys);
  const [editing, setEditing] = useState(null);

  const HOTKEY_LABELS = {
    search: 'Поиск (Spotlight)',
    tabChats: 'Вкладка: Чаты',
    tabVoice: 'Вкладка: Голос',
    tabChannels: 'Вкладка: Каналы',
    tabFriends: 'Вкладка: Друзья',
    toggleMute: 'Мут микрофона',
    settings: 'Настройки',
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
        parts.push(key.length === 1 ? key.toUpperCase() : key);
        setHotkey(editing, parts.join('+'));
        setEditing(null);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [editing, setHotkey]);

  return (
    <div className="stg-tab">
      <SettingGroup title="Горячие клавиши">
        {Object.entries(HOTKEY_LABELS).map(([action, label]) => (
          <SettingRow
            key={action}
            icon={<Keyboard size={16} />}
            label={label}
            onClick={() => setEditing(editing === action ? null : action)}
          >
            <motion.div
              className={`stg-hotkey ${editing === action ? 'stg-hotkey--editing' : ''}`}
              animate={editing === action ? { scale: [1, 1.05, 1] } : {}}
              transition={{ duration: 0.8, repeat: Infinity }}
            >
              {editing === action ? 'Нажмите...' : hotkeys[action] || '—'}
            </motion.div>
          </SettingRow>
        ))}
      </SettingGroup>
      <SettingGroup>
        <SettingRow icon={<RotateCcw size={16} />} label="Сбросить горячие клавиши" hint="Вернуть стандартные" onClick={resetHotkeys} />
      </SettingGroup>
    </div>
  );
}

function StorageTab() {
  const [cleared, setCleared] = useState(null);
  const chats = useChatStore((s) => s.chats);
  const messages = useChatStore((s) => s.messages);
  const msgCount = Object.values(messages).reduce((sum, arr) => sum + arr.length, 0);

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
      <SettingGroup title="Кеш">
        <SettingRow icon={<HardDrive size={16} />} label="Кеш сообщений" hint={`${chats.length} чатов, ${msgCount} сообщений`}>
          <motion.button className="stg-btn" onClick={clearMessages} whileTap={{ scale: 0.95 }}>
            {cleared === 'messages' ? 'Очищено' : 'Очистить'}
          </motion.button>
        </SettingRow>
        <SettingRow icon={<Download size={16} />} label="Позиции карточек" hint="Сохранённые позиции в Nebula">
          <motion.button className="stg-btn" onClick={clearNebula} whileTap={{ scale: 0.95 }}>
            {cleared === 'nebula' ? 'Очищено' : 'Сбросить'}
          </motion.button>
        </SettingRow>
      </SettingGroup>
      <SettingGroup title="Опасная зона">
        <SettingRow icon={<Trash2 size={16} />} label="Сбросить все настройки" hint="Перезагрузит приложение" danger onClick={resetSettings} />
      </SettingGroup>

      {/* [IMP-1 A2] Подтверждение сброса настроек */}
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
        <SettingRow icon={<Accessibility size={16} />} label="Уменьшить движение" hint="Отключить все анимации и переходы">
          <Toggle value={settings.reducedMotion} onChange={() => toggle('reducedMotion')} />
        </SettingRow>
        <SettingRow icon={<Eye size={16} />} label="Высокий контраст" hint="Увеличить контрастность текста и границ">
          <Toggle value={settings.highContrast} onChange={() => toggle('highContrast')} />
        </SettingRow>
        <SettingRow icon={<Accessibility size={16} />} label="Увеличенные элементы" hint="Кнопки и текст крупнее">
          <Toggle value={settings.largeControls} onChange={() => toggle('largeControls')} />
        </SettingRow>
      </SettingGroup>
    </div>
  );
}

function AboutTab({ version, logoClicks, easterEgg, handleLogoClick, setFeedbackOpen }) {
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

        <AnimatePresence>
          {easterEgg && (
            <motion.div
              className="stg-about__easter"
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ type: 'spring', damping: 15 }}
            >
              <PartyPopper size={24} strokeWidth={1.5} />
              <span>Ты нашёл секрет! Ты — настоящий блеск.</span>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      <SettingGroup>
        <a className="stg-about__link" href="https://github.com/gotblesk/blesk" target="_blank" rel="noopener noreferrer">
          <Github size={16} />
          <span>GitHub</span>
          <ChevronRight size={14} className="stg-about__link-arrow" />
        </a>
        <a className="stg-about__link" href="https://blesk.fun" target="_blank" rel="noopener noreferrer">
          <Globe size={16} />
          <span>blesk.fun</span>
          <ChevronRight size={14} className="stg-about__link-arrow" />
        </a>
        <div className="stg-about__link" onClick={() => setFeedbackOpen(true)} role="button" tabIndex={0}>
          <MessageCircle size={16} />
          <span>Обратная связь</span>
          <ChevronRight size={14} className="stg-about__link-arrow" />
        </div>
      </SettingGroup>

      <div className="stg-about__license">AGPL-3.0 License</div>
    </div>
  );
}
