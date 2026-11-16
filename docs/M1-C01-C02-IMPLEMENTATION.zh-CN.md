# M1 C-01 复核与 C-02 首批实施记录

> 历史批次记录：下文保留此前局部实现与验证结论。当前续实施和实机验收入口以 [C-01/C-02/C-03 续实施记录](<M1-C01-C03-DEVICE-READY.zh-CN.md>) 为准。

日期：2026-11-13。使用技能：lsposed-mod-dev（工作流/质量、特殊进程边界、验证清单），Kotlin API 设计按实际模块边界使用。

## 1. 结论与范围

**上轮“C-01 已完成”的结论过早。按下一大版本计划的完整范围，C-01 仍不能标全项完成。** 本轮修复构建身份可靠性，补齐 Bridge 的安装/注入构建区分与后台环境快照，覆盖 Provider 构建矩阵，并继续了 C-02 的不依赖剩余设备取证部分。

本轮只进行本地源码、构建与测试；没有连接/操作手机，没有安装、强停或清理应用，没有重启 SystemUI，没有提交、推送或发布。scope 探针已按用户要求停止，沿用用户“允许/拒绝/不再询问正常”的设备确认，不重开试验。

## 2. C-01 原实现复核

| 项目 | 复核发现 | 本轮结果 |
| --- | --- | --- |
| dirty 摘要 | 旧做法只哈希 git status/diff，未跟踪源码仅留下文件名；修改其内容可能不变。上轮独立复算也未一致 | 改为实际生产源码/资源/构建输入字节的 SHA-256 清单；独立 Node 校验通过 |
| 无 Git/命令失败 | 旧做法忽略退出码，空输出可能成为“clean” | commit/state 明确 unknown；归档不能继承父目录 Git 身份 |
| buildId/变体/resolver | 旧实现只有三项 SOURCE 字段 | 同一构建约定生成完整 buildId、variant、source state/digest、resolver revision；发布 tag 输入纳入摘要 |
| 覆盖范围 | 旧实现只有 Bridge | Bridge + 矩阵 14 个歌词 Provider + 独立动态封面均生成元数据；15 个 Provider APK 实包验证通过 |
| 安装 vs 注入 | 旧页面只显示设置 App 的 PackageInfo | App 后台读取已安装 APK 内的身份；受保护状态回包返回注入代码烘焙身份、进程 epoch、框架/API/properties、已应用外观 revision；对照未知/一致/不一致分开 |
| 环境信息 | 原有型号、Build.DISPLAY、SDK 与 SystemUI versionName 不足；将 SDK 37 写成 Android 37 也不准确 | 分别记录 Android release/SDK、OS build、型号、缩放、逻辑分辨率、导航原始值；SystemUI/插件安装包与 splits 内容指纹在 App 后台计算并缓存 |
| Provider 运行观测 | 没有构建身份来源 | 共享入口与 Universal 的现有 MODULE_LOADED 带 buildId；Readify 独立入口也补上；安装侧设置与注入日志分开，不把安装当已加载 |

### 2.1 构建身份实现与校验

- 两仓库的 [源码输入约定](<../gradle/build-identity.gradle.kts>) 和 [应用约定](<../gradle/build-identity-app.gradle.kts>) 保持镜像；[resolver revision](<../gradle/resolver-revision.txt>) 是显式版本标签，本轮不修改 resolver/Hook 策略或业务缓存。
- 使用受控生产输入集合：main/debug/release 源码与资源、Gradle/版本/矩阵/构建脚本。不纳入测试 fixture、文档、个人 artifacts、凭据、缓存和生成目录。摘要描述该输入集合，不声称覆盖所有外部依赖、签名环境或与 APK 二进制等价。
- buildId 由仓库/模块/变体、提交、Git 状态、内容摘要与 resolver revision 构成。APK 自身 SHA-256 仅写入外部验证清单，绝不反写自身。
- 生成 BuildConfig 和 APK 内 META-INF/cll/build-identity.properties；Bridge 的烘焙身份与最终 APK 元数据已比对一致。
- Git 不可用或 source archive 无 .git 时保留 unknown，不冒用父仓库提交；正式 source-freeze 归档的已认证 provenance 接入仍需后续发布链路闭合，不能用手填提交号假装已验证。
- [独立校验器](<../scripts/verify-build-identity.mjs>) 使用 Node 内置模块逐个复算输入文件并重建规范摘要，不依赖 Gradle 的打印值。Bridge 281 个输入、Providers 698 个输入均匹配。
- 隔离 Gradle fixture 实测：未跟踪文件同路径改内容会变更摘要；再改内容且父 Git 状态未变时配置缓存失效；新增文件也被发现。无 Git fixture 均为 unknown，详见 [初始清单](<../../artifacts/m1-compatibility-20261113/identity-fixture-before.json>)、[内容修改](<../../artifacts/m1-compatibility-20261113/identity-fixture-cache-content.json>)、[新增文件](<../../artifacts/m1-compatibility-20261113/identity-fixture-added.json>)。

