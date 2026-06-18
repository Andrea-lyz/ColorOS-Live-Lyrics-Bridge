# ColorOS Live Lyrics Bridge

[![构建 Debug APK](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml/badge.svg)](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml)

语言：[English](README.md) | [简体中文](README.zh-CN.md)

<p align="center">
  <img src="GIF.gif" alt="ColorOS Live Lyrics Bridge 演示" width="360">
</p>

一个基于 LSPosed/libxposed API 102 的模块，用来把受支持音乐播放器的时间轴歌词桥接到 ColorOS/OPlus 锁屏歌词管线。

当前项目内置 Salt Player 适配器和 SystemUI 渲染 hook。Salt Player 只是默认适配器之一，不再是项目本身的专属身份。

## 功能概览

播放器进程：

```text
android.media.session.MediaSession#setMetadata(android.media.MediaMetadata)
```

当受支持播放器提交的媒体元数据里没有 `MediaMetadata["lyricInfo"]` 时，模块会注入 OPlus 可识别的歌词 payload：

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
- 在官方锁屏歌词 `TextView.onDraw(Canvas)` 路径内完成绘制。
- 保持固定高度歌词 item、短句垂直居中、长主歌词使用随进度移动的两行窗口，并让当前句和下一句保持清晰显示。
- 当受支持播放器的锁屏歌词 UI 正在显示时，阻止屏幕按系统超时时间自动熄灭。

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

- 当前包名存在于 `PLAYER_ADAPTERS`。
- OPlus 歌词 UI 模式处于激活状态。
- 播放状态是正在播放。
- 有歌词证据，例如已经解析出的逐字歌词模型、官方歌词元数据，或者最近可见的官方歌词视图。
- 屏幕仍处于交互状态，并且用户还没有解锁进入桌面。

保活期间，模块还会大约每 8 秒调用一次 `PowerManager.userActivity(...)`，让系统把锁屏歌词视图视为仍在被观看。遇到息屏、用户解锁、播放停止、歌词消失、包名不受支持或其他条件变化时，会立即释放 wake lock。

新增播放器适配时，屏幕超时保活通常不需要单独写适配代码；只要播放器包名同时加入 `scope.list` 和 `PLAYER_ADAPTERS` 即可。如果某个系统版本修改了 `PluginSeedling--Template` 日志格式，则可能需要更新 SystemUI 侧的识别逻辑。

## 播放器适配

支持的播放器声明在 `LockscreenLyricsModule.PLAYER_ADAPTERS` 中。

默认适配器：

```java
new SaltPlayerAdapter()
```

新增播放器适配时，一般需要：

1. 将播放器包名加入 `src/main/resources/META-INF/xposed/scope.list`。
2. 在 `SaltPlayerAdapter` 旁边新增一个 `PlayerAdapter` 实现。
3. 在播放器进程中抓取真实时间轴歌词，并调用 `module.cacheTimedLyric(source, rawLyric)`。
4. 将新的适配器加入 `PLAYER_ADAPTERS`。
5. 保留 `scope.list` 中的 `com.android.systemui`；锁屏绘制和屏幕超时保活都依赖它。

如果某个播放器本身已经写入合法的 OPlus `lyricInfo` 字段，模块不会覆盖它。

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

使用默认 Salt Player 适配器测试：

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

- 目前默认只内置 Salt Player 适配器。
- OPlus SystemUI 的类名和字段属于厂商私有实现，系统版本更新后可能需要重新适配。
- 屏幕超时保活依赖 OPlus 锁屏歌词 UI 日志和官方歌词视图状态；如果 ROM 修改了相关日志或 UI 路径，需要重新适配。
- 锁屏歌词绘制依赖官方歌词视图存在；如果系统界面没有进入歌词 UI，模块不会强行创建新的歌词窗口。
