# Bridge / Providers 4.0 Phase 7：RC 与正式发布计划

> 状态：**Slice 7A–7F 与 RC5 真机验收已完成；发布前包名兼容策略使 Bridge APK 发生变化，RC6 待构建/验证；未创建 tag 或 Release**
>
> 目标：Bridge `4.0.0` 与 12 个 v5 Provider 形成同一批次、可复现、可回滚说明完整的正式交付物。
>
> 涉及仓库：`ColorOS-Live-Lyrics-Bridge`、`ColorOS-Live-Lyrics-Providers`、`LSPRepo`。
>
> 冻结边界：Phase 7 只接受 RC blocker、发布基础设施、版本/文档/迁移修正；不再顺手加入新功能或视觉实验。

## 1. 发布原则

1. Bridge 与 Provider 在运行时继续完全解耦，但 `4.0.0` 首发必须作为一个协调批次交付，避免用户拿 4.0 Bridge 搭配旧 v4 Provider。
2. RC 和 GA 必须来自相同的可追溯源码提交；任何进入 APK 的修复都要生成新 RC，不允许拿旧测试包口头视为最终包。
3. 真机验证对象必须是发布流水线生成的候选 APK，而不是 Phase 4/5/6 的历史 debug APK。
4. 12 个 Provider 只走 Root / LSPosed；不重新引入 NPatch、Bridge 私有广播、词幕挂载或播放器进程 Bridge adapter。
5. 发布流程 fail closed：签名、资产数量、包名、作用域、版本、Provider commit 或 Release Notes 任一不一致都必须阻止发布。
6. 不把“构建成功”当成“发布完成”；本地/CI、签名与资产、真机、文档、Git/tag/Release 五个门禁分别关闭。

## 2. 2026-08-30 现状扫描：Phase 7 开始前的已知阻塞项

以下是当前源码实扫结果，不是泛化建议：

| 项目 | 当前状态 | Phase 7 要求 |
|---|---|---|
| Bridge 工作树 | 4.0 runtime 已提交为 `a342234`；Unicode test worker 修复为 `025bfc9`；本地生成物已 ignore | Release workflow、README、版本与 Phase 7 文档按后续 slice 独立提交 |
| Provider 工作树 | Unicode test worker 修复 `d6f463b` 与 LX 最终修复 `5618582` 已提交；工作树干净 | 在 RC workflow/版本契约完成前保持本地，不推送和打 tag |
| Bridge Release workflow | `6da07d8` 已重写为 metadata / Bridge / Providers / package / publish 五段拓扑 | 首次远端 RC 必须使用不可变 Provider SHA，验证 secrets、跨仓库 checkout 与完整 16 资产 artifact |
| Provider CI | Bridge 协调流水线已调用 `testV5Matrix`、`assembleV5MatrixRelease`、机器契约和显式 collector | Provider 缺签名变量时配置阶段失败；正式签名正向构建留给远端 RC secrets |
| Bridge 版本 | `00a8333` 已冻结 `4.0.0` / `versionCode=136`，并建立 `bridge-release-contract.json` | 7C workflow 必须从契约解析 tag、LSP tag、资产名和数量，不再复制常量 |
| Provider 版本 | `cb57ce6` 已冻结独立内部版本、12 applicationId/scope/宿主基线、`providers-v1.0.0` source tag 与套件资产名 | 7C workflow 必须先运行机器契约校验，再构建和收集 APK |
| Bridge 测试 | `file.encoding=COMPAT` 修复 Windows Unicode `@argfile` 后，标准 Gradle 70 suite / 477 tests 通过，6 项显式 skipped；标准 assembleDebug 通过 | Release CI 直接使用标准任务，不再保留 direct-JUnit classpath 旁路 |
| LSPosed metadata | Bridge 内重复的 `.github/lsposed` 镜像已删除；APK scope 仍只有两项；独立 `LSPRepo/SCOPE` 仍包含 Salt/Cone 三个播放器包 | 正式发布前只更新 LSPRepo，否则仓库推荐作用域会把 4.0 Bridge 重新注入播放器进程 |
| 面向用户文档 | 根 README 已在向 4.0 迁移但仍待 7F 统一；公开 LSPosed 文档只由 LSPRepo 持有 | 统一 README 中英文、LSPRepo README/SUMMARY、安装/迁移/排错和支持矩阵 |
| 发布流程文档 | `docs/RELEASE_PROCESS.md` 仍以旧 `LyricProvider master` 为发布源 | 改成新 Provider 仓库、不可变 commit/tag、RC dry-run、LSP metadata 先行和 12 APK 资产核验 |

Slice 7A 证据与哈希见 `PHASE-7-RC-BASELINE.md`。其余项目均属于 Phase 7 本身；
在它们关闭前不能创建 `v4.0.0` 正式 tag。

