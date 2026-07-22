package io.github.andrealtb.lockscreenlyrics;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Read-only, URI-grant-only source for the complete cleanup configuration. */
public final class LyricContentCleanupConfigProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return LyricContentCleanupConfigTransfer.isConfigUri(uri)
                ? "application/json"
                : null;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!LyricContentCleanupConfigTransfer.isConfigUri(uri) || !"r".equals(mode)) {
            throw new FileNotFoundException("Unsupported lyric cleanup configuration URI");
        }
        Context context = getContext();
        if (context == null) {
            throw new FileNotFoundException("Configuration provider is not attached");
        }
        String encoded = context.getSharedPreferences(
                LyricUiSettings.PREFERENCES_NAME,
                Context.MODE_PRIVATE).getString(LyricContentCleanupRepository.PREFERENCE_KEY, "");
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Thread writer = new Thread(
                    () -> writeEncodedConfig(pipe[1], encoded == null ? "" : encoded),
                    "LockscreenLyrics-CleanupConfig");
            writer.start();
            return pipe[0];
        } catch (IOException error) {
            throw new FileNotFoundException("Unable to stream lyric cleanup configuration");
        }
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Lyric cleanup configuration is read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Lyric cleanup configuration is read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Lyric cleanup configuration is read-only");
    }

    private static void writeEncodedConfig(ParcelFileDescriptor writeSide, String encoded) {
        try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(writeSide)) {
            output.write(encoded.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // The receiver may have stopped before consuming the complete configuration.
        }
    }
}
