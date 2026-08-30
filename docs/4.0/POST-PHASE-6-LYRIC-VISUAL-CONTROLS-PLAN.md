# Phase 6 后：歌词亮度层级与边缘渐隐全面开放计划

状态：**已完成并关闭。2026-08-30 v5 pass-through、Slice 0、A、B、C、D.1 均已完成
用户设备闭环；canonical 翻译按钮、schema v3、renderer、独立视觉设置页、全部亮度/渐隐
配置、四套预设与全局 Bridge 备份/恢复均通过。**

启动门槛：Phase 6 性能/巨型方法治理完成，最终 Phase 6 APK 通过互动锁屏、AOD、
长行、翻译和无歌词返回的设备回归后再开始。本计划不得插入剩余 Phase 6 slice。

## 1. 目标

把当前 renderer 中与歌词亮度层级、翻译亮度和上下边缘渐隐直接相关的固定值开放给
用户，同时保证：

- 不修改 Provider、`lyricInfo`、歌词解析、Recycler ownership 或 AOD 时序；
- 用户不调整新选项时，视觉与 Phase 6 最终基线完全一致；
- v1/v2 设置可迁移，现有非实时透明度、颜色、字体、glow、模糊等设置不丢失；
- 主设置页不继续膨胀为巨型方法，使用独立“歌词亮度与渐隐”子页面；
- 设置预览和 SystemUI 实际 renderer 使用同一组颜色/约束 policy。

## 2. 实施前设置链路审计

本节审计的是“设置控件 → 草稿/约束 → app SharedPreferences → 定向广播快照 →
SystemUI SharedPreferences/运行时快照 → renderer/业务消费者”。结论均为源码静态确认；
“立即生效”的交互细节仍需设备验证。

### 2.1 已确认接通的设置面

`LyricUiConfig` 当前 schema v2 的 25 个字段都进入 codec/repository，主页面保存会发送完整
snapshot，SystemUI 接收后保存、更新 `lyricUiConfig` / `runtimeLyricUiConfig`，再重绘已有
Recycler/TextView。没有发现“字段能保存、但运行时完全没有消费者”的正式 UI 配置。

| 设置面 | UI/持久化 owner | 运行时消费者 | 静态结论 |
|---|---|---|---|
| 主色、光晕色、光晕强度/半径 | `LyricUiSettingsActivity` → `LyricUiConfig` | `LyricUiColors` / `LyricUiPalette` / renderer glow cache | 已接通 |
| 非活动透明度、模糊/半径、缩放/比例 | 同上 | palette、官方 Recycler scale、row blur/fade | 已接通，但存在叠乘与预览差异，见 2.2 |
| 字号、翻译字号比、字重、对齐、两类行距 | 同上 | `LyricUiLayoutPolicy`、paint/layout、Recycler spacing | 已接通 |
| 动效模式、长主歌词纵向浏览、长翻译横向滚动 | 同上 | handoff/row animation、passive pan、translation marquee | 已接通，但只在满足歌词类型/溢出条件时生效 |
| 刷新上限 | 同上 | `LyricRefreshRatePolicy` / active refresh cadence | 已接通；AOD 仍由独立低帧率策略接管 |
| 逐行进度、翻译进度 | 同上 | line/translation reveal renderer | 已接通，但有依赖关系，见 2.2 |
| 屏幕常亮与自定义秒数 | 同上 | keep-awake/wake-lock 窗口 | 已接通 |
| 全局/逐播放器翻译默认值与按钮 | `PlayerTranslationSettingsActivity` | 翻译状态缓存、renderer、media action binder | 已接通，但当前动作栏刷新不完整，见 2.2 |
| 开头歌词清理开关、学习规则、逐曲修正 | `LyricOpeningCleanupSettingsActivity` / 独立 config | payload cleanup + 当前 model 重建 | 已接通 |
| 调试总开关与 7 个 area | `BridgeDebugSettingsActivity` / 独立 prefs | `StructuredBridgeLog` / performance sampler | 已接通；bootstrap 类日志仍需重启 SystemUI 才会出现 |
| Default/Soft/Vivid/Minimal 预设 | 主页面 preset apply/detect | appearance/typography/motion 子集 | 已接通；不是独立持久化字段 |
| 中英文切换 | `SettingsBaseActivity` / app locale prefs | 设置 Activity 重建后的 resources | 已接通；有意不影响 SystemUI 语言 |
| 模块状态查询、保存 ACK、重启 SystemUI | 主页面 ResultReceiver | SystemUI 动态 receiver / restart handler | 请求与回执链已接通；真实重启结果仍以设备为准 |

现有预设只拥有 appearance/typography/motion 的一部分；翻译默认、刷新上限、常亮、兼容性
和调试项不会被预设覆盖，这是当前设计，不属于脱钩。

### 2.2 已发现的异常、弱耦合和易误判项

| 优先级 | 问题 | 源码事实 | 计划处理 |
|---|---|---|---|
| P0 | 非活动透明度的 UI 数值不是部分配置下的最终亮度 | 当缩放或模糊任一开启时，renderer 还会乘 `OFFICIAL_LYRIC_INACTIVE_ROW_FADE=0.9`；例如 44% 最终约 39.6%。当前预览没有这层 90% | 新 policy 明确“基础透明度 × 行 fade × 临时过渡 fade”，预览与 renderer 共用；UI 同时显示基础值和稳态有效值 |
| P0 | 原计划的 row fade 默认值与“默认无视觉漂移”矛盾 | 当前 90% fade 只在缩放/模糊开启时生效；默认配置二者都关，所以实际没有额外 fade。若 v3 无条件应用 90%，默认画面会变暗 | 增加独立 enable 字段；v1/v2 迁移按 `scaleEnabled || blurEnabled` 推导，fresh default 为关 |
| P0 | 当前预览不能作为 SystemUI 的精确视觉合同 | 预览把实时行整行画成主色、把 glow 画在整行；没有逐字已唱/未唱分区、90% row fade、翻译进度或 Recycler 上下渐隐 | 本计划重做为共享 alpha/palette policy 驱动的分层预览 |
| P1 | “锁屏媒体卡翻译按钮”不保证对当前已生成的动作栏立即生效 | 保存 handler 更新 prefs/cache，但没有触发一次当前 media actions 重建；`userWantsTranslationButton()` 只在下一次 `createActionsFromState` 时参与绑定 | slice 0 已对缓存 MediaButton 重绑并立即刷新 tracked view；用户设备确认无需切歌/暂停即可正确显示或隐藏 |
| P1 | 从翻译子页返回主页面会产生跨页面 stale baseline | 主页面只在 `onCreate` 设置 `savedConfig`；子页会改同一 `LyricUiConfig.defaultTranslationEnabled`，主页面 `readDraft()` 又会重新读取它，可能出现“主页面没改却显示未保存” | slice 0 已用 owner-aware `onResume` merge；本地 policy/合同测试通过 |
| P1 | 自定义常亮秒数的显示值可能与实际保存值不一致 | config 会把大于 86400 的输入 clamp 到 86400、非正数归 0；保存后页面没有按 sanitized config 回绑输入框 | slice 0 已规范解析并在保存后回绑 canonical config；本地测试通过 |
| P1 | 翻译进度开关存在未表达的依赖 | 对 `LINE_TIMED` 歌词，翻译进度还要求“普通逐行歌词进度”开启；单开翻译进度会看起来无效 | slice 0 已增加条件说明；word-timed 歌词仍可独立开启 |
| P2 | 光晕有两个 owner 表象 | `glowEnabled` 控件被隐藏，实际由光晕强度拖到 0/非 0 间接控制；异常导入的 `enabled=false,intensity>0` 会在 bind 时折叠成 0 | slice 0 已移除隐藏 UI owner，以 intensity 为唯一 owner；旧字段只作 codec 兼容 |
| P2 | 不受当前屏幕支持的已保存刷新率可能被静默改成“跟随屏幕” | 下拉只构造当前 display 报告的 60/90/120；找不到已保存值时索引回 0，随后保存会覆盖 | slice 0 已保留并标记“当前不可用”的已保存值；本地测试通过 |

