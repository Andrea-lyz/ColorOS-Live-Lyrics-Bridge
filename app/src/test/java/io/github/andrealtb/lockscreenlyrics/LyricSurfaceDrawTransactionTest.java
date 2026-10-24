package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class LyricSurfaceDrawTransactionTest {
    @Test public void pendingBatchEmitsNoneOfTheThreeSharedRows() throws Throwable {
        Bitmap image = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        int[] calls = {0};
        LyricSurfaceDrawTransaction.draw(canvas, false, () -> {
            calls[0]++;
            for (int y : new int[]{10, 30, 50}) canvas.drawRect(0, y, 80, y + 10, paint);
            return null;
        });
        assertEquals(1, calls[0]);
        assertEquals(Color.TRANSPARENT, image.getPixel(20, 15));
        assertEquals(Color.TRANSPARENT, image.getPixel(20, 35));
        assertEquals(Color.TRANSPARENT, image.getPixel(20, 55));
        canvas.drawRect(0, 80, 80, 90, paint);
        assertEquals(Color.WHITE, image.getPixel(20, 85));
        image.recycle();
    }

    @Test public void committedBatchDrawsTogetherAndPreservesTheCallersClip() throws Throwable {
        Bitmap image = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.clipRect(5, 5, 95, 95);
        Rect before = canvas.getClipBounds();
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        LyricSurfaceDrawTransaction.draw(canvas, true, () -> {
            for (int y : new int[]{10, 30, 50}) canvas.drawRect(0, y, 80, y + 10, paint);
            return null;
        });
        assertEquals(Color.WHITE, image.getPixel(20, 15));
        assertEquals(Color.WHITE, image.getPixel(20, 35));
        assertEquals(Color.WHITE, image.getPixel(20, 55));
        assertEquals(before, canvas.getClipBounds());
        image.recycle();
    }

    @Test public void hostExceptionPropagatesWithCanvasStateRestored() {
        Bitmap image = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        Rect before = canvas.getClipBounds();
        int saveCount = canvas.getSaveCount();
        IllegalStateException error = new IllegalStateException("host failure");
        Throwable observed = assertThrows(Throwable.class,
                () -> LyricSurfaceDrawTransaction.draw(canvas, false, () -> { throw error; }));
        assertSame(error, observed);
        assertEquals(before, canvas.getClipBounds());
        assertEquals(saveCount, canvas.getSaveCount());
        image.recycle();
    }
}
