# Bridge TTML 歌词与对唱（分侧对齐）适配方案

- 日期：2026-11-17
- 基线：Bridge `v5.0.0`（versionCode 150，`main` @ `1e0d703`）；Providers 矩阵 `suiteVersion 4.5.4`
- 状态：**P0 取证完成；P1 的 Bridge 侧与 P2 分侧渲染已实现（本地单测与 debug 构建通过，见附录 B、C）；P1 的 Provider 侧、P3 未实现，**P2 尚无任何设备验证**。P1 的传输语义相对 §4.1 / §5.2 有调整，以附录 B.1 为准。§2 的“现状”为静态确认；lyrics-core 的 TTML 行为见附录 A，由 `LyricsCoreTtmlCharacterizationTest` 固化。尚无设备验证。
- **附录 A 的结论优先于正文**：正文 §4、§5.3、§7 中与附录 A.2 冲突的假设，以 A.2 的修正为准。
- 目标版本：5.0.0 之后的下一个功能版本（版本号以发布契约为准，本文不预设）。

## 1. 结论摘要

1. Bridge 已经内置能完整解析 TTML 的库（`lyrics-core-jvm:0.4.7`，含 `TTMLParser`、`KaraokeAlignment`、伴唱行、音译），但**两头都被截断**：上游 Provider 把 TTML 压平成增强 LRC 时丢掉了演唱者、背景人声和内嵌翻译；下游 `LyricsCoreAdapter` 只取文本、时间和翻译，丢掉对齐与伴唱。
2. 推荐做法：在 `lyricInfo` JSON 上**新增一个可选字段 `ttml`**（原始或规范化后的 TTML 文档），旧字段 `lyric` / `rawLyric` / `translationLyric` 原样保留。新 Bridge 优先解析 `ttml`，旧 Bridge 和官方 SystemUI 忽略未知字段，行为不变。
3. 对唱用“行级对齐覆盖”实现：每行带一个 `side`（主唱侧 / 对唱侧），渲染时把现有全局 `LyricUiConfig.alignment` 换成“该行的有效对齐”。主唱沿用用户设置的对齐，对唱者镜像到另一侧。
4. 官方列表行数不变：背景人声不单独成行，挂在主行下面；因此不触碰 Recycler 行归属、active index 和 AOD 时序。
5. 分四期：P0 取证 → P1 TTML 接入（无视觉变化）→ P2 对唱分侧 → P3 背景人声（可独立延期）。

## 2. 现状与差距（静态确认）

### 2.1 Bridge 侧

| 环节 | 现状 | 位置 |
| --- | --- | --- |
| 入口契约 | 只认 `lyric` / `rawLyric` / 翻译键；用 `[mm:ss]`、`<mm:ss>` 正则判定“有时间轴”。TTML 的 `begin="…"` 不匹配该正则，直接塞进 `rawLyric` 会被当成无逐字数据 | `LyricInfoContract.java:36`、`:48-79` |
| 解析 | `AutoParser` 能识别 TTML，但进入它之前先做了 LRC 方括号归一化，并以纯 LRC 兜底结果做行数 / 双语校验，这些都按 LRC 假设写的 | `LyricsCoreAdapter.java:59-101` |
| 模型映射 | `toParsedLine` 只读 `syllables` / `translation` / 起止时间；`getAlignment()`、`getAccompanimentLines()`、`getPhonetic()` 全部丢弃 | `LyricsCoreAdapter.java:544-588` |
| 行模型 | `WordLine` 没有演唱者、对齐或伴唱字段 | `render/WordLine.java` |
| 对齐 | 全局三态 START / CENTER / END，绘制 x、缩放 pivot 都直接读 `uiConfig.alignment` | `LyricUiConfig.java:16-18`、`OfficialLyricTextRenderer.java:3556-3616`、`LyricsRecyclerViews.java:455-490`、`LyricUiLayoutPolicy.java:86-99` |

依赖库能力（反查 `lyrics-core-jvm-0.4.7.jar`）：`TTMLParser` 含 `ttm:agent` / `parseAgentTypes`、`parseAccompaniment`（背景人声及其翻译）、`parseITunesTranslations`、`parseITunesTransliterations`；`KaraokeAlignment` 为 `Start / End / Unspecified`；`MainKaraokeLine.getAccompanimentLines()` 返回伴唱行。具体的 agent → 对齐映射规则只看到了符号名，**行为需 P0 用单测确认**。

