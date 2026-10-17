<p align="center">
<a href="../../README.md"><img src="https://img.shields.io/badge/RU-777777?style=flat-square" alt="Русский"/></a>
<a href="README.en.md"><img src="https://img.shields.io/badge/EN-777777?style=flat-square" alt="English"/></a>
<a href="README.be.md"><img src="https://img.shields.io/badge/BE-777777?style=flat-square" alt="Беларуская"/></a>
<a href="README.zh.md"><img src="https://img.shields.io/badge/ZH-0048FF?style=flat-square" alt="中文"/></a>
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
<b>[<a href="https://github.com/zxcloli666/SoundCloud-Desktop">桌面版</a>] · [<a href="https://github.com/okeydw/SoundCloud-Android">Android 版</a>]</b><br>
<b>非官方 SoundCloud iPhone 与 iPad 客户端</b><br>
无广告 · 无验证码 · 无审查 · 在俄罗斯可用
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
<img src="https://img.shields.io/badge/下载_IPA-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="Download IPA"/>
</a>
</p>

---

## 这是什么？

**SoundCloud iOS** 是一款原生的 iPhone 与 iPad SoundCloud 客户端，与 [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) 同步。

使用 **Kotlin + Compose Multiplatform** 编写，采用桌面版风格的 Glass 界面。目前处于测试阶段。

---

## 下载

