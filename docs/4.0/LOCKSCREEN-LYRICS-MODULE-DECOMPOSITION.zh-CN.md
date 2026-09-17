# `LockscreenLyricsModule` 拆解与维护复杂度降低方案

## 1. 文档目的和分析基线

本文是对 `LockscreenLyricsModule` 的静态架构分析与拆解方案，不修改业务实现。目标是在保持当前核心行为的前提下，降低后续修复、版本适配和回归定位的成本。

分析对象：

```text
ColorOS-Live-Lyrics-Bridge/app/src/main/java/
  io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java
```

分析基线：

- Git commit：`e597c5b9e01c5b8c98090a0c1f138fcd4fc412f2`
- 文件 SHA-256：`80746F8FB9DAC69ECB29BFF98DC5E39FA8FB52AC4F39754D9F9A8ED2B4CDDCF5`
- 文件行数：15,735 行
- 源码中的方法/构造器签名约 666 个（包含内部类方法）
- 当前模块 scope 仍为 `system` 与 `com.android.systemui`
- `module.prop` 使用 libxposed API 102、`exceptionMode=protective`、`autoHotReload=false`

本文中的行号对应上述基线。后续改动会使行号移动，引用时应以方法名和职责区间为准。

## 2. 结论先行

这个类已经不是单一的“Xposed 入口”，而是同时承担了以下角色：

1. libxposed 生命周期入口和进程门禁；
2. `system_server` 与 SystemUI 私有方法的 Hook 安装器；
3. OPlus 媒体策略、AOD 支持和翻译 action 的兼容层；
4. `MediaSession` 播放状态订阅和播放位置时钟；
5. `lyricInfo` 解析、清理、generation/epoch 门禁和歌词模型发布器；
6. LyricsRecyclerView 的定位、对齐、几何修复和 adapter 崩溃保护器；
7. TextView 绑定、官方行匹配、绘制 frame 选择与重试调度器；
8. 自绘歌词接管、切歌 handoff、可见性恢复和 AOD 刷新调度器；
9. 屏幕超时 WakeLock 与用户活动 pulse 管理器；
10. 翻译按钮的发现、图标指纹识别、可见性和 presentation 管理器；
11. 设置广播、配置缓存、调试开关和结构化日志门面；
12. 约 3,500 行的 `OfficialLyricTextRenderer` 及其 glow、progress、char-lift、换行和动画状态。

因此，最有效的拆法不是把方法按行剪切到几个 Helper，而是按“运行时状态所有权”拆分。每个新组件必须同时带走：

- 它唯一负责的状态字段；
- 修改这些字段的回调；
- 该状态的失效、清理和 generation/epoch 规则；
- 与 Xposed Hook 的适配入口；
- 可独立验证的策略或测试。

推荐的最终形态是：入口类只保留生命周期分派、进程门禁、组件组装和少量跨组件事件转发；SystemUI 的高频回调由专门控制器处理，渲染器只接收明确的 immutable snapshot 和窄接口。

### 2.1 复杂度热点

按 Java AST 对当前基线扫描，入口类本身有 335 个字段、509 个顶层方法；连同内部类共有 704 个方法/构造器。最长的方法集中在少数几个职责混合点：

| 方法 | 基线行号 | 行数 | 混合的职责 |
| --- | ---: | ---: | --- |
| `OfficialLyricTextRenderer.drawSegment` | 14001-14160 | 160 | 文字布局、progress、char-lift、Canvas 状态和异常路径 |
| `runActiveLyricRefresh` | 8985-9127 | 143 | 播放位置、active 行、View 搜索、Recycler 刷新、日志 |
| `resolveSlotHeight` | 13234-13367 | 134 | 翻译行、换行、配置、字体 metrics、官方 slot 约束 |
| `handlePlayerTranslationSettingsChanged` | 2030-2160 | 131 | schema、SharedPreferences、renderer、View、MediaData rebind |
| `drawMainLyricPass` | 12863-12993 | 131 | 主行窗口、绘制颜色、progress、char-lift 和布局 |
| `resolveOfficialLyricDrawFrame` | 7308-7437 | 130 | TextView 匹配、Recycler 关系、缓存、时间和 fallback |
| `onSystemUiLoadLyricInBg` | 3136-3255 | 121 | metadata 读取、cleanup、模型发布、identity 和 `proceed` |
| `cacheSystemUiLyricModelLocked` | 10462-10577 | 116 | parse/assembly、epoch、provider snapshot、handoff 和 wake-lock evidence |

