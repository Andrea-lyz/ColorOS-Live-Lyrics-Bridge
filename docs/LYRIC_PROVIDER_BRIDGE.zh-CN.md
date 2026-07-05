# LyricProvider Bridge 接入线

ColorOS-Live-Lyrics-Bridge 只负责 Bridge：在 `SystemUI` 和 `system_server` 侧接收歌词、合成/覆盖 `lyricInfo`、渲染逐字歌词，并把受支持播放器放进 OPlus 历史播放器和 AOD 媒体面板白名单。QQ 音乐、网易云音乐、Apple Music、Poweramp、Spotify、汽水音乐、酷狗音乐等播放器进程内的抓取逻辑放在 `Andrea-lyz/LyricProvider` fork 里，以独立 Provider APK 交付。

## 运行边界

- Bridge APK 的静态作用域只保留 `system`、`com.android.systemui`、Salt Music、ConePlayer 及其 Google Play 包名。
- `PLAYER_ADAPTERS` 只放仍需 Bridge 进播放器进程兼容的内置适配器，例如 Salt Music、ConePlayer。
- LyricProvider fork 的 Provider APK 单独安装、单独启用，并只作用于它自己的目标播放器。
- 不要把 QQ 音乐、网易云音乐、Apple Music、Poweramp、Spotify、汽水音乐、酷狗音乐这类 Provider 目标包名加回 Bridge 的 `scope.list`，避免 Bridge 和 Provider 同时 hook 同一个播放器进程。

## 当前 Bridge 侧登记

Bridge 侧的 Provider/播放器信息集中在 `ExternalLyricSources`：

| 播放器 | 目标包名 | Bridge 用途 |
| --- | --- | --- |
| QQ 音乐 | `com.tencent.qqmusic` | bridge 播放器包名、历史播放器/AOD 放行、翻译开关 |
| QQ 音乐 HD | `com.tencent.qqmusicpad` | bridge 播放器包名、历史播放器/AOD 放行、翻译开关 |
| 网易云音乐 | `com.netease.cloudmusic` | bridge 播放器包名、历史播放器/AOD 放行、翻译开关 |
| 网易云音乐荣耀版 | `com.hihonor.cloudmusic` | bridge 播放器包名、历史播放器/AOD 放行、翻译开关 |
| Apple Music | `com.apple.android.music` | bridge 播放器包名、`lyricprovider/apple-music` source、历史播放器/AOD 放行、翻译开关 |
| Poweramp | `com.maxmpz.audioplayer` | bridge 播放器包名、`lyricprovider/poweramp-music` source、历史播放器/AOD 放行、外部歌词提升 |
| Spotify | `com.spotify.music` | bridge 播放器包名、`lyricprovider/spotify-music` source、历史播放器/AOD 放行、外部播放状态 |
| 汽水音乐 | `com.luna.music` | bridge 播放器包名、`lyricprovider/qishui-music` source、历史播放器/AOD 放行、外部播放状态、翻译开关 |
| 酷狗音乐 | `com.kugou.android` | bridge 播放器包名、`lyricprovider/kugou-music` source、历史播放器/AOD 放行、外部播放状态、翻译开关 |
| 酷狗音乐概念版 | `com.kugou.android.lite` | bridge 播放器包名、`lyricprovider/kugou-concept-music` source、历史播放器/AOD 放行、外部播放状态、翻译开关 |

包名在 `BRIDGE_PLAYER_PACKAGES` 后，会自动进入 OPlus 历史播放器和 AOD 媒体面板放行逻辑。source 在 `EXTERNAL_SOURCES` 后，Bridge 才能把 Provider 广播反查到播放器包名，用于重试提升、无官方 `lyricInfo` 时的当前曲目绑定，以及可选的外部播放状态接收。

## 新 Provider 接入清单

1. 在 LyricProvider fork 中新增播放器模块，负责 hook 目标播放器并生成完整歌词数据。
2. Provider 模块向 `com.android.systemui` 发送显式 `EXTERNAL_LYRIC_CAPTURED` 广播。
3. 在 Bridge 的 `ExternalLyricSources` 中加入目标播放器包名。
4. 如果 Provider 依赖外部广播提升歌词，或需要给 Bridge 提供播放状态，在 `EXTERNAL_SOURCES` 中加入稳定 source 映射。
5. 为新增映射补 `ExternalLyricSourcesTest`，至少覆盖包名白名单、source 到包名映射和能力开关。
6. 不修改 Bridge 的 `scope.list`，除非这个播放器确实要改成 Bridge 内置进程适配器。
7. 发布前把 Provider APK 构建 job 加入 `.github/workflows/release.yml`，并同步 README、release note、AGENTS 中的产物列表。

## Bridge 广播

Provider 拿到完整曲目信息和歌词后，发送显式广播：

