'use strict';
const { app, BrowserWindow, ipcMain, dialog, net } = require('electron');
const path = require('path');
const fs = require('fs');
const fsp = fs.promises;
const crypto = require('crypto');

// Hardware media keys + Windows media overlay
app.commandLine.appendSwitch('enable-features', 'HardwareMediaKeyHandling,MediaSessionService');

const EXT = new Set(['.mp3', '.flac', '.wav', '.m4a', '.aac', '.ogg', '.opus', '.wma', '.aiff', '.aif', '.ape', '.wv']);
let win;
const libFile = () => path.join(app.getPath('userData'), 'library.json');
const coverDir = () => path.join(app.getPath('userData'), 'covers');

function createWindow() {
  win = new BrowserWindow({
    width: 1180, height: 820, minWidth: 380, minHeight: 600,
    backgroundColor: '#f2f2f4', autoHideMenuBar: true, title: 'JPEG',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true, nodeIntegration: false,
      webSecurity: false,                    // lets Web Audio (EQ, crossfade) read local files and radio streams
      autoplayPolicy: 'no-user-gesture-required',
      backgroundThrottling: false
    }
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

app.whenReady().then(() => {
  fs.mkdirSync(coverDir(), { recursive: true });
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });

// ───────── library scanning ─────────
async function walk(dir, out) {
  let ents;
  try { ents = await fsp.readdir(dir, { withFileTypes: true }); } catch (e) { return; }
  for (const e of ents) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (!e.name.startsWith('.') && e.name !== '$RECYCLE.BIN' && e.name !== 'System Volume Information') await walk(p, out);
    } else if (EXT.has(path.extname(e.name).toLowerCase())) out.push(p);
  }
}

async function readSong(p, st, cached) {
  if (cached && cached.mtime === st.mtimeMs && cached.size === st.size) return cached;
  const mm = require('music-metadata');
  try {
    const meta = await mm.parseFile(p, { duration: true });
    const c = meta.common, f = meta.format;
    let cover = '';
    const pic = mm.selectCover(c.picture);
    if (pic) {
      const h = crypto.createHash('sha1').update(pic.data).digest('hex');
      const cp = path.join(coverDir(), h + (/png/i.test(pic.format) ? '.png' : '.jpg'));
      if (!fs.existsSync(cp)) await fsp.writeFile(cp, pic.data);
      cover = cp;
    }
    return {
      id: crypto.createHash('sha1').update(p).digest('hex').slice(0, 16), path: p, mtime: st.mtimeMs, size: st.size,
      title: c.title || path.basename(p, path.extname(p)), artist: c.artist || c.albumartist || 'Unknown artist',
      album: c.album || 'Unknown album', track: (c.track && c.track.no) || 0, disc: (c.disc && c.disc.no) || 0,
      year: c.year || 0, duration: f.duration || 0, codec: f.codec || path.extname(p).slice(1).toUpperCase(),
      lossless: !!f.lossless, sampleRate: f.sampleRate || 0, bits: f.bitsPerSample || 0,
      bitrate: f.bitrate ? Math.round(f.bitrate / 1000) : 0, cover
    };
  } catch (e) { return null; }
}

async function scan(folders) {
  let old = [];
  try { old = JSON.parse(await fsp.readFile(libFile(), 'utf8')).songs || []; } catch (e) { }
  const cache = new Map(old.map(s => [s.path, s]));
  const files = [];
  for (const f of folders) await walk(f, files);
  const songs = [];
  let done = 0, i = 0;
  async function worker() {
    while (i < files.length) {
      const p = files[i++];
      try {
        const st = await fsp.stat(p);
        const s = await readSong(p, st, cache.get(p));
        if (s && s.duration >= 30) songs.push(s);
      } catch (e) { }
      done++;
      if (done % 10 === 0 || done === files.length) win && win.webContents.send('scan-progress', { done, total: files.length });
    }
  }
  await Promise.all(Array.from({ length: 6 }, worker));
  await fsp.writeFile(libFile(), JSON.stringify({ folders, songs }));
  return { folders, songs };
}

ipcMain.handle('lib:get', async () => {
  try { return JSON.parse(await fsp.readFile(libFile(), 'utf8')); } catch (e) { return { folders: [], songs: [] }; }
});
ipcMain.handle('lib:pick', async () => {
  const r = await dialog.showOpenDialog(win, { properties: ['openDirectory', 'multiSelections'], title: 'Choose your music folders' });
  return r.canceled ? [] : r.filePaths;
});
ipcMain.handle('lib:scan', (_e, folders) => scan(folders));
ipcMain.handle('net:json', async (_e, url) => {
  try {
    const r = await net.fetch(url, { headers: { 'User-Agent': 'JPEG-Music/1.0' } });
    const txt = await r.text();
    let data = null;
    try { data = JSON.parse(txt); } catch (e) { }
    return { ok: r.ok, status: r.status, data };
  } catch (e) { return { ok: false, status: 0, data: null }; }
});
ipcMain.handle('file:read', async (_e, p) => fsp.readFile(p));
