package io.github.andrealtb.lockscreenlyrics;

import android.graphics.Canvas;

/** Emits a coherent lyric batch while preserving the vendor-owned View and Canvas state. */
final class LyricSurfaceDrawTransaction {
    interface Proceed { Object draw() throws Throwable; }

    private LyricSurfaceDrawTransaction() { }

    static Object draw(Canvas canvas, boolean committed, Proceed proceed) throws Throwable {
        if (committed) return proceed.draw();
        int save = canvas.save();
        try {
            canvas.clipRect(0, 0, 0, 0);
            return proceed.draw();
        } finally {
            canvas.restoreToCount(save);
        }
    }
}
