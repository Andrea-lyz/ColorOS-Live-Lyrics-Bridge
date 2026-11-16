# M1 退出验收：设备检查清单

日期：2026-11-14。状态：**候选已打包，等待用户设备验收；以下结论全部为本地证据，设备上尚未运行。**

M1 通过标准（[计划 §11](<NEXT-MAJOR-RELEASE-PLAN.zh-CN.md>)）：可区分“旧注入进程”“配置未到”“能力不支持”；诊断开关不改变业务行为。本清单同时覆盖本轮 5 项收口和上一轮 P0 fail-open 修复，它们都还没有上过设备。

## 1. 候选（全部 Debug，不是正式 Release）

位置：工作区 `artifacts/m1-exit-20261114/`（不入库），哈希见同目录 `SHA256SUMS.txt`。

| 文件 | 包名 / 版本 | SHA-256 | 内嵌 buildId 的 sourceDigest |
| --- | --- | --- | --- |
| `Bridge-v4.5.4-M1-exit-debug.apk` | `io.github.andrealtb.lockscreenlyrics` 4.5.4 (149) | `f3fb108e…730d1ad` | `7b9fab20…` |
| `Salt-Provider-v1.1.2-M1-exit-debug.apk` | `…provider.salt` 1.1.2 (8) | `fc88489d…c83a6bf` | `5d53d390…` |
| `Universal-Provider-M1-exit-debug.apk`（可选） | `…provider.universal` 1.1.3 (5) | `582c1924…34a40775` | `5d53d390…` |

三者签名证书 SHA-256 均为 `5a6fd43f…8dd869`，与设备上现有的 Debug 候选相同，可以直接覆盖安装，不需要卸载或清数据。版本号与已装版本相同。

包含的改动：

- Bridge：插件指纹改读插件加载器自身 APK；能力状态按对象聚合（新增 `owners=`、`summary=`）；AOD 低帧率模式切换次数与最近切换时间；App 侧环境快照新增 `aosp_doze_always_on_raw`。
- Providers（Salt / Universal 等共享）：诊断写入 fail-open（`configConsumed`、`installerReturned`）；诊断接收器注册失败后最多重试 3 次。

## 2. 前提与回退

- 保持当前配置：Universal **不**对 Salt 取词，只用专用 Salt（同一播放器只开一个 Provider）。
- 不清数据、不卸载、不改 LSPosed 作用域。
- 回退：覆盖安装上一组已确认的候选即可——Bridge `artifacts/m1-runtime-20261113/test-kit/Bridge-v4.5.4-M1-runtime-debug.apk`（`046F09CD…`），Salt `artifacts/m1-salt-bootstrap-fix-20261113/Salt-Provider-v1.1.2-M1-attach-fix-debug.apk`（`ECBE2EAC…`）。
- 所有安装、重启、播放与切歌都由你操作；我不会通过 ADB 做任何事，除非你当次另行授权。

## 3. 检查步骤

请按顺序做。每一步记录“看到的值”，不需要截图也可以；能导出 JSON 的步骤请导出。

### A. 旧注入进程（MISMATCH → MATCH）

1. 覆盖安装 Bridge 候选，**先不要重启手机或 SystemUI**。
2. 打开 Bridge 调试页，刷新运行状态。
   - 期望：安装构建为 `…7b9fab20…`，注入进程观测构建仍为旧的 `…c9037c2a…`，比较结果为 **MISMATCH**。
3. 按你平时的方式让 SystemUI 重新加载模块（例如重启手机）。
4. 再刷新。
   - 期望：两边都是 `…7b9fab20…`，结果 **MATCH**。

这是 M1 第一条标准此前从未在设备上验证过的场景。

### B. 配置是否到达

1. 在 Bridge 外观设置里改一项可见的开关（例如字符上浮开 → 关），保存。
2. 回调试页刷新。
   - 期望：“外观配置已应用 revision”变化，不是 -1。
3. 把开关改回原值。

### C. 能力状态与“能力不支持 / 配置关闭”的区分

1. 用 Salt 播放一首有逐字歌词的歌，锁屏看到自绘歌词。
2. 回调试页刷新，看“能力状态”。
   - 期望：`CUSTOM_DRAW` 的 `owners=` ≥ 1，`summary=` 列出各对象的结果（例如 `DISPATCHED:2`）。
3. 关闭字符上浮，再锁屏看一会儿歌词，回来刷新。
   - 期望：`CHAR_LIFT` 为 `outcome=DISABLED reason=CONFIG_DISABLED`，**不是** `supported=NO`。
4. 恢复字符上浮。

### D. 插件指纹（本轮修复）

1. 在调试页开始一次短时诊断（这一步才会后台读取加载器背后的文件）。
2. 等几秒后刷新，看“已观测加载器”里的 `plugin` 行。
   - 期望：`state=READABLE`，`comparison=SAME_CONTENT_SET`。
   - 如果是 `DIFFERENT_OR_DIFFERENT_CONTAINER_SET`：请记下 `plugin` 行的 `apkCount` 和 App 侧环境快照里 `plugin` 一项的 `apk_count`。两者不同说明加载器带了共享库 APK，属于已知的剩余不确定项，不是这次修复失败。
3. 停止短时诊断并导出 `bridge-runtime-diagnostics.json`。

### E. AOD 记录

