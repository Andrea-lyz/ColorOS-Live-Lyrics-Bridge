# 锁屏歌词稳定自适应槽布局开发线路

> 当前实施状态：槽高按每行的换行、翻译和排版配置稳定计算，最低 `56dp`、预留约 `12dp` 垂直绘制空间和 `1dp` 底部安全空间，宽度尚不可用时才回退 `80dp`。字体外边界按 `top/bottom` 向外取整；所有主歌词最多同时显示两行，三行及以上保留完整换行集合并通过两行窗口展示。高度不依赖逐字进度、焦点、缩放、光晕或 AOD 状态；翻译开关使用统一布局进度同步收放可见行高，并补偿当前行锚点，AOD 只应用一次终态。歌词条目间距可在 `-5–20dp` 间以 `0.5dp` 调整，歌词内换行额外间距可在 `-1–8dp` 间以 `0.5dp` 调整，总内部行距不低于 `0dp`；两者所有预设默认 `0dp`；当前行中心向下偏移 `48dp`。下文保留早期固定槽方案的设计过程与回归依据。

## 1. 背景

当前模块通过 LSPosed Hook OPlus 私有歌词组件，在系统锁屏歌词页中接管逐字高亮、长句换行和翻译绘制。

歌词列表使用 `LyricsRecyclerView`。RecyclerView 本身支持不同 item 高度；抖动的根因不是“高度不同”，而是同一个 item 在已参与布局后因逐字进度、焦点或动画持续重测。当前实现因此允许不同歌词行使用不同但稳定的高度，并在绑定、附着或首次可靠匹配时按内容与配置确定高度。

本次工作的核心目标是在保留多行与翻译完整字号的同时，让每一行的物理几何在播放期间保持稳定。

## 2. 已确认的约束

以下约束在实现中必须保持：

1. 同一歌词行在相同宽度与排版配置下必须得到确定的物理高度。
2. item 高度不能因逐字进度、焦点状态、缩放、光晕或 AOD 状态而变化。
3. item margin 不能按内容动态变化。
4. 逐字更新和翻译动画只能走 `invalidate()`，不能触发 `requestLayout()`。
5. 高度优先在绑定、附着或预热阶段应用；绘制路径只允许在 holder 首次可靠匹配或配置变更后做幂等校正，不能逐帧改变高度。
6. OPlus 私有组件必须安全降级。Hook、字段或方法解析失败时，不得导致 SystemUI 崩溃。
7. 不机械替换代码中的所有 `82`。例如 `0.82f` 可能是动画进度，与槽高无关。

## 3. 当前实现现状

### 3.1 稳定的逐行物理几何

`OfficialLyricTextRenderer` 当前使用：

- 槽高下限：`LYRIC_SLOT_MIN_HEIGHT_DP = 56f`
- 垂直绘制空间：`LYRIC_SLOT_VERTICAL_PADDING_DP = 12f`
- 宽度未知回退：`LYRIC_SLOT_HEIGHT_DP = 80f`
- 主歌词字号：用户可调 `18–28sp`；无翻译布局额外增加 `2sp`，实际最高 `30sp`
- OPlus item 间距：用户可调 `-5–20dp`、步进 `0.5dp`；所有预设默认 `0dp`
- 所有主歌词最多同时绘制两行，三行及以上保留完整换行结果并使用两行窗口切换
- 翻译绘制在同一槽内，不改变主歌词窗口容量

`resolveSlotHeight()` 使用与绘制相同的字号、字重、翻译比例和换行规则计算槽高；`drawLyricGroup()` 再在该槽内垂直居中。外边界按字体 `top/bottom` 向外取整，并额外保留 `1dp` 底部安全空间，避免 `g/j/p/q/y` 等下伸部和重音符号被裁切。复杂行不再通过局部缩小字号适配固定槽，因此相邻歌词不会出现同一排版配置下一大一小。

### 3.2 官方歌词与完整歌词模型

模块维护两份职责不同的数据：

- `payload.lyric`：提供给 OPlus，用于建立官方 RecyclerView 列表。
- `payload.rawLyric`：模块自行解析，用于逐字、翻译和自定义 Canvas 绘制。

模块生成官方 LRC 时，`sanitizeForOplusLyric()` 已按时间戳分组，并通过 `findPrimaryTextIndex()` 只输出每组的主歌词。这意味着模块自建 payload 的常规路径理论上已经不会把同时间戳翻译作为独立官方 item。

`applyOfficialDisplayTextAliases()` 会根据官方 LRC 构建 `WordLyricModel.officialLines`，用于把 OPlus adapter position 映射回模块的 `WordLine`。

### 3.3 仍存在的兼容处理

v1.7.0 已移除常规 `1px` 翻译 item 压缩路径。以下入口改为在 OPlus 解析前统一规范化，绘制层只处理主歌词 item：

