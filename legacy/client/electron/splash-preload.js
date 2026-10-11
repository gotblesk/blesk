const { contextBridge, ipcRenderer } = require('electron');

// Обработчик expand-out с очисткой при закрытии окна
const expandOutHandler = (_, data) => {
  window.dispatchEvent(new CustomEvent('splash-expand-out', { detail: data }));
};
ipcRenderer.on('splash:expand-out', expandOutHandler);

window.addEventListener('beforeunload', () => {
  ipcRenderer.removeListener('splash:expand-out', expandOutHandler);
});

contextBridge.exposeInMainWorld('splashApi', {
  ready: () => ipcRenderer.send('splash:ready'),
  retry: () => ipcRenderer.send('splash:retry'),
  // [CRIT-1] Async invoke вместо блокирующего sendSync
  version: () => ipcRenderer.invoke('app:version'),
  // [CRIT-2] Принять команду expand-out от main process
  onExpandOut: (cb) => {
    window.addEventListener('splash-expand-out', (e) => cb(e.detail));
  },
});
