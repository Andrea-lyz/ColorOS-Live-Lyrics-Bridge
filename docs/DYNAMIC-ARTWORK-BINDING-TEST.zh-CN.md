# 动态封面切片 B：真机绑定测试步骤

日期：2026-10-03。分支：`feat/dynamic-artwork-provider`。

本轮测试当前媒体卡/沉浸页与精确 MediaSession 的绑定，不展示动态视频。
使用 Bridge 测试 APK 即可；本地视频插件属于前一阶段的 App 预览测试。

## 测试包

当前修复文件：`artifacts/ColorOS-Live-Lyrics-Bridge-artwork-binding-B-20261003-fix4-debug.apk`。
原初版、`diagnostics2` 及 `fix3` 保留作此前测试对照。
包名：`io.github.andrealtb.lockscreenlyrics`；版本：`4.4.0 / 144`；Android Debug 签名。

这是本地测试包。若当前安装包的签名不同，会出现覆盖安装失败；先在 Bridge 配置备份页导出配置、
记录 LSPosed 作用域，再切换测试包。测试操作由用户执行。

## 准备

1. 安装测试包，在 LSPosed 中确认 Bridge 已启用，作用域仍为 `system`、`com.android.systemui`。
2. 打开 Bridge 的调试日志页，开启“启用调试日志”，勾选 `media`，点击“保存并应用”。
3. PC 连接 ADB，在工作区根目录打开 PowerShell，运行下面的抓取命令。先启动抓取，再重启系统界面，才能保留初始化日志。

```powershell
.\scripts\capture-lyrics-log.ps1 -Provider bridge
```

多设备连接时加 `-Serial <设备序列号>`。默认输出为 `logs/lyrics-log-时间戳.txt`，测试结束按 Ctrl+C。
这个命令会临时开启所选 Tag 的 VERBOSE、清空旧日志并抓取完整窗口，结束时恢复 INFO。
本轮新事件位于完整日志中；脚本旧 `-ArtworkProbe` 的精简过滤针对 LX/Poweramp 修图，不能作为本轮完整证据。

4. 保持抓取终端运行，在 Bridge 主设置点击“重启系统界面”，等待锁屏恢复。
   若首次启用后此按钮未响应，先手动重启手机让模块加载，再重新进行第 2～4 步。
   手机完整重启会中断 ADB/logcat，不能把中断前的日志当成重启后的启动窗口。
5. 先用一个日常可正常显示锁屏媒体卡的播放器；现有歌词 Providers 保持原来的配置。

## 操作顺序

| 顺序 | 操作 | 重点记录 |
| --- | --- | --- |
| 1 | 播放一首歌，锁屏，停留普通媒体卡 5～10 秒 | 普通卡是否有 SESSION_BOUND；静态封面、控制按钮是否正常 |
| 2 | 打开沉浸音乐页/大封面歌词页，停留 5～10 秒，返回普通卡 | 沉浸页是否绑定；两个位置是否指向同一会话；返回有无卡顿 |
| 3 | 在同一专辑连续切两首歌，每首等 3～5 秒 | 同一宿主/会话的 trackGeneration 是否递增 |
| 4 | 快速连续切歌 3～5 次，最后一首等 5 秒 | 允许过渡期 FALLBACK；最终能否稳定绑定，无持续循环或串曲迹象 |
| 5 | 暂停 3 秒再播放，重复两次 | playing 是否相应改变，稳定宿主下不应制造新的歌曲 generation |
| 6 | 打开另一个播放器并播放，再切回原播放器 | 新 token 应建立新的 sessionEpoch；不能一直停留在旧会话 |
| 7 | 播放一首没有歌词的歌，检查普通卡和沉浸页 | 会话观察应独立于歌词；缺少可靠歌曲身份时记录 FALLBACK 原因 |
| 8 | 退出沉浸页、解锁、息屏再亮屏，然后重新进入 | 原界面/AOD 行为正常；重新进入可以重新绑定，无崩溃/持续刷屏 |

建议记录每一步发生的时间和使用的播放器，便于对齐日志。
同包多 session、分身或多用户场景有条件时另开一个复现窗口；此次不需要为测试更改系统或播放器数据。

## 如何读日志

增强诊断版增加本地 `ownerId` 与 `scope`。同一个 host 的注册、Flow、关联判定及会话解绑使用同一 ownerId；
session 自己的生命周期使用独立 scope=session，按 sessionEpoch/tokenRef 对齐。
tokenRef 是当前 SystemUI 进程内的本地编号，既不是原始 token，也不在进程之间比较。

这版按 owner/event 去重；变化的状态不再共享原来的全进程 3 秒窗口。
每个 owner 每秒最多 24 条变化，重复状态不会反复输出；超限后下一条带 `diagnosticLimited`，避免逐帧刷屏。
原歌词/渲染日志的节流保持现有行为。

建议先顺着以下链路检查：

```text
ARTWORK_RUNTIME_STATE / ARTWORK_SOURCE_SNAPSHOT
ARTWORK_SECTION_HOOKS_INSTALLED
ARTWORK_SECTION_SEEN -> ARTWORK_HOST_REGISTERED | ARTWORK_HOST_REJECTED
ARTWORK_HOST_ATTACHED -> ARTWORK_FLOW_BOUND | ARTWORK_FLOW_REJECTED
ARTWORK_HOST_MODEL -> ARTWORK_ASSOCIATION_CHECK
ARTWORK_SESSION_CREATED -> ARTWORK_CONTROLLER_REGISTERED -> ARTWORK_CONTROLLER_INITIALIZED
ARTWORK_METADATA_CHECK -> ARTWORK_SESSION_BOUND | ARTWORK_FALLBACK
ARTWORK_CONTROLLER_UNBOUND -> ARTWORK_SESSION_RELEASED -> ARTWORK_CONTROLLER_UNREGISTERED
```

