# PureFrame

> Pure viewing, frame by frame

[简体中文](./README.md) | English

**PureFrame** is a minimalist Android local video player with magnet download and stream-while-downloading support.

- **Ad-free** - zero promotion, zero interruption
- **Immersive black** - a premium viewing experience
- **Minimal design** - clean and restrained UI

## ✨ Features

### Local Video Playback
- Automatic local video scanning (mp4 / mkv / mov / avi / flv / ts, etc.)
- Hardware / software decoding switch, playback speed 0.5x ~ 3x
- Gestures for brightness, volume, and seeking; double-tap to play/pause
- Playback position memory with resume prompt
- Fullscreen immersive playback with edge-to-edge support
- **Side-loaded subtitles**: automatically loads same-named `.srt` / `.ass` / `.ssa` / `.vtt` files next to the video, with a translucent background; the Settings toggle applies instantly
- Sorting: date / size / duration, persisted across sessions

### Magnet Download
- Parse `magnet:` links / open `.torrent` files
- Parallel tasks (1–5 configurable), pause, resume, delete (with or without files)
- Custom download directory (SAF folder picker)
- Background download service

### Stream-while-downloading (highlight)
- Playback starts once ~10% is downloaded
- Seeking is limited to the downloaded range; the UI shows the cached percentage
- Downloading is unaffected during playback

## 🛠️ Tech Stack

| Category | Choice |
|----------|--------|
| Language | Kotlin |
| Architecture | MVVM + Hilt + DataStore |
| UI | Jetpack Compose (Material3) |
| Player | Media3 / ExoPlayer |
| Download engine | libtorrent4j |
| Minimum SDK | Android 8.0 (API 26) |

## 🔄 CI

GitHub Actions (`.github/workflows/build.yml`) runs `assembleDebug` + `lintDebug` on every push to main and every PR. APK and lint report are uploaded as artifacts (7-day retention).

## 📄 License

Licensed under the [Apache License 2.0](./LICENSE).

- Third-party components and their licenses: [NOTICE](./NOTICE)
- **Please read the [disclaimer](./DISCLAIMER.md) before use**: this app is a client-side tool only. It does not host, store, or distribute any content. Users are solely responsible for complying with the laws of their jurisdiction when using the magnet download feature.
