# C17 fix2：歌词 Unicode 空格导致的单词内断行

日期：2026-10-05。分支：`feat/dynamic-artwork-provider`。本地构建/测试通过，尚未设备复验。

后续用户已反馈“正常了”，记录为本次 door/your 断词问题设备确认恢复；不扩大为其他功能验收。

## 证据与修复

用户截图中的 `door` 被拆为 `do / or`，此前还有 `your` 被拆开的反馈。
`lyrics-log-20261005-073522.txt:1374` 起的 Custom-drew 记录确认这句使用 Bridge 自绘，
词间分隔符在日志中显示为转码后的 `聽`，同一行的 display 文本则是普通空格。
该字符按 GB18030 编回字节为 C2 A0，与 UTF-8 的 U+00A0 NBSP 一致。
原日志存在转码，不把 `聽` 当作播放器实际提供的汉字，也不针对该汉字做文本替换。

当前换行器只用 Character.isWhitespace，漏掉 U+00A0、U+202F、U+2007。
当整句用这类空格分词时，找不到词间断点，便回退到字符边界；NBSP 测试样本可复现这种断词。

- LyricLineBreakPolicy 新增统一排版空格判断：isWhitespace 或 isSpaceChar。
- LyricDrawLayoutEngine 的行首尾裁剪和两行均衡同样使用该判断。
- 仅在歌词排版中允许这些可见空格作为换行候选；源文本、UTF-16 索引、逐字时间和高亮范围不改写。
- 单词本身宽于整行时仍保留按字符推进的兜底，不把无法容纳的长词无限循环或丢弃。
- 这是通用自绘排版修复，不限于 C17；动态封面及常亮沿用 fix1。

## 验证与测试包

执行 `scripts/dev.cmd bridge :app:testDebugUnitTest :app:assembleDebug`：
755 项测试，749 通过、6 项既有 fixture 跳过，0 失败；Debug 构建通过。
新增用例覆盖 door/your、多种 Unicode 空格、首尾裁剪、均衡/普通换行、缓存索引和超长单词。
本次没有重跑 lint，亦未安装、操作设备或发布。

[fix2 Debug APK](../../artifacts/ColorOS-Live-Lyrics-Bridge-artwork-C17-fix2-20261005-debug.apk)
SHA-256：`3FAC0E4D10526EB43E73943F514302FA049A5D41E533735A6987583226D69625`。

设备复验：相同字号和宽度下重播截图对应歌词，确认 door/your 整词换行，
翻译位置与逐字高亮仍正确；另检查普通空格英文和无空格中日文未出现排版回归。