上述 P1/P2 不代表设置永久失效：多数是条件生效、当前页面回显或即时刷新问题。它们必须在
新增视觉子页面之前通过 slice 0 收口，避免把旧 owner 问题带入 schema v3。

### 2.3 已确认的非问题

- “长歌词自动纵向浏览”只作用于逐行歌词、关闭逐行进度、超过两行且非 AOD 的场景；
- “长翻译自动横向滚动”只作用于实时行、文字溢出且非 AOD 的场景；
- 刷新上限不接管 AOD 约 5 Hz 的刷新策略；
- debug bootstrap/hook-install 日志不能在 hook 已安装后补发；
- 开头歌词清理只保留用户主动选择的版权/制作信息、标题歌手、学习规则和逐曲修正；
  Bridge 不再内置不可关闭的“歌词翻译由……提供”内容黑名单。

### 2.4 v5 `lyricInfo` 责任边界修正（2026-08-30）

目标合同改为：标准 v5 `lyricInfo` 只要能被官方 SystemUI 正确消费，Bridge 就必须在不做
播放器内容补丁的前提下正确消费。Bridge 仅保留 schema、时间轴、空值、重复文本、罗马音
识别和异常兜底等结构安全；不根据歌词正文中的播放器名称或来源声明决定删行/改 lane。

现有 v5 Provider 已定版，本计划不反向扩展 Provider 工作量。Bridge pass-through
切片已移除 `LyricMetadataFilter.isParsingProtectedLine` 在 opening cleanup、lane classifier、
LyricsCore、OPlus official-list normalizer 和 native model assembler 中的强制过滤。若设备回归
发现某一 Provider payload 确实不规范，单独记录该 Provider 的输入证据；不得先把字符串黑名单
塞回 Bridge。

这里不取消用户可配置的“开头歌词清理”功能。它是明确的显示偏好，用户可以关闭内置规则、
删除学习规则或清除逐曲修正；本次取消的是此前无论用户怎样设置都必定生效的隐藏治理。
三个内置清理规则的 fresh/missing 默认值改为关闭，“恢复默认”也回到全关；已经明确保存过的
用户开关状态继续保留，不把此次默认值调整伪装成强制迁移。

测试切片本地证据：

- 已删除 `FIXED_PARSING` 和 `isParsingProtectedLine` 的生产调用面；
- main/test Java 编译通过；相关 parser/cleanup/assembler 8 类 85 tests 通过；
- 排除已知依赖 Android runtime/JVM stub 的 8 类后，49 类 378 tests 通过；
- `assembleDebug`、APK v2 签名和 16 KiB ZIP alignment 校验通过；
- APK：`artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-v5-pass-through-debug.apk`；
  8,786,453 bytes；SHA-256
  `E1344E00B932017898A49CED31BC54C2B069B83A8785C51105205FC547E2C67B`；
- 设备状态：用户在同时关闭全部内置清理规则后完成播放测试，未见明显异常；确认永久删除
  Bridge 隐藏脏数据治理，无需重新调整已经定版的 v5 Provider。
- 默认关闭正式候选：
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-v5-pass-through-cleanup-default-off-debug.apk`；
  9,538,504 bytes；SHA-256
  `5B10F3A4C09CC35AE5C628C8775C93D714DB1262BFCAB1500926D54350D3FBB8`；main/test
  编译、85 项定向测试、378 项扩大 JVM 回归、v2 签名与 16 KiB alignment 均通过。

## 3. 当前基线与开放范围

| 层级 | Phase 6 基线 | 当前可控 | 计划 |
|---|---:|---|---|
| 实时主歌词高亮 | 100% | 仅 RGB 主色 | 独立透明度 |
| 当前行未高亮底色 | 50% | 否 | 独立透明度 |
| 实时翻译基础 | 60% | 否 | 独立透明度 |
| 实时翻译进度 | 80% | 仅开关 | 独立透明度 + 原开关 |
| 非实时主歌词 | 默认 44% | 是，30–100% | 保留现有控件 |
| 非实时翻译 | 跟随非实时主歌词 | 间接 | 跟随开关 + 独立透明度 |
| Recycler 上下渐隐 | 开，51.9dp | 否 | 开关 + 长度 |
| 非活动行额外 fade | 90%，仅缩放/模糊开启时参与 | 否 | 独立开关 + 高级透明度 |

第一版不开放 Android fading-edge strength。公开 API 只能稳定设置 enable/length；强度需要
额外 hook `getTopFadingEdgeStrength` / `getBottomFadingEdgeStrength` 或自绘遮罩，容易与官方
Recycler fade 双重叠加。等 enable/length 设备闭环后再单独评估。

## 4. `LyricUiConfig` schema v3

新增字段：

| 字段 | 默认值 | 建议范围/规则 |
|---|---:|---|
| `activeOpacityPercent` | 100 | 50–100 |
| `currentUnrevealedOpacityPercent` | 50 | 20–100 |
| `activeTranslationOpacityPercent` | 60 | 20–100 |
| `activeTranslationProgressOpacityPercent` | 80 | 20–100 |
| `inactiveTranslationFollowsMain` | true | boolean |
| `inactiveTranslationOpacityPercent` | 44 | 20–100；跟随时不参与渲染 |
| `verticalFadeEnabled` | true | boolean |
| `verticalFadeLengthTenthsDp` | 519 | 0–1200，即 0–120dp |
| `inactiveRowFadeEnabled` | false | boolean；v1/v2 迁移时从旧缩放/模糊状态推导 |
| `inactiveRowFadePercent` | 90 | 50–100 |

约束 policy：

- `activeTranslationOpacityPercent <= activeTranslationProgressOpacityPercent`；
- `activeTranslationProgressOpacityPercent <= activeOpacityPercent`；
- `currentUnrevealedOpacityPercent <= activeOpacityPercent`；
- 独立非实时翻译建议保持 `inactiveTranslationOpacityPercent <= inactiveOpacityPercent`；
- 开启翻译跟随时，非实时翻译必须直接使用 `inactiveOpacityPercent`，不能保存时偷偷复制；
- fade 关闭时保留长度值，重新打开恢复用户上次长度；
- row fade 关闭时保留百分比；开启时稳态有效 alpha 为 lane base alpha × row fade；
- v3 后用户新开/关缩放或模糊不再偷偷改 row fade enable；三者成为独立 owner，预设必须显式给值；
- handoff、model reveal、AOD transition 等临时 fade 继续由 renderer 内部控制，不进入用户配置；
- 所有百分比都在 config 构造边界 clamp，renderer 不再二次修正。

## 5. 兼容与迁移

1. `SCHEMA_VERSION` 升至 3。
2. codec 接受 v1、v2、v3；v1/v2 缺失字段使用上表 Phase 6 基线。
3. v2 的 `inactiveOpacityPercent` 原值原样保留，并作为独立非实时翻译的首次默认值。
4. v1/v2 的 `inactiveRowFadeEnabled` 迁移值必须等于旧配置的
   `scaleEnabled || blurEnabled`；fresh defaults 才是 false，确保所有旧用户视觉不漂移。
5. 新字段加入 SharedPreferences、Intent partial decode、apply result 和配置 readback。
6. `resetAppearance()`、`buildUpon()`、`equals/hashCode()` 全量覆盖新字段。
7. 降级到旧 APK 时 schema v3 可能被旧 codec 拒绝；发布说明需明确，正式发布前保留设置
   导出/重置路径，不伪装成可无损降级。

## 6. 设置 UI

新增独立子页面 `LyricVisualLayersSettingsActivity`，主页面现有“颜色”卡片增加入口。
子页面保留 sticky live preview，避免继续扩大 `LyricUiSettingsActivity#createContent`。
现有主页面“非实时歌词透明度”滑块迁入子页面，原位置改为只读摘要/入口；同一配置不能
出现两个可编辑控件 owner。

