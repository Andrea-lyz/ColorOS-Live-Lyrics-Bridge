# Bridge 4.0 Phase 5：删除 v4 耦合

状态：`COMPLETE`
日期：2026-08-29
仓库：`ColorOS-Live-Lyrics-Bridge` / 分支 `4.0`

## 结果

Bridge 已收敛为 SystemUI/system_server 增强模块：

```text
Provider -> player MediaSession lyricInfo -> ColorOS SystemUI -> optional Bridge enhancement
```

Bridge 不再进入播放器进程，不再接收或发送 Provider 私有歌词广播，也不再持有
Provider applicationId、source-to-player 映射、sender capability 或 module envelope。

## 已删除

1. `:external-lyric-protocol` Gradle module、direct-v4 action/extras、fixture 与依赖。
2. `ExternalLyricIngress`、动态 exported receiver、sender policy、source profile、
   registry、document cache、generation、promotion、replay 与 soft-handoff。
3. Apple Music / Poweramp / KuGou 仅服务 v4 的 Bridge special-case 类。
4. `PlayerAdapter` / `PlayerRuntimeBootstrap` / `FirstBatchMediaSessionAdapter`，
   以及 QQ、网易、Salt 的不可达播放器进程 hook、下载、解析、reducer 和 metadata
   injection 栈。
5. `lockscreen-lyrics-module` envelope 与 player-process `setMetadata` 注入策略。
6. Manifest 与翻译设置中的新旧 Provider applicationId / package query。

纯 Java 的 `TimedLyricDocument` 只用于 parser regression 对照，已从 main 移到 test，
不会进入 Bridge APK。

## 保留边界

- `LyricInfoContract` 只解析标准 `lyricInfo`。
- `ACTION_TOGGLE_TRANSLATION` 是 MediaSession 公共 CustomAction，不属于 v4 transport。
- `PlayerSystemUiPolicy` 只保存播放器包名，用于有设备证据的 OPlus
  history/AOD/translation SystemUI policy；它不知道 Provider APK 或 source。
- KuWo `players/kuwo/` 仍是 SystemUI 侧封面/身份增强，不是播放器进程 Provider 逻辑。

## 2026-08-29 全 Provider 回归修复

用户全量回归日志 `lyrics-log-20260829-031440.txt` 中大部分播放器状态正常，两个
专项日志确认了 Phase 5 后的边界回归：

- LX/Walnut：`lyrics-log-20260829-032948-lx.txt` 中新曲 generation 2 已发布
  `lyricInfo`，但随后 LX 的逐行 TITLE 投影被误判为新曲，generation 继续增加，
  后续 host metadata 以 `lyricInfo=0` 覆盖，Bridge 因此清空刚解析的模型。第一轮加入
  publication 歌词行识别；`lyrics-log-20260829-044318.txt` 与
  `Recording-PJZ110[192.168.2.201_6666]-20260829044321.mp4` 进一步证明第一轮未关闭：
  探针已经触发，但 buffering 窗口把当前歌词 TITLE 与上一曲残留 composite ARTIST
  组合，旧解析优先级仍把 generation 3 bump 为 4。现在 `:player-lx` 在解析 composite
  ARTIST 前优先信任“当前 session + 当前 generation + 当前 publication 精确歌词行”，
  保持 snapshot track 并重放原生 `lyricInfo`。
- 同一份 `044318` 日志还确认 Bridge 将 provider-core 的四段
  `id|title|artist|durationSeconds` trackKey 当成了两段 `title|artist`，因此即使画面中的
  SystemUI 标题正确，严格匹配仍会清空模型。Bridge 现在先通用归一化 provider-core
  stable key，再执行严格 title/artist gate；这不是 LX 包名/source 特例。
- Spotify：`lyrics-log-20260829-032726-spotify.txt` 中解析仍为 60 行 `LINE_TIMED`，
  但长行高度变为 327px 后出现 `activeIndex=2`、`officialIndex=0`、
  `recyclerIndex=0`。Phase 5 没有恢复 Spotify 包名/source 分支，而是加入通用的
  native line-timed Recycler 自适应恢复：仅在已稳定的可见 surface 上观测到已知官方
  索引持续落后时启用，随后按新活动行执行布局后对齐。

