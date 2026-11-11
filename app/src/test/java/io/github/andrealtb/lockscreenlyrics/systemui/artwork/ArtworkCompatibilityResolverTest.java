package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import static org.junit.Assert.assertSame;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.Test;

/** Proves the album-art field by its actual toString association, not by a letter or order. */
public class ArtworkCompatibilityResolverTest {

    private static final class FakeMultiIcon {
        Object staticIcon;
        Object lottieIcon;

        FakeMultiIcon(Object staticIcon, Object lottieIcon) {
            this.staticIcon = staticIcon;
            this.lottieIcon = lottieIcon;
        }

        @Override
        public String toString() {
            return "MultiIconModel(staticIcon=" + staticIcon
                    + ", lottieIcon=" + lottieIcon + ")";
        }
    }

    private static final class FakeInfo {
        String uniqueId;
        String voiceLabel;
        FakeMultiIcon albumArt;
        String rotate;
        FakeMultiIcon topRightIcon;
        FakeMultiIcon iconBeforeArtist;

        FakeInfo(
                String uniqueId,
                String voiceLabel,
                FakeMultiIcon albumArt,
                String rotate,
                FakeMultiIcon topRightIcon,
                FakeMultiIcon iconBeforeArtist) {
            this.uniqueId = uniqueId;
            this.voiceLabel = voiceLabel;
            this.albumArt = albumArt;
            this.rotate = rotate;
            this.topRightIcon = topRightIcon;
            this.iconBeforeArtist = iconBeforeArtist;
        }

        @Override
        public String toString() {
            return "MediaInfo(uniqueId=" + uniqueId
                    + ", voiceLabel=" + voiceLabel
                    + ", albumArt=" + albumArt
                    + ", rotate=" + rotate
                    + ", topRightIcon=" + topRightIcon
                    + ", iconBeforeArtist=" + iconBeforeArtist + ')';
        }
    }

    @Test
    public void probeSelectsAlbumArtByItsToStringAssociation() throws Exception {
        Field albumArt = FakeInfo.class.getDeclaredField("albumArt");
        Field topRightIcon = FakeInfo.class.getDeclaredField("topRightIcon");
        Field iconBeforeArtist = FakeInfo.class.getDeclaredField("iconBeforeArtist");

        Field selected = ArtworkCompatibilityResolver.probeAlbumArtField(
                FakeInfo.class,
                List.of(albumArt, topRightIcon, iconBeforeArtist),
                FakeMultiIcon.class);

        assertSame(albumArt, selected);
    }
}