## 3. 已确认的发布决策

### D1. Provider 版本语义

采用“**套件发布版本 + Provider 自身版本**”双层语义：

- Bridge/整套 Release：`4.0.0`；
- 新 Provider 首发：保留各 APK 自己的 `versionName` / 单调 `versionCode`；
- Release 资产文件名统一带 `v4.0.0`，资产 manifest 另外记录每个 APK 的内部版本。

Bridge 使用 `4.0.0` / `versionCode=136`。Provider 不强行跟随 Bridge 大版本；RC1 前只核对并冻结各自单调 versionCode，不把 12 个 APK 的内部版本批量改成 `4.0.0`。


### D2. Provider 资产命名

停止使用容易与旧词幕项目混淆的 `LyricProvider-*`，统一为：

```text
ColorOS-Live-Lyrics-Provider-Salt-v4.0.0.apk
ColorOS-Live-Lyrics-Provider-Cone-v4.0.0.apk
...
ColorOS-Live-Lyrics-Providers-v4.0.0.zip
```

内部 module 名只写入资产 manifest，不直接暴露为杂乱的 Gradle 默认文件名。


### D3. RC 分发方式

RC 使用手动 Actions workflow 的受控 artifact，不创建公开 GitHub Release、不创建 LSP tag。只有最终候选通过后才进入 GA。

### D4. Provider 源码发布位置

Provider 仓库保留自己的源码 tag（首发采用 `providers-v1.0.0`），但 APK 统一附在 Bridge `v4.0.0` 与 LSP mirror Release 中；不再在两个不同产品 Release 页面各自维护一套 APK 正文和资产。

### D5. RC 回归深度

12 个 APK 对应的所有宿主 profile 都至少用最终 RC 做一次冒烟；其中多宿主/多运行 profile 不能只测一个代表：Cone/GP、LX/Walnut、KuGou/概念版、NetEase 官方/Honor/9.0.40 均要分别留结果。

## 4. Slice 7A：冻结源码与建立可追溯基线

### 4.1 Bridge 收口

1. 盘点全部 modified/deleted/untracked 项，按 Phase 5 清理、Phase 6 重构、视觉设置、发布前 bugfix 和文档分组。
2. 确认所有新 Java/测试/文档都被 Git 跟踪，所有应删除的 v4 文件以删除状态进入提交。
3. 排除并补充 ignore：独立 build directory、`artifacts/` 测试 APK、`hs_err_pid*`、`replay_pid*`、本地日志和临时 Gradle init 文件。
4. 执行 `git diff --check`，检查异常 CRLF/整文件改写和误删；不覆盖用户现有修改。
5. 为最终 Bridge 候选记录 branch、commit、submodule/依赖版本、JDK/Gradle/AGP/compileSdk。

### 4.2 Provider 收口

1. 将 LX 最终修复及测试纳入正式提交，确认它就是用户最后设备通过的实现。
2. 再跑一次 Provider 仓库静态清理边界：恰好 12 个 application module，无 v4 sender、NPatch、可安装 Lyricon Provider 和被删除旧 module。
3. 记录 12 个 module、applicationId、宿主 scope、内部版本、图标来源和设备验收基线。
4. 冻结不可变 Provider commit；RC workflow 必须 checkout 该 SHA 或 tag，不能隐式取会漂移的 `master` / `4.0` HEAD。

### 4.3 分支与提交策略

1. RC 开始后进入 feature freeze，只允许 blocker 修复。
2. 每个 blocker 修复单独提交，并在 RC 台账关联测试、APK SHA-256 和设备结果。
3. 决定 `4.0` 分支如何进入公开默认分支；正式 tag 必须落在远端可访问、完整包含源码的 commit 上。
4. 发布前工作树必须干净；用户本地生成物可以保留在 ignore 中，但不能成为构建输入。

## 5. Slice 7B：版本、包名、scope 与升级契约

1. Bridge 设定最终 `versionName=4.0.0` 和递增 `versionCode`，并给版本解析增加 tag/Gradle property 一致性门禁。
2. 冻结 12 个 Provider 的内部 versionName/versionCode 策略；同一 applicationId 的 versionCode 必须严格递增，不能让 RC 安装后无法覆盖到 GA。
3. 生成机器可读的发布清单，至少包含：
   - module；
   - applicationId；
   - host package / process；
   - versionName / versionCode；
   - minSdk / targetSdk；
   - Xposed API 与 scope；
   - APK 文件名、size、SHA-256、签名证书 SHA-256；
   - Bridge commit 与 Provider commit。
