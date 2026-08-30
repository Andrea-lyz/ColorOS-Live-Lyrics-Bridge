# 大厂音乐 App Hook 工程线路

本文记录 `ColorOS-Live-Lyrics-Bridge` 后续适配 Apple Music、网易云音乐/荣耀版、QQ 音乐、酷狗音乐/概念版、Poweramp、汽水音乐时的工程路线。QQ 音乐 HD 不在 4.0 适配范围。

目标不是引入另一个歌词发布总线，而是在目标播放器进程内拿到整首时间轴歌词，复用本项目现有 `PlayerAdapter` 管线，最终注入 OPlus 可消费的 `MediaSession` `lyricInfo`。

## 第一批适配范围

第一批只做四个目标：

- QQ 音乐
- 网易云音乐
- Apple Music
- Poweramp 本地歌词

第一批能力边界：

- 核心目标是拿到整首歌词并覆盖 App 官方简版 `lyricInfo`。
- 有逐字时间轴时保留到 `rawLyric`；没有逐字时至少输出稳定逐行 LRC。
- QQ 音乐、网易云音乐、Apple Music 第一批都不输出罗马音歌词；即使上游数据包含罗马音，也只读取/忽略，不进入 `lyricInfo`。
- 暂不适配背景人声、对唱/多角色格式歌词。这些形态对锁屏歌词展示过重，容易拉高解析和 UI 复杂度。
- 日语歌词按当前解析器的初步适配能力处理，不额外接罗马音 lane。
- Poweramp 第一批只做本地：读取音频标签或本地内嵌歌词，不做在线搜索。

当前推进状态：

- 已新增第一批 `PlayerAdapter` 注册和 scope：QQ 音乐、网易云音乐、Apple Music、Poweramp。
- 已新增共享 `MediaSession` 曲目观测 adapter 基类，检测到官方简版 `lyricInfo` 时继续安排增强歌词查询。
- 已新增 QQ 音乐进程内歌词对象主链路：DexKit 定位 `MediaSessionUpdateController.h(builder, SongInfo, lyric)` -> 读取 QQ 已匹配/解密/解析好的 `com.lyricengine.base.k` 主逐字对象；同时 hook `QRCDesDecrypt.doDecryptionLyric(String)` 捕获 QQ App 自己解密出的主 QRC/翻译/罗马音候选，只合并可用内部翻译 -> 逐字 `rawLyric` + 逐行 `lyric` -> 通过外部 handoff 覆盖官方简版 `lyricInfo`；旧 QRC HTTP 请求仅保留为内部 hook 未安装时的兜底。
- 网易云音乐官方现行与 Honor 3.5.20 已迁出 Bridge `NeteaseMusicAdapter`，共用 4.0 `:player-netease` 官方 `lyricInfo` 追加；网易云只 hook 主进程，Honor 以 `:play` 为静态主链并允许主进程回退。9.0.40 仍待构造切片。Apple Music 当前仍是内置安全骨架。

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

- 网易云 / Honor：DexKit 按 `lyricInfo` + void + `LyricInfo`/`MusicInfo` 定位官方写入方法，按 `lyric`/`songName`/`artist` 定位官方编码器；不要共用混淆名（网易云样本为 `jp0.t`，Honor 样本为 `ce0.p`）。9.0.40 偏好监听仍走旧 `:163-music`。
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
- QQ 音乐：4.0 `:player-qq` 用 DexKit 按 `lyricInfo` + `transLyric` 和三参数形态定位 seedling 写手，不要只依赖固定方法名 `h`。
- QQ 音乐 HD：不在 4.0 适配范围，不要为此准备 DexKit fallback 或新建模块。
- Apple Music：PlaybackItem 映射已用 DexKit（`METADATA_KEY_MEDIA_ID` + `METADATA_KEY_PLAYBACK_ENDPOINT_TYPE`）；`PlayerLyricsViewModel` 方法名仍直接解析，版本漂移后再补。
- 汽水音乐、Poweramp：第一阶段不需要 DexKit，固定入口已经足够清晰。

工程形态建议：