### 2.2 Provider 侧（Bridge 的上游依赖）

| 来源 | 现状 | 位置 |
| --- | --- | --- |
| `parser-ttml` | 跳过 `x-bg` / `x-translation` / `x-roman` span，不读 `ttm:agent`，输出只有平铺的 LRC 与 `RichLyricLine` | `TtmlParser.kt:74`、`:260` |
| Metrolist | TTML → `TtmlParser` → 增强 LRC，`translationLyric` 固定为空 | `MetrolistLyricDecoder.kt:65-80` |
| 通用 Provider（BetterLyrics / Paxsenix） | 用正则把 TTML 抽成 LRC，结构信息全丢 | `BetterLyricsSource.kt:47`、`PaxsenixSource.kt:16-22` |
| Apple Music | 走宿主 native `SongInfo` → `AppleSongMapper.toRichLines`，手里不一定有 TTML 原文 | `ApplePlayerHooker.kt:540-570` |
| 公共编码器 | `RichLyricLine.isAlignedRight` 已存在（增强 LRC 的 `v1:` / `bg` 会置位），但 `ColorOSLyricJsonEncoder` 从不输出它 | `RichLyricLine.kt:18`、`EnhanceLrcParser.kt:97-98`、`ColorOSLyricJsonEncoder.kt:28-65` |

结论：对唱信息在 Provider 解析阶段就没了，Bridge 单独改不出效果。本方案以 Bridge 为主，但 P1 需要 Provider 配套透传（见 §7）。

## 3. 设计约束

- 链路不变：`Provider → MediaMetadata["lyricInfo"] → SystemUI → Bridge`。只在既有 JSON 上加可选字段，与 `rawLyric`、`displayMode: sentence-window-v1` 同属一类扩展；不引入私有广播、envelope 或 Bridge↔Provider 直连。
- Bridge 不联网、不取词、不认播放器包名；TTML 解析对所有来源一视同仁。
- 官方列表行归属不动：`lyric`（普通 LRC）仍决定 RecyclerView 行数与顺序，Bridge 只在已归属的行内改绘制。
- 罗马音 / 音译不进翻译 lane。
- 逐行 TTML 不伪造逐字时间轴；首词之前不伪造 karaoke fill。
- 任一步失败都退回 5.0 行为（`rawLyric` 路径），SystemUI 不崩。

## 4. 传输契约：`lyricInfo.ttml`

### 4.1 字段

```json
{
  "lyric": "[00:12.000]…",            // 必填，行为不变，决定官方行
  "rawLyric": "[00:12.000]<00:12.000>…", // 建议保留，旧 Bridge 仍有逐字
  "translationLyric": "…",            // 可选，行为不变
  "ttml": "<tt …>…</tt>",             // 新增，可选
  "ttmlSchema": 1                      // 新增，可选，缺省按 1
}
```

规则：

- `ttml` 存在且解析成功、并通过与 `lyric` 的行对齐校验（§5.2）时，Bridge 以它为逐字 / 翻译 / 演唱者 / 伴唱的唯一来源；否则整体回退到 `rawLyric` + `translationLyric`，不做半合并。
- Provider 必须保证 `lyric` 的每一行与 TTML 的每个主 `<p>` 一一对应（同起始时间、同文本），背景人声不进 `lyric`。
- 体积上限：Provider 发布前做规范化（去掉 `<head>` 里与显示无关的 metadata、songwriter、多余空白），规范化后超过上限则不带 `ttml`。上限数值在 P0 根据实测确定，暂定 **200 KB** 作为起点；MediaMetadata 与封面 bitmap 共用 binder 事务额度，这一项**需要设备验证**。
- 没有 TTML 原文但有结构化数据的 Provider（Apple、增强 LRC 的 `v1:` / `v2:`）用 provider-core 里新增的最小 TTML 写出器生成同一子集，Bridge 只维护一条解析路径。

### 4.2 Bridge 认的 TTML 子集（schema 1）

| 元素 / 属性 | 用途 |
| --- | --- |
| `<p begin end ttm:agent>` | 一行；`ttm:agent` 指向 `<head>` 中的 `<ttm:agent xml:id type>` |
| `<span begin end>` | 逐字 / 逐音节；没有 span 的 `<p>` 是逐行 |
| `<span ttm:role="x-bg">` | 背景人声，挂到所属主行 |
| `<span ttm:role="x-translation">`、iTunes `<translations>` | 翻译 lane |
| `<span ttm:role="x-roman">`、iTunes `<transliterations>` | 解析但不显示（不进翻译 lane），为后续注音功能预留 |
| `<audio lyricOffset>` | 全局偏移（Provider 现有行为，P0 确认 lyrics-core 是否处理，不处理则由 Provider 规范化时折算进时间戳） |