4. Bridge scope 的两个正式所有者一致：APK `scope.list` 与独立 `LSPRepo/SCOPE` 均只保留 `system` 和 `com.android.systemui`；Bridge `.github/lsposed` 重复镜像已在 7C 删除。
5. Provider scope 必须只包含各自宿主/进程所需包，不把 Bridge、SystemUI 或其他播放器包塞入错误模块。
6. 对 3.8.1 → 4.0.0 设置迁移做专项测试：schema v3、逐播放器翻译状态、歌词清理默认关闭、调试域、整包配置备份/恢复。
7. 明确降级边界：4.0 配置不保证旧 codec 无损读取；文档要求降级前先备份，必要时重置旧版设置。

### 5.1 Slice 7B 验证状态

- Bridge 提交 `00a8333`：`4.0.0` / `versionCode=136`、发布契约与跨仓库校验脚本。
- Provider 提交 `cb57ce6`：12 模块机器矩阵与源码一致性校验脚本。
- Provider 继续使用独立内部版本：Salt `1.0.0` / code 5，其余首发模块
  `1.0.0` / code 1；套件资产统一使用 `v4.0.0`。
- 跨仓库校验通过：12 个唯一 module/applicationId/asset，Gradle 根矩阵、
  `settings.gradle.kts`、每个 build file、manifest、scope array、process evidence 和
  validated host 均一致。
- Bridge 标准 Gradle 复跑：477 tests，0 failure/error，6 skipped；
  `assembleDebug` 通过。
- 生成 APK 经 `aapt2 dump badging` 确认：
  `io.github.andrealtb.lockscreenlyrics`、`versionCode=136`、`versionName=4.0.0`。
- Slice 7B debug APK SHA-256：
  `CF4B85ECFAF9EB21D664ABB22EC4BC99F765AC212CD28790094789F1AD483BBE`。

## 6. Slice 7C：重写 4.0 RC / Release 流水线

### 6.1 构建拓扑

建议保留三个职责清晰的 job：

```text
metadata / contract
        ├── Bridge test + lint + signed release APK
        └── Provider testV5Matrix + signed 12 APK matrix
                         ↓
              verify / bundle / publish
```

具体要求：

1. 删除旧 `Andrea-lyz/LyricProvider` checkout 和所有旧 module job。
2. checkout `Andrea-lyz/ColorOS-Live-Lyrics-Providers`，强制传入不可变 `providers_ref`；记录解析后的完整 SHA。
3. Provider 只运行根任务 `testV5Matrix` 与 `assembleV5MatrixRelease`，并从 12 个显式路径收集 APK；不要用无约束 `find . -name '*.apk'` 混入测试或历史产物。
4. Bridge 运行标准测试、lint、metadata 校验与 `assembleRelease`；缺少正式签名变量时配置阶段直接失败。
5. Bridge 与 Provider 可以并行构建，最终 verify job 统一重命名、核验、打包和发布，避免 12 份重复 checkout/Gradle 冷启动。

### 6.2 RC 与 GA 两种模式

1. `workflow_dispatch` 默认 `mode=rc`：只上传 Actions artifacts，不写 GitHub Release，不写 LSPRepo。
2. `mode=release` 只允许来自 `v4.0.0` tag，且 tag 中的版本必须与 Gradle、Release Notes 文件和 LSP tag 一致。
3. RC artifact 名包含 RC 序号、Bridge SHA 短值和 Provider SHA 短值，避免测试者拿错包。
4. 任一源码变化都必须递增 RC 序号并重新生成整套候选；不得只替换单 APK 后沿用旧整包哈希。

### 6.3 资产验证

流水线在发布前必须自动完成：

1. APK 数量恰好为 13：1 个 Bridge + 12 个 Provider。
2. 用 `aapt2 dump badging` 或等价工具核对 applicationId、versionName、versionCode、SDK。
3. `apksigner verify --verbose --print-certs` 全部通过，且所有正式 APK 的证书指纹符合预期。
4. `zipalign -c -P 16 4` 全部通过。
5. Provider ZIP 恰好含 12 个顶层 APK，无目录嵌套、无 debug APK、无重复包名、无旧 `io.github.proify.lyricon.*provider`。
6. 生成 `SHA256SUMS` 与 JSON/TSV asset manifest；两者也作为 Release 资产上传。
7. 下载刚上传的 Actions artifact 重新计算哈希，避免“构建目录正确、上传包错误”。
8. 发布 job 校验 Release 资产白名单，不允许旧 `LyricProvider-All-*` 或遗漏 Provider 悄悄通过。

### 6.4 流水线自身验证

1. `actionlint` / YAML 解析通过。
2. 用 RC mode 至少完整跑一次远端流水线，确认 secrets、跨仓库 checkout 权限、Gradle cache、artifact 下载与扁平打包均有效。
3. 模拟缺一个 Provider、重复包名、错误签名、错误 version、错误 Provider ref，确认均 fail closed。
4. 清理 keystore 和临时目录用 `if: always()`，日志不得输出密码、证书私钥或 Provider 网络凭据。

