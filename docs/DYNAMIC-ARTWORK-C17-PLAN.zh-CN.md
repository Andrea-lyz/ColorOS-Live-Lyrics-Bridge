# C17 动态封面适配计划：Square、双槽与同步动态背景

日期：2026-10-05。

状态：**设计已落档；后续已开始本地实现，C17 设备尚未验收。**
计划归档时仅修改文档；后续实现与验证分别见 [首轮实现记录](DYNAMIC-ARTWORK-C17-INITIAL-TEST.zh-CN.md)，
不能以本计划证明所有切片已完成。没有代理安装 APK 或操作真机。

## 1. 范围与决策

本计划承接 [动态封面完整方案](DYNAMIC-ARTWORK-PROVIDER-PLAN.zh-CN.md) 和
[实施记录](DYNAMIC-ARTWORK-IMPLEMENTATION.zh-CN.md)，专项补齐 C17 宿主适配。
它明确扩展原方案 AD-07 中暂不包含的 **C17 官方沉浸全屏背景**；该扩展仅为本轮设计，
不改写原首版范围，也不表示已有发布包支持。

- 优先使用现有 Square MP4；本轮不新增 Tall 资源选择、协议方向或竖版布局。
- 保留官方卡片页、沉浸页、歌词/封面模式及其显隐逻辑，不恢复 C16 单行歌词视图。
- 适配卡片小封面、沉浸前景大封面双槽，以及实际启用的全屏背景 renderer。
- 全屏背景必须实现 **中央视频 + 上下同步镜像 + 实时渐变模糊 + 遮罩**。
  中央动态而镜像/模糊仍为静态，不满足完成标准。
- 当前歌曲无资源、能力不支持或渲染失败时，整个背景退回官方当前歌曲静态展示。
- 保持 Artwork Provider 提供资源、Bridge 负责 SystemUI 展示的边界；不改变歌词 transport、
  播放器 metadata、PlaybackState 或 Bridge scope，不把联网和下载迁入 SystemUI。
- AOD 保持官方静态，不扩展到控制中心、胶囊或任意应用背景。

## 2. 样本与证据边界

本地只读反编译资料位于工作区 `PlayerSource/C17/`。以下版本读取对应 Manifest：

| 样本 | 版本 | 资料 |
| --- | --- | --- |
| SystemUI | 17.99.02 / 179902 | [Manifest](../../PlayerSource/C17/jadx-systemui/resources/AndroidManifest.xml) |
| SystemUIPlugin | 17.000.002 / 17000002 | [Manifest](../../PlayerSource/C17/jadx-plugin/resources/AndroidManifest.xml) |

Bridge 调查基线：`5766cdf61dc4f1870a11ce4a4583c3f48a8853e3`。
下面行号属于本地反编译样本，链接用于定位文件；混淆类名不作为跨版本稳定接口。
JADX 的类型恢复、重复分支和占位方法不能直接作为 Hook 实现依据；涉及歧义时须回查 DEX/smali。
本轮关键展示证据位于插件侧，不将其泛化为所有 C17 SystemUI/插件组合。

### 2.1 页面与前景

| 文件 / 方法 / 行号 | 静态确认 | 对适配的约束 |
| --- | --- | --- |
| [multi_page_root.xml](../../PlayerSource/C17/jadx-plugin/resources/res/layout/multi_page_root.xml):5；[factory/b.java](../../PlayerSource/C17/jadx-plugin/sources/com/oplus/systemui/plugins/shared/template/section/factory/b.java):34 | ViewPager2 容器仍在；style m/p 分别映射媒体卡片与沉浸 section | 不按“C17 只有一面”重建页面 |
| [media_card_section.xml](../../PlayerSource/C17/jadx-plugin/resources/res/layout/media_card_section.xml):27；[dimens.xml](../../PlayerSource/C17/jadx-plugin/resources/res/values/dimens.xml):375 | `img_album_art` 仍为 RotatableImageView，尺寸为 44dp | 小封面未删除；只在官方实际可见时展示视频 |
| [s1.java](../../PlayerSource/C17/jadx-plugin/sources/com/oplus/systemui/plugins/shared/template/section/media/s1.java):353，`q()` | 进入沉浸模式发送 `showMediaCardAlbumArt=false`；退出路径恢复 visibility | 不通过强制 VISIBLE 抵消官方隐藏 |
| [media_immersive_card_section.xml](../../PlayerSource/C17/jadx-plugin/resources/res/layout/media_immersive_card_section.xml):20、46；[Y0.java](../../PlayerSource/C17/jadx-plugin/sources/com/oplus/systemui/plugins/shared/template/section/media/Y0.java):750，`s()` / `v()` | 沉浸区包含歌词列表及大封面容器；大封面有 current/next 两个 View；歌词模式存在独立显隐信号 | 跟随模式、controller 与宿主显隐；不新增单行歌词 |
| [SmartShapeableImageView.java](../../PlayerSource/C17/jadx-plugin/sources/com/oplus/systemui/plugins/shared/template/component/media/view/SmartShapeableImageView.java):136、230，`onMeasure()` / `setImageDrawable()` | 继承 CustomLottieView；按 drawable 分类和测量，独立 smooth corner 与 foreground stroke | 不能复用 C16 Material 类型假设，也不能仅凭外层 0dp 高度推断竖版画幅 |
| [C0890e.java](../../PlayerSource/C17/jadx-plugin/sources/com/oplus/systemui/plugins/shared/template/section/media/C0890e.java):241、283、290、294，`e()` / `f()` / `g()` | 当前/下一槽、250 ms alpha 动画、pending drawable、模式门控与 renderer 选择 | 双槽角色跟随 controller 状态；不能将固定资源 ID 永久当作当前槽 |