### 6.1 歌词亮度层级

按实际视觉顺序放置：

1. 实时歌词高亮；
2. 当前行未高亮部分；
3. 实时翻译；
4. 实时翻译进度；
5. 非实时歌词（复用现有值）；
6. “非实时翻译跟随正文”开关；
7. 非实时翻译亮度（关闭跟随后显示）。

拖动上层滑块导致约束冲突时，联动压低下层值，并在 value label 中立即显示结果；不要到
保存时静默修正。

### 6.2 上下边缘渐隐

- 开关；
- 渐隐长度 0–120dp，0.1dp 步进，确保官方默认 51.9dp 可精确回显；
- “非活动行额外淡化”独立开关；
- 非活动行额外淡化 50–100%；
- 文案明确“长度决定渐变覆盖区域，不是固定第 1/2 行透明度”。

## 7. 预览要求

预览必须同时显示：

- 实时主歌词的已高亮与未高亮部分；
- 实时翻译与可选进度高亮；
- 至少两组非实时正文/翻译；
- 上下边缘 mask；
- 翻译跟随开/关两种状态。

预览使用共享的 `LyricVisualAlphaPolicy` / `LyricUiPalette`，禁止 Activity 自己复制 alpha
公式。它必须展示配置的 base alpha、row fade 后的稳态有效 alpha，并保证 word feather 的
shader 曲线只应用一次 lane alpha，不能把 active/translation opacity 二次相乘。边缘预览
可用 Canvas gradient 模拟，但 SystemUI 仍使用 RecyclerView 原生 fading edge。

## 8. 实施切片

### 0. 现有设置 owner 与回显收口（已完成并设备闭环）

- **已完成并设备通过**：v5 pass-through 删除不可关闭的翻译来源声明过滤，保留用户可配置 cleanup；
- **已完成**：内置版权/制作/标题歌手清理 fresh defaults 与“恢复默认”改为关闭；
- **设备已通过**：translation action 缓存 model 重绑 + tracked view 即时显示/隐藏；
- **已完成**：跨页 owner-aware merge、常亮秒数规范回显；
- **已完成**：翻译进度依赖说明、不可用刷新率保留策略；
- **已完成**：glow 的单一 owner 规范化为 intensity；
- **已完成**：25 个 schema v2 字段 codec round-trip 与显式 consumer 合同测试；
- 本 slice 不添加亮度字段，不改变 Phase 6 默认画面。

Slice 0 本地证据：

- main/test Java 编译通过；新增 owner/refresh/glow/timeout/contract/translation policy
  定向回归 33 项通过；
- 排除已知依赖 Android runtime/JVM stub 的 8 类后，54 类 388 项通过；
- `assembleDebug`、APK v2 签名、16 KiB alignment 通过；
- APK：`artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-slice0-settings-contract-debug.apk`；
  9,539,676 bytes；SHA-256
  `A6287D4C9497D7A8FF7123BD039A2AB24B334CE8DBCC3E5CDBC8823582E34D76`；
- 设备状态：按钮显示总开关已通过；子页返回 dirty、超范围秒数回显仍待验证。

Slice 0.1（翻译按钮状态亮暗）本地证据：

- 用户日志 `lyrics-log-20260830-030308.txt` 证明点击后翻译布局由 393px 收缩到 304px，
  翻译开关逻辑正常；用户肉眼确认按钮亮暗反馈仍异常；
- 新增共享 `TranslationActionPresentationPolicy`：开启 image alpha=255，关闭=135；
- 点击翻译开关后除更新 MediaAction model 外，同时刷新当前 tracked ImageView 的
  `imageAlpha`、content description 和 invalidate；
- 新增 policy 定向测试后 7 类 19 项通过；扩大 JVM 回归 55 类 390 项通过；
- main/test 编译、`assembleDebug`、APK v2 签名、16 KiB alignment 通过；
- APK：`artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-slice0.1-translation-state-debug.apk`；
  9,539,676 bytes；SHA-256
  `64A1E3A915AFEEB065E17CE4B8D320F4B50EFA4D40EC4ADD89BEDFCE2FC5A573`；
- 设备状态：失败；用户确认点击后不能立即刷新，必须暂停/播放触发媒体卡重绑后才更新。
  结论是只改 model Drawable / ImageView alpha 不足，不能作为最终方案。

Slice 0.2（参考官方收藏按钮即时换图）证据：

- 静态确认：ColorOS `OplusMediaDataManagerStrategy#getHeartAction` 读取
  `USER_RATING/RATING.hasHeart()`，按状态选择两个不同 icon resource，并构造新的
  `MediaAction`；点击 runnable 通过 `TransportControls#setRating()` 让后续媒体数据重建；
