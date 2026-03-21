const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('splashApi', {
  ready: () => ipcRenderer.send('splash:ready'),
  version: () => ipcRenderer.sendSync('get-version'),
});
