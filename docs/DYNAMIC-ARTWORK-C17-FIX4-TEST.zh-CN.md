# C17 fix4：光晕颜色跟随封面取色

日期：2026-10-05。分支：`feat/dynamic-artwork-provider`。
状态：已完成 C17 DSU 开发包基本复测，用户反馈未见明显异常；不代表最终签名包或完整兼容矩阵验收。
保留 fix1–fix3 的已实现修复。

2026-10-05 `lyrics-log-20261005-083432.txt`：coverColorResolved=true，两个宿主均有 primary80 available=true；
初始 bound=false 随后在同宿主变为正常会话绑定。08:34:36–08:36:13 未见新崩溃/解码错误，
退出后观察器释放、重进后视频恢复。日志不包含最终光晕 RGB 的逐帧对照，不能单独证明像素级颜色一致。

## 用户行为与范围

- C17 的“光晕颜色”下面新增“跟随封面取色”，默认关闭；C16 隐藏。
- 开启后使用当前媒体卡静态封面的 `ArtColors.primary80`，与 C17 媒体进度条主色一致。
  不是白色亮头、壁纸取色，也不随动态封面视频逐帧取色。
- 点击手动色块或自定义颜色会关闭跟随；缺少有效配色/绑定或能力不支持时回退到已保存的手动光晕颜色。
- 只改变光晕 shadow RGB。原有光晕强度/半径、文字高光颜色和填充透明度不改变。
- 不要求启用动态封面，不要求安装 Artwork Provider；仅复用 Bridge 内的宿主/会话只读绑定。
  设置预览没有当前系统媒体卡，以手动颜色预览。

## 实现与证据

1. 配色来源依据 C17 插件 17.000.002：`section/media/K0.java:294` 读取封面处理结果 colors，
   `section/media/s1.java:791` 的 M() 取其 primary80 传给进度条。
   原生生成链路为 Icon/Bitmap → WallpaperColors → Material primary80；本功能直接消费结果，不重算颜色。
2. `ArtworkPaletteAccess` 读取 `MediaInfo.d → artwork.a → icon.e → processed.b → colors.a`。
   校验非静态 final 字段类型及 ArtColors 构造/toString 标记；不满足时仅禁用取色能力，
   不影响已有歌词或动态封面。每次使用当前模型快照，空封面不会继承上一专辑颜色。
3. `DynamicArtworkRuntime` 增加独立的 cover-color 观察开关，仅保留本地宿主/session 绑定。
   没有开启视频展示时不创建 playback/provider 请求。歌词 View 必须属于该 section 的祖先链，
   并满足当前插件 epoch、挂载、输入快照、身份和 ready binding；不使用全局“最后一个播放器颜色”。
4. 渲染前为对应歌词宿主选择配色；仅当实际光晕颜色改变时更新 palette 并清理 glow bitmap 缓存，
   不在每帧重新取图或创建配色配置对象。
5. 新增默认 false 的 `glow_follows_cover` 可选配置，接入原 LyricUiConfig codec、广播/快照、持久化和备份。
   沿用 schema 3 的可选字段兼容方式，旧配置默认关闭；恢复外观或应用内置预设会恢复手动配色。
6. UI 能力提示由 Android SDK ≥37 且 SystemUI 版本前缀 `17.` 判定，并显式声明查询 SystemUI 包。
   运行时另需 C17 split-model 及配色字段契约命中；C16 导入 true 值仍使用手动配色。
   同进程缓存版本判断，不随每帧查询 PackageManager。

日志使用现有 MEDIA debug：`ARTWORK_CAPABILITY_RESOLVED coverColorResolved=...` 和
`ARTWORK_COVER_COLOR source=primary80 available=... bound=...`。日志开关不决定取色是否工作。

## 本地验证

新增测试覆盖配置往返与旧配置默认值、手动回退、透明/缺失配色、强度及文字颜色不变、
C16/未知版本隐藏、同一读取器切换专辑不保留旧色和结构变化拒绝。
既有配置完整性测试加入新字段及非默认值，未绕过字段数量或 round-trip 检查。

执行：`scripts/dev.cmd bridge :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。
结果：766 项测试，760 通过、6 项既有 fixture 跳过，0 失败；Lint 0 errors / 50 warnings；Debug 构建通过。
未安装、操作真机、提交或发布。

[fix4 Debug 测试包](../../artifacts/ColorOS-Live-Lyrics-Bridge-artwork-C17-fix4-20261005-debug.apk)
SHA-256：`50CA656FA4AB23A122F5FFE90A4DB8287AFFA9954279944B0CEB0B6CEB223BD0`。

## 设备复验

1. C17：在光晕颜色下启用跟随并保存，播放有明显配色差异的专辑，比较光晕色相与媒体进度条有色段。
   光晕强度/模糊会影响视觉明暗，不要求像素值与进度条成品画面相同。
2. 切歌、暂停/恢复、切换播放器，检查颜色更新和缺少封面时手动回退，不沿用其他播放器的颜色。
3. 关闭动态封面及其 Provider 来源、关闭 MEDIA debug 后重试，跟随配色仍应工作。
4. 选择手动色块/自定义颜色、恢复外观/预设，检查跟随关闭；保存后重启 SystemUI、备份恢复再核对选择。
5. C16：选项不显示；导入含该字段的配置后仍走手动颜色。两代都不改变歌词进度时间轴。
