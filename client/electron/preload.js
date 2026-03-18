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

  // E2E шифрование — безопасное хранение ключей
  crypto: {
    saveSecretKey: (b64) => ipcRenderer.invoke('crypto:saveSecretKey', b64),
    getSecretKey: () => ipcRenderer.invoke('crypto:getSecretKey'),
    hasSecretKey: () => ipcRenderer.invoke('crypto:hasSecretKey'),
  },

  // Платформа
  platform: process.platform,
});
