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