### 4.3 为什么不选其它方案

| 备选 | 不选的原因 |
| --- | --- |
| 把 TTML 直接放进 `rawLyric` | 旧 Bridge 的时间轴正则不匹配，会丢逐字，等于对旧版本降级 |
| 在 `rawLyric` 行首加 `v1:` / `v2:` / `[bg:]` | 旧 Bridge 会把前缀当歌词文本显示出来 |
| 新增紧凑的逐行属性数组（`lineAttrs`） | 体积小，但等于自造一种格式，伴唱 / 内嵌翻译还得继续加字段；与“完整适配 TTML”目标不符。保留为体积超限时的后备思路，本期不做 |

## 5. Bridge 改动设计

### 5.1 契约与解析

- `LyricInfoContract`：新增 `JSON_TTML`、`JSON_TTML_SCHEMA`；`Payload` 增加 `ttml`；`hasModuleExtensionData()` 把 `ttml` 计入。`normalizeOfficialLyricInfo` 不改写 `ttml`。
- `LyricsCoreAdapter`：新增 `parseTtml(String)`，直接调用 `TTMLParser`，**不经过** LRC 归一化、同时间戳合并、纯 LRC 兜底对比这些 LRC 专用步骤。在 `synchronized` 内调用的约定保持。
- `ParsedLine` 增加：`side`（`PRIMARY` / `SECONDARY` / `UNSPECIFIED`，来自 `getAlignment()`）、`accompaniment`（伴唱行列表，各自含音节与翻译）、`phonetic`（仅保存）。
- 解析在现有 lyricInfo 处理线程上做，结果随现有模型缓存；同一 `trackKey + sessionGeneration + ttml 哈希` 不重复解析。SystemUI 进程内的解析耗时与内存在 P0 量化，记入 `PHASE-6-PERFORMANCE-LEDGER`。

### 5.2 模型组装与行对齐校验

- `NativeLyricModelAssembler`：入口处若 `payload.ttml` 可用，走新分支生成 `WordLyricModel`（`parserName = "lyrics-core-ttml"`），其余下游（官方行别名、展示清理、翻译传播）复用。
- 校验：TTML 主行数与 `lyric` 行数一致，且逐行起始时间差在容差内（建议 ±50 ms）、归一化文本相同。不通过即整体回退并打一条节流日志（只记行数、首个不一致的行号与原因，不记歌词文本）。
- `WordLine` 增加 `side` 与 `accompaniment`；`WordLyricModel` 增加 `agentCount` / `hasDuet`，供渲染判断“这首歌是否需要分侧”。

### 5.3 对唱：行级有效对齐

新增纯函数（放 `LyricUiLayoutPolicy`，便于单测）：

```
effectiveAlignment(userAlignment, duetMode, modelHasDuet, lineSide) -> START | CENTER | END
```

| 用户对齐 | 这首歌有对唱 | 主唱侧行 | 对唱侧行 |
| --- | --- | --- | --- |
| START | 是 | START | END |
| END | 是 | END | START |
| CENTER | 是 | START（已定，见 §9 决策 1） | END |
| 任意 | 否 | 用户对齐 | — |

- 只有一个演唱者、或所有行都是 `Unspecified` 的歌，行为与 5.0 完全一致。
- 合唱（`type="group"`）跟主唱侧；超过两位独唱者时，第一位出现的是主唱侧，其余都在对唱侧（与 lyrics-core 的 Start / End 二分一致，P0 确认）。
- RTL 沿用现有 START / END 的逻辑镜像，不另写分支。

需要从“读全局对齐”改成“读该行有效对齐”的调用点：

| 调用点 | 说明 |
| --- | --- |
| `OfficialLyricTextRenderer.resolveTextX`（两处重载，`:3556`、`:3586`） | 主文本与翻译的绘制 x；光学居中只在有效对齐为 CENTER 时启用 |
| `LyricsRecyclerViews.applyOfficialLyricScalePivot`（`:455`） | 行缩放 pivot 必须和文字贴的那一侧一致，否则激活放大时对唱侧会向屏幕外长 |
| 超宽行溢出策略（`resolveTextX` 前半段） | 仍按逻辑起始侧溢出，不随 `side` 变，避免长行被裁掉开头 |
| 被动行横移、字符上浮（`CharLiftGeometry`）、辉光 / 羽化遮罩 | 这些以绘制 x 为基准，理论上跟随即可，P2 逐项目视回归 |
| `SettingsPreviewView` | 预览加一组对唱示例行 |

