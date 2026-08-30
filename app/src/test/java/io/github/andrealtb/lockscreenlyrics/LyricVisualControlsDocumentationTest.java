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

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
