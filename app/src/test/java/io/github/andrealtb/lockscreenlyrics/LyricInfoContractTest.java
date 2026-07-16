package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

public final class LyricInfoContractTest {
    @Test
    public void replacingTranslationRemovesLegacyAliases() throws Exception {
        JSONObject object = new JSONObject();
        object.put("lyric", "[00:01.000]Hello");
        object.put("translatedLyric", "[00:01.000]旧翻译");

        LyricInfoContract.replaceTranslationLyric(
                object,
                "[00:01.000]新翻译");

        LyricInfoContract.Payload payload = LyricInfoContract.parse(object.toString());
        assertEquals("[00:01.000]新翻译", payload.translationLyric);
        assertEquals(false, object.has("translatedLyric"));
    }
}
