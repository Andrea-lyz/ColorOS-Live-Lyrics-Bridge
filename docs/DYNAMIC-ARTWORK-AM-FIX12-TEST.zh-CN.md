# D1 AM fix12：锁屏周期后的请求卡死与换面观感

日期：2026-10-03。对应 `lyrics-log-20261003-205833.txt`。用户反馈：Style (Taylor's Version) 小卡首次加载仍卡住，
锁屏流程为“电源键 → AOD 暗屏（小卡）→ 手动点亮”；换面接力有变化，但切换时画面抽搐，
不如原先“静态封面 → 动态封面”的观感。

## 本次证据

### 小卡首次加载

```text
20:59:59.427  Keyguard locked screen off（进入 AOD）
21:00:00.733  小卡 eligible，clientEpoch=9 发起请求
21:00:03.683  ARTWORK_AM_HTTP reason=playlist_started      （线程 4633）
21:00:20.283  用户切大封面 → ARTWORK_AM_DETACHED；新请求 21:00:20.296 → 21:00:21.348 READY
```

playlist 预算 6 秒。fix11 的等待方用自身期限等待，到期后应记录 `playlist_stalled_in_*` 和重试。
但线程 4633 此后直到日志结束都没有任何输出。等待方到期后唯一可能阻塞的位置，是它在放弃阶段时同步调用的
`HttpURLConnection.disconnect()`。另一种可能是整个 AM 进程在锁屏周期后暂停运行。两者都与锁屏流程相关：
截至目前，每次卡住都是亮屏后的第一次请求。日志过滤只含本项目 tag，无法区分这两种情况。
两次日志都显示，Bridge 重新绑定并发起新请求后，约 1 秒即可完成。

### 换面

日志中每次换面都有 `HELD → PREVIEW → HANDOVER warmDecoder=true`，`clientEpoch` 不变，逻辑上接力成功。
用户在设备上看到的抽搐来自 fix10 加入的视觉过渡：旧视频层只在视图回调时跟随原生动画，新表面又叠加了一张 512×512 预览帧，
两者都会与原生切换动画错位。

## 实现

### AM 插件

- 放弃阶段时，`disconnect()` 交给独立的有界线程池（最多 2 个）异步执行，等待方、binder 线程和 reaper 都不再同步调用它；
  线程池占满时跳过 disconnect，该阶段照常放弃。交换线程池上限由 4 增至 6。
- 卡住时记录 `<stage>_stalled_in_<phase> elapsedMs=… budgetMs=… frames=… importance=…`：
  frames 是卡住线程的前 12 个栈帧（仅类名与方法名），importance 是 AM 进程当时的优先级。
  `elapsedMs` 远大于 `budgetMs`，说明等待方自身没有运行（进程被暂停）。

### Bridge

- **请求卡死看门狗**：发起请求后 10 秒仍无结果，记录 `ARTWORK_RESOLVE_STALLED`，解除绑定后重新请求；
  每首歌最多 2 次，成功一次后计数清零。只在锁屏亮屏且有可显示的表面时重启，否则每 2 秒再检查一次。
  旧请求在插件内按“已开始即完成”继续落缓存。
- **换面观感**：保留期间直接隐藏旧视频层，不再跟随原生动画，也不再在新表面叠加预览帧，
  切换过程完全由官方静态封面完成。解码器保持运行，新表面稳定后接过同一个播放器，
  首帧到达时用 200 ms 淡入。观感回到“静态 → 动态”，但省去重新解析和 prepare。
  `ARTWORK_SURFACE_PREVIEW` 不再出现，`ARTWORK_SURFACE_HELD` 改为 `layerHidden=true`。

主要源码入口（仓库相对路径）：

- `artwork-provider-am/src/main/java/io/github/andrealtb/artwork/am/AmNetwork.java:242`：卡住时的诊断描述。
- `artwork-provider-am/src/main/java/io/github/andrealtb/artwork/am/AmNetwork.java:262`：异步 disconnect。
- `app/src/main/java/io/github/andrealtb/lockscreenlyrics/systemui/artwork/ArtworkImmersivePlayback.java:382`：保留期间隐藏旧视频层。
- `app/src/main/java/io/github/andrealtb/lockscreenlyrics/systemui/artwork/ArtworkImmersivePlayback.java:400`：请求卡死看门狗。

## 本地验证与边界

- Bridge：720 项，714 通过、6 项原有 fixture 跳过，0 失败；Lint 0 errors / 48 warnings。
- AM：70 项全部通过；Lint 0 errors / 4 warnings。新测试模拟连接与 `disconnect()` 同时阻塞：
  三次尝试约 1.2 秒内结束，`stalled_in` 诊断包含卡住位置的栈帧。
- 卡住的根因（disconnect 阻塞还是进程暂停）尚未确认，需要本版日志中的 `stalled_in`/`elapsedMs`/`importance` 判断。
  看门狗属于兜底，不能替代根因定位。换面观感需要用户在设备上确认。

## 复测顺序

1. **先手动重启 SystemUI**，重新开启“AM 锁屏联调”。
2. **锁屏流程复现小卡首次加载**：清 AM 缓存，按“电源键 → AOD → 手动点亮”，切几首歌重复。
   期望小卡最慢约 10 秒出现动画（看门狗兜底），正常情况下 2–3 秒。
   日志应出现 `stalled_in_…`，或者 `ARTWORK_RESOLVE_STALLED restart=true` 后重新 `resolving` 并 `READY`。
   请保留这些行，用于确认根因。
3. **换面**：大↔小各切 5 次以上，含快速切换。期望切换过程只看到官方静态封面的动画，
   稳定后视频淡入，不再抽搐；同一首歌内 `clientEpoch` 保持不变。
4. 回归暂停/继续、切歌、解锁、息屏/AOD、关闭联调，检查静态回退与无残留层。

## 测试资产

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix12-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix12-debug.apk`

| 资产 | SHA256 |
| --- | --- |
| Bridge fix12 | `00B73DE5A315CAC2782E3BD8CC0234EF125E40E35F2823AC89B1A2BDA85D1706` |
| AM fix12 | `FA78F6B4F68C9467D9086657647A29334248DD3F4DB5D064D915D8EC0EFECE40` |

两包已通过 `scripts\dev.cmd install` 覆盖安装到已连接设备，adb 均返回 `Success`。
归档文件与对应构建输出哈希一致。SystemUI 尚需用户手动重启。
本地 Debug 测试包，不是正式发布版本。
