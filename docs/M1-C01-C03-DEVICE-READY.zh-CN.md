# M1 续实施：C-01/C-02/C-03 推进至实机验收入口

> **设备进展（2026-11-14）**：Salt 的 `Application.attach` 诊断 NPE 已修复，修正版经用户实机确认专用链路恢复供词；详见 [Salt 回归记录](<../../ColorOS-Live-Lyrics-Providers/docs/M1-SALT-ATTACH-DIAGNOSTICS-REGRESSION.zh-CN.md>)。Bridge 侧安装/注入身份与短时诊断导出同样已在设备上跑通。其余 C-01/C-02 项与跨机型仍待验证；总览见 [任务交接](<../../HANDOFF-M1-AGENT.zh-CN.md>)。
>
> **后续设备纠正（2026-11-13）**：此前可见歌词由 Universal 对 Salt 的处理掩盖，不能验收专用 Salt。旧 M1 Salt 候选存在诊断阻断业务启动的 NPE，已撤回；根因、修复和新候选见 [回归记录](<../../ColorOS-Live-Lyrics-Providers/docs/M1-SALT-ATTACH-DIAGNOSTICS-REGRESSION.zh-CN.md>)。本报告的本地测试事实保留，不能当设备通过。

日期：2026-11-13。承接 [上一批复核记录](<M1-C01-C02-IMPLEMENTATION.zh-CN.md>)。使用 lsposed-mod-dev，并按实际 Kotlin/Compose 变更使用对应边界/状态技能。

**当前结论：本地实现、测试及下列优化检查已通过，下一步必须由用户实机核对平台 IPC、加载窗口和实际显示。不是整个 M1 或全型号兼容已验收。** 本轮不重做 scope 试验，不连接、安装、强停、清数据或重启手机，不发布。

## 1. C-01：Provider 自身运行态回读

新增 [provider-runtime-diagnostics 库](<../../ColorOS-Live-Lyrics-Providers/provider-runtime-diagnostics/build.gradle.kts>)，不是新 APK。覆盖 12 个共享专用模块、Readify 例外和 Universal 的独立 system_server 流程。

- 每个模块以自身 applicationId 声明 signature 级 READ_RUNTIME_DIAGNOSTICS 权限和 uses-permission；页面不导出。已对 14 个最终歌词 Provider APK 的实际合并 Manifest 核对，不只检查源码。
- 进程仅在正常模块加载/原有上下文捕获点注册带发送者权限保护的动态接收器；没有新增播放器 Hook，没有 scope、Hot Reload、播放或启停动作。
- App 只对自身编译期 scope.list 内目标发送 REGISTERED_ONLY 显式包查询，system 映射 android；不启动宿主，只读 2.5 秒窗口，可反复刷新，无一次性提交标记。
- 回包仅有模块构建、模块/进程纪元、框架、安装器阶段、正常消费者实际消费过的配置 revision。requestId、模块、声明内目标、进程与纪元受校验；最多 16 条回复，迟到/不兼容为 unknown。没有歌词、稳定 media ID、下载/解析命令或通用执行参数。
- 进程没有新协议/没有运行/不可达不等于“模块未启用”。installed build 与进程自报 buildId 比较；这是 in-process self report，**不是框架认证或供给链安全依据**。
- 配置 revision 在普通设置写入/消费点记录，查询不重读配置、也不制造已应用。Universal 引用其正常 source-config 消费，启动旧持久配置无 revision 时保留未知；Readify 未使用共享 Debug 配置时标不适用。
- Bridge 没有任何 Provider applicationId 路由表，不进入播放器。动态封面独立服务应用不被伪装成 LSPosed 注入模块；其现有服务协议未改。

## 2. C-01：加载组件与环境观测

[LoadedComponentFingerprints](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/diagnostics/LoadedComponentFingerprints.java>) 只在既有 SystemUI/package-ready 与已过滤的 OPlus loader 回调记下弱 loader 引用和纪元。

- 观察和普通查询不做整包哈希。只有用户明确开始短时诊断才后台采样已观察 loader 的 backing APK 文件，队列/文件数有界，旧 loader 完成不能覆盖新纪元。
- 不输出文件路径。指纹是采样时 backing 文件内容集合，不是指令内存认证。与安装组件集合不同只能显示 DIFFERENT_OR_DIFFERENT_CONTAINER_SET，不自动断言旧插件；反射/读取/预算失败为 unknown。

