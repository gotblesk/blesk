import { create } from 'zustand';

// Горячие клавиши по умолчанию (пользователь может настроить)
const DEFAULT_HOTKEYS = {
  search: 'Ctrl+K',
  tabChats: 'Ctrl+1',
  tabVoice: 'Ctrl+2',
  tabChannels: 'Ctrl+3',
  tabFriends: 'Ctrl+4',
  toggleMute: 'Ctrl+Shift+M',
  settings: 'Ctrl+,',
};

function loadHotkeys() {
  try {
    const saved = localStorage.getItem('blesk-hotkeys');
    return saved ? { ...DEFAULT_HOTKEYS, ...JSON.parse(saved) } : { ...DEFAULT_HOTKEYS };
  } catch { return { ...DEFAULT_HOTKEYS }; }
}

// Значения по умолчанию
const DEFAULTS = {
  sounds: true,
  notifications: true,
  notifMessages: true,
  notifFriends: true,
  notifMentions: true,
  showOnline: true,
  showTyping: true,
  showLastSeen: true,
  theme: 'dark',
  animatedBg: true,
  compactMessages: false,
  e2eEnabled: true,
  accentColor: '#c8ff00',
  fontSize: 14,
  language: 'ru',
  launchOnStartup: false,
  // Чат
  chatBubbleStyle: 'bubbles', // 'bubbles' | 'minimal' | 'classic'
  showAvatarsInChat: true,
  timeFormat: '24h', // '12h' | '24h'
  enterToSend: true,
  // Видео качество
  cameraResolution: '720p', // '480p' | '720p' | '1080p' | '1440p'
  cameraFps: 30, // 30 | 60
  screenResolution: '1080p', // '720p' | '1080p' | '1440p'
  screenFps: 30, // 30 | 60
  // Доступность
  reducedMotion: false,
  highContrast: false,
  largeControls: false,
  // [CRIT-4] Не беспокоить
  dnd: false,
  // Масштаб интерфейса (0.8 — 1.2)
  uiZoom: 1,
};

// Загрузить из localStorage
function loadSettings() {
  try {
    const saved = localStorage.getItem('blesk-settings');
    return saved ? { ...DEFAULTS, ...JSON.parse(saved) } : { ...DEFAULTS };
  } catch {
    return { ...DEFAULTS };
  }
}

export const useSettingsStore = create((set, get) => ({
  ...loadSettings(),
  hotkeys: loadHotkeys(),

  // Установить горячую клавишу
  setHotkey: (action, combo) => {
    set((state) => {
      const next = { ...state.hotkeys, [action]: combo };
      localStorage.setItem('blesk-hotkeys', JSON.stringify(next));
      return { hotkeys: next };
    });
  },

  // Сбросить горячие клавиши на дефолт
  resetHotkeys: () => {
    localStorage.removeItem('blesk-hotkeys');
    set({ hotkeys: { ...DEFAULT_HOTKEYS } });
  },

  // Переключить boolean-настройку
  toggle: (key) => {
    set((state) => {
      const next = { ...state, [key]: !state[key] };
      const dataKeys = Object.keys(DEFAULTS);
      const data = {};
      for (const k of dataKeys) data[k] = next[k];
      localStorage.setItem('blesk-settings', JSON.stringify(data));
      return next;
    });
  },

  // Установить конкретное значение
  setValue: (key, value) => {
    set((state) => {
      const next = { ...state, [key]: value };
      const dataKeys = Object.keys(DEFAULTS);
      const data = {};
      for (const k of dataKeys) data[k] = next[k];
      localStorage.setItem('blesk-settings', JSON.stringify(data));
      return next;
    });
  },

  // Получить все настройки как объект (для совместимости)
  getAll: () => {
    const s = get();
    return {
      sounds: s.sounds,
      notifications: s.notifications,
      notifMessages: s.notifMessages,
      notifFriends: s.notifFriends,
      notifMentions: s.notifMentions,
      showOnline: s.showOnline,
      showTyping: s.showTyping,
      showLastSeen: s.showLastSeen,
      theme: s.theme,
      animatedBg: s.animatedBg,
      compactMessages: s.compactMessages,
      e2eEnabled: s.e2eEnabled,
    };
  },
}));
