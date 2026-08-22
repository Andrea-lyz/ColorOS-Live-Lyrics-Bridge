package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

import org.junit.Test;

import java.util.List;

public class OplusPluginDexKitAdapterTest {
    private abstract static class FakeIconModel {
        abstract Bitmap bitmap();

        abstract Integer color();
    }

    private static final class FakeNormalIcon extends FakeIconModel {
        private final Bitmap bitmap;
        private final Integer color;

        FakeNormalIcon(Bitmap bitmap, Integer color) {
            this.bitmap = bitmap;
            this.color = color;
        }

        @Override
        Bitmap bitmap() {
            return bitmap;
        }

        @Override
        Integer color() {
            return color;
        }
    }

    private static final class FakeLottieIcon {
    }

    private static final class FakeStaticIcon {
        final Icon icon;
        final Drawable drawable;
        final Bitmap bitmap;
        final FakeIconModel mini;
        final FakeIconModel card;

        FakeStaticIcon(
                Icon icon,
                Drawable drawable,
                Bitmap bitmap,
                FakeIconModel mini,
                FakeIconModel card) {
            this.icon = icon;
            this.drawable = drawable;
            this.bitmap = bitmap;
            this.mini = mini;
            this.card = card;
        }
    }

    private static final class FakeMultiIcon {
        final FakeStaticIcon staticIcon;
        final FakeLottieIcon lottieIcon;

        FakeMultiIcon(FakeStaticIcon staticIcon, FakeLottieIcon lottieIcon) {
            this.staticIcon = staticIcon;
            this.lottieIcon = lottieIcon;
        }

        FakeStaticIcon staticIcon() {
            return staticIcon;
        }

        FakeLottieIcon lottieIcon() {
            return lottieIcon;
        }
    }

    private static final class FakeLyricModel {
        final List<?> lines;

        FakeLyricModel(List<?> lines) {
            this.lines = lines;
        }
    }

    @SuppressWarnings("unused")
    private static final class FakeMediaModel {
        final FakeMultiIcon albumArt = null;
        final FakeMultiIcon topRight = null;
        final FakeLyricModel lyricModel = null;
        final boolean playing = false;
        final boolean lyricSupported = false;
    }

    @Test
    public void bindsObfuscatedPluginModelsByStructure() throws Exception {
        OplusPluginDexKitAdapter.Targets targets =
                OplusPluginDexKitAdapter.bindResolvedClasses(
                        FakeMediaModel.class,
                        FakeMultiIcon.class,
                        FakeStaticIcon.class,
                        FakeNormalIcon.class,
                        FakeLottieIcon.class,
                        FakeLyricModel.class,
                        true);

        assertTrue(targets.resolvedByDexKit);
        assertEquals("albumArt", targets.albumArtField.getName());
        assertEquals("lyricModel", targets.lyricModelField.getName());
        assertEquals("lyricSupported", targets.lyricSupportedField.getName());
        assertEquals("staticIcon", targets.staticIconGetter.getName());
        assertEquals("lottieIcon", targets.lottieIconGetter.getName());
        assertEquals("bitmap", targets.iconModelBitmapGetter.getName());
        assertEquals("color", targets.iconModelColorGetter.getName());
    }
}
