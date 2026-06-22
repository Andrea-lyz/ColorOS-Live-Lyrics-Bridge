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
            "\\s+[-\\u2013\\u2014]\\s+");

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
        if (!isEmpty(payload.trackKey)
                && !TrackIdentity.matchesHintKey(payload.trackKey, actualKey)) {
            return false;
        }

        String lyricHintKey = inferTrackHintKey(firstNonEmpty(payload.rawLyric, payload.lyric));
        if (!isEmpty(lyricHintKey)
                && !TrackIdentity.matchesHintKey(lyricHintKey, actualKey)) {
            return false;
        }

        if (isEmpty(payload.songName)) {
            return true;
        }
        return TrackIdentity.matchesHintKey(
                TrackIdentity.buildKey(payload.songName, payload.artist),
                actualKey);
    }

    static boolean hasStrongTrackEvidence(
            LyricInfoContract.Payload payload,
            String title,
            String artist) {
        if (payload == null || isEmpty(title)) {
            return false;
        }
        String actualKey = TrackIdentity.buildKey(title, artist);
        if (!isEmpty(payload.trackKey)
                && TrackIdentity.matchesHintKey(payload.trackKey, actualKey)) {
            return true;
        }
        String lyricHintKey = inferTrackHintKey(firstNonEmpty(payload.rawLyric, payload.lyric));
        return !isEmpty(lyricHintKey)
                && TrackIdentity.matchesHintKey(lyricHintKey, actualKey);
    }

    static boolean shouldClearSaltPlayerFallbackLyricInfo(
            LyricInfoContract.Payload payload,
            String title,
            String artist,
            boolean trackChanged,
            boolean confirmingPreviouslyClearedTrack,
            boolean sameAsStableLyricInfo,
            boolean hasCapturedLyricForCurrentTrack) {
        if (payload == null
                || hasCapturedLyricForCurrentTrack
                || (!trackChanged && !confirmingPreviouslyClearedTrack)) {
            return false;
        }
        if (sameAsStableLyricInfo) {
            return true;
        }
        if (payload.isModuleEnvelope()) {
            return false;
        }
        return !hasStrongTrackEvidence(payload, title, artist);
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
            artist = artist.replaceFirst("\\s*[\\(\\uff08].*$", "").trim();
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