`aspectRatioThreshold=1.35` 是图片分类阈值，不是目标显示比例。
Square 分支在可用高度不小于宽度时测成正方形。3:4 素材长短边比约 1.333，
也不构成该阈值下优先使用竖版布局的理由；且视频覆盖层不会自动修改宿主 drawable 的测量依据。
因此本轮沿用 Square，根据实际测量矩形等比裁剪，不以 FIT_XY 拉伸模拟竖版。

### 2.2 全屏背景与模糊

| 文件 / 方法 / 行号 | 静态确认 | 对适配的约束 |
| --- | --- | --- |
| [media_immersive_bg_fullscreen.xml](../../PlayerSource/C17/jadx-plugin/resources/res/layout/media_immersive_bg_fullscreen.xml) | contentContainer 下有 content/topMirror/bottomMirror；其后是 content_mask、topMask、bottomMask | 保持层级；不得把视频覆盖到歌词、按钮或遮罩之上 |
| [MediaFullScreenContentView.java](../../PlayerSource/C17/jadx-plugin/sources/com/oplus/systemui/plugins/shared/template/component/media/view/MediaFullScreenContentView.java):54、65、78 | 三视图均测成边长 min(宽,高) 的正方形；中央顶边为 0.2×高、水平居中；上下相邻，scaleY=-1 | Square 适配原生几何；镜像源必须与中央同帧 |
| [q.java](../../PlayerSource/C17/jadx-plugin/sources/com/oplus/systemui/plugins/shared/template/component/media/view/q.java):54、68、73，`a()` | 同一 bitmap 更新三视图；动画分支为 600 ms；有 pending bitmap 和完成处理 | 背景过渡独立于前景 250 ms，须跟随其实际生命周期 |
| [w.java](../../PlayerSource/C17/jadx-plugin/sources/com/oplus/systemui/plugins/shared/data/media/model/w.java):174–193 | 全屏 renderer 初始化 top 120→0、bottom 0→120，饱和度 1.4，并设为可见 | 运行时配置优先于 XML gone 和类默认值 |
| [BackdropBlurView.java](../../PlayerSource/C17/jadx-plugin/sources/com/oplus/systemui/plugins/shared/template/section/media/BackdropBlurView.java):29、68 | 默认半径 180；调用 OplusRenderEffect.createGradientBlurEffect | 官方效果可处理原静态背景；能否采样新增视频合成层尚未证明 |

这里的“居中”仅指水平居中；中央方形顶边由 0.2×高决定，不是垂直居中。
186dp/346dp 是样本 XML 的上下模糊区域高度，120/180 是实现中的效果参数，
不能未经核对把它们混成同一种单位或固定跨设备值。

背景 renderer 按模型选择，不能把全屏三视图存在解释为所有模式下都可见。
首次进入哪一页、不同模式下 renderer 的实际可见性、形状与过渡观感仍待设备验证。

## 3. 实施前的实现差距

本节保留计划基线；后续已实现内容及未完成项见首轮实现记录。

相关源码目录：`app/src/main/java/io/github/andrealtb/lockscreenlyrics/systemui/artwork/`。

| 当前入口 | 差距 / 实施要求 |
| --- | --- |
| `ArtworkCompatibilityResolver` | 仅绑定 `img_album_art`、`img_large_album_art`；增加 C17 双槽/controller、背景 renderer 与所属容器的结构能力解析 |
| `DynamicArtworkRuntime` | 展示、card/shape observation 存在 `!splitModel` 门控；不能仅删条件放行，须先补齐 C17 能力 |
| `ArtworkImmersiveMount` | 现有 FIT_XY 与形状前置条件针对已支持宿主；C17 增加独立挂载策略 |
| 现有官方过渡观察 | 框架 TransitionDrawable 路径不足以覆盖 C17 前景 ValueAnimator 和背景自定义 drawable 动画 |
| `DynamicArtworkRenderer` | 当前单显示输出不足以表达同步中央/镜像/渐变模糊；新增按能力选择的 C17 背景渲染路径 |
| `ArtworkContract` / Provider | 本轮继续使用已完成下载、已校验的 Square MP4，不改协议；资源/身份与租约检查继续生效 |

