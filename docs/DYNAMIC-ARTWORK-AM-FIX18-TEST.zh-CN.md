# D1 fix18：动态封面播放时保持屏幕常亮

日期：2026-10-04。只改 Bridge，AM 插件沿用 fix17。

014800 日志确认 fix17 手动绑定生效：`ARTWORK_AM_BINDING reason=user_album` 9 次，其中 6 次 `bound_file_hit`，
1 次 `user_bound_album` 联网获取。该日志未覆盖“下载中切歌”场景，fix17 第 2 项仍待验证。

## 需求

用户提出：新增动态封面专属的屏幕常亮开关。大封面上动态封面播放时保持亮屏；暂停、手动按电源键进入息屏/AOD 时释放；
兼顾已有的“歌词显示时保持屏幕点亮”。这改变了方案文档中“不新增 WakeLock”的首版约束，改为用户显式开启的选项。

## 实现（仅 Bridge，Debug 联调范围）

- 新增 `ArtworkScreenAwake`，持有独立的 `SCREEN_BRIGHT_WAKE_LOCK | ON_AFTER_RELEASE`（tag `LockscreenLyrics:ArtworkKeepAwake`）。
  沿用歌词保活自 v1.7.1 起在本机验证过的机制：15 秒租约，每 8 秒续租并发送不改变亮度的 `userActivity`。
- 持有条件（全部满足）：开关开启；当前渲染面是大封面；视频首帧已上屏且未处于换面保留；
  大封面候选通过原有播放门禁（锁屏中、亮屏、正在播放、可见、几何与原生过渡完成等）。
  小卡片、静态封面回退、解析中、无动画专辑都不持有。
- 释放：
  - 暂停、切到小卡片、视频停止或候选不再满足条件：1 秒宽限后释放，避免单帧几何检查或换面造成抖动。
  - 息屏/AOD（`SCREEN_OFF`）、解锁（`USER_PRESENT`）、关闭开关、关闭联调：立即释放。
  - 续租时会重新检查条件，即使没有新的观察回调也会发现视频已停止；租约本身也会在 15 秒后自然过期。
  - 释放时 `ON_AFTER_RELEASE` 重置用户活动计时，之后按系统超时正常熄屏，不会立刻黑屏。
- 与歌词保活的关系：两把锁互相独立，任一持有即保持亮屏，任一释放不影响另一方。
  歌词保活的“自定义秒数”只约束歌词保活；动态封面保活仅随视频播放状态持有，没有时长上限。
- 配置：Bridge 动态封面调试页新增开关“动态封面播放时保持屏幕常亮”，默认关闭，保存在 Bridge 本地偏好中，
  “清除插件选择”不会重置它。开启联调时随配置发送；联调运行中切换开关通过独立广播
  `ARTWORK_KEEP_AWAKE` 即时生效，不重启视频。两条广播都受 Bridge 签名权限保护，仅 Debug 构建生效。
- 日志（Bridge 诊断 media 区域）：`event=ARTWORK_KEEP_AWAKE`，message 为 `state=held` 或
  `state=released reason=<原因>`（如 `paused`、`screen_off`、`unlocked`、`card_surface`、`no_video`、`disabled`），
  以及 `ARTWORK_TEST_CONFIG ... keepAwake=` 和 `ARTWORK_KEEP_AWAKE_CONFIG enabled=... testRunning=...`。

## 本地验证

- Bridge：727 项测试，0 失败，6 跳过（新增 `ArtworkScreenAwakeTest` 7 项：启用与播放门禁、续租与不叠加、
  暂停宽限释放、单帧间隙不抖动、无回调时恢复/停止、息屏/关闭/销毁立即释放）。
- Lint：0 errors / 48 warnings，变更文件未新增警告。
- 实际亮屏行为、ColorOS 锁屏超时与 AOD 交互属于设备行为，需要用户验证。

## 设备验证

1. 用户重启 SystemUI。Bridge → 动态封面：打开“动态封面播放时保持屏幕常亮”，再点“开启 AM 锁屏联调”。
2. 播放有动态封面的歌曲，锁屏切到大封面，等待动画出现后不触碰屏幕，超过系统锁屏超时仍保持亮屏；
   日志出现 `state=held`。
3. 暂停：约 1 秒后 `state=released reason=paused`，随后按系统超时熄屏。
4. 恢复播放并回到大封面后再次 `state=held`；按电源键进入 AOD：`reason=screen_off`。
5. 停留在小卡片：不持有（若刚从大封面切回则出现 `reason=card_surface`），小卡片按系统超时熄屏。
6. 关闭开关：立即 `reason=disabled`。同时开启“歌词显示时保持屏幕点亮”时，确认两者互不影响。

## 设备结果（023112 日志，用户已确认“完美”）

- 保活：`ARTWORK_KEEP_AWAKE_CONFIG enabled=true` 后，大封面动画上屏即 `state=held`；
  切歌换视频时 `released reason=no_video` 后重新 `held`，按电源键 `released reason=screen_off`，
  回到小卡片 `released reason=card_surface`，返回大封面再次 `held`。
- fix17 切歌下载：`ARTWORK_AM_DETACHED reason=started_download_completes` 约 1.6 秒后
  `ARTWORK_AM_READY width=1080` 与 `ARTWORK_AM_DETACHED_RESULT reason=ready_cached`，已开始的下载在服务销毁后完成并入缓存。
- 日志中无 SystemUI 或插件崩溃。

## 测试资产

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261004-fix18-debug.apk`
- AM 沿用 `artifacts/Artwork-Provider-AM-20261003-fix17-debug.apk`

| 资产 | SHA256 |
| --- | --- |
| Bridge fix18 | `3CD9E8AFCE1EC0775A151A768EDEB0A1B72106EBF99661D85E78C24428C7F419` |
| AM fix17（未变） | `E2D169B9FC8C5D4A477AE3E950444ECB3046E13A03558FC92DEE1621E20D6EA1` |

Bridge fix18 已用 `adb install -r -d` 覆盖安装，返回 `Success`。归档文件与构建输出哈希一致。
SystemUI 尚需用户手动重启。本地 Debug 测试包，不是正式发布版本。
