# 切片 C1：宿主展示能力探测与渲染失效保护

日期：2026-10-03。分支：`feat/dynamic-artwork-provider`。

切片 C 尚未完成视频挂载。C1 收集实际宿主几何与形状能力，并补齐 renderer 的请求归属复核。
本包仍保持 `display=disabled`；普通静态封面和官方动画继续工作。

## 本轮已实现

- `ArtworkHostProbe` 在已匹配宿主上观察 image/root 布局，在绑定回调时补读几何状态。
  仅 MEDIA debug 开启时安装布局监听；detach、dispose、观察关闭或 loader 更换时移除。
  不新增定时轮询、全局 ImageView 扫描、协程订阅或 View 挂载，不改官方形状/前景。
- `ARTWORK_HOST_GEOMETRY` 记录尺寸、位置、可见性、alpha、变换、scaleType、padding、tint、foreground，
  当前 outline 的可读取/可裁剪情况及圆角矩形半径、下一槽位可见性、实际类族与观察到的过渡类型。
  这些是脱敏结构信息。`*_unverified` 表示尚未确认展示支持；`no_transition_observed` 只代表采样时未见过渡，
  不能当作官方动画完成信号。outlineCanClip 也不能证明 TextureView 合成与宿主形状完全一致。
- `ArtworkRenderGuard` 要求八维 stamp 相同且当前展示门禁允许；拒绝或关闭后不会因状态恢复而复活。
  renderer 在接收 FD、准备、启动回调、rendering-start、Texture 更新及显示前复核，失败时隐藏并回收。
  首帧要求 rendering-start 后新的 Texture 更新，不能使用此前残留的更新信号。
- App 离线预览已接入本地 previewEpoch 与页面/插件选择门禁。预览没有真实 SystemUI/歌曲 epoch，其他域保持 0。
  SystemUI 的 provider/config/stamp 接线仍未完成，不能声称八维端到端资源链已经闭合。

## C16/C17 静态证据

| 样本与参考文件 | 确认事实 | 尚未实现 |
| --- | --- | --- |
| C16 user：`PlayerSource/SystemUIPlugin/jadx-user/sources/f7/b3.java` | 新封面通过 TransitionDrawable，实际调用 startTransition(duration) | 尚未 Hook 官方过渡开始/完成，不用固定等待时间代替 |
| C16 user：`.../component/media/view/SmartShapeableImageView.java`、`i7/e.java` | Material ShapeableImageView 与额外 smooth outline；outline 由官方设置并开启裁剪 | 视频层复用形状、stroke 与 foreground 尚未实现 |
| C17：`PlayerSource/C17/jadx-plugin/sources/.../component/media/view/SmartShapeableImageView.java` | 继承 CustomLottieView，具有独立 smooth corner、real drawable 与 foreground stroke | 不能沿用 C16 Material 类型假设 |
| C17：`.../section/media/Y0.java`、`C0890e.java` | current/next 槽位由 controller 管理；存在动画、pending drawable 和背景/布局状态 | 当前/下一槽位角色与 controller 状态尚未接入 |
| 两代普通卡的 `RotatableImageView` | 存在内部 bitmap 特效 | 不以 View.rotation=0 宣称特效停止 |

参考文件是只读反编译材料；本轮未执行真实设备 Shape/Texture/MediaPlayer 验收。

## 可选设备采样步骤

测试包：`artifacts/ColorOS-Live-Lyrics-Bridge-artwork-C1-host-survey-20261003-debug.apk`，Android Debug 签名。
没有代理安装或执行真机操作。本轮不用重新跑完整切片 B，也不把漏测关闭调试作为继续开发的阻塞。

1. 覆盖安装，开启 Bridge MEDIA debug 并保存。
2. 工作区根目录运行 `.\scripts\capture-lyrics-log.ps1 -Provider bridge`，然后手动重启系统界面。
3. 普通卡停留，进入沉浸页，切歌，再切换大封面/歌词布局；有条件时横竖屏切换。
4. 退出重进，结束抓取，提供完整日志及出现形状/布局变化的时间。
5. 使用 App 离线预览时，额外检查首帧、停止/再预览、切换插件和退出页面；本轮没有改测试插件。

```powershell
.\scripts\capture-lyrics-log.ps1 -Provider bridge
```

预期有 `ARTWORK_HOST_GEOMETRY`，宿主结束时有 `ARTWORK_HOST_PROBE_RELEASED listenerCount=0`；
原来的绑定、切歌与 Flow 释放仍正常。布局过渡期间变化的诊断按原 owner/event 去重和限流。
事件采样不保证捕获每个动画帧；没有动画期间记录也不意味着动画没有发生。

## 本地验证

执行 `scripts\dev.cmd bridge :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。
680 个测试，674 通过、6 个既有 fixture 跳过；新增 4 个 guard 测试覆盖八个归属域失效、
暂停/过渡门禁撤销后不可复活、detach、缺失 stamp 与读取失败。JVM 测试不执行真实 MediaPlayer/Texture 回调。
Lint 0 errors、48 warnings；新增 getIdentifier 警告来自目标插件的运行时资源查找，Bridge 自身 R 无法代替该 ID。
没有关闭检查或添加 baseline。Debug 构建通过；没有运行未改变的 Provider 全矩阵。

## 下一实施范围

继续实现有证据的单槽/双槽过渡状态适配、shape/foreground/crop 的挂载方案，
独立保护配置与明确的本地固定视频测试模式，再将精确绑定、客户端/服务 epoch 与 configRevision 接到资源请求。
固定视频测试模式与生产插件能力分开，不能让 localTestOnly 资源在生产模式替代所有歌曲。
上述完成后再开启 SystemUI 测试展示；C1 不能作为视觉验收或 C16/C17 全版本兼容证明。
