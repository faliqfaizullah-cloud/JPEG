'use strict';
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('jpeg', {
  getLibrary: () => ipcRenderer.invoke('lib:get'),
  pickFolders: () => ipcRenderer.invoke('lib:pick'),
  scan: (folders) => ipcRenderer.invoke('lib:scan', folders),
  onScan: (cb) => ipcRenderer.on('scan-progress', (_e, p) => cb(p)),
  json: (url) => ipcRenderer.invoke('net:json', url),
  readFile: (p) => ipcRenderer.invoke('file:read', p)
});