`lyrics-log-20260829-045859.txt` 随后确认上述自适应入口过宽：LX 在正常 attach
过渡中 `officialIndex=-1`、活动行 `targetCenter=1150`，Bridge 仍于 target 3 激活恢复，
之后每次换行都执行 `Force-aligned`，把 native SmoothScroller 取消并固定到
`targetCenter=826`，表现为整表跳切且实时行偏下。现已删除“未知 official index / 单纯
换行高度或中心偏差”触发条件；只有已知 official index 持续至少 650ms 落后活动行两行
以上时才允许通用恢复。正常 LX 滚动完全交还 SystemUI。

`lyrics-log-20260829-050946.txt` 又确认 Spotify 的“实时行不高亮”是独立的播放状态
订阅缺陷，不是 SystemUI 热残留：Provider 持续发布同一 generation，原生 Recycler
从 first visible 0 滚到 5，但 Bridge 全程 `position=0`、`playing=false`、
`scaleActiveIndex=-1`，因此所有自绘行均为 `active=false`。Bridge 之前只在
`createActionsFromState` 调用时快照读取一次 `MediaController.getPlaybackState()`；如果
当时是 NONE/STOPPED，后续 PLAYING 不会进入 Bridge。现已对当前 SystemUI
MediaController 注册标准 `PlaybackState` callback，在 controller 切换时注销旧 callback，
并在状态更新后立即刷新活动行。该修复只订阅 SystemUI 已持有的 controller，不进入
播放器进程、不改写宿主 PlaybackState。

新增验证标记：

- Bridge：`Activated adaptive native line-timed Recycler follow`；
- LX Provider：`LX_PUBLISHED_LYRIC_TITLE_PROJECTION_IGNORED`，且紧随其后的
  `HOST_OUT` 应保持 `lyricInfo>0`、不产生伪 generation bump。

## 本地验证

```powershell
scripts\gradle-local.cmd :app:compileDebugJavaWithJavac :app:compileDebugUnitTestJavaWithJavac
scripts\gradle-local.cmd :app:testDebugUnitTest
scripts\gradle-local.cmd :app:assembleDebug
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\validate-lsposed-metadata.ps1
```

结果：

- Java main/test 编译通过；
- Bridge 397 tests，0 failures，0 errors，6 skipped；
- LX Provider 33 tests，0 failures，0 errors，0 skipped；
- `:app:assembleDebug` 通过；
- LSPosed metadata scope 与 APK `scope.list` 一致：`system`、
  `com.android.systemui`；
- Bridge main source、Provider runtime 与最终 APK DEX 的 Phase 5 禁止字符串均为 0；
- Debug APK SHA-256：
  - Bridge：`1C97083F62FC095EAE279A5592EE2C7CB4B71C9A9AFDED8DBF74570E735D9DA3`；
  - LX Provider：`F6EFFADDA4569611C5ED763D2E7D487D25240FCA4249AAC811AE6AE7033ACB47`。

架构 guard 现在扫描整个 `app/src/main`、Manifest、Gradle 与 `scope.list`，禁止：

- Provider applicationId；
- direct-v4 action 与 `lyricprovider/` source；
- protocol / ingress / registry / sender；
- module envelope；
- player runtime / adapter registry。

## 真机收尾结论

1. 各 4.0 Provider 的原生 `lyricInfo` 消费链已在 Phase 4 分播放器真机关闭；Bridge
   main/runtime 对 Provider 不存在代码、Gradle、Manifest 或广播依赖，因此禁用 Bridge
   不会改变该原生链，无需为 Phase 5 重复一轮相同的 Provider 获取验证。
2. 启用 Bridge 的全 Provider 回归已完成。用户最终确认 LX 切歌降级、整表平滑滚动、
   实时行位置，以及 Spotify 实时行高亮、长歌词滚动均恢复正常。
3. 最终运行时只存在 player MediaSession `lyricInfo` → SystemUI → Bridge 通用增强；
   direct-v4 action/receiver/protocol/sender、module envelope 与第二份歌词提交路径已删除。
4. Halcyon 仅支持标准 `lyricInfo`；Flamingo 旧 v4-only 接入按 4.0 不兼容处理，这属于
   已声明兼容边界，不是 Phase 5 遗留。
5. 2026-08-29 最终门禁复核：Bridge forbidden hits 0、Provider runtime v4 hits 0、
   scope 为 `system, com.android.systemui`、397 tests（0 failures / 0 errors）、
   `diff --check` 0 errors。Phase 5 正式关闭。
