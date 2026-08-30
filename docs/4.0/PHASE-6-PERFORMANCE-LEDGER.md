# Bridge 4.0 Phase 6 性能与巨型方法治理台账

状态：slice 1-6 全部完成且用户设备冒烟通过；Phase 6 主体收尾。剩余仅“后续切片”中按 `PERF_SAMPLE` 数据决定的可选项（progress/glow painter 与内嵌 renderer 是否外迁）。

## 1. 本阶段边界

- 保持 SystemUI 原生 `lyricInfo`、Recycler ownership、AOD 与 track handoff 时序不变。
- 不通过打开 `DRAW_FRAME_REUSE_ENABLED` 换取表面性能；旧缓存只按 `TextView` 身份，不能区分交叉淡入期间的 Recycler/surface。
- 性能日志仍由独立 `lockscreen_lyrics_debug` 的 `performance` area 控制。默认关闭；关闭时不读取纳秒时钟、不生成逐帧字符串。
- 每刀先建立可比较的静态/采样证据，再迁移巨型方法；不在一次提交中同时改选择算法、动画参数和布局时序。

## 2. slice 1（2026-08-29）

### 2.1 聚合性能采样

新增 `diagnostics/BridgePerformanceSampler`，固定采样项：

1. `text-view-draw`
2. `frame-resolve`
3. `renderer-draw`
4. `active-refresh`
5. `model-parse`

性能 area 打开后按 5 秒窗口汇总 `count`、`avgUs`、`maxUs`，使用
`area=performance event=PERF_SAMPLE` 一次性输出。关闭时 `begin()` 只检查 area gate，
返回 no-sample 标记；`end()` 不读取时钟、不加锁、不分配集合。

采样 wrapper 与原实现分开：`onTextViewOnDraw`、`findOfficialLyricDrawFrame`、
`refreshActiveLyricTextView` 现在只负责计时并委托到具名实现，避免把采样异常处理继续
塞入原巨型方法，也为 slice 2 的 coordinator/resolver 迁移留下稳定边界。

### 2.2 全局 View hook 快速路径

- `TextView#setText` 不再每次删除 `NO_LYRICS_RECYCLER_MATCH`。非歌词 TextView 的父链负缓存保留到下一次 attach；真实 reparent 仍由 `onViewAttachedToWindow` 清除。
- `View#onAttachedToWindow`、`onDetachedFromWindow`、`setVisibility` 统一使用已有的 Recycler class cache，不再对每次调用重复遍历 class hierarchy。

### 2.3 Recycler 私有字段缓存

新增 `systemui/lyrics/LyricsRecyclerFieldAccessor`：

- 按弱引用 Class key 缓存官方当前索引字段 `n`；
- 同时缓存 missing 结果；
- 保持字段类型约束为 `int` / `Integer`；
- 替换主模块中所有 `readIntField(recycler, "n", -1)`，避免热路径反复
  `getDeclaredField` / `setAccessible` / superclass walk。
- `findOfficialLyricDrawFrame` 在一次绘制内只取得一次 row match、Recycler、item、
  adapter position 和 official index，并把同一 official-index snapshot 传给 active-line、
  duplicate-slot 与 scale-index 决策，避免同一帧重复查父链/反射且防止读取中途漂移。

通用反射 helper 和其他字段保持不变，后续按采样结果决定是否扩展 accessor。

## 3. slice 2（2026-08-29）

### 3.1 纯 frame selection policy

新增 `render/OfficialLyricFrameResolver`：

- 只接收 model、normalized text、adapter/official index、indexed/active line、position
  与 remembered-active hint，不读取 View、Recycler、反射字段或全局模块状态；
- 统一 indexed、near-index、duplicate timestamp、active、translation-only 与
  remembered-active 选择顺序；
- `findLyricTextMatch` 与正式 draw frame 共用同一 policy，删除两份重复选择流程；
- 调用方持有两个可复用 `Selection`，没有给逐帧路径新增 selection 对象分配；
- strict official slot、duplicate adapter suppression、日志和调度仍留组合根，保持现有
  Recycler ownership 边界。

主模块变化：`findLyricTextMatch` 73 → 39 行，具名
`resolveOfficialLyricDrawFrame` 约 220 → 130 行；10 行计时 wrapper 不承载选择逻辑。

### 3.2 draw 路径不再修改歌词模型

删除 frame resolver 中按相邻重复行补写 `line.translation` 的逻辑，新增
`WordLyricModel.propagateNearbyTranslations(radius)`，在 raw/display/translation payload
全部合并完成后、发布 model 前统一执行：

