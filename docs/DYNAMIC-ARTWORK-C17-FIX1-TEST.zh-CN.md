# C17 fix1：绘制回调安全回收与全屏封面常亮

日期：2026-10-05。分支：`feat/dynamic-artwork-provider`。
状态：已收到 C17 DSU 首轮复测日志与用户“好像没啥大问题了”的初步反馈；本窗口基本链路正常，
不是完整设备矩阵或正式发布验收。沿用现有 Artwork Provider。

## 证据与原因

- 用户复现窗口 `lyrics-log-20261005-064733.txt`：06:48:17.269 出现 `stale_render`，
  06:48:17.277 SystemUI 在 `TextureView.draw()` / `TextureLayer.setLayerPaint()` 空指针崩溃。
  经授权只读 ADB 调查，crash buffer / exit-info 另确认 06:48:40.636 同类 Java 崩溃。
- 从当前 C17 DSU framework 核对：`draw()` -> `applyUpdate()` -> `onSurfaceTextureUpdated()`，
  回调返回后 `draw()` 继续读取 `mLayer`。旧回调经 `current()` -> `stale_render` 通知 ->
  playback failure -> mount.close 同步移除 View，detach 将 `mLayer` 清空，与故障吻合。
  不能把这条模块回收链路归为 DSU 偶发噪声；没有修改 framework 或添加全局异常吞噬 Hook。
- App 与 SystemUI 的两处常亮设置均为开启。初版 `background()` 无条件排除常亮，
  而本窗口实际使用 C17 全屏背景 renderer，这是动态封面常亮的覆盖缺口。
- 歌词常亮在设备电源历史中有真实获取记录；本崩溃窗口歌词 RecyclerView 隐藏、0×0，
  因此本轮不把歌词常亮判为已证实失效，也不改动歌词常亮实现。

私人原始日志与提取的 framework 保留在工作区日志目录，不入库。

## 修复

1. `DynamicArtworkRenderer` 的 TextureView 帧回调仅立即检查并撤销失效 lease、隐藏失效输出，
   状态通知、首帧确认与可能拆层的处理交给主线程消息，在当前 draw 调用返回后执行。
   不在回调内调用会触发同步拆层的 `current()`。
2. 新增 `ArtworkFrameDispatch` 合并待处理帧；stop、close、handover 清空旧任务，
   执行时再复核 session/View/SurfaceTexture 归属。快速息屏再亮屏不会复活已失效 lease。
3. available 回调中的准备/失败路径也延后执行；destroyed 回调立即撤销 lease，
   回收延后且只针对原 session/View。保留 sizeChanged 中不涉及拆层的几何同步。
4. C17 从 controller 的 `p` 字段读取歌词模式。依据为插件 `section/media/V0.java:101–108`：
   lyric mode flow 写入 p，同时驱动全屏歌词模糊。只有已确认的大封面模式下，
   可见且实际播放的全屏动态封面才加入现有常亮锁。
5. 切入歌词模式、暂停、息屏/AOD、解锁或关闭设置继续释放动态封面常亮；歌词常亮独立控制。
   C16 普通大封面的常亮条件保留，小卡与未知背景不取得该锁。

本次未安装 APK、修改设备设置、重启 SystemUI、推送或发布。

## 本地检查

执行 `scripts/dev.cmd bridge :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。
新增 7 项回归覆盖绘制返回前禁止拆层、失效不可复活、帧合并、旧任务撤销、重入排队，
以及 C17 全屏封面/歌词背景和 C16 旧路径的常亮边界。
结果：752 项测试，746 通过、6 项既有 fixture 跳过，0 失败；Lint 0 errors / 50 warnings；Debug 构建通过。
JVM 测试验证调度及策略，不能替代真实 Android 绘制栈或常亮验收。

测试包：[ColorOS-Live-Lyrics-Bridge-artwork-C17-fix1-20261005-debug.apk](../../artifacts/ColorOS-Live-Lyrics-Bridge-artwork-C17-fix1-20261005-debug.apk)。
SHA-256：`A5F18079E7D1709514338655539DBBF317EC257C4A1636F615E61977A76BFEF2`。
这是本地 Debug 测试包，不是正式发布资产；构建交付时未代为安装，后续用户复测见下。

## 2026-10-05 DSU 首轮复测结果

依据 `lyrics-log-20261005-070925.txt`，窗口 07:09:29.528–07:12:10.723：

- SystemUI 日志 PID 保持 15796，未见 FATAL EXCEPTION、Fatal signal 或原 TextureView 空指针。
  07:11:54 的 AndroidRuntime `VM exiting with result code 0` 属于另一个 PID 18579，不能当成 SystemUI 崩溃。
- 07:09:49.613 全屏视频进入 playing，49.615 动态封面常亮 state=held；07:10:16.634 暂停，
  17.638 释放 reason=no_video。07:10:31.392 恢复播放，31.393 再次取得常亮锁。
- 07:10:56.898 歌词模式开启；07:10:58.189 动态封面锁按 lyric_background_or_unknown 释放。
  歌词常亮在 07:11:04.941、12.952、27.226 有 user activity pulse；07:11:45.366 条件变化时释放歌词锁。
  07:11:44.902 动态封面锁再次 held，支持两条常亮逻辑按模式交接。
- 07:11:19、35、40 的背景状态有 ready=false→true，23.275 与 41.090 视频重新进入 playing。
  07:11:36.118 的 catalog_album_unconfirmed 属于 Provider 专辑确认暂缓，已进入静态回退并安排重试；
  后续切歌的视频恢复不能当作该专辑的重试成功。
- 本窗口未出现 stale_render，不能声称已强制覆盖原崩溃分支；没有新的运行时异常证据需要继续改代码。
  原有 lyric position controller 未解析的降级提示仍在，不是本轮新回归。

结论：记录为 C17 DSU 上播放/暂停恢复、常亮模式交接的初步复测通过；用户未见明显问题。
快速息屏/亮屏对原失效分支的覆盖、长期资源预算、其他系统组合和镜像模糊逐帧视觉验收不据此标记完成。
本次仅更新记录，未操作设备、修改代码或重新构建。

## 设备复验

1. 使用 fix1 Bridge Debug 包与原 Provider；在已开启动态封面常亮时，停留全屏封面超过原锁屏超时，
   确认日志出现 `ARTWORK_KEEP_AWAKE state=held`，屏幕继续亮起。
2. 切入歌词模式，确认动态封面锁释放，歌词常亮按自身开关工作。分别关闭其中一个开关验证相互独立。
3. 关闭两处常亮让屏幕自然超时，再快速息屏/亮屏、切歌、退出/进入沉浸页，
   复验原 `stale_render` 路径不再导致 `TextureView.draw()` 崩溃。
4. 暂停、解锁、关闭动态封面后检查无残留常亮；封面及镜像/模糊仍同步，失败回到官方静态。

按 [日志入口](../../LOGGING-DEBUG-CAPTURE.md) 保留同一复现窗口。设备操作由用户执行或另行明确授权。
