# JPEG for Windows

Electron version of the JPEG music app: album grid, release sheet with the sleeve and record, tracklist, waveform scrubber,
white-screen lyric scene, Discover (Audius + radio), playlists, search, and the Studio settings (glass styles, EQ, crossfade, speed).

## Get the .exe (no Windows PC needed)
Push this folder to GitHub, then either push a tag (`git tag v1.0 && git push origin v1.0`) or open
**Actions → Build Windows → Run workflow**. Download `JPEG-windows` from the run's Artifacts, or from the Release for the tag:
- `JPEG Setup 1.0.0.exe` installer
- `JPEG 1.0.0.exe` portable (no install)

## Run from source
```
cd windows
npm install
npm start
```

## Keys
Space play/pause · ← → seek 5 s · Shift+← → previous/next · ↑ ↓ volume · L lyrics · Esc close
