<p align="center">
<a href="README.md"><img src="https://img.shields.io/badge/RU-0048FF?style=flat-square" alt="Русский"/></a>
<a href="docs/README-lang/README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="docs/README-lang/README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="docs/README-lang/README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="docs/README-lang/README.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="docs/README-lang/README.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="docs/README-lang/README.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="docs/README-lang/README.tr.md"><img src="https://img.shields.io/badge/TR-777777?style=flat-square" alt="Türkçe"/></a>
<a href="docs/README-lang/README.ko.md"><img src="https://img.shields.io/badge/KO-777777?style=flat-square" alt="한국어"/></a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="docs/icon.png" width="160px" />
</a>
</p>

<h1 align="center">SoundCloud iOS</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Desktop версия</a>] · [<a href="https://github.com/okeydw/SoundCloud-Android">Android версия</a>]</b><br>
<b>Неофициальный клиент SoundCloud для iPhone и iPad</b><br>
Без рекламы · Без капчи · Без цензуры · Доступно в России
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="https://img.shields.io/github/v/release/okeydw/SoundCloud-iOS?style=for-the-badge&logo=github&color=0048FF&label=VERSION" alt="Version"/>
</a>

<a href="https://github.com/okeydw/SoundCloud-iOS/releases">
<img src="https://img.shields.io/github/downloads/okeydw/SoundCloud-iOS/total?style=for-the-badge&logo=github&color=0048FF&label=Downloads" alt="Downloads"/>
</a>

<a href="LICENSE">
<img src="https://img.shields.io/badge/License-MIT-0048FF?style=for-the-badge" alt="License"/>
</a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="https://img.shields.io/badge/Скачать_IPA-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="Download IPA"/>
</a>
</p>

---

## Что это?

**SoundCloud iOS** - нативный клиент SoundCloud для iPhone и iPad. Синхронизирован с [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop).

Написан на **Kotlin + Compose Multiplatform**, интерфейс в Glass-дизайне в стиле десктопной версии. Сейчас в бете.

---

## Скачать