```text
HookPointResolver
  DirectResolver: className + methodName + params
  DexKitResolver: strings + params + returnType + package scope
  Cache: package/version/sourceDir fingerprint -> resolved method descriptor
```

这样 adapter 代码只关心“拿到哪个 Method”，不直接散落 DexKit 查询细节。

## Apple Music

4.0 来源：`ColorOS-Live-Lyrics-Providers/player-apple`
（`io.github.andrealtb.coloroslyrics.provider.apple`）

目标包名：`com.apple.android.music`（仅主进程）

主要 hook 点：

- 平台 `MediaSession#setMetadata`：权威切歌身份；同曲后到的 adamId 合并进当前代；pending `lyricInfo` 附着到同一次 host 写入。
- DexKit `PlaybackItem` 映射（`METADATA_KEY_MEDIA_ID` + `METADATA_KEY_PLAYBACK_ENDPOINT_TYPE`）：缓存 adamId，不得因空 adamId 跟随队列下一首。
- Provider 自有 `PlayerLyricsViewModel#loadLyrics(PlaybackItem)`：主线程调用；禁止 hitchhike 官方歌词页实例；禁止在该 VM 上预取下一首 TTML。
- `PlayerLyricsViewModel#buildTimeRangeToLyricsMap(SongInfoPtr)`：解析 JNI 歌词。`setTranslation(系统语言)` 后遍历；罗马音不得进翻译 lane。

数据路线：

1. session 标题/adamId 绑定 generation。title-only 已是权威身份。
2. 按 adamId 或 title/artist 命中缓存 `PlaybackItem` 后再 `loadLyrics`。
3. `buildTimeRangeToLyricsMap` 后 `AppleSongMapper` 过滤和声，合并无空格拉丁音节。
4. `NativeLyricInfoPublisher` 写入 `source=com.apple.android.music-v5`。https 封面 URI 即可叠加，不等待 Glide bitmap。忽略 Cast session。

迁移重点：

- 不引入 Lyricon / v4 广播 / ExoPlayer 进度轮询。
- 不改写宿主 `setPlaybackState`，不注入公开 `ACTION_TOGGLE_TRANSLATION`。
- 拉丁音节必须在 enhanced LRC 之前合并（`LatinSyllableSpanMerger`），不要改 Bridge ASCII 插空格。
- 真机收口见 `ColorOS-Live-Lyrics-Providers/docs/4.0/PHASE-4-APPLE-MIGRATION-REPORT.md`。

风险：

- Apple 私有类名和方法名可能随版本变化。
- `loadLyrics` 依赖 Apple 内部鉴权和缓存；失败时降级为无歌词，不要发明歌词。

## 网易云音乐 / 荣耀版

推荐来源：`ColorOS-Live-Lyrics-Providers/player-netease`（网易云 9.5.70 + Honor 3.5.20）
网易云 9.0.40 仍用 `ColorOS-Live-Lyrics-Providers/163-music` 构造路线。

目标包名：

- `com.netease.cloudmusic`（只 hook 主进程）
- `com.hihonor.cloudmusic`（允许主进程与 `com.hihonor.cloudmusic:play`，仅结构命中者发布）

主要 hook 点：

- 网易云目标进程仅主包名进程；Honor 允许主进程与 `:play`，用运行时 Hook 命中确定实际持有者。
- DexKit：网易云样本发现 `o0(LyricInfo, MusicInfo)` / `I(String,String,String)`；Honor 样本独立发现 `e0(LyricInfo, MusicInfo)` / `B(String,String,String)`。运行时只信结构结果。
- 框架 `Handler#dispatchMessage` after：按各自 handler 类名、`what=16`、`LyricInfo` payload 过滤，再调用唯一零参数 `MusicInfo` 访问器；不 hook 宿主 `handleMessage`，不扫 Handler 字段。
- `android.media.session.MediaSession#setMetadata(MediaMetadata)`：叠加修补后的 `lyricInfo`；空 Builder 拷贝。
- 不要 hook `setPlaybackState`。

数据路线：

