const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('splashApi', {
  ready: () => ipcRenderer.send('splash:ready'),
  // [CRIT-1] Async invoke вместо блокирующего sendSync
  version: () => ipcRenderer.invoke('app:version'),
  // [CRIT-2] Принять команду expand-out от main process
  onExpandOut: (cb) => ipcRenderer.on('splash:expand-out', cb),
});