- 播放器主动提供的 `lyricInfo`
- 模块兼容适配器生成的 payload
- OPlus 对某些歌词格式的特殊解析结果
- 同时间戳、错位时间戳或重复文本造成的识别偏差

零宽字符占位行会被视为空行；同时间戳双语分组只向官方列表输出主歌词，完整翻译仍保留在 `rawLyric` 模型中。

### 3.4 当前滚动路径

模块目前不自行计算：

```text
position * (slotHeight + spacing)
```

而是先调用 OPlus 私有 `LyricsRecyclerView#setCurrentLyric()` 完成定位，再读取目标 holder 的实际高度，把当前 item 中心向下补偿 `48dp`；目标尚未绑定时才使用 `80dp` 回退。补偿优先使用 `scrollToPositionWithOffset()`，不可用时才根据可见子项的实际中心做一次 `scrollBy()`。

因此，调整槽高后需要实机验证 OPlus 是读取实际 measured height，还是内部缓存了默认 item 高度。只有确认 OPlus 内部存在固定高度假设时，才考虑 Hook 其滚动偏移计算。

## 4. 目标架构

目标是建立两个互相独立的层次。

### 4.1 物理布局层

- OPlus adapter 只包含主歌词 item。
- 每个歌词 item 使用按内容与排版配置确定、播放期间稳定的槽高。
- 所有 item 使用统一固定间距。
- 逐字进度、焦点与 AOD 不改变 RecyclerView 坐标系；翻译开关使用一次有界布局动画同步收放可见行，并在每帧补偿当前行锚点。

### 4.2 绘制表现层

- 主歌词、逐字高亮和翻译都由模块在同一个主歌词槽内绘制。
- 单行内容垂直居中。
- 长句保留完整换行集合，但在自适应槽内最多显示相邻两行；窗口按逐字位置或整句进度推进，不因复杂行单独缩小用户选择的字号。
- 翻译开关只改变 alpha、基线和窗口内容。
- 动画每帧只调用 `invalidate()`。

## 5. 推荐实施顺序

### 阶段 A：建立基线与诊断

先不改变视觉参数，增加只在调试环境启用的几何日志。

记录内容：

- 当前 adapter position
- 当前 item measured height
- item bottom margin
- 当前 TextView 对应主歌词还是翻译
- RecyclerView 首个可见 position 和顶部坐标
- `setCurrentLyric()` 前后的目标 item 中心坐标
- 当前 payload 来源和是否经过官方 LRC 规范化

验收条件：

- 能明确确认列表中是否仍存在 `1px` item。
- 能区分模块自建 payload 与播放器主动 payload。
- 能观察切行前后目标 item 中心是否发生非预期变化。

### 阶段 B：统一槽高常量

将槽高提升为模块级唯一常量，例如：

```java
private static final float LYRIC_SLOT_HEIGHT_DP = 80f;
```

要求：

- 所有固定歌词槽高引用统一使用该常量。
- `TRANSLATION_SCROLL_END_PROGRESS = 0.82f` 等无关数值保持不变。
- 间距继续使用独立的 `OFFICIAL_LYRIC_LINE_SPACING_DP`。
- 若未来加入滚动补偿，必须同时引用槽高和间距常量。

该常量已成为布局和滚动补偿共用的唯一槽高来源。

### 阶段 C：统一官方 LRC 规范化入口

新增一个职责明确的规范化步骤，保证任何进入 OPlus 解析器的 `payload.lyric` 都满足：

- 每个时间点只保留一个主歌词条目。
- 翻译、音译和逐字时间轴仍保留在 `rawLyric` 或模块模型中。
- 尾部 spacer 和必要的首行 pre-roll 行为保持兼容。
- 已是规范化主歌词的 payload 不发生破坏性改写。

覆盖来源：

- Salt Player 兼容适配器
- ConePlayer 兼容适配器
- 播放器主动提供的增强 `lyricInfo`
- 播放器只提供简单逐行歌词的兜底路径

不要在 Adapter `submitList()` 之后修改列表。优先在 `loadLyricInBg()` 消费 payload 之前规范化数据，以避免破坏 DiffUtil、点击位置和 OPlus 内部状态。

### 阶段 D：稳定映射

官方 position 到模块 `WordLine` 的映射不能只依赖可变 position。

匹配优先级建议：

1. 时间戳精确匹配
2. 规范化主歌词文本匹配
3. 时间邻近匹配
4. 重复文本使用 occurrence 顺序消歧

建议将稳定身份表达为：

```text
(timestamp, normalizedPrimaryText, occurrence)
```

`officialLines` 可继续作为快速索引，但其构建结果必须与规范化后的官方 LRC 一致。

验收条件：

- 重复副歌不会映射到错误时间点。
- 首行 pre-roll 不会导致后续 position 整体错位。
- 开关翻译不改变 official position。

