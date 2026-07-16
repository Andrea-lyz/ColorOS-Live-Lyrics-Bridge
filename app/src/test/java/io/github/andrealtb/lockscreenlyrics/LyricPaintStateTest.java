package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LyricPaintStateTest {
    @Test
    public void detectsActiveLayersLeftAtDefaultPixelSize() {
        assertTrue(LyricPaintState.needsTextSizeSync(
                88f,
                0.66f,
                88f,
                16f,
                16f,
                16f,
                16f,
                58.08f,
                16f,
                16f));
    }

    @Test
    public void acceptsSynchronizedMainAndTranslationLayers() {
        assertFalse(LyricPaintState.needsTextSizeSync(
                88f,
                0.66f,
                88f,
                88f,
                88f,
                88f,
                88f,
                58.08f,
                58.08f,
                58.08f));
    }

    @Test
    public void detectsGeometryPaintLeakingIntoTranslationLayer() {
        assertTrue(LyricPaintState.needsTextSizeSync(
                96f,
                0.75f,
                96f,
                96f,
                96f,
                96f,
                96f,
                63.36f,
                72f,
                72f));
    }
}
