# C3：本机小卡片固定视频测试

日期：2026-10-03。分支：`feat/dynamic-artwork-provider`。

当前已实现本机 C16 小卡片的 Debug 挂载，并继续支持已验收的大封面。
同一进程只有一个视频播放器，唯一有效大封面优先；没有有效大封面时，唯一有效小卡片才播放。
仍是固定本地视频模式，不做 AM 歌曲匹配或联网，不代表全部 C16/C17 profile 已适配。

## 测试包与视频

- Bridge：`artifacts/ColorOS-Live-Lyrics-Bridge-artwork-C3-card-20261003-debug.apk`，Android Debug 签名。
- 本地插件无需更新；沿用此前安装的插件和签名授权。
- 小卡片推荐导入用户提供 AM 源修复后的 `artifacts/artwork-test-media/1989-am-360-fixed-h264.mp4`。
  360×360、16.73 秒，H.264 8-bit，约 287KiB。
- 本插件当前只有一个固定视频，选 360 样本后大封面也使用该文件；这不是按 surface 自动选择 AM variant。
  大封面清晰度检查可另开窗口换回 1080 样本。以后生产插件才按实际物理尺寸选最低足够分辨率。

## 操作步骤

1. 用户覆盖安装 Bridge，在本地插件导入上述 360 视频，在 Bridge 本地测试页选择插件并确认 App 预览正常。
2. 开启 Bridge MEDIA debug 并保存。在工作区根目录运行下列命令，先抓取，再手动重启系统界面。

```powershell
.\scripts\capture-lyrics-log.ps1 -Provider bridge
```

3. 重启完成后，点击“开启锁屏固定视频测试”。此开关默认关闭、独立于 debug，重启后需重新开启。
4. 播放音乐，锁屏停留普通卡片；若进入沉浸页，切到图2对应的歌词布局，让大封面隐藏、小卡片显示。
   原生 bitmap 翻转结束后，小卡片应从静态切换到视频。
5. 连续切歌、快速切歌：先撤下视频，原生翻转/抖动继续，特效结束且会话稳定后再显示。
6. 暂停/恢复、拖动歌曲进度、封面点击/长按、歌词按钮和控制按钮：暂停恢复保持此前策略，视频独立循环；
   点击和长按继续属于原 ImageView，视频层不接收触摸或无障碍焦点。
7. 大封面 → 歌词/小卡片 → 大封面重复三次；检查始终只有当前选中位置显示，且无旧层、跳位或歌词遮挡。
8. 退出、解锁、息屏/AOD、再次亮屏，最后关闭固定视频测试。检查静态封面和既有歌词/AOD 行为。
9. 提供完整日志和首次异常时间。视觉重点是小封面的圆角/透明边缘、位置/尺寸，以及视频是否被 mask 完全遮掉。

代理没有执行安装、播放、重启或设备日志抓取。构建成功不代替本轮视觉验收。

## 实现与静态证据

- `ArtworkCardEffectAccess` 限定本机 C16 user 的 RotatableImageView profile：唯一 Bitmap、AnimatorSet、
  原生 spring 类型 `j1.h` 的真实 running 字段 `g`；基类唯一 Paint/RuntimeShader 与 getUseSuperDraw 契约。
  原生 AnimatorSet started/running 或 spring running 时拒绝。结构变化、普通 draw 路径或缺 bitmap 保持静态。
  证据为 `PlayerSource/SystemUIPlugin/jadx-user/sources/.../RotatableImageView.java`、`s7/a.java` 和 `j1/h.java`。
- `ArtworkCardMount` 留下原 ImageView，添加不接收触摸的独立层，CENTER_CROP 播放。
  用自己的 Paint 对原生 RuntimeShader 的 alpha 输出做 DST_IN 合成，保留其当前静止轮廓与 bitmap 透明边缘。
  只读原 shader；不改 uniforms、bitmap、useSuperDraw，不取消/替换原生动画。
  原 shader 的 RGB/光照不用于染色视频；静止视觉一致性和 GPU 成本仍需设备确认。
- 本机父容器是 MediaPlayerCardPageRootView/插件侧 ConstraintLayout。使用同 loader 的全新布局参数，
  不复制原 widget 或改原 View 约束。实际 DEX 将参数类混淆为 `r.d`，`d` 为 left-to-left、`h` 为 top-to-top。
  已从 `SystemUIPlugin_16.001.002_user.apk` 导出并核对构造器、resolveLayoutDirection 与 ConstraintLayout 消费代码。
  声明类/字段不匹配时不挂载；不把标准 AndroidX 参数名称假定为插件 ABI。
- 小卡片依赖 RuntimeShader，SDK 33+ 才解析/启用；原 minSdk 与普通卡 native 行为不变。
- 原生 bitmap 或 shader 角度更新前立即失效旧视频；PreDraw 检查真实特效状态与会话/可见性，静止布局只同步变化。
  共享 client/FD/renderer 时效链。位置切换关闭旧播放器、Surface 和临时层，不创建并行解码器。

## 日志

```text
ARTWORK_CAPABILITY_RESOLVED cardProfile=c16_user_shader cardHooks=true
ARTWORK_TEST_SELECTION eligibleCards=1 eligibleLargeCovers=0 selected=LOCKSCREEN_CARD
ARTWORK_TEST_MOUNTED surface=LOCKSCREEN_CARD shape=native_shader_alpha
ARTWORK_TEST_RESOURCE_STATE surface=LOCKSCREEN_CARD state=selecting|connecting|resolving
ARTWORK_TEST_RENDER_STATE surface=LOCKSCREEN_CARD state=preparing|waiting_first_frame|playing
```

大封面正常时 selection 应为 IMMERSIVE；多个同优先级有效候选时保持静态，不随意选第一项。
`ARTWORK_TEST_CARD_EFFECT` 说明更新阶段和当时特效状态；实际展示还需 selection/playing 与画面共同确认。
capability unsupported 或 mount rejected 只说明当前 profile 未匹配，不代表已修改原封面。

## 本地验证

执行 `scripts\dev.cmd bridge :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。
689 个测试，683 通过、6 个已有 fixture 跳过；新增两个 surface 调度测试覆盖大封面优先和同级歧义拒绝。
Lint 0 errors、48 warnings。之后核对实际混淆布局契约并重跑单元测试与 Debug 构建。
JVM 没有执行真实 shader、ConstraintLayout、TextureView 合成或触摸；这些属于本轮设备验收。
协议、插件和歌词 Providers 未改变，没有运行 Provider 全矩阵、安装或发布。
