# PureFrame

> Pure viewing, frame by frame

[简体中文](./README.md) | English

**PureFrame** is a minimalist Android local video player with magnet / direct-link download and stream-while-downloading support.

[![Latest Release](https://img.shields.io/github/v/release/ayukyo/pureframe?label=latest&color=white)](https://github.com/ayukyo/pureframe/releases/latest)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-white.svg)](./LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84.svg)](https://github.com/ayukyo/pureframe/releases/latest)
[![F-Droid](https://img.shields.io/badge/F--Droid-self--hosted%20repo-1976D2.svg)](https://ayukyo.github.io/pureframe/repo?fingerprint=466896A633FCD0210E479F6A72673D4D5FF324F20BB8062E3915491D769B755F)
[![GitHub Stars](https://img.shields.io/github/stars/ayukyo/pureframe?style=flat&color=F5C518)](https://github.com/ayukyo/pureframe/stargazers)

- **Ad-free** - zero promotion, zero interruption
- **Immersive black** - a premium viewing experience
- **Minimal design** - clean and restrained UI

<p align="center">
  <img src="https://ayukyo.github.io/pureframe/screenshots/library.png" width="250" alt="Library"/>
  &nbsp;
  <img src="https://ayukyo.github.io/pureframe/screenshots/player.png" width="250" alt="Player"/>
  &nbsp;
  <img src="https://ayukyo.github.io/pureframe/screenshots/settings.png" width="250" alt="Settings"/>
</p>

## ✨ Features

### Local Video Playback
- Automatic local video scanning (mp4 / mkv / mov, etc.) with fine-grained media permissions — no "all files access" required
- Hardware / software / auto decoding (software decoding adds DTS/AC3/EAC3 audio and H.264/HEVC/VP9), playback speed 0.5x ~ 3x
- Gestures for brightness, volume, and seeking — or switch to a short-video-style interaction with **vertical swipe to switch videos** and auto-advance playback
- Playback position memory, loop playback, playback lock, Picture-in-Picture, aspect ratio control
- Side-loaded subtitles: automatically loads same-named `.srt` / `.ass` / `.ssa` / `.vtt` files
- Same-folder queue playback: episodes play in sequence with natural sorting; skip prev/next from the notification
- Favorites, search, filters, and sorting, persisted across sessions

### Casting
- **DLNA / UPnP**: automatically discovers castable devices on the local network (routers and NAS are filtered out); push videos to TVs and set-top boxes with volume and playback control, clear failure feedback, and local progress following the remote session
- **Google Cast**: native support for Chromecast and built-in Cast receivers
- Cast session ends automatically when playback finishes; degrades gracefully on devices without GMS

### Download
- Supports `magnet:` links, `.torrent` files (including direct torrent links), and HTTP/HTTPS direct downloads
- Parallel tasks (1–5 configurable) with pause, resume, delete, and in-torrent file selection
- Custom download directory (SAF), Wi-Fi-only option, background downloads with progress notification

### Stream-while-downloading (highlight)
- Playback starts once ~10% is downloaded
- Seeking is limited to the downloaded range; the UI shows the cached percentage
- Downloading is unaffected during playback

## 🛠️ Tech Stack

| Category | Choice |
|----------|--------|
| Language | Kotlin |
| Architecture | MVVM + Hilt + Room + DataStore |
| UI | Jetpack Compose (Material3) |
| Player | Media3 / ExoPlayer (bundled with the NextLib FFmpeg software decoder) |
| Download engine | libtorrent4j |
| Casting | UPnPCast (DLNA / UPnP) + Media3 Cast |
| Requirements | Android 8.0 (API 26) and above |

## 📦 Download

- **GitHub Releases**: grab the latest APK from the [releases page](https://github.com/ayukyo/pureframe/releases/latest) (SHA256SUMS checksums included)
- **Self-hosted F-Droid repo**: in the F-Droid client, go to "Settings → Repositories" and add
  - URL: `https://ayukyo.github.io/pureframe/repo?fingerprint=466896A633FCD0210E479F6A72673D4D5FF324F20BB8062E3915491D769B755F`
  - (or just `https://ayukyo.github.io/pureframe/repo/` and verify the fingerprint matches the value above)
- **Obtainium**: add this repo URL `https://github.com/ayukyo/pureframe` for automatic updates

See [CHANGELOG.md](./CHANGELOG.md) for release notes.

## 🤝 Contributing

Issues and PRs are welcome — please read [CONTRIBUTING.md](./CONTRIBUTING.md) first. If you like the app, consider giving it a Star ⭐ on the [releases page](https://github.com/ayukyo/pureframe/releases/latest).

## 🔒 Privacy

**No data collection. No tracking. No ads.** See the [privacy policy](./docs/privacy-policy.md). Also available in-app (Settings → About → Privacy policy).

## 📄 License

Licensed under the [GNU General Public License v3.0](./LICENSE).

- Why GPL-3.0: the bundled FFmpeg software-decoding extension [NextLib](https://github.com/anilbeesetti/nextlib) is licensed under GPL-3.0, which requires this combined work to be distributed under the same license.
- Third-party components and their licenses: [NOTICE](./NOTICE).
- **Please read the [disclaimer](./DISCLAIMER.md) before use**: this app is a client-side tool only. It does not host, store, or distribute any content. Users are solely responsible for complying with the laws of their jurisdiction when using the download feature.
