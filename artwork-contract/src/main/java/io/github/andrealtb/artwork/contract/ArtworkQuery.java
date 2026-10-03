package io.github.andrealtb.artwork.contract;

import java.net.URI;

/** Immutable, bounded query. No display-binding identity crosses the process boundary. */
public final class ArtworkQuery {
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    public final String appleMusicUrl;
    public final int displayWidthPx;
    public final int displayHeightPx;
    public final int maxWidth;
    public final int maxHeight;
    public final long maxFileBytes;

    public ArtworkQuery(String title, String artist, String album, long durationMs,
            String appleMusicUrl, int displayWidthPx, int displayHeightPx,
            int maxWidth, int maxHeight, long maxFileBytes) {
        this.title = ArtworkContract.text(title, ArtworkContract.MAX_TEXT, true);
        this.artist = ArtworkContract.text(artist, ArtworkContract.MAX_TEXT, false);
        this.album = ArtworkContract.text(album, ArtworkContract.MAX_TEXT, false);
        this.appleMusicUrl = validateAppleMusicUrl(appleMusicUrl);
        if (durationMs < 0 || durationMs > 7L * 24 * 60 * 60 * 1000
                || displayWidthPx < 1 || displayWidthPx > 8192
                || displayHeightPx < 1 || displayHeightPx > 8192
                || maxWidth < 1 || maxWidth > ArtworkContract.MAX_RESOLUTION
                || maxHeight < 1 || maxHeight > ArtworkContract.MAX_RESOLUTION
                || maxFileBytes < 1 || maxFileBytes > ArtworkContract.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("invalid_limits");
        }
        this.durationMs = durationMs;
        this.displayWidthPx = displayWidthPx;
        this.displayHeightPx = displayHeightPx;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.maxFileBytes = maxFileBytes;
    }

    private static String validateAppleMusicUrl(String input) {
        String value = ArtworkContract.text(input, ArtworkContract.MAX_URL, false);
        if (value.isEmpty()) return value;
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"music.apple.com".equalsIgnoreCase(uri.getHost())
                    || uri.getRawUserInfo() != null || uri.getPort() != -1
                    || uri.getRawFragment() != null || uri.getPath() == null
                    || !uri.getPath().matches("/[a-zA-Z]{2}/(album|song)/.+")) {
                throw new IllegalArgumentException("invalid_apple_url");
            }
            return value;
        } catch (java.net.URISyntaxException error) {
            throw new IllegalArgumentException("invalid_apple_url");
        }
    }
}
