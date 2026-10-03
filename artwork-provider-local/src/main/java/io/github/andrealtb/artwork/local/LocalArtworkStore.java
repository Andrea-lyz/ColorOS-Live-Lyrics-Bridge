package io.github.andrealtb.artwork.local;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.system.Os;

import io.github.andrealtb.artwork.contract.ArtworkContract;
import io.github.andrealtb.artwork.contract.ArtworkFileVerifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/** Immutable private-cache versions. Reimport never changes the file behind an existing lease. */
final class LocalArtworkStore {
    private static LocalArtworkStore instance;
    private final Context context;
    private final File directory;
    private final Map<String, Integer> pins = new HashMap<>();
    private final Semaphore importSlot = new Semaphore(1);

    static synchronized LocalArtworkStore get(Context context) {
        if (instance == null) instance = new LocalArtworkStore(context.getApplicationContext());
        return instance;
    }

    private LocalArtworkStore(Context context) {
        this.context = context;
        directory = new File(context.getFilesDir(), "artwork");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("cache_unavailable");
        }
        // Instance creation precedes any import in this process. Remove interrupted prior imports.
        File[] abandoned = directory.listFiles();
        if (abandoned != null) {
            for (File file : abandoned) {
                if (file.getName().matches("[A-Za-z0-9_-]{1,96}\\.partial")) file.delete();
            }
        }
    }

    synchronized String selected() {
        String id = context.getSharedPreferences("local_artwork", Context.MODE_PRIVATE)
                .getString("selected", "");
        return id != null && id.matches("[A-Za-z0-9_-]{1,96}") && file(id).isFile() ? id : "";
    }

    synchronized String pinCurrent() {
        String id = selected();
        if (!id.isEmpty()) pins.put(id, pins.getOrDefault(id, 0) + 1);
        return id;
    }

    synchronized void unpin(String id) {
        Integer count = pins.get(id);
        if (count == null) return;
        if (count <= 1) pins.remove(id); else pins.put(id, count - 1);
    }

    File file(String id) {
        return new File(directory, ArtworkContract.opaqueId(id) + ".mp4");
    }

    // Serial import executor; no external path or URI is exposed through Binder.
    void importVideo(Uri uri) throws Exception {
        if (!importSlot.tryAcquire()) throw new IOException("import_in_progress");
        String id = UUID.randomUUID().toString();
        File temporary = new File(directory, id + ".partial");
        File target = file(id);
        boolean committed = false;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                    FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IOException("missing_input");
                byte[] buffer = new byte[32 * 1024];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("cancelled");
                    total += count;
                    if (total > ArtworkContract.MAX_FILE_BYTES) throw new IOException("file_too_large");
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY)) {
                ArtworkFileVerifier.inspect(fd, id, id, ArtworkContract.MAX_FILE_BYTES);
            }
            synchronized (this) {
                cleanup();
                long bytes = temporary.length();
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File existing : files) if (existing.getName().endsWith(".mp4")) bytes += existing.length();
                }
                if (bytes > 256L * 1024 * 1024) throw new IOException("cache_full");
                Os.chmod(temporary.getAbsolutePath(), 0400);
                Os.rename(temporary.getAbsolutePath(), target.getAbsolutePath());
                committed = context.getSharedPreferences("local_artwork", Context.MODE_PRIVATE)
                        .edit().putString("selected", id).commit();
                if (!committed) throw new IOException("save_failed");
                cleanup();
            }
        } finally {
            if (temporary.exists()) temporary.delete();
            if (!committed && target.exists()) target.delete();
            importSlot.release();
        }
    }

    synchronized void cleanup() {
        String current = selected();
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (!name.matches("[A-Za-z0-9_-]{1,96}\\.mp4")) continue;
            String id = name.substring(0, name.length() - 4);
            if (!id.equals(current) && !pins.containsKey(id)) file.delete();
        }
    }
}
