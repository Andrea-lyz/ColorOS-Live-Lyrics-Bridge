# 大厂音乐 App Hook 工程线路

本文记录 `SaltLyricLspDemo` 后续适配 Apple Music、网易云音乐/荣耀版、QQ 音乐、QQ 音乐 HD、酷狗音乐/概念版、Poweramp、汽水音乐时的工程路线。

目标不是引入另一个歌词发布总线，而是在目标播放器进程内拿到整首时间轴歌词，复用本项目现有 `PlayerAdapter` 管线，最终注入 OPlus 可消费的 `MediaSession` `lyricInfo`。

## 总体原则

- 主参考项目：`LyricProvider`。它多数适配已经拿到整首歌词、缓存文件或解析后的逐字模型，更贴近本项目的 `lyricInfo.lyric + rawLyric` 目标。
- 辅助参考项目：`SuperLyric`。它覆盖面广，但多数路线是当前行/蓝牙歌词/状态栏歌词发布，更适合作 fallback 或 hook 点交叉验证。
- 每个目标 App 独立实现一个 `PlayerAdapter`，不要把 Lyricon provider、SuperLyric Binder 服务或 UI 配置体系整体搬进来。
- 适配器只负责在播放器进程缓存真实歌词：拿到歌词后调用 `LockscreenLyricsModule.cacheTimedLyric(...)` 或新增可携带翻译/逐字结构的等价入口；`MediaSession#setMetadata` 注入仍由主模块统一处理。
- 歌词转换优先保留两层数据：
  - `lyric`：逐行 LRC，给 OPlus 官方列表消费。
  - `rawLyric`：逐字/卡拉 OK 时间轴，给本模块自绘逐字高亮消费。

## 后台恢复与 UI 生命周期

适配播放器时，必须把“媒体会话恢复播放”和“歌词链路恢复”分开验证。系统媒体卡片能够通过 `MEDIA_BUTTON` 或 Media3 playback resumption 重新启动播放，不代表播放器同时恢复了歌词加载。

重点规则：

- 测试冷启动时应先彻底停止播放器，再直接点击 ColorOS 历史媒体卡片的播放按钮；不要预先打开播放器 Activity。
- 不能只验证音频、标题、歌手和封面恢复。还要确认整首歌词来源 hook 在 Activity 未创建时是否执行。
- 解析器 hook 只能捕获“已经有人提交给解析器的歌词”。如果歌词加载由 Fragment、Activity 或 ViewModel 驱动，纯后台恢复可能永远不会调用解析器。
- 优先寻找后台播放服务天然可见的数据源，例如：
  - Media3 选中音轨的 `Format.metadata`
  - 音频标签中的 `LYRICS`、`USLT` 等字段
  - 播放服务使用的数据库、磁盘缓存或歌词仓库
  - 服务内部的曲目变化、音轨变化或歌词加载回调
- 不建议为补歌词而偷偷启动播放器 Activity，也不要模拟用户切歌。这样会改变播放状态、产生界面副作用，并掩盖真实的后台恢复缺陷。
- 如果后台服务确实没有歌词数据，而歌词只能由 UI 发起网络请求，适配器需要调用播放器自己的仓库/UseCase；仍应避免创建完整 UI 或 ViewModel 生命周期。
- 必须过滤播放器的“暂无歌词”“No lyrics”“纯音乐”等带伪时间标签的占位文本，不能因为它包含 `[00:00.00]` 就当作真实歌词发布。
- 服务级歌词来源与原解析器 hook 可以并存：服务级入口负责冷启动，解析器入口负责 UI 已打开、外部歌词或其他格式的兼容路径。

### ConePlayer 已验证案例

ConePlayer 的 `MediaPlayerService` 可以通过 Media3 `onPlaybackResumption` 在完全停止后恢复播放队列、当前歌曲和进度，但原有歌词流程依赖界面层：

1. `AudioPlaybackFragment` 收到音轨格式。
2. Fragment 将 `AudioFormat.c()` 中的歌词写入 `AudioPlaybackViewModel.currentLrc`。
3. `currentLrc` 的观察者调用 `MediaPlayerService.LocalBinder.setCurrentLyric(String)`。
4. `setCurrentLyric` 才调用 LRC 解析器并更新播放服务的歌词列表。