1. `o0` 校验 `filterMusicId == lyricInfo.musicId`。
2. 从 `LyricData` 取 `yrc` / `lrc` / `yrcTranslateLyric` / `lrcTranslateLyric`。罗马音字段只读取存在性，不得写入翻译 lane。
3. 优先解析 `yrc` 生成逐字主歌词；没有 `yrc` 时退回 `lrc`。
4. 翻译按 QQ 同款双锚点 1:1 合并，消费 `//`。
5. `NeteaseLyricInfoPayloadEncoder` 以显式 `OFFICIAL_APPEND` 模式保留官方 `lyric` / `songName` / `artist`，追加 `rawLyric` / `translationLyric`（`source=netease-official-append`）。

版本判断：

- Honor 3.5.20 已静态确认官方 `lyricInfo`，进入同一原生追加模块，但使用独立进程/混淆 profile。
- 9.0.40 没有官方 `lyricInfo`，必须走构造，禁止套用追加逻辑。

迁移重点：

- 这是官方 `lyricInfo` 追加，不是另发一份 metadata，也不是 Bridge 内置 `NeteaseMusicAdapter`。
- 第一批不输出罗马音。
- 9.0.40 由同一 `player-netease` 的 `CONSTRUCTED` profile 直接构造原生
  `lyricInfo`；Bridge 不保留旧模块准入或广播 fallback。

风险：

- 9.5.70 MediaSession 在主进程；Honor 3.5.20 静态 PlayService 在 `:play`，同时保留主进程运行时回退。两个 host 的混淆 profile 不得互换。
- 网络下载失败时必须保留当前 metadata，但不注入旧歌词。

## QQ 音乐

推荐来源：`ColorOS-Live-Lyrics-Providers/player-qq`

目标包名：

- `com.tencent.qqmusic`
- 播放服务进程：`com.tencent.qqmusic:QQPlayerService`

主要 hook 点：

- 只 hook `:QQPlayerService`。不要 hook 主 UI 进程。不要改写 `setPlaybackState`。
- `RemoteLyricController#onLoadSuc(LyricLoadBean)`：原文 `c()`，翻译 `h()`。罗马音 `e()` 不得进入翻译 lane。
- DexKit 定位 seedling 写手：字符串 `lyricInfo` + `transLyric`，参数 `MediaMetadataCompat.Builder` / `SongInfo` / `com.lyricengine.base.k`。不要写死方法名 `h`。afterHook 把修补后的 JSON `putString` 回官方 Builder。
- 平台 `android.media.session.MediaSession#setMetadata(MediaMetadata)`：空 Builder 拷贝后叠加 `lyricInfo`；HARDWARE / 超 240px bitmap Canvas 重绘。歌词晚到时每代最多 replay 一次。

数据路线：

1. QQ 自己完成匹配、QRC 解密和 lyricengine 解析，并已经写入官方行级 `lyricInfo`（`transLyric` 硬编码为空，缺少 `rawLyric`）。
2. Provider 从 `SongInfo`（`H2`/`j3`/`V3`，旧名仅作回退）和 `com.lyricengine.base.k` 读逐字模型：行 `e`，每行 `a/b/c/g`，逐字 `a/b/c/d/e`。
3. QRC 逐字时间按整行选择绝对或相对轴（`QqQrcWordTimePolicy`），不得逐词平移。
4. `QqOfficialLyricInfoEncoder` 保留官方 `id` / `songId` / `lyricType` / `lyric`，追加 `rawLyric`、`translationLyric` / `transLyric`、`sessionGeneration`，`source=qqmusic-internal`。
5. 不发送 v4 广播，不挂载词幕。翻译按钮走 Bridge 5 槽收藏覆盖。

迁移重点：

- 这是官方 `lyricInfo` 追加，不是另发一份 metadata，也不是 Bridge 内置 `QqMusicAdapter`。
- 样本为 QQ 音乐 20.7.5.8 / versionCode 7308。主 UI 进程不要装 hook。
- 真机收口 2026-08-27（PJZ110，`lyrics-log-20260827-083716.txt`）：逐字 + 翻译 + 5 槽收藏覆盖。Love Story 翻译走 Provider 双锚点；副歌 alias 与翻译 intro hold、句末逐字视觉封顶在 Bridge。

风险：

