import { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Volume2, Globe, Monitor, Palette, Sun, Moon, Bell, MessageSquare, Users, AtSign, Shield, Clock, Github, ExternalLink, MessageCircle, PartyPopper, Eye, Keyboard, HardDrive, Accessibility, RotateCcw, Trash2, Download, ChevronLeft, ChevronRight } from 'lucide-react';
import Glass from '../ui/Glass';
import VoiceSettings from '../voice/VoiceSettings';
import FeedbackScreen from './FeedbackScreen';
import { useSettingsStore } from '../../store/settingsStore';
import { useChatStore } from '../../store/chatStore';
import useAppVersion from '../../hooks/useAppVersion';
import API_URL from '../../config';
import './SettingsScreen.css';

const TABS = [
  { id: 'general', label: 'Общие' },
  { id: 'appearance', label: 'Вид' },
  { id: 'chat', label: 'Чат' },
  { id: 'notifications', label: 'Уведомления' },
  { id: 'voice', label: 'Голос' },
  { id: 'privacy', label: 'Приватность' },
  { id: 'hotkeys', label: 'Клавиши' },
  { id: 'storage', label: 'Данные' },
  { id: 'accessibility', label: 'Доступность' },
  { id: 'about', label: 'О blesk' },
];

const ACCENT_COLORS = [
  { color: '#c8ff00', label: 'Лайм' },
  { color: '#00d4ff', label: 'Голубой' },
  { color: '#ff6b6b', label: 'Коралл' },
  { color: '#a855f7', label: 'Фиолет' },
  { color: '#ffffff', label: 'Белый' },
];

const overlayV = { hidden: { opacity: 0 }, visible: { opacity: 1 }, exit: { opacity: 0 } };
const modalV = {
  hidden: { opacity: 0, scale: 0.94, y: 10 },
  visible: { opacity: 1, scale: 1, y: 0, transition: { type: 'spring', damping: 28, stiffness: 350 } },
  exit: { opacity: 0, scale: 0.96, y: 6, transition: { duration: 0.2 } },
};
const tabV = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: { duration: 0.15 } },
  exit: { opacity: 0, transition: { duration: 0.08 } },
};

export default function SettingsScreen({ open, onClose }) {
  const [tab, setTab] = useState('general');
  const [feedbackOpen, setFeedbackOpen] = useState(false);
  const [logoClicks, setLogoClicks] = useState(0);
  const [easterEgg, setEasterEgg] = useState(false);
  const clickTimerRef = useRef(null);

  const settings = useSettingsStore();
  const { toggle, setValue } = settings;
  const version = useAppVersion();

  useEffect(() => {
    if (!open) return;
    const handler = (e) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open, onClose]);

  useEffect(() => {
    if (!open) { setLogoClicks(0); setEasterEgg(false); }
    return () => clearTimeout(clickTimerRef.current);
  }, [open]);

  const handleThemeChange = (theme) => {
    document.documentElement.classList.add('theme-transitioning');
    setValue('theme', theme);
    document.documentElement.setAttribute('data-theme', theme);
    setTimeout(() => document.documentElement.classList.remove('theme-transitioning'), 600);
  };

  const handleLogoClick = () => {
    clearTimeout(clickTimerRef.current);
    const next = logoClicks + 1;
    setLogoClicks(next);
    if (next >= 10) { setEasterEgg(true); setLogoClicks(0); return; }
    clickTimerRef.current = setTimeout(() => setLogoClicks(0), 3000);
  };

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div className="settings-overlay" variants={overlayV} initial="hidden" animate="visible" exit="exit" onClick={onClose} />
          <motion.div className="settings-modal-wrap" variants={modalV} initial="hidden" animate="visible" exit="exit">
            <Glass depth={3} radius={28} className="settings-modal">
              <div className="settings-modal__header">
                <div className="settings-modal__title">Настройки</div>
                <motion.button className="settings-modal__close" onClick={onClose} whileHover={{ rotate: 90 }} whileTap={{ scale: 0.85 }} transition={{ type: 'spring', stiffness: 400, damping: 20 }}>
                  <X size={16} strokeWidth={2} />
                </motion.button>
              </div>

              <TabBar tabs={TABS} activeTab={tab} onTabChange={setTab} />

              <motion.div className="settings-modal__body" layout="size" transition={{ layout: { duration: 0.25, ease: [0.4, 0, 0.2, 1] } }}>
                <AnimatePresence mode="wait">
                  <motion.div key={tab} variants={tabV} initial="initial" animate="animate" exit="exit">
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
              </motion.div>
            </Glass>
          </motion.div>
          <FeedbackScreen open={feedbackOpen} onClose={() => setFeedbackOpen(false)} />
        </>
      )}
    </AnimatePresence>
  );
}

