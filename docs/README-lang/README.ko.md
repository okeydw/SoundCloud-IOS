<p align="center">
<a href="../../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-777777?style=flat-square" alt="中文"/></a>
<a href="README.de.md"><img src="https://img.shields.io/badge/DE-777777?style=flat-square" alt="Deutsch"/></a>
<a href="README.fr.md"><img src="https://img.shields.io/badge/FR-777777?style=flat-square" alt="Français"/></a>
<a href="README.es.md"><img src="https://img.shields.io/badge/ES-777777?style=flat-square" alt="Español"/></a>
<a href="README.tr.md"><img src="https://img.shields.io/badge/TR-777777?style=flat-square" alt="Türkçe"/></a>
<a href="README.ko.md"><img src="https://img.shields.io/badge/KO-0048FF?style=flat-square" alt="한국어"/></a>
</p>

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="../icon.png" width="160px" />
</a>
</p>

<h1 align="center">SoundCloud iOS</h1>

<p align="center">
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">데스크톱 버전</a>] · [<a href="https://github.com/okeydw/SoundCloud-Android">Android 버전</a>]</b><br>
<b>iPhone과 iPad용 비공식 SoundCloud 클라이언트</b><br>
광고 없음 · 캡차 없음 · 검열 없음 · 러시아에서 사용 가능
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
<img src="https://img.shields.io/badge/IPA_다운로드-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="Download IPA"/>
</a>
</p>

---

## 무엇인가요?

**SoundCloud iOS**는 iPhone과 iPad용 네이티브 SoundCloud 클라이언트입니다. [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop)과 동기화됩니다.

**Kotlin + Compose Multiplatform**으로 작성되었고, 데스크톱 버전 스타일의 Glass 인터페이스를 씁니다. 현재 베타입니다.

---

## 다운로드