### 2.2 运行与环境边界

- [BuildIdentity](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/diagnostics/BuildIdentity.java>) 的 APK/组件 I/O 只允许在 [调试页](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/BridgeDebugSettingsActivity.java>) 生命周期拥有的后台 worker 执行，销毁时取消；不进入绘制、轮询或注入启动热路径。
- SystemUI/插件指纹为已安装组件的 base/split 内容集合：各 APK SHA-256 排序后再次 SHA-256；不是“已加载 ClassLoader 指纹”，也不是单文件 APK 原始摘要。缓存容量 16，以安装路径/大小/mtime 为键；不可读或不可见为 unknown，不直接当“不支持”。
- 环境屏幕值来自 App Resources/DisplayMetrics，是当时的逻辑指标，不保证等同物理面板或所有折叠窗口状态；导航值是未经厂商推断的原始设置。
- 完整 ColorOS 版本、折叠姿态不可可靠采集时显式 unknown。AOD 由渲染器正常路径回报实际低帧率模式，不强制进入 AOD。没有 IMEI、设备序列号、歌词、原始 media ID、token/cookie、完整 URL query 或私人媒体路径。
- 外观 revision 与 Debug revision 独立；未观测用 -1。旧进程没有新增回包字段时未知，不因 versionCode 相同而标“已加载新构建”。

### 2.3 C-01 尚未完成的计划项

1. Provider App 侧完整的“注入进程构建/配置已应用”结构化回读尚未闭环；本轮只有自身 bootstrap 日志来源，设置页保持 unknown，不恢复旧私有歌词 transport。Universal 的独立 Compose 管理页本轮没有改造。
2. 已加载 SystemUI 插件/ClassLoader 的指纹与安装组件指纹关联，以及完整 ColorOS/AOD/折叠环境聚合，尚未全部接入。
3. 未进行这批改动的真实设备验收、旧进程/新 APK 的实际升级窗口、不同型号及正式签名/R8 验证。

因此完成状态是：**C-01 主要工程缺口已补齐并本地验证，完整计划验收仍未完成。**

## 3. C-02：统一能力与最终原因首批

[CompatibilityStatus](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/diagnostics/CompatibilityStatus.java>) 提供每功能的 requested、supported、eligible、selectedPath、lastOutcome、reason、observedAt、evidenceScope。前三级含 UNKNOWN，关闭不等于不支持，未观测不等于失败。

已接入：

| 功能 | 真实决策来源 | 不夸大的证据范围 |
| --- | --- | --- |
| 自绘调度 | [OfficialLyricDrawCoordinator](<../app/src/main/java/io/github/andrealtb/lockscreenlyrics/OfficialLyricDrawCoordinator.java>) 的模型缺失、绑定等待、数据集不支持、帧拒绝、原生回退和调度返回 | DISPATCHED 只表示渲染器返回，不能证明屏幕像素 |
| 随封面发光 | 原先就会执行的 coverColor 采样与颜色设置 | PREPARED 为颜色样本就绪；缺色回手动色，不能把取色当实际发光 |
| 字符上浮 | 原有开启/AOD/逐行依赖/异常守卫、几何准备与 lifted 绘制调用 | 区分 PREPARED 和 DRAW_COMMANDS；提交绘制指令仍不是设备视觉验收 |
| 逐行进度 | 原有 isLineTimedProgress 判断 | 仅当前歌词项的条件，不能外推所有数据集 |
| 翻译进度 | 原有 shouldDrawTranslationProgress 判断 | 区分关闭、AOD、逐行进度依赖与尚未观测 |