- display alias 优先、main text 回退；
- 只从指定半径内已有 translation 的同 renderable text 行复制；
- 复制后使 renderable-text index 失效并按需重建；
- draw 只读 `WordLine`，不再因为某一行恰好首次绘制才改变 translationCount/布局。

frame cache 本 slice 仍保持关闭。若后续恢复，键必须至少包含 model、recycler、
bind epoch、content hash、surface epoch；重复歌词还必须包含 adapter position/line index。

## 4. slice 3（2026-08-29）

### 4.1 renderer layout engine

新增 `render/LyricDrawLayoutEngine`，从内嵌 renderer 迁出：

- 空白裁剪、single-line、普通换行、未翻译文本平衡分行；
- CJK 标点边界仍委托原 `LyricLineBreakPolicy`，未改规则；
- `LyricDrawLine` pool 与 `WordLine` width/text-size/typeface/config cache；
- 对外只暴露只读 line view，renderer 保持唯一写 owner；
- width measurer 在 renderer 初始化时创建一次，cache hit 不调用 `measureText`。

`LockscreenLyricsModule` 中 `buildDrawLines` 从约 92 行降为 16 行，相关 helper/pool
整体删除；主模块减少约 160 行。`LyricLineBreakPolicy` 仅提升可见性供 render 子包复用，
算法未改。

### 4.2 逐帧分配清理

- `MainLineWindow` 改为 renderer 单实例可复用 mutable snapshot；每次 draw 不再 new。
- translation transient-miss cache 从 `time + "|" + normalizedText` 临时字符串改为
  `long timeMillis + String normalizedText` 两字段比较。
- configured typeface 按 official Typeface identity + font weight 缓存，避免 draw 与
  geometry pass 重复调用 `Typeface.create`。

本 slice 不改 draw order、Canvas clip/save、glow bitmap、slot-height、AOD 低帧率或
translation animation 参数；`drawLyricGroup` 的 paint pass 仍留内嵌 renderer，由 slice 4 分责。

## 5. slice 4（2026-08-29）

### 5.1 renderer palette cache

新增 immutable `LyricUiPalette`，在 renderer config snapshot 改变时一次性解析并缓存：

- inactive / focused inactive / active / played；
- glow shadow / fill；
- active feather colors。

原热路径 `applyFade`、translation pass、glow raster 和 feather shader 不再反复执行
hex `substring + Integer.parseInt`，feather color array 也只随 config 创建。动态 focus/AOD
颜色仍使用相同整数 blend 公式。

### 5.2 drawLyricGroup 分责

原 256 行 `drawLyricGroup` 拆为：

- `drawLyricGroup`（45）：Canvas ownership 与 pass 顺序；
- `prepareLyricGroupDraw`（123）：layout/progress snapshot；
- `drawMainLyricPass`（128）：passive pan / animated window / stable window；
- `drawTranslationPass`（41）：translation baseline、颜色与绘制。

新增单实例可复用 `LyricGroupDrawContext` 传递本帧标量和引用，不给逐帧路径新增对象。
原先的 `canvas.save → main → applyFade → translation → restore` 顺序保持不变。

### 5.3 官方透明度/边缘渐隐对齐（slice 4.1）

- 当前行未高亮底色固定为 50%（alpha `0x80`），不再按普通非活动行透明度加 15 个百分点。
- 普通非活动行透明度设置、默认值 44%、预设和用户已保存值全部不改。
- `LyricsRecyclerView` attach/configure 时幂等启用 vertical fading edge，并重申官方
  `51.9dp` 长度；渐隐仍由 RecyclerView parent 统一合成，不在每个 TextView 内重复绘制。
- 不修改当前高亮 100%、glow、缩放、模糊、AOD 或动画参数。

### 5.4 翻译行亮度层级（slice 4.2）

- 实时行翻译基础亮度固定 60%（alpha `0x99`），高于当前行未高亮底色 50%，
  但低于实时主歌词 100%。
- 开启“翻译进度”时，已揭示翻译最高 80%（alpha `0xCC`），仍不超过实时主歌词。
- 所有非实时翻译直接复用普通非实时歌词颜色；默认 44%，并随用户的“非实时歌词透明度”
  设置同步变化，不另建配置项。
- Settings Activity 与自绘预览同步使用 active/inactive translation 两档颜色。
- 不修改翻译字号、位置、渐显时长、marquee、glow、AOD 或正文颜色。

## 6. slice 5：model pipeline（2026-08-29）

### 6.1 native lyric payload assembler

