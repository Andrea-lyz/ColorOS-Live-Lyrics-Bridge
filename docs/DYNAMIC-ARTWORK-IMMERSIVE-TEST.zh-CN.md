# 本机单槽沉浸页：过渡与挂载测试

日期：2026-10-03。分支：`feat/dynamic-artwork-provider`。

本轮已实现 Debug 固定视频在本机 C16 样式沉浸封面上的测试挂载。
不是生产展示、歌曲匹配或 C17 双槽适配。所有歌曲使用测试插件中同一个视频，普通卡保持静态。
开关独立于 MEDIA debug；系统界面重启后默认关闭，不保存为正式展示配置或备份内容。

## 测试包与准备

- Bridge 当前修复版：`artifacts/ColorOS-Live-Lyrics-Bridge-artwork-C2-immersive-20261003-fix1-debug.apk`；原 C2 包保留作对照。
- 离线插件：`artifacts/Artwork-Provider-Local-20261003-debug.apk`；插件源码未改动，复用已有 Debug 构建。
- 两个包都是 Android Debug 签名，本地测试资产；插件无需在 LSPosed 中启用。

1. 用户覆盖安装 Bridge 和离线插件，确认 Bridge 的原有 LSPosed 作用域。
2. 打开“动态封面本地测试插件”，通过系统文件选择器导入方形 MP4，认可当前 Bridge 签名。
   视频限制沿用协议：H.264/HEVC、最长 120 秒、最大 1080px、20MiB、无旋转或加密。
   可用明显动态、边缘易辨认的视频检查首帧和裁剪。
3. Bridge 的“动态封面 · 本地测试”选择插件，先验证 App 内预览正常。
4. 开启 Bridge MEDIA debug 并保存；工作区根目录运行以下命令，再由用户重启系统界面。

```powershell
.\scripts\capture-lyrics-log.ps1 -Provider bridge
```

5. 重启完成后，再到本地测试页点击“开启沉浸页固定视频测试”。页面只说明已发送请求，
   不把发送成功当作 SystemUI 已挂载；日志应出现 `ARTWORK_TEST_CONFIG enabled=true mode=local_fixture`。
6. 播放音乐，锁屏进入沉浸页，切到封面不同的歌曲，等待官方过渡完成。
   首次开启时旧 TransitionDrawable 的起点未知，因此不会仅凭当前静止状态展示；需观察新的正向过渡。

## 验收操作

| 操作 | 预期 |
| --- | --- |
| 首次显示 | 原静态封面先保持；真实视频首帧后才淡入，无黑块、旧曲残帧；视频静音 |
| 连续切歌、快速切歌 | 官方静态过渡继续；旧视频先撤下，新绑定稳定且过渡完成后重新显示 |
| 切换大封面/歌词布局 | 布局动画期间退回静态；稳定后跟随实际 bounds，不能固定为正方形或覆盖歌词 |
| 封面点击、长按、返回 | 保持官方触摸与无障碍；原 View 不移除、不 reparent |
| 暂停/恢复 | 暂停回退静态并释放；恢复可以重新请求同曲固定视频，不伪造宿主 PlaybackState |
| 退出重进、解锁、息屏/AOD | 撤下视频和解码资源；息屏/AOD 不运行视频；亮屏后需重新满足门禁 |
| 插件停止、卸载、授权/签名变化 | 保留静态，旧回调不重新挂载；同一失败绑定不逐帧无限重试 |
| 关闭固定视频测试 | 视频层立即移除；原封面、歌词、控制与 AOD 行为正常 |
| 重启系统界面 | 测试开关重置为关闭，需手动再次开启 |

本轮暂停采用释放而非保留视频帧，不复用尚未验证的 pause/resume 生命周期。
测试结束在 Bridge 本地测试页点击“关闭沉浸页固定视频测试”，再结束抓取并关闭调试。
反馈完整日志、首次异常时间以及首帧/边缘/触摸的实际现象。代理未执行安装、播放、重启或设备日志抓取。

## 实现与证据边界

- `ArtworkImmersiveMount` 只接受确切的 SmartShapeableImageView → Material ShapeableImageView 与 FrameLayout。
  原 ImageView 继续绑定和绘制；增加非触摸视频层，复用官方 ViewOutlineProvider 的输出（包括 OPlus smooth 信息），
  同时复制唯一的非空凸 Material 内部裁剪 Path。外部减法 mask 不作为裁剪路径。
  非零 stroke、前景、tint、content padding、非 FIT_XY 或歧义 Path 均拒绝挂载。
