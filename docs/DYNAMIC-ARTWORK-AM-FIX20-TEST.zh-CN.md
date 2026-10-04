# D1 fix20：开关重开后大封面不再停在静态

日期：2026-10-04。只改 Bridge，AM 插件沿用 fix17。

## 设备反馈与日志（033854）

用户确认 fix19 正式设置大部分正常；但关闭再开启“大封面”后，大封面完全不播放。日志时间线：

| 时间 | 事件 |
| --- | --- |
| 03:42:29 | 关闭小卡片（`card=false largeCover=true decoderKept=true`） |
| 03:42:44 | 两处都关闭，`source=off`，显示整体停止 |
| 03:42:46 | 显示停止期间切歌，原生封面开始交叉淡入 |
| 03:42:53 | 先开小卡片（新建播放），再开大封面（`decoderKept=true`） |
| 之后 | 大封面门禁持续 `native_transition`；唯一的过渡记录为 `drawableRef=3 forwardKnown=false complete=false` |

原因：过渡观察只在显示运行时记录 `startTransition`，停止期间切歌的淡入开始没有记录；新建播放时又清空了已有记录。
设计上“未观察到开始的过渡保持静态”，因此该封面 Drawable 一直不满足大封面门禁。小卡片不依赖这一记录，所以不受影响。

## 修复

- `startTransition` / `reverseTransition` / `resetTransition` 在显示停止期间也记录（调用很少，开销可忽略）；
  逐帧的 `draw` 和 `invalidateSelf` 仍只在显示运行时处理，关闭时不增加绘制路径开销。
- 切换配置、重建播放时不再清空过渡记录；记录为弱引用且有上限，宿主释放时照旧撤销认领，重新绘制后才可再次放行。
- 文案：“锁屏小卡片”改为“歌词小封面”，状态提示、亮屏说明、页面描述和调试提示同步（英文为 Small lyric cover）。

边界：过渡观察钩子在首次启用动态封面时安装。若在某首歌播放中途第一次启用，那首歌的大封面仍可能保持静态，
切到下一首后恢复；启用过一次后（包括重启 SystemUI 后按保存的设置自动启用）不再出现。

## 本地验证

- Bridge：737 项测试，0 失败，6 跳过（新增过渡记录跨显示停止后仍可完成的回归测试）；Lint 0 errors / 48 warnings。

## 设备验证

1. 用户重启 SystemUI。
2. 播放中关闭“歌词小封面”和“大封面”，切一首歌，再依次打开两者：锁屏大封面应恢复动画。
3. 单独关闭再打开“大封面”、关闭再打开总开关，分别确认大封面恢复。
4. 设置页显示“歌词小封面”。

## 设备结果（035838 日志，用户确认“应该好了”）

- 03:59:22 两处都关闭（`source=off`），关闭期间发生一次切歌（`ARTWORK_SESSION_BOUND`）。
- 03:59:31 依次重新开启大封面与歌词小封面；03:59:35 大封面 `state=playing`，03:59:38 歌词小封面播放，03:59:40 回到大封面再次播放。
- 保持亮屏随之 `held` / `released reason=card_surface` / `held`；日志无 SystemUI 崩溃。

## 测试资产

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261004-fix20-debug.apk`
- AM 沿用 `artifacts/Artwork-Provider-AM-20261003-fix17-debug.apk`

| 资产 | SHA256 |
| --- | --- |
| Bridge fix20 | `EC61BF2B2C8AD4751DC8F18913D54F6C45E6C01FBEC47D9E9DB6DC7B356F74AC` |
| AM fix17（未变） | `E2D169B9FC8C5D4A477AE3E950444ECB3046E13A03558FC92DEE1621E20D6EA1` |

Bridge fix20 已用 `adb install -r -d` 覆盖安装，返回 `Success`。SystemUI 尚需用户手动重启。
本地 Debug 测试包，不是正式发布版本。