这些热点说明维护成本主要来自“一个回调同时跨越多个状态 owner”，而不是单纯的文件长度。优先拆解表中的 `onSystemUiLoadLyricInBg`、`runActiveLyricRefresh`、`resolveOfficialLyricDrawFrame` 和 translation settings handler，收益会高于先把纯工具方法搬到 `Utils`。

## 3. 当前类的职责地图

### 3.1 生命周期、Bootstrap 和 Hook 安装

位置：约 889-1313、2377-2485、6541-7240。

主要职责：

- `onModuleLoaded`、`onSystemServerStarting`、`onPackageReady`；
- SystemUI DexKit target 解析及 legacy fallback；
- System server 的 history whitelist、media blacklist Hook；
- AOD media support Hook；
- SystemUI lyric loader、TextView、View、ImageView、LyricsRecyclerView 和 adapter Hook；
- 动态插件 ClassLoader 到达后的 Hook generation 管理；
- 每个 Hook 的 ID、protective exception mode 和重复安装保护。

当前已有 `SystemUiRuntimeBootstrap`（`bootstrap/SystemUiRuntimeBootstrap.java`），说明 Bootstrap 边界已经被验证过。但真正的 Hook 安装逻辑仍通过大型 `Host` 回调回到入口类，后续应继续向专用 Installer 下沉。

### 3.2 System server / OPlus 媒体策略

位置：约 982-1313、1139-1200。

包含：

- history whitelist 适配；
- media blacklist bypass；
- AOD media support；
- OPlus RUS whitelist、lyric entrance、package action rule；
- 当前播放器包判断和模块管理播放器判断。

这些方法的共同特点是：只处理私有 OPlus 目标解析、返回值替换和失败降级，不需要知道歌词模型、TextView 或 Recycler 状态。它们是最适合优先迁出的边界。`onOplusMediaUpdatePkgActionsRule` 同时会读取翻译 action 的 rule-0 package 集合，因此拆分时应通过 `TranslationActionController` 提供一个只读 package snapshot，不能让 OPlus policy 直接访问翻译控制器的 Map。

### 3.3 翻译 action：媒体层、偏好层和 View 层混在一起

媒体 action 与偏好：约 1314-1651、1950-2240。

View 发现与 presentation：约 4466-5163。

状态字段主要在约 351-401、522-555：

- action Hook 是否已安装；
- rule-0 package、pending/refreshed package 集合；
- provider 声明的 package；
- 每个 package 的 enabled 状态和已加载偏好集合；
- 最近媒体按钮、翻译 View、图标 fingerprint、bitmap/drawable 匹配缓存；
- rebind generation 和 presentation in-flight 防重入。

当前 `TranslationToggleMediaActionBinder` 已经承担了 action drawable 生成，但 action 生命周期仍由入口类控制。建议最终分为 `TranslationActionController`（媒体 action 和偏好）与 `TranslationActionViewTracker`（View 发现、指纹和 presentation），二者通过一个小的 `TranslationActionState` 连接。

### 3.4 设置、配置和调试

位置：约 1652-2030、2030-2368、11748-11925。

包含：

- `LyricUiConfig` 与 `LyricContentCleanupConfig` 的读取、缓存和广播应用；
- 翻译偏好清理与设置 apply result；
- 当前歌词重放和 cleanup 后模型重建；
- Bridge debug 配置读取、应用、结果广播和 Logcat/framework 输出；
- renderer、screen timeout 和 translation action 的联动刷新。

这里的问题不是缺少 Repository，而是入口类直接把配置变更写进了多个运行时状态。配置层应只产生 `RuntimeConfigSnapshot`，具体组件决定如何应用；配置广播不应直接操作 renderer 私有字段。

### 3.5 SystemUI 歌词加载和模型发布

位置：约 3136-3683、10462-10647、10952-11002。

这是一个完整的“输入到模型”流水线：

```text
SystemUI lyric loader Hook
  -> metadata/title/artist/package 提取
  -> KuWo identity 修正
  -> lyricInfo parse/cleanup/official normalization
  -> publication epoch 检查
  -> NativeLyricModelAssembler
  -> current model / track key / provider snapshot
  -> geometry commit、translation rebind、screen timeout evidence
```

已经存在 `SystemUiLyricPublicationGate` 与 `NativeLyricModelAssembler`，但 `onSystemUiLoadLyricInBg` 仍同时负责输入归一化、模型提交和运行时副作用。应拆为：

