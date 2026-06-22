# 播放器主动接入协议

已经能够生成时间轴歌词的播放器，不需要向本模块提交包名，也不需要依赖模块 APK。播放器只需在当前媒体会话的元数据中发布字符串字段 `lyricInfo`，模块会在 SystemUI 侧动态识别当前提供者。

播放器本身不需要加入 LSPosed 作用域。`scope.list` 和 `PlayerAdapter` 只用于 Salt Player、ConePlayer 这类需要模块进入播放器进程抓取歌词的兼容适配。

## OPlus 历史播放器接入（可选）

需要在 ColorOS 控制中心保留历史播放器卡片、并允许 App 完全停止后从卡片重新接收媒体按键的播放器，可在自身 `AndroidManifest.xml` 的 `<application>` 中显式声明：

```xml
<meta-data
    android:name="io.github.andrealtb.lockscreenlyrics.OPLUS_MEDIA_HISTORY"
    android:value="true" />
```

模块会在 system_server 中保留原厂判断，并在原厂名单未命中时额外放行：

- 本模块 `PlayerAdapter` 中的所有内置播放器包名。
- 声明上述 Manifest 元数据的外部播放器。

外部播放器不需要加入本模块的 LSPosed 作用域，也不需要依赖模块 APK。公共键同时暴露为 `LyricInfoContract.MANIFEST_METADATA_OPLUS_MEDIA_HISTORY`。

该声明只负责允许播放器进入 OPlus 历史播放器栈。播放器仍需自行：

1. 创建有效的 `MediaSession`，并设置媒体按键接收器或 `PendingIntent`。
2. 在完全停止后正确处理 `android.intent.action.MEDIA_BUTTON`。
3. 启动自身播放服务并恢复播放状态。

模块不会为外部播放器猜测服务类名、Action 或播放队列，也不会替它伪造后台启动逻辑。已经位于 ColorOS 原厂白名单中的播放器无需添加该声明。

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
- 模块会在 OPlus 消费 `lyric` 前，把同时间戳双语分组规范化为一个主歌词 item；完整翻译和逐字时间轴应继续放在 `rawLyric` 或带时间戳的翻译字段中。
- 仅包含零宽字符的占位行会被忽略。不要依赖不可见占位行控制官方列表位置。

只提供 `lyric` 也属于完整歌词接入：OPlus 负责逐行显示，本模块仍可动态识别该播放器并处理歌词策略与屏幕超时逻辑。历史播放器卡片属于独立能力，需要原厂支持、内置适配器或上述 Manifest 显式声明。要获得逐字绘制效果，再增加 `rawLyric`。

## 数据源优先级

对于不在内置兼容适配器作用域内的播放器，合法的播放器 payload 会直接在 SystemUI 中使用。对于模块同时进入播放器进程的 Salt Player 或 ConePlayer，优先级为：

1. 播放器主动提供的增强 payload：包含带时间轴的 `rawLyric` 或翻译数据。
2. 模块兼容适配器抓取并确认属于当前歌曲的时间轴歌词。
3. 播放器或 OPlus 官方只包含逐行歌词的简单 `lyricInfo`。

因此，播放器未来只提供简单逐行 `lyricInfo` 时，它会安全地作为兜底；适配器取得逐字与翻译原始数据后仍可接管。模块接管时会重新构造 payload，不会混用简单官方 payload 中的字段。

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

如果歌词在开始播放前已经可用，应直接让首次发布的当前 `MediaItem`、媒体会话元数据和通知元数据都携带完整 `lyricInfo`，不要先发布无歌词版本，再在几毫秒内只补写一次 `extras`。

使用框架 `android.media.session.MediaSession` 时，则把同一个 JSON 写入：

```kotlin
val metadata = android.media.MediaMetadata.Builder(originalMetadata)
    .putString("lyricInfo", lyricInfo)
    .build()
mediaSession.setMetadata(metadata)
```

## 翻译按钮接入（可选）

播放器可在 `PlaybackState` 的自定义动作列表中发布以下标准动作，让模块在 OPlus 锁屏歌词界面接管为翻译开关：

