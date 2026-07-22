package io.github.andrealtb.lockscreenlyrics;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Transfers the complete cleanup configuration without putting it in a Binder-sized extra. */
final class LyricContentCleanupConfigTransfer {
    static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    static final String AUTHORITY = "io.github.andrealtb.lockscreenlyrics.contentcleanup";
    static final int LEGACY_INLINE_MAX_CHARS = 4 * 1024;
    private static final String TAG = "LockscreenLyrics";
    private static final String CONFIG_PATH = "current";
    private static final String CLIP_LABEL = "lyric-content-cleanup-config";
    private static final Uri CONFIG_URI = new Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath(CONFIG_PATH)
            .build();

    private LyricContentCleanupConfigTransfer() {
    }

    static void attachConfigUri(Intent intent) {
        if (intent == null) return;
        intent.setClipData(ClipData.newRawUri(CLIP_LABEL, CONFIG_URI));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    static boolean grantSystemUiReadAccess(Context context) {
        if (context == null) return false;
        try {
            context.grantUriPermission(
                    SYSTEM_UI_PACKAGE,
                    CONFIG_URI,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Log.i(TAG, "Granted SystemUI read access for lyric opening cleanup config");
            return true;
        } catch (SecurityException error) {
            Log.e(TAG, "Failed to grant SystemUI read access for lyric opening cleanup config", error);
            return false;
        }
    }

    static LyricContentCleanupConfig decodeFromIntent(Context context, Intent intent) {
        if (intent == null) return null;
        Uri uri = configUriFrom(intent);
        if (uri == null) {
            return LyricContentCleanupConfig.decode(intent.getStringExtra(
                    LyricUiSettings.EXTRA_CONTENT_CLEANUP_CONFIG));
        }
        if (context == null || !isConfigUri(uri)) return null;
        try {
            ParcelFileDescriptor descriptor = context.getContentResolver()
                    .openFileDescriptor(uri, "r");
            if (descriptor == null) return null;
            try (InputStream input =
                         new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return LyricContentCleanupConfig.decode(
                        new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (Throwable error) {
            Log.w(TAG, "Failed to read lyric opening cleanup config URI", error);
            return null;
        }
    }

    static boolean isConfigUri(Uri uri) {
        return uri != null
                && "content".equals(uri.getScheme())
                && AUTHORITY.equals(uri.getAuthority())
                && ("/" + CONFIG_PATH).equals(uri.getPath());
    }

    private static Uri configUriFrom(Intent intent) {
        ClipData clipData = intent.getClipData();
        if (clipData == null || clipData.getItemCount() != 1) return null;
        return clipData.getItemAt(0).getUri();
    }
}