折行处理：对唱侧的多行文本每一视觉行都贴对唱侧（右侧齐、左侧参差），不做两端对齐。布局缓存（`rendererLayout*`、`slotHeight*`）只与宽度和字号有关，x 在绘制期决定，预计不需要改 key，实现时核对。

### 5.4 设置项

- `LyricUiConfig` 新增 `duetLayoutMode`：`0 = 分侧显示（默认）`、`1 = 关闭`。`LyricUiConfigCodec` 新增键，缺省值保证老配置解码后行为等同“分侧显示”，而没有对唱信息的歌不受影响。
- `LyricUiSettingsActivity` 在“对齐”项下加一个开关和一句说明；`LyricUiPreset` 的默认外观判定把新字段计入。
- 这是一次明确的 `LyricUiConfig` schema 变更，随本任务做，不夹带其它字段。

### 5.5 背景人声（P3）

- 不新增官方行。伴唱行作为主行下方的小字号附属行绘制，处理方式比照翻译行：进入 slot 高度计算，有独立的逐字进度（按伴唱自己的音节时间）。
- 伴唱与翻译同时存在时的堆叠顺序：主行 → 伴唱 → 翻译（伴唱翻译并入翻译行，P3 再定）。
- 伴唱跟随所属主行的 `side`。
- 这一期会改 slot 高度，属于几何变更，需要按 Bridge AGENTS 的要求限定在歌词区域并与已知良好设备窗口对比；因此单独成期，可延期而不阻塞 P1 / P2。

### 5.6 降级与诊断

- 新增 feature 级降级点 `TTML_PARSE`、`DUET_LAYOUT`，接入 C-04 已有的降级 / 恢复机制：解析异常或校验失败 → 本曲回退 `rawLyric`；渲染侧异常 → 本进程生命周期内关闭分侧。
- 日志沿用 `[CLL] level=… component=bridge area=… event=…`，新增事件只带：解析器名、行数、演唱者数、是否有伴唱 / 翻译、TTML 字节数区间、回退原因。不记歌词文本与原始 media ID。
- debug 开关不改变是否解析 / 是否分侧。

## 6. 已知限制（本方案不解决）

- **同时演唱的重叠行**：官方列表同一时刻只有一个 active 行。两位歌手时间重叠的行会按 `lyric` 的顺序依次激活，不会同时高亮。要做到同时高亮得改 active index 语义，超出本期范围。
- 音译 / 注音只解析不显示。
- `sentence-window-v1` 显示模式下不启用 `ttml`（该模式的行是事件槽位而非时间轴），保持现状。
- 酷我的 SystemUI 兼容路径（`KuWoSystemUiCompat`）不在本期验证范围内；酷我没有 TTML 来源，预期不受影响，P2 回归时确认。

## 7. Provider 配套（依赖项，另仓实施）

| 项 | 内容 |
| --- | --- |
| provider-core | `ColorOSLyricJsonEncoder.encode` 增加可选 `ttml` 入参与体积闸门；新增规范化器与最小 TTML 写出器 |
| parser-ttml | 保留现有 LRC 输出用于 `lyric` / `rawLyric`；额外产出规范化 TTML；确保 `lyric` 行与主 `<p>` 一一对应（现在按 `startMs` 排序、跳过空行，规范化时要用同一套规则） |
| Metrolist | `decodeTtml` 透传规范化 TTML |
| 通用 Provider | BetterLyrics / Paxsenix 改用 `parser-ttml` 取代正则抽取，并透传 |
| Apple | 先调查 native `SongInfo` 是否暴露演唱者 / 背景人声；有则经写出器生成 TTML，没有则维持现状 |
| 增强 LRC 来源 | `isAlignedRight` 已有数据的来源经写出器输出 `ttm:agent`，顺带获得对唱 |

身份、generation、异步结果绑定等规则不变：`ttml` 与其它歌词字段同一次发布、同一 identity + generation。“所有 Provider”的范围以 `v5-provider-matrix.json` 为准，本表只列有 TTML 或对唱数据的来源。