- Bridge 的翻译状态是本地偏好，没有播放器 rating 回调可依赖，因此采用同层级的本地事务：
  直接为当前 tracked action ImageView 重新加载翻译图标、按 255/135 写入 Drawable alpha，
  调用 `setImageDrawable` 完成即时换图；不是继续等待暂停产生的 MediaData rebuild；
- 增加 WeakHashMap 状态和 in-flight 防递归：SystemUI 外部重绑会清除已应用状态，Bridge
  自己的 `setImageDrawable` 不会递归替换；容器 view 会向下查找已识别的翻译 ImageView；
- 55 类 390 项扩大 JVM 回归通过；main/test 编译、`assembleDebug`、APK v2 签名、
  16 KiB alignment 通过；
- APK：`artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-slice0.2-official-heart-rebind-debug.apk`；
  9,539,676 bytes；SHA-256
  `29742406ADF2651BBB716A3BEB6FE45C16520AAE90BBD53DE77AAC5FE2C9CB3F`；
- 设备状态：失败；见下一段设备结论。

Slice 0.2 设备结论：

- `lyrics-log-20260830-032947.txt` 有多次 `translation action clicked`，翻译布局高度同步
  改变，说明业务状态切换正常；
- 日志没有 `Rebound translation action icon immediately`，用户肉眼仍需暂停/播放才能刷新；
- 定性：真实按钮 ImageView 没有进入 tracked-view 换图路径，而暂停产生的
  PlaybackState/MediaData 重建才是有效刷新源。废止“直接找 View”作为主方案。

Slice 0.3（SystemUI MediaData 主动重绑）本地证据：

- 复用官方收藏的真正刷新层级：缓存 `OplusMediaDataManagerExImpl` 实例、当前
  `MediaController` 和原始 `PlaybackState`，点击后调用 SystemUI 合成入口
  `access$updateMediaDataFromPlayState(manager, package, state)`；
- 该入口与官方播放状态回调一致，会执行 `MediaDataManager.updateState`，重新进入
  `createActionsFromState` 并绑定新的翻译 action presentation；不伪造播放/暂停状态；
- resolver 优先精确 `updateMediaDataFromPlayState` 名称；仅一个混淆形状候选时才接受，
  多候选失败关闭；3 项 resolver 测试通过；
- 增加不节流的 `TRANSLATION_ACTION_REBIND` 结构化事件，设备日志会明确记录
  `Requested` 或缺少 manager/method/controller/state 的 `Skipped` 原因；
- 定向 3 类 13 项、扩大 JVM 回归 56 类 393 项通过；main/test 编译、`assembleDebug`、
  APK v2 签名、16 KiB alignment 通过；
- released-session 的 `MediaController#getPlaybackState` 异常会按缺失 state 失败关闭，不把
  Binder/session 失效传播到 SystemUI 主线程；
- APK：`artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-slice0.3b-media-data-rebind-safe-debug.apk`；
  9,539,676 bytes；SHA-256
  `6E6DCA746D9732772A6A2D84D394DED44001CC42DB502ADEB79D7C5646D91B65`；
- 设备状态：待验证点击后是否立即重建；若失败，只需检查
  `event=TRANSLATION_ACTION_REBIND`，无需再从海量 BUTTON debug 猜测。

Slice 0.3 设备结论（日志 + 视频）：

- `lyrics-log-20260830-034453.txt` 在 03:45:10.266 明确出现
  `TRANSLATION_ACTION_REBIND Requested enabled=false playbackState=3`，说明 manager、method、
  controller、state 全部就绪且反射调用成功；但当前按钮未刷新；
- 第二次 `Requested enabled=true playbackState=2` 同样要等恢复播放后才刷新；
- 视频逐帧确认：约 23.5 秒暂停后按钮才由高亮变暗，约 28.5 秒恢复播放时才重新变亮；
- 定性：同状态 `updateState` 请求不是缺失，而是当前动作视图没有被这次数据更新重新绑定；
  不能继续把“Requested”当作视觉刷新成功。

Slice 0.4（根视图 action 定位 + View/Data 双路径）本地证据：

- 点击后从已附着歌词 Recycler 一直上溯到完整锁屏根视图（最多 32 层），扫描整个当前
  action tree；不再要求翻译图标必须与 Recycler 处于九层共同祖先内；
- 优先按 `翻译：开启/关闭` content description 识别真实 action 容器；容器一旦可信，允许
  直接定位其三层内子 ImageView，不再依赖容易受 tint/wrapper 影响的 alpha fingerprint；
- 找到 View 后执行 fresh drawable `setImageDrawable` 即时换图；随后仍保留
  MediaData `updateState` 作为第二层兜底；
- 新增不节流事件 `Scanned current translation action views ... trackedViews=N`：设备日志能直接
  判断扫描是否找到真实按钮；
- 扩大 JVM 回归 56 类 393 项通过；main/test 编译、`assembleDebug`、APK v2 签名、
  16 KiB alignment 通过；
- APK：`artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-slice0.4-root-view-rebind-debug.apk`；
  9,539,676 bytes；SHA-256
  `298BD77BF7E8C6FF3A99D4EE7CDBFDA2CC3AC8D2882B60A03BBE7C72B126818E`；
- 设备状态：待验证；若 `trackedViews=0`，下一步应改为直接 hook action binder，不再扩展扫描。

Slice 0.4 设备结论：

- `lyrics-log-20260830-035707.txt` 记录点击后
  `Scanned current translation action views ... trackedViews=2`；
- 用户确认按钮按下后立即刷新，不再依赖播放/暂停；根视图 content-description 定位方案通过；
- 截图暴露新的纯视觉问题：直接替换的 24dp 翻译 vector 几乎填满 action 槽，明显大于
  上一首/播放/下一首图标。

Slice 0.5（翻译图标光学尺寸）本地证据：

- 截图视觉测量：翻译 glyph 线性尺寸约为播放三键的 1.7–2.0 倍，比投放图标约大 30%；
- 保持 action View、槽尺寸和点击区域不动，只对即时替换 drawable 使用四边 20% 的
  `InsetDrawable`；可见 glyph 缩为原来的 60%，目标接近其他媒体控件；
- enabled/disabled 255/135 alpha、content description、根视图定位逻辑均不变；
- 扩大 JVM 回归 56 类 394 项通过；main/test 编译、`assembleDebug`、APK v2 签名、
  16 KiB alignment 通过；
- APK：`artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-slice0.5-translation-icon-inset-debug.apk`；
  9,539,676 bytes；SHA-256
  `D0A2988383B386304BD40A1F80BB08B105FCD37BC31B7F34201BD6334CE6A9CF`；
- 设备状态：待验证图标尺寸；即时刷新无需重复证明，只需确认没有因 inset 回归。

Slice 0.5 设备结论与 0.5b 微调：

- 用户确认 60% glyph 已明显缩小，但视觉上再放大 5 个百分点更合适；
- 四边 inset 从 20% 调整为 17.5%，可见 glyph 从 60% 调整为 65%；action 槽、点击区域、
  enabled/disabled alpha 和即时刷新链均不变；