- 最小原因枚举包含计划中的 CONFIG_DISABLED、RAW_LYRIC_MISSING、LINE_PROGRESS_DISABLED、AOD_LOW_FRAME_RATE、HOST_NOT_FOUND、SNAPSHOT_PENDING、PALETTE_UNAVAILABLE、DATASET_UNSUPPORTED、FRAME_REJECTED、NATIVE_FALLBACK、TARGET_NOT_OBSERVED，另有局部异常原因。定义枚举不等于每个原因都已有实际设备命中证据。
- 查询沿原签名权限保护的模块状态通道，只复制有界状态，**不调用业务解析/取色/重绘**，不为诊断打开业务分支；Debug 开关不参与决策。
- 7 个固定功能槽位，128 个弱所有者缓存；稳定行/帧复用弱引用，不保留 View/模型强引用，不增加逐帧日志。绑定/模型/配置变化清除对应观测，超过 60 秒的旧观测投影为 unknown，查询不会刷新 observedAt。
- [24 项新增身份/运行状态/能力测试](<../app/src/test/java/io/github/andrealtb/lockscreenlyrics/diagnostics/CompatibilityStatusTest.java>) 覆盖旧回包、未知/关闭区别、准备与绘制阶段、回退、依赖、失效和有界性；具体分组结果见下一节。
- 翻译按钮与动态封面尚未接入此统一模型，槽位为 unknown。更细粒度的宿主能力/当前绑定聚合以及性能实测仍待继续；**C-02 也不是全项完成**。

## 4. 实际验证

| 检查 | 结果与证据 |
| --- | --- |
| Bridge JVM + assembleDebug | [最终构建日志](<../../artifacts/m1-compatibility-20261113/bridge-validation-final.log>) 成功；[测试报告](<../app/build/reports/tests/testDebugUnitTest/index.html>) 共 918 项，0 失败，6 项跳过。跳过项不代表已覆盖 |
| 新增 Bridge 测试 | BuildIdentityFormatTest 4、BuildIdentityRuntimeStatusTest 4、CompatibilityStatusTest 16：共 24 项，全部通过，无跳过 |
| Provider 矩阵 + 动态封面 | testV5Matrix / assembleV5MatrixDebug / testArtworkProvider / assembleArtworkProviderDebug [全部成功](<../../artifacts/m1-compatibility-20261113/providers-validation-2.log>)；[汇总](<../../artifacts/m1-compatibility-20261113/provider-test-summary.json>) 875 项，0 失败/错误/跳过（不混入旧探针结果） |
| 最终 Provider APK | [15 份实包验证清单](<../../artifacts/m1-compatibility-20261113/provider-apk-verification.json>)：身份资源、独立 buildId、包名/versionCode 与原 Xposed 入口/scope/元数据相符；不只检查生成源码 |
| 最终 Bridge Debug | 4.5.4 / 149，9,326,084 字节，SHA-256 ADD8E644C663AA4BCD180C574D39E62F64EF870277AD7D1CB07C3B28814EAADB；aapt2/apksigner 与身份一致性通过。Android Debug 证书，绝非正式 RC |
| 源码身份独立复算 | Bridge sourceDigest=92b5a448304b8691e11f77bf22bed42f9746742007ad8f0963f4f3b53479723c；Providers=ccbef875212b7880fc154a072ee7bccdbb612374d2147c1dd7629a6493d3bae3；Git 均 dirty |
| 配置缓存/归档 | [同路径内容变化](<../../artifacts/m1-compatibility-20261113/identity-fixture-cache-content.log>) 与 [新增文件](<../../artifacts/m1-compatibility-20261113/identity-fixture-added.log>) 正确失效；无 Git 不冒充 clean |
| 运行边界 | 没改歌词取词、metadata、PlaybackState、绘制几何、AOD 时序、生产 scope 或发布版本；没有手机操作/安装/发布 |

本轮编译时发现并修复了诊断上浮记录的变量引用问题；最终代码已经重跑相关检查。仍有既有 Gradle 路径实验/弃用提示，不作为设备已通过的证据。上述 build/ 与 artifacts/ 链接为本地生成证据，不入库，干净检出需重建。

## 5. 下一段工作

继续 C-02 的翻译按钮/动态封面实际决策接入与 Provider 自身运行诊断回读，再推进 C-03 离线日志摘要。保留当前 unknown 与证据范围，不先把整个 C-01/C-02 或 M1 勾为完成，不要求重新做已由用户确认的 scope 探针试验。
