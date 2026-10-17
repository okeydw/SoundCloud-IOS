<p align="center">
<a href="../../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-0048FF?style=flat-square" alt="Беларуская"/></a>
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
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Desktop версія</a>] · [<a href="https://github.com/okeydw/SoundCloud-Android">Android версія</a>]</b><br>
<b>Неафіцыйны кліент SoundCloud для iPhone і iPad</b><br>
Без рэкламы · Без капчы · Без цэнзуры · Даступна ў Расіі
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
<img src="https://img.shields.io/badge/Спампаваць_IPA-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="Download IPA"/>
</a>
</p>

---

## Што гэта?

**SoundCloud iOS** - натыўны кліент SoundCloud для iPhone і iPad. Сінхранізаваны з [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop).

Напісаны на **Kotlin + Compose Multiplatform**, інтэрфейс у Glass-дызайне ў стылі дэсктопнай версіі. Пакуль у бэце.

---

## Спампаваць

Перайдзі на [старонку рэлізаў](https://github.com/okeydw/SoundCloud-iOS/releases/latest) і спампуй патрэбны файл:

| Файл | Чым ставіць |
| --- | --- |
| `SoundCloud-iOS-vX.Y.Z.tipa` | TrollStore |
| `SoundCloud-iOS-vX.Y.Z.ipa` | AltStore, SideStore, Sideloadly |

**Патрабаванні:** iOS 15.0 або навейшы. Зборка не падпісана сертыфікатам Apple, таму ставіцца толькі праз гэтыя праграмы.

**Пакуль няма на iOS:** імпарт, эквалайзер, SoundCloud Auto, выбар аўдыявыхаду і абнаўленне з праграмы.

---

## Як увайсці

Далей ідзе аўтарызацыя, праз SoundCloud або па QR-кодзе з Desktop версіі.

1. **QR з дэсктопа (рэкамендавана).** У [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) адкрый Налады -> Акаўнт -> Перадаць сесію на іншую прыладу, пасля чаго скануй QR-код.
2. **QR з тэлефона.** На экране ўваходу націсні "Паказаць QR для ўваходу" і адскануй яго там, дзе ты ўжо ўвайшоў.
3. **Праз браўзер.** Кнопка "Увайсці праз браўзер" адкрывае звычайны ўваход SoundCloud. Такая сесія можа зляцець, калі потым увайсці дзесьці яшчэ.

---

## Скрыншоты

> Колер фону, акцэнту і тэксту можна мяняць па RGB-палітры. Таксама можна дадаць фонавую карцінку і эфекты.

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

## Функцыянал

### Музыка

- **Хваля і рэкамендацыі** - уласная сістэма рэкамендацый на аснове лайкаў і гісторыі праслухоўвання
- **Пошук** - трэкі, артысты, плэйлісты, альбомы і вайб-пошук (нейрасетка падбірае трэк паводле кантэксту)
- **Плэер** - шафл, паўтор, чарга, тэксты песень, лайкі і дызлайкі
- **Плэйлісты** - свае лакальныя плэйлісты з сінхранізацыяй у SoundCloud, публічныя або прыватныя, парадак трэкаў мяняецца перацягваннем і захоўваецца
- **Гісторыя і статыстыка** - час праслухоўвання, топ трэкаў і артыстаў, графік па днях і па гадзінах
- **Профілі** - падпіска і адпіска, спасылкі з профілю SoundCloud

### Афлайн-рэжым

- Спампоўка трэкаў і плэйлістаў, аўтаномны рэжым з кэшам

---

## Зваротная сувязь
Супольнасць: [Discord](https://discord.gg/Au3ebtfYu3)

Знайшоў баг або ёсць ідэя? Прасцей за ўсё праз "Налады -> Пра сістэму -> Падтрымка iOS-праграмы" або тыкет у Discord. Можна адразу [адкрыць issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) уручную і прыкласці логі: "Налады -> Логі -> Капіяваць".

---

## Стэк

| Слой | Тэхналогіі |
| --- | --- |
| Мова | Kotlin |
| UI | Compose Multiplatform, Haze (шкло) |
| Аўдыё | AVFoundation, фонавае прайграванне і кіраванне з экрана блакіроўкі |
| Сетка | NSURLSession, kotlinx.serialization |
| Выявы | Coil 3 |
| Камера | AVFoundation (сканер QR) |
| Захоўванне | NSUserDefaults (налады, сесія), JSON-індэксы загрузак і плэйлістаў |

Бэкенд: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

Статус хостаў [тут](https://status.soundcloud-desktop.fun/)

**Сумяшчальнасць:** iOS 15.0 і навейшыя, iPhone і iPad.

Поўны спіс змяненняў у [CHANGELOG.md](../../CHANGELOG.md).

---

## Для распрацоўшчыкаў

**Зборка:** macOS, Xcode, JDK 17, [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
./gradlew :core:compileKotlinIosArm64 :ui:compileKotlinIosArm64   # агульны код збіраецца пад iOS
cd iosApp && xcodegen generate                                    # праект Xcode з project.yml
open SoundCloud.xcodeproj                                         # Kotlin-фрэймворк збярэцца сам
```

**Зыходнікі:**

| Папка | Што ўнутры |
| --- | --- |
| `core/src/commonMain` | агульная логіка: API, плэер, загрузкі, плэйлісты |
| `core/src/iosMain` | iOS: плэер на AVFoundation, сетка, загрузкі, сховішча |
| `ui/src/commonMain` | Glass-інтэрфейс |
| `ui/src/iosMain` | iOS: налады, сканер QR, кропка ўваходу |
| `iosApp` | праект Xcode (XcodeGen) |

**Рэліз:** тэг `vX.Y.Z` (павінен супадаць з `versionName` у `app/build.gradle.kts`) збірае непадпісаныя `.ipa` і `.tipa` і выкладвае іх у рэліз. Тэставую зборку без рэлізу робіць workflow `iOS`: уручную ў Actions або комітам з `[ipa]` у паведамленні.

**Бэкенд:**

| Дамен | Для чаго |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | аўдыё |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | сховішча трэкаў |
| `images.scnative.space` | вокладкі |
| `pay.scnative.space` | аплата STAR |
| `health.scnative.space` | праверка сувязі |
| `call-*.scnative.space` | «Сетка карыстальнікаў» |
| `*.relay.scnative.space` | рэзервовыя маршруты |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | прамая загрузка трэка з SoundCloud |

Пратакол уваходу па QR: [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) у рэпазіторыі дэсктопа.

---


## Ліцэнзія

MIT. Падрабязнасці ў файле [LICENSE](../../LICENSE).

_SoundCloud - гандлёвая марка SoundCloud Ltd. Гэта праграма не афіліяваная з SoundCloud._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="https://img.shields.io/badge/Спампаваць_SoundCloud_iOS-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="Спампаваць SoundCloud iOS"/>
</a>
</p>

<p align="center">
<code>soundcloud ios</code> · <code>soundcloud iphone</code> · <code>soundcloud ipa</code> · <code>soundcloud ipad</code> · <code>soundcloud trollstore</code> · <code>soundcloud кліент</code> · <code>soundcloud без рэкламы</code> · <code>soundcloud заблакаваны</code> · <code>soundcloud не працуе</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code>
</p>
