const { contextBridge, ipcRenderer } = require('electron');

// Безопасный мост между renderer и main process
// Используем off() для конкретных хэндлеров вместо removeAllListeners

// Храним ссылки на текущие хэндлеры для корректной отписки
let _maxHandler = null;
let _unMaxHandler = null;
let _updateAvailHandler = null;
let _updateProgressHandler = null;
let _updateDownloadedHandler = null;

contextBridge.exposeInMainWorld('blesk', {
  // Управление окном
  window: {
    minimize: () => ipcRenderer.send('window:minimize'),
    maximize: () => ipcRenderer.send('window:maximize'),
    close: () => ipcRenderer.send('window:close'),
    // Ручное перетаскивание (обход бага -webkit-app-region: drag)
    startDrag: (offsetX, offsetY) => ipcRenderer.send('window:start-drag', offsetX, offsetY),
    dragging: (screenX, screenY) => ipcRenderer.send('window:dragging', screenX, screenY),
    stopDrag: () => ipcRenderer.send('window:stop-drag'),
    onMaximizeChange: (callback) => {
      // Снять предыдущие хэндлеры (не трогая чужие)
      if (_maxHandler) ipcRenderer.off('window:maximized', _maxHandler);
      if (_unMaxHandler) ipcRenderer.off('window:unmaximized', _unMaxHandler);
      _maxHandler = () => callback(true);
      _unMaxHandler = () => callback(false);
      ipcRenderer.on('window:maximized', _maxHandler);
      ipcRenderer.on('window:unmaximized', _unMaxHandler);
    },
  },

  // Версия приложения
  getVersion: () => ipcRenderer.invoke('app:version'),

  // Автозапуск
  getAutoStart: () => ipcRenderer.invoke('app:getAutoStart'),
  setAutoStart: (enabled) => ipcRenderer.invoke('app:setAutoStart', enabled),

  // Кеш
  clearCache: () => ipcRenderer.invoke('app:clearCache'),

  // Проверка обновлений
  checkForUpdates: () => ipcRenderer.invoke('app:checkForUpdates'),

  // Обновления
  update: {
    onAvailable: (callback) => {
      if (_updateAvailHandler) ipcRenderer.off('update:available', _updateAvailHandler);
      _updateAvailHandler = (_, version) => callback(version);
      ipcRenderer.on('update:available', _updateAvailHandler);
    },
    onProgress: (callback) => {
      if (_updateProgressHandler) ipcRenderer.off('update:progress', _updateProgressHandler);
      _updateProgressHandler = (_, data) => callback(data);
      ipcRenderer.on('update:progress', _updateProgressHandler);
    },
    onDownloaded: (callback) => {
      if (_updateDownloadedHandler) ipcRenderer.off('update:downloaded', _updateDownloadedHandler);
      _updateDownloadedHandler = () => callback();
      ipcRenderer.on('update:downloaded', _updateDownloadedHandler);
    },
    install: () => ipcRenderer.send('update:install'),
  },

  // Демонстрация экрана (desktopCapturer)
  screen: {
    getSources: () => ipcRenderer.invoke('screen:getSources'),
  },

  // Авто-логин — refresh token через safeStorage
  auth: {
    saveRefreshToken: (token) => ipcRenderer.invoke('auth:saveRefreshToken', token),
    getRefreshToken: () => ipcRenderer.invoke('auth:getRefreshToken'),
    clearRefreshToken: () => ipcRenderer.invoke('auth:clearRefreshToken'),
  },

  // E2E шифрование — безопасное хранение ключей (legacy + blesk Shield)
  crypto: {
    // Legacy identity key
    saveSecretKey: (b64) => ipcRenderer.invoke('crypto:saveSecretKey', b64),
    getSecretKey: () => ipcRenderer.invoke('crypto:getSecretKey'),
    hasSecretKey: () => ipcRenderer.invoke('crypto:hasSecretKey'),
    // blesk Shield — signing key (Ed25519)
    saveSigningKey: (b64) => ipcRenderer.invoke('crypto:saveSigningKey', b64),
    getSigningKey: () => ipcRenderer.invoke('crypto:getSigningKey'),
    hasSigningKey: () => ipcRenderer.invoke('crypto:hasSigningKey'),
    // blesk Shield — signed prekey (SPK)
    saveSPK: (json) => ipcRenderer.invoke('crypto:saveSPK', json),
    getSPK: () => ipcRenderer.invoke('crypto:getSPK'),
    // blesk Shield — sessions per peer
    saveSession: (peerId, json) => ipcRenderer.invoke('crypto:saveSession', peerId, json),
    getSession: (peerId) => ipcRenderer.invoke('crypto:getSession', peerId),
    deleteSession: (peerId) => ipcRenderer.invoke('crypto:deleteSession', peerId),
    listSessions: () => ipcRenderer.invoke('crypto:listSessions'),
    // blesk Shield — one-time prekeys
    saveOPKs: (json) => ipcRenderer.invoke('crypto:saveOPKs', json),
    getOPKs: () => ipcRenderer.invoke('crypto:getOPKs'),
    // Очистка всех ключей при выходе из аккаунта
    clearAll: () => ipcRenderer.invoke('crypto:clearAll'),
  },

  // Безопасное открытие внешних ссылок (только http/https)
  openExternal: (url) => {
    if (typeof url === 'string' && (url.startsWith('https://') || url.startsWith('http://'))) {
      ipcRenderer.send('open-external', url);
    }
  },

  // [IMP-4] Системные уведомления (через main process — работают в трее)
  notify: (title, body, chatId, silent) => {
    ipcRenderer.send('notification:show', { title, body, chatId, silent: !!silent });
  },

  // Badge (непрочитанные в таскбаре)
  setBadge: (count) => {
    ipcRenderer.send('badge:update', count);
  },

  // DND sync из renderer → tray menu
  syncDnd: (enabled) => {
    ipcRenderer.send('dnd:sync', enabled);
  },

  // [CRIT-2] Подписки с deregister — предотвращение утечки listeners
  onDeepLink: (() => {
    let prev = null;
    return (cb) => {
      if (prev) ipcRenderer.off('deeplink:action', prev);
      prev = (_, data) => cb(data);
      ipcRenderer.on('deeplink:action', prev);
    };
  })(),

  onNotificationOpenChat: (() => {
    let prev = null;
    return (cb) => {
      if (prev) ipcRenderer.off('notification:open-chat', prev);
      prev = (_, chatId) => cb(chatId);
      ipcRenderer.on('notification:open-chat', prev);
    };
  })(),

  onDnd: (() => {
    let prev = null;
    return (cb) => {
      if (prev) ipcRenderer.off('tray:dnd', prev);
      prev = (_, enabled) => cb(enabled);
      ipcRenderer.on('tray:dnd', prev);
    };
  })(),

  // Платформа
  platform: process.platform,
});