- `SystemUiLyricLoadHook`：只读取 Hook 参数、调用 pipeline、一次性 `proceed`；
- `LyricPublicationPipeline`：parse、cleanup、normalization、assembly 和 stale epoch 丢弃；
- `LyricModelStore`：持有当前模型、signature、track key、provider payload 和 cleanup snapshot；
- `LyricModelCommitCoordinator`：把模型发布转换为 geometry commit、handoff、translation rebind 和 wake-lock evidence 事件。

`SystemUiLyricPublicationGate` 必须继续是提交门禁，不能被普通缓存替代。旧 parse 必须在提交前再次检查 epoch；当前代码约 10500 行的 `canCommit(loadEpoch)` 是不可丢失的行为约束。

### 3.6 KuWo / Artwork / 插件媒体模型兼容

位置：约 2486-2955、6541-6987。

包含：

- KuWo artwork snapshot、seedling artwork、car lyric metadata；
- OPlus plugin class loader constructor Hook；
- KuWo plugin media model 读取、歌词支持位修复、album art 修复；
- 反射字段扫描、插件 generation 和旧 Hook handle unhook。

这部分已经有 `players/kuwo/KuWoSystemUiRuntime`、`OplusPluginDexKitAdapter` 等支撑类，但入口仍持有大量插件生命周期状态。建议拆成 `KuWoSystemUiCompatibility` 和 `PluginHookGenerationManager`。后者只负责 ClassLoader、HookHandle、generation 和清理，不要知道歌词绘制细节。

### 3.7 播放状态、MediaController 和播放时钟

位置：约 3684-4265。

包含：

- seedling media bundle 读取；
- `MediaController` discovery、callback、session destroyed；
- 播放状态、speed、position 的快照；
- track reset guard、stale position 丢弃和 playback jump 观察；
- active lyric line 更新及 Recycler/renderer 刷新。

建议拆为 `SystemUiPlaybackController`：

- 持有 `PlaybackControllerBinding`；
- 负责旧 callback 注销和新 callback epoch；
- 输出 `PlaybackSnapshot`；
- 只发出 `PlaybackPositionChanged`、`PlaybackSessionChanged`、`PlaybackJumpObserved` 事件。

播放状态不应直接写入 renderer 或 Recycler 字段。这样可以单测“暂停/播放/切歌/过期 position”而不需要构造 Android View。

### 3.8 LyricsRecyclerView 与官方几何所有权

位置：约 5208-6458、6987-7240、8094-8586。

这是除 renderer 外最复杂的子系统，包含：

- `setCurrentLyric` Hook 观察和 snapshot position 特殊处理；
- Recycler/adapter 弱引用注册；
- surface render pass、epoch、readiness state；
- model geometry commit 与亮屏/AOD 顺序；
- native line-timed Recycler 的滞后观测和自适应跟随；
- 官方 index、adapter position、item height、scroll offset；
- line spacing、scale pivot、slot height 和 visible block height；
- adapter notify crash guard；
- Recycler private method 反射和缓存。

建议拆成三个协作组件，而不是一个更大的 Recycler Helper：

1. `LyricsRecyclerHookInstaller`：只寻找目标方法、安装 Hook、处理插件 ClassLoader 和 Hook ID；
2. `LyricsRecyclerController`：处理 setCurrentLyric、prime、scroll、align、native follow；
3. `LyricSurfaceReadinessController`：处理 render pass epoch、ready frames、pending draw 和 visible session generation。

`LyricsRecyclerController` 必须保留 SystemUI 对 Recycler positioning 的所有权。当前代码约 5249 行明确说明不能改写 vendor 的 boolean/position transaction；拆分时只能观察和在已有策略允许时调用官方方法，不能把滚动逻辑迁移成模块自有动画。

### 3.9 TextView 绑定、DrawFrame 选择和 active refresh

位置：约 4266-4668、7246-8093、8587-9610。

包含：

- TextView `onDraw`、`setText`、attach/detach/visibility/contentDescription；
- lyric text normalization 和 TextView-to-WordLine match；
- 官方 DrawFrame 解析、近期 frame 缓存和 compatibility 检查；
- bound frame stamp、bind epoch、retry count；
- active text view/root/recycler 注册；
- active refresh cadence、display-rate frame budget；
- surface reactivation watchdog、provisional draw 和可见 View 搜索。

建议拆成：