// ═══════ TABS ═══════

function GeneralTab({ settings, toggle, setValue }) {
  return (
    <div className="settings-section">
      <LiquidToggle icon={<Volume2 size={16} />} label="Звуки" hint="Звуки уведомлений и интерфейса" value={settings.sounds} onChange={() => toggle('sounds')} />
      <div className="settings-sep" />
      <LiquidToggle icon={<Monitor size={16} />} label="Анимированный фон" hint="Цветные шары на фоне" value={settings.animatedBg} onChange={() => toggle('animatedBg')} />
      <div className="settings-sep" />
      <SettingSelect icon={<Globe size={16} />} label="Язык" hint="Язык интерфейса" value={settings.language} options={[{ value: 'ru', label: 'Русский' }, { value: 'en', label: 'English' }]} onChange={(v) => setValue('language', v)} />
    </div>
  );
}

function AppearanceTab({ settings, toggle, setValue, handleThemeChange }) {
  return (
    <div className="settings-section">
      <div className="setting-row">
        <div className="setting-row__left">
          <div className="setting-row__icon"><Palette size={16} /></div>
          <div className="setting-row__info"><div className="setting-row__label">Тема</div></div>
        </div>
        <div className="setting-theme-pills">
          <button className={`setting-theme-pill ${settings.theme === 'dark' ? 'setting-theme-pill--active' : ''}`} onClick={() => handleThemeChange('dark')}><Moon size={12} /> Тёмная</button>
          <button className={`setting-theme-pill ${settings.theme === 'light' ? 'setting-theme-pill--active' : ''}`} onClick={() => handleThemeChange('light')}><Sun size={12} /> Светлая</button>
        </div>
      </div>
      <div className="settings-sep" />
      <div className="setting-row">
        <div className="setting-row__left">
          <div className="setting-row__icon"><Palette size={16} /></div>
          <div className="setting-row__info"><div className="setting-row__label">Акцентный цвет</div><div className="setting-row__hint">Основной цвет интерфейса</div></div>
        </div>
        <div className="setting-colors">
          {ACCENT_COLORS.map((c) => (
            <motion.button key={c.color} className={`setting-color-dot ${settings.accentColor === c.color ? 'setting-color-dot--active' : ''}`} style={{ '--dot-color': c.color }} onClick={() => setValue('accentColor', c.color)} title={c.label} whileHover={{ scale: 1.2 }} whileTap={{ scale: 0.9 }} />
          ))}
        </div>
      </div>
      <div className="settings-sep" />
      <LiquidToggle icon={<MessageSquare size={16} />} label="Компактные сообщения" hint="Меньше отступов между сообщениями" value={settings.compactMessages} onChange={() => toggle('compactMessages')} />
      <div className="settings-sep" />
      <div className="setting-row">
        <div className="setting-row__left">
          <div className="setting-row__icon" style={{ fontSize: 14, fontWeight: 800 }}>A</div>
          <div className="setting-row__info"><div className="setting-row__label">Размер шрифта</div><div className="setting-row__hint">{settings.fontSize}px</div></div>
        </div>
        <input type="range" className="setting-slider" min={12} max={18} step={1} value={settings.fontSize} onChange={(e) => setValue('fontSize', Number(e.target.value))} />
      </div>
    </div>
  );
}