因此只启动后台服务时，Fragment 和 ViewModel 不存在，解析器 hook 不会收到真实歌词。日志中的典型表现是播放已经开始，但持续出现：

```text
Skip lyricInfo injection because no fresh real lyric is cached
```

打开 ConePlayer Activity 后才出现解析器日志，说明缺口在歌词生产端，不在 `MediaSession#setMetadata` 注入端。

当前适配策略：

- 保留 DexKit 定位的 LRC 解析器 hook，兼容正常 UI、外部歌词及后续解析路径。
- 额外 hook `MediaPlayerService#onTracksChanged`。
- 只读取已选中的音频轨道，避免误取字幕、图片或未选中音轨。
- 从 `Format.metadata` 提取 Vorbis `LYRICS`、ID3 `USLT` 等整首时间轴歌词。
- 过滤 `[00:00.00]暂无歌词` 等占位内容。
- 通过 `cacheTimedLyric(...)` 交给统一曲目绑定和 `lyricInfo` 注入逻辑，不直接修改 ConePlayer 的播放控制。

预期日志：

```text
Hooked ConePlayer selected audio-track metadata
Cached real timed lyric from ConePlayer track metadata
```

该路线不会启动 Activity、发送额外媒体按键或主动切歌。已验证 ConePlayer 完全停止后，可直接从 ColorOS 媒体卡片恢复播放并取得歌词。

## 官方 lyricInfo 覆盖策略

QQ 音乐、网易云音乐等 App 在部分 ROM、版本或机型上可能已经自行向 `MediaSession` 写入 OPlus 可消费的官方 `lyricInfo`。但这些官方实现通常只提供简版逐行歌词，不包含本项目需要的逐字时间轴、翻译合并或更完整的 `rawLyric`。因此官方 `lyricInfo` 不能作为最高优先级结果；我们的目标是在拿到增强歌词后覆盖它。

优先级建议：

1. 本项目 adapter 获取到的整首增强歌词，包含逐字 `rawLyric` 时优先。
2. 本项目 adapter 获取到的整首逐行歌词。
3. App 已写入的官方 `lyricInfo`，仅作为 adapter 结果未就绪时的临时 fallback。
4. 当前行/蓝牙歌词/status bar fallback。
5. 无歌词。

处理规则：

- 在 `MediaSession#setMetadata` hook 中先检查原始 `MediaMetadata` 是否已经包含 `LyricInfoContract.KEY_LYRIC_INFO`，用于识别 App 官方输出能力和记录对照日志。
- 如果当前曲目的 adapter 增强歌词已经就绪，无论原始 metadata 是否存在官方 `lyricInfo`，都用本项目生成的增强 `lyricInfo` 覆盖。
- 如果官方 `lyricInfo` 存在但缺少 `rawLyric`、逐字时间轴、翻译结构，仍应启动 adapter 的下载/解析路线。
- 如果 adapter 歌词尚未就绪，可暂时放行官方 `lyricInfo`，避免锁屏完全无歌词；adapter 异步完成后再触发一次 metadata 更新或在下一次 `setMetadata` 时覆盖。
- 如果 adapter 下载/解析失败，才保留官方 `lyricInfo` 作为兜底。
- 对本项目自己注入过的 metadata 要加重入保护，避免 `setMetadata` 被二次 hook 后重复写入。

建议实现一个统一判断入口：

```text
OfficialLyricInfoDetector.from(metadata)
  -> Missing
  -> Invalid(reason)
  -> Valid(trackIdentity, lyricInfoJson)
```

日志建议：

```text
Official lyricInfo detected package=...
Official lyricInfo is simple, schedule enhanced adapter lyric fetch
Override official lyricInfo with Salt enhanced lyricInfo
Temporarily keep official lyricInfo because adapter lyric is pending
Fallback to official lyricInfo because adapter lyric failed
```

这条规则尤其适用于 QQ 音乐、网易云音乐/荣耀版：官方 `lyricInfo` 只证明目标 App/ROM 链路可用，不代表内容质量足够。QRC/YRC 等增强路线拿到结果后，应覆盖官方简版输出。

## 共享工程任务