```kotlin
private const val ACTION_TOGGLE_TRANSLATION =
    "io.github.andrealtb.lockscreenlyrics.action.TOGGLE_TRANSLATION"

val action = PlaybackState.CustomAction.Builder(
    ACTION_TOGGLE_TRANSLATION,
    "歌词翻译",
    R.drawable.any_valid_media_icon // Android API 要求非零资源 ID
).build()
```

将该动作放在自定义动作列表首位。Android API 要求传入有效图标资源，但播放器无需专门制作翻译图标：模块会优先使用播放器包内名为 `ic_translation` 的资源，缺失时自动使用内置的 Salt 风格翻译图标。仅在当前 `lyricInfo` 含有可用翻译时发布；切歌或翻译不可用时移除。模块识别动作后会自动启用 OPlus 自定义动作槽位、接管点击事件并保存开关状态，播放器无需处理该动作回调。未安装模块时，该动作仍会由系统转发给播放器，因此播放器应安全地忽略未知或无操作回调。

## 生命周期要求

1. 切歌后更新 `songName`、`artist`、`songId` 和歌词，不要沿用上一首歌的 JSON。
2. 在歌词异步加载完成后重新提交当前媒体元数据。
3. 用户关闭该功能或当前歌曲没有时间轴歌词时，移除 `lyricInfo`。
4. `lyric` 与 `rawLyric` 使用相同的时间偏移，时间单位精确到毫秒或厘秒均可。
5. 不要把当前行反复写入 `lyricInfo`；它承载的是整首时间轴，播放进度仍由媒体会话提供。

## 推荐提交时序

使用事件驱动提交，不要设置每隔数秒运行一次的固定刷新任务：

1. **媒体会话创建或功能开启**：如果当前歌曲的歌词已经就绪，提交一次；首次 `MediaItem`、媒体会话元数据和通知元数据应描述同一首歌并携带同一份完整 `lyricInfo`。
2. **切歌开始**：立即移除上一首歌的 `lyricInfo`，避免旧歌词短暂匹配到新歌曲。
3. **新歌词加载完成**：构造完整 JSON 并提交一次；如果播放已经开始，应基于最新的当前项目元数据更新，并把当前 `MediaItem`、媒体会话与通知刷新视为同一个逻辑操作。
4. **会话或当前 `MediaItem` 被重建**：先读取新元数据；仅当 `lyricInfo` 缺失或与目标 JSON 不一致时补交。
5. **避免紧邻补丁**：不要先发布不含歌词的新曲元数据，随后几毫秒只修改 `extras`。OPlus 可能把第二次更新判定为防抖窗口内更新（日志表现为 `within debounce period, ignore`），导致首曲一直是 `hasLyric=false`，直到下一次切歌才恢复。
6. **可选兼容补交**：部分 Media3/OPlus 版本可能存在元数据传播延迟，可在首次完整提交约 `800 ms` 后检查一次；仍然缺失时最多补交一次。不要无条件重写，也不要继续周期性重试。
7. **功能关闭、歌词不可用或播放队列清空**：移除 `lyricInfo` 并取消尚未执行的补交任务。

提交前应比较当前值：

```kotlin
val currentJson = currentItem.mediaMetadata.extras
    ?.getString(OPLUS_LYRIC_INFO_KEY)
if (currentJson == lyricInfo) return
```

因此推荐频率不是“每 3 秒一次”，而是通常每首歌 **1 次**，遇到确实丢失元数据的系统最多 **2 次**。播放进度变化、暂停/继续和普通通知刷新都不应触发 `lyricInfo` 重写。

### 首曲接入检查表

- `lyricInfo` 使用的标题、歌手和稳定歌曲 ID 必须与当前 `MediaItem` 一致。
- 歌词已经可用时，在首次发布元数据与通知前就附加完整 `lyricInfo`。
- 歌词异步到达时，从最新元数据克隆，不要复用切歌前或播放启动前保存的旧快照；只用完整 payload 替换一次当前项目。
- 不要依赖紧随首次元数据之后的 extras-only 更新穿过 OPlus 媒体管线。
- 联调时，如果播放器已经记录“发布成功”，但 SystemUI 随后仍显示 `hasLyric=false`，通常表示更新在 SystemUI 消费前被 OPlus 防抖丢弃，并不是本模块拒绝了歌词格式。

本模块的协议常量和校验规则位于 `LyricInfoContract.java`。播放器无需链接这个类，保持上述 JSON 与元数据键兼容即可。