**2026-11-14 修正：插件槽读错加载器。** 旧实现在 `discover()` 里先取 `mBase` 再读 `pathList`。反编译核对（C16 `系统界面_16.99.12` 的 `OPlusPluginClassLoader` 构造器；C17 `jadx-systemui` 的 `PluginInstance$Factory.createClassLoader()` 第 205–226 行）显示：`OPlusPluginClassLoader` 本身是 `PathClassLoader`，dex 路径来自 `com.oplus.systemui.plugins` 的 `ApplicationInfo`；`mBase` 是 SystemUI 的基础加载器，只用于 `com.android.systemui.common/.log/.plugin` 共享包。因此插件槽实际指纹的是 SystemUI APK，与已安装插件比较必然得到 `DIFFERENT_OR_DIFFERENT_CONTAINER_SET`。现在只读被观察加载器自身的 dex 路径，不跟随 `mBase`，也不读父加载器。同一反编译显示只有 `com.oplus.systemui.plugins` 使用该类，其余插件是普通 `PathClassLoader`，不会进入插件槽，所以单槽语义不变。

- 回归：`LoadedComponentFingerprintsTest.pluginLoaderFingerprintsItsOwnPathNotTheSystemUiBase`（构造带 `mBase` 的插件形加载器，断言只返回其自身 APK）。Bridge `:app:testDebugUnitTest` 937 tests / 0 fail / 6 skip，`:app:assembleDebug` 通过；sourceDigest `8ae3c741…`。未做变异验证。
- 剩余不确定：`createClassLoader()` 用 `LoadedApk.makePaths(..., true, ...)` 生成路径，可能附带共享库 APK；若设备上插件有共享库依赖，加载器路径集合会多于“已安装插件 sourceDir + splits”，比较仍会显示 `DIFFERENT_OR_DIFFERENT_CONTAINER_SET`。**需要设备验证**：短时诊断后插件行应为 `SAME_CONTENT_SET`；若不是，再看 `apkCount` 是否大于已安装侧。
- App 后台尝试白名单 OEM version property，失败为 unknown，不从 SDK 推断 ColorOS；前台页面可观察原始铰链角度，离开即注销，不将角度或传感器缺失猜成厂商姿态。

**2026-11-14 补充：AOD 记录。** 原“渲染器观测 AOD 低帧率模式”只保留最后一个值，而且这个模式由灭屏广播和绘制路径驱动，分不清“AOD 正在显示”和“彻底熄屏”。新增 [AodModeObservation](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/diagnostics/AodModeObservation.java>)：只在渲染器 `setAodLowFrameRateMode` 真正切换时记录切换次数与最近切换时间（无逐帧写入、无分配、异常不外泄），回包新增 `cll_aod_mode_transitions` / `cll_aod_mode_changed_at` / `cll_aod_mode_current`，短时诊断导出同步。App 侧环境快照新增 `aosp_doze_always_on_raw`（AOSP 原始键，读取失败为 unknown，不映射成“AOD 已开”）。折叠形态仍为 unknown；本机无铰链传感器。回归：`AodModeObservationTest`。

## 3. C-02：全部固定功能槽位都有正常执行点

补齐翻译按钮和动态封面，保留前批自绘、取色、上浮、逐行与翻译进度。状态不通过额外业务调用、重复解析或强制重绘产生。

- 翻译按钮在现有可见性策略返回后记录 PRESENTATION_ASSIGNED，包含模型缺口、配置关闭与数据缺失；不是“屏幕已经可见”。重绑定后不再属于翻译按钮时清除观测，不误伤原生收藏。
- 动态封面分别记录生命周期和 card/immersive 绑定准备；session/binding ready 只为 PREPARED，不声称 decoder 已实际呈现。歌词模型清除不会清除独立动态封面观测。
- 只读查询沿原有签名权限状态通道，保持 last-observed-item 范围；准备、调度、绘制提交、可见性赋值不能外推成视觉验收。

**2026-11-14 补充：按对象聚合。** 原 [CompatibilityStatus](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/diagnostics/CompatibilityStatus.java>) 每个功能只有一条记录，任何一行歌词或任何一个卡片写入都会覆盖前一个，诊断页显示的不一定是眼前那一行。现在每个功能最多保留 8 个对象各自的最近观测（同一对象原位更新、无分配；超出时替换已回收或最旧的对象）。快照仍以“最近一次观测”作为主值，另给出 60 秒内仍存活的对象数 `liveOwners` 与各结果计数 `outcomeSummary`（只含枚举名与数字）。`forgetOwner` 只清该对象，`requested` 变化清空该功能全部对象。没有新增“当前焦点行”的判定：那需要改动入口类的绑定逻辑，留到 E 系列迁移时由新的 owner 处理。回归：`CompatibilityStatusTest` 新增 4 项（另一行不抹掉焦点行、同对象原位更新且有界、过期对象退出汇总、回包不含对象标识）。离线摘要脚本仍按白名单丢弃新字段，未改。

## 4. C-03：短时缓存、预览导出与离线摘要

