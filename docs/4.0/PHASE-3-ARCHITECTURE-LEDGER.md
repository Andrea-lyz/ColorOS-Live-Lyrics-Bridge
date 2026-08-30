# Bridge 4.0 Phase 3 architecture / method ledger

记录日期：2026-08-25
仓库：`ColorOS-Live-Lyrics-Bridge`
分支：`4.0`
基线文件：`app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java`（约 19661 行，约 660 个方法）

本 ledger 按数据所有权、生命周期和 hook 边界分类，不按行数机械拆分。
Renderer / AOD 方法只登记，本轮不搬迁、不改时序。

## 1. 目标包结构

```text
bootstrap/                 SystemUI/player 进程入口与 hook 装配
systemui/media/            MediaSession、MediaData、seedling、封面、screen-timeout
systemui/lyrics/           官方 lyricInfo 消费、RecyclerView、setCurrentLyric、handoff
render/                    已存在；逐字绘制与 frame 决策
aod/                       AOD attach/prime/row-scale/slot-height
settings/                  设置页与配置广播（本轮只新增 Debug 子页，不搬现有 Activity）
diagnostics/               [CLL] 日志、Debug 配置、旧事件映射
players/kuwo/              酷我同曲/封面/车载 identity
players/qq|netease|apple/  仅在有 SystemUI 证据时迁入
```

`LockscreenLyricsModule` 最终只保留：libxposed 生命周期、feature 装配、ClassLoader 分发、共享状态所有权、设置注入。

## 2. 已落地

### slice 1（2026-08-25）

| 职责 | 去向 | 状态 |
|---|---|---|
| architecture / method ledger | 本文件 | 完成 |
| 结构化 [CLL] 日志 + 旧事件映射 | `diagnostics/` | 完成 |
| Bridge Debug 总开关 + area 开关 + 配置版本 | `BridgeDebugConfig` + 设置子页 | 完成 |
| SystemUI / player bootstrap 类 | `bootstrap/` | 完成（装配逻辑未改） |
| 酷我车载同曲 identity | `players/kuwo/KuWoMediaIdentityPolicy` | 完成 |
| Renderer / AOD 方法 | 仍在主模块 | **本轮不迁** |
| v4 ingress / registry | 已删除 | Phase 5 完成，见 `PHASE-5-BRIDGE-V4-REMOVAL-REPORT.md` |

### slice 2（2026-08-25）

| 职责 | 去向 | 状态 |
|---|---|---|
| 酷我封面 URI / 尺寸 / seedling 是否修复 | `players/kuwo/KuWoCoverPolicy` | 完成 |
| 酷我封面快照 LRU | `players/kuwo/KuWoArtworkSnapshotStore` | 完成 |
| 酷我同曲 plugin 模型保留决策 | `players/kuwo/KuWoSameTrackLyricRetention` | 完成 |
| 酷我 plugin 模型包名 / labeled 文本 | `players/kuwo/KuWoPluginMediaModelReader` | 完成 |
| 酷我 runtime 状态与日志节流 | `players/kuwo/KuWoSystemUiRuntime` | 完成 |
| KuWo hook 安装与 Xposed interceptor | 仍在 `LockscreenLyricsModule` | **本轮保留** |
| https 封面网络拉取 / plugin 反射写回 | 仍在主模块 | **本轮保留** |

### slice 3（2026-08-25）

| 职责 | 去向 | 状态 |
|---|---|---|
| 播放器进程 `setMetadata` 注入 | Phase 5 已删除 | Provider 自己发布原生 `lyricInfo` |
| `LyricSessionReducer` / metadata 写回 | Phase 5 已删除 | Bridge 不再进入播放器进程 |
| `resolveMetadataTrackIdentity` / playback-state / seedling | 仍在主模块 | 下一刀 |

### slice 4（2026-08-25）

| 职责 | 去向 | 状态 |
|---|---|---|
| `loadLyricInBg` 决策（defer / suppress / accept / clear、酷我车载 identity） | `systemui/lyrics/SystemUiLoadLyricPolicy` | 完成 |
| 外部歌词 promotion / replay / 近期 SystemUI context | Phase 5 已删除 | 只消费 incoming native `lyricInfo` |
| Recycler 定位所有权与 prime 输入（不含 alpha/size gate） | `systemui/lyrics/LyricsRecyclerPolicy` | 完成 |
| `loadLyricInBg` 读参、cleanup、官方 refresh invoke | 仍在主模块 | **本轮保留** |
| `primeLyricsRecyclerView` / `setCurrentLyric` 几何与时序 | 仍在主模块 | **本轮不迁** |