1. 记下调试页的“渲染器切换次数”。
2. 灭屏（停留在 AOD 或熄屏几秒），再亮屏，重复两次。
3. 刷新。
   - 期望：切换次数大约增加 4（每次灭屏、亮屏各一次）；当前值在亮屏时为 `false`。
4. 在 App 侧环境快照里记下 `aosp_doze_always_on_raw` 的值，以及你设置里 AOD 是开还是关。这一项只是原始值，用来判断它能否反映 ColorOS 的 AOD 开关；不一致也不算失败。

### F. Salt 专用 Provider

1. 覆盖安装 Salt 候选，完全结束 Salt（不要只从最近任务划掉音乐服务）后重新打开。
2. 在 Salt 模块设置里刷新“本模块运行诊断”。
   - 期望：`observedBuildId` 含 `5d53d390…` 且与安装构建 **MATCH**；`installerStage=INSTALLER_RETURNED`；`configObservation=CONSUMED`（或 `CONSUMED_NO_REVISION`）。
3. 正常播放并切几首歌，确认歌词正常加载。

### G. 诊断开关不改变业务

在 C/F 过程中：开着短时诊断与关着短时诊断时，歌词显示、上浮、切歌是否有任何差别。这一项只能靠你的观察，请如实写“没注意到差别”或具体差别。

### H.（可选）Universal

只有你想验证 Universal 的诊断注册重试时才需要。安装 Universal 候选后，在 Universal 页面刷新运行诊断，期望能拿到回包。**不要**为此打开 Universal 对 Salt 的取词。

## 4. 需要回传的内容

- A–G 各步看到的值（或“与期望一致”）。
- D 步导出的 `bridge-runtime-diagnostics.json`。
- F 步的 Salt 诊断导出或截图。
- 任何异常现象及大致时间。

## 5. 本清单不覆盖

- 折叠形态（本机无铰链传感器，只能验证“不存在”）、其他机型、正式签名与 R8 形态、视觉外观与性能。
- 诊断注册真实失败的样本（重试路径只有本地测试）。
- 发布流程中的源码摘要记录（计划 R 系列）。

## 6. 设备结果（2026-11-14，用户在 PJZ110 / PJZ110_17.0.0.103(CN01) / LSPosed 2.2.1 (7941) 上执行）

候选由 agent 经用户当次授权用 `adb install -r` 覆盖安装（Bridge `f3fb108e…`、Salt `fc88489d…`，均返回 Success）；其余操作均由用户完成。Universal 未安装，H 步未做。

| 步骤 | 结果 | 判定 |
| --- | --- | --- |
| A 旧注入进程 | 安装后未重启显示 MISMATCH；重启后 MATCH，注入构建 `…7b9fab20…` | 通过 |
| B 配置到达 | 修改外观设置后“外观配置已应用 revision”不是 -1（`1794616360900`） | 通过（只验证了“已到达”；“未到达”状态没有在设备上制造） |
| C 能力区分 | `CUSTOM_DRAW` DISPATCHED，`owners=8 summary=DISPATCHED:8`；关闭上浮后 `CHAR_LIFT outcome=DISABLED reason=CONFIG_DISABLED supported=UNKNOWN`，`owners=7 summary=DISABLED:7` | 通过：配置关闭没有被报成不支持 |
| D 插件指纹 | 插件加载器 `READABLE`、`apkCount=1`、`df1fc20d…`；App 侧已安装 `com.oplus.systemui.plugins` 17.000.002 为同一 `df1fc20d…`、`apk_count=1`（SystemUI 两侧同为 `8f07d757…`） | 通过：`SAME_CONTENT_SET`。本机插件不带共享库 APK，第 2 节所述的不确定项在本机不出现 |
| E AOD 记录 | 初始 2；导出时 4；用户最终看到 8，当前 `false` | 记录可用；次数比“两次灭屏/亮屏 ≈ +4”多，原因 unknown（可能是 ColorOS 额外的 SCREEN_ON/OFF 广播或额外的灭屏），计数始终为偶数，与成对切换一致。`aosp_doze_always_on_raw` 因导出白名单遗漏未进入 JSON，已在本地修复（见下） |
| F Salt 专用 | 观测 buildId `…5d53d390…` MATCH；`INSTALLER_RETURNED`；`CONSUMED` | 通过（P0 fail-open 修复后的构建在设备上正常装上业务 Hook） |
| G 开关不改行为 | 用户：“没注意到差别” | 用户观察，无量化证据 |

导出文件为用户本地 `bridge-runtime-diagnostics (1).json`（未复制进仓库）：`coverage=BOUNDED_OBSERVATIONS`，无截断、无省略。

**M1 判定（本机）**：三条区分标准在这台设备上已有直接证据；“开关不改行为”只有用户观察和 Provider 侧本地测试，Bridge 侧仍没有专门测试。其他机型、折叠、正式签名与 R8 形态仍未覆盖。

**用户判定（2026-11-14）：M1 通过。** 依据为本节本机结果；B 的“未到达”状态与 G 的量化证据、其他机型与正式形态仍按上文列为未覆盖，在 C-06/M6 验收矩阵中继续跟踪。

**结果后修复**：`ShortDiagnosticsControls` 的 `appEnvironment` 导出白名单补上 `aosp_doze_always_on_raw`。Bridge 943 tests / 0 fail / 6 skip，`:app:assembleDebug` 通过，sourceDigest `d5c36f21…`。该修复只影响 App 侧导出，未重新安装、未在设备上验证。
