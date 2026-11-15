# 下一大版本更新计划：ColorOS 兼容闭环、上手引导与 Provider 商店

> 状态：**M0 部分用户设备确认；M1 C-01/C-02/C-03 代码与本地回归通过，Provider 只读回读/短时诊断导出/构建身份对照已在用户设备跑通（含一次诊断启动回归的定位与修复）；不是全型号或整项设备通过**。编制日期：2026-11-11；实施/证据更新：2026-11-14。2026-11-14 追加第五条主线“代码结构与性能治理”（第 15 节，仅方案）。当前接手入口见 [任务交接](<../../HANDOFF-M1-AGENT.zh-CN.md>)。目标版本暂称 **5.0.0**，尚未修改正式版本、发布契约或公开资产。
> 本文承接用户明确提出的四项需求；不是发布公告，不证明已有正式 UI、商店或全专用模块动态作用域。已完成计划、开发技能安装与隔离 Debug 探针；2026-11-13 新增用户设备的 staticScope=true 单次允许闭环证据（第 14.0 节），不是整个 M0 或全设备验收。本次仅记录用户材料，不代安装、不发布、不操作真机。

## 0. 版本目标与完成定义

下一大版本从“配置丰富的 Bridge + 用户自行寻找 Provider”升级为“**ColorOS 原生歌词增强 + 可解释的兼容状态 + 引导式插件管理**”。

必须同时交付：

1. ColorOS 系统体系内不同型号设备的兼容优化；不扩展到其他 ROM 或通用 Android 锁屏方案。
2. 新安装，以及从旧版本首次更新到本大版本时的新手引导；保留既有用户配置，支持跳过、恢复和重新进入。
3. Bridge 内置 Provider 商店，统一下载、安装和独立更新。下一大版本的公开 Release **不再单独附带 Provider APK 或 Provider 全量 ZIP**。
4. **仅专用播放器 Provider** 进入动态作用域编排；通用歌词与动态封面 Provider 保留独立桌面入口、配置与运行方式，商店仅管理其下载安装更新，不接管其作用域或内部配置。
5. 为以上流程新增首页、商店、状态/诊断与引导 UI：**新管理页面采用 Kotlin + Jetpack Compose Material 3，选择性采用 Material 3 Expressive，现有 Views 页面渐进迁移**；逐步拆分设置页职责，不借机重写歌词渲染器。
6. Bridge 代码结构与性能治理（2026-11-14 用户追加）：以拆分 `LockscreenLyricsModule.java` 为重点，做保持行为的结构迁移和有采样依据的性能修正；方案见第 15 节，尚未落代码。

完成标准不是“下载成功”或“作用域请求返回成功”，而是：用户能识别当前插件版本、安装/授权/生效阶段、下一步操作，以及功能未生效的具体条件；已知良好设备的基础链路不回退。

### 0.1 首次编制时源码基线（2026-11-11）

- Bridge：44aca8e4828a1b1257264bcc629c7cf5f7778061，工作树在本轮开始时干净。
- Providers：b9f82cc2208df4c767c6a371db89dae03555a994；已有未跟踪 artifacts 目录保留不动。
- Bridge 当前版本：4.5.4 / 149。Provider 矩阵当前包含 14 个歌词模块，其中 13 个专用播放器模块、1 个通用歌词模块；另有 1 个独立动态封面应用。
- 当前主链路在开发者本地设备可用属于用户反馈的已验证基线；不外推为所有 ColorOS 型号已验收。
- 本文采用当前矩阵，不沿用“旧套件版本号等于每个 Provider 实际版本”的假设。现有通用 Provider 已是独立内部版本 1.1.3 / 5。

### 0.2 已确认的 UI 技术选型（2026-11-11）

用户已确认采用本轮推荐：**Compose Material 3 为新管理界面的标准组件底座，Expressive 适度使用，旧 Views 渐进迁移**。这是对原计划“新增页面也继续使用 Views”的调整，不是所有 UI/运行代码都转换为 Compose；Compose UI 尚未接入，正式管理面与歌词运行代码未改；M0 的独立 scope 调试仪表不作为 UI 原型。具体范围、互操作、依赖冻结与验收见第 8、10–13 节。

## 1. 边界：管理面可以扩展，歌词运行面不回退

### 1.1 保留的运行架构

~~~text
专用 Provider → 播放器自己的 MediaSession / MediaMetadata["lyricInfo"]
通用 Provider → 在自己的 system_server 链路补全选定播放器的 metadata
                                      ↓
                              ColorOS SystemUI
                                      ↓
                          可选 Bridge 通用增强

动态封面应用 → 已有 artwork-contract / 服务授权链路 → Bridge
~~~

- Bridge 的 Hook scope 仍只有 system 与 com.android.systemui；不因为商店接入播放器进程。
- Provider 的播放捕获、网络取词、identity、generation 和异步提交仍由对应 Provider 持有；Provider 不依赖 Bridge 安装才能发布标准 lyricInfo。
- 商店需要的 Provider applicationId、下载地址、签名和宿主清单，只能存在于 **Bridge 普通 App 管理面**。不得注入为 SystemUI 的歌词来源路由表或私有 transport。
- 不恢复 direct-v4、旧歌词广播、运行时外置脚本或动态加载下载 APK 中的 Hook 类。
- 安装包经 Android 包安装器安装；不得用 DexClassLoader 将 Provider 装入 Bridge。
- Debug 默认关闭，诊断开关不得改变选词、时钟、渲染、AOD 或降级决策。

### 1.2 不纳入本版的范围

- 非 ColorOS ROM、没有厂商原生歌词页的设备，以及跨厂商锁屏替代实现。
- 静默 Root 安装、无人确认地启用模块/扩 scope、自动重启 SystemUI/system_server。
- 云端下发可执行兼容补丁、任意第三方插件市场、任意 URL 安装。
- 默认实时推送服务、账号系统、强制全量插件更新或自动卸载不兼容插件。
- 一次性重写所有旧 Views 页面，或将 SystemUI Hook、歌词 Canvas 渲染、AOD 与 Provider 业务迁移到 Compose；不调整 Hook API 大代际或改写所有 Provider 播放策略。

## 2. 首次规划时的代码基线与迁移落点

下表保留首次规划时的静态基线；不代表实施后的当前状态。最新 M1 代码、验证结果和未完成项见 [C-01/C-02 实施记录](<M1-C01-C02-IMPLEMENTATION.zh-CN.md>)。