### 6.5 Slice 7C 本地验证状态

- Bridge 提交 `6da07d8`：RC/GA 五段 workflow、完整资产验证器、debug CI 更新、
  单一 LSPRepo metadata 所有权。
- Provider 提交 `99831e8` / `9908293` / `c8f50c3`：显式 release collector、
  缺签名配置阶段失败、契约校验继续 fail closed。
- RC mode 只上传 16 项私有 complete artifact；publish job 只允许 tag 事件，且拒绝
  覆盖任何已存在的公开 Release。
- Provider ref 只接受完整 40 位 SHA 或精确指向 HEAD 的 tag；GA 额外要求
  `providers-v1.0.0` 指向该 commit。
- v3.8.1 公开 Bridge APK 的正式证书 SHA-256 已核对并冻结为
  `ff38544ba21922c35989097fe6f2ad0bda434b4d0bb611e016d51d7862d36195`。
- 本地用 13 个 debug APK 做负向打包测试：包名/版本检查通过后，在第一项证书检查
  精确失败，证明 debug/错误签名不能进入 RC bundle。
- Provider 无签名变量的 `assembleV5MatrixRelease --dry-run` 在配置阶段按预期失败。
- PowerShell 四个契约/collector/asset 脚本语法通过；跨仓库契约通过。
- 官方固定版 actionlint `v1.7.12`（下载 SHA-256
  `6e7241b51e6817ea6a047693d8e6fed13b31819c9a0dd6c5a726e1592d22f6e9`）
  校验两个 workflow，0 error。
- `lintDebug` 首次暴露 14 个 error；未创建 lint baseline，已用 `13bb491` 修复 API
  门禁、vendor key 注解、正确字体/Slider/LineBreaker 常量与分 API theme；
  `lintDebug`、`lintRelease` 随后均通过。
- 正式签名正向构建、artifact 下载重验与 GitHub secrets 权限已由 RC5 关闭，详见
  6.6；公开 publish job 仍保持未执行。

### 6.6 远端 RC 收口

RC workflow 按 fail-closed 方式真实迭代，没有跳过失败步骤：

1. RC1 `33299914214`：metadata/contract 通过；Bridge 暴露 README 尚未提交及 pivot
   测试依赖工作区外反编译文件；Provider 暴露 KavaRef `AnnotatedType` R8 规则只散落
   在部分 module。修复为仓库内 evidence fixture 与 core consumer rules。
2. RC2 `33300988850`：Bridge、Provider 签名构建均通过；package job 使用 runner 最新
   build-tools 37 时无法按已验证格式解析证书。改为契约固定 build-tools 36.0.0，artifact
   actions 同步更新 Node 24 runtime。
3. RC3 `33301536967`：Bridge 通过；Provider 的 Apple fake 同时生成 property setter 与
   `setTranslation` 方法，反射顺序在干净 CI 不稳定。改名 fake state，focused test 通过。
4. RC4 `33301817663`：人工输入了错误的 Provider full SHA，metadata checkout 即 fail；
   没有构建或产出候选，不计有效 RC。
5. RC5 `33301880289`：全部通过。

RC5 证据：

- Bridge commit：`88d261aea07ac59685563c592776daac7cbf7de1`；
- Provider commit：`b186e792d0fc2d5243c555b3c8118f9dcb156f34`；
- batch：`4.0.0-rc.5-88d261a-b186e79`；
- Bridge 签名构建：2m49s，通过标准测试、release lint、release assemble；
- Provider 签名构建：9m26s，通过 `testV5Matrix`、12 module release/R8、collector；
- package：21s，通过 13 APK 的 DEX 禁用字符串、包名/版本、正式证书、zipalign，
  生成 Provider ZIP、asset manifest 和 `SHA256SUMS`，最终恰好 16 项资产；
- RC mode 未执行 publish job，没有创建任何公开 tag 或 Release；
- complete artifact 已下载到
  `artifacts/4.0.0-rc.5-88d261a-b186e79-complete/`；
- 下载后独立复算 15 个 checksum target 全部一致，并用本地 build-tools 36 正向重跑
  13 APK / 16 资产验证器，结果通过；
- Bridge APK SHA-256：
  `43304e92f0610cc3b3c448b1de527a8323b0e89dec98f6587be5e68da2c52efb`；
- Provider ZIP SHA-256：
  `a806ae62a7d1c6c22eada39299d6b78ecc3007b7f166b3bfcc746f5d1a6bdd78`。

## 7. Slice 7D：测试体系与 RC 静态门禁

### 7.1 Bridge

