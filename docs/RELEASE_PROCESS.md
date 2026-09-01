# 4.1 正式发布流程

适用范围：Bridge `4.1.0`、独立 12 Provider API 102 套件与 LSPosed mirror。

涉及仓库：

- `Andrea-lyz/ColorOS-Live-Lyrics-Bridge`
- `Andrea-lyz/ColorOS-Live-Lyrics-Providers`
- `Xposed-Modules-Repo/io.github.andrealtb.lockscreenlyrics`（本地 `LSPRepo`）

更新文档、推送分支或完成 RC 不等于授权正式发布。只有用户明确要求发布时，才能创建
Provider source tag、LSP tag、Bridge tag 或 GitHub Release。

## 1. 冻结源代码

1. 分别检查三个仓库的工作树、分支、remote 和最新提交。
2. 保留用户无关修改，不使用 reset/checkout 覆盖。
3. Bridge 与 Provider 的业务代码进入 feature freeze，只接收 blocker。
4. 所有最终源码必须被 Git 跟踪；APK、日志、崩溃转储和本地构建目录不得进入提交。
5. 执行 `git diff --check`。

## 2. 校验机器发布契约

Bridge 契约：

```text
release/bridge-release-contract.json
```

Provider 契约：

```text
ColorOS-Live-Lyrics-Providers/release/v5-provider-matrix.json
```

执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File scripts\validate-release-contract.ps1 `
  -ProviderRepoRoot ..\ColorOS-Live-Lyrics-Providers
```

契约必须一致地拥有：

- Bridge versionName/versionCode、`v<version>` 与 LSP `<versionCode>-<version>` tag；
- Provider source repository/tag；
- 12 个 module/applicationId/scope/内部版本；
- 13 个 APK 与 16 项最终资产的精确数量；
- 规范文件名、正式签名证书 SHA-256、固定 Android build-tools；
- 最终 APK DEX 禁用字符串。

不要在 workflow 中复制另一份矩阵常量。

## 3. 本地门禁

