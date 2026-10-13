package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Muted looping video player used only under a frame-rate cap. Every decoded frame is released back
 * to the codec at its presentation time; the pacer decides whether that release renders. Dropped
 * frames never reach the view, so they cost neither a window redraw nor a buffer, and the codec's
 * own pool keeps cycling. All methods and callbacks run on the given media handler.
 */
final class ArtworkPacedPlayer {
    interface Listener {
        /** The first frame was rendered to the surface. */
        void onRenderingStart();

        void onError();
    }

    /** Beyond this lag the clock is re-anchored instead of rushing frames out to catch up. */
    private static final long MAX_LAG_NS = 150_000_000L;
    private final Handler media;
    private final ArtworkFramePacer pacer;
    private final Listener listener;
    private final Object token = new Object();
    private MediaExtractor extractor;
    private MediaCodec codec;
    private long loopOffsetUs;
    private long lastSampleUs;
    /** Largest timestamp of this pass; with B-frames the last sample read is not the latest shown. */
    private long maxSampleUs;
    private long sampleSpanUs;
    private long clockBaseNs = -1;
    private long ptsBaseUs;
    private boolean renderedAny;
    private boolean paused;
    private boolean released;

    ArtworkPacedPlayer(Handler media, ArtworkFramePacer pacer, Listener listener) {
        this.media = media;
        this.pacer = pacer;
        this.listener = listener;
    }

    void start(FileDescriptor descriptor, long length, Surface surface) throws IOException {
        extractor = new MediaExtractor();
        extractor.setDataSource(descriptor, 0, length);
        MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat candidate = extractor.getTrackFormat(i);
            String mime = candidate.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) {
                extractor.selectTrack(i);
                format = candidate;
                break;
            }
        }
        if (format == null) throw new IOException("no_video_track");
        codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        codec.setCallback(new MediaCodec.Callback() {
            @Override public void onInputBufferAvailable(MediaCodec mc, int index) { feed(index); }
            @Override public void onOutputBufferAvailable(MediaCodec mc, int index, MediaCodec.BufferInfo info) {
                schedule(index, info.presentationTimeUs);
            }
            @Override public void onError(MediaCodec mc, MediaCodec.CodecException error) { fail(); }
            @Override public void onOutputFormatChanged(MediaCodec mc, MediaFormat changed) {}
        }, media);
        codec.configure(format, surface, null, 0);
        codec.start();
    }

    void setSurface(Surface surface) {
        if (!released) codec.setOutputSurface(surface);
    }

    /** Freezes on the last rendered frame; held output stays with the codec until release. */
    void pause() {
        paused = true;
        media.removeCallbacksAndMessages(token);
    }

    void release() {
        if (released) return;
        released = true;
        media.removeCallbacksAndMessages(token);
        if (codec != null) {
            try { codec.stop(); } catch (RuntimeException ignored) {}
            try { codec.release(); } catch (RuntimeException ignored) {}
        }
        if (extractor != null) extractor.release();
    }

    private void feed(int index) {
        if (released || paused) return;
        try {
            ByteBuffer buffer = codec.getInputBuffer(index);
            int size = extractor.readSampleData(buffer, 0);
            if (size < 0) {
                // Loop without flushing: restart the file and keep timestamps increasing.
                loopOffsetUs += maxSampleUs + Math.max(sampleSpanUs, 1);
                maxSampleUs = 0;
                lastSampleUs = 0;
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                size = extractor.readSampleData(buffer, 0);
                if (size < 0) { fail(); return; }
            }
            long sampleUs = extractor.getSampleTime();
            if (sampleUs > lastSampleUs) sampleSpanUs = sampleUs - lastSampleUs;
            lastSampleUs = sampleUs;
            maxSampleUs = Math.max(maxSampleUs, sampleUs);
            codec.queueInputBuffer(index, 0, size, loopOffsetUs + sampleUs, 0);
            extractor.advance();
        } catch (RuntimeException error) {
            fail();
        }
    }

    private void schedule(int index, long ptsUs) {
        if (released || paused) return;
        long now = System.nanoTime();
        if (clockBaseNs < 0) {
            clockBaseNs = now;
            ptsBaseUs = ptsUs;
        }
        long dueNs = clockBaseNs + (ptsUs - ptsBaseUs) * 1000L;
        if (now - dueNs > MAX_LAG_NS) {
            clockBaseNs = now;
            ptsBaseUs = ptsUs;
            dueNs = now;
        }
        long delayMs = Math.max(0L, (dueNs - now) / 1_000_000L);
        media.postAtTime(() -> present(index, ptsUs), token, SystemClock.uptimeMillis() + delayMs);
    }

    private void present(int index, long ptsUs) {
        if (released) return;
        boolean render = !paused && pacer.accept(ptsUs * 1000L);
        try {
            codec.releaseOutputBuffer(index, render);
        } catch (RuntimeException error) {
            fail();
            return;
        }
        if (render && !renderedAny) {
            renderedAny = true;
            listener.onRenderingStart();
        }
    }

    private void fail() {
        if (released) return;
        listener.onError();
    }
}
