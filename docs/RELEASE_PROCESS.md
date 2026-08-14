# 正式发布流程

1. 分别检查 Bridge、LyricProvider 与 LSPRepo 的 Git 状态，并同步远端；保留无关的用户修改和工具目录。
2. 更新 Bridge 的 `versionName`、递增 `versionCode`，并新增 `.github/release-notes/<version>.md` 与 `docs/releases/v<version>.md`。完整发布说明必须参考前一版本的结构，至少覆盖实际变更、兼容范围、验证、升级说明和资产；用户给出的短句只作为更新摘要，不得直接缩减为单句 Release 正文。同步更新 Bridge（原仓库）`README.md` 的 `Current release` 行与 `README.zh-CN.md` 的 `当前版本` 行为当前版本。
3. 如果 LyricProvider 有发布改动，先提交并推送 LyricProvider `master`；Bridge Release 工作流默认从该分支构建 Provider。
4. 运行 Bridge 相关单元测试和 debug 构建，复核发布提交只包含本次变更，然后提交并推送 Bridge `main`。
5. 在 LSPRepo 同步 `main`，更新 `README.md` 的中英文版本说明（只保留最新版本更新日志，删除上一版及更早的版本段落），提交 `Update metadata for v<version>`，并创建 `<versionCode>-<version>` tag 指向该 update commit；推送 LSPRepo `main` 和 tag。此步骤是每次正式发布的必做项，不能用工作流上传 LSP Release 资产代替。
6. 确认远端 LSP tag 指向上述 update commit 后，在 Bridge 创建并推送 `v<version>` tag，触发统一 Release 工作流。
7. 等待 Bridge、全部 Provider 与 publish job 成功；核对 Bridge Release 和 LSP Release 的版本、说明及全部资产。
8. 最后再次核对两个仓库的远端分支、tag 与 Release，并把提交、Actions run 和资产结果写入项目记忆与 `AGENTS.md`。