- `OfficialLyricTextHookInstaller`；
- `OfficialLyricBindingController`：TextView 匹配、bind epoch、frame retry、弱缓存；
- `ActiveLyricRefreshScheduler`：active line、cadence、Runnable 和 visibility recovery；
- `LyricViewRegistry`：roots、Recycler、TextView、renderer target 的弱引用和快照。

当前大量方法通过 `currentWordLyricModel`、`activeRendererWordLine`、`activeLyricRefreshAnchor` 互相读写。拆分后应通过 `LyricRenderSnapshot` 读取模型和播放状态，通过 registry 返回 View 快照，避免控制器之间直接持有对方的 WeakHashMap。

### 3.10 切歌 handoff、官方行抑制和 surface 可见性

位置：约 2992-3125、3374-3544、10648-10952。

包含：

- system UI lyric mode 激活/停用；
- transient surface miss 保留；
- official lyric track handoff；
- Recycler alpha 抑制、恢复和 late custom takeover fade；
- track position reset guard；
- AOD/亮屏 settle window。

这些状态决定“什么时候允许自绘覆盖官方字”，是核心时序边界。建议独立为 `LyricSurfaceHandoffController`，持有 handoff generation、suppressed alpha、fade deadline、settle windows，并提供明确的状态机：

```text
INACTIVE
  -> SURFACE_PENDING
  -> OFFICIAL_SUPPRESSED
  -> CUSTOM_READY
  -> REVEALED
  -> TRANSIENT_MISS / INACTIVE
```

状态机只输出允许/禁止绘制和恢复 Recycler 的命令，不直接解析 lyricInfo 或计算文字 frame。

注意当前实现的真实语义：`suppressRememberedLyricsRecyclerViews` 和
`animateLyricsRecyclerFadeIn` 主要负责失效与重绘，SystemUI 仍拥有 Recycler 的正式 alpha/位置
过渡。拆分时不能根据方法名把 module 自己变成 Recycler alpha 动画 owner；应保持“只在
handoff 窗口内抑制自绘、等待官方 surface 准备、再恢复重绘”的现有行为。

### 3.11 Screen timeout / WakeLock

位置：约 9635-10348。

这是相对独立且高风险边界：

- receiver 注册；
- interactive/keyguard 缓存；
- lyric evidence grace window；
- custom timeout lease；
- WakeLock acquire/release；
- user activity pulse；
- screen off/user present 回调。

建议优先拆为 `ScreenTimeoutController`，其输入只有：

- `ScreenTimeoutConfig`；
- 当前 SystemUI lyric mode；
- 最近可见 lyric evidence；
- screen/keyguard 状态。

它不应调用 `currentWordLyricModel` 或 `officialLyricTextRenderer` 的内部方法，只读取 `LyricRuntimeEvidence`。WakeLock 是 system_server/SystemUI 高风险路径，迁移后必须单独验证 acquire、lease renewal、screen off、user present 和条件失效。

### 3.12 Diagnostics 与日志

位置：约 11003-11925。

当前已有 `diagnostics` 包和 `BridgePerformanceSampler`，但入口仍包含：

- 各区域日志门面；
- 采样与节流状态；
- artwork probe；
- payload/frame/geometry/Recycler 诊断格式化；
- debug 配置应用和广播回执。

建议新增 `BridgeRuntimeLogger` 或 `BridgeLogPort`，令控制器只提交结构化事件和脱敏字段。迁移时不要把现有日志字符串随意重命名，因为设备回归脚本和问题定位依赖当前 marker；可以先保留 event 常量和输出格式，再逐步迁移调用者。

### 3.13 `OfficialLyricTextRenderer` 内嵌渲染器

位置：约 11956-15465，约 3,500 行以上，另有约 200 行内部 cache/context 类。

渲染器本身已经是独立概念，但目前仍是 `LockscreenLyricsModule` 的私有内部类，因此：

- 能直接访问入口类的静态工具、配置、日志和屏幕状态；
- 绘制、翻译动画、row visual state、char lift、glow cache 和布局缓存共用一个对象；
- 任何渲染错误都会回到入口类的 Hook 回调上下文。

建议分两步：

1. 先原样移动为 `render/OfficialLyricTextRenderer.java`，通过 `RendererHost` 暴露少量能力：配置快照、时间、日志、slot height、文本宽度和失败回退。第一步不改变算法和字段；
2. 再按绘制责任拆出 `LyricGroupPainter`、`WordProgressPainter`、`CharLiftPainter`、`GlowPainter` 与 `LyricLayoutMetrics`。只有性能采样证明有收益时才做第二步。