## 8. 分期与验收

| 期 | 内容 | 验证 |
| --- | --- | --- |
| **P0 取证** | 用真实样本（Apple 逐字对唱、BetterLyrics、带 `x-bg` / 内嵌翻译 / iTunes 翻译、逐行 TTML、带 `lyricOffset`）写 JVM 单测，确认 lyrics-core 0.4.7 的 agent → 对齐规则、伴唱、偏移、容错；量化解析耗时与体积分布；确定体积上限 | 本地单测；结论写入本文件附录 |
| **P1 TTML 接入** | §4 契约、§5.1、§5.2、§5.6；Provider 侧 Metrolist 或通用 Provider 先接一个 | `:app:testDebugUnitTest :app:assembleDebug`；设备：有 `ttml` 的歌逐字 / 翻译与 5.0 一致，无视觉回归；大 TTML 不丢 metadata、不丢封面 |
| **P2 对唱分侧** | §5.3、§5.4 | 单测覆盖 `effectiveAlignment` 全表与 RTL；设备：对唱歌曲分侧、激活缩放方向正确、长行不裁切、锁屏 / AOD / 沉浸式各看一遍；非对唱歌曲与 5.0 截图对比无差异 |
| **P3 背景人声** | §5.5 | 单测覆盖 slot 高度；设备：带伴唱歌曲行高稳定、切行不跳动 |

发布门禁、RC 设备确认、Release Notes 按 `docs/RELEASE_PROCESS.md`。P1 单独发布没有用户可见变化，建议 P1 + P2 合并为一个版本，P3 视设备结果决定是否同版。

## 9. 待你决定

1. **用户对齐为“居中”时遇到对唱歌曲**：**已定（2026-11-17）——自动改为左右分侧**，主唱侧 START、对唱侧 END；非对唱歌曲仍居中。
2. **P3 背景人声是否纳入同一版本**：推荐先发 P1 + P2，P3 看设备上的行高表现再定。
3. **首个接入的 Provider**：推荐通用 Provider（BetterLyrics TTML 样本容易取得，且不依赖宿主逆向）；Apple 的对唱数据要先做一轮调查。

## 10. 待验证清单

| 项 | 性质 |
| --- | --- |
| lyrics-core 0.4.7 的 agent → Start / End 规则、`lyricOffset`、畸形 XML 容错 | **已完成**，见附录 A |
| SystemUI 进程内解析大 TTML 的耗时与内存 | 桌面 JVM 已量化（附录 A.3）；设备上仍需验证 |
| 用真实播放器 TTML 样本复核附录 A（目前样本为手写） | 需要你提供或授权抓取样本 |
| 带 `ttml` 的 MediaMetadata 在 binder 上的体积余量（含封面） | 需要设备验证 |
| 官方 SystemUI 对未知大字段的容忍度（`rawLyric` 已是先例，但体积更大） | 需要设备验证 |
| Apple native `SongInfo` 是否含演唱者 / 背景人声 | 需要逆向调查 |
| 分侧后字符上浮、辉光、被动行横移的观感 | 需要设备验证，最终外观由你确认 |

## 附录 A：P0 取证结论（2026-11-17）

证据：`app/src/test/java/io/github/andrealtb/lockscreenlyrics/LyricsCoreTtmlCharacterizationTest.java`（8 个用例，本地 `:app:testDebugUnitTest` 通过），样本在 `app/src/test/resources/fixtures/ttml/`。分侧规则另反查了 `TTMLParser.computeLineAlignments` 的字节码。

**样本是按 Apple Music 与 AMLL / BetterLyrics 的常见写法手写的，不是从播放器抓取的真实 payload。** 真实样本可能有本表没覆盖的写法。

### A.1 lyrics-core 0.4.7 的实际行为