1. 将目标包名加入 `app/src/main/resources/META-INF/xposed/scope.list`。
2. 为每个 App 新增 `PlayerAdapter` 实现，并加入 `LockscreenLyricsModule.PLAYER_ADAPTERS`。
3. 抽一个小型歌词转换工具，把外部模型统一转成：
   - line-timed LRC
   - word-timed enhanced LRC/raw lyric
   - optional translation timed LRC
4. 对网络/磁盘读取类适配器加去重和当前曲目校验，避免异步歌词回写到下一首歌。
5. 为纯解析逻辑加 JVM 单测；hook 定位逻辑主要靠设备日志验证。

## DexKit 使用策略

`LyricProvider` 不是整体依赖 DexKit 实现 hook。它主要在少数私有、混淆、版本漂移概率高的位置使用 DexKit，例如：

- 网易云音乐：用 DexKit 查找内部 `SharedPreferences` 获取方法，用于监听翻译/音译偏好。
- 酷狗音乐/概念版：用 DexKit 查找 `LyricManager` 中包含 `"file is not krc or lyc or txt file"` 的歌词文件加载方法。

本项目可以引入 DexKit，但定位为“私有 hook 点解析器”，不要把所有 adapter 都改成 DexKit 扫描。

使用边界：

- 系统稳定点继续直接 hook：`MediaSession#setMetadata`、`MediaSession#setPlaybackState`、Poweramp `TRACK_CHANGED` 广播。
- App 私有点优先固定类名/签名 hook，失败后再走 DexKit fallback。
- 对混淆明显、方法名易变但字符串/参数稳定的点，直接使用 DexKit 更合适。
- DexKit 结果需要按 `packageName + versionCode + sourceDir.lastModified` 缓存，版本变化后再重新扫描。
- DexKit 只能提升抗混淆能力，不能保证所有版本兼容；如果 App 删除字符串、重写缓存格式或关闭相关歌词链路，仍需要降级策略和日志提示。

适配目标建议：

- 酷狗音乐/概念版：优先 DexKit。歌词文件加载方法没有稳定方法名，但字符串和参数特征明确。
- 网易云音乐/荣耀版：DexKit 用于偏好/内部工具方法；歌曲 ID、播放状态仍走 `MediaSession`。
- QQ 音乐 HD：可准备 DexKit fallback，用参数类型或特征定位 `RemoteControlManager` 的 songId 来源方法。
- Apple Music：先按已知私有类名实现；如果版本漂移明显，再为 `PlayerLyricsViewModel` 相关方法补 DexKit。
- QQ 音乐、汽水音乐、Poweramp：第一阶段不需要 DexKit，固定入口已经足够清晰。

工程形态建议：

```text
HookPointResolver
  DirectResolver: className + methodName + params
  DexKitResolver: strings + params + returnType + package scope
  Cache: package/version/sourceDir fingerprint -> resolved method descriptor
```

这样 adapter 代码只关心“拿到哪个 Method”，不直接散落 DexKit 查询细节。

## Apple Music

推荐来源：`LyricProvider/apple-music`

目标包名：`com.apple.android.music`

主要 hook 点：

- `android.support.v4.media.MediaMetadataCompat` 的公开静态转换方法：监听 Apple Music 元数据变化，拿 `MediaMetadata` 和 mediaId。
- `com.apple.android.music.player.viewmodel.PlayerLyricsViewModel#buildTimeRangeToLyricsMap`：歌词构建完成后，从参数 `get()` 出 native song 对象。
- `com.apple.android.music.player.viewmodel.PlayerLyricsViewModel#loadLyrics`：本地缓存 miss 时，可构造 Apple `Song` 并调用它，让官方逻辑下载歌词。
- `com.apple.android.music.playback.player.ExoMediaPlayer` 构造器和 `getCurrentPosition`：可用于播放进度同步。
- `com.apple.android.music.playback.controller.LocalMediaPlayerController#onPlaybackStateChanged`：同步播放/暂停状态。

数据路线：

1. `MediaMetadata` 变化后记录 mediaId、标题、歌手、时长。
2. 先查本地歌词缓存。
3. 没有缓存时，通过 `PlayerLyricsViewModel#loadLyrics` 触发官方下载。
4. `buildTimeRangeToLyricsMap` 被调用后解析 native song。
5. 将 Apple 歌词行、逐字、翻译、背景人声转换为本项目 `lyricInfo`。