兼容能力拆分为小卡、前景双槽、动态背景、模糊合成，不以一个 C17 布尔值表示全部支持。
缺失哪项就对对应展示区域保持静态，能力日志说明原因；“完整 C17 适配”必须包括动态背景。

## 4. 同步动态背景方案

### 4.1 一次解码、同帧合成

拟新增的背景渲染职责如下，名称和类拆分在实现阶段决定：

```text
已校验 Square MP4 / FD
  -> 单路视频解码 -> 当前帧纹理
      -> 中央方形采样
      -> 上方同帧垂直镜像采样
      -> 下方同帧垂直镜像采样
  -> 渐变模糊与颜色处理 -> 官方遮罩 -> 官方歌词/控件
```

- 三个区域消费同一解码帧和时间戳，不启动三个独立播放器，不逐帧取 Bitmap 上传。
- 候选实现为解码到 SurfaceTexture，由 GL/EGL 在同一背景输出中采样三次；
  此为新增实现方案，现有 MediaPlayer/TextureView 不能假定已具备这种多区域合成能力。
- 复用宿主实际布局、偏移、裁剪与可见区域；尺寸变化后重新计算采样，不复制固定手机尺寸。
- 三处循环接缝、暂停、恢复、首帧显示与淡入作为同一整体处理。
- 前景双槽与背景是不同展示职责；若同时可见，实施阶段确认是否共享解码源，
  不把跨 Surface/生命周期共享视为现成能力。记录实际并发解码器数，避免隐藏层继续播放。

### 4.2 模糊实现分支

**优先验证官方模糊复用。** 将完整动态内容置于原 content_mask 和 BackdropBlurView 下方，
检查两个模糊层是否持续采样视频、上下边缘是否同步、是否有黑块/残影、遮罩和歌词层级是否正确。
View 存在、effect 创建成功或普通截图有颜色，均不能证明视频帧被连续采样。

**复用失败时实现自有合成。** 对同一帧构成的延展背景进行降采样、多遍模糊与渐变混合，
与未模糊画面合成，再匹配官方颜色/遮罩。镜像边界参与模糊采样，避免接缝；
不能只模糊单个方形后再拼接，也不能宣称普通线性混合与 OPlus 私有效果数值等价。

自有效果需要对照官方静态参考校准。仅在本动态背景接管期间避开重复的官方模糊，
停用/失败/销毁时恢复原状态；不得全局关闭 SystemUI 模糊。原版状态必须可逆保留。

若两条路径都未通过验证，则完整背景保持静态并报告能力不足，不能将中央单独动态作为最终交付。

### 4.3 状态、过渡和回退

- 前景观察 C0890e 的实际 current/next、动画结束/取消、pending drawable 提交和角色交接；
  背景观察 q 的开始、结束、pending bitmap 以及 renderer 替换。250/600 ms 仅作样本证据，不能 sleep 代替完成信号。
- 新歌曲到来立即使旧身份的异步结果失效；新视频未就绪期间由官方展示当前歌曲静态背景。
  新首帧通过身份、generation、宿主/插件 epoch、配置与可见性检查后才整体切入。
- 首版允许官方静态过渡完成后整体淡入视频，不要求两首歌的视频同时解码交叉渐变。
  不覆盖官方过渡，不出现旧歌视频盖住新歌静态图。
- 背景中央/镜像/模糊必须一起交接或一起回退；前景也不得继续暴露已失效歌曲的视频。
- 切歌词模式时跟随实际背景 renderer 的可见性决定是否播放，不能因前景大封面隐藏而误停仍可见背景，
  也不能为了视频强制显示被隐藏的小封面。
- 暂停、息屏、AOD、退出锁屏、detach、dispose、插件 loader 更换与总开关关闭，沿用播放门禁并释放资源。
- “保持屏幕常亮”沿用现有明确授权的设置范围，本轮不默认扩大到仅歌词背景可见的场景。

## 5. 实施切片与完成门槛

以下为切片定义；本地实现和设备验收分别在实施记录中更新，不因代码已接入而跳过设备退出条件。