### slice 5（2026-08-25）

| 职责 | 去向 | 状态 |
|---|---|---|
| 自然语言 `info("...")` → 显式 `BridgeEvents` | 调用点传入 `area` + `event`；原文留在 `message=` | 完成 |
| AOD 对照六句 | `RECYCLER_*` / `OFFICIAL_*` / `SET_CURRENT_LYRIC_GEOMETRY` | 完成 |
| `LegacyLogEventMap` | ERROR 分类与测试用 `emitLegacyInfo` | **保留** |

## 3. 方法账本（按目标包）

行数来自 2026-08-25 对主模块方法体的静态划分，用于决定下一刀而不是一次搬完。

### 3.1 `bootstrap/` — 约 14 方法 / 477 行

生命周期与 hook 安装入口。热度：SystemUI 启动一次。

| 方法 | 行数 | 状态读写 | 下一刀 |
|---|---|---|---|
| `onSystemUiLoadLyricInBg` | 208 | 官方歌词模型、handoff、KuWo 同曲 | 决策已外置；hook 仍读参写回 |
| `installOplusMediaPolicyBypassHooks` | 55 | RUS / whitelist / entrance | 留 bootstrap 装配 |
| `resolveSystemUiTargets` | 33 | `systemUiDexKitTargets` | 留 bootstrap |
| `onModuleLoaded` / `onPackageReady` / `onSystemServerStarting` | 8–15 | `logProcessName` | 组合根，保留 |

已外置：`SystemUiRuntimeBootstrap`。播放器进程 `PlayerRuntimeBootstrap` 已在
Phase 5 删除。

### 3.2 `systemui/media/` — 约 70 方法 / 1625 行

| 方法 | 行数 | 热度 | 备注 |
|---|---|---|---|
| 播放器进程 `onSetMetadata` | 199 | Phase 5 已删除 | 由独立 Provider 完整负责 |
| `rememberSystemUiPlaybackState` | 101 | 播放状态 | 状态 owner 应独立 |
| `readSeedlingMediaBundle` | 81 | seedling | |
| `resolveMetadataTrackIdentity` | 79 | metadata | |
| `ensureScreenTimeoutReceiver` 及 wake-lock 族 | 74+ | 锁屏超时 | 不改行为 |

### 3.3 `systemui/lyrics/` — 约 118 方法 / 2119 行

含已知良好 AOD 对照点，搬迁时必须对照日志，禁止加 readiness gate。

| 方法 | 行数 | AOD 相关 | 备注 |
|---|---|---|---|
| `applyOfficialDisplayTextAliases` | 86 | 否 | |
| `invokeLyricsRecyclerSetCurrentLyric` | 65 | 是 | 保持 prime 时机 |
| `onLyricsRecyclerSetCurrentLyric` | 55 | 是 | geometry 日志 |
| `primeLyricsRecyclerView` | 45 | **是** | 首次 attach 立即 prime |
| `applyVisibleLyricBlockHeights` | 37 | 是 | 已知良好时序 |
| `prebindOfficialLyricSlotAfterTextMutation` | 33 | 是 | 已知良好时序 |

对照事件：`Observed LyricsRecyclerView attachment`、`Stabilized LyricsRecyclerView scroll`、`Primed LyricsRecyclerView`、`Official lyric layout height changed`、`Official lyric row scale`、`LyricsRecyclerView setCurrentLyric geometry`。映射见 `PHASE-3-EVENT-MAP.md`。

### 3.4 `render/` — 约 53 方法 / 1890 行

已有 `render/` 数据类。主模块仍承载绘制 hook。

巨型方法：`findOfficialLyricDrawFrame`（202）、`onTextViewOnDraw`（175）。Phase 6 再拆；本轮只把日志改走 diagnostics。

### 3.5 `aod/` — 约 5 方法 / 68 行（另有 lyrics 中的 AOD 路径）

`installAodMediaSupportHooks`、`onAodMediaSupportLookup`、`isAodLowFrameRateLyricMode`。
低行数但时序敏感；独立迁包时不得改变 `setAodLowFrameRateLyricMode` 以外的官方私有状态。

### 3.6 `diagnostics/` — 约 24 方法 / 282 行