```kotlin
Intent("io.github.andrealtb.lockscreenlyrics.action.EXTERNAL_LYRIC_CAPTURED").apply {
    setPackage("com.android.systemui")
    putExtra("source", "lyricprovider/<player-slug>")
    putExtra("eventType", "lyricReady")
    putExtra("requestId", requestId)
    putExtra("mediaId", song.id)
    putExtra("mediaUri", song.uri)
    putExtra("songName", song.name)
    putExtra("artist", song.artist)
    putExtra("duration", song.duration)
    putExtra("trackKey", normalizedTitleArtistKey)
    putExtra("rawLyric", wordTimedEnhancedLrc)
    putExtra("lyric", lineTimedLrc)
    putExtra("translationLyric", translationTimedLrc)
    putExtra("trackGeneration", generation)
    putExtra("capturedAt", System.currentTimeMillis())
}
```

也可以额外携带完整 `lyricInfo` JSON，key 使用 `"lyricInfo"`。拆分 extras 优先；缺失字段会从 `lyricInfo` 中补齐。只发 `lyricInfo` 时，`lyricInfo.lyric` 必须是带时间标签的逐行 LRC，否则 Bridge 会按无效 payload 处理。

## 字段约定

- `source` 使用稳定、唯一的 `lyricprovider/<player-slug>`，并和 Bridge 侧 `EXTERNAL_SOURCES` 保持一致。
- `rawLyric` 是外部广播的最低要求，必须包含时间标签；有逐字时间轴时使用 enhanced LRC。
- `lyric` 可选；缺失时 Bridge 会从 `rawLyric` 规范化出 OPlus 官方逐行列表。
- `translationLyric` 可选；提供后会作为独立翻译时间轴合并进自绘模型。
- `mediaId`、`mediaUri`、`trackKey`、`songName + artist` 至少提供一组，越强越好；Bridge 会按 media id、media uri、track key、标题歌手依次匹配当前播放项。
- `trackGeneration` 用于区分同一播放器内的曲目代际。像 Poweramp 这种 SystemUI 曲目和歌词到达顺序不稳定的播放器，应提供单调递增的 generation。
- `eventType` 可使用 `trackChanged` 或 `lyricReady`。`trackChanged` 可先通知 Bridge 当前曲目已切换；`lyricReady` 表示歌词可用于缓存或提升。

如果 Provider 能稳定提供播放状态，可额外发送：

```kotlin
putExtra("playbackState", state)
putExtra("playbackPosition", positionMs)
putExtra("playbackSpeed", speed)
putExtra("playbackLastPositionUpdateTime", elapsedRealtimeMs)
```

同时在 Bridge 侧把该 source 的 `supportsPlaybackState` 设为 `true`。

## Source 能力

`ExternalLyricSources.Source` 的三个布尔能力要谨慎开启：

- `supportsPlaybackState`：Provider 广播里会提供可信播放状态和进度。Spotify、汽水音乐和酷狗音乐当前使用这个能力。
- `canPromoteAsAuthoritative`：当 SystemUI 尚未给出可匹配曲目时，Bridge 可以短时间信任 Provider 的曲目信息并直接提升为当前歌词。只给确实能证明“这是当前曲目”的 Provider 开启。Poweramp 与酷狗音乐/概念版当前使用这个能力，酷狗侧依赖 generation/key 校验。
- `allowsTitleOnlyFallbackMatch`：允许只用标题做兜底匹配，适合本地播放器缺歌手、歌手格式漂移或 metadata 不稳定的场景。开启后误匹配风险更高，应尽量配合 generation 或 media id。

QQ/网易这类已经有官方 `lyricInfo` 或能稳定走播放器 metadata 的适配，不一定需要 source 能力表；但如果 fork 模块改为只靠外部广播把歌词提升到 SystemUI，就必须补 `EXTERNAL_SOURCES` 映射。

## SystemUI 侧行为

- 如果播放器已有有效 `lyricInfo`，Bridge 的 `rawLyric/translationLyric` 会覆盖官方简版内容用于自绘逐字。
- 如果播放器没有有效 `lyricInfo`，但外部广播 source 能映射回播放器包名，且缓存能匹配当前 `MediaMetadata`，Bridge 会在 SystemUI 侧合成模块 envelope。
- 官方 `lyricInfo` 仍可作为逐行兜底，但 bridge 增强歌词优先用于逐字和翻译渲染。
- Provider 发送的文本会走 `ExternalLyricTextRepair` 的 LyricProvider mojibake 修复路径，避免 fork/目标播放器编码不一致时污染渲染模型。

## 发布注意

Provider APK 是辅助模块，不打进 Bridge APK。发布一个新 Provider 时，需要同时更新：

- `ExternalLyricSources` 与对应单元测试。
- `.github/workflows/release.yml` 的 Provider 构建 job、`publish` job `needs` 和 fallback release note。
- `README.md`、`README.zh-CN.md`、`.github/lsposed/` 元数据中面向用户的 Provider APK 列表。
- `.github/release-notes/<version>.md` 与 `docs/releases/v<version>.md`。

汽水音乐 Provider 还需要在用户文档和 release note 中提醒：必须在 LSP 管理器中为汽水音乐开启“还原内联钩子”，对列表中的应用清理 `libart.so`，否则汽水音乐可能触发“当前版本不安全”提示并影响 Provider 工作。
