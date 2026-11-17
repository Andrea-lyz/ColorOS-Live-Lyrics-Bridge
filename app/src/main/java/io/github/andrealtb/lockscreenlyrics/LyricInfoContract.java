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
    public static final String JSON_ALBUM = "album";
    public static final String JSON_SONG_ID = "songId";
    public static final String JSON_LYRIC = "lyric";
    public static final String JSON_RAW_LYRIC = "rawLyric";
    public static final String JSON_TRANSLATION_LYRIC = "translationLyric";
    public static final String JSON_PROVIDER = "provider";
    public static final String JSON_SOURCE = "source";
    public static final String JSON_TRACK_KEY = "trackKey";
    public static final String JSON_SESSION_GENERATION = "sessionGeneration";
    /** Optional TTML document carrying duet sides and background vocals; see TtmlLyricOverlay. */
    public static final String JSON_TTML = "ttml";
    private static final String[] TRANSLATION_KEYS = {
            JSON_TRANSLATION_LYRIC,
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
     * when {@code lyric} is empty but {@code rawLyric} contains timed tags, {@code rawLyric}
     * is used as the display fallback so word-only payloads still enter the pipeline.
     * {@code rawLyric} is preserved verbatim for the module's word-level renderer.
     * An optional {@code ttml} document annotates lines with duet sides; it also supplies the
     * word-timed and translation lanes when the payload does not carry them itself.
     */
    public static Payload parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(value);
            String lyric = object.optString(JSON_LYRIC, "");
            String rawLyric = object.optString(JSON_RAW_LYRIC, "");
            boolean lyricTimed = containsTimedLrc(lyric);
            boolean rawLyricTimed = containsTimedLrc(rawLyric);
            // Sentence-window rows are event slots, not a time axis the document could match.
            String ttml = SentenceWindowContract.position(object, lyric) >= 0L
                    ? ""
                    : object.optString(JSON_TTML, "");
            TtmlLyricOverlay overlay = TtmlLyricOverlay.of(ttml);
            if (overlay.isEmpty()) {
                ttml = "";
            } else if (!rawLyricTimed) {
                rawLyric = overlay.enhancedLrc();
                rawLyricTimed = containsTimedLrc(rawLyric);
            }
            if (!lyricTimed && !rawLyricTimed) {
                return null;
            }
            String resolvedLyric = lyricTimed ? lyric : rawLyric;
            String translationLyric = findTranslationLyric(object);
            if (translationLyric.isEmpty() && !overlay.isEmpty()) {
                translationLyric = overlay.translationLrc();
            }
            return new Payload(
                    object.optString(JSON_SONG_NAME, ""),
                    object.optString(JSON_ARTIST, ""),
                    object.optString(JSON_ALBUM, ""),
                    object.optString(JSON_SONG_ID, ""),
                    resolvedLyric,
                    rawLyric,
                    translationLyric,
                    object.optString(JSON_PROVIDER, ""),
                    object.optString(JSON_TRACK_KEY, ""),
                    object.optLong(JSON_SESSION_GENERATION, 0L),
                    object.optString(JSON_SOURCE, ""),
                    SentenceWindowContract.position(object, resolvedLyric),
                    ttml
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
            if (originalPayload != null && originalPayload.snapshotPositionMillis >= 0L) {
                return new NormalizedPayload(value, originalPayload, false);
            }
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

    /** Parses without normalizing lanes so display cleanup can run on complete candidates first. */
    static NormalizedPayload parseLyricInfo(String value) {
        return new NormalizedPayload(value, parse(value), false);
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

    static void replaceTranslationLyric(JSONObject object, String value) throws Exception {
        if (object == null) return;
        for (String key : TRANSLATION_KEYS) object.remove(key);
        if (containsTimedLrc(value)) object.put(JSON_TRANSLATION_LYRIC, value);
    }

    public static final class Payload {
        public final String songName;
        public final String artist;
        public final String album;
        public final String songId;
        public final String lyric;
        public final String rawLyric;
        public final String translationLyric;
        public final String provider;
        public final String trackKey;
        public final long sessionGeneration;
        public final String source;
        public final long snapshotPositionMillis;
        /** The usable TTML document, or empty when absent, oversized or unparsable. */
        public final String ttml;

        Payload(
                String songName,
                String artist,
                String album,
                String songId,
                String lyric,
                String rawLyric,
                String translationLyric,
                String provider,
                String trackKey,
                long sessionGeneration,
                String source) {
            this(songName, artist, album, songId, lyric, rawLyric, translationLyric,
                    provider, trackKey, sessionGeneration, source, -1L);
        }

        Payload(String songName, String artist, String album, String songId, String lyric,
                String rawLyric, String translationLyric, String provider, String trackKey,
                long sessionGeneration, String source, long snapshotPositionMillis) {
            this(songName, artist, album, songId, lyric, rawLyric, translationLyric,
                    provider, trackKey, sessionGeneration, source, snapshotPositionMillis, "");
        }

        Payload(String songName, String artist, String album, String songId, String lyric,
                String rawLyric, String translationLyric, String provider, String trackKey,
                long sessionGeneration, String source, long snapshotPositionMillis, String ttml) {
            this.ttml = ttml == null ? "" : ttml;
            this.snapshotPositionMillis = snapshotPositionMillis;
            this.songName = songName;
            this.artist = artist;
            this.album = album;
            this.songId = songId;
            this.lyric = lyric;
            this.rawLyric = rawLyric;
            this.translationLyric = translationLyric;
            this.provider = provider;
            this.trackKey = trackKey;
            this.sessionGeneration = sessionGeneration;
            this.source = source;
        }

        public boolean hasWordTiming() {
            return containsTimedLrc(rawLyric);
        }

        public boolean hasModuleExtensionData() {
            return hasWordTiming() || containsTimedLrc(translationLyric);
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
