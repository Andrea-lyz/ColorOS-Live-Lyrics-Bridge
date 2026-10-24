package io.github.andrealtb.lockscreenlyrics;

import java.util.regex.Pattern;

final class LyricInfoTrackMatcher {
    private static final Pattern ANY_LRC_TIME_TAG =
            Pattern.compile("[\\[<][0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?[\\]>]");
    private static final Pattern LRC_TITLE_TAG = Pattern.compile(
            "(?im)^\\s*\\[ti\\s*:(.*?)]\\s*$");
    private static final Pattern LRC_ARTIST_TAG = Pattern.compile(
            "(?im)^\\s*\\[ar\\s*:(.*?)]\\s*$");
    private static final Pattern TITLE_ARTIST_SEPARATOR = Pattern.compile(
            "\\s+[-–—]\\s+");

    private LyricInfoTrackMatcher() {
    }

    static boolean payloadMatchesTrack(
            LyricInfoContract.Payload payload,
            String title,
            String artist) {
        if (payload == null || isEmpty(title)) {
            return false;
        }
        String actualKey = TrackIdentity.buildKey(title, artist);
        String payloadTrackHintKey = normalizePayloadTrackHintKey(payload.trackKey);
        boolean trackKeyMatched = !isEmpty(payloadTrackHintKey)
                && TrackIdentity.matchesHintKey(payloadTrackHintKey, actualKey);
        if (!isEmpty(payload.trackKey)
                && !trackKeyMatched) {
            return false;
        }

        // A Provider stable key is authoritative. Ordinary lyric text must never veto an
        // already matched identity merely because an early line happens to contain "A - B".
        if (trackKeyMatched) {
            return true;
        }

        String lyricHintKey = inferTrackHintKey(firstNonEmpty(payload.rawLyric, payload.lyric));
        if (!isEmpty(lyricHintKey)
                && !TrackIdentity.matchesHintKey(lyricHintKey, actualKey)) {
            return false;
        }

        if (!isEmpty(lyricHintKey)) {
            return true;
        }

        if (isEmpty(payload.songName)) {
            // Identity-less timed text is bound to the observed SystemUI metadata when accepted.
            // It cannot independently authorize retaining a model for an arbitrary later track.
            return false;
        }
        return TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey(payload.songName, payload.artist),
                actualKey);
    }

    /** A native-only refresh must not evict a complete model bound to this player and track. */
    static boolean canRetainModelForNativeOnlyUpdate(
            LyricInfoContract.Payload previousPayload,
            String boundTrackKey,
            String previousPackage,
            LyricInfoContract.Payload incomingPayload,
            String incomingPackage,
            String observedTitle,
            String observedArtist) {
        return nativeOnlyModelRetention(
                previousPayload, boundTrackKey, previousPackage, incomingPayload,
                incomingPackage, observedTitle, observedArtist) == NativeOnlyModelRetention.RETAIN;
    }

    enum NativeOnlyModelRetention {
        RETAIN,
        MISSING_PAYLOAD,
        PLAYER_MISMATCH,
        UNBOUND_TRACK,
        OBSERVED_TRACK_MISMATCH,
        SENTENCE_WINDOW,
        MEDIA_ID_MISMATCH,
        GENERATION_MISMATCH,
        NO_COMPLETE_SOURCE,
        COMPLETE_UPDATE,
        NO_TIMED_DISPLAY,
        PREVIOUS_IDENTITY_MISMATCH,
        NATIVE_IDENTITY_MISMATCH
    }

    static NativeOnlyModelRetention nativeOnlyModelRetention(
            LyricInfoContract.Payload previousPayload,
            String boundTrackKey,
            String previousPackage,
            LyricInfoContract.Payload incomingPayload,
            String incomingPackage,
            String observedTitle,
            String observedArtist) {
        if (previousPayload == null || incomingPayload == null) {
            return NativeOnlyModelRetention.MISSING_PAYLOAD;
        }
        if (isEmpty(previousPackage) || !previousPackage.equals(incomingPackage)) {
            return NativeOnlyModelRetention.PLAYER_MISMATCH;
        }
        if (isEmpty(boundTrackKey)
                || observedTitle == null || observedTitle.trim().isEmpty()) {
            return NativeOnlyModelRetention.UNBOUND_TRACK;
        }
        if (!boundTrackKey.equals(TrackIdentity.buildKey(observedTitle, observedArtist))) {
            return NativeOnlyModelRetention.OBSERVED_TRACK_MISMATCH;
        }
        if (previousPayload.snapshotPositionMillis >= 0L
                || incomingPayload.snapshotPositionMillis >= 0L) {
            return NativeOnlyModelRetention.SENTENCE_WINDOW;
        }
        if (!isEmpty(previousPayload.songId) && !isEmpty(incomingPayload.songId)
                && !previousPayload.songId.equals(incomingPayload.songId)) {
            return NativeOnlyModelRetention.MEDIA_ID_MISMATCH;
        }
        if (previousPayload.sessionGeneration > 0L
                && incomingPayload.sessionGeneration > 0L
                && previousPayload.sessionGeneration != incomingPayload.sessionGeneration) {
            return NativeOnlyModelRetention.GENERATION_MISMATCH;
        }
        if (!LyricInfoContract.containsTimedLrc(SentenceWindowContract.renderSource(previousPayload))) {
            return NativeOnlyModelRetention.NO_COMPLETE_SOURCE;
        }
        if (LyricInfoContract.containsTimedLrc(SentenceWindowContract.renderSource(incomingPayload))) {
            return NativeOnlyModelRetention.COMPLETE_UPDATE;
        }
        if (!LyricInfoContract.containsTimedLrc(incomingPayload.lyric)) {
            return NativeOnlyModelRetention.NO_TIMED_DISPLAY;
        }
        if (!payloadMatchesTrack(previousPayload, observedTitle, observedArtist)) {
            return NativeOnlyModelRetention.PREVIOUS_IDENTITY_MISMATCH;
        }
        return nativeIdentityAgreesWithObservation(incomingPayload, observedTitle, observedArtist)
                ? NativeOnlyModelRetention.RETAIN
                : NativeOnlyModelRetention.NATIVE_IDENTITY_MISMATCH;
    }

    private static boolean nativeIdentityAgreesWithObservation(
            LyricInfoContract.Payload payload, String observedTitle, String observedArtist) {
        String observedKey = TrackIdentity.buildKey(observedTitle, observedArtist);
        if (!isEmpty(payload.trackKey)) {
            return TrackIdentity.matchesHintKey(
                    normalizePayloadTrackHintKey(payload.trackKey), observedKey);
        }
        // UI7's loader falls back to the metadata song/artist when native JSON omits them.
        // The existing model has already proved that binding; a lyric title banner is display
        // content and must not independently veto it. Explicit JSON identities still must agree.
        return TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey(
                        isEmpty(payload.songName) ? observedTitle : payload.songName,
                        payload.artist),
                observedKey);
    }

    /**
     * provider-core publishes TrackIdentity.buildStableKey as
     * {@code id|title|artist|durationSeconds}. Bridge's display matcher uses
     * {@code title|artist}; normalize the standard Provider form before applying the strict
     * track gate instead of treating the leading id as a title.
     */
    static String normalizePayloadTrackHintKey(String trackKey) {
        if (isEmpty(trackKey)) {
            return "";
        }
        String[] parts = trackKey.split("\\|", -1);
        if (parts.length != 4 || !isNonNegativeLong(parts[3])) {
            return trackKey;
        }
        return TrackIdentity.buildKey(parts[1], parts[2]);
    }

    private static boolean isNonNegativeLong(String value) {
        if (isEmpty(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    static String inferTrackHintKey(String rawLyric) {
        if (isEmpty(rawLyric)) {
            return "";
        }
        java.util.regex.Matcher titleTag = LRC_TITLE_TAG.matcher(rawLyric);
        java.util.regex.Matcher artistTag = LRC_ARTIST_TAG.matcher(rawLyric);
        String taggedTitle = titleTag.find() ? titleTag.group(1).trim() : "";
        String taggedArtist = artistTag.find() ? artistTag.group(1).trim() : "";
        if (!isEmpty(taggedTitle)) {
            return TrackIdentity.buildLrcHintKey(taggedTitle, taggedArtist);
        }

        int checked = 0;
        for (String rawLine : splitRawLyricLines(rawLyric)) {
            if (checked++ >= 8) {
                break;
            }
            String text = ANY_LRC_TIME_TAG.matcher(rawLine).replaceAll("").trim();
            if (isEmpty(text) || text.length() > 160) {
                continue;
            }
            java.util.regex.Matcher separator = TITLE_ARTIST_SEPARATOR.matcher(text);
            if (!separator.find() || separator.start() <= 0 || separator.end() >= text.length()) {
                continue;
            }
            String title = text.substring(0, separator.start()).trim();
            String artist = text.substring(separator.end()).trim();
            artist = artist.replaceFirst("\\s*[\\(（].*$", "").trim();
            if (!isEmpty(title) && !isEmpty(artist)) {
                return TrackIdentity.buildKey(title, artist);
            }
        }
        return "";
    }

    private static String[] splitRawLyricLines(String rawLyric) {
        return rawLyric.replace("\r\n", "\n").replace('\r', '\n').split("\n");
    }

    private static String firstNonEmpty(String first, String second) {
        return isEmpty(first) ? nullToEmpty(second) : first;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