- 新增根包 `NativeLyricModelAssembler`：native lyricInfo 的完整解析/alias/翻译合并/传播
  流水线从 `LockscreenLyricsModule` 迁出（约 1,580 行），纯 Java、无 Android 依赖，
  可直接跑 JUnit。配套 `LyricParseTraceSink`（verbose trace 与 Lyrics Core 失败回调，
  模块侧沿用原 `LockscreenLyricsParse` tag 与 `error(...)` 文案）和
  `LyricModelTraceSupport`（共享文本/trace 工具）。
- `parseInlineWordLrc`（150 行）、`parseInlineTimedLyricLine`（92 行）、
  `applyOfficialDisplayTextAliases`、`mergeSupplementalTranslations`、
  `mergeSameTimestampLyricLines`、`toWordLine`、`normalizeTimedWordText`、
  `splitRawLyricLines` 及其全部辅助方法整体迁移；行为逐行保持，仅把
  `TextUtils.isEmpty` 换成纯 `isEmpty`。
- 同一 payload 只解析一次：displayLyric 的 `splitRawLyricLines` 只执行一次，
  结果同时供给 official alias 分组（`parseTimedTextGroups`）与 supplemental
  解析（`parseWordLyric`）；`containsTimedLrc` 每个字符串只算一次，消除
  evidence/alias/merge 三处重复正则扫描。
- 不异步化：`assemble` 在原锁内同步执行，model 发布与 `mainHandler` UI
  调度时序与之前完全一致。

### 6.2 时间/文本索引匹配

- 新增 `SupplementalTranslationIndex`（时间数组二分 + 归一化文本桶），替换
  supplemental 合并里每个候选两次全表扫描的 `SupplementalTranslationPolicy`
  调用；合并复杂度从 O(N*M*N) 降到 O(N log N + 窗口)。候选窗口用 `lowerBound`
  + 有序游走，保留原有“距离相同后到者胜”的选择顺序。差分 fuzz（400 个随机
  模型 × 每行 × 每候选文本）证明索引判定与原 policy 完全一致。
- `WordLyricModel.findLineByText` / `findLineByTranslation` 改为
  `nearestLineByPredicate` 外扩搜索：从二分插入点按 |time-position| 升序
  访问（同时间簇按索引升序、距离并列左侧优先），命中即返回。与旧线性扫描
  行为完全一致（400 × 30 随机查询差分证明），official alias、seedling hint 与
  `OfficialLyricFrameResolver` 逐帧文本查找全部受益。
- `findOfficialAliasLine` 内的 `findLineByTextOccurrence` 步骤被证明是死代码
  （只在 `findLineByText` 无匹配时执行，而二者使用同一 `matchesWordLineText`
  谓词，必然同样无匹配），予以删除；`findLineByTextOccurrence` 公共方法保留。

### 6.3 `cacheSystemUiLyricModelLocked` 拆分

- 114 行方法拆为三段：evidence 判定（`containsTimedLrc` 只算一次）→
  `nativeLyricModelAssembler.assemble`（仍由 `MODEL_PARSE` 性能采样包裹）→
  `publishWordLyricModel` / `clearWordLyricModelState`。
- 两处重复的模型重置块合并为 `clearWordLyricModelState(area, event, message)`，
  日志 area/event/message 与原实现逐字一致；发布路径保留
  `beginBrightLyricGeometryCommit`、slot integrity 日志与
  `preparePublishedLyricModelGeometry` 的 mainHandler post，时序不变。
- 模块只保留编排与日志：aliases / 两路 supplemental / propagation 计数由
  `AssemblyResult` 返回，日志文案保持原样。

## 7. slice 6：draw coordinator（2026-08-29）

### 7.1 `OfficialLyricDrawCoordinator`

- 173 行 `drawOfficialLyricTextView` 从 `LockscreenLyricsModule` 迁出；原 hook
  只剩资格门、参数/类型读取与委托（约 15 行），计时 wrapper
  （`TEXT_VIEW_DRAW`）不变。
- 迁移覆盖全部原有阶段：hook/控件资格、surface reactivation、handoff/fade
  抑制、geometry-commit 空帧、frame resolve（recent → resolve → recent
  fallback 顺序不变）、`RENDERER_DRAW` 采样的渲染委托、bound retry、
  active-line post 回调、handoff commit / late takeover、异常兜底。
- 模块状态与渲染器所有权仍留模块：coordinator 通过 `Host` 接口（27 个
  显式方法）访问，内嵌 `OfficialLyricTextRenderer` 不提前外迁，
  `RENDERER_DRAW` 计时包装留在模块 `drawWithOfficialRenderer`。