`info` / `warn` / `error` / `formatLog` 已改走 `StructuredBridgeLog`。
`describeViewForLog` 等仍在主模块，仅在 Debug 打开时构造。

### 3.7 `players/kuwo/` — hook 仍在主模块，策略/状态已外置

已迁：identity、封面策略、快照 LRU、同曲保留决策、plugin 文本读取、runtime 状态 owner。
仍在主模块（hook 装配 + 反射写回 + https 拉取）：

| 方法 | 行数 | 职责 |
|---|---|---|
| `installKuWo*Hook` / `tryInstallKuWoPluginMediaModelHook` | 16–42 | hook 装配留组合根 |
| `onKuWoPluginMediaModelBuilt` | 薄封装 | 读模型 → `KuWoSameTrackLyricRetention` → 写回 |
| `repairKuWoPluginAlbumArt` | 反射写回 | 使用 `KuWoCoverPolicy` + snapshot store |
| `onSystemUiKuWoArtworkLookup` | 薄封装 | 使用 cover policy + snapshot store |
| `loadKuWoHttpsCoverIcon` | 网络 | URI 资格已在 `KuWoCoverPolicy` |

### 3.8 `lyrics-v4` — Phase 5 已删除

`parseExternalLyricCapture`、`applyExternalLyricCapture`、promotion / handoff、
protocol module 与播放器进程 adapter/capture 栈均已删除。当前 Bridge 只消费
SystemUI 原生 `lyricInfo`。

### 3.9 主模块杂项 — 约 206 方法 / 4059 行

未按名字命中的方法，含 `cacheSystemUiLyricModelLocked`（191）、inline LRC 解析、可见性恢复。
下一轮按调用图再归入 lyrics/render，禁止一次清空。

### 3.10 Salt 残留（SystemUI 包名特例）

`shouldPreserveSaltLyricRelayMetadata` 等仍按播放器包名处理车载 lyricInfo，不是已删除的 `SaltPlayerAdapter`。
保留在主模块直到有独立 `players/salt` SystemUI 证据类；不得恢复 player-process adapter。

## 4. 巨型方法优先拆分队列

同时承担“反射读取 → 策略判断 → 对象修改 → 日志”的方法：

1. ~~`onSetMetadata`（199）~~ Phase 5 随播放器进程 adapter 删除
2. `onSystemUiLoadLyricInBg`（已删除 v4 document/promotion 分支）
3. `onTextViewOnDraw`（175）→ **不在本轮**，render 热路径
4. `findOfficialLyricDrawFrame`（202）→ **不在本轮**
5. `onKuWoPluginMediaModelBuilt`（115）
6. `handlePlayerTranslationSettingsChanged`（119）

拆分后原 hook 只读参数、调 policy、写回结果。禁止因拆分类新增全局静态状态。

## 5. Debug / 日志契约（本轮）

- 默认关闭。不再用 `BuildConfig.DEBUG` 自动打开模块诊断。
- `log.tag.LockscreenLyrics=DEBUG|VERBOSE` 仍可作为强制打开通道，兼容现有捕获脚本。
- 总开关 + area：`bootstrap`、`media`、`lyric`、`renderer`、`aod`、`player-special`、`performance`。
- 打开后 DEBUG/INFO 同时写 logcat 与 libxposed/LSPosed framework log。
- 关闭时只保留启动摘要、关键状态切换、WARN、ERROR。
- 设置页显示 SystemUI 最近一次应用的 debug revision；bootstrap 启动日志需重启 SystemUI。
- 不修改 `LyricUiConfig` schema；debug 使用独立 prefs `lockscreen_lyrics_debug`。

## 6. 明确下一刀

1. ~~将酷我 runtime 保留/封面从主模块迁到 `players/kuwo/` 具名类。~~ slice 2 已完成；hook 安装与反射/https 写回仍在组合根。
2. ~~抽 `systemui/media`：`onSetMetadata` 的策略部分。~~ slice 3 曾完成；
   Phase 5 随播放器进程注入链整体删除。
3. ~~抽 `systemui/lyrics`：load/promote/recycler 的 policy，hook 体留薄封装。~~ slice 4 已完成。
4. ~~把剩余自然语言 `info("...")` 改为显式 `BridgeEvents` 常量。~~ slice 5 已完成；AOD 对照句仍在 `message=`。
5. 到 Phase 6 再拆 render 巨型方法与重复解析。
