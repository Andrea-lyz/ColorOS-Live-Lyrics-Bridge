package io.github.andrealtb.lockscreenlyrics.players.kuwo;

import android.graphics.Bitmap;

/**
 * KuWo cover URI, size, and snapshot-key rules. Network fetch and Icon extraction stay
 * in the SystemUI composition root.
 */
public final class KuWoCoverPolicy {
    public static final int SNAPSHOT_LIMIT = 16;
    public static final int MIN_EDGE_PX = 96;
    public static final int UNIFORM_SAMPLE_STRIDE = 24;

    private KuWoCoverPolicy() {
    }

    public static String artworkSnapshotKey(String mediaId, String titleArtistKey) {
        if (mediaId != null && !mediaId.isEmpty()) {
            return "id:" + mediaId;
        }
        return titleArtistKey == null ? "" : titleArtistKey;
    }

    public static boolean isKuWoHttpCoverHost(String scheme, String host) {
        return "http".equals(scheme)
                && host != null
                && !host.isEmpty()
                && host.startsWith("img")
                && host.endsWith(".kuwo.cn");
    }

    public static boolean isPlausibleCoverSize(int width, int height) {
        return width >= MIN_EDGE_PX && height >= MIN_EDGE_PX;
    }

    /**
     * Icon lookup fail-opens when bitmap extraction throws; a missing bitmap is not
     * plausible. Uniform-color rejection is reserved for plugin bitmap repair.
     */
    public static boolean isPlausibleCoverIconBitmap(Bitmap bitmap) {
        return bitmap != null && isPlausibleCoverSize(bitmap.getWidth(), bitmap.getHeight());
    }

    public static boolean isPlausibleCoverBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        if (!isPlausibleCoverSize(bitmap.getWidth(), bitmap.getHeight())) {
            return false;
        }
        return !isHighConfidenceUniformBitmap(bitmap, UNIFORM_SAMPLE_STRIDE);
    }

    public static boolean shouldRepairSeedlingArtwork(
            boolean incomingPlausible,
            String seedlingTrackKey,
            String currentSystemUiTrackKey,
            boolean hasSnapshot) {
        if (incomingPlausible || isEmpty(seedlingTrackKey) || !hasSnapshot) {
            return false;
        }
        return isEmpty(currentSystemUiTrackKey)
                || seedlingTrackKey.equals(currentSystemUiTrackKey);
    }

    /**
     * Uniform-color detector used by plugin bitmap repair. Package-visible for tests so
     * the 1x1 / solid-color reject can run without Android Bitmap stubs.
     */
    static boolean isHighConfidenceUniformColor(
            int width,
            int height,
            int stride,
            ColorAt colors) {
        int samplesX = Math.max(3, Math.min(9, width / stride));
        int samplesY = Math.max(3, Math.min(9, height / stride));
        long redTotal = 0L;
        long greenTotal = 0L;
        long blueTotal = 0L;
        int count = 0;
        for (int sampleY = 0; sampleY < samplesY; sampleY++) {
            for (int sampleX = 0; sampleX < samplesX; sampleX++) {
                int pixel = colors.get(sampleX(width, samplesX, sampleX), sampleY(height, samplesY, sampleY));
                redTotal += (pixel >> 16) & 0xFF;
                greenTotal += (pixel >> 8) & 0xFF;
                blueTotal += pixel & 0xFF;
                count++;
            }
        }
        if (count == 0) {
            return false;
        }
        long averageRed = redTotal / count;
        long averageGreen = greenTotal / count;
        long averageBlue = blueTotal / count;
        long maximumDelta = 0L;
        for (int sampleY = 0; sampleY < samplesY; sampleY++) {
            for (int sampleX = 0; sampleX < samplesX; sampleX++) {
                int pixel = colors.get(sampleX(width, samplesX, sampleX), sampleY(height, samplesY, sampleY));
                maximumDelta = Math.max(maximumDelta, Math.abs(((pixel >> 16) & 0xFF) - averageRed));
                maximumDelta = Math.max(
                        maximumDelta,
                        Math.abs(((pixel >> 8) & 0xFF) - averageGreen));
                maximumDelta = Math.max(
                        maximumDelta,
                        Math.abs((pixel & 0xFF) - averageBlue));
            }
        }
        return maximumDelta <= 2L;
    }

    interface ColorAt {
        int get(int x, int y);
    }

    private static boolean isHighConfidenceUniformBitmap(Bitmap bitmap, int stride) {
        try {
            return isHighConfidenceUniformColor(
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    stride,
                    bitmap::getPixel);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int sampleX(int width, int samplesX, int sampleX) {
        double fx = samplesX == 1 ? 0.5D : sampleX / (double) (samplesX - 1);
        return Math.max(0, Math.min(width - 1, (int) Math.round(fx * (width - 1))));
    }

    private static int sampleY(int height, int samplesY, int sampleY) {
        double fy = samplesY == 1 ? 0.5D : sampleY / (double) (samplesY - 1);
        return Math.max(0, Math.min(height - 1, (int) Math.round(fy * (height - 1))));
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
