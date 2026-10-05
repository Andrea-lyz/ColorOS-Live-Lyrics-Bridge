# C17 动态封面首轮实现与设备验证

日期：2026-10-05。分支：`feat/dynamic-artwork-provider`。
依据：[C17 专项计划](DYNAMIC-ARTWORK-C17-PLAN.zh-CN.md)。

状态：**已实现本地测试候选；C17 设备尚未验证。** 本文不代表正式发布或完整 C17 验收。
Provider、Square 协议、模块入口、依赖与 scope 均未改变。

后续 DSU 设备反馈已定位绘制回调崩溃和全屏封面常亮缺口，见 [C17 fix1](DYNAMIC-ARTWORK-C17-FIX1-TEST.zh-CN.md)。
本文保留初版实现与构建记录；复测应使用后续修复包。

## 1. 本轮改动

### 宿主识别与过渡

- `ArtworkCompatibilityResolver` 用 controller 和背景类的 DexKit 字符串锚点定位能力，
  `ArtworkC17Access` 校验本样本字段类型、section/controller 归属、双槽资源与原生 View 指针。
  不加载 JADX 自动重命名后的类名；不满足契约时保留静态。
- 不再仅以 `splitModel` 拒绝全部展示：小卡和沉浸能力分别解析，仍然依赖精确 session 绑定与现有 epoch/generation 门禁。
- 前景读取 controller 当前/下一槽及真实 animator/pending 状态；背景只从当前 renderer 取得 q，
  检查背景 running/pending 与 controller 的 renderer 切换、待提交模型。没有 250/600 ms 定时放行。
- Hook 封面设置、背景更新与形状更新，在已绑定宿主的原生更新之前撤销旧视频；已有 pre-draw 观察在真实状态稳定后选取展示目标。
  Hook 使用现有安装器，按插件 loader 生命周期安装/释放，部分安装失败会回滚。
- APK 中 `r8.X.onAnimationEnd()` 已额外用 JADX fallback 指令形式核对：前景结束交换 r/s、隐藏旧槽、清空 t，再处理 u。
  插件 APK SHA-256：`2CDD17E165FCFD2E257BF863C70D85F3978534381F7019038F1A5FAD912667EB`。

### 小封面、前景与背景

- 小卡新增 C17 RotatableImageView 的 spring/运行状态解析；沿用原生 shader alpha 裁剪。
  C17 挂载在原生 `header_container` 的 RelativeLayout，C16 继续用原路径。不强制恢复官方隐藏的小封面。
- `ArtworkC17Mount` 前景跟随当前槽实际几何与原生 smooth outline，保留 foreground 描边，采用等比裁剪。
- 全屏背景使用 **一个 MediaPlayer、一张 TextureView、一次解码帧**；RuntimeShader 在同一个输出中重采样中央、上镜像和下镜像。
  不创建三个播放器、不逐帧提取 Bitmap、不改 Provider 资源。几何取自原生三张 ImageView，拒绝不连续或非方形的未知布局。
- 动态层是 `MediaFullScreenContentView` 的子层，位于原生遮罩和上下 BackdropBlurView 下方。
  原布局的 `onLayout()` 只布局三张图，因此动态层自行同步尺寸和布局；仍继承父容器的 RenderEffect。
- 额外核对 `r8/H4.java` 的 `i(boolean)`：歌词模式还会给整个 contentContainer 设置半径 50 的模糊。
  动态层放在容器内部以保留该效果；没有改写或关闭原生渐变模糊、颜色遮罩和歌词模式模糊。
- 沿用单解码器选面策略：全屏 renderer 实际显示时选择整个动态背景，否则选择当前前景槽；不会把背景三部分拆开播放。
  不支持同时驱动多个独立动态输出；实际模式下是否需要同时显示前景视频与背景视频仍须设备确认。

### 生命周期与设置

- 首帧、循环、暂停、息屏、AOD、解锁与错误回退沿用原渲染器；镜像来自同一输出，所以三部分一起显现/隐藏。
- 背景 renderer 可独立于 section detach/re-attach，已增加撤销失效尝试及重新评估，避免已停止的解码器阻塞重播。
- 未改动官方静态图，失败/关闭时移除动态层；不留下中央动态、上下静态的部分挂载。
- 背景只在原生模糊对象存在且对应层可见时进入候选；**这仅是结构门禁，不证明 GPU 已正确采样视频。**
- 大封面开关同时控制支持的全屏背景；中英文设置说明同步更新。没有增加偏好 schema。
  仅背景播放不自动取得现有“大封面保持亮屏”锁，避免扩大常亮设置范围。

