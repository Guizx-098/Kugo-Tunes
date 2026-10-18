<p align="center">
  <img src="githubdocs/logo.png" alt="Logo">
</p>

<h1 align="center">Kugo Tunes</h1>

<p align="center">
  <img src="https://img.shields.io/github/v/release/Guizx-098/spd-turbo?style=for-the-badge&color=1DB954" alt="Release">
  <img src="https://img.shields.io/github/license/Guizx-098/spd-turbo?style=for-the-badge" alt="License">
  <img src="https://img.shields.io/github/stars/Guizx-098/spd-turbo?style=for-the-badge" alt="Stars">
</p>

<p align="center">
  <strong>Android app that downloads music and videos from Spotify, YouTube Music and YouTube straight to your phone, fully tagged, with cover art.</strong><br>
  <em>App Android para baixar músicas e vídeos do Spotify, YouTube Music e YouTube, com metadados e capa.</em>
</p>

---

## ✨ Features

- 🧭 **Choose what to download**: the home screen asks "What would you like to download?" with Spotify, YouTube / YouTube Music, TikTok, Kwai, Instagram and Facebook, and adapts the next steps to the platform.
- 🎵 **Spotify**: playlists straight to the audio flow, no API keys or login, with the same **MP3 / M4A** choice as YouTube Music. Links shared from the Spotify app are accepted through Android's share menu.
- ▶️ **YouTube / YouTube Music**: choose **Video** or **Music**.
  - **Video**: picks the highest resolution available; when the best quality comes as separate video and audio streams, they are joined locally without re-encoding. Output is MP4 (H.264) or WebM (VP9) depending on the source.
  - **Music**: choose **MP3** or **M4A** (M4A keeps the original audio, MP3 is converted).
  - **Audio track**: the original audio track is preferred over automatic dubs. If a video has several tracks and the original cannot be identified, you choose the track in the queue (single videos).
- 📱 **TikTok, Instagram Reels and Facebook videos**: paste a public link and the app downloads it with [yt-dlp](https://github.com/yt-dlp/yt-dlp) (via [youtubedl-android](https://github.com/yausername/youtubedl-android)), the open-source extractor that follows each platform's changes. It asks for the highest quality available (video and audio are joined without re-encoding) and avoids the formats a platform marks as watermarked. Whether a watermark-free file exists depends on the platform and cannot be guaranteed. Only public content: private posts, Stories and anything that needs a login are reported with a clear message.
- 📱 **Kwai (experimental)**: reads the video address the public page provides; it may fail if Kwai changes or blocks access.
- 🔙 **Navigation**: after a download finishes (or fails) the back arrow and Android's back button return to the platform list, and *Download Another* starts a new one without restarting the app.
- ⚡ **Simultaneous downloads (1–4)**: set in Preferences (default 1). Each track runs its own download → convert → finish pipeline, and one failure never stops the others.
- 🎧 **MP3 conversion**: applied to every track, with a strict check so a track is only "Complete" after the conversion really finished.
- 🏷️ **Embedded metadata and cover art**: title, artist and cover are written into every file.
- 🌐 **Languages**: English (default) and Português (Brasil), selectable in Preferences and remembered between sessions.
- 🚀 **In-app updates from GitHub Releases**: automatic check at startup (can be turned off), a *Check for updates* button, update dialog with release notes, APK download with progress and install, with a "Not now" option that is remembered per version.
- 📂 **Storage Access Framework**: choose any folder on your device to save the downloads.
- 🔄 **Queue management**: per-track status (waiting, downloading, processing, converting, complete, error), cancel and retry failed tracks.
- 🔔 **Background download service** with live progress in the notification.
- 🎨 **Material 3 design** with light and dark themes, built from the Kugo Tunes icon colors.

---

## 📥 Installation

1. Open the [**Releases**](https://github.com/Guizx-098/spd-turbo/releases/latest) page.
2. Download `Kugo-Tunes.apk` from **Assets**.
3. Open the APK on your Android phone and allow installing from this source if Android asks.
4. Open **Kugo Tunes**, choose the download folder in **Preferences** and start downloading.

**Updating:** the app tells you when a new Release is published. You can also use **Preferences → Check for updates**.

> All releases are signed with the same key, so updates install over the previous version. If you have an older build signed with a different key, uninstall it once before installing.

**Instalação (pt-BR):** baixe o `Kugo-Tunes.apk` na página de [Releases](https://github.com/Guizx-098/spd-turbo/releases/latest), abra no celular e permita a instalação quando o Android pedir. Para atualizar, use **Preferências → Checar atualizações**.

---

## 🛠️ Tech Stack

| Component | Technology / Library |
| :--- | :--- |
| **UI Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3) |
| **Language** | Kotlin |
| **Audio / video sourcing** | [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) (YouTube), [yt-dlp](https://github.com/yt-dlp/yt-dlp) via youtubedl-android (TikTok, Instagram, Facebook) |
| **Media processing** | [FFmpeg Kit](https://github.com/arthenica/ffmpeg-kit) |
| **HTTP client** | [OkHttp](https://github.com/square/okhttp) |
| **Updates** | GitHub Releases API + Android PackageInstaller |

---

## 🚢 Publishing a release (maintainer)

1. Update `versionCode` / `versionName` in `app/build.gradle.kts` and the notes in `RELEASE_NOTES.md`.
2. Push to `main` with the commit message exactly `vX.Y.Z` (e.g. `v2.2.0`).
3. GitHub Actions builds the signed APK and publishes the Release with `Kugo-Tunes.apk`. The in-app updater reads that Release.

---

> Kugo Tunes is a modified version of [spotify-playlist-downloader](https://github.com/supersu-man/spotify-playlist-downloader) by Sumanth (MIT).

## 🤝 Contributing

Ideas or bugs? **[Create an issue](https://github.com/Guizx-098/spd-turbo/issues)**.

## 📜 License

The original code is under the **MIT License** (see [LICENSE](LICENSE)). The distributed app also bundles GPL-3.0 components (NewPipe Extractor, youtubedl-android) and others: see [NOTICE.md](NOTICE.md).