迁移重点：

- 保留 `AppleSongParser` / `AppleSongMapper` 的字段识别思路。
- 不引入 Lyricon provider，只保留 native song -> 本项目歌词模型的转换。
- Apple 的逐字和翻译信息质量高，适合作第一批高质量逐字适配。

风险：

- Apple 私有类名和方法名可能随版本变化。
- `loadLyrics` 诱导下载依赖 Apple 内部鉴权和缓存状态，失败时要降级为无歌词。

## 网易云音乐 / 荣耀版

推荐来源：`LyricProvider/163-music`

目标包名：

- `com.netease.cloudmusic`
- `com.hihonor.cloudmusic`

主要 hook 点：

- 目标进程：主包名进程和 `:play` 进程。
- `android.media.session.MediaSession#setMetadata(MediaMetadata)`：从 `METADATA_KEY_MEDIA_ID` 取歌曲 ID。
- `android.media.session.MediaSession#setPlaybackState(PlaybackState)`：同步播放状态。
- `com.tencent.tinker.loader.TinkerLoader#tryLoad`：Tinker 热更新后重新安装偏好/类加载相关 hook。

数据路线：

1. `setMetadata` 拿网易云歌曲 ID、标题、歌手、时长。
2. 查本地歌词缓存文件。
3. 缓存 miss 时调用网易云歌词下载逻辑，获取：
   - `lrc`
   - `lrcTranslateLyric`
   - `yrc`
   - `yrcTranslateLyric`
   - `roma`
4. 优先解析 `yrc` 生成逐字主歌词。
5. 没有 `yrc` 时退回 `lrc`。
6. 翻译和罗马音按时间就近合并。

荣耀版判断：

- `LyricProvider` 的 scope 已同时包含网易云和荣耀版，说明可优先复用同一条下载/解析路线。
- `SuperLyric/Hihonor` 主要是魅族状态栏/通知歌词 fallback，不建议作为整首歌词主线。

迁移重点：

- 直接移植 `YrcDownloader`、`YrcParser`、网易云加密请求和缓存结构。
- `yrc` 适合作 `rawLyric`；`lrc` 适合作 `lyric` fallback。
- 即使 `MediaMetadata` 已经带官方 `lyricInfo`，仍应启动 YRC/LRC 增强路线；拿到逐字或更完整歌词后覆盖官方简版输出。
- 偏好监听、Tinker 重新加载等私有点可以用 DexKit；主歌曲识别仍以 `MediaSession` metadata 为准。

风险：

- 荣耀版包内类加载、登录态和接口参数可能和普通网易云不同，要实机确认 `MediaMetadata.MEDIA_ID` 是否仍是歌曲 ID。
- 网络下载失败时必须保留当前 metadata，但不注入旧歌词。

## QQ 音乐

推荐来源：`LyricProvider/qq-music`

目标包名：

- `com.tencent.qqmusic`
- 播放服务进程：`com.tencent.qqmusic:QQPlayerService`

主要 hook 点：

- 主进程 `android.app.SharedPreferencesImpl$EditorImpl#putBoolean`：监听 QQ 音乐翻译/罗马音开关变化。
- 播放服务进程 `android.media.session.MediaSession#setMetadata(MediaMetadata)`：从 `METADATA_KEY_MEDIA_ID` 获取 songId。
- `android.media.session.MediaSession#setPlaybackState(PlaybackState)`：同步播放状态。

数据路线：

1. `setMetadata` 发现 songId 变化。
2. 保存 `MediaMetadata`。
3. 查询磁盘缓存。
4. 缓存 miss 时使用 QQ QRC 下载器拉取歌词。
5. `parsedLyric.richLyricLines` 作为统一中间模型。
6. 转成 `lyric`、`rawLyric`、翻译 LRC。

迁移重点：

