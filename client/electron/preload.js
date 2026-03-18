const { contextBridge, ipcRenderer } = require('electron');

// Безопасный мост между renderer и main process
// Используем removeAllListeners перед подпиской — предотвращает накопление

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
      // Очистить старые слушатели перед подпиской
      ipcRenderer.removeAllListeners('window:maximized');
      ipcRenderer.removeAllListeners('window:unmaximized');
      ipcRenderer.on('window:maximized', () => callback(true));
      ipcRenderer.on('window:unmaximized', () => callback(false));
    },
  },

  // Версия приложения
  getVersion: () => ipcRenderer.invoke('app:version'),

  // Обновления
  update: {
    onAvailable: (callback) => {
      ipcRenderer.removeAllListeners('update:available');
      ipcRenderer.on('update:available', (_, version) => callback(version));
    },
    onProgress: (callback) => {
      ipcRenderer.removeAllListeners('update:progress');
      ipcRenderer.on('update:progress', (_, data) => callback(data));
    },
    onDownloaded: (callback) => {
      ipcRenderer.removeAllListeners('update:downloaded');
      ipcRenderer.on('update:downloaded', () => callback());
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
