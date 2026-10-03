# 小卡片封面：下一切片实施范围

日期：2026-10-03。状态：本机 C16 profile 的 Debug 小卡片视频挂载已实现，待设备验收。
当前实现与操作步骤见 [C3 小卡片测试](DYNAMIC-ARTWORK-CARD-TEST.zh-CN.md)。以下保留设计范围与取证依据。

## 当前验收依据

用户在 `lyrics-log-20261003-031842.txt` 对应设备窗口确认：沉浸大封面已显示固定动画视频，
暂停还原、切歌先还原再展示、拖动歌曲进度不改变视频循环。日志走到 mount/resource/prepare/first-frame/playing。
这证明本机基本展示，不覆盖全部 C16/C17、AOD 资源存量或长时间性能验收。
小卡片保留原封面是此前切片的范围，用户现要求继续适配该位置。

## 静态边界

- C16 user `PlayerSource/SystemUIPlugin/jadx-user/sources/com/oplus/systemui/plugins/shared/template/component/media/view/RotatableImageView.java`
  继承 `s7.a`，`setEffectBitmap` 为 shader 设置前/后 bitmap，旋转 spring 与 AnimatorSet 参与内部特效。
- `PlayerSource/SystemUIPlugin/jadx-user/sources/s7/a.java` 的 `onDraw` 可直接 `canvas.drawPaint(RuntimeShader)`；
  单个 float 方法更新内部 rotateAngle uniform。View.rotation=0 不能证明内部翻转结束。
- `PlayerSource/SystemUIPlugin/jadx-user/sources/com/oplus/systemui/plugins/shared/view/template/media/MediaPlayerCardPageRootView.java`
  继承插件侧 ConstraintLayout。现有大封面的 FrameLayout 挂载方案不能原样套用。
- 原触摸 helper、点击、长按与官方 bitmap/特效继续存在；不能设置 useSuperDraw、取消官方 spring/AnimatorSet、
  替换官方 bitmap 或移动原 ImageView 来让视频层工作。

## 实施顺序

1. 单独解析卡片的 bitmap、shader 更新、动画状态和实际绘制范围。来源必须由当前类结构/DEX 与设备日志证明；
   不能凭“旋转已归零”或固定等待时间猜测特效完成。
2. 给卡片建立独立容器挂载、形状/alpha mask 与 CENTER_CROP 适配。原生描边、裁剪或 shader 静止外观无法保持时不接管。
3. 接入已有精确绑定、时效门禁、FD/client 生命周期和测试开关。先仅使用固定本地视频，所有歌曲同视频。
4. 进程内最多一个播放器：有效大封面优先；没有有效大封面时，唯一有效卡片才播放。
   即使两个位置同曲也不同时创建两个解码器；位置切换注销旧绑定、关闭旧资源。
5. 用户设备验证圆角/边缘、点击长按、歌词布局、切歌翻转、暂停恢复、位置切换与息屏。

## 尺寸策略

用户指出 AM 提供 360×360 动画资源；本切片以 360×360 本地样本验证小区域展示，尚未调用 AM endpoint。
此前本机卡片实测为 288×288px，因此 360 档可以作为该设备的初始目标，不固定用于所有设备/布局。
生产插件阶段从实际返回且通过校验的 variants 中选择覆盖物理显示尺寸的最低合适分辨率。
360 档缺失、尺寸不足或编码不兼容时再选择更高的有效档位，不伪造 variant，也不在 Bridge 联网查资源。

独立测试样本：`artifacts/artwork-test-media/1989-animated-square-360-h264.mp4`。
该文件只用于本地测试，不能证明 AM variant 获取、匹配或缓存功能已经实现。
