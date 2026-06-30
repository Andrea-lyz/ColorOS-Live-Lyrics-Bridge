package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class ExternalLyricTextRepairTest {
    private static final Charset GB18030 = Charset.forName("GB18030");

    @Test
    public void restoresLyricProviderUtf8TextMisdecodedAsGb18030() {
        String lyric = "[00:12.230]I\u2019m not waiting for you\n"
                + "[00:13.000]\u5e0c\u671b\u6709\u7fbd\u6bdb\u548c\u7fc5\u8180";
        String mojibake = utf8AsGb18030(lyric);

        String restored = ExternalLyricTextRepair.restoreProviderMojibake(
                "lyricprovider/netease-cloud-music",
                mojibake);

        assertEquals(lyric, restored);
    }

    @Test
    public void repairsNeteaseJapaneseEnglishMojibakeEnoughForParsing() {
        String lyric = "[00:00.020]Chill in the shell \u9589\u3058\u3066\n"
                + "[00:12.230]I\u2019m not waiting for you";
        String mojibake = utf8AsGb18030(lyric);

        String restored = ExternalLyricTextRepair.restoreProviderMojibake(
                "lyricprovider/netease-cloud-music",
                mojibake);

        assertTrue(restored.contains("Chill in the shell \u9589\u3058"));
        assertTrue(restored.contains("I\u2019m not waiting for you"));
        assertFalse(restored.contains("\u95c1\u5908\u4ed2\u9287"));
        assertFalse(restored.contains("I\u9225\u6a93"));
    }

    @Test
    public void leavesNonProviderTextUntouched() {
        String lyric = "[00:00.020]\u8717\u7f29\u5728\u8eaf\u58f3\u4e4b\u4e2d";

        String restored = ExternalLyricTextRepair.restoreProviderMojibake(
                "systemui",
                lyric);

        assertEquals(lyric, restored);
    }

    private static String utf8AsGb18030(String value) {
        return GB18030.decode(StandardCharsets.UTF_8.encode(value)).toString();
    }
}
