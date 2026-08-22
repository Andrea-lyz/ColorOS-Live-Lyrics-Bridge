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

        // KuWo car lyrics may merge the stable title and artist into one field. A longer,
        // multi-artist spelling is a different track and must keep its own identity.
        return nextTitleNormalized.contains(title)
                && nextTitleNormalized.contains(artist)
                && (isBlank(nextArtistNormalized)
                || artist.equals(nextArtistNormalized));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