- LSPosed scope 必须包含 `com.tencent.qqmusic`，且实际 hook 进程是 `:QQPlayerService`。
- `SongInfo` / lyricengine 字段名随版本变化；DexKit 只保证 seedling 写手定位，字段仍要按当前样本反射。

## QQ 音乐 HD

QQ 音乐 HD（`com.tencent.qqmusicpad`）不在 4.0 适配范围。4.0 仓库已删除
`:qq-music-hd`，不要新建 `player-qqhd`，也不要把 HD 加入
`PlayerSystemUiPolicy` 或翻译设置。

## 酷狗音乐 / 酷狗概念版

推荐来源：`ColorOS-Live-Lyrics-Providers/player-kugou`

目标包名：

- `com.kugou.android`
- `com.kugou.android.lite`

触发/排查建议：

- 当前 Provider 在播放器 support 进程内 hook `LyricManager` 的歌词文件加载方法，并从 MediaSession 同步元数据和播放状态。
- 车载歌词模式不再作为硬前置条件。若日志长时间看不到歌词文件加载，可尝试在酷狗音乐/概念版 App 内开启车载歌词模式辅助触发。

主要 hook 点：

- 目标进程：`processName.endsWith(":support")` 或 `processName.endsWith(".support")`。
- `com.kugou.framework.lyric.LyricManager` 中包含字符串 `"file is not krc or lyc or txt file"` 的歌词加载方法。
- 该方法参数形态：`String path, boolean ...`。
- `android.media.session.MediaSession#setMetadata(MediaMetadata)`：保存标题、歌手、专辑、时长。
- `android.media.session.MediaSession#setPlaybackState(PlaybackState)`：同步播放状态。

数据路线：

1. 酷狗播放时加载 KRC/LYC/LRC/TXT 歌词文件。
2. hook `LyricManager` 拿到歌词文件路径。
3. 根据 MediaSession metadata 生成当前曲目身份和 track generation。
4. 根据扩展名解析：
   - `.krc`：`KrcDecryptor.decrypt` + `KrcParser.parse`
   - `.lyc`：优先按 KRC 解密解析，失败后按 LRC 文本回退
   - `.lrc` / `.txt`：按 LRC 文本解析
5. 同步 Lyricon Provider 内部输出。
6. 转成 Bridge 外部歌词广播，发送给 `com.android.systemui`。

迁移重点：

- 酷狗和概念版可共用同一个 adapter，差异主要是包名和进程。
- 保留 KRC 解密和 parser，同时增加 LYC/TXT 回退。
- 优先使用 MediaSession 的 `mediaId` / `mediaUri` 做曲目身份；没有时再回退到 `title-artist-album-duration`。

风险：

- 歌词文件加载依赖酷狗内部 `LyricManager`；若完全没有路径日志，可提示用户尝试开启车载歌词模式排查触发链路。
- support 进程必须加入 scope。
- 本地歌词加载晚于 metadata，需缓存两边并做当前曲目校验。

建议日志：

```text
Hooked Kugou LyricManager load method
Kugou lyric file loaded path=...
Parsed Kugou lyric file ext=krc lines=...
Sent KuGou bridge payload source=...
Skip Kugou lyric because metadata is missing
```

## Poweramp

推荐来源：`ColorOS-Live-Lyrics-Providers/player-poweramp`

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
6. 曲目切换时发送带 generation 的 `trackChanged`，歌词准备完成后发送 `lyricReady`，并复用同一份歌词 payload 注入 `MediaSession` 的 `lyricInfo`。
7. 播放状态只沿用 Poweramp 原生 `MediaSession`，Provider 不再额外广播同一份进度，避免拖动时重复驱动歌词 Recycler 定位。
8. 本地标签无歌词时，按用户设置进行在线搜索：
   - 中文环境优先 QQMusicProvider
   - 其他环境可用 LrcLibProvider
9. 对 `[行时间]单词 [单词时间]...` 形式的本地逐字标签，Provider 仅在 Bridge 输出侧转换成 `<单词时间>` enhanced LRC，并生成不含行内时间戳的官方逐行歌词，避免 Recycler adapter 行号与逐字模型错位。

迁移重点：

