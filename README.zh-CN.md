# ColorOS Live Lyrics Bridge

[![构建 Debug APK](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml/badge.svg)](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml)

语言：[English](README.md) | [简体中文](README.zh-CN.md)

<p align="center">
  <img src="GIF.gif" alt="ColorOS Live Lyrics Bridge 演示" width="360">
</p>

一个基于 LSPosed/libxposed API 102 的模块，用来把受支持音乐播放器的时间轴歌词桥接到 ColorOS/OPlus 锁屏歌词管线。

当前项目内置基于 DexKit 的 Salt Player、ConePlayer 兼容适配器和 SystemUI 渲染 hook；其他播放器优先通过 `lyricInfo` 接入协议主动适配。

歌词来源通过通用事务层与曲目元数据关联：带媒体 ID、URI 或完整标题/歌手的信息会精确匹配；无身份的被动回调只会等待下一次稳定元数据，避免纯音乐、预加载或异步回调造成跨曲歌词错位。

## 功能概览

播放器进程：

```text
android.media.session.MediaSession#setMetadata(android.media.MediaMetadata)
```

对于内置兼容适配器，模块成功抓取并确认属于当前歌曲的歌词后，会优先使用抓取数据生成完整 payload；播放器只提供的简单 `MediaMetadata["lyricInfo"]` 在此之前作为兜底。若播放器 payload 已包含 `rawLyric` 或带时间轴的翻译，则视为播放器主动增强接入并予以保留。其他主动接入的播放器应自行发布同样格式的 `lyricInfo`。

```json
{
  "songName": "...",
  "artist": "...",
  "songId": "lockscreen-lyrics-...",
  "lyric": "[00:00.00]...",
  "rawLyric": "[00:00.000]word[00:00.120]..."
}
```

SystemUI 进程：

- 从 OPlus 媒体数据中读取 `lyricInfo`。
- 在进入 OPlus 官方列表前规范化逐行 LRC，保证每个时间戳只生成一个主歌词 item，翻译与逐字时间轴继续保留在完整模型中。
- 优先使用 `rawLyric` 构建逐字歌词时间轴。
- 将原始 `lyricInfo` 中带时间戳的翻译行合并到逐字模型。
- 使用 DexKit 动态识别 OPlus 私有媒体与歌词入口，并保留旧类名回退路径。
- 在官方锁屏歌词 `TextView.onDraw(Canvas)` 路径内完成绘制。
- 使用时间戳、规范化文本与出现顺序映射官方 item，稳定处理重复歌词和首行预滚动。
- 使用 `80dp` 固定歌词槽位与 `6dp` 间距，收紧短句视觉密度；长主歌词继续使用两行滑动窗口，并将当前进度行放在视口中心下方约 `48dp`。
- 在歌词界面发生短暂可见性切换后恢复绘制，播放过程中不按内容改变 item 几何。
- 无需硬编码包名，动态识别主动提供 `lyricInfo` 的播放器。
- 当已识别歌词提供者的锁屏歌词 UI 正在显示时，阻止屏幕按系统超时时间自动熄灭。

## 屏幕超时保活

屏幕超时保活逻辑只运行在 SystemUI 进程中。它绑定的是 OPlus 官方锁屏歌词 UI 的可见状态，而不是单纯的“正在播放”。

这个功能用到的 SystemUI hook：

- `android.util.Log.i(String, String)`
- `android.util.Log.println(int, String, String)`
- OPlus Seedling 媒体播放位置/状态 hook
- 官方歌词 `TextView` 可见性追踪
- SystemUI 内部注册的 `ACTION_SCREEN_OFF`、`ACTION_SCREEN_ON`、`ACTION_USER_PRESENT` 广播

模块会观察 OPlus `PluginSeedling--Template` 日志，并只接受受支持播放器包名对应的日志。关键字段包括：

```text
lyricUiMode=true
lockImmersiveMode: true
containerView.isShown=true
hasLyric=false
```

只有同时满足这些条件时，模块才会持有 15 秒租约的 `SCREEN_BRIGHT_WAKE_LOCK`：

- 当前包名属于内置兼容适配器，或者是有效 `lyricInfo` 的当前提供者。
- OPlus 歌词 UI 模式处于激活状态。
- 播放状态是正在播放。
- 有来自最近可见官方歌词视图的歌词证据；刚收到的歌词元数据只提供很短的过渡窗口。
- 屏幕仍处于交互状态，并且 Keyguard 仍在显示。

