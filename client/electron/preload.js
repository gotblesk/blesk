const { contextBridge, ipcRenderer } = require('electron');

// Безопасный мост между renderer и main process
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
      ipcRenderer.on('window:maximized', () => callback(true));
      ipcRenderer.on('window:unmaximized', () => callback(false));
    },
  },

  // Версия приложения
  getVersion: () => ipcRenderer.invoke('app:version'),

  // Платформа
  platform: process.platform,
});