[ShortDiagnosticSession](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/diagnostics/ShortDiagnosticSession.java>) 默认关闭，用户主动开始后最多 60 秒。最多 128 条元数据事件/16 个通用标记，重复聚合、快速变化采样、容量省略均显式记录；无私人自由文本，不修改日志开关或增强配置。

[真实调试设置控件](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/ShortDiagnosticsControls.java>) 通过独立签名保护动作进行 start/mark/stop/snapshot；普通状态查询仍只读。可重复开启，不清数据；进程重启/旧协议不假定旧会话仍在。导出经用户选择位置及预览，只写白名单身份、实际配置、能力、加载组件和事件。

[离线摘要工具](<../scripts/summarize-lyrics-diagnostics.py>) 仅 Python 标准库，支持新 Bridge/Provider JSON 和旧 LSPosed 日志 ZIP。重点约束：

- 不执行输入、不开网络、不解压到文件系统；严格 JSON 重复键检查、归档目录/解压量/单文件/行/事件预算与缺口标记。
- 支持时间戳带冒号的合法模块日志名与 Windows ZIP 分隔符，但拒绝越界/绝对路径；优先模块/verbose/诊断日志，避免先被配置 dump 用尽预算。
- 原日志 message、歌词、媒体身份、token/cookie、URL query、私人路径和未知原文不进入输出。事件/原因/阶段/配置均白名单；旧输入缺少构建或纪元时标未知，不用相同事件序列证明重放，不按跨进程时间戳推断因果。
- [23 项 Python 回归](<../scripts/test_summarize_lyrics_diagnostics.py>) 通过；已有用户日志归档也只生成 [脱敏摘要](<../../artifacts/m1-runtime-20261113/offline-existing-archive-final/diagnostic-summary.json>)，覆盖预算/旧字段缺失明确可见，不冒充完整或视觉验收。

## 5. 实际本地证据

| 范围 | 实际结果 |
| --- | --- |
| Bridge JVM + Debug | [最终回归日志](<../../artifacts/m1-runtime-20261113/bridge-runtime-regression-2.log>) 成功；936 项，0 失败、6 跳过，跳过不算覆盖 |
| Provider 矩阵与独立封面 | [完整回归日志](<../../artifacts/m1-runtime-20261113/providers-runtime-full.log>) 成功；[统计](<../../artifacts/m1-runtime-20261113/provider-test-summary.json>) 896 项，0 失败/错误/跳过；其中新自诊断库 21 项 |
| R8/Release 编译 | [Bridge R8](<../../artifacts/m1-runtime-20261113/bridge-r8-check.log>)、[Salt/Universal R8 与 Readify Release 编译](<../../artifacts/m1-runtime-20261113/providers-r8-check.log>) 通过；没有打包或签名正式 Release，不能外推成所有 Provider 已做正式 R8/设备验收 |
| 包身份/权限 | [16 APK 清单](<../../artifacts/m1-runtime-20261113/verified-apks.json>)、[14 个独立权限/非导出页面校验](<../../artifacts/m1-runtime-20261113/verified-security.json>)；原 Xposed 入口和生产 scope 未改 |
| 源码身份独立复算 | Bridge 285 个、Providers 705 个输入均匹配；sourceDigest 分别 c9037c2af49cc0e3aa996e1a40b367aab5eac6a524c798546a53cb6255ce69a1 / e9b97b58463c0a2a5b22993071e11a2966860eadade8a38c5ba446c82cc8c61b；均 dirty |
| 隐私/离线 | [最终 23 项结果](<../../artifacts/m1-runtime-20261113/offline-tests.log>) 通过；拒绝覆盖报告的错误是有意测试，不是工具未通过 |
| 候选 | [实机说明与两份真实模块 Debug 包](<../../artifacts/m1-runtime-20261113/test-kit/README.zh-CN.md>)；签名已核验且 DEX 含实际诊断类，不是仅写 Manifest |

本轮修复了新增测试使用不兼容 Files.writeString 的编译问题，以及旧归档日志选择/合法冒号文件名被漏掉的问题，最终相关检查已重跑。既有 Gradle 实验/弃用提示及无关 Kotlin 警告仍存在。

## 6. 现在为什么必须用户实机验证

本地 Robolectric/协议测试不能证明 Android 17/ColorOS 的实际 UID 签名权限投递、现存旧注入进程/新 APK 的更新行为、OPlus loader 私有字段可读性、传感器扩展和真实画面。没有这些证据就不能把 M1 或全型号兼容勾为通过。

最小只需 Bridge + 已有 Salt，按 [验收指南](<../../artifacts/m1-runtime-20261113/test-kit/README.zh-CN.md>) 先同证书覆盖、Provider 反复只读回读、再 Bridge 60 秒记录与导出。签名不同就停止覆盖，不卸载、不清正式应用数据、不要求交出密钥。无需启用其他 Provider、重做 scope 或默认收集整包私人日志。
