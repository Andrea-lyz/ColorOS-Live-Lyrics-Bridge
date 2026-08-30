# Bridge / Providers 4.0 Phase 7 RC 基线

> 快照日期：2026-08-30
>
> 状态：Slice 7A–7F 与 RC5 真机验收已完成；发布前新增四个原生播放器纯包名 SystemUI 兼容策略，当前源码必须重建 RC6；尚未创建正式 tag 或 Release。

## 1. 源码基线

| 仓库 | 分支 | 本地基线 | 状态 |
|---|---|---|---|
| `ColorOS-Live-Lyrics-Bridge` | `4.0` | `88d261a`：RC5 runtime；`3eee743`：RC5 记录 | 7F 文档、文档契约与发布材料完成；正式 tag/Release 留到 7G |
| `ColorOS-Live-Lyrics-Providers` | `4.0` | `b186e79`：RC5 Provider 基线 | 7F 中英文适配技术文档与 README 入口完成；source tag 留到 7G |
| `LSPRepo` | `main` | `d505f18` / `135-3.8.1` | 7F 已准备 4.0 README/SUMMARY 与两项 scope；tag/Release 留到 7G |

Bridge 的 `a342234` 是 Phase 3–6 与后续视觉控制已经互相依赖后的集成基线，包含
198 个文件、16083 行新增和 19187 行删除。发布基础设施、版本号和用户文档不会混进
这个 runtime 提交，后续 blocker 继续使用独立提交。

Provider 的 `5618582` 对应用户最后确认正常的 LX 连续切歌实现，并把
`lyrics-log-20260829-044318.txt` 暴露的 buffering 期旧 composite ARTIST 竞态写入
`PHASE-4-LX-MIGRATION-REPORT.md`。

## 2. 生成物隔离

Bridge `.gitignore` 已明确排除：

- `.phase*-apk-build/`（包括 module 子目录）；
- 根目录 `artifacts/`；
- `hs_err_pid*.log` / `replay_pid*.log`；
- `phase*-build-dir.init.gradle`。

现有本地 Phase 6 APK、独立 build directory 和 JVM 崩溃转储仍保留在磁盘上，未删除，
但已不出现在 Git 候选输入中。Slice 7A 没有清理或覆盖用户本地产物。

## 3. Gradle test worker 阻塞与修复

### 3.1 根因

Bridge 和 Provider 之前的标准 Gradle 测试都不是断言失败，而是 test worker 对所有已
编译测试类报 `ClassNotFoundException`。完整取证确认：

1. `.class` 文件已经生成；同一 JDK 直接 `javap` / `java -cp` 可以加载。
2. Gradle test worker 使用 `@gradle-worker-classpath...txt` 启动，classpath 中包含带中文
   的工作区绝对路径。
3. 用相同 JDK 和最小 UTF-8 `@argfile` 可稳定复现 `ClassNotFoundException`。
4. 将 Gradle JVM 的 `file.encoding` 从 `UTF-8` 改成 JDK `COMPAT` 后，focused test、
   Bridge 全套测试和 Provider 全矩阵均恢复。

Java 官方说明要求 `@argfile` 使用系统默认编码兼容的字符集；Gradle 也有已知的 Windows
非 ASCII 路径 worker 问题：

- <https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html#java-command-line-argument-files>
- <https://github.com/gradle/gradle/issues/29213>

两个仓库均只修改 Gradle daemon 的参数文件编码。Java 源码仍由 Bridge 的
`JavaCompile.options.encoding=UTF-8` 处理；Kotlin 源码编码未改变。

## 4. 标准测试与构建结果

### 4.1 Bridge

