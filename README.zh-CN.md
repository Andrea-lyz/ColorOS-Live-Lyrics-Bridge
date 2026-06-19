# ColorOS Live Lyrics Bridge

[![构建 Debug APK](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml/badge.svg)](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml)

语言：[English](README.md) | [简体中文](README.zh-CN.md)

<p align="center">
  <img src="GIF.gif" alt="ColorOS Live Lyrics Bridge 演示" width="360">
</p>

一个基于 LSPosed/libxposed API 102 的模块，用来把受支持音乐播放器的时间轴歌词桥接到 ColorOS/OPlus 锁屏歌词管线。

当前项目保留 Salt Player 兼容适配器和 SystemUI 渲染 hook；其他播放器优先通过 `lyricInfo` 接入协议主动适配。

## v1.5.0 更新亮点

- 通过独立适配层接入 Accompanist Lyrics Core，兼容更多带时间轴的歌词格式，并保留逐字时序和同时间戳翻译。
- 增加按歌曲缓存与标准化身份匹配，兼容 `[Explicit]` / `[Clean]` 后缀和 Salt“歌词先于元数据到达”的切歌顺序。
- 优化锁屏切歌衔接，避免短暂闪回官方渲染，同时兼顾首次进入沉浸歌词界面和延迟绑定歌词行。
- 完善逐字高亮、辉光、焦点过渡、双语排版、长句窗口，以及媒体操作区内可持久保存的翻译开关。
- 收紧沉浸模式识别、屏幕保活、OPlus 媒体策略接入和视图缓存清理。

## 功能概览

播放器进程：

```text
android.media.session.MediaSession#setMetadata(android.media.MediaMetadata)
```

对于 Salt Player 这类兼容适配器，当播放器提交的媒体元数据没有 `MediaMetadata["lyricInfo"]` 时，模块会根据抓取到的时间轴歌词注入 OPlus 可识别的 payload。主动接入的播放器应自行发布同样格式的 `lyricInfo`。

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
- 优先使用 `rawLyric` 构建逐字歌词时间轴。
- 将原始 `lyricInfo` 中带时间戳的翻译行合并到逐字模型。
- 在官方锁屏歌词 `TextView.onDraw(Canvas)` 路径内完成绘制。
- 保持固定高度歌词 item、短句垂直居中、长主歌词使用随进度移动的两行窗口，并让当前句和下一句保持清晰显示。
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

只有同时满足这些条件时，模块才会持有 `SCREEN_DIM_WAKE_LOCK`：

- 当前包名属于内置兼容适配器，或者是有效 `lyricInfo` 的当前提供者。
- OPlus 歌词 UI 模式处于激活状态。
- 播放状态是正在播放。
- 有歌词证据，例如已经解析出的逐字歌词模型、官方歌词元数据，或者最近可见的官方歌词视图。
- 屏幕仍处于交互状态，并且用户还没有解锁进入桌面。

保活期间，模块还会大约每 8 秒调用一次 `PowerManager.userActivity(...)`，让系统把锁屏歌词视图视为仍在被观看。遇到息屏、用户解锁、播放停止、歌词消失、包名不受支持或其他条件变化时，会立即释放 wake lock。

主动接入的播放器会从当前媒体会话中被动态识别，不需要加入 `scope.list` 或 `PLAYER_ADAPTERS`。如果某个系统版本修改了 `PluginSeedling--Template` 日志格式，则可能需要更新 SystemUI 侧的识别逻辑。

## 播放器主动接入 lyricInfo

这是已经拥有时间轴歌词的播放器应使用的方式：在当前媒体会话中发布合法的 `lyricInfo` JSON，模块会在 SystemUI 侧动态绑定该会话。带时间标签的 `lyric` 可使用 OPlus 原生逐行歌词；额外提供 `rawLyric` 后，会自动启用本模块的逐字绘制。

完整字段定义、Media3 示例和生命周期要求见[播放器主动接入协议](docs/PLAYER_INTEGRATION.zh-CN.md)。播放器无需依赖模块 APK、登记包名或加入 LSPosed 播放器作用域。

## 兼容适配器

兼容适配器用于播放器原生元数据没有通过 `lyricInfo` 接入协议对外暴露完整歌词时间轴的情况。

内置兼容适配器：

```java
new SaltPlayerAdapter()
```

新增播放器优先走主动发布 `lyricInfo` 的接入协议。只有播放器无法自行发布 `lyricInfo` 时，才建议新增 `PlayerAdapter` 做兼容桥接。

新增兼容适配器时，一般需要：

1. 将播放器包名加入 `src/main/resources/META-INF/xposed/scope.list`。
2. 在 `SaltPlayerAdapter` 旁边新增一个 `PlayerAdapter` 实现。
3. 在播放器进程中抓取真实时间轴歌词，并调用 `module.cacheTimedLyric(source, rawLyric)`。
4. 将新的适配器加入 `PLAYER_ADAPTERS`。
5. 保留 `scope.list` 中的 `com.android.systemui`；锁屏绘制和屏幕超时保活都依赖它。