- 普通 QQ 的 songId 可直接来自 `METADATA_KEY_MEDIA_ID`。
- QRC 解析结果通常包含逐字和翻译，适合作高质量逐字适配。
- 即使原始 metadata 已经包含官方 `lyricInfo`，仍应启动 QRC 增强路线；QRC 结果就绪后覆盖官方简版输出。
- 官方 `lyricInfo` 与 QRC 结果同时存在时，以 QRC 生成的增强 `lyricInfo` 为准，尤其要保留逐字 `rawLyric` 给锁屏岛逐字高亮使用。

风险：

- QQ 音乐播放服务进程必须加入 scope，否则 metadata hook 不会执行。
- 翻译/罗马音开关不是第一阶段必须项，可先默认保留翻译。

## QQ 音乐 HD

推荐来源：`LyricProvider/qq-music-hd`

目标包名：`com.tencent.qqmusicpad`

主要 hook 点：

- `com.tencent.qqmusic.qplayer.core.player.controller.RemoteControlManager`：hook 参数为 `SongInfo` 和 `IMediaMetaDataInterface` 的方法，从 `SongInfo#getSongId()` 抽真实 songId。
- `android.media.session.MediaSession#setMetadata(MediaMetadata)`：metadata 到达时用 pending songId 触发歌词下载。
- `android.media.session.MediaSession#setPlaybackState(PlaybackState)`：同步播放状态。
- `com.tencent.mmkv.MMKV#putInt("KEY_OPEN_TRANSLATION", value)`：监听翻译开关。

数据路线：

1. RemoteControlManager 先记录真实 songId。
2. `setMetadata` 到达后绑定标题、歌手、时长。
3. 查本地缓存。
4. 缓存 miss 时复用 QRC 下载器。
5. 转成 `lyricInfo`。

迁移重点：

- QQ HD 不能简单复用普通 QQ 的 `METADATA_KEY_MEDIA_ID` 路线。
- 必须拆成单独 adapter，先拿 `pendingSongId`，再处理 metadata。

风险：

- RemoteControlManager 方法没有固定名称，需要按参数类型或字符串特征定位。
- pending songId 和 metadata 之间要做时序保护，避免串歌。

## 酷狗音乐 / 酷狗概念版

推荐来源：`LyricProvider/kugou-music`

目标包名：

- `com.kugou.android`
- `com.kugou.android.lite`

前置条件：

- 项目 README 明确说明：需要在酷狗音乐/概念版 App 内开启车载歌词模式。
- 这是适配成立的关键条件。未开启车载歌词模式时，酷狗可能不会加载或输出可 hook 的歌词文件，`LyricManager` 文件路径 hook 可能不触发。

主要 hook 点：

- 目标进程：`processName.endsWith(":support")` 或 `processName.endsWith(".support")`。
- `com.kugou.framework.lyric.LyricManager` 中包含字符串 `"file is not krc or lyc or txt file"` 的歌词加载方法。
- 该方法参数形态：`String path, boolean ...`。
- `android.media.session.MediaSession#setMetadata(MediaMetadata)`：保存标题、歌手、专辑、时长。
- `android.media.session.MediaSession#setPlaybackState(PlaybackState)`：同步播放状态。

数据路线：

1. 用户在酷狗内开启车载歌词模式。
2. 酷狗播放时加载 KRC/LRC 歌词文件。
3. hook `LyricManager` 拿到歌词文件路径。
4. 根据扩展名解析：
   - `.krc`：`KrcDecryptor.decrypt` + `KrcParser.parse`
   - `.lrc`：`LrcParser.parse`
5. 用当前 metadata 生成 track key。
6. 转成 `lyricInfo`。

迁移重点：

- 酷狗和概念版可共用同一个 adapter，差异主要是包名和进程。
- 保留 KRC 解密和 parser。
- track key 当前参考实现是 `title-artist-album-duration` 的 hash，没有真实 songId 稳定；本项目可用 `TrackIdentity` 再做一次保护。

风险：

- 车载歌词模式未开启时，不要误判为适配失败，要在文档和日志中明确提示。
- support 进程必须加入 scope。
- 本地歌词加载晚于 metadata，需缓存两边并做当前曲目校验。

建议日志：

```text
Kugou adapter requires in-app car lyric mode
Hooked Kugou LyricManager load method
Kugou lyric file loaded path=...
Parsed Kugou KRC lines=...
Skip Kugou lyric because metadata is missing
```

