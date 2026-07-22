package io.github.andrealtb.lockscreenlyrics.protocol;

/**
 * Android-free wire contract shared by the lock-screen bridge and future provider releases.
 *
 * <p>Version 4 is a direct, explicit SystemUI broadcast. SystemUI accepts only a static
 * source-to-player whitelist; it deliberately does not claim runtime sender-UID authentication
 * on ColorOS.</p>
 */
public final class ExternalLyricProtocol {
    public static final int FIXTURE_SCHEMA_VERSION = 1;
    public static final int DIRECT_PROTOCOL_VERSION = 4;
    public static final int CURRENT_PROTOCOL_VERSION = DIRECT_PROTOCOL_VERSION;

    public static final String ACTION_DIRECT_LYRIC_CAPTURED =
            "io.github.andrealtb.lockscreenlyrics.action.EXTERNAL_LYRIC_DIRECT_V4";
    public static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    public static final String EXTRA_PROTOCOL_VERSION = "protocolVersion";
    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_PLAYER_PACKAGE = "playerPackage";
    public static final String EXTRA_SENDER_PACKAGE = "senderPackage";
    public static final String EXTRA_CAPABILITIES = "capabilities";
    public static final String EXTRA_MATCH_POLICY = "matchPolicy";
    public static final String EXTRA_IDENTITY_CONFIDENCE = "identityConfidence";
    public static final String EXTRA_EVENT_TYPE = "eventType";
    public static final String EXTRA_TRACK_GENERATION = "trackGeneration";
    public static final String EXTRA_REQUEST_ID = "requestId";
    public static final String EXTRA_MEDIA_ID = "mediaId";
    public static final String EXTRA_MEDIA_URI = "mediaUri";
    public static final String EXTRA_TRACK_KEY = "trackKey";
    public static final String EXTRA_SONG_NAME = "songName";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_LYRIC = "lyric";
    public static final String EXTRA_RAW_LYRIC = "rawLyric";
    public static final String EXTRA_TRANSLATION_LYRIC = "translationLyric";
    public static final String EXTRA_CAPTURED_AT = "capturedAt";
    public static final String EXTRA_PLAYBACK_STATE = "playbackState";
    public static final String EXTRA_PLAYBACK_POSITION = "playbackPosition";
    public static final String EXTRA_PLAYBACK_SPEED = "playbackSpeed";
    public static final String EXTRA_PLAYBACK_LAST_POSITION_UPDATE_TIME =
            "playbackLastPositionUpdateTime";
    public static final String EXTRA_SENDER_KIND = "senderKind";
    public static final String SENDER_KIND_PROVIDER = "provider";
    public static final String SENDER_KIND_MODULE = "module";

    public static final String EVENT_TRACK_CHANGED = "trackChanged";
    public static final String EVENT_LYRIC_READY = "lyricReady";
    public static final String CAPABILITY_PLAYBACK_STATE = "playbackState";
    public static final String CAPABILITY_TRACK_GENERATION = "trackGeneration";
    public static final String CAPABILITY_CURRENT_TRACK_AUTHORITY = "currentTrackAuthority";
    public static final String CAPABILITY_TITLE_ONLY_FALLBACK = "titleOnlyFallback";
    public static final String CAPABILITY_TRANSLATION_TOGGLE = "translationToggle";
    public static final String MATCH_POLICY_TITLE_ONLY = "titleOnly";
    public static final String IDENTITY_CONFIDENCE_CURRENT_TRACK = "currentTrack";

    private ExternalLyricProtocol() {
    }

    public enum Transport {
        DIRECT
    }

    public static Transport transportForAction(String action) {
        if (ACTION_DIRECT_LYRIC_CAPTURED.equals(action)) {
            return Transport.DIRECT;
        }
        return null;
    }

    public static boolean isCompatible(String action, int protocolVersion) {
        return compatibilityError(action, protocolVersion) == null;
    }

    /** Returns a stable reject reason, or {@code null} when the action/version pair is supported. */
    public static String compatibilityError(String action, int protocolVersion) {
        Transport transport = transportForAction(action);
        if (transport == null) {
            return "unknown external lyric action";
        }
        if (protocolVersion == DIRECT_PROTOCOL_VERSION) {
            return null;
        }
        return "direct action requires protocol version " + DIRECT_PROTOCOL_VERSION;
    }

    public static boolean requiresExplicitSenderPackage(String action) {
        return ACTION_DIRECT_LYRIC_CAPTURED.equals(action);
    }

    /** Only this action is registered by the SystemUI-side ingress. */
    public static boolean isSystemUiIngressAction(String action) {
        return ACTION_DIRECT_LYRIC_CAPTURED.equals(action);
    }
}
