# Bridge Repository Instructions

本文件补充工作区根 AGENTS.md，仅保留 Bridge 专项约束。

## 源码入口

- `app/src/main/java/io/github/andrealtb/lockscreenlyrics/`：Bridge 设置、通用策略及 SystemUI Hook；播放器适配在独立 Providers 仓库。
- `app/src/main/resources/META-INF/xposed/`：入口、API 元数据和静态 scope。
- `app/src/test/java/`：JVM 单元测试；`libxposed-api-stubs/` 仅供编译，不打包、不增加运行时行为。
- 发布任务查 `docs/RELEASE_PROCESS.md` 与 `release/bridge-release-contract.json`，不在本文件重复版本、矩阵、资产数量和工作流步骤。

## SystemUI、Recycler 与 AOD

- Bridge scope 只含 `system` / `com.android.systemui`。PlayerSystemUiPolicy 中的包策略须有 SystemUI/OPlus 兼容或翻译 action 证据。
- 所有歌词 View Hook 以 LyricsRecyclerView 所属关系严格门禁；普通 TextView 不进入歌词自绘、遮罩、尺寸和缓存逻辑。
- 首次 attach 的透明、偏移或 0x0 可能是官方过渡状态，不能仅凭这些条件延迟 prime、setCurrentLyric 或 row scale。
- 不强制私有 AOD 状态，不在切换期叠加重复 scroll/height/visibility/active-index 补丁。几何修复限定歌词区域、一次性、带原因日志，并与已知良好设备窗口比较。
- 首行 [00:00.000] 别名不代表逐字进度已开始；第一词之前不伪造 karaoke fill。末词视觉收尾查当前 WordLyricRenderSupport.lastWordRevealEndMillis 及测试，不改写存储时间戳或行生命周期。
- 保持当前歌词项几何，除非任务就是修复滚动/尺寸。反射和 Hook 失败安全降级，避免 SystemUI 崩溃。
- 设置页维持当前固定浅色和 edge-to-edge；视觉修改不顺手改变 LyricUiConfig schema、renderer 或 AOD 时序。最终设备外观由用户确认。

## 本地开发

从工作区根使用 `scripts/dev.cmd bridge <Gradle tasks>`，或本仓库 `scripts/gradle-local.cmd <Gradle tasks>`。
入口负责 JDK/ASCII 路径处理；环境问题再检查脚本和构建配置，不依赖旧环境快照。

- Java 沿用四空格缩进与现有命名风格；不为风格重排无关代码。
- JVM 测试使用 JUnit 4、`*Test.java`，针对相关行为添加有意义的回归；外部 fixture 缺失时通过显式属性及 Assume 处理。
- 验证范围遵循根目录约定；提交前检查 `git diff --check`。

## 发布与交付

- 发布流程、签名、源代码冻结和门禁以当前发布文档及机器契约为准；按任务授权执行，不把本地构建当成正式发布授权。
- Release Notes 同步 `.github/release-notes/<version>.md` 与 `docs/releases/v<version>.md`；LSP 元数据由独立 LSPRepo 维护。
- 发布完成需独立核对源仓库/LSP 资产、哈希与模块展示，不只看 Actions 成功。已公开版本不通过重跑覆盖，修复使用新版本。
- PR 说明行为变化、受影响进程和实际验证；视觉/时序问题附已有截图或脱敏日志，缺少设备证据时如实标注，不擅自操作设备补证。