- 扩大 JVM 回归 56 类 394 项通过；main/test 编译、`assembleDebug`、APK v2 签名、
  16 KiB alignment 通过；
- APK：`artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-slice0.5b-translation-icon-65pct-debug.apk`；
  9,539,676 bytes；SHA-256
  `626DFFF2CAF35C10D813CE67EFE24B9B305235531AAFE5DDDBFED8293BDD7681`；
- 设备状态：用户确认 65% 光学尺寸完美；即时刷新、亮暗反馈、显示总开关均通过，
  但该结论只覆盖当时测试的 Salt 路径，后续跨播放器颜色回归使 slice 0 重新打开。

Slice 0.6（跨播放器 canonical 颜色）本地证据：

- Salt 路径验证后，用户测试其他播放器发现翻译按钮普遍变黑，slice 0 暂不关闭；
- 根因静态确认：`findTranslationIcon(context, playerPackage)` 会优先在播放器宿主包查找通用
  名称 `ic_translation`；不同宿主可能命中无关资源或不同主题 tint，导致图标形状/颜色漂移；
- 即时替换和 MediaAction model 统一改用 Bridge 包
  `io.github.andrealtb.lockscreenlyrics` 的 canonical translation vector，不再读取播放器宿主
  同名资源；
- canonical Drawable 显式 `Color.WHITE` tint，并栅格为带正确 density 的白色 Bitmap Icon，
  确保 `MediaAction` drawable、`OplusMediaActionEx` icon 与即时 View 换图使用同一颜色；
- 65% glyph、255/135 状态 alpha、根视图定位和点击区域保持不变；
- 扩大 JVM 回归 56 类 394 项通过；main/test 编译、`assembleDebug`、APK v2 签名、
  16 KiB alignment 通过；
- APK：`artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-slice0.6-canonical-white-icon-debug.apk`；
  9,539,676 bytes；SHA-256
  `E3CCB5104B8C7EC66FA4D2D5F3FA6173533587C3CDC2D61F6C9C774A91BCF2E4`；
- 设备状态：用户完成其他播放器回归并确认完美；canonical 白色、65% glyph、即时亮暗、
  显示总开关均通过。Slice 0 正式收尾，不再阻塞 slice A。

### A. 数据模型与迁移（已完成本地闭环）

- **已完成**：schema v3、Builder、codec、repository、Intent/Bundle canonical snapshot transfer；
- **已完成**：`LyricVisualAlphaPolicy` 纯 Java，统一解析六类 lane alpha 与 row fade 稳态叠乘；
- **已完成**：v1/v2/v3 round-trip、partial decode、约束、迁移与 exact alpha tests；
- **迁移合同**：v1/v2 的独立非实时翻译初值取迁移后的 `inactiveOpacityPercent`，row fade
  enable 严格取迁移后的 `scaleEnabled || blurEnabled`；v3 partial decode 保留缺失字段的
  baseline/dormant 值，不让 scale/blur 再反向改 row fade；
- **切片边界**：本刀没有让 renderer/UI 消费新字段，现有视觉仍走 Phase 6 常量；vertical
  fade 两字段在合同测试中显式登记为 slice B runtime deferral，而不是伪装成已接通。

Slice A 本地证据：

- main/test Java 编译通过；config/contract/alpha policy 定向 3 类 20 项通过；
- 排除已知依赖 Android runtime/JVM stub 的 8 类后，57 类 398 项扩大 JVM 回归通过；
- `assembleDebug`、APK v2 签名、16 KiB alignment 通过；
- APK：`artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-sliceA-schema-v3-debug.apk`；
  9,539,676 bytes；SHA-256
  `5E45B9F5AB0410C6139D10EFD3919F97A9E504B605DCE6C7EAD57D68183C4848`；
- 设备状态：待用户做基础启动/保存/重启 SystemUI 冒烟；由于 slice B 尚未开始，本 APK
  不提供新亮度 UI，也不应改变现有歌词画面。

### B. Renderer 消费配置（已完成本地闭环）

- **已完成**：删除 `LyricUiColors` 的固定 50/60/80 lane alpha，active、当前行未高亮、
  实时翻译、翻译进度、非实时正文和非实时翻译全部经 `LyricVisualAlphaPolicy` 解析；
- **已完成**：`LyricUiPalette` 增加独立 `inactiveTranslation`，renderer 的 active/inactive
  translation base 不再共用 `inactive`；
- **已完成**：Recycler 原生 vertical fading edge 直接应用 config enable/length；关闭时仍
  保留长度值，设置变更后立即 invalidate，不增加 TextView 内第二层 mask；
- **已完成**：row fade enable/percent 改为 config/policy，彻底取消 renderer 对
  `scaleEnabled || blurEnabled` 和固定 `0.9f` 的运行时依赖；旧用户的等价行为由 slice A
  migration 保证；
- **已完成**：移除 `WordLyricRenderConstants.OFFICIAL_LYRIC_INACTIVE_ROW_FADE` 和
  `LyricTimingTuningConstants.OfficialLyric.VERTICAL_FADING_EDGE_LENGTH_DP` 两个旧 owner；
- **精确 alpha 合同**：karaoke feather 继续保留原曲线 alpha，lane alpha 只由 Paint 施加
  一次，避免 active/translation opacity 与 shader 自乘；
- **基线**：fresh default 的 100/50/60/80/44、vertical fade 开 + 51.9dp、row fade 关与
  Phase 6 默认画面数值一致。设备截图/肉眼结论待用户冒烟。

Slice B 本地证据：

- main/test Java 编译通过；config/renderer contract/alpha/colors/palette/timing 定向 6 类
  34 项通过；
