# LyricProvider Bridge 接入线

SaltLyricLspDemo 侧不再默认进入 QQ 音乐、网易云音乐、Apple Music、Poweramp 等大厂播放器进程抓取歌词。这些播放器后续由 LyricProvider fork 负责适配，SaltLyricLspDemo 只在 `SystemUI` 与 `system_server` 侧完成接收、合成 `lyricInfo`、逐字渲染和历史播放器放行。

## 运行边界

- `PLAYER_ADAPTERS` 只保留 Salt Player 与 ConePlayer 这类仍需本模块进程内兼容的播放器。
- QQ 音乐、QQ 音乐 HD、网易云音乐/荣耀版、Apple Music、Poweramp 进入 bridge 包名集合，只用于 SystemUI 侧识别和 OPlus 历史播放器放行。
- `scope.list` 不再包含上述大厂播放器包名，避免本模块与 LyricProvider fork 同时 hook 同一个播放器进程。

## Bridge 广播

LyricProvider fork 在拿到完整 `Song/RichLyricLine` 后，向 `com.android.systemui` 发送显式广播：

```kotlin
Intent("io.github.andrealtb.lockscreenlyrics.action.EXTERNAL_LYRIC_CAPTURED").apply {
    setPackage("com.android.systemui")
    putExtra("source", "lyricprovider/qq-music")
    putExtra("requestId", requestId)
    putExtra("mediaId", song.id)
    putExtra("songName", song.name)
    putExtra("artist", song.artist)
    putExtra("duration", song.duration)
    putExtra("trackKey", normalizedTitleArtistKey)
    putExtra("lyric", lineTimedLrc)
    putExtra("rawLyric", wordTimedEnhancedLrc)
    putExtra("translationLyric", translationTimedLrc)
    putExtra("capturedAt", System.currentTimeMillis())
}
```

也可以直接额外携带完整 `lyricInfo` JSON，key 使用 `"lyricInfo"`。拆分 extras 优先，缺失字段会从 `lyricInfo` 中补齐。

## 字段约定

- `rawLyric` 是最低要求，必须包含时间标签；有逐字时间轴时使用 enhanced LRC。
- `lyric` 可选；缺失时模块会从 `rawLyric` 规范化出 OPlus 官方逐行列表。
- `translationLyric` 可选；提供后会作为独立翻译时间轴合并进自绘模型，不再依赖主歌词行内猜测。
- `mediaId`、`trackKey`、`songName + artist` 至少提供一组，越强越好；模块会按 media id、media uri、track key、标题歌手依次匹配当前播放项。

## SystemUI 侧行为

- 如果播放器已有有效 `lyricInfo`，bridge 的 `rawLyric/translationLyric` 会覆盖官方简版内容用于自绘逐字。
- 如果播放器没有有效 `lyricInfo`，但 bridge 缓存能匹配当前 `MediaMetadata`，模块会在 SystemUI 侧合成模块 envelope，不需要 LyricProvider fork 修改播放器 `MediaSession`。
- 官方 `lyricInfo` 仍可作为逐行兜底，但不再高于 bridge 增强歌词。