## 2. 与计划的关系及未完成项

| 切片 | 本轮状态 |
| --- | --- |
| C17-A | 本地能力解析、状态门禁与日志已实现；实际设备命中待确认 |
| C17-B | 小卡/双槽前景挂载已实现；形状、显隐和切换观感待设备确认 |
| C17-C | 同帧镜像背景和官方模糊复用路径已实现；真实视频采样待设备确认 |
| C17-D | 条件分支尚未实施；先验证官方模糊，若确实无法处理视频再做自有模糊合成 |
| C17-E | 生命周期门禁及本地测试已接入；持续资源预算、其他设备与用户验收未完成 |

计划中 GL/EGL 为候选技术，本轮选择 framework RuntimeShader/RenderEffect 完成同帧重采样，
继续复用已有 MediaPlayer/TextureView 和单解码器生命周期。它避免新增 GL 线程和跨 Surface 解码共享逻辑；
不能据此宣称更省电或已通过 GPU 兼容验证。

若官方模糊只显示旧静态画面、视频层未正确参与效果、AGSL/TextureView 合成异常或镜像不连续，
本候选不能作为“完整 C17 动态背景”验收。关闭该展示区域回到官方静态，按同一复现窗口定位，
必要时继续计划 C17-D；不会把模糊对象存在视为最终成功。

## 3. 本地验证

命令：`scripts/dev.cmd bridge :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。

- 新增 8 项测试：双槽交接、pending 连切、前景/背景独立完成、镜像拼接/横竖屏几何，以及字段类型/静态字段拒绝。
- 首轮 lint 发现新增 RenderEffect 引用缺少 API 声明，已补上构造器 API 契约，调用入口保留 SDK 33 门禁。
- 最终本地结果：745 项测试，739 通过、6 项既有 fixture 跳过，0 失败；Debug 构建通过。
  Lint 0 errors / 50 warnings；本轮新增两处 getIdentifier 警告用于读取插件运行时资源，
  Bridge 自身 R 不能替代该 ID。未新增 baseline 或关闭检查。
- JVM 测试不执行真实 TextureView、AGSL 编译、OPlus 私有图形效果或设备耗电测试。
  Provider 未改动，因此未运行 Provider 全矩阵。

本地测试包：[ColorOS-Live-Lyrics-Bridge-artwork-C17-initial-20261005-debug.apk](../../artifacts/ColorOS-Live-Lyrics-Bridge-artwork-C17-initial-20261005-debug.apk)，
9,986,174 bytes，SHA-256：`31D451DC526DF41B1AC6953A39E85532F63AC11779E0719692BA99669EC5DFA9`。
APK 位于工作区 artifacts，不入库；没有提交、推送、发布或安装。

## 4. 设备验证步骤（由用户执行或另行明确授权）

1. 使用本轮 Bridge Debug 测试包与现有动态封面 Provider；沿用原总开关与授权流程。
   本地 Debug 包不是正式签名发布候选。
2. 开启 MEDIA 诊断并按 [日志入口](../../LOGGING-DEBUG-CAPTURE.md) 收集同一复现窗口；
   记录设备型号、实际 SystemUI/插件版本及各步骤时间。未代理安装、播放、切歌或抓取设备日志。
3. 播放一张已缓存、运动明显的 Square 动态封面：卡片页检查小封面、点击响应；
   切换大封面/歌词模式，检查小封面按官方隐藏、前景圆角描边与背景层级。
4. **重点录制中央、上下延展和模糊区域**：三处应同时运动、同时循环，不出现边缘静态、倒影错位或接缝。
   歌词模式的整体模糊也应作用于动态内容，歌词和按钮不被视频遮挡。
5. 连续快速切歌，并在过渡中切换模式；检查 pending 最终跟随最新歌曲，没有错槽/旧帧复活。
6. 暂停/恢复、息屏/AOD/解锁、关闭后重开、反复进入/退出沉浸页；检查恢复播放与资源释放。
   无资源/失败歌曲应完整保留当前歌曲官方静态背景。
7. 进行横竖屏/窗口配置及持续播放对照，记录掉帧、内存、FD、解码器数量和温升，再决定是否需要自有模糊及性能优化。

诊断重点：`ARTWORK_CAPABILITY_RESOLVED` 的 `c17ProfileReady` / `cardProfile=c17_shader`，
`ARTWORK_C17_SURFACE` 的 fullscreen/ready，以及现有绑定、资源、首帧与 fallback 事件。
未出现 marker 只代表触发尚未证明，不直接归因于 shader 或模糊算法失败。