| 项 | 行为 |
| --- | --- |
| 分侧规则 | 不是“每位歌手固定一侧”，而是**按时间顺序、换人就换边**。首行的边由 agent id 末尾数字的奇偶决定（奇数 Start、偶数 End、无数字按 Start）。`group` 恒为 Start 且不影响状态；没有 `ttm:agent` 的行沿用当前边 |
| 两人对唱 | `v1` 恒 Start、`v2` 恒 End，稳定 |
| 三人及以上 | 同一位歌手可能先后出现在两侧（样本中 `v2` 在 1.0 s 为 End、6.5 s 为 Start） |
| 排序 | 输出按起始时间排序，与文档顺序无关 |
| 逐行 `<p>`（无 span） | 产出 `SyncedLine`，该类型**没有对齐字段**，演唱者信息丢失 |
| 单 span 包住整行 | 产出带对齐的 `MainKaraokeLine`，只有 1 个音节 |
| 背景人声 | `x-bg` 挂为主行的伴唱行，继承主行的边，首尾括号被去掉；没有伴唱时 `getAccompanimentLines()` 返回 **null** 而非空列表 |
| 翻译 | 行内 `x-translation` 与 iTunes `<translations>`（按 `itunes:key`）都进 `translation`；伴唱内的翻译进伴唱行自己的 `translation` |
| 音译 | 行内 `x-roman` 进 `phonetic`，不混入正文或翻译 |
| `lyricOffset` | **不处理**，时间戳原样输出 |
| 时间格式 | `12.345`、`1:02.500`、`00:01:10.250` 正常；`1s` 这类 offset-time 被读成 0 |
| 空白 | span 之间的单个空格并入前一个音节；**换行加缩进形式的空白被丢弃**，排版过的 TTML 会丢失词间空格（`Hello` + `world` → `Helloworld`） |
| 实体 | `&amp;` `&lt;` `&gt;` 解码；数字字符引用（`&#x4E2D;`）和未知实体原样保留为文本 |
| 畸形输入 | 截断的文档、空 body 返回空行列表，不抛异常；没有合法时间的文档 `canParse` 为 false |

### A.2 对正文设计的修正

1. **§5.3“超过两位独唱者时，第一位是主唱侧，其余都在对唱侧”不成立。** 库的行为是换人换边。采纳库的行为：两人对唱稳定分侧，三人及以上表现为“对话式交替”。若日后要求每位歌手固定一侧，需由 Provider 规范化时把 agent 重映射为两个，不在 Bridge 里猜。
2. **§4.2 逐行 TTML**：Provider 规范化器必须把每个无 span 的 `<p>` 包成一个覆盖整行的 span，否则逐行对唱没有分侧。Bridge 侧 `WordLine` 以“音节数 > 1”判定逐字，单音节行仍按逐行渲染，不会伪造逐字进度；P1 实现时加一条单测锁住这一点。
3. **§4.1 Provider 规范化器的硬性要求**（新增）：
   - 输出单行、无排版空白的 TTML，词间只用一个行内空格；
   - 时间统一写成 `mm:ss.SSS`，不使用 `1s` / `ms` 形式；
   - 把 `lyricOffset` 折算进所有时间戳后删除该属性；
   - 文本只使用五个预定义实体，其余字符直接写 UTF-8；
   - `lyric` 的行顺序按起始时间稳定排序，与库的输出顺序一致。
4. **§5.1**：`getAccompanimentLines()` 需判空。解析失败的信号是“返回空列表”而不是异常，回退判定以“主行数为 0 或与 `lyric` 行数不一致”为准。
5. **§4.2 `<audio lyricOffset>` 一行**：结论为 lyrics-core 不处理，由 Provider 折算。
6. 两人对唱的首行若是 `v2`，它会出现在 End 侧（由 id 奇偶决定，而非“先出现者为主唱”）。这与 Apple Music 的呈现一致，保留。

### A.3 体积与耗时（桌面 JVM，仅供量级参考）

- 160 行逐字样本（由对唱样本的正文重复 40 次拼成）规范化后约 **35 KB**；单次解析最快约 **1.3 ms**（Temurin 21，预热后取 20 次最小值）。
- 样本每行只有 2 到 4 个音节，真实歌曲每行音节更多，体积会按比例变大；据此估计常见歌曲在几十 KB 量级，**200 KB 上限暂时保留**，等真实样本和设备数据再调整。
- SystemUI 进程（ART）内的耗时、内存与 binder 余量未测，仍在 §10 待验证清单中。

## 附录 B：P1 Bridge 侧实现记录（2026-11-17）

验证：`:app:testDebugUnitTest :app:assembleDebug` 本地通过（1088 个用例，0 失败，10 个按 Assume 跳过）。**没有设备验证**；P1 按设计没有任何视觉变化，`side` 与 `accompaniment` 已进入模型但渲染器尚未读取。

### B.1 相对正文的设计调整：TTML 是“叠加层”，不是替换

