package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LyricVisualControlsDocumentationTest {
    @Test
    public void bothReadmesDescribeVisualControlsExportResetAndDowngrade() throws Exception {
        String english = readProjectFile("README.md");
        String chinese = readProjectFile("README.zh-CN.md");

        assertTrue(english.contains("Lyric brightness & fading"));
        assertTrue(english.contains("Default, Soft, Vivid, and Minimal"));
        assertFalse(english.contains("Default, Soft, Bold, and Minimal"));
        assertTrue(english.contains("Bridge configuration backup & restore"));
        assertTrue(english.contains("opening-cleanup rules and corrections"));
        assertTrue(english.contains("not lossless"));
        assertTrue(chinese.contains("歌词亮度与渐隐"));
        assertTrue(chinese.contains("Bridge 配置备份与恢复"));
        assertTrue(chinese.contains("开头清理规则与逐曲修正"));
        assertTrue(chinese.contains("无法保证无损降级"));
    }

    @Test
    public void detailedGuideLocksPresetMatrixAndMigrationBoundary() throws Exception {
        String guide = readProjectFile("docs/4.0/LYRIC-VISUAL-CONTROLS.md");

        assertTrue(guide.contains("100/50/60/80"));
        assertTrue(guide.contains("| 柔和 | 36% | 开；90%"));
        assertTrue(guide.contains("| 醒目 | 44% | 开；90%"));
        assertTrue(guide.contains("| 极简 | 55% | 关；值 90%"));
        assertTrue(guide.contains("scaleEnabled || blurEnabled"));
        assertTrue(guide.contains("Bridge Backup v1"));
        assertTrue(guide.contains("lockscreen_lyrics_debug"));
        assertTrue(guide.contains("schema v3 不承诺无损降级"));
    }

    @Test
    public void characterLiftHintExplainsTheLineTimedProgressDependency() throws Exception {
        String chinese = readProjectFile("app/src/main/res/values/strings.xml");
        String english = readProjectFile("app/src/main/res/values-en/strings.xml");

        assertTrue(chinese.contains(
                "<string name=\"char_lift_dependency_hint\">字符随揭示前沿从下方浮起到正常基线。"
                        + "逐字歌词生效；开启普通逐行歌词进度后，普通逐行歌词同样生效；"
                        + "息屏低帧率下自动关闭。</string>"));
        assertTrue(english.contains(
                "<string name=\"char_lift_dependency_hint\">Characters rise from below to the normal baseline "
                        + "as the reveal front passes them. Works with word-timed lyrics, and with ordinary "
                        + "line-timed lyrics when Line-timed lyric progress is enabled. Automatically off on the "
                        + "low frame rate always-on display.</string>"));
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
