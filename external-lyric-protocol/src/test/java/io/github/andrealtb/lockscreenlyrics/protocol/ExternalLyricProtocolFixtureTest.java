package io.github.andrealtb.lockscreenlyrics.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ExternalLyricProtocolFixtureTest {
    @Test
    public void version4DirectFixtureIsTheOnlySystemUiIngressShape() throws Exception {
        JSONObject fixture = readFixture("fixtures/external-lyric-direct-v4.json");

        assertEquals(ExternalLyricProtocol.FIXTURE_SCHEMA_VERSION,
                fixture.getInt("fixtureSchemaVersion"));
        assertEquals("direct", fixture.getString("transport"));
        assertEquals(ExternalLyricProtocol.ACTION_DIRECT_LYRIC_CAPTURED,
                fixture.getString("action"));
        assertEquals(ExternalLyricProtocol.DIRECT_PROTOCOL_VERSION,
                fixture.getInt("protocolVersion"));
        assertEquals(ExternalLyricProtocol.SYSTEM_UI_PACKAGE,
                fixture.getString("systemUiPackage"));
        assertEquals(ExternalLyricProtocol.SENDER_KIND_PROVIDER,
                fixture.getString("senderKind"));
        assertNull(ExternalLyricProtocol.compatibilityError(
                fixture.getString("action"), fixture.getInt("protocolVersion")));
        assertTrue(ExternalLyricProtocol.isSystemUiIngressAction(fixture.getString("action")));
        assertTrue(ExternalLyricProtocol.requiresExplicitSenderPackage(fixture.getString("action")));
        assertNotNull(ExternalLyricProtocol.compatibilityError(
                "io.github.andrealtb.lockscreenlyrics.action.UNKNOWN",
                ExternalLyricProtocol.DIRECT_PROTOCOL_VERSION));
        assertNotNull(ExternalLyricProtocol.compatibilityError(
                fixture.getString("action"),
                ExternalLyricProtocol.DIRECT_PROTOCOL_VERSION - 1));
    }

    @Test
    public void metrolistProviderFixtureUsesItsStaticHostBinding() throws Exception {
        JSONObject fixture = readFixture(
                "fixtures/metrolist-external-lyric-direct-v4.json");

        assertEquals(ExternalLyricProtocol.ACTION_DIRECT_LYRIC_CAPTURED,
                fixture.getString("action"));
        assertEquals(ExternalLyricProtocol.DIRECT_PROTOCOL_VERSION,
                fixture.getInt("protocolVersion"));
        assertEquals("lyricprovider/metrolist-music", fixture.getString("source"));
        assertEquals("com.metrolist.music", fixture.getString("playerPackage"));
        assertEquals("com.metrolist.music", fixture.getString("senderPackage"));
        assertEquals(
                "playbackState,trackGeneration,currentTrackAuthority",
                fixture.getString("capabilities"));
        assertNull(ExternalLyricProtocol.compatibilityError(
                fixture.getString("action"), fixture.getInt("protocolVersion")));
    }

    private static JSONObject readFixture(String path) throws Exception {
        InputStream stream = ExternalLyricProtocolFixtureTest.class.getClassLoader()
                .getResourceAsStream(path);
        assertNotNull("Missing fixture " + path, stream);
        try (InputStream input = stream) {
            return new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
