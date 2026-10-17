<p align="center">
<a href="../../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/ES-0048FF?style=flat-square" alt="Español"/></a>
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
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">Versión de escritorio</a>] · [<a href="https://github.com/okeydw/SoundCloud-Android">Versión Android</a>]</b><br>
<b>Cliente no oficial de SoundCloud para iPhone y iPad</b><br>
Sin anuncios · Sin captcha · Sin censura · Disponible en Rusia
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
<img src="https://img.shields.io/badge/Descargar_IPA-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="Download IPA"/>
</a>
</p>

---

## ¿Qué es?

**SoundCloud iOS** es un cliente nativo de SoundCloud para iPhone y iPad. Está sincronizado con [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop).

Escrito en **Kotlin + Compose Multiplatform**, con una interfaz Glass al estilo de la versión de escritorio. Ahora mismo está en beta.

---

## Descargar

Ve a la [página de versiones](https://github.com/okeydw/SoundCloud-iOS/releases/latest) y descarga el archivo que necesites:

| Archivo | Se instala con |
| --- | --- |
| `SoundCloud-iOS-vX.Y.Z.tipa` | TrollStore |
| `SoundCloud-iOS-vX.Y.Z.ipa` | AltStore, SideStore, Sideloadly |

**Requisitos:** iOS 15.0 o superior. La compilación no está firmada con un certificado de Apple, así que solo se instala con estas herramientas.

**Cómo instalar:** guía paso a paso [aquí](https://docs.getsoundcloud.app/install/ios).

**Todavía no está en iOS:** importación, ecualizador, SoundCloud Auto, elección de salida de audio y actualizaciones desde la app.

---

## Cómo iniciar sesión

Después viene el inicio de sesión, a través de SoundCloud o con un código QR desde la versión de escritorio.

1. **QR desde el escritorio (recomendado).** En [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) abre Ajustes -> Cuenta -> Transferir sesión a otro dispositivo y luego escanea el código QR.
2. **QR desde el móvil.** En la pantalla de inicio de sesión pulsa "Mostrar QR para iniciar sesión" y escanéalo donde ya tengas la sesión iniciada.
3. **Con el navegador.** El botón "Iniciar sesión con el navegador" abre el inicio de sesión normal de SoundCloud. Esta sesión puede cerrarse si después inicias sesión en otro sitio.

---

## Capturas de pantalla

> Los colores del fondo, del acento y del texto se pueden cambiar con una paleta RGB. También puedes añadir una imagen de fondo y efectos.

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

## Funciones

### Música

- **Ola y recomendaciones** - sistema de recomendaciones propio basado en tus me gusta y tu historial de escucha
- **Búsqueda** - pistas, artistas, listas, álbumes y búsqueda por vibra (una red neuronal elige pistas según el contexto)
- **Reproductor** - aleatorio, repetición, cola, letras, me gusta y no me gusta
- **Listas** - tus propias listas locales sincronizadas con SoundCloud, públicas o privadas, el orden de las pistas se cambia arrastrando y se guarda
- **Historial y estadísticas** - tiempo de escucha, pistas y artistas más escuchados, gráficos por días y por horas
- **Perfiles** - seguir y dejar de seguir, enlaces del perfil de SoundCloud

### Modo sin conexión

- Descarga de pistas y listas, modo sin conexión con caché

---

## Comentarios
Comunidad: [Discord](https://discord.gg/Au3ebtfYu3)

¿Has encontrado un error o tienes una idea? Lo más fácil es "Ajustes -> Acerca del sistema -> Soporte de la app de iOS" o un ticket en Discord. También puedes [abrir un issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) a mano y adjuntar los registros: "Ajustes -> Registros -> Copiar".

---

## Tecnologías

| Capa | Tecnologías |
| --- | --- |
| Lenguaje | Kotlin |
| UI | Compose Multiplatform, Haze (cristal) |
| Audio | AVFoundation, reproducción en segundo plano y controles en la pantalla de bloqueo |
| Red | NSURLSession, kotlinx.serialization |
| Imágenes | Coil 3 |
| Cámara | AVFoundation (escáner QR) |
| Almacenamiento | NSUserDefaults (ajustes, sesión), índices JSON de descargas y listas |

Backend: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

Estado de los servidores [aquí](https://status.soundcloud-desktop.fun/)

**Compatibilidad:** iOS 15.0 o superior, iPhone y iPad.

La lista completa de cambios está en [CHANGELOG.md](../../CHANGELOG.md).

---

## Para desarrolladores

**Compilación:** macOS, Xcode, JDK 17, [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
./gradlew :core:compileKotlinIosArm64 :ui:compileKotlinIosArm64   # el código común compila para iOS
cd iosApp && xcodegen generate                                    # proyecto de Xcode desde project.yml
open SoundCloud.xcodeproj                                         # el framework de Kotlin se compila solo
```

**Código fuente:**

| Carpeta | Contenido |
| --- | --- |
| `core/src/commonMain` | lógica común: API, reproductor, descargas, listas |
| `core/src/iosMain` | iOS: reproductor con AVFoundation, red, descargas, almacenamiento |
| `ui/src/commonMain` | interfaz Glass |
| `ui/src/iosMain` | iOS: ajustes, escáner QR, punto de entrada |
| `iosApp` | proyecto de Xcode (XcodeGen) |

**Versión:** una etiqueta `vX.Y.Z` (debe coincidir con `versionName` en `app/build.gradle.kts`) compila archivos `.ipa` y `.tipa` sin firmar y los publica en la versión. Para una compilación de prueba sin versión, el workflow `iOS` se lanza a mano en Actions o con un commit con `[ipa]` en el mensaje.

**Backend:**

| Dominio | Para qué |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | audio |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | almacenamiento de pistas |
| `images.scnative.space` | portadas |
| `pay.scnative.space` | pago de STAR |
| `health.scnative.space` | comprobación de conexión |
| `call-*.scnative.space` | «Red de usuarios» |
| `*.relay.scnative.space` | rutas de respaldo |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | descarga directa de la pista desde SoundCloud |

Protocolo de inicio de sesión por QR: [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md) en el repositorio de escritorio.

---


## Licencia

MIT. Detalles en el archivo [LICENSE](../../LICENSE).

_SoundCloud es una marca registrada de SoundCloud Ltd. Esta aplicación no está afiliada a SoundCloud._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="https://img.shields.io/badge/Descargar_SoundCloud_iOS-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="Descargar SoundCloud iOS"/>
</a>
</p>

<p align="center">
<code>soundcloud ios</code> · <code>soundcloud iphone</code> · <code>soundcloud ipa</code> · <code>soundcloud ipad</code> · <code>soundcloud trollstore</code> · <code>cliente soundcloud</code> · <code>soundcloud sin anuncios</code> · <code>soundcloud bloqueado</code> · <code>soundcloud no funciona</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code>
</p>
