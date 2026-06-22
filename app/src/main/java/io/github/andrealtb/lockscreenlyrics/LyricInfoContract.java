package io.github.andrealtb.lockscreenlyrics;

import org.json.JSONObject;

import java.util.regex.Pattern;

/** Stable metadata contract for players that publish OPlus-compatible lyrics themselves. */
public final class LyricInfoContract {
    public static final String METADATA_KEY = "lyricInfo";
    public static final String MANIFEST_METADATA_OPLUS_MEDIA_HISTORY =
            "io.github.andrealtb.lockscreenlyrics.OPLUS_MEDIA_HISTORY";
    public static final String ACTION_TOGGLE_TRANSLATION =
            "io.github.andrealtb.lockscreenlyrics.action.TOGGLE_TRANSLATION";
    public static final String JSON_SONG_NAME = "songName";
    public static final String JSON_ARTIST = "artist";
    public static final String JSON_SONG_ID = "songId";
    public static final String JSON_LYRIC = "lyric";
    public static final String JSON_RAW_LYRIC = "rawLyric";
    public static final String JSON_PROVIDER = "provider";
    public static final String JSON_TRACK_KEY = "trackKey";
    public static final String JSON_SESSION_GENERATION = "sessionGeneration";
    public static final String MODULE_PROVIDER = "lockscreen-lyrics-module";

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
                    findTranslationLyric(object),
                    object.optString(JSON_PROVIDER, ""),
                    object.optString(JSON_TRACK_KEY, ""),
                    object.optLong(JSON_SESSION_GENERATION, 0L)
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static NormalizedPayload normalizeOfficialLyricInfo(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new NormalizedPayload(value, null, false);
        }
        try {
            JSONObject object = new JSONObject(value);
            String lyric = object.optString(JSON_LYRIC, "");
            Payload originalPayload = parse(value);
            if (!containsTimedLrc(lyric)) {
                return new NormalizedPayload(value, originalPayload, false);
            }

            String normalizedLyric = OplusLyricNormalizer.normalizeForOfficialList(lyric);
            if (!containsTimedLrc(normalizedLyric) || normalizedLyric.equals(lyric)) {
                return new NormalizedPayload(value, originalPayload, false);
            }

            object.put(JSON_LYRIC, normalizedLyric);
            String normalizedValue = object.toString();
            Payload normalizedPayload = parse(normalizedValue);
            return new NormalizedPayload(
                    normalizedValue,
                    normalizedPayload == null ? originalPayload : normalizedPayload,
                    normalizedPayload != null);
        } catch (Throwable ignored) {
            return new NormalizedPayload(value, parse(value), false);
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
        public final String provider;
        public final String trackKey;
        public final long sessionGeneration;

        private Payload(
                String songName,
                String artist,
                String songId,
                String lyric,
                String rawLyric,
                String translationLyric,
                String provider,
                String trackKey,
                long sessionGeneration) {
            this.songName = songName;
            this.artist = artist;
            this.songId = songId;
            this.lyric = lyric;
            this.rawLyric = rawLyric;
            this.translationLyric = translationLyric;
            this.provider = provider;
            this.trackKey = trackKey;
            this.sessionGeneration = sessionGeneration;
        }

        public boolean hasWordTiming() {
            return containsTimedLrc(rawLyric);
        }

        public boolean hasModuleExtensionData() {
            return hasWordTiming() || containsTimedLrc(translationLyric);
        }

        public boolean isModuleEnvelope() {
            return MODULE_PROVIDER.equals(provider);
        }
    }

    public static final class NormalizedPayload {
        public final String lyricInfo;
        public final Payload payload;
        public final boolean changed;

        private NormalizedPayload(String lyricInfo, Payload payload, boolean changed) {
            this.lyricInfo = lyricInfo;
            this.payload = payload;
            this.changed = changed;
        }
    }
}
