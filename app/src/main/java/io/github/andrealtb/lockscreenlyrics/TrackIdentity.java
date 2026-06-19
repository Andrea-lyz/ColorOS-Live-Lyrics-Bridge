package io.github.andrealtb.lockscreenlyrics;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TrackIdentity {
    private static final Pattern CONTENT_RATING_SUFFIX = Pattern.compile(
            "(?i)\\s*[\\[\\(\\uFF08\\u3010]\\s*(?:explicit|clean)"
                    + "\\s*[\\]\\)\\uFF09\\u3011]\\s*$");

    private TrackIdentity() {
    }

    static String buildKey(String title, String artist) {
        return normalizeTitle(title) + '|' + normalizeComponent(artist);
    }

    static boolean matchesHintKey(String hintKey, String actualKey) {
        String[] hint = splitKey(hintKey);
        String[] actual = splitKey(actualKey);
        if (!hint[0].equals(actual[0])) {
            return false;
        }
        // LRC files frequently provide [ti] without [ar]. A missing artist is unknown,
        // not evidence that the lyric belongs to a different song.
        return hint[1].isEmpty()
                || actual[1].isEmpty()
                || hint[1].equals(actual[1]);
    }

    static boolean isTransitionResult(
            long deltaMillis, long preGraceMillis, long postGraceMillis) {
        return deltaMillis >= -Math.max(0L, preGraceMillis)
                && deltaMillis <= Math.max(0L, postGraceMillis);
    }

    private static String[] splitKey(String key) {
        String value = key == null ? "" : key;
        int separator = value.indexOf('|');
        if (separator < 0) {
            return new String[]{value, ""};
        }
        return new String[]{
                value.substring(0, separator),
                value.substring(separator + 1)
        };
    }

    private static String normalizeTitle(String title) {
        String normalized = normalizeComponent(title);
        Matcher matcher = CONTENT_RATING_SUFFIX.matcher(normalized);
        while (matcher.find()) {
            normalized = normalized.substring(0, matcher.start()).trim();
            matcher = CONTENT_RATING_SUFFIX.matcher(normalized);
        }
        return normalized;
    }

    private static String normalizeComponent(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        StringBuilder normalized = new StringBuilder(trimmed.length());
        boolean inWhitespace = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char character = trimmed.charAt(i);
            boolean whitespace = character == ' ' || character == '\t';
            if (whitespace) {
                if (!inWhitespace) {
                    normalized.append(' ');
                }
            } else {
                normalized.append(Character.toLowerCase(character));
            }
            inWhitespace = whitespace;
        }
        return normalized.toString().toLowerCase(Locale.ROOT);
    }
}
