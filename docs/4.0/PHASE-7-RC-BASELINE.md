# Bridge / Providers 4.0 Phase 7 RC 基线

> 快照日期：2026-08-30
>
> 状态：Slice 7A 本地源码基线已建立；尚未推送远端、创建 Provider source tag 或生成正式签名 RC。

## 1. 源码基线

| 仓库 | 分支 | 本地基线 | 状态 |
|---|---|---|---|
| `ColorOS-Live-Lyrics-Bridge` | `4.0` | `a342234`：完整 4.0 runtime / v4 删除 / Phase 6 / 视觉控制；`025bfc9`：Windows Unicode 路径测试 worker 修复 | Release workflow、README 与 Phase 7 文档按后续 slice 继续修改；核心实现已提交 |
| `ColorOS-Live-Lyrics-Providers` | `4.0` | `d6f463b`：Windows Unicode 路径测试 worker 修复；`5618582`：LX buffering 歌词投影身份最终修复 | 工作树干净，本地领先 `origin/4.0` 两个提交；尚未推送 |
| `LSPRepo` | `main` | `d505f18` / `135-3.8.1` | 仍是 3.8.1 metadata；4.0 scope 和说明留到 7F/7G |

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

## 6. 进入 Slice 7B/7C 前仍未关闭

1. Bridge 尚未改为 `4.0.0` / `versionCode=136`，Provider 内部版本契约尚未生成机器清单。
2. Bridge `.github/workflows/release.yml` 仍是旧 `LyricProvider` 构建拓扑。
3. Bridge `.github/lsposed` 与独立 `LSPRepo` 仍重复；正式 metadata 单一所有权按 7C/7F 收口。
4. `LSPRepo/SCOPE` 仍是 3.8.1 的五项 scope。
5. 当前提交只在本地；在 RC workflow、版本与文档完成前不推送、不打 tag。