- 排除已知依赖 Android runtime/JVM stub 的 8 类后，57 类 400 项扩大 JVM 回归通过；
- `assembleDebug`、APK v2 签名、16 KiB alignment 通过；
- APK：
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-sliceB-renderer-visual-config-debug.apk`；
  9,539,676 bytes；SHA-256
  `DFBFB611F4251F88105A22020EDB785AD5B1520C4174CE4C87681BA21EDEFF53`；
- 设备状态：用户确认冒烟通过；默认画面、renderer 接入与既有滚动/渐隐链未见异常。

### C. 独立设置子页面与预览（已完成并设备闭环）

- **已完成**：新增 `LyricVisualLayersSettingsActivity`、Manifest 注册和主页面“颜色与光晕”
  卡片唯一入口；原“非活动行不透明度”滑块从主页面删除，迁入子页；
- **已完成**：11 个视觉字段全部进入子页 draft/read/bind/save，使用 canonical codec
  snapshot、`ACTION_STYLE_CHANGED`、独立 source、命名 `ResultReceiver`、revision 校验和
  SystemUI apply/reject/timeout 回显；
- **已完成**：active/unrevealed/translation/progress 约束在滑动时立即回绑；翻译跟随、
  vertical fade、row fade 的从属行按开关显示/隐藏，关闭后不丢 dormant value；
- **已完成**：非实时正文/翻译 value label 同时显示 base 与 policy 计算的稳态有效值；
- **已完成**：sticky preview 固定在控件滚动区上方，展示实时主歌词已唱/未唱分区、实时
  翻译进度样例、两组非实时正文/翻译、row fade 与上下 gradient mask；颜色与 alpha
  直接使用 `LyricUiPalette` / `LyricVisualAlphaPolicy`；
- **已完成**：所有 slider/switch/preview 均有 accessibility label；中英文资源同步；
- **已完成**：主页面返回时通过 owner policy 只合并视觉子页字段和翻译默认值，保留主页面
  未保存的颜色、光晕、模糊、缩放、字体、动效等草稿；summary 与主预览同步刷新；
- **切片边界**：预设仍留给 slice D 明确赋值和 detect；本刀不提前改变预设语义。

Slice C 本地证据：

- main/test Java 与中英文 Android resources 编译通过；config/owner/codec/alpha/colors/
  palette/UI contract 定向 7 类 34 项通过；
- 排除已知依赖 Android runtime/JVM stub 的 8 类后，58 类 404 项扩大 JVM 回归通过；
- UI contract 静态确认 Manifest/入口/唯一 owner、11 字段 read/bind、canonical apply ACK、
  shared preview policy 和双语 key 全部存在；
- `assembleDebug`、APK v2 签名、16 KiB alignment 通过；
- APK：
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-sliceC-visual-layers-settings-debug.apk`；
  9,555,876 bytes；SHA-256
  `EAE77EAC1A957258B989F6F983176623AF0EEB342F243BCA937AB003F69F94C6`；
- 设备首次回归：功能页面可进入，但用户确认 4 项 UI 问题——未保存提示是原生弹窗、预览
  三组歌词间距过宽、底部 dock 被内容区横向 padding 截短、slider 轨道过短。

Slice C.1（设备 UI 回归修正）本地证据：

- 未保存提示改用 `SettingsBaseActivity` 的设置主题圆角 dialog，按钮、颜色、dim 和宽度与
  主设置页一致，不再调用原生 `AlertDialog`；
- 预览高度从 224dp 收至 160dp，三组主歌词 baseline 间距从约 72/81dp 收至 40/41dp；
- 横向 screen padding 只留给 preview/scroll content，bottom action stage 恢复屏幕全宽，
  dock 自己处理导航栏/挖孔安全区；
- slider row 改为“标题+数值在上、MATCH_PARENT 轨道在下”，轨道从约半行宽扩到卡片可用
  全宽，同时保留 content description；
- 新增 UI polish contract；相关定向 3 类 10 项通过；扩大回归 58 类 405 项通过；
- main/test Java 编译、`assembleDebug`、APK v2 签名和 16 KiB alignment 通过；
- APK：
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-sliceC.1-visual-ui-polish-debug.apk`；
  9,555,876 bytes；SHA-256
  `63C72AE010AAA01C70B97FE22127967A12C679C8E27396ECBDF59A5A388DDBBF`；
- 设备状态：待用户复验上述 4 项，并继续完成 slider 即时生效、保存 ACK、持久化和 AOD
  功能矩阵。

Slice C.2（自然分组间距 + 整 dp 渐隐长度）证据：

- 用户截图确认 C.1 没有像素级文字重叠，但“上一组翻译 → 下一组正文”只剩很窄空隙，
  翻译视觉上更像属于下一组；专用视觉检查建议组内正文→翻译 3–5dp、组间翻译→下一
  正文 12–16dp，并把整组锚点从 40/41dp 回调到 56–60dp；
- 预览采用组内约 3–5dp、组间约 14dp 的两级节奏；三组主歌词 baseline 间距调整为
  56/58dp，预览高度调整为 196dp，避免重新回到最初 72/81dp 的松散状态；
- 渐隐长度 slider 从 0.1dp 改为 1dp step，label 改为整数 dp；既有 51.9dp/其他小数值
  在用户未触碰该 slider 时原样保留，首次主动拖动后才规范为整 dp，避免升级即产生
  0.1dp 配置漂移；
- 相关定向 3 类 10 项、扩大 JVM 回归 58 类 405 项通过；main/test Java 和双语 resources、
  `assembleDebug`、APK v2 签名、16 KiB alignment 均通过；
- APK：
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-sliceC.2-natural-preview-integer-fade-debug.apk`；
  9,555,876 bytes；SHA-256
  `E609820EFC2A68454F92DC4D18EB1B9F056DD1BF64EAFA0E55B98D23B2706DAF`；
- 设备状态：待用户确认自然间距和 1dp 拖动体验。

Slice C.3（预览文字块垂直居中）证据：

- 用户确认 C.2 三组内部/组间距离合适，但整组文字块偏上：上一行贴近上边缘，下一行
  距离下边缘更远；
- 保持 56/58dp 分组节奏与 196dp 预览高度完全不变，仅把统一 top anchor 从 8dp 下移到
  19dp；按当前字体可见 bounds 估算，上下留白由约 11/33dp 调整为约 22/22dp；
- UI contract 定向 5 项、扩大 JVM 回归 58 类 405 项、main/test Java、`assembleDebug`、
  APK v2 签名与 16 KiB alignment 均通过；
