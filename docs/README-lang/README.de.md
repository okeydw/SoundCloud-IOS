<p align="center">
<a href="../../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/DE-0048FF?style=flat-square" alt="Deutsch"/></a>
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
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Desktop-Version</a>] · [<a href="https://github.com/okeydw/SoundCloud-Android">Android-Version</a>]</b><br>
<b>Inoffizieller SoundCloud-Client für iPhone und iPad</b><br>
Ohne Werbung · Ohne Captcha · Ohne Zensur · In Russland verfügbar
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
<img src="https://img.shields.io/badge/IPA_herunterladen-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="Download IPA"/>
</a>
</p>

---

## Was ist das?

**SoundCloud iOS** ist ein nativer SoundCloud-Client für iPhone und iPad. Er ist mit [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) synchronisiert.

Geschrieben in **Kotlin + Compose Multiplatform**, mit einer Glass-Oberfläche im Stil der Desktop-Version. Derzeit in der Beta.

---

## Download

Geh auf die [Release-Seite](https://github.com/okeydw/SoundCloud-iOS/releases/latest) und lade die passende Datei herunter:

| Datei | Installieren mit |
| --- | --- |
| `SoundCloud-iOS-vX.Y.Z.tipa` | TrollStore |
| `SoundCloud-iOS-vX.Y.Z.ipa` | AltStore, SideStore, Sideloadly |

**Voraussetzungen:** iOS 15.0 oder neuer. Der Build ist nicht mit einem Apple-Zertifikat signiert und lässt sich nur über diese Tools installieren.

**Installation:** Schritt-für-Schritt-Anleitung [hier](https://docs.getsoundcloud.app/install/ios).

**Noch nicht auf iOS:** Import, Equalizer, SoundCloud Auto, Wahl des Audioausgangs und Updates aus der App.

---

## Anmelden

Danach folgt die Anmeldung, entweder über SoundCloud oder per QR-Code aus der Desktop-Version.

1. **QR vom Desktop (empfohlen).** Öffne in [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) Einstellungen -> Konto -> Sitzung auf ein anderes Gerät übertragen und scanne dann den QR-Code.
2. **QR vom Handy.** Tippe auf dem Anmeldebildschirm auf "QR zum Anmelden zeigen" und scanne ihn dort, wo du schon angemeldet bist.
3. **Über den Browser.** Der Button "Mit Browser anmelden" öffnet die normale SoundCloud-Anmeldung. Diese Sitzung kann verloren gehen, wenn du dich später woanders anmeldest.

---

## Screenshots

> Hintergrund-, Akzent- und Textfarbe lassen sich per RGB-Palette ändern. Außerdem kannst du ein Hintergrundbild und Effekte hinzufügen.

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

## Funktionen

### Musik

- **Welle und Empfehlungen** - eigenes Empfehlungssystem auf Basis deiner Likes und deines Hörverlaufs
- **Suche** - Tracks, Künstler, Playlists, Alben und Vibe-Suche (ein neuronales Netz wählt Tracks passend zum Kontext)
- **Player** - Shuffle, Wiederholen, Warteschlange, Songtexte, Likes und Dislikes
- **Playlists** - eigene lokale Playlists mit Synchronisierung zu SoundCloud, öffentlich oder privat, die Reihenfolge lässt sich per Drag and Drop ändern und wird gespeichert
- **Verlauf und Statistik** - Hörzeit, Top-Tracks und Top-Künstler, Diagramme nach Tagen und Stunden
- **Profile** - folgen und entfolgen, Links aus dem SoundCloud-Profil

### Offline-Modus

- Tracks und Playlists herunterladen, Offline-Modus mit Cache

---

## Feedback
Community: [Discord](https://discord.gg/Au3ebtfYu3)

Einen Bug gefunden oder eine Idee? Am einfachsten über "Einstellungen -> Über das System -> Support für die iOS-App" oder ein Ticket in Discord. Du kannst auch direkt [ein Issue öffnen](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) und die Logs anhängen: "Einstellungen -> Logs -> Kopieren".

---

## Stack

| Ebene | Technologien |
| --- | --- |
| Sprache | Kotlin |
| UI | Compose Multiplatform, Haze (Glas) |
| Audio | AVFoundation, Wiedergabe im Hintergrund und Steuerung auf dem Sperrbildschirm |
| Netzwerk | NSURLSession, kotlinx.serialization |
| Bilder | Coil 3 |
| Kamera | AVFoundation (QR-Scanner) |
| Speicher | NSUserDefaults (Einstellungen, Sitzung), JSON-Indizes für Downloads und Playlists |

Backend: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

Status der Hosts [hier](https://status.soundcloud-desktop.fun/)

**Kompatibilität:** iOS 15.0 oder neuer, iPhone und iPad.

Alle Änderungen stehen in [CHANGELOG.md](../../CHANGELOG.md).

---

## Für Entwickler

**Build:** macOS, Xcode, JDK 17, [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
./gradlew :core:compileKotlinIosArm64 :ui:compileKotlinIosArm64   # gemeinsamer Code kompiliert für iOS
cd iosApp && xcodegen generate                                    # Xcode-Projekt aus project.yml
open SoundCloud.xcodeproj                                         # das Kotlin-Framework baut sich selbst
```

**Quellcode:**

| Ordner | Inhalt |
| --- | --- |
| `core/src/commonMain` | gemeinsame Logik: API, Player, Downloads, Playlists |
| `core/src/iosMain` | iOS: AVFoundation-Player, Netzwerk, Downloads, Speicher |
| `ui/src/commonMain` | Glass-Oberfläche |
| `ui/src/iosMain` | iOS: Einstellungen, QR-Scanner, Einstiegspunkt |
| `iosApp` | Xcode-Projekt (XcodeGen) |

**Release:** ein Tag `vX.Y.Z` (muss zu `versionName` in `app/build.gradle.kts` passen) baut unsignierte `.ipa`- und `.tipa`-Dateien und veröffentlicht sie im Release. Einen Test-Build ohne Release macht der Workflow `iOS`: manuell in Actions oder per Commit mit `[ipa]` in der Nachricht.

**Backend:**

| Domain | Wofür |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | Audio |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | Track-Speicher |
| `images.scnative.space` | Cover |
| `pay.scnative.space` | STAR-Zahlung |
| `health.scnative.space` | Verbindungsprüfung |
| `call-*.scnative.space` | „Nutzernetz“ |
| `*.relay.scnative.space` | Ausweichrouten |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | direkter Track-Download von SoundCloud |

QR-Anmeldeprotokoll: [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) im Desktop-Repository.

---


## Lizenz

MIT. Details in der Datei [LICENSE](../../LICENSE).

_SoundCloud ist eine Marke von SoundCloud Ltd. Diese App steht in keiner Verbindung zu SoundCloud._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="https://img.shields.io/badge/SoundCloud_iOS_herunterladen-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="SoundCloud iOS herunterladen"/>
</a>
</p>

<p align="center">
<code>soundcloud ios</code> · <code>soundcloud iphone</code> · <code>soundcloud ipa</code> · <code>soundcloud ipad</code> · <code>soundcloud trollstore</code> · <code>soundcloud client</code> · <code>soundcloud ohne werbung</code> · <code>soundcloud gesperrt</code> · <code>soundcloud funktioniert nicht</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code>
</p>
