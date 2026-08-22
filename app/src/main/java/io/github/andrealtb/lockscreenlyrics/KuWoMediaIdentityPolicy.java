package io.github.andrealtb.lockscreenlyrics;

import java.util.Locale;

final class KuWoMediaIdentityPolicy {
    private KuWoMediaIdentityPolicy() {
    }

    static boolean isCarLyricMetadataMutation(
            String previousTitle,
            String previousArtist,
            String nextTitle,
            String nextArtist) {
        String title = normalize(previousTitle);
        String artist = normalize(previousArtist);
        String nextTitleNormalized = normalize(nextTitle);
        String nextArtistNormalized = normalize(nextArtist);
        if (isBlank(title) || isBlank(artist)) {
            return false;
        }
        if (title.equals(nextTitleNormalized) && artist.equals(nextArtistNormalized)) {
            return true;
        }

        if (nextTitleNormalized.contains(title)
                && nextTitleNormalized.contains(artist)
                && (isBlank(nextArtistNormalized)
                || artist.equals(nextArtistNormalized))) {
            return true;
        }

        // KuWo also publishes the current lyric line as title while moving the stable
        // "song-artist" identity into artist. That remains the same track.
        return !isBlank(nextTitleNormalized)
                && !nextTitleNormalized.equals(title)
                && nextArtistNormalized.contains(title)
                && nextArtistNormalized.contains(artist);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
