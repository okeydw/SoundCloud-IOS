<p align="center">
<a href="../../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-0048FF?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="README.tr.md"><img src="https://img.shields.io/badge/TR-777777?style=flat-square" alt="Türkçe"/></a>
<a href="README.ko.md"><img src="https://img.shields.io/badge/KO-777777?style=flat-square" alt="한국어"/></a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="../icon.png" width="160px" />
</a>
</p>

<h1 align="center">SoundCloud iOS</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Desktop version</a>] · [<a href="https://github.com/okeydw/SoundCloud-Android">Android version</a>]</b><br>
<b>Unofficial SoundCloud client for iPhone and iPad</b><br>
No ads · No captcha · No censorship · Works in Russia
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="https://img.shields.io/github/v/release/okeydw/SoundCloud-iOS?style=for-the-badge&logo=github&color=0048FF&label=VERSION" alt="Version"/>
</a>

<a href="https://github.com/okeydw/SoundCloud-iOS/releases">
<img src="https://img.shields.io/github/downloads/okeydw/SoundCloud-iOS/total?style=for-the-badge&logo=github&color=0048FF&label=Downloads" alt="Downloads"/>
</a>

<a href="../../LICENSE">
<img src="https://img.shields.io/badge/License-MIT-0048FF?style=for-the-badge" alt="License"/>
</a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="https://img.shields.io/badge/Download_IPA-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="Download IPA"/>
</a>
</p>

---

## What is it?

**SoundCloud iOS** is a native SoundCloud client for iPhone and iPad. It is synced with [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop).

Written in **Kotlin + Compose Multiplatform**, with a Glass interface in the style of the desktop version. Currently in beta.

---

## Download

Go to the [releases page](https://github.com/okeydw/SoundCloud-iOS/releases/latest) and download the file you need:

| File | Install with |
| --- | --- |
| `SoundCloud-iOS-vX.Y.Z.tipa` | TrollStore |
| `SoundCloud-iOS-vX.Y.Z.ipa` | AltStore, SideStore, Sideloadly |

**Requirements:** iOS 15.0 or newer. The build is not signed with an Apple certificate, so it only installs through these tools.

**How to install:** step-by-step guide [here](https://docs.getsoundcloud.app/install/ios).

**Not on iOS yet:** import, equalizer, SoundCloud Auto, audio output selection and in-app updates.

---

## How to sign in

Next comes sign-in, either through SoundCloud or with a QR code from the Desktop version.

1. **QR from desktop (recommended).** In [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) open Settings -> Account -> Transfer session to another device, then scan the QR code.
2. **QR from phone.** On the sign-in screen tap "Show QR to sign in" and scan it where you are already signed in.
3. **Through the browser.** The "Sign in with browser" button opens the regular SoundCloud sign-in. This session may drop if you sign in somewhere else later.

---

## Screenshots

> Background, accent and text colors can be changed with an RGB palette. You can also add a background image and effects.

<p align="center">
<img src="../screenshots/full/wave.png" width="24%" />
<img src="../screenshots/full/player.png" width="24%" />
<img src="../screenshots/full/search.png" width="24%" />
<img src="../screenshots/full/suggestions.png" width="24%" />
</p>
<p align="center">
<img src="../screenshots/full/library.png" width="24%" />
<img src="../screenshots/full/playlists.png" width="24%" />
<img src="../screenshots/full/settings.png" width="24%" />
</p>

---

## Features

### Music

- **Wave and recommendations** - our own recommendation system based on your likes and listening history
- **Search** - tracks, artists, playlists, albums and vibe search (a neural network picks tracks based on context)
- **Player** - shuffle, repeat, queue, lyrics, likes and dislikes
- **Playlists** - your own local playlists synced to SoundCloud, public or private, track order changes by drag and drop and is saved
- **History and stats** - listening time, top tracks and artists, charts by day and by hour
- **Profiles** - follow and unfollow, links from the SoundCloud profile

### Offline mode

- Download tracks and playlists, offline mode with cache

---

## Feedback
Community: [Discord](https://discord.gg/Au3ebtfYu3)

Found a bug or have an idea? The easiest way is "Settings -> About -> iOS app support" or a ticket in Discord. You can also [open an issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) manually and attach the logs: "Settings -> Logs -> Copy".

---

## Stack

| Layer | Technologies |
| --- | --- |
| Language | Kotlin |
| UI | Compose Multiplatform, Haze (glass) |
| Audio | AVFoundation, background playback and lock screen controls |
| Network | NSURLSession, kotlinx.serialization |
| Images | Coil 3 |
| Camera | AVFoundation (QR scanner) |
| Storage | NSUserDefaults (settings, session), JSON indexes for downloads and playlists |

Backend: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

Host status is [here](https://status.soundcloud-desktop.fun/)

**Compatibility:** iOS 15.0 or newer, iPhone and iPad.

The full list of changes is in [CHANGELOG.md](../CHANGELOG.en.md).

---

## For developers

**Build:** macOS, Xcode, JDK 17, [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
./gradlew :core:compileKotlinIosArm64 :ui:compileKotlinIosArm64   # shared code compiles for iOS
cd iosApp && xcodegen generate                                    # Xcode project from project.yml
open SoundCloud.xcodeproj                                         # the Kotlin framework builds itself
```

**Sources:**

| Folder | What is inside |
| --- | --- |
| `core/src/commonMain` | shared logic: API, player, downloads, playlists |
| `core/src/iosMain` | iOS: AVFoundation player, network, downloads, storage |
| `ui/src/commonMain` | Glass interface |
| `ui/src/iosMain` | iOS: settings, QR scanner, entry point |
| `iosApp` | Xcode project (XcodeGen) |

**Release:** a `vX.Y.Z` tag (must match `versionName` in `app/build.gradle.kts`) builds unsigned `.ipa` and `.tipa` files and publishes them in the release. For a test build without a release, the `iOS` workflow runs manually in Actions or on a commit with `[ipa]` in the message.

**Backend:**

| Domain | Purpose |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | audio |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | track storage |
| `images.scnative.space` | covers |
| `pay.scnative.space` | STAR payment |
| `health.scnative.space` | connection check |
| `call-*.scnative.space` | "User network" |
| `*.relay.scnative.space` | backup routes |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | direct track download from SoundCloud |

QR sign-in protocol: [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) in the desktop repository.

---


## License

MIT. See the [LICENSE](../../LICENSE) file for details.

_SoundCloud is a trademark of SoundCloud Ltd. This app is not affiliated with SoundCloud._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="https://img.shields.io/badge/Download_SoundCloud_iOS-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="Download SoundCloud iOS"/>
</a>
</p>

<p align="center">
<code>soundcloud ios</code> · <code>soundcloud iphone</code> · <code>soundcloud ipa</code> · <code>soundcloud ipad</code> · <code>soundcloud trollstore</code> · <code>soundcloud client</code> · <code>soundcloud no ads</code> · <code>soundcloud blocked</code> · <code>soundcloud not working</code> · <code>soundcloud without captcha</code> · <code>soundcloud alternative client</code> · <code>soundcloud music player</code>
</p>
