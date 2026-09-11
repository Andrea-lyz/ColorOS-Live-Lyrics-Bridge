package io.github.andrealtb.lockscreenlyrics;

import java.util.Locale;
import org.json.JSONObject;

/** Opt-in event-driven rows. Slot tags encode order; they are not audio timestamps. */
final class SentenceWindowContract {
    private SentenceWindowContract() {}

    static String renderSource(LyricInfoContract.Payload payload) {
        return payload.snapshotPositionMillis >= 0L ? payload.lyric : payload.rawLyric;
    }

    static int visualIndex(long snapshotPosition, int activeIndex, int officialIndex) {
        return snapshotPosition >= 0L ? activeIndex : officialIndex;
    }

    static long position(JSONObject object, String lyric) {
        if (!"sentence-window-v1".equals(object.optString("displayMode"))) return -1L;
        int current = object.optInt("currentLine", -1);
        String[] lines = lyric.split("\n");
        if (lines.length < 1 || lines.length > 5 || current < 0 || current >= lines.length) return -1L;
        for (int i = 0; i < lines.length; i++) {
            String tag = String.format(Locale.ROOT, "[00:%02d.000]", i);
            if (!lines[i].startsWith(tag) || lines[i].substring(tag.length()).trim().isEmpty()) return -1L;
        }
        return current * 1000L;
    }
}
