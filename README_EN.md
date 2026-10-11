# PureFrame

> Pure viewing, frame by frame

[简体中文](./README.md) | English

**PureFrame** is a minimalist Android local video player with magnet / direct-link download and stream-while-downloading support.

- **Ad-free** - zero promotion, zero interruption
- **Immersive black** - a premium viewing experience
- **Minimal design** - clean and restrained UI

## ✨ Features

### Local Video Playback
- Automatic local video scanning (mp4 / mkv / mov / avi / flv / ts, etc.) using fine-grained media permissions (`READ_MEDIA_VIDEO`) — no "all files access" required; extra scan folders can be added under Settings → Video Library
- Three decoding modes: **Hardware** (hardware acceleration) / **Software** (software decoding, adds DTS/AC3/EAC3 audio and H.264/HEVC/VP9) / **Auto** (prefer hardware, fall back to software)
- Playback speed from 0.5x to 3x, with presets and a custom speed option
- Gestures for brightness, volume, and seeking; double-tap to play/pause; rewind / forward 10 seconds
- Fullscreen immersive playback with edge-to-edge support; aspect ratio options "Auto (no cropping) / Fill (fill the screen)"
- Picture-in-Picture, with an option to auto-enter system PiP when returning to the home screen
- Playback lock: lock the controls with one tap to avoid accidental touches
- Playback position memory with a resume prompt; loop playback supported
- **Side-loaded subtitles**: automatically loads same-named `.srt` / `.ass` / `.ssa` / `.vtt` files next to the video, with a translucent background; the Settings toggle applies instantly
- **Same-folder queue playback**: episodes play in sequence with natural sorting; skip prev/next from the notification
- Library filters for All / Favorites / Recent, plus search by name
- Long-press a video for a menu: Play / Details / Delete record / Delete file; the details view shows size, duration, resolution, format, play count, last played, and file path
- Favorites: bookmark videos from the player or the library list
- Sorting: date / size / duration, persisted across sessions
- Themes: light / dark / follow system; app language can be set to Simplified Chinese / English / follow system

### Casting
- **DLNA / UPnP**: automatically discovers castable devices on the local network; push videos to TVs and set-top boxes with volume and playback control
  - Devices are filtered by their `AVTransport` capability, so routers and NAS media servers never appear in the list
  - Shows a "Connecting…" state and reports failures inline in the picker with a retry option
  - While casting, the local progress bar follows the remote playback position, and the session ends automatically when playback finishes
  - Optional "auto-connect to last device"
- **Google Cast**: native support for Chromecast and built-in Cast receivers
- Degrades gracefully on devices without GMS, without affecting other features

### Magnet / Direct-link Download
- Three link types: `magnet:` links, `.torrent` files (including direct `.torrent` links), and HTTP/HTTPS direct downloads
- For torrents, choose which files to download (in-torrent file selection)
- Parallel tasks (1–5 configurable) with pause, resume, delete (with or without files), and bulk clearing of completed records
- Custom download directory (SAF folder picker), Wi-Fi-only option
- Background download service with a progress notification

### Stream-while-downloading (highlight)
- Playback starts once ~10% is downloaded; streamable tasks are badged "✓ Streamable" with one-tap playback from the download list
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
| Compile / Target | compileSdk & targetSdk 36, minimum Android 8.0 (API 26) |

## 🔄 CI

GitHub Actions (`.github/workflows/`):

- Pushes to main trigger a snapshot release named after the commit
- Semantic version tags (`v*`) trigger a stable release: debug/release-signed APKs + AAB + SHA256SUMS checksums
- Successful releases automatically refresh the self-hosted F-Droid repo index

## 📦 Project Structure

```
pureframe/
├── code/              # Source code (Gradle root)
│   └── app/           # Android application module
├── docs/              # Requirements, technical design, and distribution docs
├── fastlane/          # App store metadata
├── LICENSE            # GPL-3.0
├── NOTICE             # Third-party license notices
├── CONTRIBUTING.md    # Contribution guide
├── SECURITY.md        # Security policy
└── DISCLAIMER.md      # Disclaimer
```

## 📥 Download

- **GitHub Releases**: grab the latest APK from the [releases page](https://github.com/ayukyo/pureframe/releases/latest) (SHA256SUMS checksums included)
- **Self-hosted F-Droid repo**: in the F-Droid client, go to "Settings → Repositories" and add
  - URL: `https://ayukyo.github.io/pureframe/repo?fingerprint=466896A633FCD0210E479F6A72673D4D5FF324F20BB8062E3915491D769B755F`
  - (or just `https://ayukyo.github.io/pureframe/repo/` and verify the fingerprint matches the value above)
- **Obtainium**: add this repo URL `https://github.com/ayukyo/pureframe` for automatic updates

## 🤝 Contributing

Issues and PRs are welcome — please read [CONTRIBUTING.md](./CONTRIBUTING.md) first. If you like the app, consider giving it a Star ⭐ on the [releases page](https://github.com/ayukyo/pureframe/releases/latest).

## 🔒 Privacy

**No data collection. No tracking. No ads.** See the [privacy policy](./docs/privacy-policy.md). Also available in-app (Settings → About → Privacy policy).

## 📄 License

Licensed under the [GNU General Public License v3.0](./LICENSE).

- Why GPL-3.0: the bundled FFmpeg software-decoding extension [NextLib](https://github.com/anilbeesetti/nextlib) is licensed under GPL-3.0, which requires this combined work to be distributed under the same license.
- Third-party components and their licenses: [NOTICE](./NOTICE); also viewable in-app (Settings → About → Open-source licenses).
- **Please read the [disclaimer](./DISCLAIMER.md) before use**: this app is a client-side tool only. It does not host, store, or distribute any content. Users are solely responsible for complying with the laws of their jurisdiction when using the download feature.