- APK：
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-sliceC.3-centered-preview-debug.apk`；
  9,555,876 bytes；SHA-256
  `3F7EE173CD8FE6D1821C6546C6907AB1B8079FDE9F1710EF02DF60F65409B24B`；
- 设备状态：用户确认上下视觉居中，C.3 通过。

Slice C 最终设备闭环（2026-08-30）：

- 用户确认 Slice C 冒烟全部通过，歌词亮度与渐隐页面布局、主题弹窗、全宽 dock、长滑条、
  自然分组间距、预览文字块上下居中和整 dp 渐隐长度交互均正常；
- active、当前行未高亮、实时翻译、翻译进度、非实时正文、非实时翻译跟随/独立、
  vertical fade enable/length、inactive row fade enable/percent 等已开放配置均可正常保存并
  在实际歌词中正确生效；
- 未见新的设置脱钩或视觉异常。Slice C 正式关闭，不再阻塞 Slice D。

### D. 预设、文档与回归（已完成并设备闭环）

- **已完成**：Default/Soft/Vivid/Minimal 显式写入全部视觉字段；四套都保持
  100/50/60/80、翻译跟随正文和 vertical fade 开 + 51.9dp，非实时正文/翻译分别为
  44/36/44/55%；
- **无漂移合同**：Soft/Vivid 显式启用 row fade 90%，Default/Minimal 显式关闭并保留 90%，
  与旧 preset 的 `scaleEnabled || blurEnabled` 像素语义一致；
- **已完成**：preset detect 把 active/unrevealed/translation/progress、非实时翻译跟随与
  dormant value、vertical fade enable/length、row fade enable/percent 全部纳入匹配；任一
  偏离即 Custom；从视觉子页返回主页面会立即刷新 preset card/summary；
- **已完成**：主页面预设卡增加中英文语义说明，修正英文 README 的 Bold/Vivid 命名漂移；
- **已完成并修正 owner**：全局配置维护不放在视觉子页。主设置页增加独立
  `BridgeConfigBackupSettingsActivity`，覆盖 `lockscreen_lyrics` 与
  `lockscreen_lyrics_debug` 两个完整配置域；
- **已完成**：`Bridge Backup v1` 使用带类型和命名空间的文本格式，支持 boolean/int/long/
  float/string/string-set；恢复前完整解码和域校验，写入失败回滚；全量重置有二次确认；
- **已完成**：恢复/重置后重放 style、player translation、opening cleanup、debug 四条广播
  同步 SystemUI；若实时同步不完整，保留已恢复配置并明确提示重启 SystemUI；
- **已完成**：英文/中文 README 与 `docs/4.0/LYRIC-VISUAL-CONTROLS.md` 记录默认值、预设
  矩阵、1dp UI/历史小数保留、v1/v2 迁移、导出/重置和“schema v3 不承诺无损降级”边界。

Slice D 初始候选本地证据（已由 D.1 替代）：

- main/test Java 与中英文 Android resources 编译通过；preset/export/reset/UI/docs/config
  定向 7 类 33 项通过；
- 排除已知依赖 Android runtime/JVM stub 的 8 类后，61 类 410 项扩大 JVM 回归通过；
- `assembleDebug`、APK v2 签名和 16 KiB alignment 通过；
- APK：
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-sliceD-presets-docs-final-regression-debug.apk`；
  9,563,256 bytes；SHA-256
  `B94DC85F613C9B67ACB4B54D93B1DC4738A1C8C79C8A9297EAA164D906E087BC`；
- 设备前审查发现 owner 错误：把“完整配置”和“本页重置”放进歌词亮度子页，且只导出
  `LyricUiConfig`，不能称为完整 Bridge 备份。该候选未设备验收，不作为最终 Slice D APK。

Slice D.1（全局 Bridge 配置备份边界修正）本地证据：

- 视觉子页已彻底移除配置维护入口；主设置页“兼容与屏幕”增加独立全局备份/恢复入口；
- 源码扫描确认 Bridge App 只有 `lockscreen_lyrics` 与 `lockscreen_lyrics_debug` 两个配置域：
  前者覆盖主 UI、全局/逐播放器翻译、开头清理、学习规则/逐曲修正和设置语言，后者覆盖
  debug master/area/revision；两者均进入备份；
- restore/reset 完成后会刷新主设置 Activity，并向 SystemUI 重放 style/player translation/
  cleanup/debug；备份文本不记录日志、歌词正文或 SystemUI 缓存；
- backup codec/全局 owner/runtime sync/UI/docs/preset 定向 5 类 20 项通过；排除已知依赖
  Android runtime/JVM stub 的 8 类后，61 类 414 项扩大 JVM 回归通过；