function ChatTab({ settings, toggle, setValue }) {
  return (
    <div className="settings-section">
      <SettingSelect icon={<MessageSquare size={16} />} label="Стиль сообщений" hint="Внешний вид сообщений" value={settings.chatBubbleStyle} options={[{ value: 'bubbles', label: 'Пузыри' }, { value: 'minimal', label: 'Минимал' }, { value: 'classic', label: 'Классик' }]} onChange={(v) => setValue('chatBubbleStyle', v)} />
      <div className="settings-sep" />
      <LiquidToggle icon={<Users size={16} />} label="Аватары в чате" hint="Показывать аватары рядом с сообщениями" value={settings.showAvatarsInChat} onChange={() => toggle('showAvatarsInChat')} />
      <div className="settings-sep" />
      <SettingSelect icon={<Clock size={16} />} label="Формат времени" hint="Отображение времени в сообщениях" value={settings.timeFormat} options={[{ value: '24h', label: '24 часа' }, { value: '12h', label: '12 часов' }]} onChange={(v) => setValue('timeFormat', v)} />
      <div className="settings-sep" />
      <LiquidToggle icon={<MessageSquare size={16} />} label="Enter = отправка" hint="Enter отправляет, Shift+Enter — новая строка" value={settings.enterToSend} onChange={() => toggle('enterToSend')} />
    </div>
  );
}

function NotificationsTab({ settings, toggle }) {
  return (
    <div className="settings-section">
      <LiquidToggle icon={<Bell size={16} />} label="Уведомления" hint="Показывать уведомления" value={settings.notifications} onChange={() => toggle('notifications')} accent />
      <div className="settings-sep" />
      <LiquidToggle icon={<MessageSquare size={16} />} label="Сообщения" hint="Уведомления о новых сообщениях" value={settings.notifMessages} onChange={() => toggle('notifMessages')} />
      <div className="settings-sep" />
      <LiquidToggle icon={<Users size={16} />} label="Друзья" hint="Заявки в друзья" value={settings.notifFriends} onChange={() => toggle('notifFriends')} />
      <div className="settings-sep" />
      <LiquidToggle icon={<AtSign size={16} />} label="Упоминания" hint="Когда вас @упоминают" value={settings.notifMentions} onChange={() => toggle('notifMentions')} />
      <div className="settings-sep" />
      <LiquidToggle icon={<Volume2 size={16} />} label="Звук уведомлений" hint="Воспроизводить звук" value={settings.sounds} onChange={() => toggle('sounds')} />
    </div>
  );
}

function VoiceTab() {
  return <div className="settings-section"><VoiceSettings /></div>;
}