[릴리스 페이지](https://github.com/okeydw/SoundCloud-iOS/releases/latest)로 이동해 필요한 파일을 받으세요:

| 파일 | 설치 도구 |
| --- | --- |
| `SoundCloud-iOS-vX.Y.Z.tipa` | TrollStore |
| `SoundCloud-iOS-vX.Y.Z.ipa` | AltStore, SideStore, Sideloadly |

**요구 사항:** iOS 15.0 이상. Apple 인증서로 서명되지 않은 빌드라서 이 도구들로만 설치할 수 있습니다.

**아직 iOS에 없는 기능:** 가져오기, 이퀄라이저, SoundCloud Auto, 오디오 출력 선택, 앱 내 업데이트.

---

## 로그인 방법

다음은 로그인입니다. SoundCloud로 로그인하거나 데스크톱 버전의 QR 코드를 사용하세요.

1. **데스크톱의 QR (권장).** [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop)에서 설정 -> 계정 -> 다른 기기로 세션 전송을 연 뒤 QR 코드를 스캔하세요.
2. **휴대폰의 QR.** 로그인 화면에서 "로그인용 QR 표시"를 누르고, 이미 로그인된 기기에서 스캔하세요.
3. **브라우저로.** "브라우저로 로그인" 버튼은 일반 SoundCloud 로그인 화면을 엽니다. 나중에 다른 곳에서 로그인하면 이 세션이 끊길 수 있습니다.

---

## 스크린샷

> 배경, 강조, 텍스트 색상은 RGB 팔레트로 바꿀 수 있습니다. 배경 이미지와 효과도 추가할 수 있습니다.

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

## 기능

### 음악

- **웨이브와 추천** - 좋아요와 감상 기록을 바탕으로 한 자체 추천 시스템
- **검색** - 트랙, 아티스트, 플레이리스트, 앨범, 바이브 검색 (신경망이 맥락에 맞춰 트랙을 골라 줍니다)
- **플레이어** - 셔플, 반복, 재생 대기열, 가사, 좋아요와 싫어요
- **플레이리스트** - SoundCloud와 동기화되는 나만의 로컬 플레이리스트, 공개 또는 비공개, 드래그로 트랙 순서를 바꾸면 저장됩니다
- **기록과 통계** - 감상 시간, 인기 트랙과 아티스트, 일별 및 시간별 그래프
- **프로필** - 팔로우와 언팔로우, SoundCloud 프로필의 링크

### 오프라인 모드

- 트랙과 플레이리스트 다운로드, 캐시를 쓰는 오프라인 모드

---

## 피드백
커뮤니티: [Discord](https://discord.gg/Au3ebtfYu3)

버그를 찾았거나 아이디어가 있나요? 가장 쉬운 방법은 "설정 -> 시스템 정보 -> iOS 앱 지원" 또는 Discord 티켓입니다. 직접 [issue를 열고](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) 로그를 첨부해도 됩니다: "설정 -> 로그 -> 복사".

---

## 기술 스택

| 계층 | 기술 |
| --- | --- |
| 언어 | Kotlin |
| UI | Compose Multiplatform, Haze (유리 효과) |
| 오디오 | AVFoundation, 백그라운드 재생과 잠금 화면 제어 |
| 네트워크 | NSURLSession, kotlinx.serialization |
| 이미지 | Coil 3 |
| 카메라 | AVFoundation (QR 스캐너) |
| 저장소 | NSUserDefaults (설정, 세션), 다운로드와 플레이리스트의 JSON 인덱스 |

백엔드: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

호스트 상태는 [여기](https://status.soundcloud-desktop.fun/)에서 확인하세요

**호환성:** iOS 15.0 이상, iPhone과 iPad.

전체 변경 내역은 [CHANGELOG.md](../../CHANGELOG.md)에 있습니다.

---

## 개발자용

**빌드:** macOS, Xcode, JDK 17, [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
./gradlew :core:compileKotlinIosArm64 :ui:compileKotlinIosArm64   # 공용 코드가 iOS용으로 컴파일되는지 확인
cd iosApp && xcodegen generate                                    # project.yml에서 Xcode 프로젝트 생성
open SoundCloud.xcodeproj                                         # Kotlin 프레임워크는 자동으로 빌드됨
```

**소스:**

| 폴더 | 내용 |
| --- | --- |
| `core/src/commonMain` | 공용 로직: API, 플레이어, 다운로드, 플레이리스트 |
| `core/src/iosMain` | iOS: AVFoundation 플레이어, 네트워크, 다운로드, 저장소 |
| `ui/src/commonMain` | Glass 인터페이스 |
| `ui/src/iosMain` | iOS: 설정, QR 스캐너, 진입점 |
| `iosApp` | Xcode 프로젝트 (XcodeGen) |

**릴리스:** `vX.Y.Z` 태그(`app/build.gradle.kts`의 `versionName`과 같아야 함)를 달면 서명되지 않은 `.ipa`와 `.tipa`를 빌드해 릴리스에 올립니다. 릴리스 없는 테스트 빌드는 `iOS` 워크플로를 Actions에서 직접 실행하거나 메시지에 `[ipa]`를 넣은 커밋으로 만듭니다.

**백엔드:**

| 도메인 | 용도 |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | 오디오 |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | 트랙 저장소 |
| `images.scnative.space` | 커버 |
| `pay.scnative.space` | STAR 결제 |
| `health.scnative.space` | 연결 확인 |
| `call-*.scnative.space` | "사용자 네트워크" |
| `*.relay.scnative.space` | 예비 경로 |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | SoundCloud에서 트랙 직접 다운로드 |

QR 로그인 프로토콜: 데스크톱 저장소의 [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md).

---


## 라이선스

MIT. 자세한 내용은 [LICENSE](../../LICENSE) 파일을 참고하세요.

_SoundCloud는 SoundCloud Ltd.의 상표입니다. 이 앱은 SoundCloud와 관련이 없습니다._

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="https://img.shields.io/badge/SoundCloud_iOS_다운로드-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="SoundCloud iOS 다운로드"/>
</a>
</p>

<p align="center">
<code>soundcloud ios</code> · <code>soundcloud iphone</code> · <code>soundcloud ipa</code> · <code>soundcloud ipad</code> · <code>soundcloud trollstore</code> · <code>사운드클라우드 아이폰</code> · <code>광고 없는 사운드클라우드</code> · <code>사운드클라우드 차단</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code>
</p>