- Poweramp 不必依赖歌词 UI 页面。
- 优先读本地音频标签歌词，这比 hook TextView 当前行更稳定。
- 不接歌词 UI 当前行；本地歌词优先，在线搜索仅在用户启用时使用。
- 内部反射 probe 只在显式开启 Poweramp debug tag 后安装，避免发布态为纯诊断增加 hook 和反射开销。

风险：

- 读取 SAF URI 需要确认权限和路径格式。
- 在线搜索可能引入网络、版权和匹配误差，建议作为可选能力，第一阶段先支持本地内嵌歌词。

## 汽水音乐

4.0 来源：`ColorOS-Live-Lyrics-Providers/player-qishui`

目标包名：`com.luna.music`

主要 hook 点：

- `android.media.session.MediaSession#setMetadata(MediaMetadata)`：从 `METADATA_KEY_MEDIA_ID` 获取 mediaId。
- `com.luna.biz.playing.player.remote.control.CoreRemoteControl#update`：读取当前
  `IPlayable` 已加载的完整 `TrackLyric`。
- `android.media.session.MediaSession#setPlaybackState(PlaybackState)`：保留宿主状态并注入公开翻译 CustomAction；负缓存到期后可触发下一轮有界回退。

数据路线：

1. `setMetadata` 绑定稳定 mediaId、标题、歌手、时长与 generation。
2. `CoreRemoteControl#update` 返回后优先复用宿主 `TrackLyric` 的原文、逐字和
   `lang_translations`；Provider 不自行发起歌词网络请求。
3. 仅当内部模型暂未就绪时，根据 mediaId 计算网络歌词缓存文件名：

```text
md5("/luna/track_v2/$id")
```

4. 在 `cacheDir/NetCacheLoader/**` 下递归寻找同名缓存文件，并以
   `recent_played_*.db` / `history_*.db` 为最后回退。
5. 解析 `NetResponseCache`：
   - `lyric.type`
   - `lyric.content`
   - `lyric.lang_translations`
6. 按类型解析：
   - `krc`：汽水 KTV 格式 parser
   - `lrc`：普通 LRC parser
7. 翻译按系统语言 key 选择，再按时间就近合并；罗马音/拼音/发音 lane 永不作为翻译。
8. generation/token 校验通过后构造 `source=com.luna.music-v5` 的标准
   `lyricInfo`，保留宿主 metadata 并写回主 `MediaSession`。

迁移重点：

- 这是整首内部歌词路线，不使用蓝牙歌词当前行作为歌曲身份或歌词来源。
- `QishuiKtvLyricParser` 的 `<offset,duration,...>` 转为绝对毫秒逐字时间。
- 真机等价验证已完成，旧 `lyricprovider/qishui-music` source 与词幕 module 已删除；
  v5 运行链不发送 v4。

风险：

- 20.7.0 的运行时 getter 是 `RemoteControlContext#getA`；不同版本必须重新确认
  `CoreRemoteControl` 和 `TrackLyric` 结构。
- 内部 `TrackLyric` 就绪时机、NetCache/SQLite 回退时延与 LSPosed 默认配置仍需真机确认。

## 推荐实施顺序

第一批：

1. QQ 音乐：4.0 官方 `lyricInfo` 追加（`:player-qq`），只 hook `:QQPlayerService`。
2. 网易云 9.5.70 / Honor 3.5.20：4.0 官方 `lyricInfo` 追加（`:player-netease`）；网易云只 hook 主进程，Honor 允许主进程/`:play` 并只由结构命中者发布。9.0.40 不进入追加路线。
3. Apple Music：歌词质量高，但私有结构复杂；第一批只保留主歌词、逐字和可用翻译，裁剪背景人声、对唱格式、罗马音。
4. Poweramp：只做本地内嵌歌词，在线搜索后置。

后续批次：

- QQ 音乐 HD：不在 4.0 适配范围，不要进入后续批次。
- 汽水音乐：缓存文件路线清晰，适合做本地缓存型 adapter。
- 酷狗音乐/概念版：已接入 `LyricManager` 歌词文件加载和 MediaSession 同步；若不触发可建议用户尝试开启车载歌词模式排查。

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