正文 §4.1 / §5.2 写的是“`ttml` 校验通过后作为唯一来源，否则整体回退”。实现时发现这条路走不通：**开场清理（`LyricOpeningCleanup`）会从 `lyric` / `rawLyric` 里删掉制作人员等行**，TTML 文档不会被同步清理，行数校验必然失败。因此改为：

- `lyric` / `rawLyric` / `translationLyric` 仍是时间、文本、翻译的唯一来源，走原有的清理、官方行别名、翻译合并路径，一行不改。
- `ttml` 只做两件事：
  1. **标注**：组装完成后，按“起始时间 ±120 ms 且文本一致”把演唱者侧与背景人声贴到已有的行上（文本不一致时，只有 ±20 ms 内唯一候选才接受）。被清理掉的行自然匹配不到，不需要行数校验。
  2. **补位**：payload 只带 `ttml` 而没有 `rawLyric` / 翻译时，由 TTML 生成增强 LRC 与翻译 LRC 填入对应通道，之后与普通 payload 走同一条路。单 span 的行生成为普通 LRC 行，不伪造逐字时间轴。
- 只有文档里两侧都出现、且清理 / 匹配之后两侧仍都有行时，才标记为对唱；否则所有行保持未指定，布局不变。
- `sentence-window-v1` 模式下忽略 `ttml`。
- 不可解析、空结果或超过 512 KB 字符的文档直接忽略，payload 其余部分不受影响。
- 模型签名仅在 `ttml` 存在时追加一段，不带 `ttml` 的 payload 签名与 5.0 完全相同。

对 Provider 的含义：为了让 5.0 及更早的 Bridge 仍有逐字效果，Provider 应继续发送 `rawLyric`，`ttml` 作为附加字段；附录 A.2 第 3 条的规范化要求不变。

### B.2 改动清单

| 文件 | 内容 |
| --- | --- |
| `LyricInfoContract` | 新增 `JSON_TTML`、`Payload.ttml`；缺少通道时由 TTML 补位 |
| `TtmlLyricOverlay`（新） | 文档解析结果、单条缓存、增强 LRC / 翻译 LRC 生成 |
| `LyricsCoreAdapter` | 新增 `parseTtml`；`ParsedLine` 增加 `side`、`accompaniment` |
| `NativeLyricModelAssembler` | 新增带 `ttml` 的 `assemble` 重载与 `applyTtmlOverlay`；一条 `DETAIL` 日志（字符数、行数、匹配行数、是否对唱） |
| `render/LyricSide`（新）、`WordLine`、`WordLyricModel` | `side`、`accompaniment`、`hasDuet`、`ttmlAnnotatedLines` |
| `LyricMetadataSupport` | 模型签名纳入 `ttml` |
| `LockscreenLyricsModule` | 仅把 `payload.ttml` 传给组装器（入口类行数预算不变） |
| `guard/log-events.txt` | `BridgeEvents.DETAIL` 计数 56 → 57，对应上面新增的那条日志 |
| 测试 | `TtmlLyricOverlayTest`（10 个用例）、`LyricsCoreTtmlCharacterizationTest`（8 个用例） |

未做：§5.6 的 feature 级降级点没有单独接入 C-04 机制——P1 的失败路径是“忽略文档”，不存在需要恢复的渲染状态；等 P2 引入分侧渲染时再接。

### B.3 真实样本复核

样本：`PlayerSource/歌词解析测试/Lady Gaga & Bruno Mars - Die With A Smile.ttml`（歌词有版权，不入库；用环境变量 `CLL_TTML_TEST_FILE` 指向它即可运行 `externalDocumentAnnotatesEveryLineItProduces`）。

| 项 | 结果 |
| --- | --- |
| 体积 | 26 101 字符，44 行逐字，37 条行内翻译 |
| 解析 | 44 行全部解析，生成的增强 LRC 6 723 字符；44 行全部匹配，37 条翻译进入翻译通道 |
| 写法 | 外层有排版缩进，但 span 在行内连续书写、词间空格写在 span 文本内，未触发附录 A 的丢空格问题；时间为 `hh:mm:ss.SSS` |
| **对唱信息** | **没有**。文档没有 `<head>`，没有任何 `ttm:agent`，解析结果为非对唱 |
| 其它 | 前 8 行是曲名与制作人员信息，第 1 行带版权声明式的“翻译”，这些行会被现有开场清理处理 |

**第二份样本（带演唱者标记）**：`Lady Gaga & Bruno Mars - Die With A Smile-AM.ttml`。文件头含 `amll:meta`（`ncmMusicId`、`ttmlAuthorGithub` 等），是 AMLL TTML 歌词库的格式，不是 Apple Music 原始文件。