不要在第一次迁移时同时调整颜色、动画时长、progress 边界或 glow 算法。Phase 6 台账已经明确 renderer 后续应以 `PERF_SAMPLE` 决定，算法和组织方式必须分开变更。

## 4. 字段所有权建议

下表按当前字段群给出目标所有者。字段移动必须与所有读写方法一起移动；若暂时不能移动，应通过 package-private snapshot/port 读取，而不是开放整个入口类。

| 当前字段群 | 当前大致行号 | 目标所有者 | 说明 |
| --- | ---: | --- | --- |
| `currentWordLyricModel*`、provider payload、cleanup snapshot | 281-314、417-421 | `LyricModelStore` | 模型、签名、track key 和 cleanup 原始快照必须同一所有权 |
| publication gate、parse/assembly commit | 313-314、10462-10647 | `LyricPublicationPipeline` + `LyricModelStore` | epoch 只允许在 pipeline commit 前复核 |
| playback clock、state、speed、reset guard | 299-323、3684-4265 | `SystemUiPlaybackController` | 输出 immutable `PlaybackSnapshot` |
| SystemUI DexKit targets、private method cache | 324-335 | `SystemUiTargetResolver` | 解析一次，失败 legacy fallback |
| OPlus policy flags and logged package sets | 337-350 | `OplusMediaPolicyHooks` | 与歌词模型完全隔离 |
| translation rule/package/preferences | 351-401 | `TranslationActionController` | package-keyed preference 与 media rebind |
| translation View/fingerprint caches | 522-555 | `TranslationActionViewTracker` | View 弱引用和图标缓存 |
| lyric UI config、cleanup config、loaded timestamp | 402-422 | `LyricSettingsController` | 发布 `RuntimeConfigSnapshot` |
| handoff/fade/settle generations | 424-447、10648-10952 | `LyricSurfaceHandoffController` | 用显式状态机替代散落 boolean/deadline |
| screen timeout receiver、WakeLock、keyguard cache | 448-465、9635-10348 | `ScreenTimeoutController` | 高风险，独立验证 |
| Recycler/adapter/view registries | 485-521 | `LyricViewRegistry` + `LyricsRecyclerController` | registry 只做弱引用和快照，controller 做行为 |
| bind epochs、draw frame cache、normalized text | 502-514 | `OfficialLyricBindingController` | 按 TextView 身份和内容 hash 失效 |
| active refresh cadence/surface reactivation | 556-573、8722-8985 | `ActiveLyricRefreshScheduler` | 所有 Runnable 归一个调度器 |
| renderer instance and paint/cache state | 574-580、11956-15465 | `OfficialLyricTextRenderer` | 通过 Host 访问外部能力 |
| log throttles and debug settings | 285-308、11003-11925 | `BridgeRuntimeDiagnostics` | 保留当前 event 和脱敏规则 |

### 必须避免的状态反模式

- 把全部字段放进一个 `BridgeRuntimeState` 再让所有 Helper 访问；这只是把 God Object 改名；
- 为了减少构造参数，向控制器传入 `LockscreenLyricsModule`；这样依赖方向没有改变；
- 让 renderer 直接读取 `currentLyricProviderPayload` 或 WakeLock 状态；渲染只应依赖 render snapshot；
- 让 ScreenTimeoutController 反向调用 Recycler 或 renderer；它只消费 evidence；
- 让 TranslationActionViewTracker 修改 provider/model state；View presentation 和歌词内容必须隔离。

## 5. 建议的目标目录结构

```text
app/src/main/java/io/github/andrealtb/lockscreenlyrics/
  LockscreenLyricsModule.java             # 入口、生命周期、组装、极少量事件转发
  bootstrap/
    SystemUiRuntimeBootstrap.java
    SystemServerRuntimeBootstrap.java
  hook/
    SystemUiHookInstaller.java
    SystemServerHookInstaller.java
    LyricsRecyclerHookInstaller.java
    OfficialLyricTextHookInstaller.java
    PluginHookGenerationManager.java
  runtime/
    LyricModelStore.java
    LyricPublicationPipeline.java
    LyricSurfaceHandoffController.java
    LyricViewRegistry.java
    ActiveLyricRefreshScheduler.java
    SystemUiPlaybackController.java
    ScreenTimeoutController.java
    LyricSettingsController.java
  systemui/
    SystemUiLyricLoadHook.java
    SystemUiTargetResolver.java
    OplusMediaPolicyHooks.java
    TranslationActionController.java
    TranslationActionViewTracker.java
    KuWoSystemUiCompatibility.java
  recycler/
    LyricsRecyclerController.java
    LyricSurfaceReadinessController.java
    LyricsRecyclerReflection.java
  render/
    OfficialLyricTextRenderer.java
    OfficialLyricBindingController.java
    LyricRenderSnapshot.java
    # 后续再按性能数据拆 Painter
  diagnostics/
    BridgeRuntimeDiagnostics.java
    BridgeLogPort.java
```

