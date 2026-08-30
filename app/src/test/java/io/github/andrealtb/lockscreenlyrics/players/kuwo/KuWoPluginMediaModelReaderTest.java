package io.github.andrealtb.lockscreenlyrics.players.kuwo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class KuWoPluginMediaModelReaderTest {
    @Test
    public void labeledTextStopsAtNextMarker() {
        assertEquals(
                "Light Ripple",
                KuWoPluginMediaModelReader.readLabeledText(
                        "MediaModel(songName=Light Ripple, artist=HOYO-MiX, iconBeforeArtist=x)",
                        "songName=",
                        ", artist="));
        assertEquals(
                "HOYO-MiX",
                KuWoPluginMediaModelReader.readLabeledText(
                        "MediaModel(songName=Light Ripple, artist=HOYO-MiX, iconBeforeArtist=x)",
                        "artist=",
                        ", iconBeforeArtist="));
    }

    @Test
    public void labeledTextReturnsNullWhenMarkersAreMissing() {
        assertNull(KuWoPluginMediaModelReader.readLabeledText(
                "MediaModel(title=Light Ripple)",
                "songName=",
                ", artist="));
        assertNull(KuWoPluginMediaModelReader.readLabeledText(
                "songName=Light Ripple",
                "songName=",
                ", artist="));
        assertNull(KuWoPluginMediaModelReader.readLabeledText(null, "songName=", ", artist="));
    }

    @Test
    public void containsPlayerPackageScansStringFields() {
        assertTrue(KuWoPluginMediaModelReader.containsPlayerPackage(new KuWoModel()));
        assertFalse(KuWoPluginMediaModelReader.containsPlayerPackage(new OtherModel()));
        assertFalse(KuWoPluginMediaModelReader.containsPlayerPackage(null));
    }

    public static final class KuWoModel {
        public String packageName = KuWoMediaIdentityPolicy.PLAYER_PACKAGE;
        public String title = "Light Ripple";
    }

    public static final class OtherModel {
        public String packageName = "com.salt.music";
    }
}