| 项 | 结果 |
| --- | --- |
| 体积 | 24 579 字符，50 行逐字，单行无排版空白，时间为 `mm:ss.SSS` |
| 演唱者 | `v1`（`person`）40 行、`v2`（`other`）10 行；解析为**对唱**，50 行全部匹配并带侧 |
| 翻译 | 48 条行内翻译：46 条进入主行翻译通道，2 条属于背景人声 |
| 背景人声 | 文档有 2 处 `x-bg`，**只解析出 1 处**。被丢掉的那处是 `<span ttm:role="x-bg" begin end>(You)…</span>`，文字直接写在 span 里、没有内层计时 span；保留的那处内层有计时 span。lyrics-core 0.4.7 不识别“裸文本”的背景人声 |

对 Provider 规范化器的补充要求：裸文本的 `x-bg` 需要包一层与外层同起止时间的计时 span，否则 P3 会漏掉这类背景人声。

以下是对第一份样本的说明：这份文件的结构像是由其它逐字格式转换而来，转换过程没有带上演唱者。**对唱分侧完全依赖 `ttm:agent`，没有它 Bridge 无从判断哪一行归谁。** 因此 Provider 侧动工前要先确认：计划接入的 TTML 来源里，哪些真的带演唱者标记。

## 附录 C：P2 分侧渲染实现记录（2026-11-17）

验证：`:app:testDebugUnitTest :app:assembleDebug` 本地通过（1092 个用例，0 失败，10 个按 Assume 跳过）。**没有设备验证。在 Provider 开始发送 `ttml` 之前，设备上也看不到分侧效果**——目前没有任何来源会把这个字段放进 `lyricInfo`。

### C.1 行为

| 用户对齐 | 主唱侧行 | 对唱侧行 | 没有侧的行 |
| --- | --- | --- | --- |
| 靠起始 | 起始 | 末尾 | 起始 |
| 靠末尾 | 末尾 | 起始 | 末尾 |
| 居中 | 起始 | 末尾 | 居中 |

- 只有被 TTML 判定为对唱的曲目，行才带侧；其余曲目与 5.0 逐像素相同的路径（有效对齐恒等于用户对齐）。
- 多行折行的每一视觉行各自贴边；超宽行仍按逻辑起始侧溢出，不随侧改变。
- 行缩放支点跟随该行实际贴的那一侧，避免对唱侧在放大时向屏幕外生长。
- 新增设置项“对唱歌词分侧显示”（排版卡片，对齐项下方），默认开；关闭后所有行回到用户对齐。旧配置没有该键时按“开”解码。

### C.2 改动清单

| 文件 | 内容 |
| --- | --- |
| `LyricUiLayoutPolicy.effectiveAlignment` | 上表的纯函数 |
| `OfficialLyricTextRenderer` | `draw()` 入口计算本行有效对齐，`resolveTextX` 两个重载改读它 |
| `LyricsRecyclerViews` | 以弱引用记录“被侧改变了对齐的行视图”，`applyOfficialLyricScalePivot` 据此取支点；仅在结果变化时重设支点 |
| `LyricUiConfig` / `LyricUiConfigCodec` | 新增 `duetSplitEnabled`（键 `duet_split_enabled`，默认 true）。`SCHEMA_VERSION` 未变：新增键缺省即默认值，不需要迁移 |
| `LyricUiSettingsActivity`、两份 `strings.xml` | 开关与文案 |
| 测试 | `DuetAlignmentPolicyTest`（4 个用例）；`LyricUiConfigContractTest` 纳入新字段 |

### C.3 未做与已知风险

- **设置页预览没有对唱示例行**（正文 §5.3 表格最后一项）。预览用的是独立的 `SettingsPreviewView`，要加需要另造一组示例数据，留待设备效果确认后再做。
- 行视图被复用到另一行时，支点在该行第一次绘制时才更新，理论上可能有一帧支点不匹配；非激活行有缩放，需要在设备上看切歌 / 快速滚动时是否可见。
- 字符上浮、辉光、被动行横移都以绘制 x 为基准，代码上自动跟随，但没有逐项目视确认。
- 对唱曲目中个别没匹配到 TTML 的行，在“居中”设置下会保持居中，夹在左右分侧的行之间。
- C-04 功能级降级点仍未接入。