| 切片 | 工作 | 退出条件 |
| --- | --- | --- |
| C17-A 能力与只读观测 | 解析 section/model/controller/renderer 所属关系、双槽、背景容器；补全状态信号 | 样本结构可唯一命中；未知/歧义拒绝；真机日志确认模式、槽位与几何，不安装展示层 |
| C17-B 前景挂载 | 小卡显隐、CustomLottieView 形状/描边、双槽首帧和交接；有能力后替代 splitModel 总门控 | 切歌/取消/快速模式切换无错槽、旧帧和强制显隐；C16 路径回归通过 |
| C17-C 背景与官方模糊探测 | 同帧镜像合成，接入真实背景 renderer；验证原 BackdropBlurView | 中央和上下连续同步运动；官方模糊对视频有效且层级正确，或明确记录不能复用的证据 |
| C17-D 必要时自有模糊 | 降采样渐变模糊、边缘处理、颜色校准、可逆接管 | 动态与官方静态参考无明显接缝/色差；没有双重模糊；失败完整恢复 |
| C17-E 生命周期与验收 | 身份/epoch、首帧、pending、暂停/AOD/销毁、预算、设置文案 | 完整设备矩阵通过，资源稳定，用户设备确认；此前不标记完成 |

C17-C/D 的背景方案可以独立于 B 做局部实验，但完整交付必须覆盖全部适用能力。
不因前景视频已经播放，就把背景阶段标记为可选美化。

## 6. 验证与验收清单

### 6.1 实现后的本地验证

- 最小行为测试覆盖：双槽交接/取消/pending、背景独立过渡、旧 generation 丢弃、隐藏层不播放、
  首帧整体显现、失败整体回退与资源释放。JVM 测试不证明真实 GPU/私有 blur 合成正确。
- 验证背景几何、镜像采样与渐变区域在不同宽高下的边界行为，不为实现逐行写机械复刻测试。
- 按实际改动运行 `scripts/dev.cmd bridge :app:testDebugUnitTest :app:assembleDebug`，必要时增加相关 lint。
  Provider 未修改则不跑全矩阵；若后续确需修改 Provider，另列影响与检查。
- C16 保持现有能力选择和生命周期；C17 解析失败必须安全静态，不影响 SystemUI 正常显示。

### 6.2 C17 设备验证

| 场景 | 必须观察的结果 |
| --- | --- |
| 首次锁屏、两页切换、大封面/歌词模式切换 | 官方布局和显隐保留；只驱动实际可见且归属正确的动态层 |
| 镜像与模糊同步 | 使用有明显运动的素材，以录像/连续帧确认中央、上下同帧；模糊不是旧静态封面 |
| 切歌、快速连切、过渡中再切 | 250/600 ms 两套状态各自结束或取消；pending 最终对应最新歌曲；无旧歌画面复活 |
| 未缓存、无资源、请求失败、解码或 GL 失败 | 官方当前歌曲静态背景完整保留/恢复，不出现中央动态而边缘静态的半接管 |
| 暂停/恢复、循环接缝 | 三部分同步；无独立进度漂移、边界闪帧或残留旧纹理 |
| 息屏/AOD/解锁/重建/插件关闭 | 停止不可见渲染、释放 FD/decoder/EGL/Surface/listener；恢复后重新核对归属 |
| 横竖屏、不同尺寸与窗口配置 | 跟随真实布局；无拉伸、镜像接缝、歌词遮挡或形状/描边错位 |
| 重复切换及持续播放 | 对照官方静态和现有动态路径，记录帧耗时、CPU/GPU、内存、FD、解码器数与温升；无持续增长 |

性能门槛在实现前按可用 C17 设备、目标帧率和基线明确，不把未经测量的性能数字写成已达标。
默认不降低中央清晰度来掩盖模糊开销；先评估模糊区域降采样和不可见帧停止。

设备操作、安装和日志抓取须有用户明确授权，按 [日志入口](../../LOGGING-DEBUG-CAPTURE.md) 执行。
日志继续使用现有 CLL 脱敏与节流工具，只记录能力、状态、几何与资源计数，不输出完整歌词、
稳定 media ID、URL query 或私人路径。验收证据记录实际 SystemUI/插件配对与复现窗口。

## 7. 尚未解决的问题与交付记录

1. 官方 BackdropBlurView 是否能持续采样拟使用的视频合成层：需原型和 C17 真机验证。
2. 前景与背景同时可见时的共享解码策略、GPU 预算及稳定生命周期：需实现测量。
3. 双槽角色交接、动画取消回调与背景 renderer 切换的完整调用链：实施前继续追踪实际 DEX，
   不仅依赖资源名或固定延时。
4. 不同 C17 插件版本的结构和图形能力差异：本样本不证明其他版本支持。

计划归档时已完成：现有样本关键路径静态核对、Square 决策、同步动态背景设计与实施/验收计划。
后续运行时代码、构建/测试进度见首轮实现记录；设备采样和用户设备确认仍未完成。
