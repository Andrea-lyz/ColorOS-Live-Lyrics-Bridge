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
- 可分别调整实时高亮、当前行未高亮、实时翻译/进度、非实时正文/翻译亮度，以及上下边缘渐隐和非活动行额外淡化。
- 还可调整颜色、光晕、模糊、字号、字重、对齐、缩放和动效；歌词之间与同一句内部换行的间距可以分开设置。
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

## 4.0 架构与播放器适配

Bridge 4.0 只运行在 `system` / `com.android.systemui`，不再进入播放器进程，也不再接收 Provider 私有广播。独立 Provider 在目标播放器进程内把歌词写入播放器自己的 `MediaSession` / `MediaMetadata["lyricInfo"]`；Bridge 只消费这份 ColorOS 原生数据并提供样式、逐字渲染、翻译按钮、AOD 和兼容增强。

Provider 与 Bridge 可以独立安装：

- 只安装 Provider：ColorOS SystemUI 可直接消费播放器的原生 `lyricInfo`。
- 额外安装 Bridge：在原生链路之上增加通用增强；不会再次提交一份歌词。
- 4.0 Provider 是独立的 Root / LSPosed 模块。

| 播放器 | 4.0 Provider 模块 | 歌词能力 |
| --- | --- | --- |
| Salt Player | `player-salt` | 逐字、翻译、公开翻译 CustomAction |
| ConePlayer / 光锥音乐（正式版、Google Play 版） | `player-cone` | 完整时间轴、翻译、公开翻译 CustomAction |
| 酷我音乐 | `kuwo-music` | 官方 `lyricInfo` 追加逐字与翻译 |
| LX Music（ToSide / Walnut） | `player-lx` | 逐字、翻译、蓝牙歌词身份与封面兼容 |
| Poweramp | `player-poweramp` | 同目录 `.lrc` / 内嵌标签、翻译 |
| [Metrolist](https://github.com/metrolistgroup/metrolist) | `player-metrolist` | BetterLyrics / LrcLib / KuGou；不支持翻译 |
| 酷狗音乐 / 酷狗概念版 | `player-kugou` | 官方 payload 追加逐字与 type-1 翻译 |
| QQ 音乐官方版 | `player-qq` | 官方 payload 追加逐字与翻译；QQ HD 不在范围 |
| 网易云官方版 / 荣耀版 / 修改版 9.0.40 | `player-netease` | 官方追加或按运行 profile 构造逐字与翻译 |
| Apple Music | `player-apple` | JNI TTML 逐字与翻译 |
| Spotify | `player-spotify` | Color Lyrics 逐行或逐字；不支持翻译 |
| 汽水音乐 | `player-qishui` | 宿主 TrackLyric / 缓存回退，逐字与翻译 |

播放器更新后，私有歌词接口仍可能变化。上表表示当前 4.0 代码与真机验收矩阵，不代表未来所有播放器版本永久兼容。

Halcyon 若发布标准 `lyricInfo`，仍可走原生链路；旧的应用内 v4 广播 fallback 已删除。Flamingo 旧 v4-only 接入不再受 4.0 支持，需先改为向自身 MediaSession 发布标准 `lyricInfo`。

## 安装

1. 安装自己需要的 4.0 Provider APK，在 LSPosed 中启用它并只勾选对应音乐 App。
2. 如需 Bridge 增强，再安装 `ColorOS-Live-Lyrics-Bridge-<版本>.apk`，作用域保持 `system` 与 `com.android.systemui`。
3. 重启目标播放器与 SystemUI；首次安装或改变作用域后建议重启手机。
4. 不要同时让旧词幕 Provider 与 4.0 Provider hook 同一播放器。

Release 中的 Provider ZIP 只是 APK 下载合集，不是 Recovery 刷机包。
## 怎么设置外观

在 LSPosed 的模块页面打开 **ColorOS Live Lyrics Bridge → 设置**。

你可以先选一个风格预设，再按喜好微调。修改时只会更新预览，点击“应用并保存”后才会真正应用到锁屏。设置页还提供：

- 独立的“歌词亮度与渐隐”页面：实时/未高亮/翻译/翻译进度亮度、非实时正文与翻译跟随或独立亮度、RecyclerView 原生上下渐隐开关/长度、非活动行额外淡化；
- 播放器翻译默认值和翻译按钮记忆；
- 歌词开头信息清理；
- 普通逐行歌词进度与翻译进度；
- 长歌词纵向浏览与长翻译横向滚动；
- 60 / 90 / 120 Hz 歌词刷新上限；
- “歌词显示时保持屏幕点亮”和自定义时长。

设置页里的刷新率只限制歌词绘制，不会强制屏幕一直以高刷新率运行。

默认、柔和、醒目、极简四套预设会显式设置新增亮度与渐隐字段；任一预设管理的颜色、排版、动效、亮度或渐隐参数发生偏离后，都会识别为“自定义”。柔和与醒目保留旧版随模糊/缩放启用的 90% 非活动行淡化，默认与极简关闭额外行淡化。

主设置页提供独立的“Bridge 配置备份与恢复”入口。它会备份和恢复 Bridge 的两个配置域，覆盖主 UI、全局/逐播放器翻译设置、开头清理规则与逐曲修正、调试设置和设置页语言；页面内也提供二次确认后的全量重置。schema v3 无法保证无损降级：旧 codec 可能拒绝已保存配置，因此降级前应先创建完整 Bridge 备份，并准备在旧版中重置设置。

完整默认值、预设矩阵、迁移规则与降级边界见 [Bridge 4.0 歌词亮度与渐隐设置说明](docs/4.0/LYRIC-VISUAL-CONTROLS.md)。

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

Bridge 只读取本机播放器 MediaSession 中的原生歌词并增强 SystemUI 显示，不会修改音乐文件。是否联网获取歌词取决于对应播放器或 Provider 的实现。

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