function PrivacyTab({ settings, toggle }) {
  return (
    <div className="settings-section">
      <LiquidToggle icon={<Eye size={16} />} label="Показывать онлайн" hint="Другие видят что вы в сети" value={settings.showOnline} onChange={() => toggle('showOnline')} />
      <div className="settings-sep" />
      <LiquidToggle icon={<MessageSquare size={16} />} label="Индикатор набора" hint="Другие видят когда вы печатаете" value={settings.showTyping} onChange={() => toggle('showTyping')} />
      <div className="settings-sep" />
      <LiquidToggle icon={<Shield size={16} />} label="Сквозное шифрование" hint="Личные сообщения шифруются на устройстве" value={settings.e2eEnabled} onChange={() => toggle('e2eEnabled')} accent />
      <div className="settings-sep" />
      <LiquidToggle
        icon={<Clock size={16} />} label="Время последнего визита" hint="Другие видят когда вы были в сети"
        value={settings.showLastSeen}
        onChange={() => {
          const next = !settings.showLastSeen;
          toggle('showLastSeen');
          fetch(`${API_URL}/api/users/me`, {
            method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('token')}` },
            body: JSON.stringify({ showLastSeen: next }),
          }).then(r => { if (!r.ok) toggle('showLastSeen'); }).catch(() => { toggle('showLastSeen'); });
        }}
      />
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
    <div className="settings-section">
      {Object.entries(HOTKEY_LABELS).map(([action, label]) => (
        <div key={action}>
          <div className="setting-row" onClick={() => setEditing(editing === action ? null : action)}>
            <div className="setting-row__left">
              <div className="setting-row__icon"><Keyboard size={16} /></div>
              <div className="setting-row__info"><div className="setting-row__label">{label}</div></div>
            </div>
            <div className={`setting-hotkey ${editing === action ? 'setting-hotkey--editing' : ''}`}>
              {editing === action ? 'Нажмите...' : hotkeys[action] || '—'}
            </div>
          </div>
          <div className="settings-sep" />
        </div>
      ))}
      <div className="setting-row" onClick={resetHotkeys}>
        <div className="setting-row__left">
          <div className="setting-row__icon"><RotateCcw size={16} /></div>
          <div className="setting-row__info"><div className="setting-row__label">Сбросить горячие клавиши</div><div className="setting-row__hint">Вернуть стандартные</div></div>
        </div>
      </div>
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

  const resetSettings = () => {
    localStorage.removeItem('blesk-settings');
    localStorage.removeItem('blesk-hotkeys');
    window.location.reload();
  };

  return (
    <div className="settings-section">
      <div className="setting-row">
        <div className="setting-row__left">
          <div className="setting-row__icon"><HardDrive size={16} /></div>
          <div className="setting-row__info">
            <div className="setting-row__label">Кеш сообщений</div>
            <div className="setting-row__hint">{chats.length} чатов, {msgCount} сообщений в памяти</div>
          </div>
        </div>
        <button className="setting-action-btn" onClick={clearMessages}>{cleared === 'messages' ? 'Очищено' : 'Очистить'}</button>
      </div>
      <div className="settings-sep" />
      <div className="setting-row">
        <div className="setting-row__left">
          <div className="setting-row__icon"><Download size={16} /></div>
          <div className="setting-row__info">
            <div className="setting-row__label">Позиции карточек</div>
            <div className="setting-row__hint">Сохранённые позиции в Nebula</div>
          </div>
        </div>
        <button className="setting-action-btn" onClick={clearNebula}>{cleared === 'nebula' ? 'Очищено' : 'Сбросить'}</button>
      </div>
      <div className="settings-sep" />
      <div className="setting-row" onClick={resetSettings}>
        <div className="setting-row__left">
          <div className="setting-row__icon" style={{ color: '#ef4444' }}><Trash2 size={16} /></div>
          <div className="setting-row__info">
            <div className="setting-row__label" style={{ color: '#ef4444' }}>Сбросить все настройки</div>
            <div className="setting-row__hint">Перезагрузит приложение</div>
          </div>
        </div>
      </div>
    </div>
  );
}

function AccessibilityTab({ settings, toggle }) {
  return (
    <div className="settings-section">
      <LiquidToggle icon={<Accessibility size={16} />} label="Уменьшить движение" hint="Отключить все анимации и переходы" value={settings.reducedMotion} onChange={() => toggle('reducedMotion')} />
      <div className="settings-sep" />
      <LiquidToggle icon={<Eye size={16} />} label="Высокий контраст" hint="Увеличить контрастность текста и границ" value={settings.highContrast} onChange={() => toggle('highContrast')} />
      <div className="settings-sep" />
      <LiquidToggle icon={<Accessibility size={16} />} label="Увеличенные элементы" hint="Кнопки и текст крупнее для удобства" value={settings.largeControls} onChange={() => toggle('largeControls')} />
    </div>
  );
}

function AboutTab({ version, logoClicks, easterEgg, handleLogoClick, setFeedbackOpen }) {
  return (
    <div className="settings-section settings-about">
      <motion.div className={`settings-about__logo ${easterEgg ? 'settings-about__logo--party' : ''}`} onClick={handleLogoClick} whileHover={{ scale: 1.06 }} whileTap={{ scale: 0.92, rotate: -4 }} title={logoClicks > 5 ? `${10 - logoClicks}...` : undefined}>
        <img className="settings-about__logo-img" src="./blesk.png" alt="blesk" onError={(e) => { e.target.style.display = 'none'; if (e.target.nextSibling) e.target.nextSibling.style.display = 'flex'; }} />
        <span className="settings-about__logo-fallback" style={{ display: 'none' }}>bl</span>
        {easterEgg && <div className="settings-about__sparks">{Array.from({ length: 12 }).map((_, i) => <div key={i} className="settings-about__spark" style={{ '--angle': `${i * 30}deg`, '--delay': `${i * 0.05}s` }} />)}</div>}
      </motion.div>
      {logoClicks > 0 && !easterEgg && (
        <div className="settings-about__progress">{Array.from({ length: 10 }).map((_, i) => <motion.div key={i} className={`settings-about__progress-dot ${i < logoClicks ? 'settings-about__progress-dot--filled' : ''}`} animate={i < logoClicks ? { scale: [1, 1.4, 1] } : {}} transition={{ duration: 0.2 }} />)}</div>
      )}
      <div className="settings-about__name">blesk</div>
      <div className="settings-about__version">v{version}</div>
      <div className="settings-about__slogan">Твой блеск. Твои правила.</div>
      <AnimatePresence>
        {easterEgg && <motion.div className="settings-about__easter" initial={{ opacity: 0, scale: 0.8 }} animate={{ opacity: 1, scale: 1 }} transition={{ type: 'spring', damping: 15 }}><PartyPopper size={24} strokeWidth={1.5} /><span>Ты нашёл секрет! Ты — настоящий блеск.</span></motion.div>}
      </AnimatePresence>
      <div className="settings-about__links">
        <a className="settings-about__link" href="https://github.com/gotblesk/blesk" target="_blank" rel="noopener noreferrer"><Github size={14} /> GitHub <ExternalLink size={10} /></a>
        <a className="settings-about__link" href="https://blesk.fun" target="_blank" rel="noopener noreferrer"><Globe size={14} /> blesk.fun <ExternalLink size={10} /></a>
      </div>
      <button className="settings-about__feedback" onClick={() => setFeedbackOpen(true)}><MessageCircle size={15} /> Обратная связь</button>
      <div className="settings-about__license">AGPL-3.0 License</div>
    </div>
  );
}

// ═══ Tab Bar with arrows + wheel scroll ═══

function TabBar({ tabs, activeTab, onTabChange }) {
  const scrollRef = useRef(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const checkScroll = useCallback(() => {
    const el = scrollRef.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 4);
    setCanScrollRight(el.scrollLeft < el.scrollWidth - el.clientWidth - 4);
  }, []);

  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    checkScroll();
    el.addEventListener('scroll', checkScroll, { passive: true });
    return () => el.removeEventListener('scroll', checkScroll);
  }, [checkScroll]);

  // Mouse wheel → horizontal scroll
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const onWheel = (e) => {
      if (Math.abs(e.deltaY) > Math.abs(e.deltaX)) {
        e.preventDefault();
        el.scrollBy({ left: e.deltaY * 2, behavior: 'smooth' });
      }
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, []);

  const scroll = (dir) => {
    scrollRef.current?.scrollBy({ left: dir * 150, behavior: 'smooth' });
  };

  return (
    <div className="settings-tabs-wrap">
      <button className={`settings-tabs-arrow ${!canScrollLeft ? 'settings-tabs-arrow--hidden' : ''}`} onClick={() => scroll(-1)}>
        <ChevronLeft size={16} strokeWidth={2.5} />
      </button>
      <div className="settings-tabs" ref={scrollRef}>
        {tabs.map((t) => (
          <button
            key={t.id}
            className={`settings-tab ${activeTab === t.id ? 'settings-tab--active' : ''}`}
            onClick={(e) => { onTabChange(t.id); e.currentTarget.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' }); }}
          >
            {t.label}
            {activeTab === t.id && <motion.div className="settings-tab__indicator" layoutId="tabIndicator" transition={{ type: 'spring', stiffness: 400, damping: 30 }} />}
          </button>
        ))}
      </div>
      <button className={`settings-tabs-arrow ${!canScrollRight ? 'settings-tabs-arrow--hidden' : ''}`} onClick={() => scroll(1)}>
        <ChevronRight size={16} strokeWidth={2.5} />
      </button>
    </div>
  );
}

// ═══ Components ═══

function LiquidToggle({ icon, label, hint, value, onChange, accent }) {
  return (
    <div className="setting-row" onClick={onChange}>
      <div className="setting-row__left">
        {icon && <div className="setting-row__icon">{icon}</div>}
        <div className="setting-row__info"><div className="setting-row__label">{label}</div>{hint && <div className="setting-row__hint">{hint}</div>}</div>
      </div>
      <div className={`liquid-toggle ${value ? 'liquid-toggle--on' : ''} ${accent ? 'liquid-toggle--accent' : ''}`}>
        <div className="liquid-toggle__blob" />
        <div className="liquid-toggle__thumb" />
      </div>
    </div>
  );
}

function SettingSelect({ icon, label, hint, value, options, onChange }) {
  return (
    <div className="setting-row">
      <div className="setting-row__left">
        {icon && <div className="setting-row__icon">{icon}</div>}
        <div className="setting-row__info"><div className="setting-row__label">{label}</div>{hint && <div className="setting-row__hint">{hint}</div>}</div>
      </div>
      <select className="setting-select" value={value} onChange={(e) => onChange(e.target.value)}>
        {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </div>
  );
}
