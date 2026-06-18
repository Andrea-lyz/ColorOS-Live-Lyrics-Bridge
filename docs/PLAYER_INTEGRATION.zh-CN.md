# 播放器主动接入协议

已经能够生成时间轴歌词的播放器，不需要向本模块提交包名，也不需要依赖模块 APK。播放器只需在当前媒体会话的元数据中发布字符串字段 `lyricInfo`，模块会在 SystemUI 侧动态识别当前提供者。

播放器本身不需要加入 LSPosed 作用域。`scope.list` 和 `PlayerAdapter` 只用于 Salt Player 这类尚未原生发布 `lyricInfo`、需要模块进入播放器进程抓取歌词的兼容适配。

## 数据格式

`lyricInfo` 的值是 JSON 字符串：

```json
{
  "songName": "Song title",
  "artist": "Artist",
  "songId": "stable-player-song-id",
  "lyric": "[00:00.00]Line one\n[00:05.20]Line two",
  "rawLyric": "[00:00.000]Line[00:00.320] [00:00.440]one[00:05.200]\n[00:00.000]第一行"
}
```

字段约定：

- `songName`：当前歌曲标题，建议始终提供。
- `artist`：歌手名，建议始终提供。
- `songId`：播放器内稳定且唯一的歌曲标识，建议始终提供。
- `lyric`：必填，至少包含一个有效 LRC 时间标签；这是 OPlus 原生逐行歌词的数据源。
- `rawLyric`：可选，逐字时间轴；提供后会启用本模块的逐字高亮、固定 item 高度、长句两行窗口和清晰度优化。

只提供 `lyric` 也属于完整接入：OPlus 负责逐行显示，本模块仍可动态识别该播放器并处理白名单与屏幕超时逻辑。要获得逐字绘制效果，再增加 `rawLyric`。

## Media3 示例

```kotlin
private const val OPLUS_LYRIC_INFO_KEY = "lyricInfo"

val lyricInfo = JSONObject()
    .put("songName", title)
    .put("artist", artist)
    .put("songId", mediaId)
    .put("lyric", timedLrc)
    .put("rawLyric", wordTimedLrc) // 没有逐字歌词时可省略
    .toString()

val extras = Bundle(currentItem.mediaMetadata.extras ?: Bundle.EMPTY).apply {
    putString(OPLUS_LYRIC_INFO_KEY, lyricInfo)
}
val updatedItem = currentItem.buildUpon()
    .setMediaMetadata(currentItem.mediaMetadata.buildUpon().setExtras(extras).build())
    .build()

player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
```

使用框架 `android.media.session.MediaSession` 时，则把同一个 JSON 写入：

```kotlin
val metadata = android.media.MediaMetadata.Builder(originalMetadata)
    .putString("lyricInfo", lyricInfo)
    .build()
mediaSession.setMetadata(metadata)
```

## 生命周期要求

1. 切歌后更新 `songName`、`artist`、`songId` 和歌词，不要沿用上一首歌的 JSON。
2. 在歌词异步加载完成后重新提交当前媒体元数据。
3. 用户关闭该功能或当前歌曲没有时间轴歌词时，移除 `lyricInfo`。
4. `lyric` 与 `rawLyric` 使用相同的时间偏移，时间单位精确到毫秒或厘秒均可。
5. 不要把当前行反复写入 `lyricInfo`；它承载的是整首时间轴，播放进度仍由媒体会话提供。

## 推荐提交时序

使用事件驱动提交，不要设置每隔数秒运行一次的固定刷新任务：

1. **媒体会话创建或功能开启**：如果当前歌曲的歌词已经就绪，提交一次。
2. **切歌开始**：立即移除上一首歌的 `lyricInfo`，避免旧歌词短暂匹配到新歌曲。
3. **新歌词加载完成**：构造完整 JSON 并立即提交一次，这是正常情况下唯一需要的写入。
4. **会话或当前 `MediaItem` 被重建**：先读取新元数据；仅当 `lyricInfo` 缺失或与目标 JSON 不一致时补交。
5. **可选兼容补交**：部分 Media3/OPlus 版本可能存在元数据传播延迟，可在首次提交约 `800 ms` 后检查一次；仍然缺失时最多补交一次。不要无条件重写，也不要继续周期性重试。
6. **功能关闭、歌词不可用或播放队列清空**：移除 `lyricInfo` 并取消尚未执行的补交任务。

提交前应比较当前值：

```kotlin
val currentJson = currentItem.mediaMetadata.extras
    ?.getString(OPLUS_LYRIC_INFO_KEY)
if (currentJson == lyricInfo) return
```

因此推荐频率不是“每 3 秒一次”，而是通常每首歌 **1 次**，遇到确实丢失元数据的系统最多 **2 次**。播放进度变化、暂停/继续和普通通知刷新都不应触发 `lyricInfo` 重写。

本模块的协议常量和校验规则位于 `LyricInfoContract.java`。播放器无需链接这个类，保持上述 JSON 与元数据键兼容即可。