1. 解决 Gradle test worker `ClassNotFoundException`；让 Release CI 运行标准 `testDebugUnitTest`，而不是手工拼 classpath。
2. 对当前 8 个 Android/JVM stub 边界测试逐一分类：可纯 JVM 化、需要 Robolectric、需要 instrumentation，或改为源码契约测试；任何排除都要有显式理由和独立任务。
3. 必跑：架构 guard、v4 删除 guard、配置 schema/owner/backup、视觉 alpha/fade、model assembler/index、draw coordinator、scale pivot、翻译按钮、AOD/renderer 核心策略。
4. 运行 `lintDebug` / `lintRelease`，逐项审阅新增 exported component、权限、SharedPreferences/备份、RTL、国际化和 API level 警告。
5. 检查最终 APK：不含 v4 receiver/protocol、Provider applicationId registry、播放器 adapter、调试测试类、完整歌词 fixture 或本地路径。
6. 对比 3.8.1 与 RC 的 APK 大小、DEX 数量、native libraries、权限、activity/provider/receiver/service 和 scope，异常增量必须解释。

### 7.2 Providers

1. `testV5Matrix` 全量通过；每个 player module 和 core/parser/compat helper 都必须在任务图中。
2. `assembleV5MatrixRelease` 恰好生成 12 个正式 APK。
3. 静态扫描最终 DEX/资源/manifest：无 Bridge v4 action/source/sender、无 NPatch marker、无 Lyricon mount、无旧 Provider applicationId、无错误宿主 scope。
4. 保留并核对 `LICENSE`、`NOTICE`、第三方依赖许可和宿主图标来源；发布包不附逆向输入 APK、input SHA 文件或设备日志。
5. 对网络型 Provider 检查日志与缓存隐私：不输出 token/cookie/完整歌词/本地媒体路径；Release 默认 debug 关闭。

### 7.3 共同契约

1. Bridge 不依赖 Provider 包名、版本或 source；Provider 不依赖 Bridge 安装状态。
2. 只安装 Provider 时，ColorOS 能消费标准 `lyricInfo`；只安装 Bridge 时，原生支持播放器仍可被增强。
3. 同时安装时只存在一条 metadata 发布链，不重复歌词、不抢封面、不改坏播放状态。
4. 4.0 Bridge 与旧 v4 Provider 不兼容必须在安装说明和 Release Notes 顶部突出，而不是埋在 FAQ。

### 7.4 Slice 7D 本地验证状态

- Bridge：标准 `testDebugUnitTest` 477 tests，0 failure/error，6 skipped；从零
  `clean → testDebugUnitTest → lintDebug → assembleDebug` 通过；`lintRelease` 通过。
- 首轮 lint 的 14 个 error 未用 baseline 隐藏，均由 `13bb491` 关闭；剩余 43 warning
  已分类审阅：13 项未使用资源、4 项设置预览 draw allocation、依赖版本提示、
  OEM resource reflection、可访问性/RTL/图标提示等，不构成当前 runtime/签名/manifest
  blocker，保留给后续常规维护而不在 RC 冻结期扩张重构。
- 干净构建的 4.0 debug APK：8521058 bytes，8 个 DEX、4 个 DexKit native ABI，
  SHA-256 `E89A8678C99DB6DC4643EE52D365F256FA450A921463E1241267C0CBEDD89D61`。
- 与公开 3.8.1 release APK 对照：applicationId、minSdk 26、targetSdk 35、两项权限、
  4 个 native ABI 均未改变；版本由 135/3.8.1 升至 136/4.0.0。体积从 7258768
  增至 8521058 bytes，来自 4.0 设置/诊断/renderer/runtime 新实现；未新增 native 库。
- 4.0 packaged manifest 只有 1 个自有配置 provider、6 个自有设置 activity；另有
  AndroidX Startup provider / ProfileInstallReceiver。不存在 Bridge v4 receiver/service。
- `142bb0c` 将 23 个旧 v4 action/class/source、旧 Provider applicationId、NPatch、
  Lyricon mount、测试类和本机路径片段加入最终 APK DEX 禁用字符串门禁。
- 13 个当前 debug APK 已实际经过该 DEX 扫描，全部通过；随后按预期在正式证书检查
  拒绝，证明静态扫描位于签名门禁之前。正式签名 APK 的正向结果留给远端 RC1。

## 8. Slice 7E：最终 RC 真机回归

### 8.1 使用最终候选包

1. 从 Actions 下载同一 RC batch 的 13 个已签名 APK 和 Provider ZIP，先核对 `SHA256SUMS`。
2. 记录设备型号、系统版本、SystemUIPlugin 版本、LSPosed 版本、播放器版本、Bridge/Provider 内部版本和 RC batch ID。
3. 测试中发现 blocker 后停止把当前 batch 当最终候选；修复后从新 commit 重建整套 RC。

本次通过的最终候选固定为 RC5：

```text
artifacts/4.0.0-rc.5-88d261a-b186e79-complete/
```

