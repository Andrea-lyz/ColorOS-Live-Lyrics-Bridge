package io.github.andrealtb.artwork.contract;

/** Public artwork-only wire contract. Never carries lyricInfo, session tokens or media IDs. */
public final class ArtworkContract {
    public static final String ACTION_BIND = "io.github.andrealtb.artwork.action.BIND_PROVIDER";
    public static final String META_PROTOCOL_MAJOR = "io.github.andrealtb.artwork.PROTOCOL_MAJOR";
    public static final String META_SETTINGS_ACTIVITY = "io.github.andrealtb.artwork.SETTINGS_ACTIVITY";
    public static final int MAJOR = 1;
    public static final int MINOR = 0;
    public static final String MIME = "video/mp4";
    public static final String ORIENTATION = "square";
    public static final int MAX_TEXT = 512;
    public static final int MAX_URL = 2048;
    public static final int MAX_BUNDLE_BYTES = 16 * 1024;
    public static final int MAX_RESOLUTION = 1080;
    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    public static final long MAX_VIDEO_DURATION_MS = 120_000;
    public static final long LEASE_MS = 60_000;

    private ArtworkContract() {}

    public static String text(String value, int limit, boolean required) {
        String result = value == null ? "" : value.trim();
        if (result.length() > limit || (required && result.isEmpty())) {
            throw new IllegalArgumentException("invalid_text");
        }
        return result;
    }

    public static String opaqueId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,96}")) {
            throw new IllegalArgumentException("invalid_id");
        }
        return value;
    }

    public static boolean supports(int major, String mime, String orientation) {
        return major == MAJOR && MIME.equals(mime) && ORIENTATION.equals(orientation);
    }
}
