package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class BridgeConfigBackupCodecTest {
    @Test
    public void roundTripPreservesNamespacesAndEveryPreferenceType() {
        LinkedHashMap<String, Object> main = new LinkedHashMap<>();
        main.put("bool", true);
        main.put("int", 42);
        main.put("long", 1234567890123L);
        main.put("float", 1.25f);
        main.put("string", "歌词\nconfig=ok");
        main.put("set", new LinkedHashSet<>(Arrays.asList("中文", "value=2")));
        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put("enabled", false);
        LinkedHashMap<String, Map<String, ?>> namespaces = new LinkedHashMap<>();
        namespaces.put("lockscreen_lyrics", main);
        namespaces.put("lockscreen_lyrics_debug", debug);

        BridgeConfigBackupCodec.Backup decoded = BridgeConfigBackupCodec.decode(
                BridgeConfigBackupCodec.encode(namespaces));

        assertEquals(main, decoded.namespace("lockscreen_lyrics"));
        assertEquals(debug, decoded.namespace("lockscreen_lyrics_debug"));
        assertEquals(
                new LinkedHashSet<>(Arrays.asList(
                        "lockscreen_lyrics",
                        "lockscreen_lyrics_debug")),
                decoded.namespaceNames());
    }

    @Test
    public void emptyNamespaceRoundTrips() {
        LinkedHashMap<String, Map<String, ?>> namespaces = new LinkedHashMap<>();
        namespaces.put("lockscreen_lyrics", new LinkedHashMap<>());
        BridgeConfigBackupCodec.Backup decoded = BridgeConfigBackupCodec.decode(
                BridgeConfigBackupCodec.encode(namespaces));
        assertTrue(decoded.namespace("lockscreen_lyrics").isEmpty());
    }

    @Test
    public void malformedOrUnknownBackupIsRejectedBeforeRestore() {
        assertRejected("not a bridge backup");
        assertRejected(BridgeConfigBackupCodec.HEADER + "\nE\tS\ta2V5\tdmFsdWU\n");
        assertRejected(BridgeConfigBackupCodec.HEADER + "\nP\tbWFpbg\nE\tX\ta2V5\tdmFsdWU\n");
    }

    private static void assertRejected(String text) {
        try {
            BridgeConfigBackupCodec.decode(text);
            fail("Expected malformed backup rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }
}