如果播放器本身已经写入合法的 OPlus `lyricInfo`，通常无需新增歌词源 Hook；模块会通过外部协议自动识别。

## 为什么使用 API 102

本地 `../LSP_api` 目录对应 libxposed API `102.0.0`。项目使用当前 API 102 的模块布局：

- 入口类继承 `io.github.libxposed.api.XposedModule`
- 入口列表：`src/main/resources/META-INF/xposed/java_init.list`
- 模块配置：`src/main/resources/META-INF/xposed/module.prop`
- 静态作用域：`src/main/resources/META-INF/xposed/scope.list`
- Hook 写法：`hook(method).setId(...).setExceptionMode(...).intercept(...)`

`libxposed-api-stubs` 只作为编译期依赖，不会打包进 APK。它的作用是在不下载 `io.github.libxposed:api:102.0.0` 的情况下让项目完成编译；运行时由 LSPosed 提供真实 API 类。

## 构建

```powershell
.\gradlew.bat :app:assembleDebug
```

APK 输出位置：

```text
app\build\outputs\apk\debug\app-debug.apk
```

建议使用 JDK 17 或 JDK 21。过新的非兼容 JDK 版本可能在 Android `jlink` 转换阶段构建失败。

## GitHub Actions

- `Build Debug APK`：当 `main` 分支源码更新或发起 Pull Request 时自动构建，生成的 debug APK 会作为 workflow artifact 上传。
- `Release APK`：在 Actions 页面手动触发。输入类似 `v1.1.0` 的 tag 后，工作流会构建 release 签名 APK，把 APK `versionName` 设为 `1.1.0`，并创建 GitHub Release。

手动发布工作流需要这些仓库 secrets：

- `SIGNING_KEY`：keystore 文件内容的 base64 编码。
- `KEY_STORE_PASSWORD`：keystore 密码。
- `KEY_ALIAS`：签名 key alias。
- `KEY_PASSWORD`：签名 key 密码。

发布产物会命名为 `ColorOS-Live-Lyrics-Bridge-<tag>.apk`。

使用播放器适配器测试：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.salt.music
```

在 LSPosed 中为目标播放器包名和系统界面启用模块，然后重启系统界面或重启设备。之后打开播放器开始播放歌曲，再锁屏查看效果。

常用日志：

```powershell
adb logcat -v time -s LockscreenLyrics
adb logcat -v time | Select-String -Pattern "LockscreenLyrics|OplusMediaDataManagerEx|loadLyricInBg|Failed to parse lyric data|LyricsRecyclerView|hasLyric"
```

预期模块日志示例：

```text
LockscreenLyrics: Hooked MediaSession#setMetadata
LockscreenLyrics: Hooked Salt Player lyric result constructors
LockscreenLyrics: Hooked SystemUI official lyric TextView draw hooks
LockscreenLyrics: Registered SystemUI screen timeout receiver
LockscreenLyrics: Cached real timed lyric from LRC_FILE, rawChars=..., oplusChars=...
LockscreenLyrics: Injected real LRC_FILE lyricInfo for title=...
LockscreenLyrics: Cached SystemUI word lyric model, lines=...
LockscreenLyrics: Lockscreen lyric UI keep-awake ON
LockscreenLyrics: Acquired screen timeout wake lock without timeout
LockscreenLyrics: Pulsed screen timeout user activity without changing lights
LockscreenLyrics: Hooked LyricsRecyclerView#setCurrentLyric, methods=...
LockscreenLyrics: LyricsRecyclerView current index=...
LockscreenLyrics: Seedling playback state=3, playing=true, storedPosition=..., computedPosition=..., speed=...
LockscreenLyrics: Custom-drew official lyric TextView at position=..., playing=true, focused=true, line=...
LockscreenLyrics: Refreshed active lyric renderer at position=..., line=...
```

如果只看到 `Skip lyricInfo injection because no fresh real lyric is cached`，说明当前进程里适配器还没有抓到时间轴歌词，或者当前歌曲只有非时间轴歌词。

## 当前限制

- 兼容适配器依赖播放器私有类和混淆成员；播放器大版本更新后可能需要同步更新 Hook。
- OPlus SystemUI 的类名和字段属于厂商私有实现，系统版本更新后可能需要重新适配。
- 屏幕超时保活依赖 OPlus 锁屏歌词 UI 日志和官方歌词视图状态；如果 ROM 修改了相关日志或 UI 路径，需要重新适配。
- 锁屏歌词绘制依赖官方歌词视图存在；如果系统界面没有进入歌词 UI，模块不会强行创建新的歌词窗口。