- 视频沿用本机 FIT_XY，匹配 `1312×1312 → 1312×1284` 的实际尺寸变化。几何同步按变化执行，
  不在稳定的每一帧重新遍历反射字段。PreDraw 仅属于已开启测试的已挂载沉浸宿主，结束时移除。
- 过渡使用公开 `startTransition/reverseTransition/resetTransition/draw/invalidateSelf`，按实例限定在当前沉浸 drawable。
  已观察正常正向开始，且一次完整 draw 不再请求 invalidateSelf 才认为完成；未知初始/反向/重置保持静态。
  依据 Android 16 AOSP TransitionDrawable.draw：运行阶段请求 invalidateSelf，终止分支直接绘制并返回。
  参考：[Android 16 AOSP 源码](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/graphics/java/android/graphics/drawable/TransitionDrawable.java)。
  本机实际 Hook 触发与 OEM 行为仍需本轮日志验证。没有使用隐藏状态字段或固定时长门禁。
- 生效请求经现有签名权限保护的 SystemUI 设置接收器，Release 解析强制关闭。只接受 v1 的 localTestOnly 插件，
  组件/当前签名重新验证，资源检查与 IPC 沿用有界后台队列。测试 query 不发送真实曲名、media ID、session 或歌词。
- source/card/controller 的精确绑定仍沿用 fix4。资源交付和 renderer 的准备、启动、首帧、显示复核本地归属；
  service epoch、config revision 与 client 生命周期进入 render stamp，旧绑定失败即释放 FD/播放器/Surface/View。
  解码器进程内最多一个，唯一可见沉浸候选才挂载；多候选保持静态。生产模式与配置同步尚未实现。

## 日志判断

fix1 修复了 attach 前 startTransition 被丢弃的时机缺口：测试开启期间，正向开始及反向/重置事件存入最多 32 项的弱引用缓存。
实际封面在 owned draw 时才认领状态；缓存压力只淘汰未认领观察，不清空当前宿主状态。
绘制栈最多 8 层，避免嵌套绘制覆盖外层记录。停用测试/loader 更换清空缓存，detach 或换 drawable 撤销认领。
这不允许未知初始状态直接展示，不新增固定延时或隐藏字段读取。

`ARTWORK_TEST_TRANSITION` 新字段：`stage=startTransition|draw|reverseTransition|resetTransition`、
`ownerAttached`、进程内 `drawableRef`、`forwardKnown`、`drawing`、`drawInvalidated`、`complete`。
同一 drawableRef 上，attach 前开始可以为 `ownerAttached=false forwardKnown=true`；
后续 owned draw 若 `drawInvalidated=false complete=true` 才通过过渡门禁。
`forwardKnown=false` 表示没有可靠正向起点；`drawInvalidated=true` 表示本次绘制仍要求重绘。
编号不包含原始 drawable/token/媒体 ID，也不跨进程比较。

本地 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 已通过：683 个测试，677 通过、6 个已有 fixture 跳过，
新增 3 个过渡门禁测试通过；Lint 0 errors、48 warnings。没有隐藏 API 抑制、baseline 或关闭检查。
JVM 测试不执行真实 View、Path、Texture、Binder 或解码；构建成功不代表设备视觉验收。

fix1 新增 4 个 registry 回归，覆盖 attach 前开始与精确实例认领、反向/替换、嵌套绘制，以及缓存压力不淘汰当前封面。
本地 687 个测试：681 通过、6 个已有 fixture 跳过；Lint 0 errors、48 warnings，Debug 构建通过。

```text
ARTWORK_TEST_CONFIG
ARTWORK_TEST_HOST_CHECK / ARTWORK_TEST_GATE
ARTWORK_TEST_TRANSITION complete=false -> complete=true
ARTWORK_TEST_MOUNTED
ARTWORK_TEST_RESOURCE_STATE selecting -> connecting -> resolving
ARTWORK_TEST_RENDER_STATE preparing -> waiting_first_frame -> playing
```

`MOUNTED` 只证明测试层建立，不代表首帧已显示；必须结合 `playing` 和视觉现象。
`ARTWORK_TEST_MOUNT_REJECTED` 提供固定结构原因码；`ARTWORK_TEST_FALLBACK` 说明资源或展示失败。
同 owner/event 相同内容会去重，不要求每次退出都输出相同记录。
旧 `ARTWORK_SESSION_BOUND display=disabled` 仍表示正式展示关闭，不能代替独立 `ARTWORK_TEST_RENDER_STATE`。
本机 C1 的材质和尺寸证据不证明 TextureView 的实际合成、圆角或 touch 验收，必须由本轮设备现象确认。
