# ColorOS Live Lyrics Bridge

[![构建 Debug APK](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml/badge.svg)](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml)
[![最新版本](https://img.shields.io/github/v/release/Andrea-lyz/ColorOS-Live-Lyrics-Bridge)](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/releases/latest)

语言：[English](README.md) | 简体中文

<p align="center">
  <img src="GIF.gif" alt="ColorOS 锁屏歌词演示" width="360">
</p>

让更多音乐 App 的歌词，出现在 ColorOS / OPlus 自带的锁屏歌词页面里。

它不是一套盖在锁屏上的悬浮窗，而是把播放器的完整歌词交给系统原生界面显示。这样既能保留 ColorOS 的锁屏风格、切歌动画和息屏显示，也能补上逐字高亮、翻译和外观设置。

> 当前版本：**v3.8.0**。升级时请把 Bridge 和正在使用的 LyricProvider 从同一 Release 一起更新，避免新旧版本配合异常。

## 主要功能

- 在 ColorOS 原生锁屏和 AOD 页面显示完整歌词。
- 支持普通逐行歌词、逐字高亮和双语翻译；具体效果取决于播放器能提供什么数据。
- 长歌词会自动换行或平滑浏览，不会为了塞进一行而缩成很小的字。
- 可调整颜色、透明度、光晕、模糊、字号、字重、对齐、缩放和动效；歌词之间与同一句内部换行的间距可以分开设置。
- 内置“默认、柔和、醒目、极简”四种风格，设置时可以直接预览。
- 可按播放器记住翻译开关，也可以清理歌词开头的歌名、制作人员和版权信息。
- 歌词显示时可以保持屏幕常亮，也可以设置一个自定义的亮屏时长。
- 保留系统媒体卡片的上一首、播放/暂停、下一首等原有操作。
- 针对切歌、暂停恢复、AOD 切换、重复歌词和中日文长句做了专门处理。

## 使用前先确认

这个模块适合满足以下条件的设备：

- 已 Root，并安装支持 **libxposed API 102** 的 LSPosed / LSP 管理器。
- 系统本身带有 ColorOS / OPlus 原生锁屏歌词页面。
- 愿意在系统更新或播放器大版本更新后，重新确认兼容性。

项目最低 Android API 为 26，但这不等于所有 Android 8.0 以上设备都能使用。真正决定是否兼容的是手机上的 OPlus SystemUI 实现。当前主要围绕 ColorOS 16 的原生锁屏歌词链路开发和验证；一加、OPPO、真我等设备即使系统名称相近，也可能因为 SystemUI 版本不同而表现不同。

如果系统原本没有锁屏歌词页面，本模块不会另外创建一个悬浮歌词窗口。

## 播放器适配

安装分为两种：

- **Bridge 直接支持**：只安装主模块即可。
- **需要 LyricProvider**：除了主模块，还要安装对应播放器的 Provider APK。Provider 负责从音乐 App 里取得歌词，Bridge 负责把歌词交给锁屏界面。

| 播放器 | 需要额外 Provider | 当前支持情况 |
| --- | --- | --- |
| Salt Player | 否 | Bridge 内置适配；支持播放器提供的逐字与翻译歌词 |
| ConePlayer / 光锥音乐（正式版、Google Play 版） | 否 | Bridge 内置适配；支持完整时间轴歌词和后台恢复 |
| [Metrolist](https://github.com/metrolistgroup/metrolist) | `LyricProvider-Metrolist` | Bridge 已放行 v4 直达 Provider；按 Metrolist 中配置的供应商顺序搜索，支持 BetterLyrics / KuGou 逐字歌词及 LrcLib 回退；不支持翻译歌词，也不支持词幕（Lyricon） |
| QQ 音乐 / QQ 音乐 HD | `LyricProvider-QQMusic` | Bridge 已放行 v4 直达 Provider；支持逐字歌词、翻译歌词 |
| 网易云音乐 / 网易云音乐荣耀版 | `LyricProvider-163Music` | Bridge 已放行 v4 直达 Provider；支持逐字歌词、翻译歌词 |
| Apple Music | `LyricProvider-AppleMusic` | Bridge 已放行 v4 直达 Provider；支持逐字歌词、翻译歌词；不输出背景人声和对唱分轨 |
| LX Music（ToSide / Walnut 版本） | `LyricProvider-LXMusic` | Bridge 已放行 v4 直达 Provider；支持完整时间轴歌词；播放器提供时支持翻译歌词 |
| Poweramp | `LyricProvider-Poweramp` | Bridge 已放行 v4 直达 Provider；支持本地内嵌歌词与可匹配的在线歌词 |
| Spotify | `LyricProvider-Spotify` | Bridge 已放行 v4 直达 Provider；目前只支持原文标准歌词，不支持翻译 |
| 汽水音乐 | `LyricProvider-QiShui` | Bridge 已放行 v4 直达 Provider；支持逐字歌词、翻译歌词；需做好 Root 隐藏并完成下方特殊设置 |
| 酷狗音乐 / 酷狗概念版 | `LyricProvider-KuGou` | Bridge 已放行 v4 直达 Provider；支持逐字歌词、翻译歌词 |
| 酷我音乐 | `LyricProvider-KuWo` | 通过酷我原生 `lyricInfo` 链路提供完整逐行、逐字和翻译歌词；无需开启车载歌词模式；保留酷我原生封面和媒体元数据 |
| [Halcyon](https://github.com/Kifranei/Halcyon) | 否 | 原生 `lyricInfo` + 静态 source-to-player 绑定的应用内 v4 广播（`lyricprovider/halcyon`）；沿用原生 MediaSession 播放时钟，并兼容会丢弃 extras-only 更新的 ColorOS 版本 |
| Flamingo | 否 | 通过 `yos.music.player` / `lyricprovider/flamingo` 静态绑定放行原生 v4 接入；播放状态沿用播放器本身，无需额外 Provider APK |

[Metrolist](https://github.com/metrolistgroup/metrolist) 是**适用于安卓系统的 YouTube Music 客户端**。由于 Metrolist 本身没有稳定的歌词获取接口，本 Provider 采用与 Metrolist 相同的方式从第三方歌词提供商获取歌词，因此两者获取的歌词可能存在差异。

上表以 Bridge 的 v4 直达放行注册表为准：Provider 的 `source` 与播放器包名已被 Bridge 静态放行，即视为已完成 Bridge 适配。目前放行的 Provider source 为：`qq-music`、`netease-cloud-music`、`apple-music`、`lx-music`、`lx-walnut-music`、`poweramp-music`、`spotify-music`、`qishui-music`、`kugou-music`、`kugou-concept-music`、`metrolist-music`、`halcyon` 和 `flamingo`。酷我不在这份直达 source 列表中，因为酷我 Provider 是通过酷我自己的 MediaSession 写入 `lyricInfo`，而不是发送 v4 直达广播。LyricProvider 仓库中其他模块虽然存在，但在加入 Bridge 注册表前不属于当前适配清单。

播放器更新后，私有歌词接口可能发生变化。表格表示当前代码已经包含相应适配，不代表未来所有播放器版本都能永久兼容。

已经主动支持公开 `lyricInfo` 协议的播放器通常不需要额外 Provider，也不需要加入 Bridge 的播放器作用域。[Halcyon](https://github.com/Kifranei/Halcyon) 和 Flamingo 是当前已知的原生接入项目。Halcyon 会发布 `lyricInfo`，并从 `com.ella.music` 发送 `lyricprovider/halcyon` v4 广播，以便 ColorOS 16.9 起 extras-only 更新被丢弃后仍能收到歌词；播放状态继续沿用原生 MediaSession。Flamingo 则通过 `yos.music.player` 的 `lyricprovider/flamingo` 原生 v4 source 接入，不额外发送播放状态。两者都无需额外 Provider APK。

## 安装

1. 打开 [Releases](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/releases/latest)，安装 `ColorOS-Live-Lyrics-Bridge-<版本>.apk`。
2. 在 LSPosed 中启用 **ColorOS Live Lyrics Bridge**，保留模块推荐的默认作用域。
3. 如果你使用 Metrolist、QQ 音乐、网易云、Apple Music、LX Music、Poweramp、Spotify、汽水音乐、酷狗或酷我，再安装同一版本中对应的 `LyricProvider-*.apk`。
4. 在 LSPosed 中分别启用这些 Provider，并只勾选它对应的音乐 App。
5. 重启手机，让 SystemUI、系统服务和播放器里的模块完整加载。

Release 中的 `LyricProvider-All-<版本>.zip` 只是全部 Provider APK 的合集，不是需要刷入 Recovery 的包。只安装自己用得到的 Provider 即可。

### 汽水音乐的额外一步

在 LSP 管理器中为汽水音乐开启“还原内联钩子”，并按管理器提示对列表中的应用处理 `libart.so`；同时需做好 Root 隐藏。跳过这些设置时，汽水音乐可能提示版本不安全，Provider 也可能无法稳定工作。

## 怎么设置外观

在 LSPosed 的模块页面打开 **ColorOS Live Lyrics Bridge → 设置**。

你可以先选一个风格预设，再按喜好微调。修改时只会更新预览，点击“应用并保存”后才会真正应用到锁屏。设置页还提供：

- 播放器翻译默认值和翻译按钮记忆；
- 歌词开头信息清理；
- 普通逐行歌词进度与翻译进度；
- 长歌词纵向浏览与长翻译横向滚动；
- 60 / 90 / 120 Hz 歌词刷新上限；
- “歌词显示时保持屏幕点亮”和自定义时长。

设置页里的刷新率只限制歌词绘制，不会强制屏幕一直以高刷新率运行。

## 常见问题

### 安装后完全没有歌词

先确认三件事：系统原生锁屏歌词页面是否存在、Bridge 是否使用推荐作用域、目标播放器是否需要单独安装 Provider。完成后强行停止音乐 App；如果仍无效，再重启手机。

### 有普通歌词，但没有逐字或翻译

这通常表示当前播放器或当前歌曲只提供了逐行歌词。逐字和翻译不能凭空生成；它们取决于歌词源本身是否包含对应数据。Spotify Provider 当前就是仅原文歌词。

### 切歌后还是上一首，或显示“暂无歌词”

请先确认 Bridge 与所有 Provider 都来自同一个 Release。混用不同版本最容易出现切歌时序不一致。随后强行停止播放器并重启系统界面；涉及作用域变化时直接重启手机。

### 系统更新后失效

ColorOS 的锁屏歌词属于厂商私有 SystemUI 功能，系统更新可能改变内部结构。请在 [Issues](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/issues) 中附上手机型号、系统版本、SystemUI 版本、播放器版本和 LSPosed 日志。

### 会不会修改音乐文件或上传歌词？

Bridge 只在本机播放器、系统媒体会话和 SystemUI 之间传递歌词，不会修改音乐文件。是否联网获取歌词取决于对应播放器或 LyricProvider 的实现。

## 给播放器开发者

如果你的播放器已经有完整时间轴歌词，推荐直接发布公开的 `MediaMetadata["lyricInfo"]` 数据。这样用户只需安装 Bridge，不必再安装专用 Provider，播放器本身也不需要依赖模块 APK。

- [播放器接入协议（中文）](docs/PLAYER_INTEGRATION.zh-CN.md)
- [Player integration protocol (English)](docs/PLAYER_INTEGRATION.md)
- [Bridge 与 LyricProvider 的职责说明](docs/LYRIC_PROVIDER_BRIDGE.zh-CN.md)

## 本地构建

需要 JDK 21。项目输出仍使用 Java 17 字节码，以保持 Android 兼容性。

```powershell
.\scripts\gradle-local.cmd testDebugUnitTest assembleDebug
```

Debug APK 位于 `app\build\outputs\apk\debug\app-debug.apk`。

## 支持项目

如果这个项目对你有帮助，欢迎通过微信或支付宝支持后续适配。

<p align="center">
  <img src="PY_QR.png" alt="微信和支付宝支持二维码" width="600" height="400">
</p>

## 开源协议与致谢

Copyright 2026 Andrea-lyz。本项目采用 [Apache License 2.0](LICENSE) 开源。

项目使用 [Accompanist Lyrics Core](https://github.com/6xingyv/accompanist-lyrics-core) 解析时间轴歌词；可选 Provider 基于 [tomakino/LyricProvider](https://github.com/tomakino/LyricProvider) 生态扩展。感谢相关项目的作者与贡献者。

Android、ColorOS、OPlus、LSPosed 以及各音乐 App 名称的商标权归各自权利人所有。本项目与这些产品的官方团队没有隶属或背书关系。