Перейди на [страницу релизов](https://github.com/okeydw/SoundCloud-iOS/releases/latest) и скачай нужный файл:

| Файл | Чем ставить |
| --- | --- |
| `SoundCloud-iOS-vX.Y.Z.tipa` | TrollStore |
| `SoundCloud-iOS-vX.Y.Z.ipa` | AltStore, SideStore, Sideloadly |

**Требования:** iOS 15.0 или новее. Сборка не подписана сертификатом Apple, поэтому ставится только через эти программы.

**Как установить:** пошаговая инструкция [здесь](https://docs.getsoundcloud.app/install/ios).

**Пока нет на iOS:** импорт, эквалайзер, SoundCloud Auto, выбор аудиовыхода и обновление из приложения.

---

## Как войти

Дальше идёт авторизация, через SoundCloud, либо по QR-коду с Desktop версии

1. **QR с десктопа (рекомендовано).** В [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) открой Настройки -> Аккаунт -> Передать сессию на другое устройство, После чего сканируем QR-код.
2. **QR с телефона.** На экране входа нажми "Показать QR для входа" и отсканируй его там, где ты уже вошёл.
3. **Через браузер.** Кнопка "Войти через браузер" открывает обычный вход SoundCloud. Такая сессия может слетать, если потом войти где-то ещё.

---

## Скриншоты

> Цвет фона, акцента, текста можно менять по RGB палитре. Также добавление фоновой картинок и эффектов

<p align="center">
<img src="docs/screenshots/full/wave.png" width="24%" />
<img src="docs/screenshots/full/player.png" width="24%" />
<img src="docs/screenshots/full/search.png" width="24%" />
<img src="docs/screenshots/full/suggestions.png" width="24%" />
</p>
<p align="center">
<img src="docs/screenshots/full/library.png" width="24%" />
<img src="docs/screenshots/full/playlists.png" width="24%" />
<img src="docs/screenshots/full/settings.png" width="24%" />
</p>

---

## Функционал

### Музыка

- **Волна и рекомендации** - своя система рекомендаций на основе лайков и истории прослушивания
- **Поиск** - треки, артисты, плейлисты, альбомы и вайб-поиск (нейронка подбирает трек, согласно контексту)
- **Плеер** - шафл, повтор, очередь, текст песен, лайки и дизлайки
- **Плейлисты** - свои локальные плейлисты с синхронизацией в SoundCloud, публичные или приватные, порядок треков меняется перетаскиванием и сохраняется
- **История и статистика** - время прослушивания, топ треков и артистов, график по дням и по часам
- **Профили** - подписка и отписка, ссылки из профиля SoundCloud

### Оффлайн режим

- Скачивание треков и плейлистов, автономный режим с кэшем

---

## Обратная связь
Коммьюнити: [Discord](https://discord.gg/Au3ebtfYu3)

Нашёл баг или есть идея? Проще всего через "Настройки -> О системе -> Поддержка iOS-приложения" или тикет в Discord. Можно сразу [открыть issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) вручную и приложить логи: "Настройки -> Логи -> Копировать".

---

## Стек

| Слой | Технологии |
| --- | --- |
| Язык | Kotlin |
| UI | Compose Multiplatform, Haze (стекло) |
| Аудио | AVFoundation, фоновое воспроизведение и управление с экрана блокировки |
| Сеть | NSURLSession, kotlinx.serialization |
| Изображения | Coil 3 |
| Камера | AVFoundation (сканер QR) |
| Хранение | NSUserDefaults (настройки, сессия), JSON-индексы загрузок и плейлистов |

Бекенд: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

Статус хостов [здесь](https://status.soundcloud-desktop.fun/)

**Совместимость:** iOS 15.0 и новее, iPhone и iPad.

Полный список изменений в [CHANGELOG.md](CHANGELOG.md).

---

## Для разработчиков

**Сборка:** macOS, Xcode, JDK 17, [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
./gradlew :core:compileKotlinIosArm64 :ui:compileKotlinIosArm64   # общий код собирается под iOS
cd iosApp && xcodegen generate                                    # проект Xcode из project.yml
open SoundCloud.xcodeproj                                         # Kotlin-фреймворк соберётся сам
```

**Исходники:**

| Папка | Что внутри |
| --- | --- |
| `core/src/commonMain` | общая логика: API, плеер, загрузки, плейлисты |
| `core/src/iosMain` | iOS: плеер на AVFoundation, сеть, загрузки, хранилище |
| `ui/src/commonMain` | Glass-интерфейс |
| `ui/src/iosMain` | iOS: настройки, сканер QR, точка входа |
| `iosApp` | проект Xcode (XcodeGen) |

**Релиз:** тег `vX.Y.Z` (должен совпадать с `versionName` в `app/build.gradle.kts`) собирает неподписанные `.ipa` и `.tipa` и выкладывает их в релиз. Тестовую сборку без релиза делает workflow `iOS`: вручную в Actions или коммитом с `[ipa]` в сообщении.

**Бэкенд:**

| Домен | Для чего |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | аудио |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | хранилище треков |
| `images.scnative.space` | обложки |
| `pay.scnative.space` | оплата STAR |
| `health.scnative.space` | проверка связи |
| `call-*.scnative.space` | «Сеть пользователей» |
| `*.relay.scnative.space` | резервные маршруты |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | прямая загрузка трека с SoundCloud |

Протокол входа по QR: [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) в репозитории десктопа.

---


## Лицензия

MIT. Подробности в файле [LICENSE](LICENSE).

_SoundCloud - торговая марка SoundCloud Ltd. Это приложение не аффилировано с SoundCloud._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="https://img.shields.io/badge/Скачать_SoundCloud_iOS-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="Скачать SoundCloud iOS"/>
</a>
</p>

<p align="center">
<code>soundcloud ios</code> · <code>soundcloud iphone</code> · <code>soundcloud ipa</code> · <code>soundcloud ipad</code> · <code>soundcloud trollstore</code> · <code>soundcloud для айфона</code> · <code>soundcloud без рекламы</code> · <code>soundcloud россия</code> · <code>soundcloud не работает</code> · <code>soundcloud заблокирован</code> · <code>soundcloud без капчи</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code>
</p>
