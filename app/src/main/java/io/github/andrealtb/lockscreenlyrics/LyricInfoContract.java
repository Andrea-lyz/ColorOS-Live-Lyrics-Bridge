package io.github.andrealtb.lockscreenlyrics;

import org.json.JSONObject;

import java.util.regex.Pattern;

/** Stable metadata contract for players that publish OPlus-compatible lyrics themselves. */
public final class LyricInfoContract {
    public static final String METADATA_KEY = "lyricInfo";
    public static final String JSON_SONG_NAME = "songName";
    public static final String JSON_ARTIST = "artist";
    public static final String JSON_SONG_ID = "songId";
    public static final String JSON_LYRIC = "lyric";
    public static final String JSON_RAW_LYRIC = "rawLyric";

    private static final String[] TRANSLATION_KEYS = {
            "translationLyric",
            "translatedLyric",
            "translateLyric",
            "transLyric",
            "lyricTranslation",
            "translationLrc",
            "transLrc",
            "translation"
    };

    private static final Pattern TIMED_LRC_TAG =
            Pattern.compile("[\\[<][0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?[\\]>]");

    private LyricInfoContract() {
    }

    /**
     * Parses a player-provided payload. A timed {@code lyric} field is the minimum contract;
     * {@code rawLyric} is optional and enables the module's word-level renderer.
     */
    public static Payload parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(value);
            String lyric = object.optString(JSON_LYRIC, "");
            if (!containsTimedLrc(lyric)) {
                return null;
            }
            return new Payload(
                    object.optString(JSON_SONG_NAME, ""),
                    object.optString(JSON_ARTIST, ""),
                    object.optString(JSON_SONG_ID, ""),
                    lyric,
                    object.optString(JSON_RAW_LYRIC, ""),
                    findTranslationLyric(object)
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean containsTimedLrc(String value) {
        return value != null && TIMED_LRC_TAG.matcher(value).find();
    }

    private static String findTranslationLyric(JSONObject object) {
        for (String key : TRANSLATION_KEYS) {
            String value = object.optString(key, "");
            if (containsTimedLrc(value)) {
                return value;
            }
        }
        return "";
    }

    public static final class Payload {
        public final String songName;
        public final String artist;
        public final String songId;
        public final String lyric;
        public final String rawLyric;
        public final String translationLyric;

        private Payload(
                String songName,
                String artist,
                String songId,
                String lyric,
                String rawLyric,
                String translationLyric) {
            this.songName = songName;
            this.artist = artist;
            this.songId = songId;
            this.lyric = lyric;
            this.rawLyric = rawLyric;
            this.translationLyric = translationLyric;
        }

        public boolean hasWordTiming() {
            return containsTimedLrc(rawLyric);
        }
    }
}
