package io.github.andrealtb.artwork.am;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Pictures for the provider pages only; never used by the lock-screen service.
 * Album art comes from Apple's image CDN for albums the user just searched; a bound album keeps a
 * local copy so the bindings list never goes online. Video frames come from the motion cover cache.
 */
final class AmThumbnails {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "artwork-am-thumbs");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AmThumbnails() {}

    /** Search result art; {@code url} was already checked by {@link AmPage#artworkUrl}. */
    static void album(Context context, CoverTile tile, String url, int px) {
        Context app = context.getApplicationContext();
        load(tile, url.isEmpty() ? null : "album:" + url, () -> AmSettings.connected(app) ? download(url, px) : null);
    }

    static void video(CoverTile tile, File file, int px) {
        load(tile, "video:" + file.getName(), () -> frame(file, px));
    }

    static void bound(Context context, CoverTile tile, String country, String albumId, int px) {
        File file = boundFile(context, country, albumId);
        load(tile, boundKey(country, albumId), () -> file.isFile() ? decodeFile(file, px) : null);
    }

    /** Keeps the search art of a newly bound album, if it was shown. */
    static void keep(Context context, String url, String country, String albumId) {
        Bitmap bitmap = url.isEmpty() ? null : MEMORY.get("album:" + url);
        if (bitmap == null) return;
        MEMORY.put(boundKey(country, albumId), bitmap);
        File target = boundFile(context, country, albumId);
        submit(() -> {
            File folder = target.getParentFile();
            if (folder == null || !folder.isDirectory() && !folder.mkdirs()) return null;
            File temp = File.createTempFile("thumb-", ".part", folder);
            try {
                try (FileOutputStream stream = new FileOutputStream(temp)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream); }
                if (!temp.renameTo(target)) throw new IOException("thumb_commit");
            } finally {
                if (temp.isFile()) temp.delete();
            }
            return null;
        });
    }

    static void forget(Context context, String country, String albumId) {
        MEMORY.remove(boundKey(country, albumId));
        File file = boundFile(context, country, albumId);
        submit(() -> file.delete());
    }

    private static String boundKey(String country, String albumId) { return "bound:" + country + "-" + albumId; }

    /** Country and album ID come from a validated binding: two letters and digits only. */
    private static File boundFile(Context context, String country, String albumId) {
        return new File(new File(context.getCacheDir(), "am-thumbs"), country.toLowerCase(Locale.ROOT) + "-" + albumId + ".jpg");
    }

    private static void load(CoverTile tile, String key, Callable<Bitmap> source) {
        tile.loadKey = key;
        Bitmap cached = key == null ? null : MEMORY.get(key);
        tile.setBitmap(cached, false);
        if (key == null || cached != null) return;
        submit(() -> {
            Bitmap bitmap = source.call();
            if (bitmap == null) return null;
            MEMORY.put(key, bitmap);
            MAIN.post(() -> { if (key.equals(tile.loadKey)) tile.setBitmap(bitmap, true); });
            return null;
        });
    }

    private static void submit(Callable<?> work) {
        try {
            IO.execute(() -> {
                try { work.call(); } catch (Exception ignored) { /* a missing picture keeps the monogram */ }
            });
        } catch (RejectedExecutionException ignored) { /* queue full: the tile keeps its monogram */ }
    }

    private static Bitmap download(String url, int px) throws IOException {
        URI uri = URI.create(url);
        if (!AmPage.artworkHost(uri)) return null;
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(8_000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK || connection.getContentLengthLong() > MAX_BYTES) return null;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = connection.getInputStream()) {
                byte[] buffer = new byte[16 * 1024];
                for (int count; (count = stream.read(buffer)) != -1;) {
                    bytes.write(buffer, 0, count);
                    if (bytes.size() > MAX_BYTES) return null;
                }
            }
            byte[] data = bytes.toByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample(bounds, px);
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } finally {
            connection.disconnect();
        }
    }

    private static Bitmap decodeFile(File file, int px) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample(bounds, px);
        return BitmapFactory.decodeFile(file.getPath(), options);
    }

    private static Bitmap frame(File file, int px) throws IOException {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(file.getPath());
            return retriever.getScaledFrameAtTime(400_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, px, px);
        }
    }

    private static int sample(BitmapFactory.Options bounds, int px) {
        int sample = 1, smaller = Math.min(bounds.outWidth, bounds.outHeight);
        while (smaller / (sample * 2) >= px) sample *= 2;
        return sample;
    }
}
