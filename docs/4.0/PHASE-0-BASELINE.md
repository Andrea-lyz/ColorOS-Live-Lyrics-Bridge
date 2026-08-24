# 4.0 Phase 0 基线记录

记录日期：2026-08-24  
范围：只完成基线冻结、分支/仓库隔离和迁移映射；未进入 Phase 1 的 Provider 基础设施实现。

## 1. Git 基线

| 边界 | 基线 | 4.0 工作边界 |
| --- | --- | --- |
| Bridge | `v3.8.1`，commit `85f27b5f0d3bb813e668c46591573d16d2d93472`，versionCode `135` / versionName `3.8.1` | 已从该提交建立本地分支 `4.0`；旧 `main` 仍指向同一发布提交 |
| 旧 LyricProvider | `master`，commit `292a7da3f88a87e8c6df6b4ae4f56455b6856c72` | 原仓库保持不改；新仓库 `ColorOS-Live-Lyrics-Providers` 从该提交独立复制并建立 `4.0` 分支 |
| 新 Provider 仓库 | 初始来源为旧 LyricProvider `master` | 当前没有远端，避免 Phase 0 误推送到旧仓库；Phase 1 起只在新仓库实施 4.0 改造 |

## 2. 未提交 ConePlayer 修改

Bridge `4.0` 工作树中保留用户已有修改，未暂存、未提交、未覆盖：

- `app/src/main/java/io/github/andrealtb/lockscreenlyrics/ConePlayerAdapter.java`：新增当前歌词发布、广播接收、Lyricon Song 取证链路；工作树 SHA-256：`8B71E01A816A7EAAF174FA8EDF7A409306B26D6F598A2287AF14D288E894B9C8`。
- `app/src/test/java/io/github/andrealtb/lockscreenlyrics/ConePlayerAdapterTest.java`：新增广播提取和 Lyricon Song LRC 测试；工作树 SHA-256：`BC731702F4FBCD56ECA9CCDCF9E1067A56AD32E208459A43D14F4F7771850A32`。
- 相对 `v3.8.1` 共 `319` 行新增，迁移期间不得回滚、覆盖或把它误当成已完成的 Cone v5 Provider。
- 对应真机日志保留在工作区 `logs/`，哈希见 `PHASE-0-LOGS.sha256`。

## 3. 基线构建与测试

### Bridge

在无 Cone 工作树修改的临时 clean worktree（`v3.8.1`）执行：

```text
scripts\\gradle-local.cmd testDebugUnitTest assembleDebug
```

结果：`BUILD SUCCESSFUL`，耗时约 50 秒。基线 APK 已复制到：

```text
_archive/packages/4.0-phase0-baseline/bridge-v3.8.1/
```

APK SHA-256 见 `PHASE-0-ARTIFACTS.sha256`。

### 旧 LyricProvider

在旧仓库 `master`（tracked source clean；仅有 `.filescope/`、`.serena/` 两个未跟踪工具目录）执行：

```text
scripts\\gradle-ascii.cmd testDebugUnitTest assembleDebug
```

结果：`BUILD SUCCESSFUL`，耗时约 2 分 38 秒，`905` 个 actionable tasks。构建覆盖旧仓库全部已注册 application modules，生成 `18` 个 debug APK；APK 已复制到：

```text
_archive/packages/4.0-phase0-baseline/lyricprovider-v3.x/
```

本次只验证 debug 构建。release 构建仍受既有 release signing 配置约束，没有用 debug 签名冒充 release。

构建中出现的 KSP deprecated/invisible-reference 警告和 native library strip 警告均未阻断任务；它们属于后续 Phase 1/Provider 迁移的独立清理项。

## 4. Bridge 3.x 模块与 scope

当前 Bridge `ColorOS-Live-Lyrics-Bridge` 的 Gradle modules：

- `:app`
- `:external-lyric-protocol`
- `:libxposed-api-stubs`

APK metadata：

- namespace：`io.github.andrealtb.lockscreenlyrics`
- applicationId：`io.github.andrealtb.lockscreenlyrics`
- `META-INF/xposed/module.prop`：`minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`、`exceptionMode=protective`
- 当前静态 scope：`system`、`com.salt.music`、`ink.trantor.coneplayer`、`ink.trantor.coneplayer.gp`、`com.android.systemui`

Phase 0 不删除 `external-lyric-protocol`、Salt/Cone adapter 或 v4 ingress；它们必须等对应 Provider 完成 v5 并通过真机门禁后，按 Phase 5 删除。

## 5. 资产与真机日志

- 本地冻结资产：Bridge debug APK `1` 个，旧 LyricProvider debug APK `18` 个，共 `19` 个 APK。
- 旧 LyricProvider 本次构建同时生成 `LyricProvider-debug.zip`；其 SHA-256 为 `A6413D90E1C27E976FECE951032985AAB0D167F7FBA9DAF9BC9B069E17D4B726`，源路径为 `LyricProvider/build/distributions/LyricProvider-debug.zip`。
- 已发布的 v3.8.1 远端基线按既有发布记录保留：Bridge release 和 LSPosed release 各 `12` 项资产，即 Bridge APK、`10` 个 Provider APK 和 Provider ZIP；这与“本地全模块 debug 构建的 18 个 APK”是两个不同集合。
- 当前 ConePlayer 真机日志：`logs/coneplayer.txt`、`logs/coneplayer2.txt`、`logs/coneplayer3.txt`；哈希见 `PHASE-0-LOGS.sha256`。

## 6. Phase 0 结论与边界

已完成：

1. Bridge 3.x 与旧 LyricProvider 的提交、debug APK、构建/测试结果和日志指纹已冻结。
2. Bridge `4.0` 分支已建立；ConePlayer 用户修改仍留在工作树。
3. `ColorOS-Live-Lyrics-Providers` 已从旧 LyricProvider 独立复制，已移除旧 remote 并建立 `4.0` 分支。
4. applicationId、namespace、module、scope、资产和 v4→v5 / 包名 / 模块映射已写入新仓库的 `docs/4.0/PHASE-0-MIGRATION-MAP.md`。

尚未做：

- 未改 applicationId、namespace 或 Kotlin/Java package。
- 未实现 `provider-core`、`reflection-core`、`NativeLyricInfoPublisher`、`RuntimeModeResolver` 或 NPatch marker。
- 未删除词幕注册、v4 sender、Bridge ingress、Salt/Cone adapter，也未改变 Renderer/AOD 时序。