前往 [发布页面](https://github.com/okeydw/SoundCloud-iOS/releases/latest) 下载所需的文件：

| 文件 | 安装方式 |
| --- | --- |
| `SoundCloud-iOS-vX.Y.Z.tipa` | TrollStore |
| `SoundCloud-iOS-vX.Y.Z.ipa` | AltStore, SideStore, Sideloadly |

**系统要求：** iOS 15.0 或更高版本。该构建未使用 Apple 证书签名，只能通过这些工具安装。

**iOS 暂不支持：** 导入、均衡器、SoundCloud Auto、音频输出选择以及应用内更新。

---

## 如何登录

接下来是登录，可以通过 SoundCloud，也可以扫描桌面版的二维码。

1. **桌面版二维码（推荐）。** 在 [SoundCloud-Desktop](https://github.com/zxcloli666/SoundCloud-Desktop) 中打开 设置 -> 账号 -> 将会话转移到其他设备，然后扫描二维码。
2. **手机二维码。** 在登录界面点击"显示登录二维码"，在已登录的设备上扫描。
3. **通过浏览器。** "通过浏览器登录"按钮会打开普通的 SoundCloud 登录页。之后如果在别处登录，此会话可能会失效。

---

## 截图

> 背景色、强调色和文字颜色可通过 RGB 调色板修改，还可以添加背景图片和效果。

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

## 功能

### 音乐

- **音浪与推荐** - 基于点赞和收听历史的自研推荐系统
- **搜索** - 曲目、艺人、播放列表、专辑以及氛围搜索（神经网络根据上下文挑选曲目）
- **播放器** - 随机播放、循环、播放队列、歌词、喜欢与不喜欢
- **播放列表** - 与 SoundCloud 同步的本地播放列表，可公开或私密，拖动即可调整曲目顺序并保存
- **历史与统计** - 收听时长、热门曲目和艺人、按天和按小时的图表
- **个人主页** - 关注与取消关注，SoundCloud 主页中的链接

### 离线模式

- 下载曲目和播放列表，带缓存的离线模式

---

## 反馈
社区: [Discord](https://discord.gg/Au3ebtfYu3)

发现了 bug 或有想法？最简单的方式是"设置 -> 关于系统 -> iOS 应用支持"或在 Discord 中创建工单。也可以手动 [提交 issue](https://github.com/okeydw/SoundCloud-Android/issues/new/choose) 并附上日志："设置 -> 日志 -> 复制"。

---

## 技术栈

| 层 | 技术 |
| --- | --- |
| 语言 | Kotlin |
| UI | Compose Multiplatform, Haze（玻璃效果） |
| 音频 | AVFoundation，后台播放与锁屏控制 |
| 网络 | NSURLSession，kotlinx.serialization |
| 图片 | Coil 3 |
| 相机 | AVFoundation（二维码扫描） |
| 存储 | NSUserDefaults（设置、会话），下载和播放列表的 JSON 索引 |

后端: [SoundCloud-Backend](https://github.com/zxcloli666/SoundCloud-Backend).

服务器状态请看 [这里](https://status.soundcloud-desktop.fun/)

**兼容性：** iOS 15.0 或更高版本，iPhone 与 iPad。

完整更新记录见 [CHANGELOG.md](../../CHANGELOG.md)。

---

## 开发者

**构建：** macOS，Xcode，JDK 17，[XcodeGen](https://github.com/yonaskolb/XcodeGen)。

```bash
./gradlew :core:compileKotlinIosArm64 :ui:compileKotlinIosArm64   # 检查共享代码能否为 iOS 编译
cd iosApp && xcodegen generate                                    # 根据 project.yml 生成 Xcode 项目
open SoundCloud.xcodeproj                                         # Kotlin 框架会自动构建
```

**源码：**

| 目录 | 内容 |
| --- | --- |
| `core/src/commonMain` | 共享逻辑：API、播放器、下载、播放列表 |
| `core/src/iosMain` | iOS：AVFoundation 播放器、网络、下载、存储 |
| `ui/src/commonMain` | Glass 界面 |
| `ui/src/iosMain` | iOS：设置、二维码扫描、入口 |
| `iosApp` | Xcode 项目（XcodeGen） |

**发布：** 打上 `vX.Y.Z` 标签（须与 `app/build.gradle.kts` 中的 `versionName` 一致）后，会构建未签名的 `.ipa` 和 `.tipa` 并发布到 Release。如需不发布的测试构建，可在 Actions 中手动运行 `iOS` 工作流，或推送提交信息带 `[ipa]` 的提交。

**后端：**

| 域名 | 用途 |
|---|---|
| `api.scnative.space`, `api-star.scnative.space` | API |
| `stream.scnative.space`, `stream-star.scnative.space` | 音频 |
| `storage.scnative.space`, `storage-star.scnative.space`, `s3.scnative.space` | 曲目存储 |
| `images.scnative.space` | 封面 |
| `pay.scnative.space` | STAR 支付 |
| `health.scnative.space` | 连接检测 |
| `call-*.scnative.space` | "用户网络" |
| `*.relay.scnative.space` | 备用线路 |
| `soundcloud.com`, `api-v2.soundcloud.com`, `*.sndcdn.com` | 直接从 SoundCloud 下载曲目 |

二维码登录协议：桌面版仓库中的 [qr-link-protocol.md](https://github.com/zxcloli666/SoundCloud-Desktop/blob/main/docs/qr-link-protocol.md)。

---


## 许可证

MIT。详见 [LICENSE](../../LICENSE) 文件。

_SoundCloud 是 SoundCloud Ltd. 的商标。本应用与 SoundCloud 无关。_

---

<p align="center">
<a href="https://github.com/okeydw/SoundCloud-iOS/releases/latest">
<img src="https://img.shields.io/badge/下载_SoundCloud_iOS-0048FF?style=for-the-badge&logo=apple&logoColor=white" alt="下载 SoundCloud iOS"/>
</a>
</p>

<p align="center">
<code>soundcloud ios</code> · <code>soundcloud iphone</code> · <code>soundcloud ipa</code> · <code>soundcloud ipad</code> · <code>soundcloud trollstore</code> · <code>soundcloud 客户端</code> · <code>soundcloud 无广告</code> · <code>soundcloud 被封锁</code> · <code>soundcloud alternative client</code> · <code>soundcloud no ads</code>
</p>