不得混入 Phase 6 debug APK、RC1–RC4 或单独替换的 Provider APK。

用户于 2026-08-30 确认该批次测试通过。Actions run 为 `33301880289`；后续若修改任何
进入 APK 的源码，必须重新生成 RC，不得沿用本结论。

### 8.2 Bridge 公共场景

每个关键播放器至少覆盖：

- 首次播放、暂停/恢复、seek、连续切歌、同曲重播；
- 锁屏进入/退出、熄屏/AOD、解锁再进入；
- 行级与逐字歌词、翻译有/无、长句换行、长列表滚动；
- 左/中/右对齐缩放 pivot；
- 翻译按钮即时亮/暗和跨播放器图标颜色；
- 亮度层级、上下边缘渐隐、非活动行淡化、四预设、保存/恢复默认；
- 3.8.1 升级保留设置、4.0 整包备份/恢复、重启 SystemUI 后配置仍生效；
- Debug 默认关闭无高频日志，开启后 Provider → SystemUI → renderer 可关联且无敏感明文。

### 8.3 Provider / 宿主矩阵

| Provider APK | 必测宿主 profile | 重点 |
|---|---|---|
| Salt | Salt 12.3 基线 | CJK/逐字、车载 metadata、公开翻译 action |
| Cone | 正式版 + GP | 双包隔离、多来源去重、公开翻译 action |
| KuWo | KuWo 12.2 | 官方 payload 追加、封面、暂停/seek、同曲模型 |
| LX | 官方 LX + Walnut | 蓝牙投影、封面、buffering/旧 composite identity、连续切歌 |
| Poweramp | Poweramp 1025 | sidecar/embedded、两阶段封面、暂停后切歌翻译按钮 |
| Metrolist | 13.6.1 | 多歌词源、无翻译、pending artwork |
| KuGou | 标准版 + 概念版 | support-only、车载身份、不改 PlaybackState、5 槽按钮 |
| QQ | 标准版 | `:QQPlayerService`、QRC、翻译 alias、无 QQ HD |
| NetEase | 官方 9.5.70 + Honor 3.5.20 + 9.0.40 | official append/constructed 三 profile、generation 防串歌 |
| Apple | 6.5.2 | TTML、切歌身份、URI artwork、拉丁音节合并 |
| Spotify | 9.1.78 | 非缓存首次取词、LINE/WORD、404→回歌、无翻译 |
| QiShui | 20.7 | VIP 时钟、TrackLyric/cache fallback、逐字/翻译 action |

### 8.4 安装与冲突回归

1. Bridge 3.8.1 正式签名 → 4.0 RC 正式签名可覆盖升级。
2. 新 Provider applicationId 与旧 Provider 可并存安装，但文档要求旧模块卸载或取消对应宿主 scope；验证双 hook 风险提示准确。
3. 首次安装/改变 scope 后的重启说明真实有效。
4. Provider 未安装时，Bridge 设置页不再通过 Provider package 查询伪造“已安装/未安装”状态。
5. 对不再支持的 QQ HD、MusicFree、Gramophone、Symfonium 明确显示为“不在 4.0 Provider 支持矩阵”；Halcyon、Flamingo、QZ Music 与 PrismMusic 仅提供纯包名 SystemUI 兼容，必须由播放器主动发布标准 `lyricInfo`，不能写成旧 v4 source 仍受支持。

### 8.5 Slice 7E 结论

- RC5 批次 `4.0.0-rc.5-88d261a-b186e79` 已由用户完成真机验收并确认通过。
- Bridge 与 12 Provider 的正式签名候选不再存在已知 blocker/critical 设备问题。
- 该结论只绑定上述 batch 与 Actions run `33301880289`，不外推到后续源码变更。
- 9.5 的四播放器纯包名策略属于后续 Bridge 源码变更，因此 RC5 已降为历史设备基线；
  当前最终候选必须由 RC6 重新建立。

## 9. Slice 7F：迁移文档、Release Notes 与支持材料

### 9.1 必须更新的入口

1. Bridge `README.md` / `README.zh-CN.md`。另重写中英`播放器主动接入协议`，提供更详细准确的接入适配技术文档。
2. Bridge `.github/lsposed` 重复镜像已删除；正式 LSPosed metadata 只维护独立 `LSPRepo`。
3. 独立 `LSPRepo/README.md`、`SUMMARY`、`SCOPE`。
4. Provider `README.md` / `README-English.md` 与 `docs/4.0/README.md`。另需新增适配技术文档（中英），为后续新增播放器Provider约定主要技术线路，在readme中简单介绍并设置跳转按钮。
5. `docs/RELEASE_PROCESS.md`。
6. 新增 3.8.x → 4.0 迁移说明和最终 12 Provider 支持/宿主版本矩阵。

### 9.2 迁移说明必须回答

