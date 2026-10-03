package io.github.andrealtb.artwork.am;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.SystemClock;
import java.io.File;
import java.nio.ByteBuffer;
import io.github.andrealtb.artwork.contract.ArtworkResult.Status;

/** Copy AVC samples into a local MP4 with zero-origin PTS; no decoder/encoder is created. */
final class AmMp4Normalizer {
    static void remux(File source, File output, AmHls.Variant variant, AmHls.FilePlan plan,
            long maxBytes, AmNetwork.Task task) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean started = false;
        try {
            extractor.setDataSource(source.getAbsolutePath());
            if (extractor.getTrackCount() != 1) throw new AmFailure(Status.UNSUPPORTED, "source_track_layout");
            MediaFormat format = extractor.getTrackFormat(0);
            if (!"video/avc".equals(format.getString(MediaFormat.KEY_MIME))
                    || format.getInteger(MediaFormat.KEY_WIDTH) != variant.width()
                    || format.getInteger(MediaFormat.KEY_HEIGHT) != variant.height()
                    || (format.containsKey(MediaFormat.KEY_ROTATION) && format.getInteger(MediaFormat.KEY_ROTATION) != 0)) {
                throw new AmFailure(Status.UNSUPPORTED, "source_format_mismatch");
            }
            extractor.selectTrack(0);
            long origin = extractor.getSampleTime();
            if (origin < 0 || (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) == 0) throw new AmFailure(Status.UNSUPPORTED, "missing_initial_keyframe");
            format.setLong(MediaFormat.KEY_DURATION, Math.round(plan.durationSeconds() * 1_000_000));
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int track = muxer.addTrack(format);
            muxer.start(); started = true;
            ByteBuffer buffer = ByteBuffer.allocate(4 * 1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long deadline = SystemClock.elapsedRealtime() + 3000;
            long compressed = 0, highestPts = 0;
            int samples = 0;
            while (extractor.getSampleTime() >= 0) {
                task.check();
                long pts = extractor.getSampleTime() - origin;
                int flags = extractor.getSampleFlags();
                long size = extractor.getSampleSize();
                if (++samples > 10000 || SystemClock.elapsedRealtime() >= deadline || size <= 0 || size > buffer.capacity()
                        || pts < 0 || pts > Math.round((plan.durationSeconds() + 1) * 1_000_000)
                        || (flags & (MediaExtractor.SAMPLE_FLAG_ENCRYPTED | MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME)) != 0) {
                    throw new AmFailure(Status.UNSUPPORTED, "source_sample_budget");
                }
                buffer.clear();
                int read = extractor.readSampleData(buffer, 0);
                if (read != size || (compressed += read) > source.length()) throw new AmFailure(Status.UNSUPPORTED, "source_sample_incomplete");
                // Keep B-frame presentation reordering; do not force timestamps to increase or alter speed.
                info.set(0, read, pts, (flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                muxer.writeSampleData(track, buffer, info);
                highestPts = Math.max(highestPts, pts);
                if (output.length() > maxBytes) throw new AmFailure(Status.UNSUPPORTED, "remux_size_budget");
                if (!extractor.advance()) break;
            }
            if (samples == 0 || Math.abs(highestPts - Math.round(plan.durationSeconds() * 1_000_000)) > 1_000_000) {
                throw new AmFailure(Status.UNSUPPORTED, "source_duration_mismatch");
            }
            muxer.stop(); started = false;
            if (output.length() <= 0 || output.length() > maxBytes) throw new AmFailure(Status.UNSUPPORTED, "remux_size_budget");
        } finally {
            try {
                if (muxer != null) {
                    if (started) try { muxer.stop(); } catch (RuntimeException ignored) { /* incomplete output is discarded */ }
                    muxer.release();
                }
            } finally { extractor.release(); }
        }
    }
}
