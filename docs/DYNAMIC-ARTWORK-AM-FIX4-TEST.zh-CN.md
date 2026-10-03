# D1 AM fix4：诊断预算饥饿修复

日期：2026-10-03。针对 `lyrics-log-20261003-184839.txt` 无法证实挂载/播放链路的问题。
仅 Bridge 变更；AM 插件沿用 fix3。

## 日志实测

184839 窗口内 AM 请求、缓存与跨尺寸复用都产生了结果，但以下事件计数为零：

```text
ARTWORK_TEST_MOUNTED          0
ARTWORK_TEST_RENDER_STATE     0
ARTWORK_RETRY_SCHEDULED       0
ARTWORK_RETRY_FIRED           0
ARTWORK_NETWORK_RECOVERED     0
ARTWORK_ASSET_DROPPED         0
ARTWORK_TEST_FALLBACK         0
```

同时 fix3 新增的 `ARTWORK_GATE_LOCKSCREEN_CARD` 有 2915 条。
按秒统计，144 个采样秒中有 111 秒该 owner 的已接受事件数达到 24 条上限，
多个秒里门禁事件本身正好占满 24 条。

原因是新门禁日志按候选逐条输出：同一事件名在两个候选的 `eligible` 与 `paused` 之间逐帧交替，
每次都算“状态变化”，绕过 `ArtworkDiagnosticGate` 的去重。
门禁日志在 `observe()` 前段执行，先耗尽每秒预算，同一帧稍后产生的挂载与播放状态被丢弃。

因此 184839 **不能**用来证明或否定 fix3 的挂载、首帧和重试行为；
它只能证明 AM 侧解析、校验与缓存链路正常。

## 修复

`ArtworkImmersivePlayback` 改为按界面聚合：每个 surface 每轮只发一条事件，内容为各判定原因的计数，
例如 `total=2 eligible=1 paused=1`。计数稳定时被去重，不再逐帧交替；
候选遍历同时复用已算出的判定结果，不再重复求值。

事件名与其余语义不变，不改变选择、挂载、重试或缓存行为，也不放宽任何门禁。

## 复测包

`artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix4-debug.apk`

文件 SHA256：`D598E4DC03938AF313899F4E5462897B2A3A00FE71B7843F311A49C505316282`。
apksigner 验证通过，Debug signer 与 fix3 相同：
`5a6fd43fc11b4c70ff12b6f82a7261c2ed9c4945692b3d818ca50bc64c8dd869`。

只更新 Bridge；AM 插件继续使用 fix3。用户自行重启 SystemUI 后重新开启 AM 锁屏联调。

## 复测重点

沿用 [fix3 步骤](DYNAMIC-ARTWORK-AM-FIX3-TEST.zh-CN.md)，本轮额外确认这些事件重新出现：

```text
ARTWORK_TEST_MOUNTED
ARTWORK_TEST_RENDER_STATE state=preparing|waiting_first_frame|playing
ARTWORK_RETRY_SCHEDULED / ARTWORK_RETRY_FIRED
ARTWORK_NETWORK_RECOVERED
ARTWORK_ASSET_DROPPED
ARTWORK_GATE_IMMERSIVE / ARTWORK_GATE_LOCKSCREEN_CARD   (聚合计数)
```

提供首次失败时间、当时是首次下载/恢复网络/大小切换之一，以及是否暂停或息屏。

## 本地验证

Bridge 708 项测试、6 项既有 fixture 跳过，Lint 0 errors/48 warnings，Debug 构建通过。

fix4 让挂载与播放事件重新可见后，185656 日志暴露出首次下载被 `lockscreen_inactive` 取消、
以及瞬时网络失败整链重来的问题；已在 [fix5](DYNAMIC-ARTWORK-AM-FIX5-TEST.zh-CN.md) 处理。
本次为诊断输出范围变更，没有可被 JVM 覆盖的行为分支；聚合是否真正释放预算需下一份真机日志确认。
未安装、未操作设备、未提交或发布。