### 阶段 E：移除 `1px` 翻译 item 依赖（已完成）

官方列表规范化和实机日志确认后，已删除 `collapseSkippedOfficialLyricTextView()` 及常规 `1px` 高度路径。零宽占位行和同时间戳翻译在数据入口过滤，不再进入 RecyclerView 坐标系。

### 阶段 F：收紧统一槽高（已完成）

实机从 `82dp` 逐步比较后，最终选择 `80dp`。更激进的动态短句压缩会在切行时改变累计几何，产生挤压和下跳，因此没有采用。

统一内容包络为：

- 推荐所有主歌词最多显示两行。
- 超过两行时使用现有的垂直滑动窗口。
- 不推荐逐字歌词使用水平跑马灯作为主要方案。
- 翻译始终占主歌词槽内的一行绘制预算，不新增 item。

### 阶段 G：必要时接管居中补偿（定向完成）

调整槽高后首先验证 OPlus 原生 `setCurrentLyric()`。

实机确认缩小槽高后当前行视觉位置偏高，因此增加 `48dp` 向下补偿。实现优先调用 LayoutManager 的 `scrollToPositionWithOffset()`；若 ROM 不暴露该方法，则读取实际可见子项中心并调用一次 `scrollBy()`。

统一 item 的理论步长为：

```text
itemPitch = lyricSlotHeight + officialLineSpacing
```

补偿基于实际像素值、统一槽高和 RecyclerView viewport，不维护独立的累计 item pitch。

## 6. 测试计划

### 6.1 单元测试

为官方 LRC 规范化增加确定性测试：

- 同时间戳主歌词与翻译只输出主歌词
- 翻译时间戳存在轻微偏移
- 重复副歌文本
- 纯中文、纯英文和混合语言
- 逐字 LRC 中包含多个内联时间标签
- 首行晚于 `1.5s` 的 pre-roll
- 尾部 spacer
- 播放器已提供纯主歌词时保持原顺序

为映射策略增加测试：

- position 与 `WordLine` 一一对应
- 重复文本按时间和 occurrence 消歧
- pre-roll 不破坏后续映射

### 6.2 实机矩阵

至少覆盖：

- 连续单行短句
- 单行与双行交替
- 双行与三行长句交替
- 有翻译与无翻译歌曲切换
- 播放中切换翻译开关
- 快速连续切歌
- 暂停后恢复
- 拖动进度到重复副歌
- AOD/锁屏歌词界面进入和退出
- Salt Player、ConePlayer、Halcyon 主动 `lyricInfo`

### 6.3 视觉与稳定性验收

必须同时满足：

- 切行时当前歌词中心无肉眼可见跳动。
- RecyclerView 中不存在常规 `1px` 翻译 item。
- 翻译开关不触发 item 高度变化。
- 逐字动画期间不触发持续 layout pass。
- 长句不裁掉关键文本。
- 连续短句的视觉间距小于原 `82dp + 6dp` 方案，且切行时不改变 item 几何。
- SystemUI 无 Hook 异常或崩溃。

## 7. 回滚策略

每个阶段应独立提交，推荐顺序：

1. 诊断日志
2. 槽高常量重构
3. 官方 LRC 规范化
4. 映射测试与实现
5. 移除 `1px` 常规路径
6. 调整统一槽高
7. 可选滚动补偿

如果新槽高产生居中偏差，可以只回滚视觉参数，保留数据规范化和稳定映射。若主动 payload 兼容性出现问题，可以按 provider/source 暂时回退到原始官方 LRC，不影响模块自建 payload。

## 8. 当前实施结论

1. 歌词行使用最低 `56dp`、约 `12dp` 垂直绘制空间的稳定自适应槽；`80dp` 只用于宽度未知时的回退。行间距可在 `-5–20dp` 间以 `0.5dp` 调整，所有预设默认 `0dp`。
2. 所有进入 `loadLyricInBg()` 的官方 `payload.lyric` 通过 `OplusLyricNormalizer` 规范化。
3. 官方 item 使用时间戳、规范化文本和 occurrence 稳定映射到 `WordLine`。
4. 常规 `1px` 翻译 item 路径已移除。
5. 所有主歌词最多同时显示两行；长句保留完整换行集合并使用两行滑动窗口，翻译始终绘制在同一主歌词槽内，复杂行不单独缩小字号。
6. 当前行使用基于实际视口的 `48dp` 定向下移补偿。
7. 槽高只由内容、宽度和排版配置决定，不随播放进度、焦点、缩放、光晕或 AOD 状态改变。
8. 字体外边界使用向外取整的 `top/bottom`，槽底额外保留 `1dp`，主歌词、翻译和光晕缓存共用同一防裁切口径。
9. Opalite 零宽占位、重复副歌、同时间戳双语、快速切歌和暂停恢复均已加入测试或实机回归。
