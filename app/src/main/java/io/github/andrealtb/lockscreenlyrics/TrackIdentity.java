package io.github.andrealtb.lockscreenlyrics;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TrackIdentity {
    private static final String[] SALT_RELAY_SEPARATORS = {
            " - ",
            " \u2013 ",
            " \u2014 "
    };

    private static final Pattern CONTENT_RATING_SUFFIX = Pattern.compile(
            "(?i)\\s*[\\[\\(\\uFF08\\u3010]\\s*(?:explicit|clean)"
                    + "\\s*[\\]\\)\\uFF09\\u3011]\\s*$");
    private static final Pattern SAME_RECORDING_EDITION_SUFFIX = Pattern.compile(
            "(?i)\\s*[\\(\\uFF08]\\s*from\\s+the\\s+vault"
                    + "\\s*[\\)\\uFF09]\\s*$");
    private static final Pattern BRACKETED_FEATURE_SUFFIX = Pattern.compile(
            "(?i)\\s*[\\[\\(\\uFF08\\u3010]\\s*"
                    + "(?:feat(?:uring)?|ft)\\.?\\s+.*"
                    + "[\\]\\)\\uFF09\\u3011]\\s*$");
    private static final Pattern BARE_FEATURE_SUFFIX = Pattern.compile(
            "(?i)\\s+(?:feat(?:uring)?|ft)\\.?\\s+.*$");
    private static final Pattern ARTIST_FEATURE_SEPARATOR = Pattern.compile(
            "(?i)\\s+(?:feat(?:uring)?|ft)\\.?\\s+");
    private static final Pattern ARTIST_SEPARATOR = Pattern.compile(
            "\\s*[/,&;\\uFF0C\\uFF1B\\u3001]\\s*");
    private static final Pattern TRANSLATED_TITLE_SUFFIX = Pattern.compile(
            "^(.*?)[\\(\\uFF08]([^\\)\\uFF09]+)[\\)\\uFF09]\\s*$");
    private static final Pattern VERSION_MARKER = Pattern.compile(
            "(?i)(?:live|remix|remaster(?:ed)?|version|edit|acoustic|demo|"
                    + "instrumental|karaoke|cover|sped\\s*up|slowed|reverb|radio|"
                    + "\\u73b0\\u573a|\\u7248\\u672c|\\u91cd\\u5236|\\u6df7\\u97f3|"
                    + "\\u4f34\\u594f|\\u7eaf\\u97f3\\u4e50|\\u7ffb\\u5531|"
                    + "\\u30ab\\u30d0\\u30fc)");

    private TrackIdentity() {
    }

    static String buildKey(String title, String artist) {
        return normalizeTitle(title) + '|' + normalizeComponent(artist);
    }

    static String buildLrcHintKey(String titleTag, String artistTag) {
        String title = titleTag == null ? "" : titleTag.trim();
        String artist = artistTag == null ? "" : artistTag.trim();
        if (title.isEmpty()) {
            return "";
        }
        if (artist.isEmpty()) {
            int separatorIndex = -1;
            String matchedSeparator = null;
            for (String separator : SALT_RELAY_SEPARATORS) {
                int candidateIndex = title.indexOf(separator);
                if (candidateIndex > 0
                        && candidateIndex + separator.length() < title.length()
                        && (separatorIndex < 0 || candidateIndex < separatorIndex)) {
                    separatorIndex = candidateIndex;
                    matchedSeparator = separator;
                }
            }
            if (separatorIndex > 0 && matchedSeparator != null) {
                artist = title.substring(separatorIndex + matchedSeparator.length()).trim();
                title = title.substring(0, separatorIndex).trim();
            }
        }
        return buildKey(title, artist);
    }

    static boolean matchesHintKey(String hintKey, String actualKey) {
        String[] hint = splitKey(hintKey);
        String[] actual = splitKey(actualKey);
        if (!titlesMatch(hint[0], actual[0])) {
            return false;
        }
        // LRC files frequently provide [ti] without [ar]. A missing artist is unknown,
        // not evidence that the lyric belongs to a different song.
        return hint[1].isEmpty()
                || actual[1].isEmpty()
                || artistsMatch(hint[1], actual[1]);
    }

    static SaltRelayIdentity parseSaltRelayArtist(String compositeArtist) {
        if (compositeArtist == null) {
            return null;
        }
        String value = compositeArtist.trim();
        int separatorIndex = -1;
        String matchedSeparator = null;
        for (String separator : SALT_RELAY_SEPARATORS) {
            int candidateIndex = value.indexOf(separator);
            if (candidateIndex > 0
                    && (separatorIndex < 0 || candidateIndex < separatorIndex)) {
                separatorIndex = candidateIndex;
                matchedSeparator = separator;
            }
        }
        if (separatorIndex < 0 || matchedSeparator == null) {
            return null;
        }

        String artist = value.substring(0, separatorIndex).trim();
        String title = value.substring(separatorIndex + matchedSeparator.length()).trim();
        if (artist.isEmpty() || title.isEmpty()) {
            return null;
        }
        return new SaltRelayIdentity(title, artist);
    }

    static boolean relayIdentityMatchesHint(SaltRelayIdentity identity, String hintKey) {
        return identity != null
                && hintKey != null
                && !hintKey.isEmpty()
                && matchesHintKey(hintKey, buildKey(identity.title, identity.artist));
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
        matcher = SAME_RECORDING_EDITION_SUFFIX.matcher(normalized);
        while (matcher.find()) {
            normalized = normalized.substring(0, matcher.start()).trim();
            matcher = SAME_RECORDING_EDITION_SUFFIX.matcher(normalized);
        }
        return normalized;
    }

    private static boolean titlesMatch(String hintTitle, String actualTitle) {
        if (hintTitle.equals(actualTitle)) {
            return true;
        }
        String hintBase = stripFeatureSuffix(hintTitle);
        String actualBase = stripFeatureSuffix(actualTitle);
        if (hintBase.equals(actualBase)) {
            return true;
        }
        return stripTranslatedTitleSuffix(hintBase).equals(actualBase)
                || hintBase.equals(stripTranslatedTitleSuffix(actualBase));
    }

    private static String stripFeatureSuffix(String title) {
        String normalized = title == null ? "" : title;
        Matcher bracketed = BRACKETED_FEATURE_SUFFIX.matcher(normalized);
        if (bracketed.find()) {
            return normalized.substring(0, bracketed.start()).trim();
        }
        Matcher bare = BARE_FEATURE_SUFFIX.matcher(normalized);
        if (bare.find()) {
            return normalized.substring(0, bare.start()).trim();
        }
        return normalized;
    }

    private static String stripTranslatedTitleSuffix(String title) {
        String normalized = title == null ? "" : title;
        Matcher matcher = TRANSLATED_TITLE_SUFFIX.matcher(normalized);
        if (!matcher.matches()) {
            return normalized;
        }
        String base = matcher.group(1).trim();
        String suffix = matcher.group(2).trim();
        if (!containsAsciiLetter(base)
                || !containsNonAsciiLetter(suffix)
                || VERSION_MARKER.matcher(suffix).find()) {
            return normalized;
        }
        return base;
    }

    private static boolean containsAsciiLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNonAsciiLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch > 0x7f && Character.isLetter(ch)) {
                return true;
            }
        }
        return false;
    }

    private static boolean artistsMatch(String hintArtist, String actualArtist) {
        if (hintArtist.equals(actualArtist)) {
            return true;
        }
        Set<String> hintArtists = splitArtists(hintArtist);
        Set<String> actualArtists = splitArtists(actualArtist);
        return !hintArtists.isEmpty() && hintArtists.equals(actualArtists);
    }

    private static Set<String> splitArtists(String artist) {
        String normalized = ARTIST_FEATURE_SEPARATOR.matcher(
                artist == null ? "" : artist).replaceAll("/");
        Set<String> artists = new TreeSet<>();
        for (String part : ARTIST_SEPARATOR.split(normalized)) {
            String value = normalizeComponent(part);
            if (!value.isEmpty()) {
                artists.add(value);
            }
        }
        return artists;
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
            if (character == '\u2018'
                    || character == '\u2019'
                    || character == '\u02bc'
                    || character == '\uff07') {
                character = '\'';
            }
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

    static final class SaltRelayIdentity {
        final String title;
        final String artist;

        SaltRelayIdentity(String title, String artist) {
            this.title = title;
            this.artist = artist;
        }
    }
}