保活期间，模块会续租 wake lock，并大约每 8 秒调用一次 `PowerManager.userActivity(...)`，让系统把锁屏歌词视图视为仍在被观看。遇到息屏、真正离开 Keyguard、播放停止、可见歌词证据消失、包名不受支持或其他条件变化时，会立即释放 wake lock。收到 `ACTION_USER_PRESENT` 后会短暂延迟复核 Keyguard 状态，因此人脸识别成功但仍停留在锁屏歌词页时会恢复保活。

主动接入的播放器会从当前媒体会话中被动态识别，不需要加入 `scope.list` 或 `PLAYER_ADAPTERS`。如果某个系统版本修改了 `PluginSeedling--Template` 日志格式，则可能需要更新 SystemUI 侧的识别逻辑。

## 播放器主动接入 lyricInfo

这是已经拥有时间轴歌词的播放器应使用的方式：在当前媒体会话中发布合法的 `lyricInfo` JSON，模块会在 SystemUI 侧动态绑定该会话。带时间标签的 `lyric` 可使用 OPlus 原生逐行歌词；额外提供 `rawLyric` 后，会自动启用本模块的逐字绘制。

已完成主动接入的播放器：

- [Halcyon](https://github.com/Kifranei/Halcyon) — 已完成 `lyricInfo` 接入。

完整字段定义、Media3 示例和生命周期要求见[播放器主动接入协议](docs/PLAYER_INTEGRATION.zh-CN.md)。播放器无需依赖模块 APK、登记包名或加入 LSPosed 播放器作用域。

## 兼容适配器

兼容适配器用于播放器原生元数据没有通过 `lyricInfo` 接入协议对外暴露完整歌词时间轴的情况。

内置兼容适配器：

```java
new SaltPlayerAdapter()
new ConePlayerAdapter("ink.trantor.coneplayer")
new ConePlayerAdapter("ink.trantor.coneplayer.gp")
```

Salt 适配器已验证 Salt Player 12.0.0 正式版与 alpha07；ConePlayer 适配器已跨版本验证正式包 1.1.3 至 1.1.5，并包含 Google Play 包名作用域。

新增播放器优先走主动发布 `lyricInfo` 的接入协议。只有播放器无法自行发布 `lyricInfo` 时，才建议新增 `PlayerAdapter` 做兼容桥接。

新增兼容适配器时，一般需要：

1. 将播放器包名加入 `src/main/resources/META-INF/xposed/scope.list`。
2. 在 `SaltPlayerAdapter` 旁边新增一个 `PlayerAdapter` 实现。
3. 在播放器进程中抓取真实时间轴歌词，并调用 `module.cacheTimedLyric(source, rawLyric)`。
4. 将新的适配器加入 `PLAYER_ADAPTERS`。
5. 保留 `scope.list` 中的 `system` 与 `com.android.systemui`；OPlus 历史播放器接入和 SystemUI 侧锁屏能力分别依赖它们。

如果作用域外的播放器已经自行写入合法的 OPlus `lyricInfo`，通常无需新增歌词源 Hook；模块会通过外部协议自动识别。对于已有内置适配器的播放器，仅含逐行 `lyric` 的简单 payload 会作为兜底，适配器抓到当前歌曲的 `rawLyric` 后会接管。

## 为什么使用 API 102

本地 `../LSP_api` 目录对应 libxposed API `102.0.0`。项目使用当前 API 102 的模块布局：

- 入口类继承 `io.github.libxposed.api.XposedModule`
- 入口列表：`src/main/resources/META-INF/xposed/java_init.list`
- 模块配置：`src/main/resources/META-INF/xposed/module.prop`
- 静态作用域：`src/main/resources/META-INF/xposed/scope.list`
- LSPosed 仓库元数据：`.github/lsposed/`
- Hook 写法：`hook(method).setId(...).setExceptionMode(...).intercept(...)`

`libxposed-api-stubs` 只作为编译期依赖，不会打包进 APK。它的作用是在不下载 `io.github.libxposed:api:102.0.0` 的情况下让项目完成编译；运行时由 LSPosed 提供真实 API 类。

## 构建

```powershell
.\scripts\gradle-local.cmd testDebugUnitTest assembleDebug
```

APK 输出位置：

```text
.gradle-local-build\app\outputs\apk\debug\app-debug.apk
```

构建需要 JDK 21，以便读取 Lyrics Core 依赖。本地脚本会依次从 `SALT_LYRIC_JAVA_HOME`、`JAVA_HOME` 和常见 JDK 安装目录中查找，并把仓库临时映射到 ASCII 盘符，避免中文路径导致 Gradle 测试进程类路径异常；应用本身仍输出 Java 17 字节码以保持 Android 兼容性。

## GitHub Actions

- `Build Debug APK`：当 `main` 分支源码更新或发起 Pull Request 时自动构建，生成的 debug APK 会作为 workflow artifact 上传。
- `Release APK`：推送类似 `v1.9.0` 的 tag 后手动触发。工作流会校验 JSON 格式的 `SCOPE`，同步 README/SUMMARY/SOURCE_URL/SCOPE 到 LSPosed 仓库，构建签名 APK，并以 `90-1.9.0` 这类 `versionCode-versionName` 标签同步版本。

手动发布工作流需要这些仓库 secrets：

- `SIGNING_KEY`：keystore 文件内容的 base64 编码。
- `KEY_STORE_PASSWORD`：keystore 密码。
- `KEY_ALIAS`：签名 key alias。
- `KEY_PASSWORD`：签名 key 密码。
- `LSP_REPO_TOKEN`：对 `Xposed-Modules-Repo/io.github.andrealtb.lockscreenlyrics` 具有仓库内容与 release 写入权限的 PAT。

发布产物会命名为 `ColorOS-Live-Lyrics-Bridge-<tag>.apk`。

使用播放器适配器测试：

```powershell
adb install -r .gradle-local-build\app\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.salt.music
# 或：adb shell am force-stop ink.trantor.coneplayer
```

在 LSPosed 中为目标播放器包名、`system` 与系统界面启用模块，然后重启目标播放器和系统界面。若修改了作用域，或需要验证 `system` 中的历史播放器能力，再重启设备。之后打开播放器开始播放歌曲，再锁屏查看效果。

常用日志：

```powershell
adb logcat -v time -s LockscreenLyrics
adb logcat -v time | Select-String -Pattern "LockscreenLyrics|OplusMediaDataManagerEx|loadLyricInBg|Failed to parse lyric data|LyricsRecyclerView|hasLyric"
```

预期模块日志示例：

```text
LockscreenLyrics: Hooked MediaSession#setMetadata
LockscreenLyrics: Hooked Salt Player lyric result constructors via DexKit: result=..., source=..., scroll=..., count=2
LockscreenLyrics: Hooked ConePlayer lyric parser via DexKit: ...
LockscreenLyrics: Hooked SystemUI official lyric TextView draw hooks
LockscreenLyrics: Registered SystemUI screen timeout receiver
LockscreenLyrics: Accepted lyric transaction from EMBEDDED, rawChars=..., oplusChars=..., identity=..., association=...
LockscreenLyrics: Injected real LRC_FILE lyricInfo for title=...
LockscreenLyrics: Cached SystemUI word lyric model, lines=...
LockscreenLyrics: Lockscreen lyric UI keep-awake ON
LockscreenLyrics: Acquired bright screen timeout wake lock lease=15000ms
LockscreenLyrics: Pulsed screen timeout user activity without changing lights
LockscreenLyrics: Hooked LyricsRecyclerView#setCurrentLyric, methods=...
LockscreenLyrics: LyricsRecyclerView current index=...
LockscreenLyrics: Seedling playback state=3, playing=true, storedPosition=..., computedPosition=..., speed=...
LockscreenLyrics: Custom-drew official lyric TextView at position=..., playing=true, focused=true, line=...
LockscreenLyrics: Refreshed active lyric renderer at position=..., line=...
```

如果只看到 `Skip lyricInfo injection because no fresh real lyric is cached`，说明当前进程里适配器还没有抓到时间轴歌词，或者当前歌曲只有非时间轴歌词。

## 开源协议与致谢

Copyright 2026 Andrea-lyz。本项目采用 [Apache License 2.0](LICENSE) 开源。

本项目使用 [Accompanist Lyrics Core](https://github.com/6xingyv/accompanist-lyrics-core) `0.4.5`（`com.mocharealm.accompanist:lyrics-core-jvm`）解析时间轴歌词，该项目由 [6xingyv](https://github.com/6xingyv) 维护，同样采用 [Apache License 2.0](https://github.com/6xingyv/accompanist-lyrics-core/blob/main/LICENSE)。

Android、ColorOS、OPlus、LSPosed、Salt Player、ConePlayer 及其他产品名称的商标权归各自权利人所有；本项目与这些权利人不存在隶属或官方背书关系。

## 当前限制

- 兼容适配器使用 DexKit 根据稳定字符串与结构特征定位播放器私有实现；播放器大版本改变歌词架构后仍可能需要同步更新特征。
- OPlus SystemUI 的类名和字段属于厂商私有实现，系统版本更新后可能需要重新适配。
- 屏幕超时保活依赖 OPlus 锁屏歌词 UI 日志和官方歌词视图状态；如果 ROM 修改了相关日志或 UI 路径，需要重新适配。
- 锁屏歌词绘制依赖官方歌词视图存在；如果系统界面没有进入歌词 UI，模块不会强行创建新的歌词窗口。