1. 为什么 4.0 Bridge 不再兼容旧 v4 Provider。
2. 旧 Provider applicationId → 新 Provider applicationId / APK 文件名的完整映射。
3. 为什么旧、新 Provider 可以同时安装但不能同时 hook 同一播放器。
4. 如何在 LSPosed 中正确设置 Bridge 两项 scope 和每个 Provider 的播放器 scope。
5. 词幕功能不再随本项目发布；需要词幕时前往词幕官方获取 Provider，词幕本身的问题向词幕官方反馈，不在本仓库受理。
6. README 与 Release Notes 只写当前 Root / LSPosed 安装要求，不解释或提及内部试错过的 NPatch/non-root 路线。
7. Bridge 和 Provider 可独立安装时各自能做什么：例如只安装 Provider 也可让 SystemUI 原生消费标准 `lyricInfo`，呈现官方样式的锁屏岛歌词；“同批发布”是升级协调要求，不是运行时私有依赖。
8. 如何备份/恢复 Bridge 全部配置，如何处理 4.0 → 3.8.x 降级。
9. 如何提交有效 issue：版本、scope、复现步骤、结构化日志；不得上传 token 或完整私人歌词。

### 9.3 `4.0.0` Release Notes 结构

正文至少包含：

1. 4.0 架构变化与 breaking change 摘要；
2. Bridge Phase 3/5/6、歌词亮度/渐隐、配置备份、翻译按钮和对齐 pivot 的用户可见变化；
3. 12 Provider 与所有宿主 profile、翻译能力和已知限制；
4. 从 3.8.x 的卸载/安装/scope/重启步骤；
5. 测试与设备验证范围；
6. 已知限制：ColorOS/SystemUI 私有实现、播放器更新易漂移、Spotify/Metrolist 无翻译、QQ HD 不支持；
7. 资产清单与 Provider ZIP 不是 Recovery 包的说明；
8. Bridge/Provider 源码 commit、Actions run、SHA256SUMS；
9. 完整致谢、许可证和 donation 区块。

### 9.4 Slice 7F 完成记录

2026-08-30 已完成：

1. 重写 Bridge 中英文 README 与中英文播放器主动接入协议；协议按当前
   `MediaMetadata["lyricInfo"]`、时间轴、翻译 lane、generation、封面和公开翻译 action
   契约编写。
2. 新增中英文 3.8.x → 4.0 迁移指南，覆盖旧→新 Provider applicationId/资产映射、
   Bridge/Provider scope、双 hook 风险、配置备份与降级、Provider-only 行为，以及词幕原
   项目获取和反馈边界。
3. 新增 Provider 中英文适配技术指南，并从 Provider 中英文 README 与 4.0 文档入口链接。
4. 重写发布流程，改用新 Provider 仓库、不可变 source revision、精确 16 资产门禁与
   LSP metadata 先行顺序；明确 RC/文档/推送不等于授权正式发布。
5. 新增完整 `4.0.0` Release Notes 和版本归档页，并更新独立 `LSPRepo` 的中英文 README、
   `SUMMARY` 与两项 `SCOPE`。
6. 两仓库 release-contract 脚本加入文档存在性、入口链接和公开文档禁用词门禁；Bridge
   增加 Phase 7 文档契约单测。

本 slice 只提交并推送上述文件。未创建 Provider source tag、LSP metadata tag、Bridge
`v4.0.0` tag 或 GitHub/LSP Release；这些动作仍属于 Slice 7G，必须取得用户明确授权。

### 9.5 发布前旧 v4 PR 收口与 RC6 门禁

发布前处理 PR #42（QZ Music）与 PR #37（PrismMusic）时，只保留有设备/宿主依据的纯包名
SystemUI 兼容价值，不合并 `ExternalLyricProviderRegistry`、source/sender 放行、title-only
外部提升或播放器专属收藏按钮覆盖：

- `com.ella.music`、`yos.music.player`、`love.qz.music`、`com.lg.sllocalmusic` 加入
  `PlayerSystemUiPolicy` 的歌词入口、媒体历史与 AOD 包名策略；
- 四者不加入 12 Provider 资产矩阵，也不加入播放器专属翻译收藏覆盖；
- 播放器作者按主动接入协议在自己的 MediaSession 发布完整标准 `lyricInfo`，需要公共翻译
  按钮时发布 `ACTION_TOGGLE_TRANSLATION`；
- 两个旧 PR 以“4.0 架构替代旧实现”说明关闭，不合并、不 retarget 到 4.0。

该策略变更进入 Bridge APK，因此已通过真机的 RC5 不再是当前源码的最终候选。必须生成
RC6，至少复核四个包名的 SystemUI 放行不影响现有 12 Provider，并在可用新版播放器上
验证原生 `lyricInfo`；不得沿用 RC5 的最终候选结论直接发布。