- main/test Java、中英文 resources、`assembleDebug`、APK v2 签名和 16 KiB alignment 通过；
- APK：
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-sliceD.1-global-bridge-backup-debug.apk`；
  8,511,814 bytes；SHA-256
  `CA277D8F08F98E5808D81C626E9F1CF48C9B4131FB1325E3B0BFB5294978D371`；
- 设备状态：用户确认 Slice D 冒烟通过。完整备份实机输出包含正确的
  `ColorOS-Live-Lyrics-Bridge Backup v1` header、`lockscreen_lyrics` 与
  `lockscreen_lyrics_debug` 两个配置域，以及受支持的 B/I/L/S typed entries；四套预设、
  Custom 检测和全局配置维护未见异常。

Slice D 最终设备闭环（2026-08-30）：

- 用户确认 D.1 为最终验收版本，初始 D 候选保持“被替代、未验收”状态；
- Slice D 预设、Custom 检测、双语文档、全局备份/恢复/重置页面与 SystemUI 同步链通过
  冒烟；备份内容未发现缺域、类型错误、凭据或歌词正文泄露；
- Slice 0、A、B、C、D 全部关闭。Phase 6 后“歌词亮度层级与边缘渐隐全面开放计划”
  正式完成，不再保留待实施 slice。

## 9. 可继续开放的硬编码参数盘点

本轮只把与“亮度层级/Recycler 边缘渐隐”直接相关且能保持默认行为的参数纳入 schema v3。
其余参数记录下来，不在同一刀顺手改。

| 参数/现值 | 影响 | 决策 |
|---|---|---|
| active/unrevealed/translation/progress 100/50/60/80% | 歌词层级 | v3 开放 |
| inactive translation 跟随 44% | 非实时翻译层级 | v3 开放跟随/独立 |
| vertical fade enabled + 51.9dp | Recycler 上下边缘 | v3 开放 |
| row fade 90% + 旧的 scale/blur 条件 | 非活动整行最终亮度 | v3 开放 enable/percent，并做无漂移迁移 |
| 无翻译布局字号额外 `+2sp`（最大 30sp） | “主歌词字号”实际值会因有无翻译变化 | 下一轮可开放“无翻译字号增益”，或先把 UI 文案写清楚；不塞入本轮 |
| 主歌词与翻译固定 2dp 间距 | 翻译组紧凑度 | 下一轮低风险候选 |
| karaoke feather 0.58、glow feather 0.74 | 逐字进度边缘软硬 | 下一轮高级候选，需要长行/换行/GPU 对比 |
| settled glow radius 1.22、alpha 0.88 | 光晕实际半径/亮度 | 已有强度/半径控件，首轮不再暴露二级乘数，避免重复 owner |
| translation marquee 8%–82%、passive pan 曲线 | 长文本运动区间 | 属于动效调参，不是亮度；另开 motion advanced 计划 |
| model reveal 76%/260ms、translation toggle 320ms | 切歌/翻译过渡 | 时序敏感，保持内部常量 |
| active center/shift、slot 80/56/12/1dp、最多两行 | Recycler 几何与 AOD ownership | 不开放；可能破坏官方滚动和高度合同 |
| handoff hidden alpha/420ms、AOD 180ms 等 | 防闪屏与低帧率时序 | 不开放；属于设备证据常量 |
| fading-edge strength | 原生边缘强度 | 首版不开放，避免 hook/双重 mask |
| `PLAYED_ALPHA=0xF0` / `playedPaint` | 静态扫描未发现实际 draw 消费 | 不开放；实现前先删除死状态或给出明确视觉语义 |

## 10. 验证矩阵

### 本地

- v5 pass-through：翻译来源声明不再被 cleanup、normalizer、LyricsCore 或 assembler 静默删除；
- 删除内容黑名单后，明确的 `translationLyric`、罗马音排除和相同文本去重保持不变；
- 现有 25 个 v2 字段的 UI → codec → repository → Intent → consumer 合同；
- translation button 当前动作栏即时刷新策略；
- 子页面修改全局翻译默认后，主页面不产生虚假 dirty，也不覆盖未保存视觉草稿；
- 常亮秒数和不可用刷新率的规范回显/保留；
- config clamp/constraint；
- v1/v2 → v3 migration；
- v3 codec/repository/Intent round-trip；
- palette 精确 ARGB；
- 跟随与独立翻译；
- preset detect/reset；
- UI draft/bind/apply 与 preview policy。

### 设备

- v5 pass-through APK：12 个已定版 Provider 至少抽查官方追加型与构造型，确认歌词列表、
  主/翻译 lane、逐字进度、无歌词返回和切歌均无回归；
- 若出现来源声明/版权等内容，先保存原始 `lyricInfo` 与 SystemUI 消费结果，不在 Bridge 热补丁；
- 设置审计回归：当前播放不变时切换翻译按钮应立即生效；
- 设置审计回归：子页返回、超范围常亮秒数、不可用刷新率不出现假回显/静默覆盖；
- 默认值与 Phase 6 最终 APK并排对照，无视觉漂移；
- 互动锁屏：逐项最小/默认/最大；
- 翻译开关与翻译进度开关组合；
- 普通行、长行、重复副歌、顶部/底部边缘；
- AOD 进入/退出与低帧率；
- SystemUI 重启后配置持久化；
- 边缘 fade 关闭、短长度、官方 51.9dp、最大长度，确认没有双重渐隐。

## 11. 完成标准

- 所有表中字段均可在 UI 修改并实时预览；
- 默认值精确保持 100/50/60/80/44/51.9dp；row fade fresh default 关闭、值保留 90%；
- v1/v2 用户配置升级后旧字段不变；
- v1/v2 的 row fade enable 按旧 scale/blur 状态迁移，升级前后最终像素不变；
- untouched defaults 的 SystemUI 截图与 Phase 6 基线无可见差异；
- 2.2 中 P0/P1 项关闭，P2 至少有确定且可测试的兼容策略；
- 无新增逐帧对象、字符串解析或 SharedPreferences 读取；
- 设备矩阵通过后才从“计划”改为“已实现”。

## 12. 非目标

- Phase 6 期间不实施本计划；
- 本计划不重新修改已经定版的 v5 Provider；pass-through 发现的 Provider 问题另立证据单；
- 首版不开放 fading-edge strength；
- 不做播放器/Provider 独立视觉配置；
- 不修改歌词 timing、解析、Recycler 跟随、AOD ownership 或屏幕常亮策略。

## 13. 关闭后的缺陷修复

### 13.1 居中/右对齐歌词缩放仍沿左缘（首个候选设备失败）

- 现象：启用非活动行缩放后，歌词正文虽然按居中/右对齐绘制，但行缩放仍像左对齐一样
  从左缘收缩；
- 静态根因：Bridge 的 `horizontalScalePivot` 已正确返回 start/center/end pivot，但
  `isLyricsRecyclerScalePivotMethod` 只识别 OPlus obfuscated `m(TextView)`。已保存的官方
  SystemUIPlugin user 样本
  `PlayerSource/SystemUIPlugin/jadx-user/sources/com/oplus/systemui/plugins/shared/template/component/media/view/LyricsRecyclerView.java:287`
  证明同一 pivot 准备方法
  在另一验证变体中名为 `k(AppCompatTextView)`；该方法会依据插件仍为左对齐的私有字段把
  `pivotX` 重置为 0，Bridge 未 hook 时每次缩放事务都会覆盖正确 pivot；
- 修复：pivot hook 名称策略同时识别已验证的 `m` / `k`，仍要求 `void` 返回、单一
  `TextView` 子类参数；hook 在 vendor 方法执行后重申 Bridge alignment pivot。attach post、
  layout change listener 和当前 bound row 同步兜底保持不变；
- 精确合同：LTR 下 start/center/end 分别取内容左缘、中心、右缘；RTL 下 start/end 交换；
- 定向 `LyricUiLayoutPolicyTest` 等 3 类 35 项通过；排除已知 Android/JVM stub 依赖的 8 类
  后，61 类 414 项扩大 JVM 回归通过；main/test Java、`assembleDebug`、APK v2 签名和
  16 KiB alignment 通过；
- APK：
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-bugfix-alignment-scale-pivot-debug.apk`；
  8,880,121 bytes；SHA-256
  `AC5423777706D83484213BE6932C4BAEC85F929BDC9946E5C7E9F7492EB7590B`；
- 设备状态：失败。`logs/lyrics-log-20260830-074737.txt:3-7` 证明 `alignment=1` 已正确到达
  renderer/SystemUI，但插件报告 `No LyricsRecyclerView current lyric hook target found`；用户
  肉眼确认居中/右对齐仍沿左侧拉扯。结论：当前 u/v/C 插件的方法混淆名不属于假设的
  `m/k`，按名称补丁不能作为最终修复。

### 13.2 按方法形状覆盖 pivot 重置（已设备闭环）

- 修复边界：不再依赖任意 obfuscated method name；在已确认的
  `LyricsRecyclerView` 类内，hook 所有 `void(single TextView subtype)` 方法，并在 vendor
  方法返回后幂等恢复 Bridge alignment pivot；该形状只可能接收歌词行 TextView，对普通
  style/bind helper 重申 pivot 也无副作用；
- hook 安装日志改为同时输出 `currentMethods=N, pivotMethods=N`。即使当前歌词更新方法未命中，
  也能独立确认 pivot hook 是否覆盖实际插件；
- attach post、layout-change listener、bound-row pivot/scale 同步三层兜底不变；
- 新增 source contract，直接锁定官方 `k(AppCompatTextView)` + `setPivotX(0.0f)` 反编译证据
  和 name-independent 形状策略；布局/配置/预设定向 4 类 36 项通过；
- 排除已知 Android/JVM stub 依赖的 8 类后，62 类 416 项扩大 JVM 回归通过；main/test Java、
  `assembleDebug`、APK v2 签名和 16 KiB alignment 通过；
- APK：
  `artifacts/ColorOS-Live-Lyrics-Bridge-3.8.1-post-phase6-bugfix-alignment-scale-pivot-shape-hook-debug.apk`；
  8,880,066 bytes；SHA-256
  `C8F5632C0C69124C0311D40790EC0EE680859FF264C4C5514F08F8A4203999F8`；
- 设备闭环：`logs/lyrics-log-20260830-075526.txt:5` 记录
  `currentMethods=0, pivotMethods=1`，证明 name-independent shape hook 命中当前 u/v/C 插件；
  同日志先以 `alignment=1` 启动，随后在 `:559-561` 成功切换到 `alignment=2` 并收到
  `applied=true` ACK。用户肉眼确认居中与右对齐缩放均恢复正常；
- `currentMethods=0` 只表示该插件的 current-lyric 私有更新方法仍由现有 fallback 处理，
  与本次独立命中的 pivot hook 无冲突。该缺陷正式关闭。
