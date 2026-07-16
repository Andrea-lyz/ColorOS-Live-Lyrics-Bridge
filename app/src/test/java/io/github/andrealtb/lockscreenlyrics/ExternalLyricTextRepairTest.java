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
        String lyric = "[00:12.230]I’m not waiting for you\n"
                + "[00:13.000]希望有羽毛和翅膀";
        String mojibake = utf8AsGb18030(lyric);

        String restored = ExternalLyricTextRepair.restoreProviderMojibake(
                "lyricprovider/netease-cloud-music",
                mojibake);

        assertEquals(lyric, restored);
    }

    @Test
    public void repairsNeteaseJapaneseEnglishMojibakeEnoughForParsing() {
        String lyric = "[00:00.020]Chill in the shell 閉じて\n"
                + "[00:12.230]I’m not waiting for you";
        String mojibake = utf8AsGb18030(lyric);

        String restored = ExternalLyricTextRepair.restoreProviderMojibake(
                "lyricprovider/netease-cloud-music",
                mojibake);

        assertTrue(restored.contains("Chill in the shell 閉じ"));
        assertTrue(restored.contains("I’m not waiting for you"));
        assertFalse(restored.contains("闁夈仒銇"));
        assertFalse(restored.contains("I鈥檓"));
    }

    @Test
    public void leavesNonProviderTextUntouched() {
        String lyric = "[00:00.020]蜗缩在躯壳之中";

        String restored = ExternalLyricTextRepair.restoreProviderMojibake(
                "systemui",
                lyric);

        assertEquals(lyric, restored);
    }

    private static String utf8AsGb18030(String value) {
        return GB18030.decode(StandardCharsets.UTF_8.encode(value)).toString();
    }
}