## 10. Slice 7G：正式发布顺序与发布后核验

### 10.1 发布前锁定

1. 用户确认最终 RC 通过，RC 台账没有 blocker / critical open item。
2. Bridge 与 Provider 工作树干净，远端目标 commit 可见；Release Notes 已审阅。
3. Provider 源码 tag（若采用 D4 建议）先推送并验证指向最终 Provider commit。
4. Bridge `4.0` 合入目标公开分支后，先跑普通 debug CI，确认全新 clone 可构建。

### 10.2 LSP metadata 先行

1. 更新 LSPRepo `README.md` / `SCOPE` / `SUMMARY`，提交 `Update metadata for v4.0.0`。
2. 创建 `<versionCode>-4.0.0` tag 指向该 metadata commit并推送。
3. 远端核对 tag 指向、两项 scope 与版本说明；此步骤不能由 Release asset 上传替代。

### 10.3 Bridge GA

1. 在最终 Bridge commit 创建并推送 `v4.0.0`。
2. workflow 解析并锁定 Bridge tag + Provider commit，构建 13 APK、Provider ZIP、`SHA256SUMS` 和 asset manifest。
3. publish job 同时更新 Bridge GitHub Release 与 LSP mirror Release，正文和资产必须一致。
4. 预期资产白名单建议为 16 项：1 Bridge APK + 12 Provider APK + 1 Provider ZIP + `SHA256SUMS` + 1 asset manifest。

### 10.4 发布后独立复核

1. 从两个公开 Release 重新下载全部资产，逐个比对 SHA-256、签名、包名、内部版本和 ZIP 内容。
2. 核对 GitHub `latest`、Bridge tag、Provider source tag、LSP tag、Actions run 和 release body 链接。
3. 用公开下载的 Bridge + 至少一个 Provider 做最后安装冒烟，确认公开资产不是错误上传版本。
4. 记录最终 Bridge commit、Provider commit、LSP metadata commit、三个 tag、Actions run、资产清单与设备结论。
5. 发布后发现问题不移动 `v4.0.0` tag、不静默替换 APK；普通修复走 `4.0.1`，严重签名/安全/bootloop 问题按撤回说明处理。

## 11. RC 台账字段

每个 RC 至少记录：

| 字段 | 内容 |
|---|---|
| Batch | `4.0.0-rc.N` |
| Bridge source | branch + full commit |
| Provider source | branch/tag + full commit |
| Workflow | run URL / run id |
| Assets | 13 APK + ZIP + manifest + SHA256SUMS |
| Static gates | Bridge tests/lint/metadata；Provider tests/matrix；签名/zipalign/asset count |
| Device gates | 设备/SystemUI/LSPosed/宿主版本及逐 profile 结果 |
| Open blockers | 严重度、负责人、复现证据、目标 RC |
| Superseded by | 新 RC batch；旧 RC 明确作废 |

## 12. Phase 7 完成定义

只有同时满足以下条件，才能宣布 4.0 正式发布完成：

1. Bridge 与 Provider 源码均已提交、推送、可从干净 clone 重建。
2. Release workflow 不再引用旧 `LyricProvider` 或旧 module，且精确构建 12 个 v5 Provider。
3. 标准 Bridge Gradle 测试门禁恢复；Provider `testV5Matrix` 与两个仓库 release 构建通过。
4. 13 个 APK 的包名、内部版本、scope、签名、zipalign、哈希和资产数量全部自动核验。
5. 最终 RC 的 Bridge 公共场景和全部 Provider/宿主 profile 已有真机结论。
6. 3.8.1 升级、配置备份/恢复、旧新 Provider scope 冲突和降级边界已验证并写入文档。
7. README、LSPosed metadata、LSPRepo、迁移说明、Release Notes、LICENSE/NOTICE 一致。
8. LSP metadata tag 先于 Bridge tag 创建并验证；Bridge 与 LSP 两个公开 Release 资产完全一致。
9. 公共 Release 重新下载复核通过，并留下完整 commit/tag/run/hash 台账。
10. 无 blocker/critical 未关闭项；Phase 7 台账标记完成。

## 13. Phase 7 明确不做

1. 不在 RC 期间增加新播放器、新歌词源、新视觉特效或新的 Provider 私有协议。
2. 不为了兼容旧 Provider 恢复 Bridge v4 receiver/registry/adapter。
3. 不重新发布词幕 Provider，不加入 NPatch/embedded 资产。
4. 不把历史 debug APK、设备日志、逆向输入 APK、崩溃转储或本地缓存上传到 Release。
5. 不用浮动 Provider 分支构建正式 Release。
6. 不因流水线已绿而跳过最终签名包真机验证。
7. 不移动已发布 tag，不静默替换公开 APK。