- `preferRecentFrame` 布尔表达式抽为纯函数 `shouldPreferRecentDrawFrame`；
  其余行为逐行保持，含全部注释、日志 reason 与返回值语义
  （`proceed()` 结果 vs `null` 吞帧）。
- `shortenForLog` 在 coordinator 内保留同逻辑私有副本（纯 8 行格式化），
  模块侧 16 处调用不受影响。

### 7.2 测试

- `OfficialLyricDrawCoordinatorTest`：`shouldHandleDraw` 门四态与
  `shouldPreferRecentDrawFrame` 全真值表。完整绘制路径依赖 SystemUI 实
  景视图，继续走设备冒烟。
- `BridgeArchitectureGuardTest` 增加根包断言。

## 8. 验证状态

- `compileDebugJavaWithJavac`：通过。
- `compileDebugUnitTestJavaWithJavac`：通过。
- `BridgePerformanceSamplerTest` + `LyricsRecyclerFieldAccessorTest`：直接 JUnit 运行，6 项通过。
- `OfficialLyricFrameResolverTest` + `WordLyricModelTest`：直接 JUnit 运行，39 项通过；
  合并 slice 1 聚焦测试共 45 项通过。
- 加入 `LyricLineBreakPolicyTest` + `LyricDrawLayoutEngineTest` 后，slice 1-3 聚焦回归
  共 56 项通过。
- 加入 `LyricUiPaletteTest` 与既有 `LyricUiColorsTest` 后，slice 1-4 聚焦回归
  共 61 项通过。
- 加入 50% focused base 与官方 fading-edge 常量断言后，聚焦回归共 67 项通过。
- 加入 active translation 60% / progress 80% / inactive 同步断言后，聚焦回归共 68 项通过。
- 加入 `SupplementalTranslationIndexTest`（4）、`NativeLyricModelAssemblerTest`（5）、
  `WordLyricModelNearestSearchTest`（2）后，slice 1-5 聚焦回归共 231 项通过
  （含 `BridgeArchitectureGuardTest`、`WordLyricModelTest`、
  `LyricParsingRegressionMatrixTest`、`OfficialLyricFrameResolverTest`、
   `LyricsCoreAdapterTest`、`BridgePerformanceSamplerTest` 等）。
- 加入 `OfficialLyricDrawCoordinatorTest`（2）后，slice 1-6 聚焦回归共 233 项通过。
- Gradle `testDebugUnitTest --tests ...`：当前 Gradle test worker 对新增与既有测试均报
  `ClassNotFoundException`，但对应 `.class` 已生成；判定为测试运行器/类路径问题，不是本 slice 编译失败。
- 默认输出目录的 `assembleDebug` 曾因 `app/build/outputs/apk/debug` 被外部进程占用而
  无法删除；未强杀用户进程或删除工作区文件。改用独立 build directory 后完整
  `assembleDebug` 通过，测试包复制到
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-phase6-slice1-debug.apk`。
- APK：8,444,174 bytes；SHA-256
  `1A34009E317032BEDE518FB37650D07EE5E4602CD18270E6AC524D0D81467A9A`；
  `apksigner verify` 通过（Android Debug certificate，v2 scheme）。
- slice 2 独立输出目录 `assembleDebug` 通过；测试包
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-phase6-slice2-debug.apk`，
  8,461,423 bytes，SHA-256
  `7EF5A27E5ED18915426B8A84F3BFE77827D1618E5D3682BCBB84AA223411F1F4`；
  `apksigner verify` 通过（Android Debug certificate，v2 scheme）。
- slice 3 独立输出目录 `assembleDebug` 通过；测试包
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-phase6-slice3-debug.apk`，
  8,463,632 bytes，SHA-256
  `EB459CECE5F056A57A064997DB291220C0510A4448C655CBFFFEC8DC8E893955`；
  `apksigner verify` 通过（Android Debug certificate，v2 scheme）。
- slice 4 独立输出目录 `assembleDebug` 通过；测试包
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-phase6-slice4-debug.apk`，
  8,463,632 bytes，SHA-256
  `C70FDE3F9FAFDD6D3A7ED85BE7661DD53697DEA556566DF1E5C3377FEDE1494D`；
  `apksigner verify` 通过（Android Debug certificate，v2 scheme）。
