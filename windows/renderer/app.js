'use strict';
/* JPEG for Windows: renderer. Library grid, release sheet, tracklist, waveform, lyric scene, discover, playlists, studio. */
const $ = (s, r = document) => r.querySelector(s);
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = s => { s = Math.max(0, s | 0); return String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0'); };
const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
const fileUrl = p => 'file:///' + String(p).replace(/\\/g, '/').split('/').map((seg, i) => (i === 0 && /^[A-Za-z]:$/.test(seg)) ? seg : encodeURIComponent(seg)).join('/');
const cssUrl = u => String(u).replace(/'/g, '%27');
const artUrl = s => s ? (s.cover ? fileUrl(s.cover) : (s.art || '')) : '';
const bgStyle = u => u ? `style="background-image:url('${cssUrl(u)}')"` : '';
const isHires = s => s.lossless && (s.sampleRate > 48000 || s.bits >= 24);

// ───────── icons ─────────
const IC = {
  grid: '<rect x="3.5" y="3.5" width="7" height="7" rx="1.8"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.8"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.8"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.8"/>',
  radio: '<circle cx="12" cy="12" r="1.8"/><g fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M8 8.3a5.7 5.7 0 0 0 0 7.4M16 8.3a5.7 5.7 0 0 1 0 7.4M5 5.5a9.8 9.8 0 0 0 0 13M19 5.5a9.8 9.8 0 0 1 0 13"/></g>',
  search: '<g fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m15.5 15.5 5 5"/></g>',
  play: '<path d="M7 4.5v15l13-7.5z"/>',
  pause: '<rect x="6" y="4" width="4.2" height="16" rx="1.2"/><rect x="13.8" y="4" width="4.2" height="16" rx="1.2"/>',
  close: '<g fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></g>',
  stack: '<g fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"><path d="M12 2.8l8.4 4.8v8.8L12 21.2l-8.4-4.8V7.6z"/><path d="M12 12L3.6 7.6M12 12l8.4-4.4M12 12v9.2"/></g>',
  heart: '<path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>',
  heartO: '<path fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" d="M12 20.3l-1.2-1.1C5.8 14.7 3 12.1 3 8.8 3 6.2 5 4.2 7.5 4.2c1.6 0 3.1.8 4.5 2.3 1.4-1.5 2.9-2.3 4.5-2.3C19 4.2 21 6.2 21 8.8c0 3.3-2.8 5.9-7.8 10.4z"/>',
  dots: '<circle cx="5" cy="12" r="1.6"/><circle cx="12" cy="12" r="1.6"/><circle cx="19" cy="12" r="1.6"/>',
  back: '<g fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></g>'
};
const ico = (k, s = 24) => `<svg class="i" viewBox="0 0 24 24" width="${s}" height="${s}" fill="currentColor">${IC[k]}</svg>`;
const CRATE = '<svg viewBox="0 0 64 64" width="38" height="38"><rect x="7.7" y="5" width="48.6" height="23" rx="3" fill="#1b1b1d"/>' +
  ['#ededed', '#9a9a9a', '#ededed', '#6b6b6b', '#ededed', '#9a9a9a'].map((c, i) => `<rect x="${10.9 + i * 7.4}" y="7.7" width="4.5" height="16.6" fill="${c}"/>`).join('') +
  '<rect x="3.8" y="21.8" width="56.4" height="37" rx="5" fill="#86e048"/>' +
  [0, 1].map(r => [0, 1, 2].map(k => `<rect x="${9 + k * 17.3}" y="${28.2 + r * 14}" width="12.8" height="9" rx="2" fill="#2e7d1e"/>`).join('')).join('') + '</svg>';

// ───────── preferences ─────────
const DEF = { accent: '#111113', vinyl: '#0e0e0e', glass: { style: 0, blur: 24, tint: .55, border: .7, shadow: .18, refract: 18 },
  eq: [0, 0, 0, 0, 0], bass: 0, cf: 0, rate: 33.3, pure: false, shuffle: false, repeat: false, volume: .9 };
let P;
try { P = Object.assign({}, DEF, JSON.parse(localStorage.getItem('jpeg.prefs') || '{}')); } catch (e) { P = Object.assign({}, DEF); }
P.glass = Object.assign({}, DEF.glass, P.glass || {});
const savePrefs = () => localStorage.setItem('jpeg.prefs', JSON.stringify(P));
let PL;
try { PL = JSON.parse(localStorage.getItem('jpeg.playlists') || 'null'); } catch (e) { PL = null; }
if (!PL || !PL.Favorites) PL = Object.assign({ Favorites: [] }, PL || {});
const savePL = () => localStorage.setItem('jpeg.playlists', JSON.stringify(PL));
const lum = hex => { const n = parseInt(hex.slice(1), 16); return (0.2126 * ((n >> 16) & 255) + 0.7152 * ((n >> 8) & 255) + 0.0722 * (n & 255)) / 255; };
function applyTheme() {
  const r = document.documentElement.style, g = P.glass;
  r.setProperty('--accent', P.accent); r.setProperty('--on', lum(P.accent) > .45 ? '#111113' : '#fff'); r.setProperty('--vinyl', P.vinyl);
  r.setProperty('--blur', g.blur + 'px'); r.setProperty('--tint', g.tint); r.setProperty('--border', g.border); r.setProperty('--shadow', g.shadow);
  r.setProperty('--spin', (60 / (P.pure ? 33.3 : P.rate)).toFixed(3) + 's');
  document.body.className = ['g-default', 'g-frost', 'g-liquid'][g.style] || 'g-default';
  const d = $('#disp'); if (d) d.setAttribute('scale', g.refract);
}

// ───────── state & library ─────────
const S = { songs: [], albums: [], byId: new Map(), albumOf: new Map(), folders: [], tab: 'library', queue: [], index: -1, mode: 'deck', fading: false,
  disc: { mode: 'music', q: '', results: [], busy: false, fail: false }, search: { q: '', mode: 'songs' }, plOpen: null };
const sideCache = new Map();
function sideInfo(tracks, key) {
  if (sideCache.has(key + tracks.length)) return sideCache.get(key + tracks.length);
  const total = Math.max(1, tracks.reduce((a, t) => a + (t.duration || 0), 0));
  const n = tracks.length < 3 ? 1 : Math.max(2, Math.ceil(total / (22 * 60)));
  const target = total / n; let cum = 0; const counts = {};
  const out = tracks.map(t => {
    const mid = cum + (t.duration || 0) / 2; cum += t.duration || 0;
    const si = Math.min(n - 1, Math.floor(mid / target), 25);
    counts[si] = (counts[si] || 0) + 1;
    return { si, side: String.fromCharCode(65 + si), code: String.fromCharCode(65 + si) + counts[si] };
  });
  sideCache.set(key + tracks.length, out); return out;
}
function buildAlbums() {
  const groups = new Map();
  for (const s of S.songs) { const k = (s.album || 'Unknown album').toLowerCase(); if (!groups.has(k)) groups.set(k, []); groups.get(k).push(s); }
  const tk = t => ((t.disc || 0) * 1000 + (t.track || 0)) || 1e9;
  S.albums = [...groups].map(([k, ts]) => {
    ts.sort((a, b) => tk(a) - tk(b) || a.title.localeCompare(b.title));
    const artists = [...new Set(ts.map(t => t.artist))];
    const c = ts.find(t => t.cover);
    return { key: k, title: ts[0].album || 'Unknown album', artist: artists.length === 1 ? artists[0] : 'Various artists', tracks: ts, cover: c ? c.cover : '' };
  }).sort((a, b) => a.title.localeCompare(b.title));
  S.byId = new Map(S.songs.map(s => [s.id, s]));
  S.albumOf = new Map(); for (const a of S.albums) for (const t of a.tracks) S.albumOf.set(t.id, a);
  sideCache.clear();
}
const cur = () => S.queue[S.index] || null;
const plSongs = n => (PL[n] || []).map(id => S.byId.get(id)).filter(Boolean);
const isFav = s => !!s && PL.Favorites.includes(s.id);
function toggleFav(s) { if (!s || s.online) return; const l = PL.Favorites, i = l.indexOf(s.id); if (i >= 0) l.splice(i, 1); else l.push(s.id); savePL(); changed(); }

function toast(m) { const t = $('#toast'); t.textContent = m; t.classList.add('on'); clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.remove('on'), 2400); }

// ───────── audio engine (Web Audio: EQ, crossfade, speed) ─────────
const AC = new (window.AudioContext || window.webkitAudioContext)();
const master = AC.createGain();
const bands = [60, 230, 910, 3600, 14000].map((f, i) => { const b = AC.createBiquadFilter(); b.type = i === 0 ? 'lowshelf' : i === 4 ? 'highshelf' : 'peaking'; b.frequency.value = f; b.Q.value = 1; return b; });
const bassF = AC.createBiquadFilter(); bassF.type = 'lowshelf'; bassF.frequency.value = 120;
bands.reduce((a, b) => { a.connect(b); return b; }); bands[4].connect(bassF); bassF.connect(master); master.connect(AC.destination);
const streamEl = new Audio(); streamEl.preload = 'none';
const CURVE_OUT = Float32Array.from({ length: 64 }, (_, i) => Math.cos(i / 63 * Math.PI / 2));
const CURVE_IN = Float32Array.from({ length: 64 }, (_, i) => Math.sin(i / 63 * Math.PI / 2));
let curDeck = 0, fadeTimer = null, sleepTimer = null;
const mkDeck = () => {
  const el = new Audio(); el.preload = 'auto';
  const g = AC.createGain(); AC.createMediaElementSource(el).connect(g); g.connect(bands[0]);
  const d = { el, g };
  el.addEventListener('ended', () => { if (d === decks[curDeck] && S.mode === 'deck' && !S.fading) onEnded(); });
  el.addEventListener('timeupdate', () => { if (d === decks[curDeck] && S.mode === 'deck') maybeCrossfade(); });
  el.addEventListener('error', () => { if (d === decks[curDeck] && S.mode === 'deck') toast('Can’t play this file'); });
  el.addEventListener('play', changed); el.addEventListener('pause', changed);
  return d;
};
const decks = [mkDeck(), mkDeck()];
streamEl.addEventListener('play', changed); streamEl.addEventListener('pause', changed);
streamEl.addEventListener('error', () => { if (S.mode === 'stream') toast('Can’t play this stream'); });
streamEl.addEventListener('ended', () => { if (S.mode === 'stream') onEnded(); });
const actEl = () => S.mode === 'stream' ? streamEl : decks[curDeck].el;
const isPlaying = () => { const e = actEl(); return !!e.src && !e.paused; };
const pos = () => actEl().currentTime || 0;
const dur = () => { const s = cur(); return (s && s.duration) || (isFinite(actEl().duration) ? actEl().duration : 0); };
function fx() {
  master.gain.value = P.volume; streamEl.volume = P.volume;
  bands.forEach((b, i) => { b.gain.value = P.pure ? 0 : (P.eq[i] || 0); });
  bassF.gain.value = P.pure ? 0 : P.bass / 100 * 12;
  applyRate();
}
function applyRate() {
  const r = P.pure ? 1 : P.rate / 33.3;
  for (const d of decks) { d.el.preservesPitch = false; d.el.defaultPlaybackRate = r; d.el.playbackRate = r; }
  streamEl.preservesPitch = false; const s = cur(), sr = s && s.live ? 1 : r; streamEl.defaultPlaybackRate = sr; streamEl.playbackRate = sr;
}
function killFade() { if (S.fading) { clearTimeout(fadeTimer); S.fading = false; decks.forEach((d, i) => { if (i !== curDeck) d.el.pause(); }); } }
function nextIndex() { const n = S.queue.length; if (S.shuffle && n > 1) { let r; do { r = Math.floor(Math.random() * n); } while (r === S.index); return r; } return (S.index + 1) % n; }
function playQueue(list, i) { if (!list.length) return; S.queue = list; play(i); }
function play(i) {
  if (!S.queue.length) return;
  const n = S.queue.length; S.index = ((i % n) + n) % n; const s = S.queue[S.index];
  killFade(); decks.forEach(d => d.el.pause()); streamEl.pause(); AC.resume();
  if (s.online) { S.mode = 'stream'; streamEl.src = s.url; applyRate(); streamEl.play().catch(e => { if (!e || e.name !== 'AbortError') toast('Can’t play this stream'); }); }
  else {
    S.mode = 'deck'; const d = decks[curDeck]; d.g.gain.cancelScheduledValues(0); d.g.gain.value = 1;
    d.el.src = fileUrl(s.path); applyRate(); d.el.play().catch(e => { if (!e || e.name !== 'AbortError') toast('Can’t play this file'); });
  }
  changed();
}
function crossTo(n) {
  const from = decks[curDeck], to = decks[1 - curDeck], s = S.queue[n], cf = P.cf, t = AC.currentTime;
  S.fading = true; S.index = n; curDeck = 1 - curDeck;
  to.g.gain.cancelScheduledValues(0); to.g.gain.value = 0;
  to.el.src = fileUrl(s.path); applyRate(); to.el.play().catch(() => { });
  from.g.gain.cancelScheduledValues(t); from.g.gain.setValueCurveAtTime(CURVE_OUT, t, cf);
  to.g.gain.setValueCurveAtTime(CURVE_IN, t, cf);
  fadeTimer = setTimeout(() => { from.el.pause(); S.fading = false; from.g.gain.cancelScheduledValues(0); from.g.gain.value = 1; }, cf * 1000 + 60);
  changed();
}
function maybeCrossfade() {
  if (!P.cf || S.fading) return; const s = cur(); if (!s || s.online) return;
  const el = decks[curDeck].el, d = el.duration;
  if (!isFinite(d) || d < P.cf * 2 + 1) return;
  if ((d - el.currentTime) / el.playbackRate <= P.cf) { const n = S.repeat ? S.index : nextIndex(), ns = S.queue[n]; if (ns && !ns.online) crossTo(n); }
}
function go(n) { const s = S.queue[n]; if (P.cf > 0 && isPlaying() && S.mode === 'deck' && s && !s.online && !S.fading) crossTo(n); else play(n); }
function onEnded() { if (S.repeat) play(S.index); else go(nextIndex()); }
function next() { if (S.queue.length) go(nextIndex()); }
function prev() { if (!S.queue.length) return; if (pos() > 3) seek(0); else go((S.index - 1 + S.queue.length) % S.queue.length); }
function toggle() {
  if (!cur()) { if (S.songs.length) playQueue(S.albums[0].tracks, 0); return; }
  const e = actEl(); AC.resume();
  if (!e.src) play(S.index); else if (e.paused) e.play(); else e.pause();
}
function seek(t) { try { actEl().currentTime = t; } catch (e) { } changed(); }
function setSleep(min) { clearTimeout(sleepTimer); if (min) sleepTimer = setTimeout(() => { actEl().pause(); toast('Sleep timer: stopped'); }, min * 60000); }
function playNext(s) { if (!S.queue.length) { S.queue = [s]; S.index = -1; return; } S.queue.splice(S.index + 1, 0, s); }

// media keys / Windows media overlay
let mediaFor = null;
function updateMedia() {
  if (!('mediaSession' in navigator)) return; const s = cur(); if (!s) return;
  try {
    if (mediaFor !== s.id) { mediaFor = s.id; const u = artUrl(s); navigator.mediaSession.metadata = new MediaMetadata({ title: s.title, artist: s.artist, album: s.album || '', artwork: u ? [{ src: u, sizes: '512x512' }] : [] }); }
    navigator.mediaSession.playbackState = isPlaying() ? 'playing' : 'paused';
  } catch (e) { }
}
if ('mediaSession' in navigator) {
  const set = (a, f) => { try { navigator.mediaSession.setActionHandler(a, f); } catch (e) { } };
  set('play', toggle); set('pause', toggle); set('previoustrack', prev); set('nexttrack', next);
  set('seekto', d => seek(d.seekTime)); set('seekbackward', () => seek(pos() - 10)); set('seekforward', () => seek(pos() + 10));
}

// ───────── change notification ─────────
let miniKey = '', changedQ = false;
function changed() {
  if (changedQ) return; changedQ = true;
  queueMicrotask(() => { changedQ = false; renderMini(); syncSheet(); syncRows(); updateMedia(); if (LY.open) lyricsTick(); });
}
function trackCode(s) { const a = S.albumOf.get(s.id); if (!a) return ''; const i = a.tracks.findIndex(t => t.id === s.id); return i >= 0 ? sideInfo(a.tracks, a.key)[i].code : ''; }
function renderMini() {
  const el = $('#mini'), s = cur();
  if (!s) { el.className = ''; el.innerHTML = ''; miniKey = ''; return; }
  const key = s.id + '|' + P.accent;
  if (key !== miniKey) {
    miniKey = key; const code = trackCode(s);
    el.innerHTML = `<div class="minirec"><div class="lab cover" ${bgStyle(artUrl(s))}></div></div><div class="mt"><b>${esc(code ? code + ' · ' + s.title : s.title)}</b><small>${esc(s.artist)}</small></div><button class="round bare bounce" data-act="toggle" id="miniBtn" style="width:44px;height:44px"></button>`;
  }
  el.className = 'glass on' + (isPlaying() ? ' playing' : '');
  $('#miniBtn').innerHTML = ico(isPlaying() ? 'pause' : 'play', 20);
}
function syncRows() { const s = cur(); document.querySelectorAll('.songrow[data-id]').forEach(r => r.classList.toggle('act', !!s && r.dataset.id === s.id)); }

// ───────── top bar, tabs ─────────
function renderTop() { $('#topbar').innerHTML = `<button class="round glass bounce" data-act="settings" title="Studio">${ico('stack')}</button><button class="round bare bounce" data-act="lyrics" title="Lyrics">${CRATE}</button>`; }
function renderTabs() {
  $('#tabs').className = 'glass';
  $('#tabs').innerHTML = [['library', 'grid'], ['discover', 'radio'], ['playlists', 'heartO'], ['search', 'search']]
    .map(([k, i]) => `<button class="tab bounce ${S.tab === k ? 'on' : ''}" data-act="tab" data-v="${k}" title="${k}">${ico(i, 26)}</button>`).join('');
}

// ───────── views ─────────
const badgeHtml = s => s.online ? '' : isHires(s) ? '<span class="badge hi">HI-RES</span>' : s.lossless ? '<span class="badge">LOSSLESS</span>' : '';
function songRowHtml(s, act, i) {
  const u = artUrl(s), c = cur();
  return `<div class="songrow ${c && c.id === s.id ? 'act' : ''}" data-act="${act}" data-id="${esc(s.id)}" data-i="${i == null ? '' : i}">
    <div class="cover ${u ? '' : 'none'}" ${bgStyle(u)}></div>
    <div class="mt"><b>${esc(s.title)}</b><small>${badgeHtml(s)}<span>${esc(s.artist)} · ${s.live ? 'LIVE' : fmt(s.duration)}</span></small></div>
    ${s.online ? '' : `<button class="dots" data-act="more" data-id="${esc(s.id)}">${ico('dots', 20)}</button>`}</div>`;
}
function renderView() {
  const v = $('#view');
  $('#fadeTop').style.display = $('#fadeBot').style.display = S.tab === 'library' ? '' : 'none';
  if (S.tab === 'library') return viewLibrary(v);
  if (S.tab === 'discover') return viewDiscover(v);
  if (S.tab === 'playlists') return viewPlaylists(v);
  viewSearch(v);
}
function viewLibrary(v) {
  v.scrollTop = 0;
  if (!S.albums.length) { v.innerHTML = '<div class="empty"><h2>Your library is empty</h2><p>Choose the folders that hold your music.</p><button class="pill on" data-act="addFolder">ADD MUSIC FOLDER</button><p id="scanMsg" class="sub" style="margin-top:12px"></p></div>'; return; }
  v.innerHTML = '<div class="grid">' + S.albums.map(a => `<button class="cell cover ${a.cover ? '' : 'none'}" data-act="album" data-k="${esc(a.key)}" title="${esc(a.title)} — ${esc(a.artist)}" ${bgStyle(a.cover ? fileUrl(a.cover) : '')}></button>`).join('') + '</div>';
}
function viewSearch(v) {
  v.innerHTML = `<div class="wrap"><div class="t1">Search</div><input id="q" class="field" placeholder="Songs, albums, artists" value="${esc(S.search.q)}">
    <div class="chips">${[['songs', 'SONGS'], ['albums', 'ALBUMS'], ['lossless', 'LOSSLESS'], ['hires', 'HI-RES']].map(([k, l]) => `<button class="pill ${S.search.mode === k ? 'on' : ''}" data-act="smode" data-v="${k}">${l}</button>`).join('')}</div><div id="results"></div></div>`;
  $('#q').addEventListener('input', e => { S.search.q = e.target.value; fillResults(); }); fillResults();
}
function fillResults() {
  const q = S.search.q.trim().toLowerCase(), m = S.search.mode, out = $('#results'); if (!out) return;
  if (m === 'albums') {
    const l = S.albums.filter(a => !q || a.title.toLowerCase().includes(q) || a.artist.toLowerCase().includes(q)).slice(0, 400);
    out.innerHTML = '<div class="grid">' + l.map(a => `<button class="cell cover ${a.cover ? '' : 'none'}" data-act="album" data-k="${esc(a.key)}" title="${esc(a.title)}" ${bgStyle(a.cover ? fileUrl(a.cover) : '')}></button>`).join('') + '</div>'; return;
  }
  const l = S.songs.filter(s => (!q || s.title.toLowerCase().includes(q) || s.artist.toLowerCase().includes(q) || s.album.toLowerCase().includes(q)) &&
    (m === 'songs' || (m === 'lossless' && s.lossless) || (m === 'hires' && isHires(s)))).slice(0, 300);
  out.innerHTML = l.length ? l.map(s => songRowHtml(s, 'playsong')).join('') : '<p class="sub" style="padding:16px">Nothing found</p>';
}
function viewDiscover(v) {
  const d = S.disc;
  v.innerHTML = `<div class="wrap"><div class="t1">Discover</div><div class="chips" style="margin-top:0"><button class="pill ${d.mode === 'music' ? 'on' : ''}" data-act="dmode" data-v="music">MUSIC</button><button class="pill ${d.mode === 'radio' ? 'on' : ''}" data-act="dmode" data-v="radio">RADIO</button></div>
    <input id="dq" class="field" placeholder="${d.mode === 'music' ? 'Search tracks or artists, Enter to go' : 'Search stations, Enter to go'}" value="${esc(d.q)}">
    <p class="sub" style="margin:8px 4px">${d.mode === 'music' ? 'Streaming from Audius' : 'Live stations from Radio Browser'}</p><div id="dres"></div></div>`;
  $('#dq').addEventListener('keydown', e => { if (e.key === 'Enter') { d.q = e.target.value; runDiscover(); } });
  if (!d.results.length && !d.busy) runDiscover(); else fillDiscover();
}
function fillDiscover() {
  const d = S.disc, out = $('#dres'); if (!out) return;
  out.innerHTML = d.busy ? '<p class="sub" style="padding:16px">Loading…</p>' : d.results.length ? d.results.map((s, i) => songRowHtml(s, 'playdisc', i)).join('') : '<p class="sub" style="padding:16px">Nothing found, or no internet connection.</p>';
}
async function runDiscover() { const d = S.disc; d.busy = true; fillDiscover(); d.results = await (d.mode === 'music' ? audius(d.q.trim()) : radio(d.q.trim())); d.busy = false; fillDiscover(); }
async function audius(q) {
  for (const host of ['https://api.audius.co', 'https://discoveryprovider.audius.co']) {
    const path = q ? `/v1/tracks/search?query=${encodeURIComponent(q)}&limit=40&` : '/v1/tracks/trending?limit=40&';
    const r = await jpeg.json(`${host}${path}app_name=JPEG`); const arr = r.data && r.data.data; if (!Array.isArray(arr)) continue;
    const out = arr.filter(t => t.id && t.is_streamable !== false).map(t => ({ id: 'a:' + t.id, online: true, title: t.title || 'Untitled', artist: (t.user && t.user.name) || 'Audius', album: 'Audius', duration: t.duration || 0, url: `${host}/v1/tracks/${t.id}/stream?app_name=JPEG`, art: (t.artwork && t.artwork['480x480']) || '' }));
    if (out.length) return out;
  } return [];
}
async function radio(q) {
  for (const host of ['https://de1.api.radio-browser.info', 'https://nl1.api.radio-browser.info', 'https://at1.api.radio-browser.info']) {
    const url = q ? `${host}/json/stations/search?name=${encodeURIComponent(q)}&limit=40&hidebroken=true&order=clickcount&reverse=true` : `${host}/json/stations/topclick/40`;
    const r = await jpeg.json(url); if (!Array.isArray(r.data)) continue;
    const out = r.data.filter(o => o.url_resolved && (o.lastcheckok == null || o.lastcheckok == 1)).map(o => ({ id: 'r:' + (o.stationuuid || o.url_resolved), online: true, live: true, title: (o.name || 'Radio').trim(), artist: [o.country, (o.tags || '').split(',')[0]].filter(Boolean).join(' · ') || 'Live radio', album: '', duration: 0, url: o.url_resolved, art: o.favicon || '' }));
    if (out.length) return out;
  } return [];
}
function viewPlaylists(v) {
  const open = S.plOpen;
  if (!open) {
    v.innerHTML = `<div class="wrap"><div class="t1">Playlists</div><div class="row" style="justify-content:space-between;margin-bottom:8px"><span class="sub">${Object.keys(PL).length} playlists</span><button class="pill on" data-act="newpl">+ NEW</button></div>` +
      Object.keys(PL).map(n => { const l = plSongs(n), c = l.find(s => s.cover);
        return `<div class="songrow" data-act="openpl" data-n="${esc(n)}"><div class="cover ${c ? '' : 'none'}" ${bgStyle(c ? fileUrl(c.cover) : '')}></div><div class="mt"><b>${n === 'Favorites' ? '♥ Favorites' : esc(n)}</b><small>${l.length} songs</small></div><button class="round bounce" style="width:40px;height:40px;background:var(--accent);color:var(--on)" data-act="playpl" data-n="${esc(n)}">${ico('play', 16)}</button>${n !== 'Favorites' ? `<button class="dots" data-act="delpl" data-n="${esc(n)}">${ico('close', 16)}</button>` : ''}</div>`; }).join('') + '</div>';
    return;
  }
  const l = plSongs(open);
  v.innerHTML = `<div class="wrap"><div class="row" style="gap:6px;margin-bottom:12px"><button class="round bare bounce" data-act="plback" style="width:40px;height:40px">${ico('back')}</button><div class="t1" style="margin:0;flex:1">${open === 'Favorites' ? '♥ Favorites' : esc(open)}</div><button class="pill on" data-act="playpl" data-n="${esc(open)}">PLAY</button></div>` +
    (l.length ? l.map((s, i) => songRowHtml(s, 'plsong', i)).join('') : '<p class="sub">Empty. Use ••• on a song to add it here.</p>') + '</div>';
}

// ───────── menus & dialogs ─────────
function showMenu(title, items) {
  const m = $('#modal');
  m.innerHTML = `<div class="box glass"><div class="sub" style="font-size:12px;padding:0 4px 4px">${esc(title)}</div>${items.map((it, i) => `<button class="mi" data-mi="${i}">${esc(it[0])}</button>`).join('')}</div>`;
  m.classList.add('open');
  m.onclick = e => { const b = e.target.closest('[data-mi]'); if (b) { const f = items[+b.dataset.mi][1]; m.classList.remove('open'); f(); } else if (e.target === m) m.classList.remove('open'); };
}
function askText(title) {
  return new Promise(res => {
    const m = $('#modal');
    m.innerHTML = `<div class="box mid glass"><b style="font-size:18px">${esc(title)}</b><input id="ask" class="field" style="margin:12px 0" maxlength="24"><div class="row" style="gap:8px"><button class="pill" data-k="no">CANCEL</button><button class="pill on" data-k="ok">CREATE</button></div></div>`;
    m.classList.add('open'); const inp = $('#ask'); inp.focus();
    const done = v => { m.classList.remove('open'); m.onclick = null; res(v); };
    inp.addEventListener('keydown', e => { if (e.key === 'Enter') done(inp.value.trim()); if (e.key === 'Escape') done(null); });
    m.onclick = e => { const b = e.target.closest('[data-k]'); if (b) done(b.dataset.k === 'ok' ? inp.value.trim() : null); else if (e.target === m) done(null); };
  });
}
function songMenu(s) {
  const items = [['Play next', () => { playNext(s); toast('Added to play next'); }],
    [isFav(s) ? 'Remove from Favorites' : 'Add to Favorites', () => toggleFav(s)]];
  Object.keys(PL).filter(n => n !== 'Favorites').forEach(n => items.push(['Add to ' + n, () => { if (!PL[n].includes(s.id)) { PL[n].push(s.id); savePL(); } }]));
  if (S.tab === 'playlists' && S.plOpen && S.plOpen !== 'Favorites') items.push(['Remove from ' + S.plOpen, () => { PL[S.plOpen] = PL[S.plOpen].filter(x => x !== s.id); savePL(); renderView(); }]);
  showMenu(s.title, items);
}

// ───────── release sheet ─────────
const sh = { album: null, out: false };
const albumArt = a => a.cover ? fileUrl(a.cover) : ((a.tracks.find(t => t.art) || {}).art || '');
function openSheet(a, play) {
  if (play && !a.tracks.some(t => cur() && t.id === cur().id)) playQueue(a.tracks, 0);
  sh.album = a; sh.out = false; renderSheet(); $('#sheet').classList.add('open'); loadPeaks(cur());
}
function closeSheet() { $('#sheet').classList.remove('open'); sh.album = null; }
function renderSheet() {
  const a = sh.album, art = albumArt(a), info = sideInfo(a.tracks, a.key);
  const total = Math.round(a.tracks.reduce((x, t) => x + (t.duration || 0), 0) / 60), year = (a.tracks.find(t => t.year) || {}).year;
  $('#sheet').innerHTML = `
  <div class="sheet-top"><button class="round glass bounce" data-act="closeSheet">${ico('close', 22)}</button><div class="ttl"><b id="shT"></b><small id="shA"></small></div><button class="round bare bounce" data-act="lyrics" title="Lyrics">${CRATE}</button></div>
  <div class="sheet-body"><div class="left">
    <div class="stage" id="stage" data-act="pull">
      <div class="s paper"></div>
      <div class="r"><div class="disc"><div class="lab cover" ${bgStyle(art)}></div></div><div class="sheen"></div></div>
      <div class="s sleeve-wrap"><div class="sleeve cover ${art ? '' : 'none'}" ${bgStyle(art)}></div></div>
    </div>
    <div class="info"><div class="cover ${art ? '' : 'none'}" ${bgStyle(art)}></div><div class="mt"><b>${esc(a.artist)}</b><span>${esc(a.title)}</span><small>${year ? year + ' · ' : ''}${a.tracks.length} tracks · ${total} min</small></div><button class="round bare bounce" id="favBtn" data-act="fav"></button></div>
  </div><div class="right tl"><div class="h"><span class="c1">Side</span><span class="c2">Time</span><span class="c3"></span><span class="c4">Tracklist</span></div>` +
    a.tracks.map((t, i) => `<div class="r2" data-act="trow" data-i="${i}" data-id="${esc(t.id)}"><span class="c1">${i === 0 || info[i].si !== info[i - 1].si ? info[i].side : ''}</span><span class="c2">${t.live ? 'LIVE' : fmt(t.duration)}</span><span class="c3"></span><span class="c4">${esc(t.artist + ' - ' + t.title)}</span></div>`).join('') +
  `</div></div>
  <div id="wavePill" class="glass"><button class="round bare bounce" data-act="toggle" id="wpBtn" style="width:40px;height:40px"></button><canvas id="wave"></canvas></div>`;
  const c = $('#wave'); const seekFrom = e => { const r = c.getBoundingClientRect(), s = cur(); if (s && !s.live && dur()) seek(clamp((e.clientX - r.left) / r.width) * dur()); };
  c.addEventListener('pointerdown', e => { c.setPointerCapture(e.pointerId); seekFrom(e); c.onpointermove = seekFrom; });
  c.addEventListener('pointerup', () => { c.onpointermove = null; });
  const st = $('#stage');
  st.addEventListener('mousemove', e => { const r = st.getBoundingClientRect(); st.style.setProperty('--ry', ((e.clientX - r.left) / r.width - .5) * 10 + 'deg'); st.style.setProperty('--rx', (.5 - (e.clientY - r.top) / r.height) * 8 + 'deg'); });
  st.addEventListener('mouseleave', () => { st.style.setProperty('--ry', '0deg'); st.style.setProperty('--rx', '0deg'); });
  syncSheet(); drawWave();
}
function syncSheet() {
  if (!sh.album) return; const s = cur(), a = sh.album, inAlbum = s && a.tracks.some(t => t.id === s.id), head = inAlbum ? s : a.tracks[0], pl = isPlaying();
  $('#shT').textContent = head ? head.title : a.title; $('#shA').textContent = head ? head.artist : a.artist;
  $('#sheet').querySelectorAll('.tl .r2').forEach(r => { const on = !!s && r.dataset.id === s.id; r.classList.toggle('cur', on); r.querySelector('.c3').innerHTML = ico(on && pl ? 'pause' : 'play', 14); });
  $('#wpBtn').innerHTML = ico(pl ? 'pause' : 'play', 18);
  $('#stage').classList.toggle('playing', pl); $('#stage').classList.toggle('out', sh.out);
  $('#favBtn').innerHTML = ico(isFav(s) ? 'heart' : 'heartO', 26); $('#favBtn').style.display = s && !s.online ? '' : 'none';
  if (inAlbum && !a._peaked) { a._peaked = s.id; loadPeaks(s); } else if (inAlbum && a._peaked !== s.id) { a._peaked = s.id; loadPeaks(s); }
}

// waveform from the real audio (decoded lazily), pseudo wave while loading
const peaksCache = new Map();
function pseudo(id) { let h = 0; for (const ch of String(id)) h = (Math.imul(31, h) + ch.charCodeAt(0)) | 0; let seed = Math.abs(h) || 1, v = .5; const o = new Float32Array(150); for (let i = 0; i < 150; i++) { seed = (seed * 1664525 + 1013904223) >>> 0; v = clamp(v + (seed / 4294967296 - .5) * .55, .2, 1); o[i] = v; } return o; }
async function loadPeaks(s) {
  if (!s || s.online || peaksCache.has(s.id)) return; peaksCache.set(s.id, null);
  try {
    const raw = await jpeg.readFile(s.path); if (raw.byteLength > 90e6) return;
    const buf = await AC.decodeAudioData(raw.buffer.slice(raw.byteOffset, raw.byteOffset + raw.byteLength));
    const ch = buf.getChannelData(0), n = 150, step = Math.max(1, Math.floor(ch.length / n)), out = new Float32Array(n); let mx = 0;
    for (let i = 0; i < n; i++) { let m = 0; const a = i * step, b = Math.min(ch.length, a + step), inc = Math.max(1, (step / 200) | 0); for (let k = a; k < b; k += inc) { const v = Math.abs(ch[k]); if (v > m) m = v; } out[i] = m; if (m > mx) mx = m; }
    for (let i = 0; i < n; i++) out[i] = Math.max(.14, out[i] / (mx || 1));
    peaksCache.set(s.id, out); drawWave();
  } catch (e) { }
}
function drawWave() {
  const c = $('#wave'); if (!c || !sh.album) return; const dpr = devicePixelRatio || 1, w = c.clientWidth, h = c.clientHeight; if (!w) return;
  if (c.width !== Math.round(w * dpr)) { c.width = Math.round(w * dpr); c.height = Math.round(h * dpr); }
  const g = c.getContext('2d'); g.setTransform(dpr, 0, 0, dpr, 0, 0); g.clearRect(0, 0, w, h);
  const s = cur(), pk = s ? (peaksCache.get(s.id) || pseudo(s.id)) : pseudo(0), n = pk.length, step = w / n, d = dur(), prog = s && !s.live && d ? pos() / d : 0;
  g.lineCap = 'round'; g.lineWidth = Math.max(1.5, step * .55);
  for (let i = 0; i < n; i++) { const hh = pk[i] * h, x = i * step + step / 2; g.strokeStyle = i / n < prog ? '#6a6a70' : '#c9c9ce'; g.beginPath(); g.moveTo(x, (h - hh) / 2); g.lineTo(x, (h + hh) / 2); g.stroke(); }
  const px = clamp(prog) * w; g.strokeStyle = '#111113'; g.lineWidth = 3; g.beginPath(); g.moveTo(px, h * .08); g.lineTo(px, h * .92); g.stroke();
}

// ───────── lyric scene ─────────
const LY = { open: false, songId: null, phrases: [], state: 'idle', cur: -2, timers: [] };
const SPR = { heart: { src: 'assets/sk_heart.webp', fw: 130, fh: 165, dur: [67, 133, 167, 133, 167, 133, 167, 133, 167, 133, 167, 133, 100], id: 'stkH' },
  emoji: { src: 'assets/sk_emoji.webp', fw: 188, fh: 179, dur: [67, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 200, 100, 100, 100, 100, 33], id: 'stkE' } };
function startStickers() {
  stopStickers();
  for (const k of Object.keys(SPR)) {
    const sp = SPR[k], c = document.getElementById(sp.id), g = c.getContext('2d'), img = new Image(); c.width = sp.fw; c.height = sp.fh; img.src = sp.src; let i = 0;
    const step = () => { g.clearRect(0, 0, sp.fw, sp.fh); if (img.complete && img.naturalWidth) g.drawImage(img, i * sp.fw, 0, sp.fw, sp.fh, 0, 0, sp.fw, sp.fh); const d = sp.dur[i]; i = (i + 1) % sp.dur.length; LY.timers.push(setTimeout(step, d)); };
    step();
  }
}
const stopStickers = () => { LY.timers.forEach(clearTimeout); LY.timers = []; };
function phraseHtml(p) {
  const top = (p.y - 0.0554).toFixed(4);
  if (p.adlib) return `<div class="adlib" style="top:calc(var(--S)*${top});transform:translateX(calc(var(--S)*${(p.x - .5).toFixed(4)}))"><span class="w" data-at="${p.rows[0][0].at}">${esc(p.rows[0].map(w => w.text).join(' '))}</span></div>`;
  return `<div class="blk" style="left:calc(var(--S)*${p.x.toFixed(4)});top:calc(var(--S)*${top})">${p.rows.map(r => `<div class="row">${r.map((w, k) => `<span class="w${w.red ? ' red' : ''}" data-at="${w.at}" style="margin-left:${k ? w.gapEm + 'em' : 0}">${esc(w.text)}</span>`).join('')}</div>`).join('')}</div>`;
}
const W0 = (text, g = 0) => ({ text, at: 0, red: false, gapEm: g });
function openLyrics() {
  if (!cur()) { toast('Play a song first'); return; }
  LY.open = true; LY.songId = null; LY.cur = -2;
  $('#lyrics').innerHTML = '<div class="lstage"><canvas class="stk" id="stkH"></canvas><canvas class="stk" id="stkE"></canvas><div id="ph"></div></div><button class="lclose" data-act="closeLyrics">' + ico('close', 22) + '</button>';
  $('#lyrics').classList.add('open'); startStickers(); setTimeout(() => $('#lyrics').querySelectorAll('.stk').forEach(c => c.classList.add('on')), 30); ensureLyrics();
}
function closeLyrics() { LY.open = false; $('#lyrics').classList.remove('open'); stopStickers(); }
async function ensureLyrics() {
  const s = cur(); if (!s || !LY.open) return; LY.songId = s.id; LY.cur = -2; LY.phrases = [];
  if (s.live) { LY.state = 'live'; return renderLyricState(); }
  LY.state = 'loading'; renderLyricState();
  const d = await getLyrics(s); if (!LY.open || !cur() || cur().id !== s.id) return;
  LY.phrases = d && (d.synced.length || String(d.plain).trim()) ? Lyr.buildPhrases(d, (s.duration || 0) * 1000) : [];
  LY.state = LY.phrases.length ? 'ok' : 'none'; LY.cur = -2; renderLyricState();
}
function renderLyricState() {
  const ph = $('#ph'); if (!ph) return;
  if (LY.state === 'ok') { ph.innerHTML = ''; return; }
  const one = (t) => ({ adlib: true, rows: [[W0(t)]], x: .504, y: .521 });
  if (LY.state === 'live') ph.innerHTML = phraseHtml(one('live radio'));
  else if (LY.state === 'loading') ph.innerHTML = phraseHtml(one('searching…'));
  else ph.innerHTML = phraseHtml({ adlib: false, rows: [[W0('no'), W0('lyrics', .56)], [W0('found')]], x: .285, y: .404 }) + '<button class="retry" data-act="retryLyrics">retry</button>';
  ph.querySelectorAll('.w').forEach(w => w.classList.add('on'));
}
function lyricsTick() {
  if (!LY.open) return; const s = cur(); if (!s) return;
  if (LY.songId !== s.id) { ensureLyrics(); return; }
  const ph = $('#ph'); if (!ph || LY.state !== 'ok') return;
  const ms = pos() * 1000; let idx = -1;
  for (let i = LY.phrases.length - 1; i >= 0; i--) if (LY.phrases[i].start <= ms) { idx = ms < LY.phrases[i].end ? i : -1; break; }
  if (idx !== LY.cur) { LY.cur = idx; ph.innerHTML = idx >= 0 ? phraseHtml(LY.phrases[idx]) : ''; }
  ph.querySelectorAll('.w').forEach(w => w.classList.toggle('on', ms >= +w.dataset.at));
}
async function getLyrics(s) {
  const key = 'jpeg.lyr.' + s.id, c = localStorage.getItem(key);
  if (c) { try { return JSON.parse(c); } catch (e) { } }
  const artist = /^unknown/i.test(s.artist) ? '' : s.artist, d0 = Math.round(s.duration || 0);
  const q = o => Object.entries(o).filter(([, v]) => v !== '' && v != null).map(([k, v]) => k + '=' + encodeURIComponent(v)).join('&');
  let reached = false, found = null;
  if (artist && s.album && d0) {
    const r = await jpeg.json('https://lrclib.net/api/get?' + q({ track_name: s.title, artist_name: artist, album_name: s.album, duration: d0 }));
    if (r.status === 200 && r.data) { reached = true; if (r.data.syncedLyrics || r.data.plainLyrics) found = r.data; } else if (r.status === 404) reached = true;
  }
  if (!found) {
    const r = await jpeg.json('https://lrclib.net/api/search?' + q({ track_name: s.title, artist_name: artist }));
    if (r.ok && Array.isArray(r.data)) {
      reached = true; let best = null, bs = 1e9;
      for (const o of r.data) { if (!o.syncedLyrics && !o.plainLyrics) continue; const diff = d0 ? Math.abs((o.duration || 0) - d0) : 0; if (d0 && diff > 8) continue; const sc = diff + (o.syncedLyrics ? 0 : 20); if (sc < bs) { bs = sc; best = o; } }
      if (best) found = best;
    }
  }
  const d = { synced: found && found.syncedLyrics ? Lyr.parseLrc(found.syncedLyrics) : [], plain: (found && found.plainLyrics) || '' };
  if (reached) { try { localStorage.setItem(key, JSON.stringify(d)); } catch (e) { } }
  return d;
}

// ───────── studio (settings) ─────────
const ACC = ['#111113', '#34c759', '#ff9f0a', '#ff375f', '#0a84ff', '#8e5cf7'], VIN = ['#0e0e0e', '#8e1b16', '#12233f', '#3a3a3a'];
const PRESET = { 1: { blur: 24, tint: .55, border: .7, shadow: .18 }, 2: { blur: 18, tint: .32, border: .85, shadow: .22 } };
const EQP = { flat: [0, 0, 0, 0, 0], bass: [8, 5, 0, 0, 0], vocal: [-3, 0, 5, 5, -2], treble: [0, 0, 0, 5, 8] };
const card = (t, h) => `<div class="card glass"><h3>${t}</h3>${h}</div>`;
const cfg = (k, v, label, on) => `<button class="pill ${on ? 'on' : ''}" data-act="cfg" data-k="${k}" data-v="${v}">${label}</button>`;
const slider = (label, k, min, max, val, unit, scale = 1) => `<label class="sl"><span>${label}</span><input type="range" min="${min}" max="${max}" step="1" value="${val}" data-set="${k}" data-scale="${scale}" data-unit="${unit}"><output>${val}${unit}</output></label>`;
function renderSettings() {
  const g = P.glass, nLL = S.songs.filter(s => s.lossless).length, nHR = S.songs.filter(isHires).length;
  $('#settings').innerHTML = `<div class="sheet-top"><button class="round glass bounce" data-act="closeSettings">${ico('close', 22)}</button><h1>Studio</h1></div><div class="set">` +
  card('Music folders', (S.folders.map(f => `<div class="frow"><span>${esc(f)}</span><button class="pill" data-act="rmFolder" data-v="${esc(f)}">REMOVE</button></div>`).join('') || '<p class="sub">No folders yet.</p>') +
    '<div class="chips"><button class="pill on" data-act="addFolder">ADD FOLDER</button><button class="pill" data-act="rescan">RESCAN</button></div><p class="sub" id="scanMsg"></p>') +
  card('Glass style', `<div class="chips">${cfg('glass.style', 0, 'DEFAULT', g.style === 0)}${cfg('glass.style', 1, 'FROSTED', g.style === 1)}${cfg('glass.style', 2, 'LIQUID', g.style === 2)}</div>` +
    (g.style ? slider('Blur', 'glass.blur', 0, 60, g.blur, 'px') + slider('Tint', 'glass.tint', 0, 90, Math.round(g.tint * 100), '%', .01) + slider('Border', 'glass.border', 0, 100, Math.round(g.border * 100), '%', .01) + slider('Shadow', 'glass.shadow', 0, 50, Math.round(g.shadow * 100), '%', .01) + (g.style === 2 ? slider('Refraction', 'glass.refract', 0, 60, g.refract, '') : '') + '<p class="sub">Applies live. Refraction is the wavy lens effect of liquid glass.</p>' : '<p class="sub">Default keeps the plain translucent look. Frosted and Liquid add real backdrop blur.</p>')) +
  card('Appearance', 'Accent colour<div class="sw">' + ACC.map(c => `<i class="${P.accent === c ? 'on' : ''}" style="background:${c}" data-act="cfg" data-k="accent" data-v="${c}"></i>`).join('') + '</div>Vinyl colour<div class="sw">' + VIN.map(c => `<i class="${P.vinyl === c ? 'on' : ''}" style="background:${c}" data-act="cfg" data-k="vinyl" data-v="${c}"></i>`).join('') + '</div>') +
  card('Playback', `<div class="chips">${cfg('shuffle', !P.shuffle, 'SHUFFLE', P.shuffle)}${cfg('repeat', !P.repeat, 'REPEAT ONE', P.repeat)}</div>Crossfade<div class="chips">${[0, 3, 6, 10].map(v => cfg('cf', v, v ? v + ' SEC' : 'OFF', P.cf === v)).join('')}</div>Turntable speed<div class="chips">${cfg('rate', 33.3, '33.3 RPM', P.rate === 33.3)}${cfg('rate', 45, '45 RPM', P.rate === 45)}</div>Sleep timer<div class="chips">${[0, 15, 30, 60].map(v => `<button class="pill" data-act="sleep" data-v="${v}">${v ? v + ' MIN' : 'OFF'}</button>`).join('')}</div>`) +
  card('Equalizer', `<div class="chips">${Object.keys(EQP).map(k => `<button class="pill" data-act="eqp" data-v="${k}">${k.toUpperCase()}</button>`).join('')}</div>` + ['60 Hz', '230 Hz', '910 Hz', '3.6 kHz', '14 kHz'].map((l, i) => slider(l, 'eq.' + i, -12, 12, P.eq[i] || 0, ' dB')).join('') + slider('Bass boost', 'bass', 0, 100, P.bass, '')) +
  card('Audio quality', `<p>${nHR} Hi-Res · ${nLL} lossless files</p><div class="chips">${cfg('pure', !P.pure, 'PURE MODE (NO EQ / SPEED FX)', P.pure)}</div><p class="sub">Output mixer: ${AC.sampleRate} Hz. Windows may resample Hi-Res audio to this rate unless you use WASAPI exclusive mode or a USB DAC.</p>`) +
  '</div>';
}
function setPath(path, v) { const p = path.split('.'); let o = P; for (let i = 0; i < p.length - 1; i++) o = o[p[i]]; o[p[p.length - 1]] = v; }
function parseV(v) { return v === 'true' ? true : v === 'false' ? false : (v !== '' && !isNaN(+v)) ? +v : v; }
function afterCfg(k) {
  if (k === 'glass.style') { const pr = PRESET[P.glass.style]; if (pr) Object.assign(P.glass, pr); }
  S.shuffle = P.shuffle; S.repeat = P.repeat;
  savePrefs(); applyTheme(); fx(); miniKey = ''; renderSettings(); changed();
}

// ───────── library scanning ─────────
jpeg.onScan(p => document.querySelectorAll('#scanMsg').forEach(m => { m.textContent = `Scanning… ${p.done}/${p.total}`; }));
async function doScan(quiet) {
  const lib = await jpeg.scan(S.folders); S.folders = lib.folders; S.songs = lib.songs; buildAlbums(); renderView();
  document.querySelectorAll('#scanMsg').forEach(m => { m.textContent = ''; }); if (!quiet) toast(lib.songs.length + ' songs'); if ($('#settings').classList.contains('open')) renderSettings();
}
async function loadLibrary() { const lib = await jpeg.getLibrary(); S.folders = lib.folders || []; S.songs = lib.songs || []; buildAlbums(); renderView(); if (S.folders.length) doScan(true); }

// ───────── events ─────────
const albumByKey = k => S.albums.find(a => a.key === k);
function queueAlbum() { const s = cur(); if (!s) return null; return S.albumOf.get(s.id) || { key: 'queue', title: s.album || 'Now playing', artist: s.artist, tracks: S.queue, cover: '' }; }
async function act(name, t) {
  const d = t.dataset;
  switch (name) {
    case 'tab': S.tab = d.v; S.plOpen = null; renderTabs(); renderView(); break;
    case 'album': openSheet(albumByKey(d.k), true); break;
    case 'closeSheet': closeSheet(); break;
    case 'settings': renderSettings(); $('#settings').classList.add('open'); break;
    case 'closeSettings': $('#settings').classList.remove('open'); break;
    case 'lyrics': openLyrics(); break;
    case 'closeLyrics': closeLyrics(); break;
    case 'retryLyrics': { const s = cur(); if (s) { localStorage.removeItem('jpeg.lyr.' + s.id); LY.songId = null; ensureLyrics(); } break; }
    case 'toggle': toggle(); break;
    case 'pull': sh.out = !sh.out; $('#stage').classList.toggle('out', sh.out); break;
    case 'trow': { const s = sh.album.tracks[+d.i]; if (cur() && cur().id === s.id) toggle(); else playQueue(sh.album.tracks, +d.i); break; }
    case 'fav': toggleFav(cur()); break;
    case 'addFolder': { const picked = await jpeg.pickFolders(); if (picked.length) { S.folders = [...new Set([...S.folders, ...picked])]; await doScan(); } break; }
    case 'rmFolder': S.folders = S.folders.filter(f => f !== d.v); await doScan(); break;
    case 'rescan': await doScan(); break;
    case 'smode': S.search.mode = d.v; renderView(); break;
    case 'playsong': { const s = S.byId.get(d.id), a = s && S.albumOf.get(s.id); if (a) { playQueue(a.tracks, a.tracks.findIndex(x => x.id === s.id)); openSheet(a, false); } break; }
    case 'more': { const s = S.byId.get(d.id); if (s) songMenu(s); break; }
    case 'dmode': S.disc.mode = d.v; S.disc.results = []; S.disc.q = ''; renderView(); break;
    case 'playdisc': { playQueue(S.disc.results, +d.i); openSheet(queueAlbum(), false); break; }
    case 'newpl': { const n = await askText('New playlist'); if (n && !PL[n]) { PL[n] = []; savePL(); renderView(); } break; }
    case 'openpl': S.plOpen = d.n; renderView(); break;
    case 'plback': S.plOpen = null; renderView(); break;
    case 'playpl': { const l = plSongs(d.n); if (l.length) { playQueue(l, 0); openSheet({ key: 'pl:' + d.n, title: d.n, artist: 'Playlist', tracks: l, cover: (l.find(x => x.cover) || {}).cover || '' }, false); } break; }
    case 'delpl': delete PL[d.n]; savePL(); renderView(); break;
    case 'plsong': { const l = plSongs(S.plOpen); playQueue(l, +d.i); openSheet({ key: 'pl:' + S.plOpen, title: S.plOpen, artist: 'Playlist', tracks: l, cover: (l.find(x => x.cover) || {}).cover || '' }, false); break; }
    case 'cfg': setPath(d.k, parseV(d.v)); afterCfg(d.k); break;
    case 'sleep': setSleep(+d.v); toast(+d.v ? 'Sleep timer: ' + d.v + ' min' : 'Sleep timer off'); break;
    case 'eqp': P.eq = EQP[d.v].slice(); afterCfg('eq'); break;
  }
}
document.addEventListener('click', e => {
  if (e.target.closest('#modal')) return;
  const t = e.target.closest('[data-act]');
  if (t) { act(t.dataset.act, t); return; }
  if (e.target.closest('#mini')) { const a = queueAlbum(); if (a) openSheet(a, false); return; }
  if (e.target.closest('#lyrics')) toggle();
});
document.addEventListener('input', e => {
  const t = e.target; if (!t.dataset || !t.dataset.set) return;
  setPath(t.dataset.set.startsWith('eq.') ? t.dataset.set : t.dataset.set, parseFloat(t.value) * parseFloat(t.dataset.scale || 1));
  if (t.dataset.set.startsWith('eq.')) { const i = +t.dataset.set.slice(3); P.eq[i] = parseFloat(t.value); }
  t.nextElementSibling.textContent = Math.round(parseFloat(t.value)) + (t.dataset.unit || '');
  savePrefs(); applyTheme(); fx();
});
document.addEventListener('keydown', e => {
  const typing = /^(INPUT|TEXTAREA)$/.test(e.target.tagName);
  if (e.key === 'Escape') { if (LY.open) closeLyrics(); else if ($('#modal').classList.contains('open')) $('#modal').classList.remove('open'); else if ($('#settings').classList.contains('open')) $('#settings').classList.remove('open'); else if (sh.album) closeSheet(); return; }
  if (typing) return;
  if (e.code === 'Space') { e.preventDefault(); if (document.activeElement && document.activeElement.blur) document.activeElement.blur(); toggle(); }
  else if (e.key === 'ArrowRight') { e.shiftKey ? next() : seek(pos() + 5); }
  else if (e.key === 'ArrowLeft') { e.shiftKey ? prev() : seek(pos() - 5); }
  else if (e.key === 'ArrowUp') { P.volume = clamp(P.volume + .05); fx(); savePrefs(); }
  else if (e.key === 'ArrowDown') { P.volume = clamp(P.volume - .05); fx(); savePrefs(); }
  else if (e.key.toLowerCase() === 'l') openLyrics();
});

// ───────── boot ─────────
S.shuffle = P.shuffle; S.repeat = P.repeat;
applyTheme(); fx(); renderTop(); renderTabs(); renderView();
loadLibrary();
setInterval(() => { if (sh.album) drawWave(); if (LY.open) lyricsTick(); }, 120);
