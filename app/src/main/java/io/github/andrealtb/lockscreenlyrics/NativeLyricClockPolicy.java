package io.github.andrealtb.lockscreenlyrics;

/**
 * Keeps the vendor lyric list on the module's smoothed playback clock.
 *
 * <p>The immersive controller hands its position to {@code LyricsRecyclerView}'s timed method
 * and schedules the next row change from that same value. A player whose PlaybackState anchors
 * jitter (for example 400 ms position blocks republished every second) therefore makes the
 * vendor row flip back across a boundary, and disagree with word fill driven by the module
 * clock. Only position arguments are replaced: the animate flag, the index/scroll transaction,
 * the vendor timers and every call stay with SystemUI (see
 * {@code docs/LYRIC_RENDERING_OWNERSHIP.zh-CN.md}).
 */
final class NativeLyricClockPolicy {
    private NativeLyricClockPolicy() {
    }

    /**
     * Position for the controller entry. Inside the small-correction band the module clock
     * replaces the vendor value, so the vendor also computes and schedules its next row change on
     * the module clock; outside it (seek, restart, another session) the vendor value stays
     * authoritative.
     */
    static long controllerPosition(long vendorPosition, long modulePosition, long bandMillis) {
        if (vendorPosition < 0L || modulePosition < 0L) {
            return vendorPosition;
        }
        long delta = vendorPosition - modulePosition;
        return delta > bandMillis || delta < -bandMillis ? vendorPosition : modulePosition;
    }

    /**
     * Position for the vendor timed-lyric method. Its callers are the controller entry (already
     * aligned) and the controller's row-boundary timer, which passes the exact row time it
     * scheduled. Replacing that value with a clock read when the timer fires could land just
     * before the boundary (millisecond rounding, or playback slower than 1x) and delay the row
     * change to the next update, so only a monotonic guard applies: a sub-band step back keeps
     * the previous result and cannot flip the row back; larger steps (seek) pass.
     *
     * @param heldPosition the previous result extrapolated to now, or -1 when there is none
     */
    static long timedLyricPosition(long vendorPosition, long heldPosition, long bandMillis) {
        if (heldPosition >= 0L
                && vendorPosition >= 0L
                && vendorPosition < heldPosition
                && heldPosition - vendorPosition <= bandMillis) {
            return heldPosition;
        }
        return vendorPosition;
    }

    /** Extrapolates a remembered position while playing; -1 when there is nothing to hold. */
    static long heldPosition(long position, long rememberedAtMillis, long nowMillis, float speed) {
        if (position < 0L || rememberedAtMillis < 0L || nowMillis < rememberedAtMillis) {
            return -1L;
        }
        if (speed <= 0f || Float.isNaN(speed) || Float.isInfinite(speed)) {
            return position;
        }
        return position + (long) ((nowMillis - rememberedAtMillis) * speed);
    }
}