- slice 4.1 独立输出目录 `assembleDebug` 通过；测试包
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-phase6-slice4.1-debug.apk`，
  8,463,632 bytes，SHA-256
  `3EEE818B2BE64A50D76A3DBBCAF8A22C8D2ADA31AC74D8FE4F3113FA87AFF344`；
  `apksigner verify` 通过（Android Debug certificate，v2 scheme）。
- slice 4.2 独立输出目录 `assembleDebug` 通过；测试包
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-phase6-slice4.2-debug.apk`，
  8,463,632 bytes，SHA-256
   `016E1DE59CBAD129D7D89CDB481FDDBA42A0714C3606CC8BA1031565052ED058`；
   `apksigner verify` 通过（Android Debug certificate，v2 scheme）。
- slice 5 独立输出目录 `assembleDebug` 通过；测试包
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-phase6-slice5-debug.apk`，
  8,784,568 bytes，SHA-256
  `1928F8F15F4F24988028AE8441CCF15EF51CEA74B8312741CF5D251FCBEE50C8`；
  `apksigner verify` 通过（Android Debug certificate，v2 scheme），
  `zipalign -c -P 16 4` 通过。
- slice 5 体积说明：dex 内容增量只有 classes5/classes7 合计约 7.5KB，类清单 diff
  确认仅新增 `NativeLyricModelAssembler` / `SupplementalTranslationIndex` /
  `LyricModelTraceSupport` / `LyricParseTraceSink` 及嵌套类型，删除模块内
  `TimedLyricGroup` 与 2 个合成 lambda；相对 slice 4.2 的约 320KB 增幅来自
   `packageDebug` 重新执行的 zip 条目布局（slice 3-4.2 复用同一包输出、字节数相同），
   与代码内容无关。
- slice 6 独立输出目录 `assembleDebug` 通过；测试包
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-phase6-slice6-debug.apk`，
  8,787,379 bytes，SHA-256
  `C6572A7F4052D44023A169B42E04F71A67B685152BECD34ADA5A540D599A3CC8`；
  `apksigner verify` 通过（Android Debug certificate，v2 scheme）。
- 设备：slice 1/2/3/4 用户实测未见明显异常；slice 4.1/4.2 尚未验证。应重点观察当前行
  未高亮区域是否稳定为 50%，实时翻译是否为 60% 且非实时翻译与正文等亮，
  以及上下首尾行是否只出现一层 Recycler 渐隐。
- 设备：slice 5 尚未验证。应重点观察各播放器歌词显示/翻译/逐字时机与
  slice 4.2 一致（alias 映射、supplemental 翻译、model 发布时序均未变行为，
  只改变解析与匹配的组织方式）。
- 设备：2026-08-29 用户随 slice 5 包冒烟全部通过，覆盖 4.1/4.2 视觉对齐与
  slice 5 model pipeline 观察点，未见异常。
- 设备：slice 6 尚未验证。应重点观察：锁屏歌词自绘正常（无整行空白、无官方
  字闪烁）、切歌 handoff 与淡入、AOD 低帧率、翻译行与 active 行跟随；如打开
  debug performance area，`renderer-draw`/`text-view-draw` 采样应与 slice 5 相当。
- 设备：slice 6 于 2026-08-29 用户冒烟肉眼无异常
  （`logs/lyrics-log-20260829-142637.txt`）。日志证据：全程 0 条
  `draw-error` / `frame-miss-suppressed` / `model-null` / `hook-inactive` /
  `Failed to custom-draw` / level=ERROR；两条解析路径均实测——
  Forever & Always 走 lyrics-core（lines=59, officialAliasMismatches=0），
  The Story of Us 走 inline-lrc（lines=65, translations=57,
  officialAliasMismatches=0, duplicateObjects=0）；PERF_SAMPLE 显示
  frame-resolve avg 24-66µs、renderer-draw avg 197-563µs、
  model-parse 单曲一次约 26ms，无异常采样。日志中 `LockscreenLyricsParse`
  trace 的中文乱码为 logcat 抓取编码伪影，非解析问题。

## 9. 后续切片

### renderer 后续

- 后续根据 `PERF_SAMPLE` 决定是否继续外迁 progress/glow painter，
  再评估是否整体迁出内嵌 `OfficialLyricTextRenderer`。
- 保留现有 glow bitmap、FontMetrics 和 slot-height 缓存。

### slice 5：model pipeline

- 已完成，见第 6 节。
- 后续若 `PERF_SAMPLE model-parse` 仍偏高，再评估 displayLyric 组视图与
  supplemental 视图的进一步共享，以及歌词模型解析结果的按签名缓存。

### slice 6：draw coordinator

- 已完成，见第 7 节。
- 后续按 `PERF_SAMPLE` 决定是否继续外迁 progress/glow painter 与内嵌
  `OfficialLyricTextRenderer`（与“renderer 后续”合并评估）。
