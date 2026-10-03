# D1 AM fix6：下载与显示解耦

日期：2026-10-03。回应“不应该联动取消下载，匹配成功即缓存完成”。两个 APK 都需要更新。

## 规则变化

旧规则把下载绑在显示状态上：门禁一变（息屏、暂停、锁屏锁定标志抖动、布局尺寸变化、退到桌面、关闭联调）
就取消 provider 请求，已下载进度全部丢弃，下次只能整链重来。这就是 fix5 之前
`185656` 日志里 28 秒不出的直接原因。

新规则只有一条：**一首歌一旦发起查询、并且匹配成功，下载就必须跑完并写入缓存。**
显示层可以随时挂上或撤下，但不再影响这次下载。

- **Bridge**：已经发起的查询只在这三种情况放弃：功能关闭、`detached`、`binding_not_ready`。
  息屏、`display_off`、`keyguard_open`、`paused`、`hidden`、无尺寸、原生过渡、几何变化都不再取消。
  门禁原因改为 `bridge_off` / `binding_not_ready` / `detached` / `paused` / `screen_off` /
  `display_off` / `keyguard_open` / `hidden` / `no_bounds` / `image_effect` / `shape_profile` /
  `geometry_transition` / `native_transition` / `eligible`，日志可直接区分。
- **AM 插件**：`cancel` 到达时若身份已经匹配成功，请求转为 detached，不再 `task.cancel()`；
  下载继续、完成后照常写入缓存，只是不再回复客户端。并发 detached 上限 2。
  （fix7 已把条件从“匹配成功”放宽为“已开始执行”，并把上限改为 1、工作线程提到 2；见
  [fix7](DYNAMIC-ARTWORK-AM-FIX7-TEST.zh-CN.md)。）
  尚未匹配的取消仍然立即中止，避免为跳过掉的歌曲白下载。

资源到达但当前不可显示时，仍然关闭 FD 并安排一次 1 秒的有界重检，重检会命中刚写入的缓存，
不会长期占用文件描述符。

## 行为边界

- 关闭联调后，已经匹配的下载可能再跑最多 38 秒并落缓存；这是本规则的有意结果。
  若在插件里关闭来源，网络层会立刻以 `network_blocked` 中止。
- 切歌会为新歌发起新查询；如果旧歌已匹配，旧下载在后台完成并缓存，最多 1 个旧任务 + 1 个新任务。
- 单请求仍有 38 秒期限、20MiB 上限、单 worker 与租约上限，不会无限堆积。

## 测试包

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix6-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix6-debug.apk`

| 资产 | 文件 SHA256 |
| --- | --- |
| Bridge fix6 | `4E44DDA209D23B4FF5F9F4E377555506EF8FFCC295BA5E4C2D7597B0B17121C3` |
| AM fix6 | `0D3BD6F051EB643B4D670DE3FC9BDE5BFC731756FD9FA4A6DB2548B721C0E7AC` |

apksigner 验证通过，Debug signer 与 fix5 相同：
`5a6fd43fc11b4c70ff12b6f82a7261c2ed9c4945692b3d818ca50bc64c8dd869`。

用户自行重启 SystemUI 后重新开启 AM 锁屏联调。

## 复测重点

1. **首次缓存不再被显示状态打断。**清插件缓存，锁屏播放未缓存歌曲；等待期间故意息屏再亮屏、暂停再播放、切大小封面。
   期望：这些操作不再让下载从零开始；`ARTWORK_AM_HTTP` 不出现同一阶段的反复 `*_started`。
2. 观察 `ARTWORK_AM_DETACHED matched_download_completes` 是否出现；出现即表示客户端走了但下载仍在继续。
3. 恢复网络、同专辑跨尺寸复用、同长切歌、Explicit/Clean 回归。
4. 关闭联调后确认没有残留视频层；后台完成的缓存可在下次开启时立即命中。

JVM 不能覆盖真实锁屏回调与网络时序，本轮仍需设备日志确认取消时机。