### Bridge

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug
```

检查：

- 标准测试无 failure/error；
- lint 不使用 baseline 隐藏新增错误；
- packaged manifest 只有预期组件；
- APK scope 只有 `system`、`com.android.systemui`；
- 版本、权限、DEX/native 库增量可解释。

### Providers

```powershell
.\gradlew.bat testV5Matrix assembleV5MatrixDebug
```

使用非发布 keystore 的本地 release/R8 只能验证 shrinker，不得作为候选资产。正式签名
release 必须由受控 Actions secrets 构建。

## 4. 文档门禁

发布前同步：

- Bridge `README.md` / `README.zh-CN.md`；
- `docs/PLAYER_INTEGRATION.md` / `.zh-CN.md`；
- `docs/4.0/MIGRATION-3.8-TO-4.0.md` / `.zh-CN.md`；
- Provider 两份 README、`docs/4.0/README.md` 与双语 Provider 适配指南；
- LSPRepo `README.md`、`SUMMARY`、`SOURCE_URL`、`SCOPE`；
- `.github/release-notes/<version>.md`；
- `docs/releases/v<version>.md`。

Release Notes 必须是完整历史风格正文，至少包含架构变化、用户功能、兼容矩阵、迁移、
验证、已知限制、资产和致谢，不能只有一句摘要。

## 5. 私有 RC

推送 Bridge/Provider 的候选分支，但不打 tag。手动运行：

```text
Build 4.1 RC and Release
mode=rc
rc_number=<N>
providers_ref=<完整 40 位 Provider SHA>
```

RC mode 必须：

1. checkout 不可变 Provider SHA；
2. Bridge 标准测试、release lint、正式签名 release 构建；
3. Provider `testV5Matrix`、12 module release/R8、正式签名；
4. 校验 13 APK 的 DEX 禁用字符串、包名、版本、证书和 zipalign；
5. 生成 12 APK Provider ZIP、asset manifest 和 `SHA256SUMS`；
6. 上传恰好 16 项 complete artifact；
7. 跳过 publish job，不创建公开 Release。

下载 complete artifact 后，再独立复算哈希并重跑 asset verifier。真机只测试该 batch；
任一源码改动都生成新 RC。

## 6. 真机门禁

使用最终签名 RC 验证：

- Bridge 4.0.0 覆盖升级和 schema v3 设置保留；
- 12 个 Provider 从 4.0 覆盖到 4.1 后仅使用 libxposed API 102 入口；
- Provider Debug Remote Preferences 首次升级的默认关闭与重启生效行为；
- Bridge 配置备份/恢复；
- 12 Provider 及所有多宿主 profile；
- 播放、暂停/恢复、seek、连续切歌、同曲重播；
- 锁屏、AOD、长歌词、逐字、翻译、封面与 action row；
- Provider-only 原生显示与安装 Bridge 后无重复提交；
- debug 关闭/开启的结构化日志与隐私。

用户明确确认最终 RC 后才进入正式发布准备。

## 7. 锁定 Provider 源码

1. Provider 工作树必须干净，候选 commit 已推送。
2. 创建契约规定的 Provider source tag，例如 `providers-v1.1.0`。
3. 推送 tag 后从远端核对其完整 commit。
4. 不在 Provider 仓库重复维护另一套 APK Release；APK 由 Bridge/LSP 协调 Release 交付。

## 8. LSPRepo metadata 先行

1. 在 `LSPRepo/main` 更新 `README.md`、`SUMMARY`、`SOURCE_URL`、`SCOPE`。
2. `SCOPE` 必须与 Bridge APK 一致，只含 `system`、`com.android.systemui`。
3. 提交 `Update metadata for v<version>` 并推送 `main`。
4. 创建 `<versionCode>-<version>` tag 指向该 metadata commit。
5. 推送并远端核对 LSP tag。

该步骤是每次正式发布必做项，不能由上传 LSP Release 资产替代。

## 9. Bridge tag 与正式 workflow

1. 将审核通过的 `4.1` 分支按仓库策略合入公开默认分支。
2. 从干净 clone/默认分支再跑普通 debug CI。
3. 在最终 Bridge commit 创建 `v<version>` 并推送。
4. tag 事件自动进入 release mode；workflow 必须确认：
   - Bridge tag 等于契约 tag；
   - Provider source tag 指向预期 commit；
   - LSP tag 已存在；
   - `.github/release-notes/<version>.md` 已存在。
5. publish job 创建 Bridge GitHub Release 与 LSP mirror Release。

workflow 拒绝覆盖已经存在的公开 Release。发布后修复使用新版本/tag，不移动旧 tag，
不静默替换 APK。

## 10. 正式资产白名单

`4.1.0` 预期恰好 16 项：

1. `ColorOS-Live-Lyrics-Bridge-v4.1.0.apk`
2. 12 个 `ColorOS-Live-Lyrics-Provider-<Name>-v4.1.0.apk`
3. `ColorOS-Live-Lyrics-Providers-v4.1.0.zip`
4. `release-assets-v4.1.0.json`
5. `SHA256SUMS`

Provider ZIP 只能包含 12 个顶层 APK，不含目录、debug/unsigned APK 或旧包名。

## 11. 发布后独立复核

1. 等待 workflow 结束，用 `gh run view` 核对每个 job；不能从轮询中推测成功。
2. 分别从 Bridge 与 LSP Release 下载 16 项资产。
3. 比较两个 Release 的文件名、字节数和 SHA-256。
4. 对 13 APK 重跑 package/version/certificate/zipalign 检查。
5. 核对 Bridge tag、Provider source tag、LSP tag 与三个 commit。
6. 用公开下载的 Bridge + 至少一个 Provider 做最后安装冒烟。
7. 将 run、commit、tag、哈希和设备结论写入 4.1 发布台账。

只有这些门禁全部关闭，才能将 v4.1.0 标记为正式完成。