具体原因现在可区分为缺少/重复候选、source title/artist 不一致、缺少 media ID 且 fallback 关闭、
card 与 controller 文字不一致、相同 ID 的文字改变、时长不一致、Flow 候选数量及反射错误类型。
`ARTWORK_OBSERVER_ERROR` 会说明此前被保护性捕获的观察异常；不输出异常 message 或堆栈中的私人数据。

复测优先做两件事：

1. 普通卡 → 沉浸页 → 返回：核对 IMMERSIVE 是否走到 SECTION_SEEN，以及实际被哪个门禁拒绝。
2. 同专辑切歌/暂停恢复：若 sessionEpoch 变化但 tokenRef 相同，检查 CONTROLLER_UNBOUND 的 reason，
   区分暂态关联失效导致重建和真实 token 更换。

fix4 的预期：

- 沉浸页 FLOW_BOUND 带 `access=stable_section_input`，通过 VM 的普通快照 accessor 与 section Map
  交叉核对并绑定已有输入。不调用创建派生 Flow 的工厂，也不启动额外协程订阅；普通卡保留原输入路径。
- 在沉浸页连续快速切歌至少一分钟，同一 attach 内 `watchedCount` 保持为 1 或 2，不能逐次增长。
  每首最终稳定后应继续出现当前模型/绑定更新，不能等退出重进才刷新。
- 实际 detach、dispose 或关闭观察时出现 `ARTWORK_FLOW_RELEASED releasedCount=... watchedCount=0`；
  单纯切换可见性未触发 detach 时不强求释放事件。重进后可重新绑定，旧输入回调不能恢复旧模型。
- 切歌时 card/source 文字短暂不一致产生 `source_card_transition controllerRetained=true`。
  token/user/package 不变时不注销 controller 或重置 policy；旧展示状态立即失效。
- 无 media ID 时，必须 card/source/controller 的 title、artist 一致且时长已知。
  保持至少 500ms 后后台读取当前 controller metadata 再复核，日志包含 `ARTWORK_FALLBACK_RECHECK`。
- 若有 DISPLAY_TITLE/DISPLAY_SUBTITLE 与 TITLE/ARTIST 冲突，或同 artist、相近时长只改变 title，保持静态：
  `display_metadata_conflict` / `fallback_title_change_ambiguous`。
- 可靠 ID 暂时消失也保持静态，不能自动切换身份命名空间。

本轮按顺序复测：开沉浸页 → 同专辑正常切歌 → 快速切歌 → 暂停/恢复。
相同 token 的 sessionEpoch 应保持，真实切歌稳定后 trackGeneration 增加；
同名同歌手且时长相近的无 ID 曲目可能因身份歧义保持静态，这是保守门禁。

初始化应出现：

```text
ARTWORK_CAPABILITY_RESOLVED source=pre_ums_seedling
ARTWORK_CAPABILITY_RESOLVED model=flat|info_progress immersiveResolved=true|false display=disabled
```

普通卡与沉浸页的正常绑定事件为 `ARTWORK_SESSION_BOUND`。
测试包提供 `surface`、`sessionEpoch`、`trackGeneration`、`requestRevision`、`surfaceEpoch`、`pluginEpoch`、`playing`。
这些都是位置/状态和本地计数，不输出曲名、原始 media ID 或 token。

- 同一播放器同一会话切歌：sessionEpoch 通常不变，trackGeneration 增加。
- 同一首歌的 album/duration 补齐：requestRevision 可以增加，不应当作新歌。
- 暂停/播放：playing 改变，稳定绑定下 generation 不应因此改变。
- section 重建、退出/重进会产生新的 surfaceEpoch；不同 surface 的 generation 不应直接相互比较。
- 无法精确关联、字段暂态、source 不一致时会出现 `ARTWORK_FALLBACK`。
  `association_unavailable`、`metadata_unavailable` 或短暂 `metadata_unstable` 可出现在切换期。
- fix3 在精确会话绑定下启用受控无 ID fallback；缺字段、文字/时长暂态、显示字段冲突或身份歧义仍可出现 `metadata_unstable`。
  结合具体 META reason 判断，不能单凭缺少 SESSION_BOUND 判定全部不兼容。
- `ARTWORK_CAPABILITY_UNSUPPORTED` 表示某一能力未通过本机匹配；保留完整启动窗口分析。

**没有动态动画是当前预期。** `display=disabled` 应保持出现，不以 App 预览成功替代 SystemUI 绑定验收。

## 测试结束与反馈

1. PC 终端按 Ctrl+C，等脚本恢复所选 Tag 为 INFO。
2. 关闭 Bridge 调试日志并“保存并应用”，结束只读会话诊断。
3. 提供完整 `logs/lyrics-log-时间戳.txt`，以及：
   - C16/C17 和 Android 版本、SystemUI 与 SystemUIPlugin 版本（已知时）。
   - 测试播放器及版本、普通卡/沉浸页的结果、首次异常步骤和时间。
   - 是否出现 SystemUI 重启、卡顿、静态封面/歌词/按钮/AOD 回归。
4. 正常也应反馈日志；它用于确认宿主与会话确实绑定，而不是仅确认模块加载。

本包构建与签名校验不代表设备验收。没有自动安装、重启 SystemUI、播放或抓取设备日志。
