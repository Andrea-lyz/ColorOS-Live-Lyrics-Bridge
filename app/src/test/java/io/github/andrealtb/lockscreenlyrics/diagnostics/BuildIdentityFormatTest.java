package io.github.andrealtb.lockscreenlyrics.diagnostics;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class BuildIdentityFormatTest {
    @Test public void installedAndObservedMustBeKnownToMatch() {
        assertEquals("MATCH", BuildIdentity.compare("id", "id"));
        assertEquals("unknown", BuildIdentity.compare("id", null));
        assertEquals("unknown", BuildIdentity.compare("unknown", "unknown"));
        assertEquals("unknown", BuildIdentity.compare("", "id"));
        assertTrue(BuildIdentity.compare("new-code", "old-code").startsWith("MISMATCH"));
    }
    @Test public void snapshotCopiesAndPreservesUnknownSourceState() {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("source_state", "unknown");
        facts.put("variant", "release");
        BuildIdentity.Snapshot snapshot = new BuildIdentity.Snapshot(facts);
        facts.put("source_state", "clean");
        String text = BuildIdentity.format(snapshot);
        assertTrue(text.contains("source_state: unknown"));
        assertTrue(text.contains("variant: release"));
        assertFalse(text.contains("source_state: clean"));
        try { snapshot.facts.put("bad", "value"); fail(); }
        catch (UnsupportedOperationException expected) { }
    }
    @Test public void fingerprintsDistinguishBytesWithSameVersionLabel() throws Exception {
        java.io.File first = Files.createTempFile("cll-component-a", ".bin").toFile();
        java.io.File second = Files.createTempFile("cll-component-b", ".bin").toFile();
        try {
            Files.write(first.toPath(), "same-version-contents-A".getBytes(StandardCharsets.UTF_8));
            Files.write(second.toPath(), "same-version-contents-B".getBytes(StandardCharsets.UTF_8));
            String a = BuildIdentity.sha256(first), b = BuildIdentity.sha256(second);
            assertEquals(64, a.length());
            assertNotEquals(a, b);
            assertEquals(a, BuildIdentity.sha256(first));
        } finally { Files.deleteIfExists(first.toPath()); Files.deleteIfExists(second.toPath()); }
    }
    @Test public void absentComponentRemainsUnknownRatherThanUnsupported() {
        assertEquals("unknown", BuildIdentity.sha256(new java.io.File("missing-cll-component.apk")));
    }
}
