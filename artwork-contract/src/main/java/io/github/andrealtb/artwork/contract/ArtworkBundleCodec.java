package io.github.andrealtb.artwork.contract;

import android.os.Bundle;
import android.os.Parcel;

/** Framework-only values, strict types, bounded Parcel size; unknown primitive fields are ignored. */
public final class ArtworkBundleCodec {
    private ArtworkBundleCodec() {}

    public static Bundle capabilities(boolean localTestOnly) {
        Bundle value = base();
        value.putString("acceptedMime", ArtworkContract.MIME);
        value.putString("orientation", ArtworkContract.ORIENTATION);
        value.putBoolean("localTestOnly", localTestOnly);
        return value;
    }

    public static boolean readCapabilities(Bundle value) {
        validate(value);
        if (!ArtworkContract.supports(integer(value, "protocolMajor"),
                string(value, "acceptedMime"), string(value, "orientation"))) {
            throw new IllegalArgumentException("unsupported_protocol");
        }
        return bool(value, "localTestOnly");
    }

    public static Bundle encodeQuery(ArtworkQuery query) {
        Bundle value = base();
        value.putString("title", query.title);
        value.putString("artist", query.artist);
        value.putString("album", query.album);
        value.putLong("durationMs", query.durationMs);
        value.putString("appleMusicUrl", query.appleMusicUrl);
        value.putInt("displayWidthPx", query.displayWidthPx);
        value.putInt("displayHeightPx", query.displayHeightPx);
        value.putInt("maxWidth", query.maxWidth);
        value.putInt("maxHeight", query.maxHeight);
        value.putLong("maxFileBytes", query.maxFileBytes);
        value.putString("acceptedMime", ArtworkContract.MIME);
        value.putString("orientation", ArtworkContract.ORIENTATION);
        validate(value);
        return value;
    }

    public static ArtworkQuery decodeQuery(Bundle value) {
        validate(value);
        if (!ArtworkContract.supports(integer(value, "protocolMajor"),
                string(value, "acceptedMime"), string(value, "orientation"))) {
            throw new IllegalArgumentException("unsupported_protocol");
        }
        return new ArtworkQuery(string(value, "title"), string(value, "artist"),
                string(value, "album"), number(value, "durationMs"),
                string(value, "appleMusicUrl"), integer(value, "displayWidthPx"),
                integer(value, "displayHeightPx"), integer(value, "maxWidth"),
                integer(value, "maxHeight"), number(value, "maxFileBytes"));
    }

    public static Bundle encodeResult(ArtworkResult result) {
        Bundle value = base();
        value.putString("status", result.status.name());
        value.putString("reason", result.reason);
        value.putLong("retryAfterMs", result.retryAfterMs);
        if (result.asset != null) {
            ArtworkAsset asset = result.asset;
            value.putString("assetId", asset.assetId);
            value.putString("resourceVersion", asset.version);
            value.putString("mime", ArtworkContract.MIME);
            value.putString("codec", asset.codec);
            value.putInt("width", asset.width);
            value.putInt("height", asset.height);
            value.putLong("videoDurationMs", asset.durationMs);
            value.putLong("fileBytes", asset.fileBytes);
            value.putLong("validForMs", asset.validForMs);
        }
        validate(value);
        return value;
    }

    public static ArtworkResult decodeResult(Bundle value) {
        validate(value);
        if (integer(value, "protocolMajor") != ArtworkContract.MAJOR) {
            throw new IllegalArgumentException("unsupported_protocol");
        }
        ArtworkResult.Status status = ArtworkResult.Status.valueOf(string(value, "status"));
        ArtworkAsset asset = null;
        if (status == ArtworkResult.Status.READY) {
            if (!ArtworkContract.MIME.equals(string(value, "mime"))) {
                throw new IllegalArgumentException("unsupported_mime");
            }
            asset = new ArtworkAsset(string(value, "assetId"), string(value, "resourceVersion"),
                    string(value, "codec"), integer(value, "width"), integer(value, "height"),
                    number(value, "videoDurationMs"), number(value, "fileBytes"),
                    number(value, "validForMs"));
        }
        return new ArtworkResult(status, asset, number(value, "retryAfterMs"), string(value, "reason"));
    }

    private static Bundle base() {
        Bundle value = new Bundle();
        value.putInt("protocolMajor", ArtworkContract.MAJOR);
        value.putInt("protocolMinor", ArtworkContract.MINOR);
        return value;
    }

    public static void validate(Bundle value) {
        if (value == null) throw new IllegalArgumentException("missing_bundle");
        value.setClassLoader(null);
        if (value.size() > 48 || value.hasFileDescriptors()) {
            throw new IllegalArgumentException("invalid_bundle");
        }
        for (String key : value.keySet()) {
            Object field = value.get(key);
            if (key.length() > 64 || (!(field instanceof String) && !(field instanceof Integer)
                    && !(field instanceof Long) && !(field instanceof Boolean))
                    || (field instanceof String && ((String) field).length() > ArtworkContract.MAX_URL)) {
                throw new IllegalArgumentException("invalid_field");
            }
        }
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(value);
            if (parcel.dataSize() > ArtworkContract.MAX_BUNDLE_BYTES) {
                throw new IllegalArgumentException("bundle_too_large");
            }
        } finally {
            parcel.recycle();
        }
        if (integer(value, "protocolMinor") < 0) {
            throw new IllegalArgumentException("invalid_minor");
        }
    }

    private static String string(Bundle value, String key) {
        Object field = value.get(key);
        if (!(field instanceof String)) throw new IllegalArgumentException("invalid_string");
        return (String) field;
    }

    private static int integer(Bundle value, String key) {
        Object field = value.get(key);
        if (!(field instanceof Integer)) throw new IllegalArgumentException("invalid_integer");
        return (Integer) field;
    }

    private static long number(Bundle value, String key) {
        Object field = value.get(key);
        if (!(field instanceof Long)) throw new IllegalArgumentException("invalid_long");
        return (Long) field;
    }

    private static boolean bool(Bundle value, String key) {
        Object field = value.get(key);
        if (!(field instanceof Boolean)) throw new IllegalArgumentException("invalid_boolean");
        return (Boolean) field;
    }
}