目录不是硬性要求；关键是依赖只能从入口向下，不能让所有新类再次依赖入口类。

## 6. 推荐迁移顺序

### Slice 0：建立保护网，不移动业务

1. 固定当前 commit、源码 hash 和测试基线；
2. 记录当前测试数量、编译任务和已知测试 runner 问题；
3. 为模型发布、handoff、screen timeout、translation action、Recycler ownership 建立行为清单；
4. 在源码中为 epoch、generation、scope 和官方所有权写短注释；
5. 禁止在这一步改算法、常量或日志格式。

### Slice 1：迁移低耦合、低风险的策略/适配器

优先顺序：

1. `OplusMediaPolicyHooks`；
2. `SystemUiTargetResolver`；
3. `PluginHookGenerationManager`；
4. `BridgeRuntimeDiagnostics`；
5. `ScreenTimeoutController`。

原因：这些组件的输入输出明确，和歌词绘制算法耦合较少，迁移后能马上减少入口类的字段和方法数量。ScreenTimeout 虽然风险高，但行为边界独立，适合在有测试和日志门禁后迁出；它排在 diagnostics 之后，是为了先保留可观察的 acquire/release 证据。

### Slice 2：拆翻译 action

先迁 `TranslationActionViewTracker`，再迁 `TranslationActionController`。迁移期间保留 `TranslationToggleMediaActionBinder.Host`，但 Host 只能暴露：当前 package、enabled 查询、图标 fingerprint 回调和 click 回调。不要把整个 module 作为 Host。

### Slice 3：拆播放状态和歌词模型发布

1. 先创建 `PlaybackSnapshot`，让现有代码继续消费；
2. 迁移 MediaController discovery/callback 和 track reset guard；
3. 迁移 `LyricModelStore`；
4. 迁移 `LyricPublicationPipeline`；
5. 最后让 SystemUI loader Hook 只负责调用 pipeline 和 `HookProceed.once`。

这个顺序能把最容易发生 stale result 的异步链先固定下来。每一步都必须保留“输入 identity + generation/epoch，提交前再次复核”的规则。

### Slice 4：拆 Recycler 和 View registry

先把弱引用列表、缓存和 snapshot 方法放入 `LyricViewRegistry`，不改变 Recycler 行为；然后迁移 `LyricsRecyclerController`；最后迁移 `LyricSurfaceReadinessController`。这样可以先减少入口字段，再处理最敏感的时序。

必须保留以下行为：

- SystemUI/vendor 仍拥有 native Recycler 的 position、SmoothScroller、adapter notification 和 row transform；
- module 只在已有策略和已知 official index 滞后证据下进行 adaptive follow；
- attach 期间的透明、0x0 或 `officialIndex=-1` 不得单独触发强制对齐；
- 每个 Recycler 的 render pass 必须按 epoch 丢弃旧回调。

### Slice 5：拆 handoff 和 active refresh

把 handoff 状态机和 active refresh scheduler 迁出后，TextView Hook 回调应变成：

```text
read view -> binding controller -> handoff decision -> renderer/coordinator -> proceed/fallback
```

不要让 `onTextViewOnDraw` 同时决定模型、surface、translation button 和 WakeLock。

### Slice 6：外移 `OfficialLyricTextRenderer`

先做机械迁移：保持方法、字段、锁和算法不变，只替换外部访问点。完成编译和设备冒烟后，再根据 `PERF_SAMPLE` 判断是否拆 painter。任何 painter 拆分都必须保持：

- AOD low-frame-rate 下的 word progress 规则；
- char-lift 开关和异常回退；
- translation layout amount 的动画状态；
- glow cache 生命周期；
- official slot height 和 Recycler geometry 的所有权。

## 7. 入口类迁移后的职责标准

最终 `LockscreenLyricsModule` 应只保留：

1. `onModuleLoaded`、`onSystemServerStarting`、`onPackageReady`；
2. 进程和 scope 级别门禁；
3. 控制器和 installer 的构造；
4. 依赖注入（logger、scheduler、hook registrar、context provider）；
5. 极少数跨组件事件，例如“新模型已发布”“surface 已失效”；
6. `detach`、全局紧急降级和组件关闭。