执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks
.\gradlew.bat :app:assembleDebug
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\validate-lsposed-metadata.ps1
```

结果：

- 70 个 XML test suite；477 tests，0 failure，0 error，6 skipped；
- 标准 `assembleDebug` 成功，不再依赖 Phase 6 临时 build-dir init script；
- metadata 校验通过，APK scope 为 `system`、`com.android.systemui`；
- APK：8511814 bytes；
- SHA-256：`1CCB8FC14E833E1802D36C3A63CD9B4D933929410DD25821C23678516EB5D677`；
- `apksigner verify`：Android Debug certificate，v2 scheme；
- `zipalign -c -P 16 4`：通过。

### 4.2 Providers

执行：

```powershell
.\gradlew.bat testV5Matrix assembleV5MatrixDebug --rerun-tasks
```

结果：

- 140 个 XML test suite；462 tests，0 failure，0 error，0 skipped；
- `testV5Matrix` 成功；
- `assembleV5MatrixDebug` 成功；
- 642 actionable tasks 全部执行；
- 显式矩阵恰好生成 12 个 debug APK。

| Module | Bytes | SHA-256 |
|---|---:|---|
| `player-salt` | 9040300 | `7D4D3CE2CA47F2F8FDBE11ECDCDBD7A76C5AA6E9C45C3A3C2EFE912D361E4D2B` |
| `player-cone` | 9014738 | `9E2F4F7E5FDA63CB42559E2549A1186CD457F635AC24C5445FA0782D8C1A35B1` |
| `kuwo-music` | 9056403 | `5611208455F8B401343EC2584844CE2CA4BC8E9127C47A64D1209BB61C792D36` |
| `player-lx` | 7192302 | `9FE92C9D90A6642810C2BE05E05C0B6ACC16E947063402E770B71E2D8651EB03` |
| `player-poweramp` | 11782971 | `AC2E16F0CC96E8EB6865D0DF45195F337524DB9E2BBB12D1581F77FA32CDBB97` |
| `player-metrolist` | 7921873 | `88F64F889E57676338768160CF773ED9E032AAE5E7C5B1749E1B0F24B07B446F` |
| `player-kugou` | 9036120 | `7A1173868BEDC11C1F85D4CA7EFA0DA491B13118B4A414292B72D015A5AFC513` |
| `player-qq` | 9000966 | `BE1587DA218AA76F1B28A0A407ED37361B9F1C8596CEF44C550C1F793FAE629B` |
| `player-netease` | 9804524 | `D8ED0D3CAE684C9706F7899A5D62BD95FB84B9565E58455BC8E21C78DF1BE899` |
| `player-apple` | 9016595 | `99A8B522075810EA0D75A4A659B044751F2C6CE657595FEB69772BD179984071` |
| `player-spotify` | 9672360 | `D5E81C5620097185BD1D7632335BC481C420E168109FE042DF2EED9AA89C6535` |
| `player-qishui` | 7214346 | `C5BA36AF12817D01D1D0F2F33254BC73D38295F9637FD3FB6B0F9C7606B5A151` |

## 5. 静态边界

- Provider `settings.gradle.kts` 和根 `v5ProviderModules` 均只含 12 个可安装 v5 module。
- Provider 非文档/非测试 runtime 扫描未发现 v4 sender、NPatch marker、
  `LyriconFactory` / `LyriconProvider` 或 `lyricprovider/` source；命中的三个
  `lyricprovider/` 字符串均是“编码结果不得包含旧 source”的负向测试断言。
- Bridge `BridgeArchitectureGuardTest` 已随标准 Gradle 测试通过；scope 只剩
  `system` / `com.android.systemui`。
- 两仓库 `git diff --check` 无 whitespace error；PowerShell 显示的 LF→CRLF 提示是
  checkout 行尾告警，不是 diff 错误。

## 6. Slice 7F 之后仍未关闭

1. 四个原生播放器包名策略进入 Bridge APK 后，必须重新生成 RC6；RC5 仅保留为变更前
   的设备基线，不能再直接转正式发布。
2. Provider source tag、LSP metadata tag、Bridge 正式 tag 与 GitHub/LSP Release 均未创建。
3. 正式公开资产尚未发布，因此公开下载后的 16 资产重验尚未执行。

## 7. Slice 7D 静态回归摘要

- Bridge 477 tests、`lintDebug`、`lintRelease`、clean `assembleDebug` 通过。
- 干净 4.0 debug APK 为 8521058 bytes / 8 DEX / 4 native ABI，SHA-256
  `E89A8678C99DB6DC4643EE52D365F256FA450A921463E1241267C0CBEDD89D61`。
- 对照 v3.8.1，applicationId、SDK、权限和 native ABI 不变；v4 exported receiver 已从
  packaged manifest 消失，新增组件仅为 4.0 设置子页和现有 AndroidX runtime 组件。
- 最终 package 脚本对 13 APK 执行 DEX ASCII 禁用字符串扫描；当前 debug 矩阵扫描
  通过并在正式证书门禁按预期失败。
- release signing 正向路径与上传后 16 资产重验已由 RC5 关闭；用户已完成并通过 7E 真机验证。

## 8. 私有 RC5 签名候选

- Actions run：`33301880289`。
- Bridge：`88d261aea07ac59685563c592776daac7cbf7de1`。
- Providers：`b186e792d0fc2d5243c555b3c8118f9dcb156f34`。
- batch：`4.0.0-rc.5-88d261a-b186e79`。
- Bridge、12 Provider、package 三段均通过；publish job 因 RC mode 跳过。
- 完整 artifact 16 项；下载后 `SHA256SUMS` 与本地正向 asset verifier 再次通过。
- 本地目录：`artifacts/4.0.0-rc.5-88d261a-b186e79-complete/`。
- Bridge APK SHA-256：
  `43304e92f0610cc3b3c448b1de527a8323b0e89dec98f6587be5e68da2c52efb`。
- Provider ZIP SHA-256：
  `a806ae62a7d1c6c22eada39299d6b78ecc3007b7f166b3bfcc746f5d1a6bdd78`。
- 用户于 2026-08-30 确认该 RC5 batch 真机测试通过。

## 8.1 Slice 7F 文档基线

- Bridge：中英文 README、播放器主动接入协议、3.8.x → 4.0 迁移指南、发布流程、
  `4.0.0` Release Notes 与归档页已完成。
- Providers：中英文 README、4.0 文档入口与中英文 Provider 适配技术指南已完成。
- LSPRepo：中英文 4.0 README、`SUMMARY` 与仅 `system` / `com.android.systemui` 的
  `SCOPE` 已完成。
- 文档存在性、入口链接与公开文档边界已进入两仓库 release-contract 门禁；Bridge
  另有 Phase 7 文档契约单测。
- 本基线不代表正式发布；未创建任何 tag 或 Release。

## 8.2 发布前原生播放器兼容补充

- Bridge 提交 `d85cbf6` 将 Halcyon、Flamingo、QZ Music、PrismMusic 的四个宿主包加入
  `PlayerSystemUiPolicy`，只提供歌词入口、媒体历史与 AOD 的 SystemUI 包名兼容。
- 新增契约测试确认四者不进入播放器专属收藏按钮翻译覆盖；主源码仍无
  `ExternalLyricProviderRegistry`、QZ/Prism v4 source 或 sender 放行。
- PR #42 与 #37 均在留下 4.0 主动接入迁移要求后关闭，`mergedAt=null`。
- 完整测试为 72 suites / 481 tests，0 failure/error，6 skipped；`lintDebug` 与 release
  contract 通过。
- 由于该提交改变 Bridge APK，下一正式候选从 RC6 开始。

## 9. Slice 7B 版本与机器契约补充

- Bridge 已冻结 `4.0.0` / `versionCode=136`；契约提交 `00a8333`。
- Provider 保持独立内部版本；套件 source tag 冻结为 `providers-v1.0.0`，契约提交
  `cb57ce6`。
- Bridge `release/bridge-release-contract.json` 统一拥有 tag、LSP tag、Bridge scope、
  Provider source repo/tag、资产名和 13 APK / 16 资产计数。
- Provider `release/v5-provider-matrix.json` 统一拥有 12 个 module、applicationId、
  内部版本、asset、scope、process evidence 和已验证宿主版本。
- 两仓库 PowerShell 校验脚本可独立运行，也可由 Bridge 跨仓库执行；实际校验通过。
- 版本更新后 Bridge 标准测试仍为 477 tests 通过（6 skipped），`assembleDebug` 通过；
  `aapt2` 确认 APK 为 `4.0.0` / code 136，SHA-256 为
  `CF4B85ECFAF9EB21D664ABB22EC4BC99F765AC212CD28790094789F1AD483BBE`。
