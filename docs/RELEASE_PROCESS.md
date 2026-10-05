# 4.3.0 正式发布流程

适用范围：Bridge `4.3.0`、14 个独立 Provider API 102 套件与 LSPosed mirror。

涉及仓库：

- `Andrea-lyz/ColorOS-Live-Lyrics-Bridge`
- `Andrea-lyz/ColorOS-Live-Lyrics-Providers`
- `Xposed-Modules-Repo/io.github.andrealtb.lockscreenlyrics`（本地 `LSPRepo`）

更新文档、推送分支或完成 RC 不等于授权正式发布。只有用户明确要求发布时，才能创建
LSP tag、Bridge tag 或 GitHub Release。

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
- Provider source repository/main 与 metadata 阶段冻结的 commit SHA；
- 14 个 module/applicationId/scope/内部版本；
- 15 个 APK 与 18 项最终资产的精确数量；
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
Build and Release
mode=rc
rc_number=<N>
```

RC mode 必须：

1. checkout Providers `main` 并记录解析后的不可变 Provider SHA；
2. Bridge 标准测试、release lint、正式签名 release 构建；
3. Provider `testV5Matrix`、14 module release/R8、正式签名；
4. 校验 15 APK 的 DEX 禁用字符串、包名、版本、证书和 zipalign；
5. 生成 14 APK Provider ZIP、asset manifest 和 `SHA256SUMS`；
6. 上传恰好 18 项 complete artifact；
7. 跳过 publish job，不创建公开 Release。

下载 complete artifact 后，再独立复算哈希并重跑 asset verifier。真机只测试该 batch；
任一源码改动都生成新 RC。

## 6. 真机门禁

使用最终签名 RC 验证：

- Bridge 4.0.0 覆盖升级和 schema v3 设置保留；
- 12 个专属 Provider 与通用播放器 Provider 均使用 libxposed API 102 入口；
- Provider Debug Remote Preferences 首次升级的默认关闭与重启生效行为；
- Bridge 配置备份/恢复；
- 12 个专属 Provider、通用播放器 Provider 及所有多宿主 profile；
- 播放、暂停/恢复、seek、连续切歌、同曲重播；
- 锁屏、AOD、长歌词、逐字、翻译、封面与 action row；
- Provider-only 原生显示与安装 Bridge 后无重复提交；
- debug 关闭/开启的结构化日志与隐私。

用户明确确认最终 RC 后才进入正式发布准备。

## 7. 锁定 Provider 源码

1. Provider `main` 工作树必须干净，候选 commit 已推送。
2. workflow 只从 `Andrea-lyz/ColorOS-Live-Lyrics-Providers` 的 `main` checkout，并在 metadata 阶段记录完整 commit SHA。
3. 后续构建、打包和资产 manifest 只使用该 SHA；运行中 `main` 移动不影响本次 batch。
4. 不在 Provider 仓库重复维护另一套 APK Release；APK 由 Bridge/LSP 协调 Release 交付。

## 8. LSPRepo metadata 先行

1. 在 `LSPRepo/main` 更新 `README.md`、`SUMMARY`、`SOURCE_URL`、`SCOPE`。
2. `SCOPE` 必须与 Bridge APK 一致，只含 `system`、`com.android.systemui`。
3. 提交 `Update metadata for v<version>` 并推送 `main`。
4. 创建 `<versionCode>-<version>` tag 指向该 metadata commit。
5. 推送并远端核对 LSP tag。

该步骤是每次正式发布必做项，不能由上传 LSP Release 资产替代。

## 9. Bridge tag 与正式 workflow

1. 将审核通过的功能分支按仓库策略合入公开默认分支。
2. 从干净 clone/默认分支再跑普通 debug CI。
3. 在最终 Bridge commit 创建 `v<version>` 并推送。
4. tag 事件自动进入 release mode；workflow 必须确认：
   - Bridge tag 等于契约 tag；
   - Provider `main` 已解析为完整 commit SHA，且 Provider/Bridge 发布契约一致；
   - LSP tag 已存在；
   - `.github/release-notes/<version>.md` 已存在。
5. publish job 创建 Bridge GitHub Release 与 LSP mirror Release。

workflow 拒绝覆盖已经存在的公开 Release。发布后修复使用新版本/tag，不移动旧 tag，
不静默替换 APK。

## 10. 正式资产白名单

`4.3.0` 预期恰好 18 项：

1. `ColorOS-Live-Lyrics-Bridge-v4.3.0.apk`
2. 14 个 `ColorOS-Live-Lyrics-Provider-<Name>-v4.3.0.apk`
3. `ColorOS-Live-Lyrics-Providers-v4.3.0.zip`
4. `release-assets-v4.3.0.json`
5. `SHA256SUMS`

Provider ZIP 只能包含 14 个顶层 APK，不含目录、debug/unsigned APK 或旧包名。

## 11. 发布后独立复核

1. 等待 workflow 结束，用 `gh run view` 核对每个 job；不能从轮询中推测成功。
2. 分别从 Bridge 与 LSP Release 下载 18 项资产。
3. 比较两个 Release 的文件名、字节数和 SHA-256。
4. 对 15 APK 重跑 package/version/certificate/zipalign 检查。
5. 核对 Bridge tag、Provider `main` 的已冻结 SHA、LSP tag 与三个 commit。
6. 用公开下载的 Bridge + 至少一个 Provider 做最后安装冒烟。
7. 将 run、commit、tag、哈希和设备结论写入 4.3 发布台账。

只有这些门禁全部关闭，才能将 v4.3.0 标记为正式完成。

## 12. Bridge 预览版（非主线）

预览版只用于让特定系统的用户提前测试 Bridge，不属于正式发布，也不替代第 5、6 节的 RC 与
真机门禁。只有用户明确要求时才创建。

1. 预览 tag 格式为 `<契约 releaseTag>-<标签>`，例如 `v4.4.0-C17-Preview` 或
   `v4.4.0-C16-Artwork`；tag 可以打在功能分支上。推送后由 `preview.yml` 处理，
   `release.yml` 忽略带连字符的 tag。
2. 在该 commit 上提交 `.github/release-notes/<版本号去掉 v>.md`，写明预览性质、适用系统、
   验证状态和已知限制。
3. `preview.yml` 构建正式签名的 Bridge：
   - 运行单测、release lint；
   - versionName 取 tag，versionCode 沿用契约，便于与正式版互相覆盖安装；
   - 校验包名、版本、证书、zipalign 与 DEX 禁用字符串。
4. tag 以 `-Artwork`、`-Artwork-Preview` 或编号后缀 `-Artwork-Preview1` 等结尾时，额外附带动态封面 Provider：
   - 按契约 `previewArtworkProvider.ref` 检出 Providers 仓库的固定 commit；
   - 先校验 `artwork-contract` 镜像、Providers 契约与两个契约的一致性，再执行
     `assembleArtworkProviderRelease`；
   - applicationId、versionCode/Name、资产名与证书都必须与契约一致。
5. 若 tag 列在 `previewUniversalProvider.previewTags` 中，还从同一固定 Provider commit 构建
   通用播放器 Provider；执行该模块的 debug 单测，并校验包名、独立版本、签名、scope 与 zipalign。
   `v4.4.0-C17-Artwork-Preview` 与 `v4.4.0-C17-Artwork-Preview1` 因此交付三个 APK 和一个 `SHA256SUMS`，不会在发布后临时补附件。
6. `preview.yml` 创建 GitHub pre-release，不标记 latest。不构建其余歌词 Provider 矩阵，
   不创建 LSP tag，不上传 LSP mirror，也不修改正式契约版本。
7. 每次需要新的动态封面预览时，先把 `previewArtworkProvider.ref` 更新为 Providers 仓库的
   固定 commit，再推 tag，保证构建可复现。
8. 用户要求移除被替代的旧预览 Release 时，先保存旧发布信息和资产，待新预览发布并独立下载验证后
   再删除旧 Release；不自动删除其 Git tag，也不移除未被点名的其他预览。

### 已发布预览的独立 Provider 附件升级

用户明确要求保留预览 Release 并升级独立 Provider 时，可交付新版本附件；不重跑原预览发布或移动原 tag。先递增 Provider versionName/versionCode 并冻结源码，更新当前契约。使用 `artwork-provider-v<版本>` 构建 tag 触发 `artwork-provider-candidate.yml`，只生成受控签名候选资产，不自动发布。候选复用公开 Bridge/Universal APK，运行既有包名、版本、证书、scope、DEX 和 zipalign 校验。

发布前备份原 Release 正文和全部附件，独立验证候选并确认保留 APK 的哈希未变；上传新版本 Provider、更新校验和与双语说明，验证公开下载后移除旧版本 Provider 附件。台账记录新 Provider SHA、构建 tag/run、公开哈希及设备证据。预览性质及正式 RC 门禁不变；同版本 APK 不覆盖。保留 APK 的实际来源以原发布台账为准，不能因当前契约更新而改写历史来源。