入口类不应再包含：

- 具体 reflection 方法扫描；
- TextView/Recycler 的递归遍历；
- Bitmap alpha fingerprint 算法；
- WakeLock lease 细节；
- translation preference 的 SharedPreferences key 规则；
- renderer paint 和 Canvas 绘制；
- payload parse/assembly 细节。

## 8. 跨组件接口建议

### 8.1 用 snapshot 传数据

推荐定义不可变快照：

```java
final class LyricRenderSnapshot {
    final WordLyricModel model;
    final PlaybackSnapshot playback;
    final LyricUiConfig uiConfig;
    final boolean translationEnabled;
    final boolean aodLowFrameRate;
    final int handoffGeneration;
}
```

实际实现可以根据项目风格使用 package-private final class。重点是 renderer 不读取控制器的可变 Map/WeakHashMap，也不自己拼接跨线程状态。

### 8.2 用窄 Port 代替入口 Host

建议最小接口：

- `MainThreadPort`: `post`、`postDelayed`、`removeCallbacks`；
- `ContextPort`: 当前 application context；
- `BridgeLogPort`: structured event、warning、error、throttle；
- `HookPort`: 安装 Hook、设置 ID、protective mode；
- `LyricModelPort`: 当前模型和提交回调；
- `LyricViewPort`: registry snapshot 和 invalidate/requestLayout；
- `ScreenEvidencePort`: mode、visible text、model evidence。

如果一个接口出现超过约 12 个方法，说明边界可能再次变成 God Host，应继续拆分。

### 8.3 明确线程规则

- Xposed Hook 回调可能不全在主线程；模型 store 的 epoch/identity 提交继续使用现有锁或原子门禁；
- View、Canvas、Recycler、WakeLock 和 receiver 的操作必须在现有允许的线程执行；
- Scheduler 由统一的 `MainThreadPort` 提供，控制器内部不直接创建新的 Handler；
- snapshot 在后台解析完成后再切回主线程提交，提交前必须复核 model identity 和 publication epoch。

## 9. 不应拆分或应最后拆分的部分

### 不应立即拆分

- `onSystemUiLoadLyricInBg` 内的完整提交链：应先引入 pipeline/store，再移动；
- `OfficialLyricTextRenderer` 的算法内部：机械外移前不要做 painter 重写；
- `LyricSurfaceHandoffController` 与 Recycler readiness：二者虽然可分文件，但必须共享明确的 generation 协议；
- 反射工具方法：如果先抽成通用 `ReflectionUtils`，容易隐藏目标类型和失败语义，建议按系统边界分别封装。

### 可以保留在入口的少量内容

- Xposed API 的入口回调；
- `currentApplicationContext`、`post`、`postDelayed` 的组装适配；
- 最终的 emergency detach；
- 组件构造顺序和日志启动事件。

## 10. 验证和回归门禁

### 10.1 每个 slice 的静态检查

- `git diff --check`；
- `LockscreenLyricsModule` 中目标字段和方法确实减少，未出现重复实现；
- `BridgeArchitectureGuardTest` 仍通过；
- Phase 5 禁止的 player-process/provider transport 依赖仍为 0；
- `scope.list`、`module.prop` 和 `java_init.list` 不变；
- 新类不反向依赖 `LockscreenLyricsModule`，除非是明确的生命周期适配层。

当前测试中有源码路径契约，必须同步迁移：

- `OfficialLyricScalePivotHookContractTest` 直接读取入口源码并检查 pivot Hook 片段；
- `CharLiftRendererContractTest` 直接读取入口源码并检查 renderer 字段和行为；
- `LyricTimingTuningConstantsTest` 通过入口类文档/引用锁定常量来源；
- `BridgeArchitectureGuardTest` 直接检查入口源码中的架构禁止项。

拆分后应把这些测试改为读取新类，或改成行为/策略测试。不能为了让旧测试继续通过而在入口保留一份“影子实现”。

### 10.2 现有建议构建命令

按仓库规定使用：

```powershell
scripts\dev.cmd bridge :app:compileDebugJavaWithJavac :app:compileDebugUnitTestJavaWithJavac
scripts\dev.cmd bridge :app:testDebugUnitTest
scripts\dev.cmd bridge :app:assembleDebug
```

文档/结构性 slice 不需要每次启动完整 Android 构建，但涉及 Java 移动时至少执行编译和受影响测试；涉及 Hook 或 renderer 时再执行完整 debug assemble。