| 领域 | 已确认现状与源码 | 下一步及原因 |
| --- | --- | --- |
| Bridge 入口/权限 | [Manifest](<../app/src/main/AndroidManifest.xml#L1-L41>) 只有现有模块设置入口；没有 INTERNET、REQUEST_INSTALL_PACKAGES 或商店安装组件 | 增加普通 App 管理入口、网络与安装权限；不要把网络放入 Hook 启动路径 |
| 设置页职责 | [主设置 Activity](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/LyricUiSettingsActivity.java#L190-L257>) 持有草稿、预览、模块状态和外观配置；当前约 3,283 行 | 作为外观页保留，新增管理首页/商店，提取状态和流程控制器 |
| UI 基础 | [SettingsBaseActivity](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/SettingsBaseActivity.java#L38-L110>) 已统一语言、窗口与 edge-to-edge；[当前依赖](<../app/build.gradle.kts#L94-L105>) 使用 Material Components 1.11.0，[主题](<../app/src/main/res/values/themes.xml#L3-L17>) 已是 Material 3 浅色 | 新管理页改用 Compose Material 3；保留既有浅色/语言/窗口约束并统一 tokens，旧 Views 分步接入；不为商店改动 LyricUiConfig |
| 诊断 | [StructuredBridgeLog](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/diagnostics/StructuredBridgeLog.java>) 已有双日志出口、区域开关、脱敏与节流；[现有调试页](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/BridgeDebugSettingsActivity.java>) 已能查询配置应用状态 | 补运行构建身份、功能终态、短时诊断与统一导出，不另造常驻逐帧日志 |
| 兼容解析 | [SystemUI 解析/缓存](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java#L1030-L1060>)、[C17 角色校验](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/systemui/artwork/ArtworkC17Access.java#L31-L99>) 已有 DexKit/旧路径及实际 View 归属验证 | 在其上统一 capability/result 模型，不推倒重写，也不只看型号名 |
| Provider 范围 | [机器矩阵](<../../ColorOS-Live-Lyrics-Providers/release/v5-provider-matrix.json>) 的 xposedStaticScope 当前统一为 true | 改成每类/每模块 scope 策略；专用模块与通用模块不能一起全局翻转 |
| 构建校验 | [统一 convention](<../../ColorOS-Live-Lyrics-Providers/gradle/provider-app-convention.gradle.kts#L61-L98>) 按矩阵全局校验 staticScope，另校验 scope.list | 同时迁移生成逻辑、校验器和测试，不能只手改 module.prop |
| 模块 App 的 LSP 服务 | [ProviderModuleApplication](<../../ColorOS-Live-Lyrics-Providers/provider-settings-api102/src/main/kotlin/io/github/andrealtb/coloroslyrics/provider/settings102/ProviderModuleApplication.kt>) 已注册 XposedServiceHelper；[共享设置模块](<../../ColorOS-Live-Lyrics-Providers/provider-settings-api102/build.gradle.kts#L40-L46>) 将 service 限于 App 路径 | 可复用本模块自己的服务接入，不引入被 Hook 播放器路径 |
| Readify 例外 | [ReadifyApplication](<../../ColorOS-Live-Lyrics-Providers/player-readify/src/main/java/io/github/andrealtb/coloroslyrics/provider/readify/ReadifyApplication.java>) 是独立 Application；[其构建](<../../ColorOS-Live-Lyrics-Providers/player-readify/build.gradle.kts>) 应用 convention 但不依赖共享设置 UI | 必须单独接入管理/授权 adapter，不能只改公共 Application 后声称全矩阵完成 |
| 独立入口 | [Universal Manifest](<../../ColorOS-Live-Lyrics-Providers/universal-provider/src/main/AndroidManifest.xml#L26-L34>) 和 [动态封面 Manifest](<../../ColorOS-Live-Lyrics-Providers/artwork-provider-am/src/main/AndroidManifest.xml#L12-L17>) 都有 LAUNCHER | 原样保留；Universal 的 system scope 不参加专用播放器动态授权；动态封面是服务应用，不是待勾选播放器 scope 的模块 |
| API 版本 | [Provider 依赖目录](<../../ColorOS-Live-Lyrics-Providers/gradle/libs.versions.toml#L19-L20>) 使用 libxposed api/service 102.0.0；[Bridge 依赖](<../app/build.gradle.kts#L94-L105>) 尚无 service 客户端 | 按锁定版本验证，不把上游 master 新版本说明当作已安装框架能力 |
| 分发耦合 | [Bridge 契约](<../release/bridge-release-contract.json>) 目前规定 16 APK / 19 资产；[校验器](<../scripts/validate-release-contract.ps1#L46-L49>) 强校验数量与同套件关系 | 迁移为 Bridge Release 与 Provider catalog 两份发布契约 |
| 当前 CI | [Bridge 发布流程](<../.github/workflows/release.yml#L38-L116>) 冻结 Provider 源码；[Providers CI](<../../ColorOS-Live-Lyrics-Providers/.github/workflows/android-release.yml#L50-L123>) 构建矩阵和 ZIP，主要上传 Actions artifacts | 增加独立、可审计的 Provider 正式发布和签名目录原子晋级；Actions 临时产物不是生产下载源 |
| 架构守卫 | [BridgeArchitectureGuardTest](<../app/src/test/java/io/github/andrealtb/lockscreenlyrics/BridgeArchitectureGuardTest.java#L32-L45>) 当前禁止整个 app/src/main 出现 Provider applicationId | 按管理面/Hook 面重划测试边界；旧 transport 禁令仍全局保留，不靠字符串拼接逃避守卫 |

## 3. 目标模块分类与职责

| 类型 | 当前范围 | 商店职责 | scope/设置职责 |
| --- | --- | --- | --- |
| 专用播放器 Provider | 矩阵中的 13 个专用模块 | 下载、安装、更新、宿主匹配、启动模块自身授权流程、显示结果 | Provider 自己请求自己模块的 scope；既有播放/调试策略归 Provider |
| 通用歌词 Provider | universal-provider | 只提供下载安装更新及版本状态 | 独立 App 选择播放器和歌词源；system scope 维持独立管理，Bridge 不代加播放器 scope |
| 动态封面 Provider | artwork-provider-am | 只提供下载安装更新及版本状态 | 独立 App 配置/登录/绑定；已有 artwork 服务授权不等于 LSP 作用域 |
| Bridge | 本体 | 不作为 Provider 商品重复安装 | 自身保持 system / com.android.systemui；框架启用与原有配置不混进播放器 scope |

专用模块的完整首发清单：player-salt、player-cone、kuwo-music、player-lx、player-poweramp、player-metrolist、player-kugou、player-qq、player-netease、player-apple、player-spotify、player-qishui、player-readify。清单来源是上述机器矩阵，后续生成目录与包可见性声明也必须由矩阵导出。

**产品入口统一，不等于合并 APK，也不等于改变 Provider 的业务进程。**

## 4. 主线一：ColorOS 型号兼容专项（C 系列）

### C-01 构建身份与型号环境快照（P0）

- 为 Bridge 与所有 Provider 生成唯一 buildId：源码提交、可追溯的 dirty 摘要、构建变体、resolverRevision；最终 APK SHA-256 留在签名发布清单，不反向写入自身 APK。
- 分别显示“已安装构建”“注入进程观测到的构建”“配置已应用 revision”；安装更新不等于旧进程已经加载新代码。
- 型号是索引，不是全部适配判据。记录 ColorOS 版本/构建、SDK、SystemUI 与插件版本/内容指纹、框架版本及能力、屏幕与字体缩放、分辨率、导航方式、AOD/折叠姿态等与功能相关的维度。
- 不采集 IMEI、设备序列号等永久设备标识。组件哈希只在适当后台/缓存路径计算，不在绘制或启动热路径整包反复读取。
- 对相同“ColorOS 版本号/插件 versionName”但内容不同的型号，保留不同兼容记录。范围不扩大为其他 ROM。

C-01 当前实施（2026-11-13）：已补全矩阵身份、Bridge 安装/注入对照、Provider 自身签名保护只读回读及已观察 loader 的显式后台指纹采样；完整厂商姿态不可得时保持 unknown。代码/本地回归已过，平台 IPC、更新窗口和真实字段可读性必须实机确认，不标全项验收。详见 [当前记录](<M1-C01-C03-DEVICE-READY.zh-CN.md>)；[前批复核](<M1-C01-C02-IMPLEMENTATION.zh-CN.md>) 保留历史差距。

### C-02 统一能力与最终原因（P0）

每项增强输出 requested、supported、eligible、selectedPath、lastOutcome、reason、observedAt 与证据范围；未知、尚未就绪、不支持、用户关闭分别表示。

最小原因集合应覆盖：CONFIG_DISABLED、RAW_LYRIC_MISSING、LINE_PROGRESS_DISABLED、AOD_LOW_FRAME_RATE、HOST_NOT_FOUND、SNAPSHOT_PENDING、PALETTE_UNAVAILABLE、DATASET_UNSUPPORTED、FRAME_REJECTED、NATIVE_FALLBACK、TARGET_NOT_OBSERVED。

- Hook 安装、模型解析、颜色可读、实际进入自绘、应用配置、请求行重绘是不同阶段；不能把某个中间 marker 当最终效果。
- 最终原因在正常决策处产生，状态查询只读；不得为了诊断打开原本不执行的业务分支。
- 用现有受保护状态通道扩展聚合快照；Provider 运行状态通过其自身管理/诊断能力提供，不恢复歌词 transport。

C-02 当前实施（2026-11-13）：自绘/取色/上浮/逐行/翻译进度、翻译按钮、动态封面均已从正常决策点接入有界模型并本地验证。准备、调度、绘制指令、可见性赋值都不是视觉验收；未实际观测仍为 unknown。当前仍需真实画面及播放器动作回归。

### C-03 短时诊断包与离线摘要（P0）

当前实施（2026-11-13）：已落地默认关闭的 60 秒/128 事件缓存、通用标记、预览/用户选择导出，以及白名单离线 JSON/ZIP 摘要。23 项隐私/预算/旧输入回归通过；实际设备捕获和导出待验收，详见 [续实施记录](<M1-C01-C03-DEVICE-READY.zh-CN.md>)。

- 优先交付现有 LSPosed 导出包的离线摘要器，再提供手机“开启短时诊断 → 复现 → 标记异常 → 导出”流程。
- 详细事件缓存仅在用户开启诊断后启用，建议初始窗口 60 秒、每进程有界容量；具体预算经性能基线确认。只记录关键状态变化，不常驻逐帧打印。
- 包含构建/环境/实际配置、进程和启动 epoch、功能终态、关键链路事件、缺失项清单；导出前允许预览与删去附加信息。
- 优先使用单设备单次启动的 elapsedRealtime 与进程 epoch。不同 Provider 的 generation 不能直接视为全链路同一个计数器，关联不足标 unknown。
- 普通 App 无权默认读取其他所有进程日志。沿现有框架日志与经授权的采集能力收集；不可用部分标记“未采集”，不能假称完整同窗证据。
- 不记录完整歌词、原始媒体 ID、Token、Cookie、URL query 或私人媒体路径；不默认上传遥测。

### C-04 局部降级与恢复（P1）

- 在现有守卫上统一功能级失败范围：取色退手动色，上浮退普通绘制，按钮不支持保官方槽位，自绘不安全退官方歌词。
- 错误隔离到功能/绑定/组件版本；恢复绑定到明确事件并限次，不单次异常后全局永久拉黑，也不每帧无限重试。
- 更新插件/classloader、surface 交接、异步回调均验证所有权和 epoch。不要用一组固定延迟掩盖状态竞争。
- 不因首次透明、0×0 或过渡态强制重排官方列表；不重复接管滚动/行高/AOD 私有状态。

### C-05 兼容样本与回归（P1）

- 按实际 SystemUI/插件内容指纹建立只读契约样本库；可授权使用的 APK 留本地或私有环境，公开仓库只留合法的结构摘要/哈希/测试元数据。
- 增加重复、乱序、迟到、失效回调测试：歌词先到/封面后到、旧曲异步结果、新旧数据集交接、View detach/attach、快速切歌、暂停后补资源、AOD 往返。
- 关键不变量：不串曲/串封面、不越过 View 所属边界、失效回调不写当前状态、诊断开关不改变行为。
- 文本与状态机回放不等于 GPU/布局视觉验收。兼容声明对应的必需样本缺失时，不允许用 skipped 测试宣称已覆盖。

### C-06 ColorOS 型号验收矩阵（贯穿各阶段）

- 保留开发者本地已知良好设备；按不同组件指纹、屏幕/折叠能力与字体缩放组合选择少量代表型号。
- 以当前已有支持证据的 ColorOS 组件族为范围，未知型号先只读识别与安全降级；不承诺所有历史 ColorOS 都支持。
- 固定场景：冷启动首曲、锁屏进入、连续切歌、暂停/继续、译文显隐、逐行/逐字、AOD 往返、解锁再锁屏。
- 每次明确候选 APK 哈希、配置、框架版本和复现窗口；同一 RC 不给不同用户偷偷替换成不同内容。

## 5. 主线二：新安装/大版本更新引导（G 系列）

### 5.1 流程不是欢迎页轮播

~~~text
G1 环境检查与边界说明
 → G2 启用/确认 Bridge（框架确认由框架负责）
 → G3 选择自己实际使用的播放器
 → G4 安装匹配的专用 Provider，或说明独立通用/封面应用的使用方式
 → G5 专用 Provider 自请求 scope、回读、提示宿主进程重新启动
 → G6 用户播放一次并检查原生歌词/Bridge 增强的实际状态
~~~

- 第一步明确：只适用于项目支持的 ColorOS 原生歌词实现，需要支持项目 API 的框架；Bridge 不取代播放器，商店的“通用”不意味着全应用无条件支持。
- 识别已安装宿主，只推荐对应模块；未安装的宿主可浏览但不默认安装 Provider。
- 一个播放器已有专用 Provider 时，不默认引导再启用通用 Provider。无法可靠读取另一个独立应用的绑定状态时，标记“待用户确认冲突”，不猜测已消除冲突。
- Bridge 原生静态 scope 的启用提示与播放器 Provider 的动态 scope 步骤分开。
- 基础引导不要求开启字符上浮、逐字、Debug 或所有增强；相关开关依赖在用户进入对应设置时解释。
- 通用歌词与动态封面分支在下载安装说明后转为“请从独立应用入口完成配置/授权”；商店不嵌入其设置和 scope 页面。动态封面的已有 Bridge 侧消费设置继续保留，不等于接管来源应用。

### 5.2 新安装与升级迁移分开

引导状态单独持久化，不放入 LyricUiConfig，也不能只存一个 firstLaunch 布尔值。建议包含 flowRevision、lastAcknowledgedMajor、各步骤结果/证据时间、待处理安装/授权 requestId、可恢复的用户选择。

| 场景 | 行为 |
| --- | --- |
| 真正新安装 | 显示完整流程，可跳过/后退；未完成项留在首页，不锁死外观页 |
| 4.x 首次升级本大版本 | 先发现现有配置/已安装 Provider；展示更新引导与待修复项，不从零重装 |
| 同大版本补丁升级 | 不重复完整引导；仅迁移新 flowRevision 真正新增的必要步骤 |
| 目录/网络不可用 | 使用内置或上次已验证目录的有限信息，允许保留现有运行链路与稍后设置 |
| 从安装器/框架/独立应用返回 | 重新查询真实安装、服务和授权状态，不凭 Activity 返回码自动打勾 |
| 杀进程、旋转/折叠、系统回收 | 恢复事务与步骤，过期请求不能重复弹安装/作用域确认 |
| 用户跳过、拒绝或取消 | 明确剩余问题和重入入口，不反复强制弹窗；不显示虚假“全部完成” |

升级必须保留外观、字体、翻译记忆、清理规则、动态封面已有授权与旧 scope。目录接管已安装包不要求卸载；签名不一致时只提示，绝不自动清数据。

**引导完成度与运行健康是两个概念。** 用户可以完成说明但暂未播放；未观察到目标进程只能显示“待验证”，不能伪装成歌词链路已成功。

## 6. 主线三：Provider 商店（S 系列）

### 6.1 首发商品范围与版本策略

- 首版只收录项目官方 13 个专用模块、通用歌词模块和独立动态封面应用，共 15 项；具体集合仍由矩阵生成，不在客户端另写第二套业务清单。
- 每个商品以稳定 providerId + applicationId 标识；版本排序使用对应应用的 longVersionCode，显示其自身 versionName/buildId，不使用 Bridge suiteVersion 判断新旧。
- 新 scope/管理入口进入专用 Provider APK 后，必须为这些 APK 分别提升版本，不能以相同 versionCode 替换已发布二进制。
- 专用模块可以共享宿主家族（例如矩阵中的多个发行包），选择用户实际安装/使用的包；同一个包的修改版仍由原 Provider profile/进程策略判定，不靠商店包名放宽运行权限。
- 通用和动态封面只提供下载安装更新；保留其独立桌面图标与配置流程。专用模块不新增一排桌面图标，保留明确的 INFO/框架设置前门及必要的自身授权界面。

### 6.2 目录来源与供应链安全（发版硬门禁）

推荐生产形态：**官方 HTTPS 对象存储/CDN + 不可变版本目录 + 可验签 catalog**。实际域名、地域可达性与运营者由实施前确定；不是承诺本轮已经有服务器。

- 公开 GitHub/LSP Release 只向用户分发 Bridge 本体及其必要校验/说明资产，不再附单独 Provider APK。**（2026-11-14 用户决定 D1 = 方案 M）** Provider 商店的默认下载后端为 Providers 仓库自身的公开 GitHub Release，按 Provider 独立打 tag；可选地再套一层免费 CDN 作为加速镜像。完整性只看签名目录，不看托管。
- Providers CI 仍产出独立 APK，但正式发布到商店 artifact 存储。GitHub Actions 需鉴权、会过期的 artifacts 仅作构建/RC 中间产物，不作为客户端长期下载接口。
- HTTPS 与 APK SHA-256 不能单独代替目录真实性。目录需独立签名，客户端固定受信目录公钥；APK 还需校验证书、包名、versionCode、大小和内容哈希。
- 目录 envelope 固定规范：schemaVersion、catalogRevision、issuedAt、expiresAt、keyId、原始 payload 字节的编码与签名。签名覆盖确定字节序列，不让不同 JSON 序列化造成验签歧义。
- 使用已审核、适配最低 SDK 的签名实现；算法和密钥轮换先在 M0/M2 确认，不能默认最低 API 原生支持最新算法，也不能自行设计加密。
- 维护最后接受的 revision，拒绝重放降序目录；过期目录只可离线显示已安装状态，阻止新安装/更新，不影响现有歌词功能。
- APK 签名证书与目录签名密钥是两套信任。不能通过一个被新目录声明的任意证书绕过客户端既有信任；证书轮换需受信升级/验证过的 lineage 策略。
- URL 与重定向限制到发布配置中的 HTTPS 受信主机；无用户随意填写源，无下载后执行命令，无将鉴权 Token 放客户端。受信主机列表随**签名目录**下发（客户端另有内置的最小列表），以便以后增加 CDN 镜像时不必升级 Bridge；目录验签失败时只用内置列表。
- 首次目录可采用随 Bridge 打包的已签名快照；远端更新需显式展示网络/来源状态。新商品若超出已生成包可见性范围，需要客户端升级或明确标为无法识别，不能误报未安装。

### 6.3 目录最小信息模型（草案，不是现存文件）

~~~json
{
  "schemaVersion": 1,
  "catalogRevision": 1,
  "entries": [{
    "providerId": "salt",
    "kind": "dedicated-lyrics",
    "applicationId": "<由机器矩阵生成>",
    "versionName": "<插件自身版本>",
    "versionCode": 0,
    "buildId": "<可追溯构建身份>",
    "channel": "stable",
    "minSdk": 27,
    "minStoreProtocol": 1,
    "managementProtocol": 1,
    "scopePolicy": "self-request-selected-hosts",
    "supportedHosts": ["<编译期支持范围>"],
    "requires": {"frameworkApi": 102},
    "artifact": {
      "url": "<官方HTTPS不可变地址>",
      "bytes": 0,
      "sha256": "<实际APK摘要>",
      "signerSha256": ["<预置受信正式证书>"]
    },
    "sourceCommit": "<Providers提交>",
    "releaseNotes": "<经限制的纯文本说明>"
  }]
}
~~~

- 示例中的零值与占位符只是结构说明；正式校验必须拒绝这些值。
- minStoreProtocol 是管理流程兼容要求，不代表 Provider 发布歌词在运行时依赖 Bridge；可选增强的 Bridge/artwork 协议版本要求单独标记。
- kind、scopePolicy、入口协议、schema 和版本约束由同一机器契约生成；远端目录不能给旧 APK 增加它未编译支持的宿主。
- “官方推荐、当前已验证、不兼容、未知”分别表达；未验证的新宿主版本不能标成永久支持。

### 6.4 下载与安装事务

~~~text
可安装 → 下载中 → 完整性/包身份/证书校验 → 等待用户安装确认
      → 安装器处理中 → PackageManager 确认已安装
      → 专用模块：待框架启用/待自身 scope 请求/待宿主重新启动/待运行验证
      → 独立应用：安装管理完成，内部配置从独立入口完成
~~~

- 下载到 App 私有临时目录，校验完才转为可安装文件；中断/重复点击/空间不足有可恢复事务，不半包进入安装器。
- 优先使用 PackageInstaller 会话；兼容方案必须同样保留用户确认与状态校验。新增 REQUEST_INSTALL_PACKAGES，按需检查 canRequestPackageInstalls 并引导到系统授权；拒绝后不循环拉起。
- 安装器 STATUS_PENDING_USER_ACTION 是等待用户操作，不是失败也不是成功；失败区分取消、签名冲突、SDK 不兼容、空间不足、系统拦截。
- 不请求静默安装权限，不用 Root 绕过 ColorOS 安装确认。通知权限只在用户选择更新提醒时申请，不绑定首次打开。
- 安装完成同时检查平台最终状态与 PackageManager 的实际包/版本/签名；Activity RESULT_OK、下载 100% 或 PACKAGE_ADDED 单独都不是充分证明。
- Package visibility 由已知 Provider/宿主集合生成定向 queries；不为了商店复制通用应用的 QUERY_ALL_PACKAGES。当前用户/资料之外的双开和工作资料先明确标为未支持或待验证，不把不可见当未安装。
- 安装回调、包变更与 App 重启重入必须幂等。保存 sessionId/requestId，超时后先 reconcile，不能直接重新发起第二个安装。

### 6.5 更新提醒与故障回退

- 插件独立发布后，Bridge 无须发布新版即可发现其兼容更新。首版提供打开商店时检查、手动刷新；用户可选择后台更新检查与通知。
- 后台采用有网络约束、退避/抖动和合理频率的调度；建议初始检查间隔 24 小时，实际由 ColorOS 调度决定，**不承诺实时必达**。无法后台运行时下次打开补查。
- 用户关闭通知、无通知权限、网络失败或插件目录不可用时，不阻断已安装插件工作。提醒与自动下载安装分开，默认不静默更新。
- stable 与测试渠道分离；有本地更高版本时不强制降级；本地同包不同签名标“无法覆盖安装”，不提示一键清数据。
- Android 通常拒绝低 versionCode 覆盖安装；产品不能承诺任意一键降级。首选将已知良好源码重新发布为更高 versionCode 的签名恢复版；卸载降级只能由用户知情决定并先备份。
- 撤回某条目录记录可禁止后续安装并告警，不通过网络远程无提示关闭用户正在运行的功能。

## 7. 动态作用域：仅专用模块自请求（A 系列）

### 7.0 用户已确认的目标：LSP 原生“作用域请求”通知

2026-11-11 用户提供的截图明确了目标交互：由 **LSP 框架发出“作用域请求”原生通知**，正文表示“某模块请求将所选播放器添加至其作用域”，用户使用框架的“允许 / 拒绝 / 不再询问”操作。截图是交互验收参照，不是本项目 Provider 已完成 API 接入或设备验收的证据。

- 必须由对应专用 Provider 的 requestScope 调用触发框架请求；通知内的请求模块身份应是该 Provider，而不是 Bridge 冒充或替它改 scope。
- Bridge 不能用自绘对话框、NotificationCompat 仿制通知或自己的勾选页，替代这次真正的框架授权。播放器选择页仅选择意向，不是 scope 已批准的证明。
- 用户选择“安装并配置”后，允许在安装完成、Bridge 仍在前台且上下文有效时自动接续 Provider 的最小授权承接流程，不要求用户再手动找 Provider 或重新勾选同一播放器。该承接界面只取得本模块 service、展示状态并请求框架授权，不重复制造一套“允许/拒绝”权限页。
- 只选择“安装”或用户已离开前台时，不从后台强拉 Activity；留“继续配置”入口，由用户触发后发起原生请求。
- 允许后回读 scope；拒绝/不再询问后停止自动请求，不用轮询反复弹通知，也不绕过框架记录。API 未暴露“不再询问”的精确原因时，只能标记未获批准/需人工处理，不解析错误文案伪造状态。
- 框架可能因版本以通知或其他原生授权形态呈现，实际目标框架须验证截图所示路径；文案、按钮和样式由框架控制。本项目不承诺跨所有实现逐像素相同。
- 没看见框架通知时，要区分框架通知权限/通知渠道、模块启用前置条件、service 未连接和 API 失败；申请 Bridge 自己的更新通知权限不能替代 LSP 的通知权限。
- Universal 和动态封面不进入这条请求流程。

### 7.1 已核实 API 与不能承诺的内容

当前依赖的 [libxposed service 102.0.0 官方源码](<https://raw.githubusercontent.com/libxposed/service/3318940876192e29cf6ab07637e899e22a87ebf0/service/src/main/java/io/github/libxposed/service/XposedService.java>) 提供：

- getScope()：当前模块自己的 scope。
- requestScope(List<String>, OnScopeEventListener)：请求添加 scope，回调 approved 列表或失败原因。
- removeScope(List<String>)：当前模块移除 scope。
- getRunningTargets()：可用时查询本模块已 Hook 的运行目标；不等于能管理其他模块。

**API 没有一个可供 Bridge 传入任意 Provider 包名的跨模块 scope 修改参数，也没有这里可用的静默 enableModule 通用接口。** Bridge 不能借自己的 service 替另一个独立 APK 授权。

[现代模块官方指南](<https://raw.githubusercontent.com/wiki/LSPosed/LSPosed/Develop-Xposed-Modules-Using-Modern-Xposed-API.md>) 说明服务在模块 App 启动后交付。API 声明可用不代表所有用户框架都完整实现；必须对实际支持的框架做 M0 PoC。

staticScope 在官方指南中描述为限制用户把模块应用到声明范围之外；不能无证据断言所有框架在 true 时都会拒绝对声明内宿主的 requestScope。**本版设计仍明确迁移专用模块为按模块声明的动态授权策略（建议 staticScope=false），保留编译期宿主白名单与 scope.list 建议范围，最终结合框架验证决定元数据细节。** Universal 和 Bridge 保持原静态策略，不全局翻转。

2026-11-13 设备证据补充：用户提交的隔离探针在 LSPosed 2.2.1 / API 102、staticScope=true 下，对声明内 com.salt.music 发出了原生通知，且允许回调及 getScope 回读通过（第 14.0 节）。这排除了“为触发原生请求必须先改 false”的前提；上述策略建议仍待最终管理需求与其余测试决定，不据单例修改生产矩阵。

### 7.2 可实施的交互序列

1. 用户在 Bridge 选择已安装宿主和对应专用 Provider，完成下载/安装确认。
2. Bridge 核验已安装 Provider 的包名、证书与 managementProtocol。“安装并配置”在有效前台流程中继续；“仅安装”或中断流程提供“继续配置”。
3. 在上述用户意图范围内启动该 Provider 的显式最小管理/授权 Activity；服务注册发生在 **Provider 自己的 App 进程**，不是 Bridge 或播放器进程，不要求重新选择已经确定的宿主。
4. Provider 校验请求来源和一次性 requestId，并从自己的编译期矩阵派生允许宿主；只请求用户选中的、受支持的宿主，拒绝 system/SystemUI 或任意外来包。
5. Provider 读取 getScope，只为缺少的宿主调用 requestScope；保留用户已有 scope，不自动删除额外勾选。
6. **触发用户截图所示的 LSP 原生作用域请求**，等待用户在框架通知中允许/拒绝/不再询问；不由 Bridge 代选。回调只代表此次请求结果，回读 getScope 验证所需子集，部分批准按实际结果显示。
7. Provider 返回最小管理结果，Bridge 重新核对安装身份与 requestId；回调切换主线程后更新 UI，不传可序列化的 service/binder 业务对象。
8. scope 已配置后提示播放器相关进程需要重新启动；不能自动强停播放器。观察目标运行和发布/渲染证据，才提升到相应“已验证”状态。

### 7.3 请求与结果的安全边界

- proposed ManagementProtocol v1 只携带协议版本、requestId、操作类型、所选宿主、配置结果及可解释原因；不能携带歌词或替代 MediaSession。
- 导出的管理入口使用受控签名权限并验证调用包/UID、包签名及允许操作；同签名不自动代表任意同签名应用都有权传任意宿主。
- 框架设置或 Provider 自身前门可保留人工 scope 流程；独立使用 Provider 不要求先安装 Bridge。
- 不信任 Intent 的裸字符串包名、RESULT_OK 或远端目录对 scope 的声明。结果不能让 Bridge 绕过 Provider 自身白名单。
- 拒绝、取消、Binder 死亡、服务迟到、回调丢失、超时和进程重建都需状态机处理；未确认时标待操作或 unknown，而不是“已激活”。

### 7.4 必须覆盖的现状差异

- 共享设置模块增加一个唯一 service listener 的复用层和 scope controller，不在 Activity 每次创建时重新注册。
- Readify 当前是独立 Application，必须专门接入同等服务/授权 adapter，保留原独立设置行为；不能漏掉这个专用模块。
- 按每个模块写入 scopePolicy/staticScope/managementProtocol，并迁移 convention、矩阵校验、R8 入口保留规则及最终 APK 校验。
- 保留异常 protective、现有 ProcessPolicy/RuntimeProfile 和不自动热重载的基线；scope 放开不等于放宽 Hook 的进程门禁。
- Universal 不接入此管理协议；其 system scope、播放器绑定和设置仍从独立应用/框架管理。动态封面应用没有此类 LSP scope 请求步骤。

### 7.5 框架未就绪时的降级与发布门禁

服务未交付或模块尚未启用时显示确定的操作说明，允许用户打开框架管理器完成启用并返回重查；不能把“服务未连接”直接判成模块被禁用。

不支持动态请求的框架提供人工 scope 操作路径，明确标注限制。**如果目标支持框架的 M0 PoC 没有通过，不能通过写一个假成功 UI 宣称满足动态作用域要求；本版是否缩减范围或调整框架要求需重新确认。**

安装完成、框架连接、模块启用、scope 批准、目标运行、加载新构建、业务有效是独立状态。getRunningTargets 没有目标可能只是播放器没启动，不能直接判失败。

## 8. UI 新增与职责重构（U 系列）

### 8.0 标准组件库与采用范围（已确认）

- **主方案：Kotlin + Jetpack Compose + 官方 Material 3。** 新首页、Provider 商店/详情/安装任务、新手引导、状态与诊断采用同一组件与主题体系。
- **Material 3 Expressive 选择性使用**：用于主操作层级、适量形状/排版、进度反馈与状态转场；不把所有按钮、卡片都做成大尺寸、大动画，不用满屏模糊/玻璃制造管理工具的视觉负担。Expressive 是 Material 3 的设计/组件方向，不另外混入一套无关 UI 底座。
- **稳定依赖优先**：通过 Compose BOM 管理适用依赖，冻结与 AGP、Kotlin、Compose compiler、JDK 和最低 SDK 兼容的组合；逐项核实所选版本的 Expressive API，不以 master 源码代表稳定版可用，也不为单个新控件全局追 Alpha/RC。确需实验性 API 时单独列出、局部封装并评审，不默认全局 opt-in。
- 不采用 Miuix 作为本版主组件库，不直接依赖 ROM 私有 ColorOS 控件，也不把网页组件库搬进原生 App。标准组件之上定制项目自己的颜色、圆角、间距和状态表达，不照搬 Pixel 页面。
- 现有外观设置、预览及子页先保留；通过独立页面和必要的 AndroidView / ComposeView 互操作分步迁移。不是“所有 Java 改 Kotlin”，也不是一次性更换所有 Activity。
- Compose 只服务 Bridge 普通 App 的管理 UI。SystemUI Hook、歌词 Canvas 绘制、AOD、Provider 业务及其独立应用不因本次选型一并重写。LSP 作用域授权仍是第 7 节的框架原生通知，不由 Compose 模拟。

### 8.1 信息架构

~~~text
Bridge 唯一管理入口（建议新增桌面 LAUNCHER，LSP 模块设置进入同一首页）
├─ 首页：当前 ColorOS/Bridge 状态、继续设置、当前播放器、待处理操作
├─ Provider 商店：适合我的播放器 / 全部 / 已安装 / 更新
├─ 外观：保留当前预览、预设、歌词/翻译/动态封面消费设置
└─ 状态与帮助：功能可用性、原因、自检、短时诊断、导出、版本与来源

按需页面：新手引导、商品详情、下载/安装任务、专用插件授权、升级迁移摘要
独立应用：Universal 与动态封面原有桌面入口不合并
~~~

- 四个顶级入口有文字和图标；折叠大屏可用等价 navigation rail，保持相同信息层级。
- 现有 LyricUiSettingsActivity 保留为外观页兼容入口，现有子页和配置语义不破坏；模块设置入口迁移到首页时为旧显式调用保留路由。
- 新增 Bridge 桌面入口是推荐产品决策，需在 UI 原型评审确认；不要误把“旧版没有桌面图标”当成安装失败。
- 不把商店网络、安装事务、框架服务和引导流程继续堆进现有大 Activity；提取独立 controller/repository/状态模型。

### 8.2 关键页面内容

| 页面 | 必须显示 | 主要操作 |
| --- | --- | --- |
| 首页 | 已安装/运行构建是否一致、配置应用情况、引导剩余项、最近健康状态 | 继续设置、进入商店、查看原因 |
| 商品卡片 | 插件自身版本、对应播放器、是否已安装、专用/通用/封面分类、兼容/签名异常 | 下载、更新或继续专用模块配置，不能一个按钮隐藏所有失败阶段 |
| 专用商品详情 | 宿主家族、已选择宿主、支持/未知版本、更新说明、实际 scope/待重启状态 | 前台明确发起安装或自身授权 |
| 通用/封面详情 | 独立应用标识、实际版本、安装/更新状态、独立使用说明 | 仅下载安装更新，不嵌入内部设置或 LSP scope |
| 安装任务 | 下载/校验/系统确认/安装处理等分阶段进度，可取消或恢复 | 重试具体失败阶段，不伪造百分比式“激活中” |
| 状态与诊断 | 每个功能的支持条件、运行结果、原因、证据是否缺失 | 自检/短时采集/导出，非自动改配置 |

### 8.3 视觉与交互约束

- 保留当前固定浅色、edge-to-edge 与系统字体；新管理页面采用 Compose Material 3，旧 Views 与新 UI 使用一致的颜色/排版/间距规范。本版不引入网络字体或网页式 Hero/营销轮播，不自动扩大为全旧页重写。
- 触摸目标至少 48dp，正文正常对比度至少 4.5:1；状态同时用文字和图标，不只依赖红绿颜色。
- 引导提供步数、返回、稍后设置/跳过；跨外部 App 的长流程有持久化状态和恢复入口，不用一条 Toast 代替错误说明。
- 保留草稿/预览的保存与放弃语义，切换顶级导航不能悄悄提交或丢掉外观修改；返回时恢复列表位置和选择。
- 显示加载、离线、空目录、无匹配宿主、签名不符、过期目录、通知关闭等真实状态。
- 适配中文/英文、大字体、TalkBack、手势/三键导航、横屏/折叠、系统减少动画；状态正确性不能依赖动画完成回调。
- UX 资料检索中“步骤进度、允许跳过和返回”适用于本项目；通用搜索产生的网页 Hero、网络字体和营销配色不采用。实际外观待原型/设备确认，不在本文伪称已有截图验收。

### 8.4 渐进迁移与设计系统落地（U-01 至 U-05）

1. **U-01 小原型先行**：先以明确标注的样例数据制作“首页 + 商店列表”两个最小原型，确认视觉密度、主操作、状态文字、大字体和导航；同时验证 Compose 接入与当前构建工具链。样例状态不作为真实安装或授权证据。
2. **U-02 统一主题与复用组件**：在官方 Material 3 控件上建立 AppTheme、Scaffold、状态卡、Provider 卡、步骤/任务进度行、原因提示等薄封装，统一颜色/字级/形状/间距/动效 tokens。优先复用官方 Button、Switch、Slider、Card、Dialog、BottomSheet 和导航组件，不重新手写另一套基础控件库。
3. **U-03 新管理面先完成**：以独立 UI 状态模型驱动首页、商店、引导和诊断；网络、包安装、框架服务与事务归 repository/controller，不能藏进 Composable 或因重组重复执行安装/作用域请求。
4. **U-04 旧 Views 保留与互操作**：既有外观页可先独立打开；仅在确有复用价值时用 AndroidView 承载原预览/View，或用 ComposeView 承载局部新内容。明确生命周期和状态所有权，不能同时维护两份配置真值或重复设置 insets。草稿保存/放弃、预览和返回行为保持原语义。必要的 MDC-Android 升级只作为旧页过渡，不作为新页面的长期底座。
5. **U-05 按页验收再迁移**：先迁移收益明确的页面，逐页比较外观、行为和性能。没有价值或风险较高的旧自定义绘制无需为了技术统一强迁；本大版本不要求清零所有 Views。

实施要求：

- Kotlin/Compose/Material 3 依赖只从 Bridge 管理 UI 边界进入，不让 Hook/bootstrap/render 路径引用新 UI、Activity、Composable 或启动主题相关对象；架构测试检查引用与初始化边界，不只依赖包名约定。
- 页面使用生命周期感知的状态订阅；remember/rememberSaveable 只承接适当页面状态，安装 session、引导步骤和授权 requestId 的可恢复性继续由原计划的持久化事务负责。重组、旋转或返回页面不能重复发送外部操作。
- 不因 UI 框架迁移修改 LyricUiConfig schema、现有偏好通道、真实 PlaybackState 或歌词时序；也不借机更改通用/动态封面独立 App 的 UI 技术栈。
- 冻结稳定依赖组合后验证正式签名/R8、最低支持 SDK 与入口可达；实际测量 APK 大小、设置 App 冷启动、列表滚动和内存，与旧 UI 基线比较，不承诺引入 Compose 零成本。
- 原型样式确认后形成可复用规范；浅色、无障碍、减少动画与错误反馈属于硬约束，不以追求 Expressive 外观为由削弱。

### 8.5 开发技能安装、使用与边界（2026-11-11 创建，2026-11-12 增补）

已在当前 Agent 的用户技能目录安装以下三个来源的适用技能，并保留许可、来源、固定提交、安装时间与入口摘要记录。**这是开发期技能，不是 Android 组件库/运行依赖，不打包进 APK。**

| 来源 | 固定提交 / 许可 | 安装与实际使用 |
| --- | --- | --- |
| [hamen/material-3-skill](<https://github.com/hamen/material-3-skill/tree/14385f2bf3804d8779f8b4db2604211f1e70b4c1>) | 14385f2bf3804d8779f8b4db2604211f1e70b4c1 / MIT | 安装 material-3；已通过技能工具加载，用 Compose 优先、语义 tokens、自适应布局与十类审查规范细化本节 |
| [Kotlin/kotlin-agent-skills](<https://github.com/Kotlin/kotlin-agent-skills/tree/087f5689c1934135ed58718cdf524525e452b7e9>) | 087f5689c1934135ed58718cdf524525e452b7e9 / Apache-2.0 | 安装该提交下全部 10 个技能；首批实际加载 kotlin-tooling-java-to-kotlin，采用小范围逐文件迁移与行为/互操作保持约束 |
| [chrisbanes/skills](<https://github.com/chrisbanes/skills/tree/50d54366e577460b769ce05b50da007de9939783>) | 50d54366e577460b769ce05b50da007de9939783 / Apache-2.0 | 2026-11-12 精选安装 11 项 Kotlin/Compose/Android 技术技能，见第 8.5.4 节；不安装其完整工作流插件 |

2026-11-11 首批安装 11 个技能并核对 59 个上游文件；2026-11-12 新增 11 个技能并核对 27 个上游文件，均与固定源码副本 SHA-256 一致。本专项累计新增 22 个技能，不包含原有其他技能；未覆盖已有技能，新装项已被当前技能目录发现。只复制技能/参考资料，**未执行仓库附带脚本，未启用附带的 MCP 或工作流插件配置**，不为阅读技能运行 npx 安装链或修改 App 工具链。

#### 8.5.1 按任务激活，不把技能集合当通用 Kotlin 写码规范

| 技能 / 分组 | 本项目使用条件 |
| --- | --- |
| material-3 | 编写/评审新 Compose 页面时加载；按 theme、scaffold、component、layout 或 audit 任务读取对应参考资料；只使用 Compose/Android 分支 |
| kotlin-tooling-java-to-kotlin | 只有明确选定旧管理 UI/控制器的 Java 文件需要转换时使用；扫描相关框架、保持可空性/可变性/注解及 JVM 调用边界，逐文件编译和回归；不是批量转换 Hook 的授权 |
| kotlin-tooling-agp9-migration | 仅在单独确认需要 AGP 9 迁移或出现对应兼容问题时使用；先核对真实插件与构建版本，不因新增 Compose 就机械升级 AGP 或拆成 KMP 工程 |
| kotlin-tooling-immutable-collections-0-5-x-migration | 只有实际存在对应依赖升级任务时使用，不为了套技能新增或升级集合库 |
| kotlin-tooling-kotlin-toolchain、其 plugin-authoring、gradle-to-kotlin-toolchain-project / plugin | 本项目现为既有 Gradle 工程，默认不适用；不改成 Kotlin Toolchain/Amper，不替换项目构建入口 |
| kotlin-tooling-cocoapods-spm-migration、kotlin-tooling-native-build-performance、kotlin-backend-jpa-entity-mapping | 当前 Android 管理 UI 无对应 iOS/Native/JPA 工作，安装但不激活，不引入跨平台或后端需求 |

Kotlin 官方仓库目前主要是工具链/迁移专项集合，**不包含一个覆盖所有 Compose 业务写法的万能规范**。新 Kotlin UI 的具体 API、协程/生命周期和编译器兼容事实仍须按锁定依赖查官方文档；技能是参考，不能覆盖项目指令、用户授权与实际代码证据。技能示例中的自动提交、脚本、依赖升级或外部服务配置不自动获得执行许可。

#### 8.5.2 编写 Kotlin + Compose Material 3 的工作顺序

1. **开始前**：加载 material-3 确认设计语言；按 using-chrisbanes-skills 将具体状态、组件、异步或测试问题路由到对应主要技能，只有独立问题才叠加技能。按需读取对应 references 并记录提交。先确认当前 BOM/API，不把技能里的新 API 名称当已可直接编译；纯 Kotlin 数据层工作不因提到 UI 就强加载 Compose 规则。
2. **主题与布局**：颜色使用 MaterialTheme.colorScheme 及正确的 onX/X 配对，字级和形状使用主题角色；全局间距采用统一 tokens，以 8dp 为主节奏、必要的 4dp 微调也集中定义，不在每个页面散落魔法值。保留固定浅色和系统字体，不因技能描述了动态色/深色就自动开启壁纸取色、深色模式或网络字体。
3. **适配与组件**：优先官方 Material 3 组件和与锁定版本匹配的 WindowSizeClass/adaptive API；按实际窗口宽度/折叠姿态安排 bar、rail 或列表详情。正确处理 Scaffold padding、系统栏与 IME，避免与旧 Views 重复加 insets；不用 Web/CSS 模板代替原生 Compose。
4. **状态与迁移**：继续遵守第 8.4 节的生命周期、持久事务和单一配置真值；重组不得重复安装或请求 scope。只在真正转换旧 Java UI 文件时使用 Java-to-Kotlin 技能，保留行为、可空性、可变性、注解目标与需要的 JVM 静态/字段接口，先编译回归再迁移下一项；不要求为技能示例自动 commit。
5. **完成后审查**：按 material-3 的颜色、排版、形状、层级、组件、布局、导航、动效、无障碍、主题十类输出问题与证据；结合大字体、TalkBack、减少动画和 ColorOS 设备检查。审查分数只是辅助，不代替真实框架授权、安装器和运行验证，不在未有 UI 的当前文档阶段生成虚假合规分数。

#### 8.5.3 与里程碑的关系

- 技能安装和本轮规划使用已完成；M0 的构建验证/原型、M5 的 Kotlin/Compose 页面、M6 的 UI/设备验收仍未实现。
- M0 先以 material-3 指导的首页/商店原型固定 tokens、导航和可用 API 清单；只有需要迁移构建工具时才另行激活相应 Kotlin tooling 技能。
- M5 每个 UI 切片记录使用的技能/参考与发现的问题；M6 留存 MD3 审查结果及实际测试证据。未来升级技能需要重新审阅并更新来源提交，不自动跟随仓库最新内容。

#### 8.5.4 Chris Banes Kotlin / Compose 专项技能（2026-11-12 增补）

已从 [chrisbanes/skills 固定提交](<https://github.com/chrisbanes/skills/tree/50d54366e577460b769ce05b50da007de9939783>) 选择安装以下 11 项（Apache-2.0），不是安装整个工作流插件。已通过技能工具加载 using-chrisbanes-skills、compose-state-and-effects、kotlin-concurrency-and-flow，并据其状态所有权与交付语义要求细化下述约束。

| 技能 | 本项目适用场景 |
| --- | --- |
| using-chrisbanes-skills | 按具体决策选择一个主要技能，仅在同一变更存在独立问题时叠加第二个，不每次把全部技能加载一遍 |
| compose-state-and-effects | 页面状态所有权、状态提升、remember、Effect 生命周期、订阅/清理；新管理页面的基础规范 |
| compose-component-design | 可复用卡片/进度行等组件的 Modifier 归属、可变内容 slot 和公开 API，避免大量形状布尔开关 |
| compose-ui-testing-patterns | 优先纯状态驱动内容的语义/回调测试；布局外观使用截图，生命周期/安装平台交互再用集成测试 |
| compose-performance | 有具体重组、稳定性报告、读写阶段或 trace 问题时测量和局部修复；不靠批量加 Stable/Immutable 注解假装优化 |
| compose-animations | 状态转场、显隐、内容身份与可打断动效，遵守现有减少动画要求，不将动画结束当业务成功 |
| compose-focus-navigation | 只有确有键盘/焦点恢复/无障碍焦点需求时使用，不因此扩展为 TV/桌面产品 |
| kotlin-concurrency-and-flow | 协程所有权、取消、状态与一次性事件、StateFlow/SharedFlow/Channel 的具体交付条件 |
| kotlin-control-flow | 安装/授权/兼容结果等封闭状态的穷尽分支和空值处理；未知平台输入仍保留明确 fallback |
| kotlin-api-design | 管理面领域类型、行为所有权和 Android 平台服务边界，避免无业务含义的 helper 或过度抽象 |
| android-benchmark-comparison | 实际比较两个 Android 配置/候选的性能时使用；控制型号、温度、电源、负载和顺序，记录分布，不以一次最快值定默认；真机操作仍需授权 |

**这批补充与前两批的分工**：material-3 负责设计语言；Chris Banes 的专项技能补充 Compose/Kotlin 代码结构、异步状态、测试和有证据的性能诊断；Kotlin 官方仓库的 tooling 技能继续仅用于匹配的迁移任务。它们不能替代锁定版本的官方 API 文档或本项目的实际测试。

新管理面采用以下具体约束：

- 将依赖/业务状态的 screen wiring、组合期 UI 对象与纯内容分开。纯内容接收不可变 UI 状态和回调，可用样例数据及普通 UI 测试调用；LazyListState、FocusRequester 等对象归组合期，不塞进持久业务状态。是否加 Preview 注解先检查 tooling 依赖，不为文档或小改动擅自扩大构建范围。
- 下载/安装/授权事务有明确 owner、取消与重入边界；可渲染状态与一次性命令分开。选择 Flow/Channel 前先说明消费者数量、无人订阅时是否允许丢失、缓冲/重放与同步读取需求，不预设某一种流包治所有问题。
- **必须跨进程重建恢复的安装/授权结果，继续以持久化事务 + requestId/sessionId + 实际回读为权威**；内存 Channel 或 SharedFlow 都不是持久化，不能把关键接续动作只存在 replay=0 的临时事件里，也不能承诺单靠某种流实现端到端 exactly-once。
- 组件绘制只投影当前状态，Effect 绑定其应重启/释放的语义身份；重组、切页、旋转和回收均不能无意再次安装或请求 scope。
- 性能按同一个可观察场景测量后只改最小原因；源码能证明多一次失效/布局，不等于已证明卡顿或无限循环。仓库自己的模型评测百分比不算本项目兼容/性能验收结果。

**本轮刻意未安装** gradle-run、changelog、grounded-writing、release-kotlin-library、deliver-spec、to-plan、implement-with-subagents、subagent-delegation、run-github-project、shepherd 等工作流/写作项。部分已装技术技能会交叉引用 gradle-run；这不表示其构建包装器已接入。项目仍以既有 Windows/CJK 构建入口和上层任务约束为准，不执行缺失脚本、不静默补装其工作流依赖；未来若要接入须先评估兼容性并明确授权。未运行 npm、上游脚本、评测、构建或设备基准，也未修改全局代理编排规则。

## 9. 发布体系与旧版本迁移（R 系列）

### 9.1 契约拆分，不再套件锁步

未来建议形成三个明确角色（名称为拟新增，尚不存在）：

~~~text
Bridge release contract      → Bridge 版本、正式证书、Bridge Release 资产
Provider source matrix       → 模块身份/宿主/进程策略/自身版本/分类/scope 策略
Provider publish/catalog     → 不可变 APK、证书/摘要、兼容条件、目录签名与 revision
~~~

- 取消“每个 Bridge Release 必须有全部 Provider、总数 16 APK/19 资产、Bridge 与 Provider suiteVersion 相同”的旧分发校验；替换为分别完整、可交叉追溯的发布契约。
- 14 个歌词模块 + 动态封面的完整 CI 验证仍保留，**停止随 Bridge 分发不等于停止构建/测试 Provider**。
- 独立 Provider 更新只需要其源码/依赖验证、版本提升、正式签名、artifact 发布和 catalog 原子晋级，不要求新 Bridge tag。
- 共享 provider-core 或授权组件变化时，按依赖影响范围重建并测试全部受影响模块，不把共享变更伪装成只改一个 Provider。
- 正式 APK 沿用当前受信发布证书体系；测试/Debug 证书进入单独测试渠道，生产商店拒绝混入。
- 新发布流先上传并验证不可变 APK，再签名并晋级目录；失败时旧目录不变，不能公布下载不到的版本。
- 目录发布也需要审计与授权门禁；不是任意 push main 自动对所有用户推送更新。

### 9.2 CI 与文档的实施清单

- 拆分 [现有 Bridge workflow](<../.github/workflows/release.yml>) 对 Provider 全量发布的依赖；保留发布前源冻结、签名、RC 与 LSP 元数据核验。
- 将 [现有 Providers workflow](<../../ColorOS-Live-Lyrics-Providers/.github/workflows/android-release.yml>) 从“全量打包 ZIP”扩展为“受影响模块独立发布 + 定期全矩阵验证”；补精确集合/身份校验，不沿用只判断至少 14 个 APK 的收集条件。
- 迁移 [资产构建脚本](<../scripts/build-release-assets.ps1>)、[Bridge 校验器](<../scripts/validate-release-contract.ps1>)、两仓库矩阵/版本校验与 [发布流程](<RELEASE_PROCESS.md>)；实施前逐项读实际脚本，不只改 YAML。
- 更新 Providers 适配指南中全局静态 scope 的说明；更新两仓库 README、安装教程、Release Notes 和 LSPRepo 的下载/安装说明。
- 对历史公开 Release 不覆盖、不删资产；它们属于旧版本归档。新版本说明明确用户从 Bridge 商店安装/更新 Provider。
- 新文档必须区分“商店已安装”和“框架已加载”；不把安装器授权与 LSP 作用域授权合成一个步骤。

### 9.3 存量插件接管

1. 本地先枚举已知 applicationId，验证版本和签名；同包同受信证书可直接纳入已安装列表，不要求卸载。
2. 老 Provider 无 managementProtocol 时标“旧版，需更新以使用引导式作用域”；仍保留其原有功能与人工入口。
3. 升级到动态策略前保存/回读 scope；不通过清空框架数据库重建，不擅自缩减用户既有勾选。
4. 本地更高测试版本、第三方签名版本和不可见资料空间分别标注，不能误提示“已最新且已兼容”。
5. 旧版 Bridge 不因服务端只发新目录而突然失去现有 Provider 功能；新商店协议不支持时提示管理端升级，不强改原 MediaSession 链路。
6. Universal 与动态封面的偏好、账号与授权保持在其独立 App，不复制到 Bridge 造成第二份权威配置。

## 10. 拟落地代码单元与测试所有权

下列为新增边界建议，不是已经存在的类；具体命名可在实施时微调，但职责不能重新并回大 Activity。

| 计划单元 | 职责 | 约束/对应测试 |
| --- | --- | --- |
| management/catalog | 目录验签、版本/兼容决策、已安装商品投影 | 无 Hook 依赖；恶意目录/重放/过期/证书测试 |
| management/download + install | 下载缓存、PackageInstaller 会话与恢复 | 仅 Bridge 普通 App；取消/系统确认/进程回收测试 |
| management/onboarding | flowRevision、升级迁移、步骤状态与恢复 | 与 LyricUiConfig 解耦；新装/升级/跳过测试 |
| management/provider | 专用模块管理请求编排与结果核验 | 不替其他模块调用 scope API；请求伪造/重放测试 |
| Provider settings102 scope adapter | 本模块 service、允许宿主、自请求/回读 | 只在模块 App；全专用矩阵与 Readify 例外测试 |
| compatibility / diagnostics | buildId、capability/result、原因与脱敏快照 | Debug 不改变业务；缺证据为 unknown |
| management/ui + design system | Kotlin/Compose Material 3 新管理页、薄组件封装与统一 tokens；Expressive 局部使用 | 仅普通 App UI；Compose 语义/状态测试、稳定 API、生命周期与无障碍测试 |
| UI controllers/repositories + View interop | 新管理面与旧外观页间投影、草稿恢复和必要互操作 | 重组不得重复安装/请求 scope；旧配置单一真值，预览/返回/insets 不回归 |
| publish/catalog tooling | 矩阵导出、APK 身份校验、目录签名与原子晋级 | 正式签名、集合完整、来源 SHA、最小支持版本门禁 |
| Hook 面运行时组件（E 系列） | 从 `LockscreenLyricsModule` 迁出的渲染器、播放、模型发布、Recycler、handoff、屏幕超时、翻译 action 等状态 owner | 不反向依赖入口类；入口类预算守卫只降不升；详见第 15 节 |

[现有架构守卫](<../app/src/test/java/io/github/andrealtb/lockscreenlyrics/BridgeArchitectureGuardTest.java#L105-L156>) 应改为：管理面允许官方 Provider 目录身份；Hook/bootstrap/render 路径不得依赖商店网络、安装器或 Provider App 注册表；旧 v4 transport 标记继续全局禁止。相关 guard 不可简单删除。

## 11. 里程碑、依赖与发版门禁

未承诺工期。2026-11-12 已开始 M0 的隔离 scope 探针本地切片（第 14.0 节）；它不等于 M0 全部通过。下表正式功能及设备/供应链/UI 门禁仍为 **待完成**。

| 里程碑 | 任务/交付 | 依赖 | 通过标准 |
| --- | --- | --- | --- |
| M0 基线与可行性 | 冻结本地已知良好基线；PoC 原生 LSP scope 通知/三类选择/回读；确定托管与签名；按第 8.5 节技能规范验证 Compose 接入和首页/商店原型（样例明确标注） | 无 | 框架有真实证据；UI tokens/导航/API 清单及样式获确认，工具链/最低 SDK 组合可用；四类应用边界确认 |
| M1 ColorOS 兼容基础 | C-01/02/03：buildId、实际配置/capability、最终原因、离线摘要/短时诊断协议 | M0 基线 | 可区分旧注入进程、配置未到与能力不支持；开关不改行为 |
| M2 商店供应链 | 单模块版本策略、签名 catalog、artifact 存储、CI 原子晋级、内置可信快照 | M0 托管/密钥 | 错签/重放/过期/半发布不可安装；不依赖公开 Provider Release |
| M3 商店安装更新 | 已安装发现、商品/详情、下载、安装、恢复、独立更新提醒 | M1、M2 | 包安装与版本/签名回读闭环，通用/封面只管理安装 |
| M4 专用动态 scope | 13 个专用模块按矩阵适配、Readify 例外、管理协议与安全校验 | M0 PoC、M3 | 每个模块均以自己的服务授权；无跨模块代设；拒绝/部分批准/服务死亡可恢复 |
| M5 引导与 UI 集成 | 按 material-3 规范编写 Kotlin/Compose Material 3 新管理页与引导；选择性 Expressive；旧 Views 仅按实际需要结合 Kotlin 迁移技能渐进衔接 | M0 UI 验证、M1、M3、M4 | 不强制重装/清配置，旧草稿/预览保留，重组不重复外部操作；附十类 MD3 审查问题与证据 |
| M6 回归与 RC | C-04/05/06、正式签名/R8、ColorOS 型号矩阵、存量升级、发布文档与 LSP 下载说明 | 全部前项 | 已知良好设备不回归；目录和全部商品身份核验；用户完成最终 RC 设备确认 |

里程碑状态（2026-11-14）：**M1 已由用户判定通过**（PJZ110 单机证据，见 [M1 退出验收清单 §6](<M1-EXIT-DEVICE-CHECKLIST.zh-CN.md>)；其他机型与正式签名/R8 形态未覆盖）。**M0 已完成（2026-11-14）**：scope 探针与首页/商店原型已用户设备确认；基线已冻结；D1 托管（方案 M：Providers 仓库独立 tag 的 GitHub Release）与 D2 目录签名已由用户决定（第 14.5 节）。下一项为 M2。

E 系列（第 15 节）不单列里程碑：E-0 随 M1，E-1/E-2 在 C-04/C-05 开工前，E-3 至 E-7 与 M2–M4 并行，E-8 随 M5；M6 RC 冻结后不再做结构迁移。排期与截断规则见第 15.5 节。

优先推进 M0/M1，兼容专项先落地；M2/M3 不等待所有渲染兼容细节做完。商店上线前必须有诊断数据面，避免新安装流程又成为新的不可解释问题来源。

## 12. 验收矩阵（发布前必须给出通过/失败/未覆盖）

| 组 | 最小验收内容 |
| --- | --- |
| ColorOS 型号 | 已知良好设备、相同版本号不同插件指纹、不同缩放/屏幕/AOD 能力；仅目标 ColorOS 组件族，不增加其他 ROM 目标 |
| 新装 | 无 Provider、已有播放器、多宿主家族、用户跳过、无网络、尚未启用 Bridge |
| 升级 | 4.x 保留外观/翻译/封面、旧 Provider 接管、旧 scope 保留、同大版本不重复引导、备份恢复后不假报就绪 |
| 专用 scope | 13 个模块全覆盖，尤其 Readify；框架原生通知确由对应 Provider 请求触发；允许/拒绝/不再询问、取消、部分批准、超时、Binder 死亡/迟到、回读失败、不重复催授权 |
| 安全 | 非 Bridge/非受信调用者、伪造 requestId、外来包/system scope、篡改 catalog/APK、错签名、过期/降序目录 |
| 安装 | 未授权安装源、ColorOS 系统确认/拦截、取消、空间不足、低 SDK、重复回调、安装期间进程死亡、版本未变化 |
| 独立应用 | Universal 和动态封面桌面入口、账号/绑定/设置保留；商店不发 LSP 播放器 scope 请求 |
| 更新 | 独立 Provider 发布而 Bridge 不升级、后台被限制、通知权限拒绝、本地更高/不同签名版本、目录不可用、恢复版高 versionCode |
| UI | Compose 语义与状态测试、中文/英文、大字体、TalkBack、手势/三键、旋转/折叠、外部页面返回、减少动画；十类 MD3 审查；旧 Views/Java-Kotlin 互操作、草稿/预览/insets 不回归；重组不重复安装或 scope 请求 |
| 运行回归 | 原生歌词、逐字/逐行、上浮、取色、翻译按钮、切歌/暂停、AOD、静态/动态封面；不改宿主真实 PlaybackState |
| 性能 | 无商店网络/安装 I/O 或 Compose UI 初始化进入 SystemUI 绘制/Hook 热路径；诊断容量有界；测量新 UI 的 APK 增量、设置 App 冷启动、列表滚动/内存，并与已知良好设备基线比较 |
| 结构治理（E 系列） | 入口类预算守卫生效且未上调；已完成各刀的 Hook ID、日志 event、安装顺序清单与基线一致；同场景 `PERF_SAMPLE` 与掉帧率不劣于 E-0 基线；未完成的切片逐项标“未覆盖” |
| 发布 | 正式证书而非默认测试签名；R8 后管理/模块入口可达；APK/目录双校验；公开 Release 无 Provider APK/ZIP；artifact 可达后才晋级目录 |

需要动态 API/安装流程的测试必须最终覆盖正式签名且 R8 处理后的候选。普通 JVM/Robolectric 测试只能证明对应行为，不能代替框架授权或 ColorOS 安装器真机验证。

## 13. 实施前需要冻结的决策与风险

| 决策/风险 | 本文决定/推荐 | 门禁/剩余验证 |
| --- | --- | --- |
| D1 商店托管位置与地区可达性 | **用户已确认（2026-11-14）：方案 M**：Providers 仓库（`Andrea-lyz/ColorOS-Live-Lyrics-Providers`）公开 GitHub Release，每个 Provider 独立 tag（如 `salt-v1.1.3`）；大陆网络由用户自行解决，或以后加一层免费 CDN 作为镜像；不开 14 个 LSP 仓库 | 不使用 assets.lsposed.org 作为商店后端；CDN 选型与可达性待定且非 M2 门禁；不以“GitHub artifacts 能下载”代替生产方案 |
| D2 目录签名与密钥 | **用户已确认（2026-11-14）**：受保护 GitHub Environment 签名（晋级须用户审批）；目录过期 180 天、时钟偏差 ±24 小时；算法按推荐 ECDSA P-256（用户未提异议）；Bridge 内置主用 + 备用两把公钥 | 最低 SDK 算法支持、轮换、过期/时钟偏差与反重放需测试；私钥不入库 |
| D3 Bridge 管理入口 | **用户已确认（2026-11-14）**：新增 Bridge 桌面入口，设置中可选隐藏桌面入口；框架模块设置进入同一首页；管理面四入口为首页 / 商店 / 外观 / 状态 | 隐藏后仍须能从 LSPosed 模块设置进入；通用/封面独立入口不动；桌面入口在首页接入真实状态后再上线，不把样例页放上桌面 |
| D4 框架支持矩阵 | libxposed 102.0.0 现有依赖起步，按实际能力验证 | requestScope 的框架交互/启用前置条件不可猜；不把 scope 请求等同静默启用模块 |
| D5 静态 scope 迁移 | 专用模块逐项动态策略；Universal/Bridge 保持独立静态 | 不仅改文本；验证 convention、APK metadata、旧授权保留与 ProcessPolicy |
| D6 更新提醒 | 手动/打开检查，用户选择后台通知 | ColorOS 后台限制不可保证实时，不新增默认常驻服务 |
| D7 兼容契约 | 各 Provider 自身版本 + 管理协议 + 实际宿主能力 | 不强制所有 Provider 与 Bridge 同版，不让更新越过 APK 编译期宿主范围 |
| D8 供应链或服务不可用 | 停止新安装/更新，保留已安装链路与离线状态 | 不默认清缓存/数据、不改播放状态、不隐藏证据缺口 |
| D9 UI 标准组件底座 | **用户已确认：新管理页使用 Compose Material 3，选择性使用 Expressive** | 实施时冻结稳定依赖组合/API 清单，先验收首页/商店原型；不重新默认选择 Miuix 或新页全用 Views |
| D10 UI 迁移范围 | **用户已确认：旧 Views 渐进迁移，Hook/歌词 Canvas/AOD/Provider 业务不重写** | 生命周期、单一配置真值、R8 与性能实测通过；不是一口气全项目 Kotlin/Compose 化 |
| D11–D15 结构与性能治理 | **用户已确认（2026-11-14）按第 15.8 节推荐采用**：E-0 为硬门禁、其余可按刀截断；渲染器外移提前；入口类 ≤ 1,500 行、可变字段 ≤ 30；迁移期保持 Java；分包放到 E-8 | 方向已定，代码尚未开始；E-0 的预算数值以实际计数实现为准重新取值 |

上表待验证项不是要求用户现在逐条选配置；采用推荐方案继续细化即可。但 M0 未通过、托管/密钥未具备时，不应提前宣布商店可发布。

## 14. 文档交付与下一步

本文和既有 README 保留正式功能“规划中”的入口，开发技能见第 8.5 节。当前 M1 已修改构建身份生成、诊断页面、原有决策点观测和受保护诊断回读；新增插件定向可见性与按模块隔离的诊断权限。歌词业务策略、几何、生产 scope、CI 与发布契约未改；不得把本地验证当作整项或设备验收。

当前状态与接手顺序以 [M1 任务交接](<../../HANDOFF-M1-AGENT.zh-CN.md>) 为准；C-01/C-02/C-03 的实现与本地证据见 [M1 续实施记录](<M1-C01-C03-DEVICE-READY.zh-CN.md>)，[前批复核](<M1-C01-C02-IMPLEMENTATION.zh-CN.md>) 仅作历史对照。

2026-11-13 出现并已修复一次由本批诊断引入的真实回归：新诊断在 `Application.attach` 调用宿主 `getApplicationContext()` 抛 NPE，导致 Provider 业务 Hook 未安装。根因、最小修复与设备确认见 [Salt 回归记录](<../../ColorOS-Live-Lyrics-Providers/docs/M1-SALT-ATTACH-DIAGNOSTICS-REGRESSION.zh-CN.md>)。该事件说明：**新增的可选诊断必须证明失败不改变业务，且只有设备证据才能确认恢复**。

进入实施时建议按 M0 → M1 开分支；每个阶段补状态机/契约测试、记录实际设备证据，再合并下一层流程。发布仍遵守 [当前发布流程](<RELEASE_PROCESS.md>) 的授权与最终 RC 门禁，待 M6 同步新版流程，不提前执行发布。

### 14.0 首个实施切片：隔离的原生 scope 探针（2026-11-12）

- 基线提交与既有工作树内容已记录，不创建 tag、不回滚用户修改。
- 新增可选 provider-scope-probe：独立测试包、仅 Debug、无歌词 Hook，复用模块 App 的现有 service owner，宿主与 scope.list 从 Salt 矩阵样本生成。
- 请求只由前台手动点击触发；提交前持久化单次标记，生命周期不重放；批准/失败均回读，缺失/迟到/死亡标未知，不仿制 LSP 通知。
- staticScope=true/false 两份隔离 Debug APK 已构建并通过签名/身份/打包元数据检查；28 项 JVM 测试在两种配置下均通过。验证日志、摘要与真机清单见 [M0 scope PoC 实施记录](<../../ColorOS-Live-Lyrics-Providers/docs/M0-SCOPE-POC.zh-CN.md>)。2026-11-13 用户截图与摘要已确认 LSPosed 2.2.1 / API 102、SDK 37 下 staticScope=true 的原生通知→允许→回读。后续用户手动确认允许、拒绝、不再询问均正常；已补充 OnePlus 13（PJZ110）、PJZ110_17.0.0.103(CN01)、LSPosed 2.2.1 (7941) 的设备事实并停止探针试验。真实专用 Provider 管理授权集成尚未开始。
- 未改生产 staticScope、发布链路或 UI 技术选型。Compose 原型、托管与目录密钥仍待后续 M0 切片，不能标 M0 全部完成。

### 14.1 外部 API 证据（查询于 2026-11-11）

- [libxposed service 102.0.0：固定提交的 XposedService](<https://raw.githubusercontent.com/libxposed/service/3318940876192e29cf6ab07637e899e22a87ebf0/service/src/main/java/io/github/libxposed/service/XposedService.java>)：getScope、requestScope、removeScope、运行目标查询；接口属于当前模块。
- [现代 Xposed 模块官方指南](<https://raw.githubusercontent.com/wiki/LSPosed/LSPosed/Develop-Xposed-Modules-Using-Modern-Xposed-API.md>)：模块 App 的服务交付、现代 metadata 与 staticScope 语义。Wiki 为可变文档，不作为某个用户框架行为已通过的证据。
- [XposedServiceHelper 固定源码](<https://raw.githubusercontent.com/libxposed/service/0cfe0a4cd099f030f47251a6d5b35e259ec219c8/service/src/main/java/io/github/libxposed/service/XposedServiceHelper.java>)：listener 注册与服务生命周期；该链接为较新固定提交，实施时仍应以锁定依赖版本核对，不顺手升级依赖。
- [AOSP PackageInstaller（Android 15 固定源码）](<https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-15.0.0_r1/core/java/android/content/pm/PackageInstaller.java>)：用户操作等待/成功等平台安装状态。仅引用已读取的接口事实，不代表 ColorOS 安装器差异已验收。

### 14.2 UI 选型依据与确认范围（2026-11-11）

- [Compose Material 3 官方依赖目录](<https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3/maven-metadata.xml>)：用于区分稳定与预发布版本；本计划不把当前版本列表固定成未来实施时的唯一选项。
- [Material Components for Android 官方说明](<https://github.com/material-components/material-components-android#readme>)：Views 库已进入维护模式，新功能方向转向 Compose，因此新管理面不再以旧库作为长期扩展底座。
- [Material Components 1.14.0 说明](<https://github.com/material-components/material-components-android/releases/tag/1.14.0>)：已有 Expressive 主题/样式，可作为既有 Views 的过渡参考，不据此推导同名 Compose API 在任意版本均稳定可用。
- 用户确认的是“Compose Material 3 + 适量 Expressive + 旧 Views 渐进迁移”的方向，不等于确认了最终视觉稿、依赖版本或同意重写歌词业务；这些仍须按 M0/M5/M6 验收。

### 14.3 M0 Compose 接入验证（2026-11-14，本地）

状态：**本地构建与测试通过；未在设备上打开过该页面；未提交**（分支 `feat/m0-compose-integration`，基于 `c572437`）。这是 U-01 的“工具链验证”部分，首页/商店原型尚未开始。

冻结的依赖组合（候选，待原型阶段复核）：

| 项 | 版本 | 说明 |
| --- | --- | --- |
| AGP / Gradle | 8.13.2 / 9.2.0 | 不变；不做 AGP 9 迁移 |
| Kotlin Android 插件、Compose 编译器插件 | 2.3.20 | 与 Providers 仓库相同 |
| Compose BOM | 2025.12.01 | 实际解析 `compose.ui` 1.10.0、`androidx.core` 1.16.0；material3 1.4.0（稳定版；Expressive 组件主要在 1.5 预发布线，原型阶段再核对） |
| activity-compose | 1.9.2 | 刻意不用 1.12.x：其 AAR 要求 compileSdk 36 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 | 均不变；构建期 AAR 元数据检查通过 |
| Kotlin jvmTarget | 17 | 与既有 Java 17 一致 |

改动范围：

- 根 `build.gradle.kts` 声明两个 Kotlin 插件；`app/build.gradle.kts` 应用插件、打开 `buildFeatures.compose`、加入 BOM/ui/material3/activity-compose。
- 新增 `app/src/main/kotlin/.../management/ManagementPreviewActivity.kt`：固定浅色 `MaterialTheme(lightColorScheme())`、edge-to-edge + `Scaffold` padding，只显示一段注明“样例、不显示真实状态”的文字。Manifest 中 `exported=false`，**目前没有任何入口**，用户还打不开它。它直接继承 `AppCompatActivity`：既有 `SettingsBaseActivity` 是包内可见的 Java 类，统一语言/窗口处理留给原型阶段决定。
- 字符串新增中英文各两条；没有改 `LyricUiConfig`、偏好通道、Hook、渲染或 AOD 代码。
- 架构守卫 `BridgeArchitectureGuardTest.composeManagementUiStaysOutOfHookAndRenderCode`：`management` 包以外的任何源码不得引用 `androidx.compose`、`management` 包或该 Activity；Xposed 入口 `java_init.list` 不得指向 `management`。同一 APK 会被加载进 SystemUI，这条守卫保证 Compose 只在普通 App 进程里被引用。

本地验证：

- `writeSourceIdentityReport :app:testDebugUnitTest :app:assembleDebug`：944 tests / 0 fail / 6 skip（新增守卫 1 项）。
- `:app:minifyReleaseWithR8`：通过（未打包、未签名正式 Release；`ManagementPreviewActivity` 类名保留在 R8 输出中）。
- 体积，与基线提交 `c572437` 在临时 worktree 中用相同任务对比：R8 后 dex 2,400,096 → 3,364,904 字节（约 +0.92 MiB、+40%）；新增原生库 `libandroidx.graphics.path.so` 四个 ABI 合计约 37 KB；Debug APK 8.9 MB → 17.3 MB（Debug 不做压缩，不代表发布体积）。正式签名 APK 的最终增量未测。
- sourceDigest `4d4e64ca…`（287 输入，dirty）。

未覆盖 / 待原型阶段：真机打开 Compose 页面、冷启动与内存、大字体/TalkBack、正式签名 + R8 下的入口可达性；material3 稳定版可用的 Expressive API 清单；Compose 页面是否沿用 `SettingsBaseActivity` 的语言与窗口处理。

### 14.4 M0 首页与商店原型（2026-11-14，本地，待用户看外观）

状态：**本地构建与测试通过；尚未在设备上打开；未提交**（同分支 `feat/m0-compose-integration`）。按 U-01 要求只做“首页 + 商店列表”，全部为样例数据，用来确认视觉密度、主操作、状态文字、大字体和导航。

实现：

- 入口：Bridge 调试页新增按钮“管理界面原型（样例数据）”，打开 `ManagementPreviewActivity`。不加桌面图标（D3 待原型评审），不改 LSP 模块设置入口。
- 导航：四个顶级入口“首页 / 商店 / 外观 / 状态”，带文字和图标；窗口宽度 ≥ 600dp 时改为左侧 navigation rail，内容最大宽度 840dp。宽度判断目前用 `screenWidthDp`，没有引入 adaptive/WindowSizeClass 库。
- 首页：运行构建是否一致、设置是否应用（图标 + 文字，不只靠颜色）、“还有 N 步设置未完成 → 继续设置”、“N 项功能未生效 → 查看原因”（跳到状态页）、“N 个插件有更新 → 进入商店”。
- 商店：筛选“适合我的播放器 / 全部 / 已安装 / 更新”；卡片显示名称、专用/通用/动态封面标签、对应播放器、已安装与最新版本、异常说明（签名不同、未检测到播放器、通用/封面“在它自己的应用里设置”）。每张卡最多一个主操作：未安装→下载，有更新→更新，专用已安装→继续配置；签名不同和通用/封面已是最新时**不提供按钮**，也不提供清数据。样例覆盖全部 4 种安装状态和 3 种类型。
- 外观：一张卡片打开现有 `LyricUiSettingsActivity`，不复制任何外观配置。状态：占位文字，诊断仍在调试页。
- 所有页面顶部有“样例数据”横幅；任何下载/更新/继续设置按钮只弹出“原型：没有执行任何操作”。
- 主题：固定浅色，颜色取自现有 `res/values/colors.xml`（主色琥珀 `settings_primary_deep` 配深色文字、辅色青色、背景与卡片同设置页），间距集中为 4/8/12/16/24dp tokens。系统字体，无动态取色、无深色模式、无 Expressive 组件（material3 1.4.0 稳定版）。
- 语言：抽出 `SettingsLocaleContext`，Views 设置页与 Compose 页面共用同一个语言偏好；`SettingsBaseActivity` 改为调用它，行为不变。
- 页面状态只有选中的页签和筛选（`rememberSaveable`），旋转/重建后保留；没有任何外部操作会因重组重复执行。
- 架构守卫改为：`management` 包以外只有 `BridgeDebugSettingsActivity` 这一个普通 App 页面可以打开它，且仍不得引用 Compose。

本地验证：

- `writeSourceIdentityReport :app:testDebugUnitTest :app:assembleDebug :app:minifyReleaseWithR8`：949 tests / 0 fail / 6 skip（新增 `PrototypeModelTest` 5 项：主操作映射、通用/封面不在商店配置、签名不同无操作、筛选语义、样例覆盖）。没有 Compose UI/截图测试（需要额外测试依赖，留到 U-02）。
- R8 后 dex 3,806,068 字节：比 14.3 的 Compose 空页 +0.44 MB，比接入 Compose 前的基线 2,400,096 字节合计约 +1.41 MB。Debug APK 23.9 MB（未压缩，含完整 material-icons-core）。
- 候选：`artifacts/m0-prototype-20261114/Bridge-v4.5.4-M0-prototype-debug.apk`，SHA-256 `98488fed…831775e7`，Debug 证书 `5a6fd43f…`，sourceDigest `3f177baa…`。

自查（按 material-3 十类，仅源码阅读，未上设备；不打分）：颜色均用主题角色且成对使用，主按钮为琥珀底深色字；排版只用主题字级；形状用主题 shapes；层级靠卡片与背景的明暗差，没有阴影；组件均为官方 Material 3；布局有最大宽度和 rail；导航四入口；动效只有组件默认；无障碍上图标都有文字伴随（图标 `contentDescription` 为空，由文字承担），触控尺寸用组件默认 48dp，**大字体、TalkBack、对比度未在设备上检查**；主题固定浅色。

请用户在设备上确认（需当次授权安装）：

1. 从调试页进入原型，四个页签能切换，返回键回到调试页。
2. 首页与商店的信息密度、按钮主次、状态文字是否清楚；样例横幅是否足够醒目。
3. 系统字体放大到最大后有无截断或重叠。
4. 中文/英文切换（设置页语言开关）后原型跟随。
5. 旋转或分屏后页签与筛选保留。
6. 主色琥珀 + 青色辅色与现有设置页是否一致、是否需要调整。
7. 是否同意以“首页 / 商店 / 外观 / 状态”四入口作为 5.0 管理面结构，以及是否新增桌面入口（D3）。

**用户设备反馈（2026-11-14，PJZ110，候选 `98488fed…`）：**

| 项 | 结果 |
| --- | --- |
| 1 进入、切换、返回 | 通过 |
| 2 信息密度与内容 | 要求首页显示**当前模块状态**与 **SystemUI 加载状态**（文字 + 图标）→ 已改，见下 |
| 3 最大字体 | 无截断、无重叠 |
| 4 中英文切换 | 原型跟随 |
| 5 旋转 / 分屏 | 页签与筛选保留 |
| 6 配色 | 与现有设置页一致 |
| 7 结构与入口 | 同意四入口结构；**要新增桌面图标，并在设置中可选隐藏桌面入口**（D3 已定） |

大字体、TalkBack 与实际对比度中，只有“最大字体无截断”有用户证据；TalkBack 与对比度仍未检查。

**按反馈修改（r2，本地）：** 首页状态卡改为三行，各自图标 + 文字：模块状态（已启用且作用域含系统界面 / 未启用或缺作用域 / 未知）、SystemUI 加载状态（已加载当前版本 / 仍在运行旧版本需重启 / 尚未加载 / 未知：没有回应）、设置应用。删除原“运行构建”一行（被 SystemUI 加载状态取代）。样例刻意显示“仍在运行旧版本”以展示警告样式。新增 `PrototypeModelTest` 1 项：已安装未加载、未加载都算“需要处理”，未知不算正常。950 tests / 0 fail / 6 skip，Debug 与 R8 通过；候选 `artifacts/m0-prototype-20261114/Bridge-v4.5.4-M0-prototype-r2-debug.apk`（`901b4999…`），sourceDigest `bca57c08…`。仍是样例数据，**尚未接入真实状态**；未上设备。

**首页状态卡接入真实状态（r3，本地，2026-11-14）：** 只有首页状态卡改为实时；首页其余卡片与商店仍是样例，首页横幅改为“状态卡是本机实时查询结果；下方卡片和商店仍是样例数据”。

- 数据来源：新增 App 侧 `ModuleStatusQuery`（Java），发送调试页已在用的只读 `ACTION_REQUEST_MODULE_STATUS` 广播到 SystemUI，读取回包中的注入构建 `cll_runtime_build_id` 与已应用设置 revision `cll_ui_config_revision`，并核对回包进程为 `com.android.systemui`。已安装构建取本 App 进程的 `BuildConfig.BUILD_ID`（即已安装 APK）。不新增任何 SystemUI 侧代码。
- 判定（`homeStatus`，纯函数）：
  - 查询中 → 三行都显示“正在查询…”。
  - 2.5 秒无回包或发送失败 → 模块状态“系统界面没有回应：可能未启用、作用域没有勾选系统界面，或启用后还没重启”（警告），另两行未知。App 进程读不到框架的启用/作用域状态，所以**不会显示“未启用”**。
  - 有回包 → 模块状态“已在系统界面中运行”；构建相同为“已加载当前安装的版本”，不同为“仍在运行旧版本，重启手机后生效”（警告），任一侧缺失为未知；设置 revision ≥ 0 为“已应用它启动后收到的最近一次设置”，-1 为“启动后还没收到过设置推送，正在使用启动时读取的设置”（中性，不算失败）。
- 时机：每次 `onResume` 重新查询（从 LSPosed 返回或重启后能刷新），另有“重新检查”按钮；暂停时作废未完成的请求，迟到回包和超时都不会覆盖新结果。
- 已知局限：设置行只能说明 SystemUI 启动后是否收到过推送，不能证明“你刚才那次修改”已生效（App 侧不保存最近一次发送的 revision）；需要逐次回执时再加持久化。
- 本地验证：954 tests / 0 fail / 6 skip（`PrototypeModelTest` 现为 10 项，新增查询中、无回包、构建相同/不同、构建缺失、启动后未推送 5 项），Debug 与 R8 通过。候选 `artifacts/m0-prototype-20261114/Bridge-v4.5.4-M0-prototype-r3-live-status-debug.apk`（`4c98f6ad…`），sourceDigest `f991bfe2…`。未上设备。
- 设备上预期：覆盖安装 r3 后不重启，SystemUI 仍运行此前加载的构建，首页应显示“仍在运行旧版本”；重启后应变为“已加载当前安装的版本”。在 LSPosed 中关闭模块并重启后应显示“没有回应”。
- **用户设备确认（2026-11-14，r3 `4c98f6ad…` 由 agent 经当次授权 adb 安装）**：三项均通过——未重启时显示“仍在运行旧版本”，重启后“已加载当前安装的版本”，在 LSPosed 关闭模块并重启后显示“没有回应”。

**商店接入真实安装状态与打开按钮（r4，本地，2026-11-14，按用户要求）：**

- 用户要求：所有专用插件加“调试”按钮，进入各自的调试页、各自开启 debug；通用歌词与动态封面加“打开”按钮，直接跳到各自主页。
- 商店列表改为官方 15 项（`ProviderDirectory`，与 `v5-provider-matrix.json` 一致），安装状态与版本由本机 PackageManager 实时读取，“适合我的播放器”按矩阵中的宿主包是否已安装判断。样例商店数据已删除。
- 按钮：
  - 专用插件已安装 →“调试”，打开该插件自己导出的 `MAIN + INFO` 页（即 LSPosed 打开的那一页），debug 开关在插件页内由用户自己切换；商店不读写任何插件配置。12 个专用插件是调试页。
  - **Readify 例外**：它的 INFO 页只有模块信息和只读运行诊断，没有调试开关，所以按钮写“模块信息”，卡片注明原因。
  - 通用歌词、动态封面已安装 →“打开”，使用其桌面启动入口。
  - 未安装 →“下载”，提示“商店目录尚未接入”；“更新”筛选显示“目录未接入，暂不能检查更新”。签名不同、有更新这两种状态需要目录，暂不产生。
  - 打不开（页面不存在、无权限）时提示“无法打开 X 的页面”，不崩溃。
- 包可见性：Manifest 新增一段定向 `<queries>`，列出 15 个插件包与 17 个宿主播放器包；不使用 `QUERY_ALL_PACKAGES`。
- 架构守卫按第 10 节重划：Provider applicationId 只允许出现在 `management` 包与 Manifest 的 `<queries>` 中；Hook/bootstrap/render 代码仍禁止，其他 v4 transport 标记全局禁止不变。
- 新增 `ProviderDirectoryContractTest`：目录与 Providers 矩阵逐项相同（模块、包名、宿主包），Manifest `<queries>` 覆盖全部插件与宿主。构建在替换盘符下看不到相邻仓库时需通过环境变量 `CLL_PROVIDER_MATRIX` 指定矩阵路径，否则该测试跳过；本次已指定并通过。
- 本地验证：957 tests / 0 fail / 6 skip，Debug 与 R8 通过（R8 dex 3,816,736 字节）。候选 `artifacts/m0-prototype-20261114/Bridge-v4.5.4-M0-prototype-r4-store-debug.apk`（`0d00b3dc…`），sourceDigest `8af97562…`。未上设备。
- **用户设备确认（2026-11-14，r4 `0d00b3dc…`，agent 经当次授权 adb 安装）**：安装状态与版本、专用插件“调试”进入各自调试页、通用歌词/动态封面“打开”、筛选与返回均通过；Readify 卡片在“全部”中可见（“适合我的播放器”因本机未装宿主不显示，符合设计）。**M0 首页/商店原型由用户确认通过。**

### 14.5 M0 剩余决策：D1 托管 与 D2 目录签名（2026-11-14，用户已决定）

> 状态：**决策已定（见第 14.5.1 节），尚未实现，未申请任何仓库或密钥**。本节为第 13 节 D1/D2 的展开，是 M2 的输入。

**先说一个前提，它决定了 D1 可以放松多少：** 第 6.2 节要求目录独立签名、APK 校验包名/versionCode/大小/SHA-256/证书。客户端只信“验签通过的目录 + 摘要一致的 APK”，所以**存储和 CDN 只负责“能不能下到”，不负责“下到的是不是真的”**。托管被篡改、被劫持或镜像被替换，结果只会是校验失败、拒绝安装，不会装上假包。因此 D1 可以按可达性和成本选，也可以同时挂多个镜像。

**现有事实（静态确认）：**

- 体量：4.5.4 的 15 个 Provider/封面 APK 合计约 26 MB，单个 0.15–4.7 MB；一个用户通常只装 1–3 个，单次下载约 2–6 MB。目录本身预计几 KB。
- 签名：Bridge 与所有 Provider 目前共用一张正式证书（`bridge-release-contract.json` 的 `releaseCertificateSha256`），由 Actions secrets 签名。
- 客户端：Bridge `minSdk = 26`；Provider 矩阵 `minSdk` 27/30。目录验签在 Bridge 普通 App 进程（管理页）中做，算法须在 API 26 可用。
- 用户主体是 ColorOS 国行设备，中国大陆可达性是主要风险。

#### D1 托管：可选方案

| 方案 | 大陆可达性 | 费用/门槛 | 运维 | 主要代价 |
| --- | --- | --- | --- | --- |
| A. 公开 GitHub Release（独立的商店仓库，每版不可变 tag） | 差且不稳定，常需代理 | 免费 | 最低，可复用现有 Actions | 与第 6.2 节“不以公开 Provider Releases 作为默认后端”冲突；大陆用户体验差 |
| B. Cloudflare R2 + 自有域名 | 一般，时好时坏 | 需域名；10 GB 存储与出站流量基本免费 | 低 | 大陆速度无保证；需要一个域名 |
| C. 大陆对象存储 + CDN（阿里云 OSS / 腾讯云 COS 等） | 最好 | 自定义域名需 ICP 备案（实名）；按流量计费，以本体量每月很低 | 中：备案、账号、计费 | 备案与实名门槛；账号归属需明确 |
| D. 香港区域对象存储（不走大陆 CDN） | 较好 | 无需备案；流量单价高于大陆但体量小 | 中 | 晚高峰可能慢 |
| E. 多镜像：目录为每个 APK 列出多个 URL（同一 SHA-256），客户端按顺序回退 | 取决于组合 | 组合成本 | 中：每次发布需同步到全部镜像，晋级前逐个校验 | 发布工具更复杂；须保证“全部镜像就绪”后才晋级目录 |

**推荐：E = B 为主 + A 作为回退**（或 C/D 为主 + B 回退，若你愿意备案或付费）。理由：完整性不依赖托管，多镜像几乎只增加发布脚本工作；B 与 A 都免费，且不需要备案。若采用 A 作为回退，需修改第 6.2 节的措辞：A 只作为“由签名目录指向的镜像”，不作为默认或唯一来源，用户也不直接从 Release 页面挑包。

**需要你决定的：** ① 是否已有或愿意注册一个域名；② 是否接受 ICP 备案（决定 C 是否可行）；③ 每月可接受的费用上限（可以是 0）；④ 账号和域名归谁（建议归你本人账号，CI 只拿最小权限的上传凭据）。

**无论选哪种都必须做（M2 门禁）：** 对象路径带版本且不可变（不覆盖已发布对象）；先上传并逐个镜像校验 SHA-256，最后才晋级目录；客户端只接受发布配置中列出的 HTTPS 主机与重定向；托管不可用时只阻止新安装或更新，不影响已装功能（第 13 节 D8）。

#### D2 目录签名：可选方案

**算法（客户端须在 API 26 上原生可用，不自行实现密码学）：**

| 方案 | API 26 原生支持 | 依赖 | 评价 |
| --- | --- | --- | --- |
| ECDSA P-256 + SHA-256（`SHA256withECDSA`） | 是 | 无 | **推荐**。签名短，平台实现成熟；硬件密钥（如 YubiKey PIV）也支持 |
| Ed25519 | 否（平台从 API 33 才有） | 需引入 Tink 等库，增加 APK 体积和审计面 | 算法更现代，但为它增加依赖不划算 |
| RSA-PSS 3072 | 是 | 无 | 可用；签名和公钥较大，无额外收益 |

**签名位置（决定 CI 被攻破时的影响）：**

| 方案 | 做法 | 优点 | 代价 |
| --- | --- | --- | --- |
| S1. CI 自动签名 | 目录私钥放在 Actions secrets | 全自动 | 目前 APK 签名密钥也在 CI；一次 CI 泄露即可同时伪造 APK 和目录，第 6.2 节“两套信任”实际失效 |
| S2. 受保护 Environment 签名 | 私钥放在带必需审核人的 GitHub Environment，晋级任务须你点批准 | 自动化程度高，每次晋级有人工确认 | 仍是同一平台托管的密钥，只多了一道审批 |
| S3. 本地离线签名 | CI 只产出待签目录；你在本机（或硬件密钥）用脚本签名后上传 | 目录密钥与 CI 完全隔离 | 每次发布和续期都要手动一步 |

**推荐：P-256 + S3**；若手动一步太麻烦则用 S2。理由是本项目已经让 CI 持有 APK 签名密钥，目录签名再放进 CI 就失去独立性。

**密钥与时效（推荐值，可调）：**

- Bridge 内置**两把**目录公钥：主用钥与离线备用钥，envelope 中用 `keyId` 区分。主钥泄露或丢失时切换到备用钥签名，并随下一版 Bridge 更换新的一对；不做在线吊销服务。
- 目录不能引入新的 APK 证书：客户端只接受内置的正式证书 SHA-256。所以即使目录密钥泄露，攻击者也只能“回滚到旧的真包”或“不让更新”，不能装上假包；revision 只增不减可以挡住回滚。
- `expiresAt = issuedAt + 180 天`，允许 ±24 小时时钟偏差。过期后只停新安装和更新，已装状态照常显示。180 天意味着即使没有新版本，也至少每半年重签一次；若选 S3，这是一个需要提醒的人工步骤。
- 随 Bridge 打包一份已签名的目录快照，作为首次启动的数据和 revision 下限。

**需要你决定的：** ① 算法（推荐 P-256）；② S1/S2/S3 选哪个；③ 是否有硬件密钥（没有也可以用本地加密文件，离线备份到你选的位置）；④ 过期周期（推荐 180 天）。

**无论选哪种都必须测试（M2 门禁）：** 错签名、未知 keyId、篡改 payload 一个字节、revision 降序重放、过期与时钟偏差边界、APK 摘要/大小/证书/包名/versionCode 不符、零值或占位字段，均须拒绝。正式私钥不入库；仓库只放正式公钥（内置到 Bridge）和测试用的一次性密钥对。

#### 14.5.1 用户回复（2026-11-14）与 LSPosed 官方仓库方案分析

**用户回复：** D2 选“受保护 Environment”，过期周期 180 天；D1 与域名问题均回复“利用 LSP 官方公开仓库（modules.lsposed.org），官方似乎对大陆做了优化”，即不另购域名或对象存储。

**已核实的事实（2026-11-14 读取 `modules.lsposed.org/module/io.github.andrealtb.lockscreenlyrics.json`）：**

- 本项目已在用该仓库：Bridge 的 LSP 仓库 `Xposed-Modules-Repo/io.github.andrealtb.lockscreenlyrics` 每版附 19 个资产（含全部 Provider 与动态封面），由 `release.yml` 用 `LSP_REPO_TOKEN` 发布。
- 网站 JSON 中每个资产有两个地址：`originalDownloadUrl`（GitHub 原地址）与 `downloadUrl`（`assets.lsposed.org/...` 代理地址，带 `sign=` 与 `t=` 参数）。代理地址带签名参数，含义与有效期未公开，**不能作为固定地址写进签名目录**。
- 动态封面（`io.github.andrealtb.artwork.am`）是普通 App（绑定服务），不是 Xposed 模块；矩阵中其 `distribution` 为 `bridge-release`。它不能单独申请 LSP 模块仓库。
- “对大陆做了优化”：本机无法验证，标为 **unknown**，需要用户用手机在移动数据和 Wi-Fi 下分别实测 `assets.lsposed.org` 的下载速度。

**两个变体：**

| 变体 | 做法 | 优点 | 代价 |
| --- | --- | --- | --- |
| L1. 继续挂在 Bridge 的 LSP 仓库 | 维持现状，商店目录指向 Bridge 每版附带的 Provider 资产 | 零新增仓库，现有流程可用 | Provider 无法独立发版：每次只改一个 Provider 也要发一个 Bridge LSP Release，LSPosed 管理器会把它显示成 Bridge 更新；与第 6.1 节、第 11 节 M2“独立 Provider 更新不要求新 Bridge tag”冲突 |
| L2. 每个 Xposed Provider 各自一个 LSP 仓库 | 13 个专用 + 通用 = 14 个仓库，仓库名等于包名；动态封面仍挂在 Bridge 的 LSP 仓库 | 每个 Provider 独立版本；用户在 LSPosed 管理器里也能原生看到和更新；同样走 assets.lsposed.org | 14 个仓库需逐个向 LSPosed 维护者提交并获批；CI token 需对 14 个仓库有写权限；LSP 列表中出现 14 条；动态封面更新仍需随 Bridge 发版 |

**两种变体共同需要解决的问题：**

1. **许可（硬前置）：** 商店是第三方 App 直接使用 LSPosed 的下载代理和网站 JSON，不是 LSPosed 管理器本身。须由用户先向 LSPosed 维护者确认允许；这是对外沟通，由用户本人进行。
2. **非稳定接口：** `modules.lsposed.org` 的 JSON 是网站内部格式，没有公开的稳定性承诺。客户端只把它当作“换取下载地址”的提示：目录中固定写入 GitHub 原地址 + 资产名 + 大小 + SHA-256；下载时查询网站 JSON，按资产名找到代理地址；代理不可用或格式变化时回退到 GitHub 原地址。完整性只看目录中的摘要和证书，不信网站 JSON 的任何字段。
3. **可信主机：** 发布配置的 HTTPS 白名单为 `modules.lsposed.org`、`assets.lsposed.org`、`github.com` 及其 Release 下载重定向主机；不接受其他重定向。
4. **索引延迟与原子晋级：** 网站 JSON 由 LSPosed 定期重新生成，发布后不会立即出现。晋级流程为：发布 Release → 轮询网站 JSON 直到资产名和大小一致 → 下载代理地址并核对 SHA-256 → 才签名并晋级目录。超时则不晋级。
5. **目录本身放哪里：** 目录只有几 KB 且已签名，托管不影响真实性。建议放在 Providers 仓库的固定路径，客户端通过 jsDelivr（`cdn.jsdelivr.net/gh/...`）读取，`raw.githubusercontent.com` 作为回退；首次启动使用随 Bridge 打包的签名快照。这样仍无需域名。jsDelivr 在大陆的可达性同样标 **unknown**，需实测。

**推荐：L2**（动态封面保持 L1 方式）。理由：独立版本是商店的核心目标，L1 会让每次 Provider 更新都表现为 Bridge 更新。前置条件：先完成第 1 项许可确认，再提交 14 个仓库申请；若维护者不同意第三方使用代理，退回第 14.5 节方案 B（R2）或 E。

**用户已选 L2（2026-11-14）。** 用户随后回复：① LSPosed 维护者**允许**第三方 App 使用其下载代理与网站 JSON（用户转述，未见书面记录，本文不存档对外沟通内容）；② 无需实测，用户判断 `assets.lsposed.org` 稳定。据此 D1 冻结。jsDelivr（目录读取）在大陆的可达性仍为 **unknown**；客户端保留 `raw.githubusercontent.com` 回退与内置签名快照，M2 设备验收时顺带观察。

#### 14.5.2 D1 最终决定（2026-11-14）：方案 M

用户在对比 L2（14 个 LSP 仓库）后改选**方案 M**，理由是项目为爱发电，不值得维护 14 个仓库；大陆网络问题由用户自行处理，或以后套一层免费 CDN。第 14.5.1 节的 L1/L2 分析保留作历史记录，**不再执行**；LSPosed 维护者许可与 `assets.lsposed.org` 均不再是商店依赖。

- **托管：** `Andrea-lyz/ColorOS-Live-Lyrics-Providers` 的公开 GitHub Release。tag 采用 `<providerId>-v<versionName>`，每个 Release 只附一个 Provider APK；已发布 Release 不覆盖，修复用新版本。
- **目录：** 签名目录放在同一仓库的固定路径，客户端经 jsDelivr 读取、`raw.githubusercontent.com` 回退，首次使用内置签名快照。
- **CDN（可选，以后）：** 只作为镜像地址加入目录；受信主机列表随签名目录下发，加镜像不需要升级 Bridge。CDN 只是传输通道，不参与信任。
- **Bridge Release：** 按第 6.2 节不再附 Provider APK；动态封面的分发方式（目前 `bridge-release`）在 M2 中改为同样走 Providers 仓库的独立 tag，具体以 M2 实施为准。
- **与 D2 的关系：** 不变（受保护 Environment、P-256、180 天）。

### 14.6 M2 第一刀：目录签名格式、客户端验签与签名工具（2026-11-14，本地）

> 状态：**本地实现与测试通过，未接入 UI、网络或安装流程，未上设备，未提交**。正式目录密钥尚未生成，生产信任集为空。

**格式（D2）：** 信封 `envelopeVersion`=1、`keyId`、`algorithm`=`ECDSA_P256_SHA256`、`payload`（目录 JSON 原始 UTF-8 字节的 base64）、`signature`（DER）。签名内容为 `CLL-CATALOG-V1
` + payload 字节；客户端先验签，再只解析已签字节，不依赖 JSON 规范化。payload schema 1 在第 6.3 节草案基础上落为：`catalogRevision`、`issuedAtEpochSeconds`/`expiresAtEpochSeconds`、`channel`、`trustedHosts`、`entries[]`（`providerId` 即矩阵 `module`、`kind`、`applicationId`、`versionName`、`versionCode`、`minSdk`、`minStoreProtocol`、`sourceCommit`、可选 `releaseNotes`、`artifact{urls[], bytes, sha256, signerSha256[]}`）。`scopePolicy`、`supportedHosts`、`requires` 等留待 M3/M4 需要时以新字段加入（未知字段忽略）。

**Bridge（分支 `feat/m2-catalog-verification`）：** 新增 `management/catalog/`，纯 JVM，无 Android/网络/存储/Hook 依赖：

- `CatalogVerifier`：整份拒绝，不存在“部分可信”。拒绝原因：过大、信封格式/版本/算法、未知 keyId、签名错误、payload 非 UTF-8/非 JSON、schema、渠道不是 stable、有效期非法或超过 190 天、早于 issuedAt 超过 24 小时、revision 降序（ROLLBACK）、同 revision 不同内容（REVISION_CONFLICT）、条目字段非法或为 0/占位、URL 非纯 https 或主机不在已签 `trustedHosts`、签名证书不在客户端内置集合、重复条目。过期超过 24 小时仍接受但标 `EXPIRED`（调用方据此只显示已安装状态、阻止安装/更新）。数字只接受整数类型，不接受 org.json 会自动转换的字符串或小数。
- `CatalogTrust`：生产目录公钥表为空（待在受保护 Environment 生成主用 + 备用两把后填入）；只接受 P-256（按群阶校验）；内置正式 APK 证书与 `bridge-release-contract.json` 由测试绑定。
- `ArtifactCheck`：流式测量大小与 SHA-256（超限即停），以及包名/versionCode/签名者核对；签名者必须全部在条目允许集合内。从 APK 读取包信息的 Android 适配留到 M3。
- `entry.supportedBy()`：要求更新商店协议的条目照常列出但不可安装。

**Providers（分支 `feat/m2-catalog-tooling`）：** 新增 `scripts/catalog/`：`catalog-envelope.mjs`（与客户端同一套规则，签名前校验并用派生公钥自检）、`sign-catalog.mjs`（私钥来自 `--key` 或 secret `CLL_CATALOG_SIGNING_KEY`，不覆盖已有输出）、`verify-catalog.mjs`、`README.zh-CN.md`、测试与夹具。夹具使用标明 TEST-ONLY 的一次性密钥；Bridge 只复制公钥与签出的信封，证明 Node 签名与 Kotlin 验签字节一致。

**本地验证：** Bridge `:app:testDebugUnitTest :app:assembleDebug` 通过，976 tests / 0 fail / 6 skip（新增 `CatalogVerifierTest` 15 项、`ArtifactCheckTest` 4 项，均未跳过；设置了 `CLL_PROVIDER_MATRIX`）。Providers `node --test scripts/catalog/catalog-envelope.test.mjs` 7 项通过（Node 24）。新代码尚无运行时调用方，未单独跑 R8。

**已知差异：** JVM 上的 org.json 对重复键报错，Android 平台 org.json 后者覆盖前者；签名覆盖原始字节且签名工具只输出 `JSON.stringify` 结果（不会有重复键），因此不影响真实性，记录备查。

**M2 第二刀（2026-11-14，本地，未提交）：** Providers `scripts/catalog/` 新增 `catalog-payload.mjs`、`apk-facts.mjs`、`build-catalog-payload.mjs` 与测试。由矩阵和实际 APK 生成下一版目录：包名、版本、minSdk 读 `aapt2 dump badging`，签名者读 `apksigner`，大小与 SHA-256 直接计算；必须与矩阵一致、只由正式证书签名；在已验签的上一版目录上生成，revision 加 1，其余条目原样保留；已发布 versionCode 不能降低或换字节重发，无变化时拒绝新 revision；另提供只续期的 `renewPayload`。下载地址为 `github.com/<repo>/releases/download/<providerId>-v<versionName>/<文件名>`。

- 本地验证：Node 测试 14 项通过（目录信封 7 + 生成 7）。用本机 `logs/release-lsp-4.5.4/` 的 4.5.4 **正式签名** APK 端到端跑通“读 APK → 生成 → 测试密钥签名 → 验签”，Salt 1.1.2/8 与动态封面 1.0.6/10 的大小与 SHA-256 与文件一致。
- 按规则被拒绝的实例：4.5.4 公开的 Universal 是 1.1.2/4，矩阵已升到 1.1.3/5，因此拒绝；M1 的 debug 签名 Salt 拒绝；同内容重发拒绝；被篡改的上一版目录验签失败并拒绝。
- 未覆盖：没有在 CI 中运行；没有接入任何发布 workflow；正式目录密钥仍未生成。

**M2 第三刀（2026-11-14，本地，未提交，未在 GitHub 运行）：** Providers 新增 `.github/workflows/provider-store-release.yml`、`release/catalog-trust.json`（只含公钥，目前为空）、`scripts/catalog/resolve-release-tag.mjs`、`generate-catalog-key.mjs` 与测试；`build-catalog-payload`/`verify-catalog` 支持 `--trust`，信任文件与 Bridge 都用 base64 SPKI 格式存公钥。

- 触发：`main` 上的 tag `<module>-v<versionName>`，必须与矩阵版本一致；`providers-v*` 旧套件 tag 排除。
- build：要求 tag 指向的提交在 main 上；跑目录工具测试与整份 `testV5Matrix`（共享代码，第 9.1 节），动态封面另跑自身测试与协议镜像；正式签名构建单模块，只收一个 APK，按目录规则核对身份与证书。
- publish：创建不可覆盖的 Release（仅 1 个 APK + `.sha256`），再从公开地址下载核对 SHA-256。
- promote：受保护 Environment `provider-catalog`（审批 + `CLL_CATALOG_SIGNING_KEY` + 变量 `CLL_CATALOG_KEY_ID`）；以重新下载到的已发布字节为数据源，验签 `store-catalog` 分支上的当前目录后生成并签名下一版，普通 push 到 `store-catalog:catalog/stable.json`；全局串行。客户端读取地址预定为 jsDelivr `gh/<repo>@store-catalog/catalog/stable.json`，`raw.githubusercontent.com` 回退（jsDelivr 对分支有缓存延迟，revision 单调保证不会回退）。
- 本地验证：Node 测试 17 项通过；workflow YAML 能解析（本机无 actionlint）；在临时仓库模拟了“首次建 `store-catalog` 孤儿分支 → 再次快进晋级”的 git 步骤，并据此把检出改为 `worktree add -B`；`generate-catalog-key.mjs` 拒绝 `test-` 前缀、拒绝写入仓库目录、不覆盖已有文件。
- Bridge 新增测试把 `CatalogTrust` 的生产公钥与正式证书绑定到 Providers `release/catalog-trust.json`（两边目前都为空公钥表、同一证书）；Bridge 977 tests / 0 fail / 6 skip，Debug 构建通过。
- **未覆盖：** workflow 从未在 GitHub 上运行；Environment、secret、变量、正式密钥均未创建（需用户操作，见工具 README）；`store-catalog` 分支不存在；Release 说明与目录说明的模板未定。

**M2 第 ③ 项（2026-11-14，本地，未提交）：** 用户已在本机生成正式目录密钥对 `cll-catalog-2026a`（主用）与 `cll-catalog-2026b`（离线备用），并已在 GitHub 上建好 Environment `provider-catalog`、secret 与变量（用户转述，agent 未登录 GitHub 核对）。两把公钥（base64 SPKI，P-256）已填入 Providers `release/catalog-trust.json` 与 Bridge `CatalogTrust.PRODUCTION_CATALOG_KEYS`，与用户贴出的生成输出逐字一致，绑定测试通过。生产信任集不再为空，测试夹具（`test-only-2026`）仍被生产信任拒绝。未用正式私钥签出任何目录。Bridge 977 tests / 0 fail / 6 skip，Node 17 项通过。

**M2 第 ⑤ 项：拆分 Bridge 发布契约（2026-11-14，本地，未提交）：** Bridge 分支 `feat/m2-split-release-contract`（基于 `6598dbd`），Providers 分支 `feat/m2-catalog-tooling` 追加一处未提交改动。

- `bridge-release-contract.json` 升为 schema 2：删除 `providerBundleAsset`、`providerApkCount`、`artworkProviderApkCount`；新增 `providerDistribution = provider-store`；`totalApkCount = 1`、`totalReleaseAssetCount = 3`（Bridge APK、`release-assets-v<version>.json`、`SHA256SUMS`）。`suiteVersion` 字段名保留，含义即 Bridge 版本。
- `validate-release-contract.ps1`：校验 schema 2、退役字段不得回归；跨仓库只核对动态封面模块身份，不再要求 Bridge 与 Provider 套件版本相同；正式模式要求矩阵中动态封面 `distribution = provider-store`。新增 `-PreviewProviders` 开关，供仍附带 Universal/动态封面预览 APK 的旧预览链路（`preview.yml`、`artwork-provider-candidate.yml`，本次只加该开关，未改其行为）保留原有版本对照。
- `build-release-assets.ps1`：只接收并核对 Bridge APK，不再生成 Provider ZIP；manifest schema 2 记录 `providerDistribution` 与契约测试所绑定的 Provider commit。
- `release.yml`：删除 `build-providers` 作业；Bridge 构建作业只读检出冻结的 Provider commit 并设置 `CLL_PROVIDER_MATRIX`，使商店目录与目录信任绑定测试在 CI 中真正运行而非跳过。
- Providers 矩阵：动态封面 `distribution` 改为 `provider-store`。矩阵中的 `suiteVersion`、`bundleAsset` 与按套件命名的 `asset` 仍供旧的整矩阵构建 `android-release.yml` 使用，作为“定期全矩阵验证”保留（第 9.2 节），未改。
- `RELEASE_PROCESS.md`：资产白名单、RC 步骤、复核步骤改为 3 项；新增第 12 节“Provider 商店发布”；写明合入顺序门禁。
- 本地验证：正式模式与预览模式校验器均通过；用本机 4.5.4 正式签名 Bridge APK 跑新资产脚本，产出恰好 3 项、manifest schema 2。应拒绝的情况均被拒绝：输入多一个 Provider APK、对照 Providers `main`（仍为 `bridge-release`）的正式模式、契约重新出现 `providerApkCount`。当时以为 Bridge 单元测试只读取该契约的证书字段，未重跑 Gradle；第 ④ 项重跑时发现 `Phase7DocumentationContractTest` 仍按旧套件规则检查发布文档（要求出现 19 项与动态封面资产名）而失败，已改为检查新规则（“恰好 3 项”、`provider-store-release.yml`、`provider-store`）。
- **合入顺序门禁：** schema 2 只能在 M3（商店可安装 Provider）之后进入正式发布；Providers 的矩阵改动须先进入其 `main`。README、安装教程与 LSPRepo 下载说明尚未改（第 9.2 节，留到 M6）。

**M2 第 ④ 项：内置签名快照与 revision 持久化（2026-11-14，本地，未提交）：** 同在 Bridge 分支 `feat/m2-split-release-contract`。

- `CatalogStore`（纯 JVM）：在“已持久化目录 / 本 Bridge 内置快照 / 远端新目录”中选用目录。每个候选都以持久化的 revision 下限验签，因此镜像或较旧的内置快照都不能让商店回退；下限只升不降，且先写入再使用。较新的内置快照（随 Bridge 升级带来）会抬高下限。持久化失败时不使用未落盘的新目录（`CatalogOffer.PersistFailed`）。过期目录可用于显示但 `installable = false`。
- `FileCatalogPersistence`：单个 JSON 文件，放在 `noBackupFilesDir/store`（备份恢复不能重置或伪造下限），写临时文件后 `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` 替换；读取到损坏内容视为无。`BundledCatalogSnapshot` 读取 APK assets 中的 `catalog/stable.json`。两者尚无运行时调用方（M3 接入）。
- 快照来源：正式私钥只在受保护 Environment 中，本地不签快照。`release.yml` 在构建 Bridge 前从冻结 Provider commit 所在仓库取 `store-catalog` 分支的当前目录，用 `catalog-trust.json` 验签后写入 `app/build/generated/catalogSnapshot/catalog/stable.json`；`app/build.gradle.kts` 把该目录加入 assets。正式发布缺快照即失败，RC 只警告。
- 本地验证：Bridge 987 tests / 0 fail / 6 skip（新增 `CatalogStoreTest` 10 项），Debug 构建通过；放入测试快照后 APK 含 `assets/catalog/stable.json`，删除后重建即不含；release.yml 可解析。**未覆盖：** 快照嵌入步骤未在 GitHub 运行（`store-catalog` 分支尚不存在）；内置快照未在设备上读取过。

**M2 剩余：** 所有切片均已在本地完成；workflow 均未在 GitHub 运行，首个正式目录尚未发布。下一步为 M3（商店安装更新：已安装发现、下载、PackageInstaller、恢复、更新提醒），届时接入 `CatalogStore` 与 `ArtifactCheck`。

### 14.7 M3：目录接入商店、下载校验、安装事务与后台更新提醒（2026-11-14，本地，已提交 `2516ad2`）

分支：Bridge `feat/m3-store-install`（基于 `e242f60`）。Providers 未改。

**第一刀：目录投影与远端刷新**

- `StoreProjection.kt`（纯函数 `storeItems`）把内置目录（`ProviderDirectory`）投影到本机与已验签目录。只有 `providerId` 和 `applicationId` 都与内置目录一致的条目才算数，目录不能把已知插件指向别的包；本 Bridge 不认识的条目只计数（“需要更新 Bridge 后才能显示”）。
- 安装状态按各插件自己的 versionCode 判定：未安装 / 已安装 / 有更新 / 本机更新 / 签名不同。本机签名（只取当前签名，不含轮换历史）不全在条目允许集合内、或读不到，都判为签名不同，不提供更新。
- 是否可下载（`Availability`）：无目录、目录过期（只读）、目录中没有、需要更新 Bridge（`minStoreProtocol`）、系统版本过低（`minSdk`）时都不提供下载/更新按钮。签名不同和本机更新时仍可打开插件自己的页面（例如本地 debug 构建的 Provider），但不提供覆盖安装或清数据。
- 首页“N 个插件有更新”改为实时计数，为 0 时不显示。
- `CatalogRefresher` 与 `CatalogSources`：从 jsDelivr 和 raw GitHub 两个镜像获取 `store-catalog` 分支上的 `catalog/stable.json`，每个响应都交给 `CatalogStore`，保留最高的有效 revision。镜像落后（ROLLBACK）算“已是最新”；一个镜像成功即不报其他镜像的失败。地址与 Providers `release/catalog-trust.json` 的分支/路径由测试绑定。
- `HttpsTransport`：只允许 `https`、默认端口、无 userinfo；不自动跟随重定向，每一跳都检查主机白名单；错误信息只写主机，不写路径或 query。目录响应超过 256 KiB 即停止。
- `StoreRuntime`：进程内唯一的目录存储，所有读写串行，避免两个页面同时改 revision 下限。打开商店时最多每 10 分钟自动检查一次，另有“检查更新”按钮。
- 商店顶部原“样例数据”横幅改为目录卡片：目录版本、来源（随 Bridge 内置 / 本机保存 / 刚从网络获取）、是否过期、最近一次检查的结果（含被拒原因）。

**第二刀：下载、校验与 PackageInstaller 事务（`management/install`）**

- `ApkDownloader`：下载到 App 私有缓存 `cacheDir/provider-store` 的 `.part` 文件，按条目大小限制流式计算 SHA-256；大小与摘要都与签名目录一致后才改名为 `<providerId>-<versionCode>.apk`。按目录顺序尝试镜像，坏镜像会换下一个；取消、空间不足、内容不符时删除半成品。已校验过的缓存文件可直接复用（例如用户在安装确认里取消后重试）。
- 交给安装器前用 `getPackageArchiveInfo` 读取包名、versionCode 和签名，经 `ArtifactCheck.checkPackage` 核对；读不出或不一致都删除文件，不进入安装器。
- `StoreOperations`：进程级、一次只运行一个操作，Activity 重建不会重复发起。未获“安装未知应用”授权时停在“需要授权”，按钮打开系统设置；返回后若已允许则继续，未允许不会再次弹出。
- 安装使用 `PackageInstaller` 会话（`MODE_FULL_INSTALL`，Android 12 起显式 `USER_ACTION_REQUIRED`，始终由用户确认）。状态由不导出的 `InstallStatusReceiver` 接收：
  - `STATUS_PENDING_USER_ACTION` 显示“等待你确认”，不算成功也不算失败；确认界面只在管理页可见时启动一次，用户退出后可以“重新打开”或“取消”（放弃会话）。
  - 失败分别显示：取消、签名冲突/降级、不兼容、空间不足、被系统拦截、安装包无效、其他。
  - `STATUS_SUCCESS` 之后还要回读 PackageManager，versionCode 必须等于条目，签名必须在允许集合内，才显示“已安装并核对”；否则显示“安装器报告完成，但未核对到”。专用插件成功后提示去 LSPosed 启用并勾选播放器（scope 自请求属于 M4）。
- 会话记录（`InstallRecordFile`，`noBackupFilesDir/store/install-session.json`）在提交前写入。App 被杀后重新打开时 `reconcile`：已装上则补记成功；会话还在但确认界面已丢失，则放弃会话并显示“被中断，可重试”；会话不在且没装上同样显示中断。**从不对同一记录提交第二个会话。**
- 权限：Manifest 新增 `INTERNET` 与 `REQUEST_INSTALL_PACKAGES`，只供普通 App 进程使用；架构守卫新增：`HttpURLConnection`、`HttpsURLConnection`、`java.net.URL`、`openConnection(`、`PackageInstaller` 只允许出现在 `management` 包内。这取代了动态封面方案中“Bridge 不加 INTERNET”的旧约束（第 2 节表格已列明 5.0 需要网络与安装权限）；Hook 路径仍不联网。
- 后台任务里出现意外运行时异常时，操作显示“安装失败”，不会让 App 崩溃；重定向地址格式错误按网络错误处理。

**本地验证：** `writeSourceIdentityReport :app:testDebugUnitTest :app:assembleDebug :app:minifyReleaseWithR8` 通过，1009 tests / 0 fail / 6 skip。新增 `StoreProjectionTest` 7 项、`CatalogRefresherTest` 6 项、`InstallFlowTest` 9 项（下载/镜像回退/缓存复用/取消、安装器状态映射、成功回读、reconcile、记录文件），`PrototypeModelTest` 按新规则调整为 11 项，架构守卫新增上述标记。R8 dex 3,901,460 字节（比 r4 +84,724）。合并后的 Manifest 已确认含两项新权限与 `exported=false` 的接收器。

**未覆盖 / 待设备：**

- 正式目录尚未发布（`store-catalog` 分支不存在），本地 Debug 构建也没有内置快照，所以设备上目前只会显示“没有可用目录”，点“检查更新”会得到网络失败。**真实的下载与安装要等首个正式目录发布之后才能在设备上验证**；在那之前可验证的只有目录卡片、无目录状态和已安装状态。
- PackageInstaller 全流程、ColorOS 安装确认界面、“安装未知应用”授权往返、取消/返回、进程被杀后的 reconcile、`getPackageArchiveInfo` 在各 Android 版本上能否读到签名：均未在设备上运行。读不到签名时会按失败处理（不安装），如在某版本上出现需要再评估。
- 没有 Compose UI 测试；下载未做断点续传。

**第三刀：后台更新提醒（同分支，本地，未提交）**

- `management/reminder/UpdateReminders.kt`：商店页新增“后台检查插件更新”开关，**默认关闭**。开启时，Android 13 起才申请通知权限（只在用户打开开关时申请，拒绝后开关保持关闭并提示）；关闭时取消任务、撤下通知并清除已提醒记录。
- 调度：`JobScheduler` 周期任务，约 24 小时一次、6 小时浮动窗口，要求有网络，持久化（为此新增 `RECEIVE_BOOT_COMPLETED`）。实际执行时间由 ColorOS 决定，**不承诺实时到达**；没有镜像响应时交给系统按指数退避重试。打开管理页时若任务丢失会补建。未引入 WorkManager 依赖。
- 每次检查：刷新目录（与前台同一个串行化的 `StoreRuntime`），读取本机安装状态，只提醒“可安装、签名一致、本机较旧”的更新（签名不同、本机较新、目录过期都不提醒）。
- 去重（纯函数 `planNotice`）：同一插件的同一版本只响铃一次；已提醒过的更新只静默刷新已显示的通知，用户划掉后不再重发；该插件出现更新的版本时再提醒；更新装好后从通知中去掉，全部没有时撤下通知。没有通知权限时不记“已提醒”，以后获得权限仍能提醒。
- 通知只打开管理页的商店页（`EXTRA_OPEN_STORE`），**不会自动下载或安装**。偏好存在独立的 `provider_store` SharedPreferences，不进入 LyricUiConfig；提醒失败不影响任何歌词功能。
- Manifest 新增 `POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED` 与不导出的 `UpdateCheckJobService`（`BIND_JOB_SERVICE`）。

本地验证：同一组 Gradle 任务通过，1012 tests / 0 fail / 6 skip（新增 `UpdateNoticeTest` 3 项）；R8 dex 3,941,028 字节（比第二刀 +39,568）；合并后的 Manifest 已确认含两项新权限与该服务。**未覆盖：** 任务调度、通知显示、权限申请、点通知跳到商店页都没在设备上运行；ColorOS 对后台任务的限制（例如未加入自启动白名单时可能长期不执行）是 unknown。

**M3 剩余：** 首个正式目录发布后的设备验收（下载、安装确认、授权往返、取消、被杀后恢复、后台提醒）。功能切片已全部在本地实现。

### 14.8 M3 真机测试点：TEST-ONLY 目录（2026-11-14，本地，未提交；用户设备确认通过）

目的：正式目录发布前，在真机上走完一次真实的“下载 → 校验 → 系统安装 → 回读”。不推送、不打 tag、不用正式私钥。

- 分支 Bridge `feat/m3-store-device-test`（基于 `2516ad2`）。
- 只在 Debug 且构建时设置 Gradle 属性 `cllStoreTestCatalog=true`（Windows 下用环境变量 `ORG_GRADLE_PROJECT_cllStoreTestCatalog=true`，因为 `dev.cmd` 会拆开 `=`）时，`BuildConfig.STORE_TEST_TRUST` 为 true，商店额外信任 Providers 仓库里公开的 TEST-ONLY 目录公钥 `test-only-2026`。该公钥只在 `app/src/debug` 源集中；`app/src/release` 的同名对象为空。Release 构建即使带着该属性也固定为 false，已确认 R8 后的 Release dex 中没有该 keyId。`StoreTestTrustTest` 2 项检查 release 源集为空、debug 密钥就是测试夹具密钥且不是正式密钥。
- 测试构建的 revision 下限另存在 `noBackupFilesDir/store-test`，测试目录永远不会挡住正式目录。商店目录卡片顶部会显示红色“测试构建”警告。
- 测试目录（`artifacts/m3-store-test-20261114/test-stable.json`，revision 1，30 天有效，payload SHA-256 `d7cbc44a…`）用 TEST-ONLY 私钥签名，Providers 的 `verify-catalog.mjs` 用测试公钥验证通过、用 `release/catalog-trust.json` 验证被拒（unknown keyId）。只有两个条目，都指向 Bridge 仓库 **v4.5.4 正式 Release 中已存在的资产**（正式证书 `ff38544b…`，本地用 apksigner 核对过）：
  - LX Provider 1.2.1（versionCode 4，145,290 字节，`6b84d229…`）
  - QiShui Provider 1.1.2（versionCode 4，169,277 字节，`9cbc021a…`）
  
  下载会经过 github.com → release-assets 主机的重定向，正好覆盖白名单逐跳检查。
- 候选：`artifacts/m3-store-test-20261114/Bridge-M3-store-test-debug.apk`，SHA-256 `18645c6b…`，Debug 证书 `5a6fd43f…`（与设备上 r4 相同，可直接覆盖安装）。嵌入的 `assets/catalog/stable.json` 与测试目录逐字节相同。构建后已从 `app/build/generated/catalogSnapshot` 删除测试快照，之后的普通构建不会带上它。
- 本地验证：1014 tests / 0 fail / 6 skip，测试版 Debug 构建通过；带属性的 Release R8 构建通过且不含测试密钥。

**设备检查清单（需用户当次授权安装 Bridge；两个 Provider 由用户在商店里自己点安装）：**

1. 商店页：红色“测试构建”警告；目录卡显示“第 1 版（随 Bridge 内置），签名已验证”；LX、QiShui 显示“未安装 · 商店 x.y”和“下载”按钮；其余插件显示“商店目录中暂无这个插件”，已安装的仍显示原来的打开/调试按钮。
2. “检查更新”：正式镜像上还没有目录，预期显示“网络检查失败，继续使用现有目录”，测试目录保持可用。
3. 点 LX“下载”：进度、核对，然后：
   - 若 Bridge 尚未获“安装未知应用”授权：卡片显示“去允许安装”；跳到系统设置允许后返回，应自动继续；不允许则不再弹出。
   - 出现系统（ColorOS）安装确认：取消一次 → 卡片显示“已取消”，再点下载应直接复用已校验文件（不重新下载）。
   - 再次确认安装 → “已安装 1.2.1，并核对了版本与签名”和 LSPosed 提示；卡片变为已安装并出现“调试”按钮。
4. 在安装确认界面按返回或切走后回到商店：应显示“等待你确认”，“打开安装确认”能重新打开，“取消”能结束。
5. （可选）点 QiShui 下载、在安装确认出现时从最近任务划掉 Bridge，再打开商店：应显示“安装过程被中断，可以重试”，不会自动再弹第二个安装。
6. 后台提醒开关：打开时请求通知权限（Android 13+）；拒绝后开关保持关闭。由于测试目录里没有本机已安装的旧版本，这一步不会产生更新通知，只验证开关与权限。
7. 测试结束后，LX/QiShui 插件是否卸载由用户决定（未在 LSPosed 启用时不会影响任何播放器）。

**用户设备确认（2026-11-14，PJZ110 `f05be72a`，候选 `18645c6b…`，agent 经当次授权 `adb install -r`）：第 1–6 项全部通过。** 即：测试构建警告与内置目录显示正确；正式镜像无目录时显示网络失败且测试目录仍可用；LX 的下载、校验、“安装未知应用”授权往返、ColorOS 安装确认中取消后复用已校验文件、确认安装后回读版本与签名均通过；退出确认后可重新打开或取消；进程被划掉后显示中断且未再次发起安装；提醒开关的通知权限申请与拒绝处理正确。

仍未覆盖：后台提醒的实际调度与通知显示（测试目录中没有本机旧版本可提醒）；签名冲突、空间不足、系统拦截等失败分支；正式目录（`store-catalog` 分支、正式密钥签名、jsDelivr/raw 镜像）的端到端流程；其他 ColorOS 型号与 Android 版本。

### 14.9 商店正式名称与 LSPosed 启用状态提醒（2026-11-14，本地，未提交）

分支：Bridge `feat/m3-store-names`（基于 `60679b1`），Providers `feat/m3-module-status`（基于 `358d9aa`）。

**正式名称（用户要求）：** 商店卡片、提示和更新通知改用“<播放器正式名> Provider”，中英文分开（例如 洛雪音乐 Provider / LX Music Provider、光锥音乐 Provider / ConePlayer Provider、汽水音乐 Provider / Soda Music Provider）；播放器名本身是英文的中英一致（Salt Player、Poweramp、Metrolist、Apple Music、Spotify、Readify）。矩阵里的 `displayName` 短名不变（发布资产文件名依赖它）；各插件自身的应用名（桌面/LSPosed 中显示）未改，需要时随下一轮 Provider 发版统一。`StoreNamesTest` 检查 15 项都有不重复的正式名且中英文都以“ Provider”结尾。

**启用状态提醒（用户要求：只作引导提醒，不证明 Hook 已在播放器中运行）：**

- Providers 新增 `provider-module-status` 库（只依赖 libxposed service，不进入 Hook 路径）。它把 `ModuleStatusProvider` 合并进每个模块的 Manifest，authority 为 `<applicationId>.cll.module-status`，`call("status")` 返回协议 1：`connected`（LSPosed 是否已把服务交给本模块 App 进程）、`apiVersion`，以及 `scope`（仅 connected 时返回）。调用方必须与本模块签名相同（`checkSignatures(callingUid, myUid)`），否则抛 SecurityException。模块进程刚被这次调用拉起时，最多等 1.5 秒服务到达；只读，不改配置，不记录调用方或 scope。
  - 12 个用 `settings102` 的专用插件：`ProviderModuleApplication` 把已有的服务回调同时转给 `ModuleStatus`（libxposed 只允许一个 listener）。
  - 通用播放器 Provider、Readify：在各自 Application.onCreate 调用 `ModuleStatus.register()`。
  - 动态封面不是 LSPosed 模块，不适用。
  - **这些插件都需要发新版本**，用户才会在商店里看到状态；旧版本显示“这个版本的插件还不支持”。
- Bridge：对已安装的 LSPosed 插件逐个查询（每次回到管理页都重查，从 LSPosed 管理器返回即可看到变化），卡片显示：已启用 / 未启用（引导去 LSPosed 启用并勾选播放器）/ 已启用但作用域里没有勾选本机已安装的支持播放器（只对专用插件，且本机装了支持的播放器时）/ 插件版本不支持或被系统限制 / 签名与 Bridge 不同 / 无法检测。查不到时从不显示为“未启用”。
- **依据与假设：** 判定依赖“LSPosed 只为已启用模块的 App 进程连接服务”。这一点来自 libxposed 的服务模型，**尚未在设备上用“关闭模块”验证**，见下方清单。ColorOS 的“关联启动”限制可能阻止 Bridge 拉起插件进程，此时显示“被系统限制”。

**本地验证：** Providers `testV5Matrix assembleV5MatrixDebug` 通过（新模块 4 项测试），14 个插件 Debug APK 的合并 Manifest 都含该 provider；Bridge 1019 tests / 0 fail / 6 skip（新增 `ProviderEnablementTest` 3 项、`StoreNamesTest` 2 项），Debug 与 R8 通过。

**设备测试候选：** `artifacts/m3-enablement-20261114/`
- `Bridge-M3-enablement-test-debug.apk`（`77e4145c…`）：带 §14.8 测试目录信任，Debug 证书 `5a6fd43f…`。
- `Salt-Provider-enablement-debug.apk`（`5460d06a…`）：Salt Provider 1.1.2（versionCode 8）Debug 构建，同一 Debug 证书，含状态接口。只有同签名才能查询，所以真机验证用 Debug 版 Salt；若设备上现有 Salt 是正式签名，覆盖安装会被系统拒绝，**不得为此卸载或清数据**。

**设备检查清单：**
1. 商店卡片名称为正式中文名（切到英文后为英文名）。
2. Salt（Debug 新版）在 LSPosed 中**已启用**并勾选 Salt Player：卡片显示“已在 LSPosed 中启用”。
3. 在 LSPosed 中**取消启用** Salt 模块后返回管理页：显示“未在 LSPosed 中启用…”。（验证“未启用时服务不连接”这一假设。注意：Salt 插件进程若仍在运行，可能还持有之前的连接；若显示仍为已启用，请在系统设置里“强行停止”Salt Provider 后再回到管理页，并告诉我是哪种情况。）
4. 重新启用但取消勾选 Salt Player（本机已装 Salt Player 时）：显示“作用域里没有勾选本机已安装的播放器”。之后恢复原设置。
5. 商店装的 LX 1.2.1（v4.5.4 正式版，无接口）：显示“这个版本的插件还不支持…”。

**§14.9 设备结果（2026-11-14，PJZ110，用户实测）：**
- 冷启动时卡片一直显示“无法检测”：ColorOS 不允许 Bridge 拉起插件进程（关联启动限制）。先打开插件调试页再返回后，显示“已启用”。
- 在 LSPosed 中禁用 Salt Provider 后，只要插件进程还在，就仍显示“已启用”；**强行停止**插件后才显示“未启用”。说明 LSPosed 只在插件进程启动时连接服务，不会断开正在运行的进程。
- 结论：插件自报只能作为没有 Root 时的退路。用户不满意这个效果，决定改用 Root 读取（见 §14.10）。

### 14.10 Root 只读 LSPosed 状态、首页 Root 状态与“去 LSPosed”跳转（2026-11-14，本地，未提交）

**用户决定：**
- Bridge 申请 Root，只用来**读取** LSPosed 中各插件的启用状态与作用域；首页显示 Root 状态；以后做进引导教程（M5）。
- 不自动启用插件、不写 LSPosed 配置。曾讨论过“安装后用 Root 默认打开 LSP 开关”，因有绕过 LSPosed 确认、改坏配置、依赖内部格式的风险，已放弃。
- 改为提供跳转按钮，直接打开 LSPosed 管理器中对应模块的详情页，由用户自己一步打开开关。

**新边界（取代“Bridge 不使用 Root”）：**
- Root 只在普通 App 进程（`management/root`）中使用，且只做两件事：用 `su -c cat` 把 LSPosed 的配置文件复制进 Bridge 私有缓存读取，以及启动 LSPosed 管理器。
- 不运行 LSPosed 自带的命令行工具，不打开原文件写入，不改任何启用状态或作用域。
- 架构守卫新增 `ProcessBuilder("su"`，它只能出现在 management 包内。

**r3 设备结果与改道（2026-11-14）：**
- r3 按 JingMatrix/Vector 源码去找 `cli`，设备上显示“没有找到 LSPosed 命令行工具”。
- 经用户授权在设备上只读检查：装的是 lsposed.zip 一支的 **LSPosed v2.2.1 (7941)**，模块目录 `zygisk_lsposed`，作者 “LSPosed Developers”，自带的工具叫 `lspctl`。它的帮助里写明“不是稳定的控制协议……不要基于它构建 App 或无人值守的集成”，所以**用户决定不用自带工具，改为读取本地配置文件**。
- 用户授权的检查中，把 `modules_config.db`（含 WAL）复制到电脑上，只看了表结构和我们插件的行，看完即删除。该版本的表结构：
  - `modules(module_pkg_name, apk_path)`
  - `modules_state(module_pkg_name, user_id, enabled, scope_request_blocked)`
  - `scope(module_pkg_name, app_pkg_name, user_id)`
  - 数据库为 WAL 模式，设备上没有 `sqlite3`。
- 另外拉取了 `manager.apk`、`daemon.apk`，但字符串已混淆，看不出启动方式；设备上的临时副本已删除。
- 跳转写法在设备上执行成功（系统接受，没有报错）：`am start -n com.android.shell/.BugreportWarningActivity -c org.lsposed.manager.LAUNCH_MANAGER -d module://<pkg>:<user>`。打开的是主页还是模块页，待用户确认。

**实现（r4）：**
- `RootAccess`：只有用户点“授权 Root”时才执行 `su -c id -u`，可能触发 Magisk/KernelSU/APatch 弹窗。之后每次回到管理页静默复查已授权状态，不主动弹窗。状态：未请求 / 已授权 / 被拒绝或超时 / 未检测到 su。stderr 一律丢弃。
- `LsposedConfig`：
  - 先用 `su -c cat` 复制 `/data/adb/lspd/config/modules_config.db-wal`，再复制主文件（单个文件最大 32 MiB），都放进 `cacheDir/lspd-read`。
  - 用 Android SQLite 打开这份私有副本（以读写方式打开，好让 SQLite 回放 WAL）；按表结构识别两种布局：2.2.x 的 `modules_state` 和 1.x/Vector 的 `modules.mid/enabled`。
  - 只用参数化查询读取被问到的我们插件、当前 Android 用户的行；读完立即删除整个副本目录。
  - 在 LSPosed 中登记过但没有状态行时，视为“未启用”；没登记时视为“未知”。布局不认识时不猜测。
- 商店：已授权 Root 且读到配置时，以 LSPosed 配置为准，禁用或启用后回到管理页立即反映；否则退回 §14.9 的插件自报。“未启用”“作用域没勾选”“无法检测”的卡片有“去 LSPosed 启用 / 去 LSPosed 勾选播放器”按钮。跳转时先尝试独立安装的管理器 App（不需要 root），否则用上面验证过的 root 写法；打不开时提示手动打开 LSPosed 并找到该模块。
- 首页状态卡有“Root 权限”一行和“授权 Root”按钮；读不到配置时说明原因，并已改用插件自报。

**M5 引导教程要求（记录，未实现）：** 引导中加入一步“授权 Root（只读查看 LSPosed 状态）”，说明用途和“不会修改任何设置”。拒绝也不影响其他步骤，之后可从首页再授权。插件安装完成后，引导到“去 LSPosed 启用”。

**本地验证：** Bridge 1023 tests / 0 fail / 6 skip（`LsposedConfigTest` 4 项：识别两种布局并拒绝未知布局、状态行到启用状态的映射、`id -u` 判定），Debug 与 R8 通过。Android SQLite 读取副本这一步只能在设备上验证。

**设备候选：** `artifacts/m3-enablement-20261114/Bridge-M3-root-db-test-debug-r4.apk`（`bb0de514…`，含 §14.8 测试目录信任）。

**设备检查清单：**
1. 首页：r3 已授权过 Root，应直接显示“已授权”。若显示“没能读取 LSPosed 配置”，告诉我。
2. 商店：Salt 卡片显示“已在 LSPosed 中启用”。在 LSPosed 中禁用 Salt Provider 后直接回到 Bridge，**不强行停止**，应立刻显示“未在 LSPosed 中启用”。
3. 点“去 LSPosed 启用”：应打开 LSPosed 管理器并停在 Salt Provider 模块页（或其作用域页）；打开开关后返回，应显示“已启用”。
4. 取消勾选 Salt Player：显示“作用域里没有勾选…”，按钮为“去 LSPosed 勾选播放器”。之后恢复原设置。
5. LX（正式签名、v4.5.4 无状态接口）：有 Root 时也应正确显示启用与否，说明不再依赖插件版本。

**r4 设备结果（2026-11-14，用户实测）：**
- Root 显示已授权。从 LSPosed 返回后，LX 正确显示启用状态，说明 Root 读取配置可行。
- 但一次从 LSPosed 返回时，静默复查 Root 失败，商店退回插件自报，显示“无法检测”；重新点授权后正常。具体原因（超时还是拒绝）没有日志，unknown。
- 跳转打开的是 LSPosed **主页**，没有定位到模块页；这一支 2.2.1 对 `module://` 链接的处理方式未知。

**r5（本地）：** 静默复查最多试 3 次（每次 10 秒，间隔 1 秒）；如果本页面之前已授权、且失败全是超时，不降级。刷新任务串行，避免回到页面与操作完成时同时发起两个 su。跳转成功后提示“在‘模块’里找到 X，打开开关后返回”。1023 tests / 0 fail / 6 skip，R8 通过；候选 `Bridge-M3-root-db-test-debug-r5.apk`（`8290aa2a…`，含新任务启动管理器）。模块页定位需要在设备上再做几次 `am start` 试验。

**跳转结论（2026-11-14，用户在 PJZ110 上实测）：**
- 设备上另外装有独立的管理器 App `org.lsposed.manager`。它的 `MainActivity` 对外开放，但 Manifest 里没有声明任何可接收的链接。
- 用户执行 `am start -n org.lsposed.manager/.ui.activity.MainActivity -d module://<pkg>:0 -f 0x10008000`（在新任务里启动）后，仍然停在**主页**。所以 2.2.1 不支持从外部定位到某个模块页。
- r4 用的是桌面启动的 Intent，管理器已在后台时只会被调到前台。r5 改为在新任务里启动它（仍附带 `module://` 链接，以后的版本如果支持就会生效）。打开后一律提示“在‘模块’里找到 X，打开开关后返回”。没有独立管理器时，用 root 的寄生方式打开，结果同样是主页。
- **r5 设备结果：** 用户从 LSPosed 模块页右下角 ▷ 进入 Bridge 后点跳转，看到的仍是之前那一页（Bridge 的模块页），不是主页。原因：管理器进程还活着，会恢复最后显示的页面。
- **r6（用户提议）：** 已授权 Root 时，先 `su -c am force-stop org.lsposed.manager`，再用 `NEW_TASK | MULTIPLE_TASK` 在独立任务里打开。这只停掉管理器的界面进程，框架守护进程独立运行、不受影响；Bridge 页面留在原来的任务里，按返回仍能回到 Bridge。没有 Root 时保持 r5 的做法。候选 `Bridge-M3-root-db-test-debug-r6.apk`（`08a36d8d…`），1023 tests / 0 fail / 6 skip，R8 通过。
- **r6 设备结果（用户实测）：** 第 1–3 项全部通过：从 ▷ 进入后跳转打开的是 LSPosed 主页；打开开关并返回后，卡片立刻更新；Root 一直显示“已授权”。

### 14.11 商店列表显示各插件图标（2026-11-14，本地，未提交）

- 用户要求在商店列表中显示各插件的图标。图标沿用插件自身的启动图标，也就是目标播放器的官方图标（AGENTS“模块图标使用目标播放器官方 launcher 资源”）。
- 做法：从 Providers 仓库各模块的 `res` 复制出启动图标，改名为 `store_icon_<id>`（及 `_fg` / `_bg` / `_bmp`），放进 Bridge 的 `res/drawable*` 和 `values/store_icons.xml`。只复制前景和背景，不复制单色层。Cone 和 Poweramp 的位图只复制 xxxhdpi 一档；通用 Provider 用它清单里的 `provider_launcher` 位图。每个 XML 都注明了来源模块。
- 不联网取图，也不读已安装插件的图标，所以没安装的插件同样有图标。代价是插件以后改图标时，Bridge 要跟着重新复制一次。
- `DirectoryEntry` 和 `StoreItem` 新增了 `iconRes`；卡片标题左边显示 40dp 的图标。自适应图标通过平台 Drawable 绘制，套用设备的图标形状。Readify 插件本身用的是系统播放图标，所以商店里显示一个通用的播放图标。
- 本地验证：Bridge 1024 tests / 0 fail / 6 skip（`StoreNamesTest` 新增一项：只有 Readify 没有专属图标，其余各不相同），Debug 与 R8 通过；已确认 APK 内含 32 个 `store_icon` 资源。
- 设备候选：`artifacts/m3-enablement-20261114/Bridge-M3-store-icons-test-debug-r7.apk`（`61ef122d…`，含 §14.8 测试目录信任）。已经当次授权 adb 安装，**用户设备确认没有问题**（图标一致、形状、清晰度、深色模式）。

### 14.12 首个正式商店发布准备：洛雪音乐 Provider 1.2.2（2026-11-14，本地，未提交、未发布）

**选择：** 用户同意以 LX 首发。其余 13 个 Provider 跟随各自下一次版本更新发布；有 Root 时启用状态不依赖插件版本。

**Providers 分支 `release/lx-store-1.2.2` = `270c540`（已提交，未推送）：**
- 从 `d534e5c` 开出，cherry-pick 模块状态接口为 `be58f3b`。所以相对 `main` 包含 `215b0c9`（M1 诊断与启动失败放行）、`d534e5c`（目录工具与发布 workflow）、`be58f3b`。**不含 `358d9aa`**：它把动态封面改为商店分发，只能在 schema 2 的 Bridge 5.0 发布前进入 `main`，否则 4.x 套件发布校验会失败。
- `270c540`：LX 升到 1.2.2 (5)（`player-lx/build.gradle.kts` 与矩阵同步），新增 `release/notes/player-lx/1.2.2.md`（中英双语），更新 `scripts/catalog/README.zh-CN.md` 的 GitHub 就绪清单。
- `resolve-release-tag` 对 `player-lx-v1.2.2` 解析为 `ColorOS-Live-Lyrics-Provider-LX-v1.2.2.apk`、`:player-lx:assembleRelease`。

**本地验证：** `testV5Matrix assembleV5MatrixDebug :player-lx:minifyReleaseWithR8` 通过；目录工具测试 17/17；`validate-v5-release-contract.ps1` 通过。正式签名 APK 和目录生成只能在 workflow 中做（本地不碰正式签名与目录私钥）。Bridge 侧仓库名、分支、两把公钥与 Providers 一致（静态确认）。

**GitHub 只读核对发现的阻塞（需用户在 GitHub 设置）：**
1. 仓库级没有 `SIGNING_KEY` / `KEY_STORE_PASSWORD` / `ALIAS` / `KEY_PASSWORD`，构建任务会失败。
2. Environment `provider-catalog` 没有必需审核人（D2 要求审批）。
3. `provider-catalog` 启用了自定义部署规则但一条也没有，tag 触发的晋级会被拒绝；需允许 tag `*-v*`。

**发布步骤（每步需用户当次授权）：** 推送分支并开 PR 合入 `main` → 在合入后的 `main` 提交上打 `player-lx-v1.2.2` 并推送 → workflow 构建、建 Release、复核下载 → 审批晋级，生成 `store-catalog` 的 revision 1。

**设备验收（发布后）：** 用不含测试目录的 Bridge 构建刷新正式目录，下载安装 LX 1.2.2，查看启用状态；在洛雪中播放、切歌确认歌词正常。M1 的启动改动只在 Salt 上验证过，LX 与 Release/R8 组合尚无设备证据。

**发布结果（2026-11-14，用户授权推送、合并、打 tag，并在 GitHub 上审批晋级）：**
- PR #10 合入 `main`（`e277e2a`），打 `player-lx-v1.2.2`。用户按提示在 GitHub 补了 4 个签名 secret、Environment 审核人与 tag 规则。
- 第 1 次运行：构建与正式签名通过，“Check APK identity”报 `no signer certificate in apksigner output`。PR #11（`d9b240f`）放宽解析并在失败时列出验证行；第 2 次运行显示原因：运行器的 **build-tools 37.0.0 apksigner 不再打印 `Signer #N certificate SHA-256 digest` 行**。PR #12（`ba6c1cb`）同时读 stdout/stderr，缺证书行时依次退回更旧的 apksigner，并在 workflow 中安装 build-tools 36.1.0。每次修复前确认该 tag 尚无 Release，再移动 tag。
- 第 3 次运行在“Matrix tests”因依赖解析失败（Maven Central 上存在的 `jsr305:3.0.2` 等报 Could not find），判断为临时问题，同一提交重跑失败任务后通过。
- **已发布：** Release `player-lx-v1.2.2`，附件 `ColorOS-Live-Lyrics-Provider-LX-v1.2.2.apk`（160090 字节，SHA-256 `1219a197…501f5`）与 `.sha256`；本地复核包名、1.2.2 (5)、minSdk 27、唯一签名者 `ff38544b…`。
- **目录：** `store-catalog` 分支 `35a1909`，revision 1，`cll-catalog-2026a` 签名，1 个条目（LX，`sourceCommit` `ba6c1cb`），`verify-catalog.mjs` 验签通过；git、raw.githubusercontent.com、jsDelivr 三处字节相同。
- **设备候选：** `artifacts/m3-enablement-20261114/Bridge-M3-prod-catalog-debug-r8.apk`（`0f0d9caf…`，基于 `bf6837c`；`STORE_TEST_TRUST=false`、不内置目录，只信任正式密钥）。已经当次授权 adb 安装，**用户设备确认全部通过**（PJZ110）：刷新正式目录、LX 显示 1.2.1 → 1.2.2 可更新、下载校验与 ColorOS 安装、启用状态（Root 路径）、在洛雪中播放与切歌歌词正常。M3 的正式目录端到端链路由此闭合；LX 也是 M1 启动改动在 Salt 之外、Release/R8 下的第一份设备证据。

### 14.13 M4 第一刀：插件自己申请作用域（管理协议 v1，2026-11-15，本地，未提交）

**范围：** 共用 `provider-settings-api102` 的 12 个专用插件（Salt、Cone、酷我、洛雪、Poweramp、Metrolist、酷狗、QQ、网易云、Apple、Spotify、汽水）。Readify 用自己的 Application，留到下一刀；通用 Provider 与动态封面不进入此流程（§7.0）。`staticScope` 保持 `true`（§14.0 设备证据：声明内宿主可发原生请求），矩阵不改。

**Providers `feat/m4-scope-request`（基于 `main` 的 `ba6c1cb`）：**
- `scope/ScopeRequestProtocol`：协议 v1 的 action 与 extra 名；允许的宿主只取本 APK 内编译的 `META-INF/xposed/scope.list`（构建约定已校验它等于矩阵）；请求 id 8–64 位，宿主最多 16 个，不得重复，必须全部在列表内，否则整体判无效（不截断）；`android`、`system`、SystemUI 永不允许。
- `scope/ScopeRequestController`：M0 探针控制器的生产版。先读 scope，只为缺少的宿主调 `requestScope`，结果一律以回调后的重新读取为准；保留已有 scope，从不删除。结果：已包含 / 已批准 / 部分批准 / 未批准（拒绝或不再询问，API 102 无法区分）/ 待定（120 秒无回应）/ 服务未连接（10 秒）/ 失败 / 请求无效。“已批准但回读没有”判失败。迟到回调忽略。
- `scope/ScopeRequestActivity`：导出的入口（随库合并进 12 个插件的清单）。只接受 `startActivityForResult` 且调用方是本插件或签名一致的应用（Bridge）；页面只说明“请在 LSPosed 通知中选择允许”，不自绘允许/拒绝。重建或按返回时回复“待定”。
- 插件自己的调试页加了“申请播放器作用域（LSPosed 原生请求）”按钮，不装 Bridge 也能用。

**Bridge `feat/m4-scope-request`（基于 `32f9b65`）：**
- `ScopeRequestContract` 镜像协议字符串；`parseScopeReply` 只信任同一请求 id、协议 v1、且只含所问宿主的回复，否则视为未知。
- 商店卡片在“已启用但作用域没勾选本机播放器”时多一个“申请播放器作用域”按钮，只传本机已安装的受支持播放器；插件旧版本没有入口时提示更新或去 LSPosed 勾选。回复只用来提示，卡片随后重新读取 LSPosed 状态。

**本地验证：** Providers `testV5Matrix assembleV5MatrixDebug :player-salt:minifyReleaseWithR8` 通过，`ScopeRequestControllerTest` 7/7；Salt Debug APK 内含 `ScopeRequestActivity`（exported）与 `scope.list`（`com.salt.music`）。Bridge 1028 tests / 0 fail / 6 skip（`ScopeRequestContractTest` 4 项，含跨仓库常量与状态枚举比对），Debug 与 R8 通过。

**设备候选（`artifacts/m4-scope-20261115/`，均为 debug 签名，与设备上现有 Salt、Bridge 同签名）：** `Bridge-M4-scope-debug-r1.apk`（`8f91d0e7…`），`Salt-M4-scope-debug-r1.apk`（`147288c6…`，1.1.2 / 8）。待授权安装后验证：在 LSPosed 中取消 Salt Provider 对 Salt Player 的勾选 → 商店卡片出现“申请播放器作用域” → 点按出现 LSPosed 原生“作用域请求”通知 → 允许后回到 Bridge 显示已包含且卡片恢复“已启用” → 再取消勾选并选“拒绝”显示未获批准 → 插件调试页按钮在已勾选时显示“已包含”。正式签名插件与 debug Bridge 签名不同，会被插件拒绝，这是预期。

**r1 设备结果（用户实测）：** 第 1、2、4 项通过：卡片出现按钮，点按后出现 LSPosed 原生“作用域请求”通知，允许后卡片恢复“已启用”；插件调试页在已勾选时提示已包含。第 3 项（拒绝）：插件页自动关闭，卡片仍为作用域未勾选，但**没有出现任何提示**。原因在 Bridge：提示由 `LaunchedEffect(message)` 弹出，代码先清空 `message` 再弹，清空改变了键，提示随即被取消（从插件页返回时界面在重组，所以立刻消失；第 2 项的提示很可能也没显示，只是卡片状态对了）。r2 改为提示消失后再清空。1028 tests / 0 fail；候选 `Bridge-M4-scope-debug-r2.apk`（`effc7d47…`），Salt 不变。 **r2 设备结果：用户确认允许与拒绝两条路径的提示与卡片状态均正确。** 第一刀（12 个共用设置库的插件 + Bridge 发起）设备验证完成，仅 Salt 实测。

### 14.14 M4 第二刀：Readify 接入，协议代码独立成库（2026-11-15，本地，未提交）

- 作用域请求代码从 `provider-settings-api102` 移到新库 **`provider-scope-request`**（包 `io.github.andrealtb.coloroslyrics.provider.scoperequest`），并入 `testV5Matrix`。服务改从 `ModuleStatus.awaitService` 取得：共用 Application 已把服务转给它，Readify 的 `ReadifyApplication` 也已调用 `ModuleStatus.register()`，所以两边用同一份实现，Readify 不依赖共用设置库，保持独立。
- `provider-settings-api102` 与 `player-readify` 依赖该库；Readify 的说明页加了“Request player scope (LSPosed prompt)”按钮。
- Bridge：`offersScopeRequest` 不再排除 Readify（全部专用 Provider）；跨仓库常量测试改读新路径。
- **本地验证：** Providers `testV5Matrix assembleV5MatrixDebug :player-salt:minifyReleaseWithR8` 通过，`ScopeRequestControllerTest` 7/7；13 个专用模块的 Debug APK 都含 `ScopeRequestActivity`，通用 Provider 与动态封面不含。Bridge 测试与 Debug 通过，`ScopeRequestContractTest` 4 项实际运行。
- **设备覆盖缺口：** 设备上的 Readify Provider 是正式签名，debug 包无法覆盖安装（按边界不卸载），且设备未安装 Readify（`com.readin.app`），所以 **Readify 路径只有本地证据**。Salt 因服务来源改动需回归：候选 `Salt-M4-scope-debug-r2.apk`（`0cf39501…`）、`Bridge-M4-scope-debug-r3.apk`（`cbc8f16c…`）。
- **r3 设备结果（用户实测）：** 允许后常见其他插件卡片变为“无法检测启用状态：这个版本的插件还不支持”，首页 Root 行显示“已授权，但没能读取 LSPosed 配置”；点“重新检查”无效，退出 Bridge 重进才恢复。原因（静态分析，无日志）：申请结果回调里调用了一次 `refreshEnablement()`，随后 `onResume` 又调用一次；协程取消停不住阻塞中的读取，两次 `LsposedConfig.read` 同时清空、复制同一个 `cacheDir/lspd-read`，读取失败。“重进才好”推测是 SQLite 在本进程保留了被删文件的 WAL 共享内存状态（未证实）。r4：读取加进程内锁、每次用独立子目录；回调不再自行刷新，交给 `onResume`。候选 `Bridge-M4-scope-debug-r4.apk`（`363ae56b…`），Salt 不变。 **r4 设备结果：用户确认多次允许后 Root 读取与其他卡片均保持正常，拒绝与插件页按钮回归通过。**

### 14.15 M4 收口：接线守卫与现状（2026-11-15，本地，未提交）

- 新增 `ScopeRequestWiringTest`（`provider-scope-request`，并入 `testV5Matrix`）：从矩阵读出 14 个 Provider，凡 `processPolicy.kind` 不是 `system-server-bound-list` 的专用模块必须依赖 `provider-settings-api102` 或 `provider-scope-request`（即带入口），通用 Provider 必须都不依赖。新增专用模块忘接、通用模块误接都会让矩阵测试失败。本地 8/8 通过。
- **未做、且建议不做：** “安装后自动接续申请作用域”（§7.2 第 2–3 步）。刚安装的插件尚未在 LSPosed 中启用，服务不会连上，立即申请只会得到“服务未连接”；启用必须由用户在 LSPosed 中完成（§14.10 决定不自动启用）。现有路径已覆盖：安装 → 卡片提示“去 LSPosed 启用” → 启用后若作用域缺失，卡片出现“申请播放器作用域”。M5 引导中按这个顺序串起来即可。
- **矩阵元数据：** §7.4 设想的 `scopePolicy` / `managementProtocol` 字段未加入矩阵。接入与否已由上面的守卫按 `processPolicy.kind` 推出，`staticScope` 维持矩阵全局 `true`；如以后某个专用模块需要不同策略，再加字段。
- **M4 现状：** 13 个专用 Provider 本地全部带入口；设备只验证了 Salt（允许、拒绝、插件页按钮、Root 读取并发修复）。正式生效需要各插件发新版；在此之前，已发布的旧版插件在商店里会提示“请更新或去 LSPosed 勾选”。M4 门禁“每个模块均以自己的服务授权；无跨模块代设；拒绝/部分批准/服务死亡可恢复”：机制满足；部分批准只有单元测试证据（设备上只有单宿主的 Salt）。

### 14.16 M5 第一刀：设置向导（G1–G6）接真实状态（2026-11-15，本地，未提交）

**分支：** Bridge `feat/m5-setup`（基于 `e139c74`）。技能：已加载 material-3（只用 Compose 分支；固定浅色不变）。

**做法：**
- `onboarding/SetupModel`（纯 Kotlin）：六步 BRIDGE、ROOT（可选）、PLAYERS、INSTALL、ENABLE、PLAYBACK（可选）。每一步的状态都由当前事实**推导**：模块回应与 SystemUI 构建、Root 状态、已安装的播放器与插件、商店卡片上的启用状态。状态分已完成 / 需要处理 / 正在检查 / 等待前面的步骤 / 已跳过 / 本机不需要。从 LSPosed、安装器或插件返回不会自动打勾，只有重新读取才会变化。
- 只持久化用户自己的选择：所选播放器、跳过的可选步骤、“我已看到歌词”的时间（`onboarding/SetupStore`，独立 SharedPreferences `management_setup`，含 `flowRevision`=1；不进 LyricUiConfig）。
- 4.x 升级用户：没有保存过选择时，把“已装插件所对应、且本机已装的播放器”视为已选，不从第一步重来（§5.2）。已存的选择若播放器已卸载则忽略。
- 第 6 步明确是“用户确认”，文案说明 Bridge 不据此判定链路生效（§5.2 引导完成度与运行健康分开）。
- 页面 `onboarding/SetupScreen`：CenterAlignedTopAppBar + 返回（含系统返回）、总进度、每步编号/勾选徽标 + 状态文字（不只靠颜色）、说明、FilterChip 选择播放器、动作按钮（打开 LSPosed、重新检查、授权 Root、去商店处理、我已看到歌词），可选步骤可“跳过/撤销跳过”。页面只把动作交给 Activity，自身不发起外部操作；重组不会重复执行。
- 首页：去掉“样例数据”横幅和样例的“功能未生效”卡，“还有 N 步设置未完成”改为真实计数，点“继续设置”打开向导。`SampleData` 与两条样例字符串删除。

**本地验证：** Bridge 1033 tests / 0 fail / 6 skip（`SetupModelTest` 5 项：新机按序、选择后安装与启用状态、4.x 升级不重来、Bridge/Root/可选步骤、已卸载播放器的旧选择），Debug 与 R8 通过。

**MD3 自查（源码层面，未做设备审查）：** 颜色全部取 `MaterialTheme.colorScheme` 角色；字级取 typography 角色；间距用 `Spacing` tokens；状态同时有文字与图标；按钮为官方 Button/OutlinedButton/TextButton，触摸目标为组件默认 48dp；宽屏限制 840dp。待设备检查：大字体、TalkBack 朗读顺序、横屏。

**设备候选：** `artifacts/m5-setup-20261115/Bridge-M5-setup-debug-r1.apk`（`8b30de29…`，debug，无测试目录）。

**r1 设备结果（用户实测）：** 1–6 步状态与跳过/撤销/确认均正常。问题：① 一个播放器只显示“音乐”，是荣耀版网易云 `com.hihonor.cloudmusic`；两个“光锥音乐”分别是普通版与 `.gp` 版，仅凭应用名分不清。② “确认选择”没有任何反馈。③ 全部完成后首页没有入口再进入向导；点“重新检查”时首页会闪出“还有 1 步未完成”（检查中的步骤被计入）。r2：选项第二行显示包名；确认后提示“已保存：N 个播放器”，选择未变时按钮不可点；未完成计数只算“需要处理”；全部完成时首页显示“设置向导的步骤都已处理”卡片和“查看设置向导”。`SetupModelTest` 6/6，候选 `Bridge-M5-setup-debug-r2.apk`（`1391f029…`）。

**r2 设备结果：** 用户确认功能全部通过。反馈“首页有点空”，给出参考截图（卡片化、信息化、悬浮 dock）。

**r3 首页仪表盘与悬浮 dock：**
- `HomeDashboard`：大标题 + 副标题；“歌词链路”主卡（状态徽标：运行中 / 检查中 / 旧版本 / 无回应，标题：已就绪 / 需要重启 SystemUI / Bridge 未运行，下方 SystemUI 构建与设置推送两行，右侧 72dp 圆形“重新检查”按钮）；有未完成步骤时显示琥珀色“继续设置”卡（进度条）；2×2 信息块：插件（已装数，需处理数或已启用数）、更新（可用数）、Root（状态，未授权时点按授权）、设置向导（x/6）。每块点按进入对应位置。Root 已授权但读不到配置时追加说明卡。所有数字都来自实时读取。
- `FloatingDock`：圆角胶囊悬浮在内容上方，四个顶级入口（图标 + 文字），选中项用 secondaryContainer 胶囊；宽屏（≥600dp）仍用 NavigationRail。各页底部留出 dock 空间，提示条显示在 dock 之上。
- 旧 `HomeContent` / `ActionCard` / `HomeState` 删除；“设置全部完成后的入口”改由“设置向导”信息块常驻提供。
- 只用官方 Material 3 组件（Card、FilledIconButton、Surface、LinearProgressIndicator），没有引入实验性 floating toolbar API；颜色、字级、形状取主题角色（卡片 `shapes.extraLarge`）。
- 本地：Bridge 1036 tests / 0 fail / 6 skip（新增 `HomeDashboardTest` 2 项），Debug 与 R8 通过。候选 `artifacts/m5-setup-20261115/Bridge-M5-home-debug-r3.apk`（`70c0eaad…`）。外观待用户设备确认。
- **r3 设备反馈：** dock 不是真正悬浮：各页底部整体留出了 dock 与导航条的空间，内容滚不到 dock 下面，露出一条背景；dock 内按钮只占上半部分，下方留白过多。**r4：** Scaffold 不再吃系统栏 inset，内容铺到屏幕底部、从 dock 下面滚过，只在列表末尾加滚动留白（dock 高度 + 导航条）；顶部单独留状态栏；dock 用 `navigationBarsPadding` 定位；按钮填满 dock 高度并居中，dock 内高 56dp、内边距 6dp。候选 `Bridge-M5-home-debug-r4.apk`（`ac677e22…`）。 **r4 设备结果：用户确认悬浮 dock、内容滚动与居中均通过。**

### 14.17 M5 第二刀：“状态”页（2026-11-15，本地，未提交）

**分支：** Bridge `feat/m5-status`（基于 `6b9be43`）。

**数据来源（不新增通道，不改 SystemUI 侧）：** SystemUI 对 `ACTION_REQUEST_MODULE_STATUS` 的回复本来就包含 C-02 的能力行（`CompatibilityStatus.putStatus`，schema 1），此前 App 只读了构建号与配置修订号。`ModuleStatusQuery.Listener` 增加带完整回复 Bundle 的默认方法（旧调用方不受影响），管理页把能力行解析为 `status/CapabilityRow`。行内时间是 SystemUI 的 `elapsedRealtime`，与 App 同一系统时钟，用来显示“N 秒前记录”。

**页面（`status/StatusScreen`）：**
- 运行的版本：已安装的 Bridge 构建号、SystemUI 正在运行的构建号（检查中/无回应如实显示），旧版本时提示重启；“重新检查”。
- 功能状态：7 项（歌词自绘、封面取色、字符上浮、逐行进度、译文进度、翻译按钮、动态封面），每项为图标 + 文字结论（已在设置中关闭 / 最近 60 秒没有记录 / 等待中 / 已退回系统原生效果 / 未生效 / 已执行到某阶段 / 未知）、原因、记录时间。顶部写明这是 SystemUI 内部的决策记录，不等于画面效果；SystemUI 未回复或旧构建不提供时明确说明，不显示空白。
- 插件：已安装插件及其启用状态（与商店同源），点按进入商店。
- 诊断：说明短时诊断（60 秒、默认关闭、预览后导出脱敏摘要）的用途，按钮打开现有 `BridgeDebugSettingsActivity`，不在新页重复实现。
- 只读：本页不修改任何设置。

**本地验证：** Bridge 1039 tests / 0 fail / 6 skip（`CapabilityModelTest` 3 项：结论映射与记录时长、未知功能与时钟倒挂、与 SystemUI 侧 Feature/Outcome/Reason 枚举一一对应且每个原因都有文案），Debug 与 R8 通过。候选 `artifacts/m5-status-20261115/Bridge-M5-status-debug-r1.apk`（`a933c026…`）。设备上的实际行内容待验证。

**r1 设备反馈：** “逐行进度”与“动态封面”时而“已执行到”、时而“等待中”，与实际效果不一致（用户在逐字歌词下看到“等待中”）。原因：SystemUI 对同一功能按多个对象分别记录（每个歌词行、卡片/沉浸两个封面界面和播放器生命周期），快照的结论取“最近一次记录的那个对象”，App 原样显示，所以随最近记录者跳变。逐行进度只用于逐行时间轴的行，逐字歌词的行记作“等待 / 未观测到目标”，实为不适用。**r2（只改 App 侧投影，SystemUI 不变）：** 用回复中已有的 `outcomeSummary`（60 秒内存活对象的结果计数），只要有对象执行到某阶段就显示“已执行到”（取最远阶段），并写“N 处已执行，M 处未执行”；原因只在结论来自最近记录者时显示；逐行进度只剩等待时显示“当前歌词不是逐行时间轴，不使用此项”，名称改为“逐行歌词进度”。`CapabilityModelTest` 6/6，候选 `Bridge-M5-status-debug-r2.apk`（`694a241b…`）。

**r2 设备结果：** 用户确认逐行进度与动态封面显示正常；提出增加“逐字歌词进度”。**r3（改动 SystemUI 侧，仅诊断记录）：** `CompatibilityStatus.Feature` 末尾追加 `WORD_PROGRESS`（不改变已有序号），新增 `wordProgress(owner, drawn, aod)`；`LockscreenLyricsModule` 在两处已有的“当前行是否绘制进度”决定之后调用 `recordWordProgress`：只记录当前行、逐字时间轴、且不是“单字极短整行高亮”的行；记录走既有的无分配写路径，不改变任何绘制判断。结论：已准备（会绘制逐字填充）或等待（息屏低帧率）。App 侧列在“逐行歌词进度”之前，名称“逐字歌词进度”；镜像测试改为按名称集合比对。本地 1043 tests / 0 fail / 6 skip（`CompatibilityStatusTest` 新增 1 项、`CapabilityModelTest` 覆盖新项），Debug 与 R8 通过。**需要重启 SystemUI 后才会出现该行**（旧注入构建不发送）。候选 `Bridge-M5-status-debug-r3.apk`（`993f142f…`）。 **r3 设备结果：用户重启 SystemUI 后确认全部通过（逐字歌词进度显示正确，锁屏逐字效果与以前一致）。**

### 14.18 M5 第三刀：“外观”页（2026-11-15，已提交 `a8b177f`，未推送）

**分支：** Bridge `feat/m5-appearance`（基于 `0d883d1`，即 §14.17 的提交）。

**做法（U-04：旧页保留，配置单一真值）：** 外观标签从占位卡片改为**只读摘要**，不在 Compose 里编辑任何外观配置。修改仍在原 `LyricUiSettingsActivity` 中进行，预览、草稿、保存/放弃、返回确认全部沿用旧语义；所以切换底部标签既不会悄悄提交，也不会丢掉修改。
- 新增 `AppearanceSnapshot`（Java，根包）：`LyricUiConfig`、`LyricUiPreset`、`ArtworkSettingsRepository` 都是包内可见，由它读出**已保存**的配置（不是打开中的草稿）并转成公开的纯值。预设用旧页同一个 `LyricUiPreset.detect`，动态封面状态与旧页“动态封面”行的判断一致（未开启 / 已开启 · 插件名 / 需要检查插件）。
- 新增 `management/appearance/AppearanceModel`（把快照投影为卡片行，纯函数）与 `AppearanceScreen`。
- 页面：当前风格预设及说明 + “打开外观设置”；颜色与光晕（歌词颜色色块、光晕颜色或跟随封面、模糊、缩放）；排版（字号、翻译字号、字重、对齐）；动效与刷新（动效档位、刷新上限）；进度与动画（逐行进度、翻译进度、字符上浮）；动态封面；更多设置（播放器翻译、开头信息清理、配置备份与恢复）。前四类卡片打开旧外观页；动态封面、播放器翻译、开头清理、配置备份直接打开各自的旧子页（它们本来就是独立页面，自己保存，不经旧主页草稿）。调试日志入口已在“状态”页，这里不重复。
- 每次回到管理页（`onResume`）在 IO 线程重新读取快照，所以旧页保存后返回即显示新值。
- 清理：删掉不再使用的 `PlaceholderContent`、`SampleBanner` 和对应文案 `mgmt_appearance_body`、`mgmt_sample_banner`（样例数据此前已移除）。

**本地验证：** Bridge 1047 tests / 0 fail / 6 skip（新增 `AppearanceSnapshotTest` 4 项：默认配置识别为默认预设、修改值与 `%d` 文案的参数类型、光晕颜色/跟随封面、插件异常时不显示插件名），Debug 与 `:app:minifyReleaseWithR8` 通过。候选 `artifacts/m5-appearance-20261115/Bridge-M5-appearance-debug-r1.apk`（`7bb9337d…`）。

**r1 设备结果：** 用户确认数值一致、保存/放弃后返回刷新正确、各入口正确、底部不被 dock 遮挡。反馈：风格预设、颜色与光晕、排版、动效与刷新、进度与动画五张卡片都打开同一个外观页，入口重复。**r2：** 选择合并而不是拆分（拆分需要让 3,283 行的旧页按分区定位，并与其悬浮预览的滚动逻辑交互，风险高）：合并为一张“歌词外观”卡片，顶部为“风格：<预设>”及说明，下面按原分区分组列出各项，底部只有一个“打开外观设置”按钮，卡片本身不可点按。动态封面与“更多设置”各自通往不同页面，保持不变。`AppearanceGroup` 去掉了不再需要的 target。本地 1047 tests / 0 fail / 6 skip，Debug 与 R8 通过。候选 `Bridge-M5-appearance-debug-r2.apk`（`dc04333e…`）。

**r2 设备结果：** 用户确认 1–5 项通过。反馈：入口既已统一，旧外观页里的播放器翻译、开头信息清理、备份与恢复、动态封面、调试入口应移除；移除顶部状态卡（模块状态 + 语言）和标题栏版本号；语言设置放到外观一级页；整理旧页剩余各项的分类与顺序。**r3：**
- **旧外观页 `LyricUiSettingsActivity`（3,283 → 3,058 行）：** 删除状态卡及其 SystemUI 状态查询（模块状态已在首页/状态页）、标题栏版本号、动态封面卡、“兼容与屏幕”中的五个子页链接（含备份的 `onActivityResult` 重建）。剩余各项重新分组排序为：风格预设 → 颜色与光晕 → 排版 → **进度与动画**（逐行进度、翻译进度、字符上浮及强度，原在“兼容与屏幕”）→ 动效与刷新 → **屏幕**（保持屏幕点亮及秒数）。控件、绑定、草稿/保存逻辑与配置字段不变，只改变所在卡片和顺序。
- **入口路由（计划 D3 已确认的“框架模块设置进入同一首页”）：** 旧页去掉调试入口后，原先“旧页 → 调试页 → 管理页”的唯一路径会断，所以 LSPosed 的 `MODULE_SETTINGS` 入口从旧页移到管理首页（`ManagementPreviewActivity` 改为 exported）；旧页保留 exported 供显式调用，不再是框架入口。各子页的 `parentActivityName` 改为管理页。**桌面图标仍未添加**（D3 待决）。
- **调试页：** 删除 M0 的“管理页原型”按钮；仅 Debug 构建出现的“动态封面 · 调试”页改从这里进入。
- **外观标签：** 新增“界面语言”卡片（中文 / English，写入原来的语言偏好后重建页面，所有设置页共用）；摘要分组顺序与旧页一致，并新增“屏幕”组（关 / 一直点亮 / N 秒）。
- **清理：** `SettingsBaseActivity.toggleSettingsLocale` 改为 `SettingsLocaleContext.setEnglish`；删除 13 条不再引用的文案；`BridgeConfigBackupContractTest` 改为断言备份入口在外观标签、不在旧页；架构守卫中“可进入管理页的旧页”清单清空。

本地 1048 tests / 0 fail / 6 skip（`AppearanceSnapshotTest` 5 项：新增分组顺序与屏幕常亮），Debug 与 R8 通过。候选 `Bridge-M5-appearance-debug-r3.apk`（`4e8ca56e…`）。待设备验证：LSPosed 模块设置打开管理首页；旧页新分组与保存/预览照常；语言切换后各页语言一致；调试页的动态封面入口（仅 Debug）。

**r3 设备反馈（4 项）与 r4：**
1. 英文下 dock 的“Appearance”两端被裁：选中项用整圆胶囊形状，弧线裁到长标签的首尾。改为 20dp 圆角矩形，标签单行不折行。
2. “重启系统界面”移到首页：旧页底栏只留“应用并保存”（占满宽度），删除旧页的重启请求与回执代码；新增 `SystemUiRestartRequest`（同一广播与回执，回调只触发一次，2.5 秒无回执视为未响应）。首页状态卡加按钮：SystemUI 运行旧构建时为主按钮，其余时候为次要按钮，检查中禁用；点按先弹确认框；收到回执后 5 秒重新检查状态。
3. 调试页“日志区域”各行贴边：原代码把开关行内边距设为 0。改为沿用通用开关行内边距，行间加分隔线。
4. 首页插件卡片“1 个需要处理”在插件只是未启用时也显示红字：把“需要处理”拆成两类——已启用但作用域里没有播放器（插件无法工作）仍为红色警告；已安装但未启用（可能是用户的选择）改为灰色信息图标 +“N 个未启用”。卡片提示改为三级（无 / 提示 / 警告）。

本地 1048 tests / 0 fail / 6 skip（`HomeDashboardTest` 改为分别统计），Debug 与 R8 通过。候选 `Bridge-M5-appearance-debug-r4.apk`（`ca269091…`）。 **r4 设备结果：用户确认全部通过。**

### 14.19 M5 收口：MD3 十类审查（2026-11-15，本地，未提交）

**分支：** Bridge `feat/m5-wrapup`（基于 `a8b177f`）。**方法：** 加载 material-3 技能，按 §8.5.2 第 5 步的十类逐项阅读管理 UI 源码（`management/` 下首页、商店、外观、状态、设置向导与主题，约 2,400 行），用脚本计算 WCAG 对比度。只做源码与计算，**未在设备上检查**；按 §8.5.2 不打分。

| 类别 | 结论与证据 | 处理 |
| --- | --- | --- |
| 颜色 | **问题（已修）：** `primary` 是浅琥珀 `#F2C14E`，与白色对比度仅 1.68:1。Material 把 `primary` 用作描边按钮/文字按钮的文字、进度条、开关轨道，外观页分组小标题也用它，均不达 4.5:1（文字）或 3:1（组件）。`outline`、`outlineVariant`、`inverseSurface`（Snackbar）、`tertiary`、`surfaceVariant` 未设置，沿用 Material 默认的紫调基线。其余文字配对：`onSurfaceVariant` 6.05:1、`error` 4.90:1、`secondary` 5.54:1、深色字配琥珀 11.25:1，均达标。 | `primary` 改为深金 `#8A5A00`（白底 5.93:1，新增 `settings_primary_text`），`onPrimary` 白；琥珀保留为 `primaryContainer` 和主按钮填充（新增 `BrandButton`，14 处替换，深色字 11:1，与旧页“应用并保存”一致）；补齐中性灰 `outline`/`outlineVariant`/`inverseSurface` 等，`tertiary` 用品牌青，`inversePrimary` 用琥珀 |
| 排版 | 全部使用 `MaterialTheme.typography` 字级，没有 `sp` 硬编码；系统字体 | 无 |
| 形状 | 卡片用 `shapes.extraLarge`，状态胶囊与色块用 `CircleShape`；dock 选中项曾为魔法值 20dp | dock 改为 `shapes.large` |
| 层级 | 卡片与背景靠明度区分；只有悬浮 dock 用 8dp 阴影（浮在滚动内容上，属 MD3 允许的保护性阴影） | 无 |
| 组件 | 均为官方 Material 3（Card、Button、FilterChip、Switch、AlertDialog、NavigationRail、Snackbar、TopAppBar） | 无 |
| 布局 | 内容最大宽 840dp；≥600dp 用 rail；间距统一走 `Spacing`。宽度判断仍是 `screenWidthDp`，未用 WindowSizeClass（§14.4 已记录） | 维持，M6 视折叠屏结果再定 |
| 导航 | 四个顶级入口有文字和图标；设置向导有返回按钮和 `BackHandler`；LSPosed 模块设置已进首页 | 无 |
| 动效 | 只有组件默认动效，状态不依赖动画完成回调 | 设备上检查“减少动画” |
| 无障碍 | 纯图标按钮（刷新、向导返回）有 `contentDescription`；其余图标都有文字伴随（描述置空避免重复朗读）；可点卡片有 `Role.Button`；FilterChip 等受 48dp 最小触控区约束。**未验证：** 大字体、TalkBack 朗读顺序、横屏 | 列入设备清单 |
| 主题 | 固定浅色、无动态取色（§8.3 决定） | 无 |

**范围外但记录：** 旧 Views 页的辅助说明文字用 `settings_text_muted #9AA0A6`（白底 2.64:1，10.5sp），包括外观页“进度与动画”里的两条依赖说明。属于旧页面统一配色，本次不改；建议 M6 前与旧页其他灰字一起处理。

**本地验证：** Bridge 1048 tests / 0 fail / 6 skip，Debug 与 R8 通过。候选 `artifacts/m5-wrapup-20261115/Bridge-M5-wrapup-debug-r1.apk`（`27975e8e…`）。视觉变化：描边/文字按钮、进度条、开关和外观页小标题从浅琥珀变为深金色；实心主按钮仍是琥珀。

**设备检查清单（M5 收口，需用户执行）：**
1. 颜色：上述按钮、进度条、开关、小标题清晰可读，主按钮观感与旧页一致。
2. 系统字体调到最大：首页四个小卡片、dock 标签、商店卡片按钮、设置向导选项不截断、不重叠。
3. TalkBack：首页从上到下朗读顺序合理；dock 四项读出名称与选中状态；刷新按钮读出“重新检查”。
4. 横屏：内容不被导航条或摄像头挖孔遮挡；dock 或 rail 正常。
5. 三键导航与手势导航各一次：dock 与列表末尾不被导航条遮住。
6. 开发者选项关闭动画（动画缩放设为“关闭”）：切换页面、展开确认框、商店安装进度正常。

**仍待用户决定：**
- **D3 桌面图标：** LSPosed 模块设置已进入管理首页；是否新增桌面入口（设置里可隐藏）。
- **升级提示：** 4.x 用户升级后，在 LSPosed 点模块设置看到的从“外观设置”变为管理首页。是否在首页加一次性“新版说明”卡片（可关闭，只显示一次）。

**§14.19 设备结果：** 用户确认清单 1–6 项通过（r1 `27975e8e…`）。

### 14.20 M5 收口：桌面图标（D3）与一次性“新版说明”（2026-11-15，本地，未提交）

**用户决定（2026-11-15）：** 新增桌面图标，首页提供隐藏开关；首页加一次性“新版说明”卡片。

- **桌面图标：** 新增 `activity-alias` `.management.LauncherEntry`（MAIN/LAUNCHER，指向管理首页，名称 “Live Lyrics Bridge”）。首页底部“在桌面显示图标”开关通过 `PackageManager.setComponentEnabledSetting(..., DONT_KILL_APP)` 启用/停用这个 alias；LSPosed 的 `MODULE_SETTINGS` 入口在 Activity 本身，隐藏图标不影响从 LSPosed 进入。首页 Activity 的标题由“管理界面预览”改为 “Live Lyrics Bridge”（最近任务里显示）。
- **新版说明：** 只给从旧版升级的人看：`lastUpdateTime > firstInstallTime` 且未点过“知道了”。全新安装在首次打开时直接记为已看过，之后 5.x 小版本更新也不会再弹。内容五条：LSPosed 模块设置改进管理页、外观在“外观”标签；新增商店；状态页与短时诊断；桌面图标可隐藏；原有外观/翻译/开头清理设置保留。点“知道了”后不再显示（`management_home` 偏好）。
- **已知限制：** 部分桌面在停用 alias 后需要片刻才刷新图标；Android 10+ 对“没有桌面入口但申请了权限”的应用可能显示系统生成的应用信息图标，ColorOS 是否这样需设备确认。

本地 1050 tests / 0 fail / 6 skip（新增 `HomeExtrasTest` 2 项：只对升级且只一次、默认显示图标），Debug 与 R8 通过。候选 `artifacts/m5-wrapup-20261115/Bridge-M5-wrapup-debug-r2.apk`（`f21a4c58…`）。待设备验证：安装后桌面出现图标并打开首页；开关隐藏/恢复图标；隐藏后 LSPosed 仍可进入；新版说明卡片显示一次，点“知道了”后重进不再出现（本机是覆盖安装，所以会显示）。

**r2 设备结果：用户确认全部通过（桌面图标、隐藏/恢复、隐藏后 LSPosed 仍可进入、新版说明只显示一次）。M5 完成，已提交 `87b7645`（未推送）。**

## 15. 主线五：代码结构与性能治理（E 系列）

> 状态：**仅分析与方案，未落任何代码**；第 15.8 节 D11–D15 已于 2026-11-14 由用户确认按推荐采用。落档日期：2026-11-14。本节是用户在 M1 期间追加的第五条主线：在下一大版本内完整治理 Bridge 的代码结构与性能，重点拆分 `LockscreenLyricsModule.java`。
> 证据范围：全部为**静态分析**（文本/括号深度扫描，不是 AST；数字允许小幅误差）加上既有 [Phase 6 性能台账](<4.0/PHASE-6-PERFORMANCE-LEDGER.md>) 的历史设备数据。本轮没有运行构建、采样或设备测试。第 15.4 节的性能候选项在采样确认前都只是候选，不是已证实的瓶颈。
> 范围：Bridge 仓库。Providers 仓库本轮**未做**同等盘点，见第 15.9 节。

### 15.0 与本文其他约束的关系

- 第 0 节第 5 条“不借机重写歌词渲染器”、第 1.2 节“不将 SystemUI Hook、歌词 Canvas 渲染、AOD 迁移到 Compose”继续有效。E 系列是**保持行为的结构迁移**加**有采样依据的性能修正**，不改语言、不改 Hook API 代际、不改 `LyricUiConfig` schema、不改 scope。
- 结构迁移与算法/参数/时序调整**不得出现在同一次提交**。任何一刀只做其中一类。
- 本节承接并更新 [4.0 拆解方案](<4.0/LOCKSCREEN-LYRICS-MODULE-DECOMPOSITION.zh-CN.md>)（基线 `e597c5b`，15,735 行）。该文的职责地图、字段所有权表、反模式清单和行为回归矩阵仍然适用，本节不重复，只给出新基线、未执行原因的对策、与 5.0 里程碑的排期和新增的性能部分。两者冲突时以本节为准。

### 15.1 当前基线（2026-11-14，工作树，HEAD `44aca8e` + 未提交修改）

**体量与增长。** `LockscreenLyricsModule.java` 现为 17,968 行，占 Bridge 主源码（186 个 Java 文件，53,175 行）的约 34%。

| 版本 | 行数 | 说明 |
| --- | ---: | --- |
| v4.0.0 | 14,565 | |
| v4.3.0（= 4.0 拆解方案基线） | 15,735 | 方案于 2026-09-18 入库 |
| v4.5.0 | 16,948 | |
| v4.5.4 | 17,439 | |
| HEAD `44aca8e` | 17,869 | |
| 当前工作树 | 17,968 | M1 诊断接入 +99 |

4.0 拆解方案入库后，该文件又被 29 次提交改动（+2,618 / −484），净增约 2,200 行。方案建议的 `hook/`、`runtime/`、`recycler/` 目录和其中的组件**一个都没有出现**。同期新功能（动态封面）是以独立包 `systemui/artwork/` 落地的，说明“新功能进新包”可行，但“旧代码迁出”没有发生。结论：**只有方案不够，必须有阻止继续增长的机器门禁**（见 E-0）。

**结构计数**（入口类顶层，不含内嵌类内部）：

| 项 | 数量 |
| --- | ---: |
| 字段声明 | 约 371（常量 117；可变实例字段约 174；`volatile` 147；集合类字段 53；专用锁对象 17） |
| 方法 | 564（其中 `static` 148）；中位数 15 行；≥50 行 50 个；≥100 行 7 个 |
| 内嵌类型 | 13 个，共 4,149 行；`OfficialLyricTextRenderer` 独占 3,762 行（114 个方法、约 84 个字段，6 个方法 ≥100 行） |
| 匿名 Host 回调块 | 5 个，共约 320 行（`OfficialLyricDrawCoordinator.Host` 一个就 184 行） |
| Hook 安装点（`.intercept(`） | 40；Hook ID 常量 28 |
| `synchronized` 使用 | 132 处，分布在 20 个以上不同的锁对象上 |
| Handler 投递（`post`/`postDelayed`） | 67 处 |
| `catch (Throwable` | 108 处 |
| 反射调用点 | 64 处 |
| 日志调用 | `maybeLog*` 70 处，其余 `info`/`warn`/调试约 130 处 |

最长的顶层方法：`cacheSystemUiLyricModelLocked` 160 行、`runActiveLyricRefresh` 143、`handlePlayerTranslationSettingsChanged` 131、`onSystemUiLoadLyricInBg` 129、`resolveOfficialLyricDrawFrame` 128、`tryInstallLyricsRecyclerViewHook` 116、`installSystemUiWordLyricHooks` 101。渲染器内：`drawSegment` 190、`draw` 135、`resolveSlotHeight` 135、`drawMainLyricPass` 131、`prepareLyricGroupDraw` 128、`drawLiftedGraphemes` 100。

注意两点与 4.0 基线的差异：`cacheSystemUiLyricModelLocked` 在 Phase 6 slice 5 做过拆分，4.0 基线时为 116 行，现在是 160 行；Phase 6 已外移的 `OfficialLyricDrawCoordinator`、`NativeLyricModelAssembler` 等仍通过大型匿名 Host 回到入口类读写状态，状态所有权没有随类一起迁出。

**职责分区**（按当前行号区间估算，合计约等于文件总行数）：

| 分区 | 约行数 | 当前位置（行号仅供定位，以方法名为准） |
| --- | ---: | --- |
| 内嵌渲染器 `OfficialLyricTextRenderer` | 3,760 | 13936–17698 |
| LyricsRecyclerView：定位、对齐、几何、Hook 安装、反射 | 2,810 | 5842–7521、8343–8809、9532–10191 |
| 字段、常量、Host 组装 | 1,010 | 1–1006 |
| 翻译 action：媒体层与偏好 / View 发现与指纹 | 940 / 660 | 1492–1999、2355–2783 / 5186–5841 |
| 歌词模式、加载、provider 绑定 + 模型缓存与提交 | 740 + 390 | 3547–4283、12178–12567 |
| SystemUI Hook 安装 + KuWo/封面/插件 ClassLoader 兼容 | 1,510 | 2784–3546、7522–7932、8009–8342 |
| 设置/配置应用、广播接收器、调试配置 | 900 | 2001–2354、11365–11790、13749–13873 |
| active refresh、View 注册表、surface 重激活 | 830 | 10192–11016 |
| 诊断日志（`maybeLog*`、描述、门面） | 810 | 12941–13748 |
| DrawFrame 选择与 TextView 绑定 | 720 | 8810–9531 |
| 播放状态、MediaController、时钟 | 670 | 4284–4879、7933–8008 |
| 生命周期、system_server/OPlus 策略、AOD 支持 | 490 | 1007–1491 |
| 屏幕超时 / WakeLock | 480 | 11791–12177 及其接收器 |
| 切歌 handoff 与官方行抑制 | 370 | 12568–12940 |
| 通用反射工具 | 350 | 11017–11364 |
| 全局 View/TextView/ImageView Hook 回调 | 310 | 4880–5185 |
| 其余小内嵌类 | 300 | 13903–13935、17699–17968 |

**测试耦合。** 140 个测试类中有 15 个引用入口类，其中 12 个**直接读取入口源码文本**做契约断言：`BridgeArchitectureGuardTest`、`CharLiftRendererContractTest`、`LyricUiConfigContractTest`、`OfficialImmersiveLyricEntryContractTest`、`OfficialLyricRowTypefaceContractTest`、`OfficialLyricScalePivotHookContractTest`、`OfficialLyricsRecyclerFieldsContractTest`、`OplusPkgActionsRuleReplayContractTest`、`OplusPluginMediaInfoContractTest`、`PluginClassLoaderFactoryHookContractTest`、`SystemUiLyricSupportGateContractTest`、`TranslationActionRedirectContractTest`。任何迁移都会让它们失败，这是每一刀必须同步处理的固定成本，也是此前迁移没有发生的原因之一。

**入口类以外的结构问题。**

- 根包 `io.github.andrealtb.lockscreenlyrics` 平铺 93 个文件，设置页 Activity（普通 App 进程）与 Hook 侧类（SystemUI 进程）混在同一个包。第 10 节要求的“管理面 / Hook 面”守卫目前只能靠文件名清单实现。
- `LyricUiSettingsActivity.java` 3,283 行：归第 8 节 U 系列处理，E 系列不重复规划，只要求两边不同时改同一文件。
- `NativeLyricModelAssembler.java` 1,626 行、`DynamicArtworkRuntime.java` 1,013 行：已是独立类，暂列观察项，按第 15.4 节采样结果决定是否处理。
- R8 只保留 `XposedModule` 子类入口（`java_init.list` 经 `-adaptresourcefilecontents` 改写），内部类移动不影响保留规则；但正式 R8 形态仍需按 M6 门禁验证。

### 15.2 目标形态与完成定义

入口类最终只保留：三个生命周期回调、进程/scope 门禁、组件构造与依赖注入、极少量跨组件事件转发、全局紧急降级。目标组件与字段所有权沿用 4.0 拆解方案第 4、5、7、8 节，这里只列与当前代码对应的调整：

- 已存在且应成为真正 owner 的类：`OfficialLyricDrawCoordinator`、`LyricRenderTransactions`、`SystemUiLyricPublicationGate`、`NativeLyricModelAssembler`、`LyricSurfaceDrawTransaction`、`LyricsRecyclerFieldAccessor`、`SystemUiRuntimeBootstrap`。E 系列优先把它们从“通过 Host 回读入口状态”改为“自己持有状态”，再新增类。
- 4.0 方案中的 `LyricModelStore` 应与现有 `LyricRenderTransactions` 合并考虑：`currentWordLyricModel` 在入口类仍有 76 处引用，而 `lyricRenderTransactions` 已有 38 处，两套模型真值并存是首要清理对象。
- 包结构同时落实第 10 节守卫：Hook 侧进 `bootstrap/`、`hook/`、`runtime/`、`recycler/`、`render/`、`systemui/`、`players/`；普通 App 侧进 `settings/`（旧 Views）与第 10 节的 `management/`。守卫从“文件名清单”改为“按包判定”。

**完成定义**（全部满足才算 E 系列完成）：

1. `LockscreenLyricsModule.java` ≤ 1,500 行，顶层可变字段 ≤ 30，无内嵌业务类；这是预算上限，不是每一刀的目标。
2. 没有任何组件以入口类或“全量状态对象”为依赖；单个 Host/Port 接口方法数 ≤ 12。
3. 模型、播放、Recycler、handoff、屏幕超时、翻译 action、诊断、渲染器各有唯一状态 owner；每个可变字段在源码中能指出唯一写入组件。
4. 12 个源码文本契约测试全部迁到新文件或改为行为测试，入口类不保留影子实现。
5. 性能不劣于迁移前基线（判定方法见第 15.4 节），已确认的候选项按采样结论处理或书面放弃。
6. 第 12 节“运行回归”组在用户设备上通过；正式签名 + R8 候选通过。

### 15.3 实施切片（E-0 至 E-8）

每一刀的通用规则：

- **纯移动**的刀：方法体、字段初始值、锁、日志 event 与字符串、Hook ID、安装顺序逐字不变，只改访问点。用脚本比对迁移前后方法体（忽略缩进与限定名前缀）作为机械等价证据，结果附在该刀的记录里。
- 每刀结束跑 `:app:testDebugUnitTest :app:assembleDebug`，重算 `verify-build-identity`，并更新入口类预算文件（只允许下降）。
- 中高风险的刀必须有用户设备冒烟后才能进入下一刀；设备操作仍需当次授权。本地通过不等于设备通过。
- 一刀失败时整刀回退，不在入口类留半迁移状态。

| 切片 | 内容 | 入口类预计减少 | 风险 | 设备验证 |
| --- | --- | ---: | --- | --- |
| **E-0 止血与保护网** | ① 在 `BridgeArchitectureGuardTest` 增加入口类预算守卫：行数、顶层字段数、方法数、内嵌类行数不得超过入库的预算值，预算只能下调。② 12 个源码契约测试改为通过一个“源码定位表”读取目标文件，后续迁移只改表。③ 生成 Hook ID 清单、日志 event 清单、安装顺序清单三份基线，作为后续每刀的比对对象。④ 写入方法体等价比对脚本。⑤ 按第 15.4 节采集性能基线。不移动任何业务代码。 | 0 | 低 | 仅性能基线采集 |
| **E-1 渲染器机械外移** | `OfficialLyricTextRenderer` 及其内部类原样移到 `render/`，外部访问收敛为一个窄 `RendererHost`。它已是 `static` 内嵌类，不持有入口实例，是体量最大而耦合最浅的一块。不拆 painter、不改任何常量。 | 约 3,760 | 中（体量大，但纯移动） | 需要：逐字、逐行、上浮、发光、译文、AOD 低帧率 |
| **E-2 无状态与低耦合部分** | 通用反射工具按系统边界归入各自 accessor（不建通用 `ReflectionUtils`）；`maybeLog*` 与描述函数归入 `diagnostics/`；system_server/OPlus 策略 Hook 与 AOD 支持归入 `OplusMediaPolicyHooks`；小内嵌值类各归其主。 | 约 1,900 | 低到中 | 需要：模块加载、白名单/黑名单、AOD 入口 |
| **E-3 屏幕超时** | `ScreenTimeoutController` 带走接收器、WakeLock、keyguard 缓存、pulse、evidence 窗口。输入只有配置、歌词模式与可见证据。 | 约 480 | 高（WakeLock） | 需要：续租、灭屏、解锁、条件失效后释放 |
| **E-4 翻译 action** | 先 `TranslationActionViewTracker`（View 发现、指纹、presentation），再 `TranslationActionController`（rule-0、偏好、rebind）。 | 约 1,600 | 中到高 | 需要：按钮显隐、切换、切歌后重绑、各播放器策略 |
| **E-5 播放与模型发布** | `SystemUiPlaybackController`（MediaController、时钟、reset guard）→ 模型真值并入 `LyricRenderTransactions`/store → `LyricPublicationPipeline` → 加载 Hook 只剩读参、调 pipeline、一次 `proceed`。`canCommit(loadEpoch)` 提交前复核不可丢。KuWo/插件兼容与 `PluginHookGenerationManager` 在此刀一并迁出。 | 约 3,300 | 高 | 需要：快速切歌、同曲 metadata 更新、暂停/seek、KuWo、清理配置重放 |
| **E-6 Recycler 与 View 注册表** | `LyricViewRegistry`（弱引用与快照）→ `LyricsRecyclerHookInstaller` → `LyricsRecyclerController` → `LyricSurfaceReadinessController`。SystemUI 对定位、滚动、adapter 通知、行变换的所有权不变。 | 约 2,800 | 高 | 需要：attach/detach、亮屏/AOD 往返、沉浸入口、对齐与行高 |
| **E-7 handoff、绑定与 active refresh** | `LyricSurfaceHandoffController`（显式状态机）、`OfficialLyricBindingController`、`ActiveLyricRefreshScheduler`；设置应用改为发布 `RuntimeConfigSnapshot`，各组件自行应用。全局 View Hook 回调归入专用 installer。 | 约 3,100 | 高 | 需要：切歌淡入、迟到接管、transient miss、刷新率档位 |
| **E-8 收尾** | 入口类收敛到完成定义；根包按 Hook 面/App 面分包并把守卫改为按包判定；第 15.4 节中确认有效的性能项落地；painter 是否继续拆分按采样结论书面决定。 | 余量 | 中 | 需要：第 12 节运行回归全组 + 正式 R8 候选 |

预计减少量为按第 15.1 节职责分区行号区间的估算，只计方法与内嵌类；文件开头约 1,000 行的字段、常量与 Host 组装未计入各刀，它们随各自 owner 一起迁出。实际以每刀后的预算文件为准。

顺序理由：E-1 放在最前，是因为它一次拿掉五分之一体量且不涉及状态所有权，能最早降低其余各刀的合并冲突面；E-3 至 E-7 按“输入输出越独立越先做”排列，与 4.0 方案的 Slice 1–6 一致，只是把渲染器外移从最后提到最前、把诊断日志迁移并入 E-2。

### 15.4 性能治理

**原则。** 先在同一可观察场景建立基线，再只改被采样证实的最小原因；源码能看出多一次加锁或分配，不等于已证明掉帧。性能修正与结构迁移分开提交。诊断默认关闭，关闭时不得读纳秒时钟或拼接字符串。

**已有手段**（全部沿用，不另造）：`BridgePerformanceSampler` 的五项 `PERF_SAMPLE`（`text-view-draw`、`frame-resolve`、`renderer-draw`、`active-refresh`、`model-parse`）；`BridgePerfTrace` 的 `CLL:*` atrace 区段；`scripts/capture-lockscreen-perf.ps1 -Mode jank`（Perfetto，FrameTimeline）。

**已有设备数据**（2026-10-13，PJZ110，单轮样本，来自 Phase 6 台账 8A）：停用 Bridge 时 AOD 切换窗掉帧 5.7%、其他 1.9%；Bridge 增量约 0.5–4 个百分点；歌词绘制 p50 约 521µs；剩余热点为当前行主歌词（上浮开启时约 60% 在上浮）与偶发 glow 光栅化（1–6ms/次）。这些是迁移前的参考值，E-0 需要在当前代码上重采，不能直接当作 5.0 基线。

**E-0 需要采集的基线场景**（每项记录候选 APK 哈希、配置、框架版本）：

| 场景 | 观察量 |
| --- | --- |
| 锁屏无歌词（Bridge 启用但无 lyricInfo） | SystemUI 主线程 doFrame 均值、全局 Hook 回调耗时；用于度量“休眠路径”开销 |
| 锁屏逐字歌词，上浮开/关 | 五项 `PERF_SAMPLE`、掉帧率、RenderThread |
| 切歌（连续 10 次） | `model-parse`、主线程长任务、handoff 窗口掉帧 |
| 亮屏 ↔ AOD 往返 | 切换窗掉帧率、主线程 binder 次数 |
| SystemUI 冷启动 | Hook 安装与 DexKit 解析耗时（目前**没有**现成采样点，需新增一个仅在 tracing 开启时生效的区段） |
| 长时间播放（约 30 分钟） | SystemUI 进程 Java 堆与 bitmap 占用趋势 |

**判定标准。** 不预设绝对数字。每刀结构迁移后，同场景同设备的五项 `PERF_SAMPLE` 均值与掉帧率不得劣于 E-0 基线超过单轮测量噪声（噪声幅度在 E-0 用重复采样确定）；超出即视为该刀回归，先定位再继续。

**候选项**（静态阅读得到，全部**待采样确认**）：

| 编号 | 候选 | 静态依据 | 处理方式 |
| --- | --- | --- | --- |
| P-1 | 全局 View Hook 的休眠快路径 | SystemUI 内所有 `TextView.onDraw`/`setText`、`View.onAttachedToWindow`/`onDetachedFromWindow`/`setVisibility`/`setContentDescription`、`ImageView.setImageDrawable`/`setImageBitmap` 都经过 Bridge 回调（`installSystemUiWordLyricHooks`）。其中 `onImageViewSetImage` 每次调用都先进入 `translationActionViewsLock` 并查弱引用表，没有前置的无锁判断；`isLyricsRecyclerView` 每次调用都在全局 `synchronized` 内查类缓存 | 用“无歌词”场景量化。若可测，改为先做一次 `volatile` 状态判断再进入锁；类匹配缓存改为无锁读。不改变 Hook 集合与安装时机 |
| P-2 | 关闭调试时仍然拼接日志字符串 | `info(area, event, String)`、`translationButtonDebug(String)` 的签名接收已拼好的字符串，后者内部才包成 Supplier；调用点 28 + 约 56 处，其中部分位于全局 Hook 回调内（如 `onViewSetContentDescription`） | 逐点核对是否已被开关前置保护；未保护且在高频路径的改为 Supplier 重载。事件名与输出格式不变 |
| P-3 | 锁与 `volatile` 过多 | 132 处 `synchronized`、20+ 锁对象、147 个 `volatile` 字段；其中有多少状态实际只在主线程读写**尚未逐个核实** | 不单独做。随 E-3 至 E-7 明确每个组件的线程归属后，主线程独占状态去锁，跨线程状态收敛为不可变快照。先加调试期线程断言，再删锁 |
| P-4 | 通用反射工具每次调用都遍历类层级 | `readFieldValue`、`tryInvokeNoArgByName`、`invokeTwoIntByName`、`findMethod` 等无缓存；Phase 6 已为 Recycler 索引字段和 `getAdapter` 做过缓存并验证有效 | 随 E-2 归入各 accessor 时按同样方式加类级缓存（含 missing 结果）；只处理采样中出现的调用点 |
| P-5 | active refresh 调度 | `runActiveLyricRefresh` 143 行，经 Handler 延时驱动，内部混合 View 搜索与日志 | 随 E-7 迁出后，用 `active-refresh` 采样评估不可见时是否完全停止、是否有重复投递。不预设改用 Choreographer |
| P-6 | 渲染器剩余热点 | Phase 6 台账“未做”项：上浮静止字合并绘制、下一行 glow 预热 | E-1 纯移动完成后再评估；只在 `renderer-draw` 采样显示收益时做，且与移动分开提交 |
| P-7 | 模型解析重复 | 台账遗留项：按签名缓存解析结果、displayLyric 与 supplemental 视图共享 | 随 E-5 评估，以 `model-parse` 采样为准 |
| P-8 | SystemUI 启动期开销 | 40 个 Hook 安装点与 DexKit 解析都在 SystemUI 启动路径上，目前没有量化数据 | 先新增采样点量化；未量化前不改安装时机，不做懒安装 |
| P-9 | 常驻内存 | 多个静态 `WeakHashMap` 缓存、glow bitmap 缓存、翻译图标指纹缓存，容量上限不统一 | 盘点每个缓存的上限与失效条件，随各 owner 迁出时写明；以长时间播放场景验证 |

明确不做：打开 `DRAW_FRAME_REUSE_ENABLED` 换取表面性能；为性能改变 Recycler 所有权、AOD 私有状态或 handoff 时序；为减少 Hook 数量而放宽 LyricsRecyclerView 归属门禁。

### 15.5 与里程碑的排期关系

| 时点 | E 系列工作 | 约束 |
| --- | --- | --- |
| M1（当前） | E-0 | 与 C-01/02/03 收尾并行；预算守卫一旦入库，C-04 及之后的新代码不得再进入入口类 |
| C-04/C-05 开工前 | E-1、E-2 | C-04 的“功能级降级与恢复”和 C-05 的回放测试都要落在决策点上；先把渲染器与无状态部分移走，避免两条线反复改同一个 18,000 行文件 |
| M2–M4（商店与 scope，主要在普通 App 侧） | E-3 至 E-7，每刀独立设备冒烟 | 与 Hook 面改动少的阶段错开；C-04 的降级状态归属按 E 系列的 owner 落位 |
| M5（UI 集成） | E-8 的分包 | 与 U 系列协调，不同时改 `LyricUiSettingsActivity` |
| M6 RC 冻结后 | 不再做任何结构迁移 | 未完成的切片顺延到下一版本，并在第 12 节标“未覆盖” |

E 系列不阻塞商店与引导功能；若进度不足，允许 5.0 只完成到某一刀（每刀结束都是可发布状态），但 E-0 的预算守卫必须在 5.0 内生效。

### 15.6 验证与门禁

- 每刀：`git diff --check`；`:app:testDebugUnitTest :app:assembleDebug`；`verify-build-identity`；Hook ID、日志 event、安装顺序三份清单与 E-0 基线一致；`scope.list`、`module.prop`、`java_init.list` 不变；新类不反向依赖入口类；预算文件下调。
- 纯移动的刀附方法体等价比对结果。
- 行为回归沿用 4.0 拆解方案第 10.3 节矩阵（模块加载、无 lyricInfo、快速切歌、同曲 metadata 更新、播放/暂停/seek、AOD/亮屏、Recycler attach/detach、翻译开关、灭屏/解锁、KuWo/插件 loader、渲染器异常降级）。
- 诊断开关开与关两种状态下行为一致（M1 的教训：可选诊断必须证明失败与开关都不改变业务）。
- C-02 的 `CompatibilityStatus` 七个槽位的观测点随决策代码一起迁移，迁移前后同场景的状态序列一致。
- 设备验证由用户执行或当次授权；本地构建、单元测试、`PREPARED`/`DISPATCHED`/`DRAW_COMMANDS` 都不是视觉验收。

### 15.7 风险与对策

| 风险 | 对策 |
| --- | --- |
| 方案再次只停留在文档，文件继续增长 | E-0 预算守卫是硬门禁；新功能评审以“是否触碰预算”为检查项 |
| 纯移动中混入行为变化，事后难以归因 | 移动与修改分提交；方法体等价比对；每刀设备冒烟 |
| 把入口类换成一个全量状态对象或万能 Host | 完成定义第 2 条；沿用 4.0 方案“必须避免的状态反模式” |
| 源码文本契约测试成为迁移阻力，或被改成无意义断言 | E-0 先改为定位表；能改成行为测试的优先改 |
| 与 C-04/C-05、U 系列并行造成大面积合并冲突 | 第 15.5 节排期；同一文件同一时间只属于一条线 |
| 去锁引入竞态 | 先加线程断言并在调试构建运行，再删锁；跨线程状态只走不可变快照与既有 epoch 复核 |
| R8 或框架热重载形态下的差异 | 每个高风险刀后抽查 R8-only 构建；正式形态在 M6 验证 |
| 性能“优化”缺少证据 | 所有 P 项以 E-0 基线与同场景采样为准，未证实的不做 |

### 15.8 决策（2026-11-14 用户已确认按推荐采用）

下表“推荐”列即已确认的决定。“说明”列保留当时的理由与备选，供以后需要调整时参考；调整须用户再次确认。

| 决策 | 推荐 | 说明 |
| --- | --- | --- |
| D11 E 系列是否作为 5.0 的发布门禁 | E-0 为硬门禁；E-1 至 E-8 尽力完成，可按刀截断 | 全部设为硬门禁会让 5.0 的发布时间取决于最敏感的时序迁移 |
| D12 渲染器外移提前到最前 | 提前（E-1） | 与 4.0 方案“最后做”的顺序相反；理由见第 15.3 节。若更看重先验证低风险流程，可改为 E-2 先行 |
| D13 入口类完成上限 | ≤ 1,500 行、可变字段 ≤ 30 | 数字可调；重点是有一个入库的、只降不升的预算 |
| D14 迁移期间语言 | 保持 Java | 与第 0.2、8 节一致，Kotlin 仅用于新管理 UI；结构迁移不叠加语言迁移 |
| D15 根包分包时机 | 放到 E-8 | 提前做会与所有在途改动冲突，且对运行行为没有收益 |

### 15.8.1 E-0 实施记录（2026-11-15，已提交 `634a9d0`，未推送）

**分支：** Bridge `feat/e0-guardrails`（基于 `87b7645`）。只加测试、基线文件和脚本，**不改任何 `app/src/main` 代码**，所以不需要设备回归。

1. **入口类预算守卫**：`guard/EntryClassGuardTest.entryClassStaysWithinItsBudget` 用 `guard/JavaOutline`（去掉注释和字面量后按括号深度扫描，不是 AST）统计 `LockscreenLyricsModule.java`，与 `app/src/test/resources/guard/entry-class-budget.properties` 比较。入库值（2026-11-15）：行数 17,989、顶层字段 373、方法 565、内嵌类型 13 个共 4,168 行；与第 15.1 节的静态估计（约 371 / 564 / 13 个 4,149 行）一致，差异来自计数口径。超出预算即失败；低于预算超过余量（行 60、字段 3、方法 3、内嵌类型 0、内嵌行 30）也失败，提示把预算调低，所以预算只降不升。扫描器有一条小样本自检测试。
2. **契约测试定位表**：新增 `guard/BridgeSourceMap`，按将来迁移的刀分区（RENDERER=E-1、OPLUS_POLICY=E-2、TRANSLATION_ACTION=E-4、PLUGIN_COMPAT=E-5、RECYCLER=E-6，另有 CONFIG_CONSUMERS、SYSTEMUI_RUNTIME），目前全部指向入口类。第 15.1 节列出的 12 个源码文本契约测试（共 15 处读取）改为 `BridgeSourceMap.read(Area.X)`，断言本身一字未改；以后某刀移走代码只改这张表。另有测试检查表中每个文件都存在。
3. **三份基线**（`app/src/test/resources/guard/`，测试逐字比对，变化时把当前值写到 `app/build/e0-guard/*.actual` 供审阅后覆盖）：
   - `hook-ids.txt`：28 个 `HOOK_ID_*` 常量的值与引用次数；
   - `log-events.txt`：`BridgeEvents` 全部定义，以及全部源码中每个事件常量和字面事件名的使用次数；
   - `hook-install-order.txt`：40 处 `.intercept(` 按所在方法分组、按源码顺序列出 `setId` 与回调。按方法名分组，所以整方法搬到别的类不会改变清单。
4. **方法体等价脚本**：`scripts/guard/method-bodies.py --base <ref> [--head <ref>]`。按“方法名 + 参数类型”匹配两个版本中的全部方法，不管在哪个文件或类；从左花括号起比较方法体，去掉注释、合并空白，并忽略 `this.` / `LockscreenLyricsModule.`（可用 `--strip` 追加，例如 `host.`）。方法体有变化或消失则退出码为 1，并输出 diff。自检：`HEAD` 对工作区 2,722 个方法全部一致；`0d883d1 → 87b7645` 正确报出 M5 里实际改动的 Java 方法（10 处改动、14 处删除）。
5. **性能基线：未做，需要设备。** 按第 15.4 节的六个场景采集：锁屏无歌词、逐字歌词且上浮开/关、连续切歌 10 次、亮屏与 AOD 往返、SystemUI 冷启动、约 30 分钟长播放。使用现有 `scripts/capture-lockscreen-perf.ps1 -Mode jank` 和 Bridge 调试页的性能区域；每项记录 APK 哈希、配置与框架版本。抓取 Perfetto/logcat 需要用户当次授权。另外，冷启动场景目前没有现成采样点，需要先新增一个只在开启 tracing 时生效的区段。

本地 Bridge 1056 tests / 0 fail / 6 skip（新增 6 项守卫测试），Debug 与 R8 通过。

### 15.8.2 E-1 实施记录：渲染器纯移动（2026-11-15，已提交，未推送）

**分支：** Bridge `feat/e1-renderer-move`（基于 `634a9d0`）。

- **移动：** 内嵌类 `OfficialLyricTextRenderer`（入口类第 13938–17718 行，3,781 行，含其内部类型）原样移到同包新文件 `OfficialLyricTextRenderer.java`。只做三处机械改动：整体左移 4 个空格；`private static final class` 改为包内可见的顶层 `final class`；文件头复制入口类的 import，并加 `import static ...LockscreenLyricsModule.*`，让方法体里对入口类静态成员的不带限定名引用保持原样。
- **访问点：** 入口类里渲染器用到的 11 个 `private static` 成员去掉 `private`，改为包内可见，方法体不变：`dp`、`sp`、`nullToEmpty`、`shortenForLog`、`isLyricLayoutDiagnosticsEnabled`、`isLyricVerboseDiagnosticsEnabled`、`setOfficialLyricSlotHeight`、`findLyricsRecyclerItemView`、`findContainingLyricsRecyclerView`、`applyFocusedOfficialLyricViewEffects(TextView, float, boolean)`、`AOD_WORD_PROGRESS_TO_LINE_ANIMATION_MS`。
- **名称解析核对：** 新文件只有这一条静态导入；入口类 12 个内嵌类型与新文件的单类型导入、同包顶层类型均不重名，渲染器也不引用其中任何一个。因此移动后没有任何简单名会解析到别的成员。
- **与第 15.3 节的差异（有意）：**
  - 放在同包而不是 `render/` 子包：渲染器依赖大量包内可见的类型（如 `LyricUiConfig`），移进子包就得把它们改成 public，这不再是纯移动；分包按 D15 留到 E-8。
  - 收窄为显式 `RendererHost`：会改动方法体，按“移动与修改不同提交”的规则，另开一个提交做。
- **证据：**
  - `scripts/guard/method-bodies.py --base 634a9d0`：2,598 个方法未变，124 个移动且方法体一致，0 个改动，0 个删除。
  - Hook ID、日志事件、Hook 安装顺序三份基线未变。
  - 12 个源码契约测试的断言未改，只把定位表的 RENDERER 改为“新文件 + 入口类”，SYSTEMUI_RUNTIME 与 CONFIG_CONSUMERS 加入新文件。
  - 入口类预算按守卫要求下调：17,989 → 14,207 行，内嵌类型 13 → 12 个，4,168 → 387 行；字段与方法数不变。
  - `scope.list`、`module.prop`、`java_init.list` 未改；`verify-build-identity` 通过（353 个输入）。
  - 本地 1056 tests / 0 fail / 6 skip，Debug 与 R8 通过。
- **候选：** `artifacts/e1-renderer-20261115/Bridge-E1-renderer-debug-r1.apk`（`d12646fa…`）。安装后需要重启 SystemUI 才会加载新的 Hook 代码。

**设备冒烟（第 15.3 节 E-1 行，需用户执行）：**
1. 逐字歌词：当前行逐字填充正常。
2. 逐行歌词：开启“普通逐行歌词进度”时进度正常。
3. 字符上浮：开启后效果与以前一致。
4. 发光：光晕颜色、跟随封面正常。
5. 译文：翻译行与翻译进度正常。
6. AOD 低帧率：熄屏显示下歌词正常，亮屏与 AOD 来回切换不闪。
7. 状态页：“歌词自绘”“逐字歌词进度”等显示已执行。

**设备结果：用户重启 SystemUI 后确认 1–7 项全部通过（2026-11-15）。**

### 15.9 未覆盖

- **Providers 仓库**的结构与性能没有做同等盘点。若要纳入“完整优化”，需要另做一次基线（13 个专用模块的 Hooker、`provider-core`、Universal），本节不对其作任何结论。
- 第 15.1 节的数字来自文本扫描，不是编译器 AST；E-0 的预算守卫应以实际实现的计数方式为准重新取值。
- 第 15.4 节没有任何新的设备数据；所有候选项的收益未知。
- 各刀“预计减少”是按行号区间的估算，没有做逐方法的依赖闭包分析；E-5 至 E-7 之间的边界在实施前需要再细化一次。
