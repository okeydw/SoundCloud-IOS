<p align="center">
<a href="../../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/FR-0048FF?style=flat-square" alt="Français"/></a>
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
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Version Desktop</a>] · [<a href="https://github.com/okeydw/SoundCloud-Android">Version Android</a>]</b><br>
<b>Client SoundCloud non officiel pour iPhone et iPad</b><br>
Sans pub · Sans captcha · Sans censure · Disponible en Russie
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
<img src="https://img.shields.io/badge/Télécharger_IPA-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="Download IPA"/>
</a>
</p>

---

## Qu'est-ce que c'est ?

**SoundCloud iOS** est un client SoundCloud natif pour iPhone et iPad. Il est synchronisé avec [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop).

Écrit en **Kotlin + Compose Multiplatform**, avec une interface Glass dans le style de la version Desktop. Actuellement en bêta.

---

## Télécharger

Va sur la [page des versions](https://github.com/okeydw/SoundCloud-iOS/releases/latest) et télécharge le fichier qu'il te faut :

| Fichier | S'installe avec |
| --- | --- |
| `SoundCloud-iOS-vX.Y.Z.tipa` | TrollStore |
| `SoundCloud-iOS-vX.Y.Z.ipa` | AltStore, SideStore, Sideloadly |

**Configuration requise :** iOS 15.0 ou plus récent. La compilation n'est pas signée avec un certificat Apple, elle ne s'installe donc qu'avec ces outils.

**Pas encore sur iOS :** import, égaliseur, SoundCloud Auto, choix de la sortie audio et mises à jour depuis l'application.

---

## Se connecter

Vient ensuite la connexion, via SoundCloud ou avec un QR code depuis la version Desktop.

1. **QR depuis le Desktop (recommandé).** Dans [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop), ouvre Paramètres -> Compte -> Transférer la session vers un autre appareil, puis scanne le QR code.
2. **QR depuis le téléphone.** Sur l'écran de connexion, appuie sur "Afficher le QR de connexion" et scanne-le là où tu es déjà connecté.
3. **Via le navigateur.** Le bouton "Se connecter via le navigateur" ouvre la connexion SoundCloud habituelle. Cette session peut sauter si tu te connectes ailleurs ensuite.

---

## Captures d'écran

> Les couleurs du fond, de l'accent et du texte se changent avec une palette RVB. Tu peux aussi ajouter une image de fond et des effets.

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

## Fonctionnalités

### Musique

- **Vague et recommandations** - notre propre système de recommandations basé sur tes likes et ton historique d'écoute
- **Recherche** - titres, artistes, playlists, albums et recherche par ambiance (un réseau neuronal choisit les titres selon le contexte)
- **Lecteur** - lecture aléatoire, répétition, file d'attente, paroles, likes et dislikes
- **Playlists** - tes propres playlists locales synchronisées avec SoundCloud, publiques ou privées, l'ordre des titres se change par glisser-déposer et reste enregistré
- **Historique et statistiques** - temps d'écoute, top titres et artistes, graphiques par jour et par heure
- **Profils** - s'abonner et se désabonner, liens du profil SoundCloud

### Mode hors ligne

- Téléchargement de titres et de playlists, mode hors ligne avec cache

---

## Retours
Communauté: [Discord](https://discord.gg/Au3ebtfYu3)

Tu as trouvé un bug ou tu as une idée ? Le plus simple est "Paramètres -> À propos du système -> Support de l'app iOS" ou un ticket sur Discord. Tu peux aussi [ouvrir une issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) toi-même et joindre les logs : "Paramètres -> Logs -> Copier".

---

## Stack

| Couche | Technologies |
| --- | --- |
| Langage | Kotlin |
| UI | Compose Multiplatform, Haze (verre) |
| Audio | AVFoundation, lecture en arrière-plan et commandes sur l'écran verrouillé |
| Réseau | NSURLSession, kotlinx.serialization |
| Images | Coil 3 |
| Caméra | AVFoundation (scanner QR) |
| Stockage | NSUserDefaults (paramètres, session), index JSON des téléchargements et des playlists |

Backend: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

État des serveurs [ici](https://status.soundcloud-desktop.fun/)

**Compatibilité :** iOS 15.0 ou plus récent, iPhone et iPad.

La liste complète des changements se trouve dans [CHANGELOG.md](../../CHANGELOG.md).

---

## Pour les développeurs

**Compilation :** macOS, Xcode, JDK 17, [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
./gradlew :core:compileKotlinIosArm64 :ui:compileKotlinIosArm64   # le code commun compile pour iOS
cd iosApp && xcodegen generate                                    # projet Xcode depuis project.yml
open SoundCloud.xcodeproj                                         # le framework Kotlin se compile tout seul
```

**Sources :**

| Dossier | Contenu |
| --- | --- |
| `core/src/commonMain` | logique commune : API, lecteur, téléchargements, playlists |
| `core/src/iosMain` | iOS : lecteur AVFoundation, réseau, téléchargements, stockage |
| `ui/src/commonMain` | interface Glass |
| `ui/src/iosMain` | iOS : paramètres, scanner QR, point d'entrée |
| `iosApp` | projet Xcode (XcodeGen) |

**Publication :** un tag `vX.Y.Z` (doit correspondre à `versionName` dans `app/build.gradle.kts`) compile des fichiers `.ipa` et `.tipa` non signés et les publie dans la version. Pour une compilation de test sans version, le workflow `iOS` se lance à la main dans Actions ou avec un commit contenant `[ipa]` dans le message.

**Backend :**

| Domaine | Rôle |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | audio |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | stockage des titres |
| `images.scnative.space` | pochettes |
| `pay.scnative.space` | paiement STAR |
| `health.scnative.space` | vérification de la connexion |
| `call-*.scnative.space` | « Réseau des utilisateurs » |
| `*.relay.scnative.space` | routes de secours |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | téléchargement direct du titre depuis SoundCloud |

Protocole de connexion par QR : [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) dans le dépôt Desktop.

---


## Licence

MIT. Détails dans le fichier [LICENSE](../../LICENSE).

_SoundCloud est une marque de SoundCloud Ltd. Cette application n'est pas affiliée à SoundCloud._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="https://img.shields.io/badge/Télécharger_SoundCloud_iOS-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="Télécharger SoundCloud iOS"/>
</a>
</p>

<p align="center">
<code>soundcloud ios</code> · <code>soundcloud iphone</code> · <code>soundcloud ipa</code> · <code>soundcloud ipad</code> · <code>soundcloud trollstore</code> · <code>client soundcloud</code> · <code>soundcloud sans pub</code> · <code>soundcloud bloqué</code> · <code>soundcloud ne fonctionne pas</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code>
</p>