### 10.3 行为回归矩阵

每个高风险 slice 至少覆盖：

| 场景 | 必须观察的结果 |
| --- | --- |
| 模块加载 | `system_server`、SystemUI 进程门禁和 Hook installed 日志不变 |
| 无 lyricInfo | 官方 renderer 保持原行为，module 不残留旧 model |
| 新曲快速切换 | 旧 parse 不得提交，handoff generation 单调有效 |
| 同曲 metadata 更新 | 不制造伪 track reset，不清空有效 model |
| 播放/暂停/seek | active line、position 和 Recycler ownership 正常 |
| AOD/亮屏切换 | 低帧率规则、surface readiness 和淡入顺序不变 |
| Recycler attach/detach | 弱引用清理，旧 surface callback 不影响新 Recycler |
| translation 开关 | action、偏好、icon presentation 和歌词 translation layout 一致 |
| screen off/user present | WakeLock lease、pulse 和 release 条件正确 |
| KuWo/plugin loader | 新 ClassLoader 会清旧 Hook，旧 loader 不被 pin 住 |
| renderer 异常 | protective fallback 生效，SystemUI 不崩溃 |

### 10.4 日志 marker

迁移期间保留现有结构化日志区域和事件名，重点关注：

- `SYSTEMUI_BOOTSTRAP`；
- `HOOK_INSTALLED`；
- `SESSION_REDUCED`；
- `RENDER_STATE_CHANGED`；
- `AOD_TRANSITION`；
- `TRANSLATION_ACTION_REBIND`；
- `PERF_SAMPLE`；
- Recycler attach/align/notify guard；
- model parse、stale epoch drop、handoff start/release。

缺少 marker 只能说明当前路径没有被观测到，不能直接证明算法失败。SystemUI、AOD、WakeLock 和 Recycler 的最终行为仍需要设备验证。

## 11. 风险分级

### 低到中风险

- 纯策略类和 OPlus 返回值适配；
- 日志门面；
- 偏好读取/配置 snapshot；
- View registry 的机械迁移。

### 中到高风险

- MediaController callback 和 playback clock；
- model store/publication pipeline；
- Recycler reflection、adapter notify guard；
- translation action 的 action rule/rebind；
- plugin ClassLoader generation。

### 高风险

- `OfficialLyricTextRenderer`；
- handoff alpha 与 surface readiness；
- screen timeout WakeLock；
- 任何改变 SystemUI private method 参数或返回值的 Hook。

高风险组件迁移必须具备：稳定的失败回退、明确的日志、单独的行为测试和用户设备冒烟。编译通过不等于 SystemUI/AOD 设备验收通过。

## 12. 推荐的完成标准

拆解工作完成后，应达到以下状态：

1. `LockscreenLyricsModule` 只负责生命周期、组装和事件分派；
2. 模型、播放、Recycler、handoff、screen timeout、translation action、diagnostics 和 renderer 各自有单一状态所有者；
3. 新组件不把入口类作为万能 Host；
4. stale parse、track identity、generation、surface epoch 和 plugin loader generation 都有显式协议；
5. 所有官方 ownership 边界写在组件接口或注释中；
6. 源码契约测试已经迁移到新文件或行为测试；
7. 每个高风险组件有最小回归矩阵和设备验证记录；
8. 未恢复 Provider 进程 Hook、私有歌词广播或 direct-v4 transport；
9. 性能采样显示拆分没有把高频路径变成更多对象分配或日志输出；
10. 任何算法参数调整都与文件/职责迁移分开提交。

## 13. 最终建议

不要把这次工作命名为“把巨型类拆成多个类”，而应按三个目标推进：

1. **减少状态共享**：每个 generation、cache、WeakMap、WakeLock 和 renderer 状态只有一个 owner；
2. **减少时序耦合**：Hook 回调、模型提交、surface handoff 和绘制通过 snapshot/event 连接；
3. **减少验证范围**：策略、解析、播放、Recycler、renderer 和 screen timeout 可以分别编译、单测和定位。

按 Slice 1-6 逐步迁移，比一次性重写 `LockscreenLyricsModule` 更能保护当前核心功能。最值得优先处理的是 OPlus policy、ScreenTimeout、Translation action、Playback controller、LyricModelStore 和 Recycler/View registry；内嵌 renderer 的算法拆分应最后进行，并以实际 `PERF_SAMPLE` 证据决定是否继续。
