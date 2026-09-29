package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

    /** ColorOS 16 MediaModel order: isPlayingState precedes the lyric model; the flag is last. */
    @SuppressWarnings("unused")
    private static final class FakeColorOs16MediaModel {
        final FakeMultiIcon albumArt = null;
        final FakeMultiIcon topRight = null;
        final boolean playing = false;
        final List<?> buttons = null;
        final FakeLyricModel lyricModel = null;
        final boolean lyricSupported = false;
    }

    /** ColorOS 17 MediaInfo order: primaryColor and artworkFullBgEnable follow the flag. */
    @SuppressWarnings("unused")
    private static final class FakeColorOs17MediaInfo {
        final Object albumArt = null;
        final FakeLyricModel lyricModel = null;
        final boolean lyricSupported = false;
        final Object primaryColor = null;
        final boolean artworkFullBgEnable = false;
    }

    @SuppressWarnings("unused")
    private static final class FakeModelWithoutFlagAfterLyricModel {
        final boolean playing = false;
        final FakeLyricModel lyricModel = null;
        final Object primaryColor = null;
    }

    @Test
    public void bindsColorOs16ModelWithAlbumArtRepair() {
        OplusPluginDexKitAdapter.Targets targets =
                OplusPluginDexKitAdapter.bindResolvedClasses(
                        FakeColorOs16MediaModel.class,
                        FakeLyricModel.class,
                        modelClass -> OplusPluginDexKitAdapter.bindArtwork(
                                modelClass,
                                FakeMultiIcon.class,
                                FakeStaticIcon.class,
                                FakeNormalIcon.class,
                                FakeLottieIcon.class),
                        true);

        assertTrue(targets.resolvedByDexKit);
        assertEquals("lyricModel", targets.lyricModelField.getName());
        assertEquals("lyricSupported", targets.lyricSupportedField.getName());
        assertNull(targets.artworkFailure);
        OplusPluginDexKitAdapter.Artwork artwork = targets.artwork;
        assertNotNull(artwork);
        assertEquals("albumArt", artwork.albumArtField.getName());
        assertEquals("staticIcon", artwork.staticIconGetter.getName());
        assertEquals("lottieIcon", artwork.lottieIconGetter.getName());
        assertEquals("bitmap", artwork.iconModelBitmapGetter.getName());
        assertEquals("color", artwork.iconModelColorGetter.getName());
    }

    @Test
    public void coloros17LyricFlagIsTheBooleanAfterTheLyricModelNotTheLast() {
        OplusPluginDexKitAdapter.Targets targets =
                OplusPluginDexKitAdapter.bindResolvedClasses(
                        FakeColorOs17MediaInfo.class,
                        FakeLyricModel.class,
                        modelClass -> {
                            throw new IllegalStateException("Expected one StaticIcon, found 0: []");
                        },
                        true);

        assertEquals("lyricModel", targets.lyricModelField.getName());
        assertEquals("lyricSupported", targets.lyricSupportedField.getName());
        // Album-art repair is optional: its failure never costs the lyric fields.
        assertNull(targets.artwork);
        assertTrue(targets.artworkFailure instanceof IllegalStateException);
    }

    @Test(expected = IllegalStateException.class)
    public void modelWithoutBooleanAfterTheLyricModelIsRejected() {
        OplusPluginDexKitAdapter.bindResolvedClasses(
                FakeModelWithoutFlagAfterLyricModel.class,
                FakeLyricModel.class,
                modelClass -> null,
                true);
    }
}
