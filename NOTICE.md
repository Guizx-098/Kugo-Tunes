# Third-party components

Kugo Tunes is a modified version of [spotify-playlist-downloader](https://github.com/supersu-man/spotify-playlist-downloader)
by Sumanth (MIT License, see `LICENSE`, which keeps the original copyright notice).

The distributed app bundles these components, each under its own license:

| Component | License |
| :--- | :--- |
| [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) | GPL-3.0 |
| [youtubedl-android](https://github.com/yausername/youtubedl-android) (library and ffmpeg module) | GPL-3.0 |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Unlicense (public domain) |
| Python (bundled by youtubedl-android) | PSF License |
| [FFmpeg Kit](https://github.com/arthenica/ffmpeg-kit) (audio package) and FFmpeg | LGPL-3.0 / GPL (depending on build) |
| [TikWM](https://www.tikwm.com) (third-party web service, last-resort TikTok source) | Not bundled: external service with its own terms |
| [OkHttp](https://github.com/square/okhttp), AndroidX, Jetpack Compose | Apache-2.0 |

Because the app includes GPL-3.0 components, the combined app is distributed under the terms of the
[GNU GPL v3](https://www.gnu.org/licenses/gpl-3.0.html). Its complete source code is available in this repository.
This note is informational and is not legal advice.
