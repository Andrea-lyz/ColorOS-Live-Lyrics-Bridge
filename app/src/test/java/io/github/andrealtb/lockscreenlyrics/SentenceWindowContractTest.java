package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import org.json.JSONObject;
import org.junit.Test;

public final class SentenceWindowContractTest {
    @Test public void snapshotIndexSurvivesParsingAndNormalization() throws Exception {
        JSONObject json = payload();
        assertEquals(1000L, LyricInfoContract.parse(json.toString()).snapshotPositionMillis);
        assertEquals(1000L, LyricInfoContract.normalizeOfficialLyricInfo(json.toString()).payload.snapshotPositionMillis);
    }

    @Test public void musicAndInvalidWindowsKeepPlaybackClock() throws Exception {
        JSONObject json = payload();
        json.remove("displayMode");
        assertEquals(-1L, LyricInfoContract.parse(json.toString()).snapshotPositionMillis);
        json = payload().put("currentLine", 3);
        assertEquals(-1L, LyricInfoContract.parse(json.toString()).snapshotPositionMillis);
        json = payload().put("lyric", "[00:00.000]a\n[00:03.000]b\n");
        assertEquals(-1L, LyricInfoContract.parse(json.toString()).snapshotPositionMillis);
    }

    private JSONObject payload() throws Exception {
        return new JSONObject().put("displayMode", "sentence-window-v1").put("currentLine", 1)
                .put("lyric", "[00:00.000]previous\n[00:01.000]current\n[00:02.000]next\n");
    }
}
