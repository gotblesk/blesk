import { create } from 'zustand';

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
    };
  },
}));
