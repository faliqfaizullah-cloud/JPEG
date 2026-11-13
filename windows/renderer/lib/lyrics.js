// Lyric parsing and the "white screen" phrase layout. Pure functions: runs in the app and in Node for testing.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.Lyr = factory();
})(this, function () {
  const ADLIB = /^(?:o{2,}h*|o+h+|a{2,}h*|a+h+|u{2,}h*|u+h+|e+h+|i+h+|m{2,}|h+m+|ye+a+h*|woa+h*|whoa+h*|hey+|(?:la)+|(?:na)+|(?:da)+)$/;
  const STRIP = /[\[\](),.;:!?"“”…]/g;

  function parseLrc(raw) {
    const out = [];
    for (const line of String(raw || '').split(/\r?\n/)) {
      const stamps = [...line.matchAll(/\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\]/g)];
      if (!stamps.length) continue;
      const last = stamps[stamps.length - 1];
      const text = line.slice(last.index + last[0].length).trim();
      for (const m of stamps) {
        const f = m[3] || '';
        const ms = f.length === 0 ? 0 : f.length === 1 ? +f * 100 : f.length === 2 ? +f * 10 : +f.slice(0, 3);
        out.push({ ms: +m[1] * 60000 + +m[2] * 1000 + ms, text });
      }
    }
    return out.sort((a, b) => a.ms - b.ms);
  }

  const clean = s => String(s).toLowerCase().replace(STRIP, ' ').replace(/\s+/g, ' ').trim();
  const jhash = s => { let h = 0; for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0; return Math.abs(h); };

  // Stack words into short rows: at most 4 rows, 2 words per row.
  function chunkRows(words) {
    let limit = 10;
    for (let attempt = 0; attempt < 4; attempt++) {
      const rows = []; let len = 0;
      for (let i = 0; i < words.length; i++) {
        const w = words[i].length, cur = rows[rows.length - 1];
        if (cur && cur.length < 2 && len + 1 + w <= limit) { cur.push(i); len += 1 + w; }
        else { rows.push([i]); len = w; }
      }
      if (rows.length <= 4) return rows;
      limit += 3;
    }
    const rows = [];
    for (let i = 0; i < words.length; i += 3) rows.push(words.slice(i, i + 3).map((_, k) => i + k));
    return rows;
  }

  // d = { synced: [{ms,text}], plain: '' }
  function buildPhrases(d, durMs) {
    let raw;
    if (d.synced && d.synced.length) raw = d.synced.map(l => [l.ms, l.text]);
    else {
      const ls = String(d.plain || '').split(/\r?\n/).filter(l => l.trim());
      const step = Math.floor(durMs / Math.max(ls.length, 1));
      raw = ls.map((t, i) => [i * step, t]);
    }
    const lines = raw.map(([t, s]) => [t, clean(s)]).filter(l => l[1]);
    return lines.map(([start, text], i) => {
      const next = i + 1 < lines.length ? lines[i + 1][0] : start + 6000;
      const span = Math.min(6500, Math.max(900, next - start));
      const end = Math.min(next, start + span + 400);
      const h = jhash(text);
      const adlib = ADLIB.test(text.replace(/ /g, ''));
      const ws = text.split(' ');
      const rowsIdx = adlib ? [ws.map((_, k) => k)] : chunkRows(ws);
      const total = ws.reduce((a, w) => a + w.length + 1, 0);
      let cum = 0;
      const times = ws.map(w => { const t = start + Math.floor(span * 0.8 * cum / total); cum += w.length + 1; return t; });
      const cands = ws.map((w, k) => k).filter(k => ws[k].length >= 4);
      const redIdx = !adlib && cands.length && h % 5 < 3 ? cands[Math.floor(h / 7) % cands.length] : -1;
      const rows = rowsIdx.map(r => r.map((wi, k) => ({
        text: ws[wi], at: times[wi], red: wi === redIdx,
        gapEm: k === 0 ? 0 : (wi === ws.length - 1 && r.length >= 2 ? 2.2 : 0.56)
      })));
      const x = adlib ? 0.504 : 0.285 + ((h >> 3) % 25) / 1000;
      const y = adlib ? 0.521 : Math.max(0.2, Math.min(0.404 + ((h >> 9) % 40) / 1000, 0.62 - rows.length * 0.0694));
      return { start, end, adlib, rows, x, y };
    });
  }
  return { parseLrc, buildPhrases, clean, chunkRows };
});