## Poweramp

推荐来源：`LyricProvider/poweramp-music`

目标包名：`com.maxmpz.audioplayer`

主要 hook/入口：

- 广播：`com.maxmpz.audioplayer.TRACK_CHANGED`
- extras 字段：
  - `id`
  - `title`
  - `artist`
  - `album`
  - `durMs`
  - `path`
- `android.media.session.MediaSession#setPlaybackState(PlaybackState)`：同步播放状态。

数据路线：

1. 注册 Poweramp `TRACK_CHANGED` 广播。
2. 从 extras 保存曲目信息和文件路径。
3. 将 Poweramp 路径转换为 SAF URI。
4. 使用 TagLib 读取音频标签里的 `LYRICS` 字段。
5. 用增强 LRC parser 按时长解析。
6. 本地标签无歌词时，可选在线搜索：
   - 中文环境优先 QQMusicProvider
   - 其他环境可用 LrcLibProvider

迁移重点：

- Poweramp 不必依赖歌词 UI 页面。
- 优先读本地音频标签歌词，这比 hook TextView 当前行更稳定。

风险：

- 读取 SAF URI 需要确认权限和路径格式。
- 在线搜索可能引入网络、版权和匹配误差，建议作为可选能力，第一阶段先支持本地内嵌歌词。

## 汽水音乐

推荐来源：`LyricProvider/qishui-music`

目标包名：`com.luna.music`

主要 hook 点：

- `android.media.session.MediaSession#setMetadata(MediaMetadata)`：从 `METADATA_KEY_MEDIA_ID` 获取 mediaId。
- `android.media.session.MediaSession#setPlaybackState(PlaybackState)`：同步播放状态，并在必要时重试加载缓存。

数据路线：

1. `setMetadata` 保存 mediaId、标题、歌手、时长。
2. 根据 mediaId 计算网络歌词缓存文件名：

```text
md5("/luna/track_v2/$id")
```

3. 在 `cacheDir/NetCacheLoader/**` 下递归寻找同名缓存文件。
4. 解析 `NetResponseCache`：
   - `lyric.type`
   - `lyric.content`
   - `lyric.lang_translations`
5. 按类型解析：
   - `krc`：汽水 KTV 格式 parser
   - `lrc`：普通 LRC parser
6. 翻译按系统语言 key 选择，再按时间就近合并。

迁移重点：

- 这是整首歌词路线，优先级高于蓝牙歌词当前行 hook。
- `KtvLyricParser` 的 `<offset,duration,...>` 逐字解析逻辑可直接迁移为 raw lyric 转换依据。

风险：

- 依赖 App 网络缓存存在；如果当前歌曲还没加载过歌词，缓存可能为空。
- `NetCacheLoader` 目录层级和缓存文件名规则要实机确认。

## 推荐实施顺序

1. QQ 音乐：songId 清晰，QRC 路线完整，适合先打通整套新增 adapter 流程。
2. 网易云音乐：YRC/LRC/翻译/罗马音都完整，适合验证多字段转换。
3. QQ 音乐 HD：复用 QRC，但验证 pending songId 与 metadata 时序。
4. 汽水音乐：缓存文件路线清晰，适合做本地缓存型 adapter。
5. 酷狗音乐/概念版：需要用户开启车载歌词模式，适合在前几条 adapter 框架稳定后接入。
6. Apple Music：歌词质量高但私有结构更复杂，适合单独做一轮字段验证。
7. Poweramp：先做本地内嵌歌词，在线搜索后置。

## 验证清单

- LSPosed scope 已包含目标包名和必要子进程。
- 目标 App 切歌时能看到 metadata 日志。
- 歌词来源日志能确认：
  - 网络下载完成
  - 缓存命中
  - 歌词文件路径命中
  - 音频标签命中
- `cacheTimedLyric` 只缓存当前曲目的歌词。
- `MediaSession#setMetadata` 注入后 SystemUI 侧可读到合法 `lyricInfo`。
- `lyric` 至少包含一条合法 LRC 时间标签。
- `rawLyric` 有逐字时间轴时，本模块自绘逐字高亮可启用。
- 切歌、暂停、无歌词、纯音乐、翻译关闭都不会沿用上一首歌。
