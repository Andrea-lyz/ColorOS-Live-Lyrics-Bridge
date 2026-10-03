package io.github.andrealtb.artwork.contract;

/** A declaration, not proof of the received FD's contents. Verify again in the consumer. */
public final class ArtworkAsset {
    public final String assetId;
    public final String version;
    public final String codec;
    public final int width;
    public final int height;
    public final long durationMs;
    public final long fileBytes;
    public final long validForMs;

    public ArtworkAsset(String assetId, String version, String codec, int width, int height,
            long durationMs, long fileBytes, long validForMs) {
        this.assetId = ArtworkContract.opaqueId(assetId);
        this.version = ArtworkContract.text(version, 96, true);
        this.codec = ArtworkContract.text(codec, 64, true);
        if ((!"video/avc".equals(codec) && !"video/hevc".equals(codec))
                || width < 1 || width > ArtworkContract.MAX_RESOLUTION
                || height < 1 || height > ArtworkContract.MAX_RESOLUTION
                || Math.abs((long) width - height) * 100 > Math.max(width, height) * 2L
                || durationMs <= 0 || durationMs > ArtworkContract.MAX_VIDEO_DURATION_MS
                || fileBytes <= 0 || fileBytes > ArtworkContract.MAX_FILE_BYTES
                || validForMs <= 0 || validForMs > ArtworkContract.LEASE_MS) {
            throw new IllegalArgumentException("invalid_asset");
        }
        this.width = width;
        this.height = height;
        this.durationMs = durationMs;
        this.fileBytes = fileBytes;
        this.validForMs = validForMs;
    }

    public boolean fits(ArtworkQuery query) {
        return width <= query.maxWidth && height <= query.maxHeight
                && fileBytes <= query.maxFileBytes;
    }
}
