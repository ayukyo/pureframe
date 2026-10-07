# PureFrame

> Pure viewing, frame by frame

[简体中文](./README.md) | English

**PureFrame** is a minimalist Android local video player with magnet download and stream-while-downloading support.

- **Ad-free** - zero promotion, zero interruption
- **Immersive black** - a premium viewing experience
- **Minimal design** - clean and restrained UI

## ✨ Features

### Local Video Playback
- Automatic local video scanning (mp4 / mkv / mov / avi / flv / ts, etc.) with fine-grained media permissions — no "all files access" required
- Hardware / software decoding switch (software decoding adds DTS/AC3/EAC3 audio and H.264/HEVC/VP9), playback speed 0.5x ~ 3x
- Gestures for brightness, volume, and seeking; double-tap to play/pause
- Playback position memory with resume prompt
- Fullscreen immersive playback with edge-to-edge support and Picture-in-Picture
- **Side-loaded subtitles**: automatically loads same-named `.srt` / `.ass` / `.ssa` / `.vtt` files next to the video, with a translucent background; the Settings toggle applies instantly
- **Same-folder queue playback**: episodes play in sequence with natural sorting; skip prev/next from the notification
- Favorites: bookmark videos from the player or the library list
- Sorting: date / size / duration, persisted across sessions
- Themes: light / dark / follow system; Settings → About shows version, author info, and a one-tap share button

### Casting
- **DLNA / UPnP**: discovers devices on the local network automatically; push videos to TVs and set-top boxes with volume and playback control
- **Google Cast**: native support for Chromecast and built-in Cast receivers
- Cast session ends automatically when playback finishes; degrades gracefully on devices without GMS

### Magnet Download
- Parse `magnet:` links / open `.torrent` files
- Parallel tasks (1–5 configurable), pause, resume, delete (with or without files)
- Custom download directory (SAF folder picker), Wi-Fi-only option
- Background download service with progress notification

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

GitHub Actions (`.github/workflows/`):

- Pushes to main trigger a snapshot release named after the commit
- Semantic version tags (`v*`) trigger a stable release: debug/release-signed APKs + AAB + SHA256SUMS checksums
- Successful releases automatically refresh the self-hosted F-Droid repo index

## 🔒 Privacy

**No data collection. No tracking. No ads.** See the [privacy policy](./docs/privacy-policy.md). Also available in-app (Settings → About → Privacy policy).

## 📥 Download

- **GitHub Releases**: grab the latest APK from the [releases page](https://github.com/ayukyo/pureframe/releases/latest) (SHA256SUMS checksums included)
- **Self-hosted F-Droid repo**: in the F-Droid client, go to "Settings → Repositories" and add
  - URL: `https://ayukyo.github.io/pureframe/repo?fingerprint=466896A633FCD0210E479F6A72673D4D5FF324F20BB8062E3915491D769B755F`
  - (or just `https://ayukyo.github.io/pureframe/repo/` and verify the fingerprint matches the value above)
- **Obtainium**: add this repo URL `https://github.com/ayukyo/pureframe` for automatic updates

## 🤝 Contributing

Issues and PRs are welcome — please read [CONTRIBUTING.md](./CONTRIBUTING.md) first. If you like the app, consider giving it a Star ⭐ on the [releases page](https://github.com/ayukyo/pureframe/releases/latest).

## 📄 License

Licensed under the [GNU General Public License v3.0](./LICENSE).

- Why GPL-3.0: the bundled FFmpeg software-decoding extension [NextLib](https://github.com/anilbeesetti/nextlib) is licensed under GPL-3.0, which requires this combined work to be distributed under the same license.
- Third-party components and their licenses: [NOTICE](./NOTICE); also viewable in-app (Settings → About).
- **Please read the [disclaimer](./DISCLAIMER.md) before use**: this app is a client-side tool only. It does not host, store, or distribute any content. Users are solely responsible for complying with the laws of their jurisdiction when using the magnet download feature.
