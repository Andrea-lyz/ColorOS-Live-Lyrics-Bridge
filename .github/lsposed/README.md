# ColorOS Live Lyrics Bridge

## 简体中文

将受支持音乐播放器的时间轴歌词桥接到 ColorOS/OPlus 原生锁屏歌词界面，并补充逐字高亮、翻译切换、媒体卡片和后台恢复能力。

### 主要功能

- 内置 Salt Player 与 ConePlayer 兼容适配器。
- 支持播放器通过 `MediaMetadata["lyricInfo"]` 主动接入，无需依赖模块 APK。
- 支持逐行 LRC、逐字 `rawLyric`、翻译行识别和重复歌词稳定定位。
- 通过通用歌词事务层隔离异步回调，避免有歌词/无歌词曲目连续切换时歌词错绑或后续持续显示无歌词。
- 长日语、中文歌词按 Unicode 字符边界换行，避免无空格长句被自动缩小。
- 保留播放器原始媒体 action 语义，仅通过 OPlus Rule0 提供翻译按钮，避免上一首、播放/暂停、下一首错位。
- Salt Player 完全停止后可从 ColorOS 历史媒体卡片恢复播放。
- ConePlayer 冷启动恢复播放时可从已选中音轨元数据恢复歌词。
- 内置播放器自动接入 OPlus 历史播放器；外部播放器可通过 Manifest 元数据主动申请接入。

### 推荐作用域

本仓库的 [`SCOPE`](SCOPE) 使用 LSPosed 模块仓库要求的 JSON 数组格式：

```json
["system", "com.salt.music", "ink.trantor.coneplayer", "ink.trantor.coneplayer.gp", "com.android.systemui"]
```

| 作用域 | 用途 |
| --- | --- |
| `system` | 在 system_server 中扩展 OPlus 历史播放器判断。 |
| `com.salt.music` | Salt Player 歌词抓取与后台播放恢复。 |
| `ink.trantor.coneplayer` | ConePlayer 正式版歌词适配。 |
| `ink.trantor.coneplayer.gp` | ConePlayer Google Play 版歌词适配。 |
| `com.android.systemui` | 锁屏歌词渲染、翻译按钮、媒体 action 与屏幕超时处理。 |

外部播放器仅通过公开 `lyricInfo` 协议接入歌词时，通常无需加入播放器作用域。若需要进入 OPlus 历史播放器栈，可在自身 `AndroidManifest.xml` 中声明：

```xml
<meta-data
    android:name="io.github.andrealtb.lockscreenlyrics.OPLUS_MEDIA_HISTORY"
    android:value="true" />
```

该声明不会替播放器实现 `MediaSession`、媒体按键接收、后台服务启动或播放队列恢复。

### 安装与升级

1. 从 Releases 下载 APK 并安装。
2. 在 LSPosed 中启用模块，并确认推荐作用域包含 `system`、`com.android.systemui` 和需要进程内适配的播放器。
3. 重启设备，使 SystemUI、system_server 和播放器进程中的 Hook 完整加载。

源码、完整接入协议和问题反馈：

- [源码仓库](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge)
- [播放器接入协议](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/blob/main/docs/PLAYER_INTEGRATION.zh-CN.md)
- [Issues](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/issues)

## English

Bridges timed lyrics from supported music players into the native ColorOS/OPlus lock-screen lyric UI, with word-level highlighting, translation controls, media-card integration, and playback recovery.

### Highlights

- Built-in compatibility adapters for Salt Player and ConePlayer.
- Public `MediaMetadata["lyricInfo"]` protocol for self-integrating players without an APK dependency.
- Line-timed LRC, word-timed `rawLyric`, translation detection, and stable repeated-line matching.
- A generic lyric transaction layer prevents stale asynchronous callbacks from binding across tracks, including sequences that contain instrumentals or no-lyric tracks.
- Long Japanese and Chinese lyric lines wrap at Unicode character boundaries instead of being reduced to tiny text.
- Preserves the player's original media-action semantics and exposes translation only through OPlus Rule0, preventing previous/play-pause/next slot corruption.
- Restores Salt Player playback from the ColorOS history media card after the app has fully stopped.
- Restores ConePlayer lyrics from selected audio-track metadata during background playback resumption.
- Automatically accepts built-in adapters into OPlus media history; external players may opt in through manifest metadata.

### Recommended Scope

The repository [`SCOPE`](SCOPE) follows the required JSON-array format:

```json
["system", "com.salt.music", "ink.trantor.coneplayer", "ink.trantor.coneplayer.gp", "com.android.systemui"]
```

| Scope | Purpose |
| --- | --- |
| `system` | Extends OPlus media-history decisions in system_server. |
| `com.salt.music` | Salt Player lyric capture and background playback recovery. |
| `ink.trantor.coneplayer` | ConePlayer standard-package lyric adapter. |
| `ink.trantor.coneplayer.gp` | ConePlayer Google Play-package lyric adapter. |
| `com.android.systemui` | Lock-screen rendering, translation action, media actions, and screen-timeout handling. |

External players that only publish the public `lyricInfo` payload usually do not need player-process scope. To opt into OPlus media history, an external player may declare:

```xml
<meta-data
    android:name="io.github.andrealtb.lockscreenlyrics.OPLUS_MEDIA_HISTORY"
    android:value="true" />
```

This declaration does not replace the player's own `MediaSession`, media-button receiver, playback service, or queue-restoration implementation.

### Installation

1. Download and install the APK from Releases.
2. Enable the module in LSPosed and confirm that `system`, `com.android.systemui`, and the required built-in player scopes are selected.
3. Reboot the device so the SystemUI, system_server, and player-process hooks are loaded.

Source, integration documentation, and support:

- [Source repository](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge)
- [Player integration protocol](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/blob/main/docs/PLAYER_INTEGRATION.md)
- [Issues](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/issues)
