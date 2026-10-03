package io.github.andrealtb.artwork.contract;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Build;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Must run off the main thread. The caller always owns/closes the supplied descriptor. */
public final class ArtworkFileVerifier {
    private ArtworkFileVerifier() {}

    public static ArtworkAsset inspect(ParcelFileDescriptor descriptor, String assetId,
            String version, long maxBytes) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("verification_on_main_thread");
        }
        if (Build.VERSION.SDK_INT < 30) throw new IOException("unsupported_fd_platform");
        StructStat stat = Os.fstat(descriptor.getFileDescriptor());
        int flags = Os.fcntlInt(descriptor.getFileDescriptor(), OsConstants.F_GETFL, 0);
        if (!OsConstants.S_ISREG(stat.st_mode)
                || (flags & OsConstants.O_ACCMODE) != OsConstants.O_RDONLY
                || stat.st_size <= 0 || stat.st_size > Math.min(maxBytes, ArtworkContract.MAX_FILE_BYTES)) {
            throw new IOException("invalid_fd");
        }
        Os.lseek(descriptor.getFileDescriptor(), 0, OsConstants.SEEK_SET);
        // Verify the MP4 container, not a guessed extension or the provider's declaration.
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(descriptor.getFileDescriptor(), 0, stat.st_size);
            if (!ArtworkContract.MIME.equals(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE))) {
                throw new IOException("unsupported_container");
            }
            String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (rotation != null && !"0".equals(rotation)) throw new IOException("unsupported_rotation");
        } finally {
            retriever.release();
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(descriptor.getFileDescriptor(), 0, stat.st_size);
            if (extractor.getTrackCount() > 16) throw new IOException("track_budget");
            MediaFormat video = null;
            for (int index = 0; index < extractor.getTrackCount(); index++) {
                MediaFormat track = extractor.getTrackFormat(index);
                String mime = track.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    if (video != null) throw new IOException("multiple_video_tracks");
                    video = track;
                    extractor.selectTrack(index);
                }
            }
            if (video == null || !video.containsKey(MediaFormat.KEY_DURATION)) {
                throw new IOException("missing_video");
            }
            if (video.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                double frameRate;
                try { frameRate = video.getInteger(MediaFormat.KEY_FRAME_RATE); }
                catch (ClassCastException error) { frameRate = video.getFloat(MediaFormat.KEY_FRAME_RATE); }
                if (!Double.isFinite(frameRate) || frameRate <= 0 || frameRate > 60) {
                    throw new IOException("unsupported_frame_rate");
                }
            }
            ArtworkAsset asset = new ArtworkAsset(assetId, version, video.getString(MediaFormat.KEY_MIME),
                    video.getInteger(MediaFormat.KEY_WIDTH), video.getInteger(MediaFormat.KEY_HEIGHT),
                    video.getLong(MediaFormat.KEY_DURATION) / 1000, stat.st_size, ArtworkContract.LEASE_MS);
            verifySamples(extractor, stat.st_size, asset.durationMs);
            return asset;
        } finally {
            extractor.release();
            Os.lseek(descriptor.getFileDescriptor(), 0, OsConstants.SEEK_SET);
        }
    }

    private static void verifySamples(MediaExtractor extractor, long fileBytes, long durationMs) throws IOException {
        // Read compressed samples only; no decoder is created during verification.
        ByteBuffer buffer = ByteBuffer.allocate(4 * 1024 * 1024);
        long deadline = SystemClock.elapsedRealtime() + 3000;
        long total = 0;
        int samples = 0;
        while (extractor.getSampleTime() >= 0) {
            if (++samples > 10_000 || SystemClock.elapsedRealtime() >= deadline
                    || Thread.currentThread().isInterrupted()) throw new IOException("sample_budget");
            if ((extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0
                    || extractor.getSampleTime() > (durationMs + 1000) * 1000) {
                throw new IOException("invalid_sample");
            }
            long expected = Build.VERSION.SDK_INT >= 28 ? extractor.getSampleSize() : -1;
            if (expected > buffer.capacity()) throw new IOException("sample_too_large");
            buffer.clear();
            int read = extractor.readSampleData(buffer, 0);
            if (read <= 0 || (expected >= 0 && read != expected)) throw new IOException("incomplete_sample");
            total += read;
            if (total > fileBytes) throw new IOException("invalid_sample_bytes");
            if (!extractor.advance()) break;
        }
        if (samples == 0) throw new IOException("missing_samples");
    }

    public static void verify(ParcelFileDescriptor descriptor, ArtworkAsset declared,
            ArtworkQuery query) throws Exception {
        ArtworkAsset actual = inspect(descriptor, declared.assetId, declared.version, query.maxFileBytes);
        if (!actual.fits(query) || actual.width != declared.width || actual.height != declared.height
                || actual.fileBytes != declared.fileBytes || actual.durationMs != declared.durationMs
                || !actual.codec.equals(declared.codec)) {
            throw new IOException("asset_mismatch");
        }
    }
}
