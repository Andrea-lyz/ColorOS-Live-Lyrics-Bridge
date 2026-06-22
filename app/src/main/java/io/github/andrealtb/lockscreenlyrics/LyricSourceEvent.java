package io.github.andrealtb.lockscreenlyrics;

/**
 * Player-independent event emitted by a lyric source adapter.
 *
 * <p>Adapters should attach the strongest identity they can prove. A request id, media id or
 * media URI is preferred; title/artist hints are the portable fallback. Events without identity
 * are still supported, but are associated through the reducer's ordered fallback queue.</p>
 */
final class LyricSourceEvent {
    enum Outcome {
        LOOKUP_STARTED,
        RESOLVED,
        NO_LYRIC,
        PARSE_FAILED,
        SOURCE_MISS
    }

    final Outcome outcome;
    final String source;
    final String requestId;
    final String mediaId;
    final String mediaUri;
    final String trackHintKey;
    final String lyric;
    final String rawLyric;
    final long occurredAtMillis;
    final LyricProviderCapabilities capabilities;

    private LyricSourceEvent(
            Outcome outcome,
            String source,
            String requestId,
            String mediaId,
            String mediaUri,
            String trackHintKey,
            String lyric,
            String rawLyric,
            long occurredAtMillis,
            LyricProviderCapabilities capabilities) {
        this.outcome = outcome == null ? Outcome.SOURCE_MISS : outcome;
        this.source = nullToEmpty(source);
        this.requestId = nullToEmpty(requestId);
        this.mediaId = nullToEmpty(mediaId);
        this.mediaUri = normalizeUri(mediaUri);
        this.trackHintKey = nullToEmpty(trackHintKey);
        this.lyric = nullToEmpty(lyric);
        this.rawLyric = nullToEmpty(rawLyric);
        this.occurredAtMillis = occurredAtMillis;
        this.capabilities = capabilities == null
                ? LyricProviderCapabilities.PASSIVE_PARSER
                : capabilities;
    }

    static LyricSourceEvent lookupStarted(
            String source,
            String requestId,
            String mediaId,
            String mediaUri,
            String trackHintKey,
            long occurredAtMillis,
            LyricProviderCapabilities capabilities) {
        return new LyricSourceEvent(
                Outcome.LOOKUP_STARTED,
                source,
                requestId,
                mediaId,
                mediaUri,
                trackHintKey,
                "",
                "",
                occurredAtMillis,
                capabilities);
    }

    static LyricSourceEvent resolved(
            String source,
            String requestId,
            String mediaId,
            String mediaUri,
            String trackHintKey,
            String lyric,
            String rawLyric,
            long occurredAtMillis,
            LyricProviderCapabilities capabilities) {
        return new LyricSourceEvent(
                Outcome.RESOLVED,
                source,
                requestId,
                mediaId,
                mediaUri,
                trackHintKey,
                lyric,
                rawLyric,
                occurredAtMillis,
                capabilities);
    }

    static LyricSourceEvent terminal(
            Outcome outcome,
            String source,
            String requestId,
            String mediaId,
            String mediaUri,
            String trackHintKey,
            String rawLyric,
            long occurredAtMillis,
            LyricProviderCapabilities capabilities) {
        if (outcome != Outcome.NO_LYRIC
                && outcome != Outcome.PARSE_FAILED
                && outcome != Outcome.SOURCE_MISS) {
            throw new IllegalArgumentException("Unsupported terminal outcome " + outcome);
        }
        return new LyricSourceEvent(
                outcome,
                source,
                requestId,
                mediaId,
                mediaUri,
                trackHintKey,
                "",
                rawLyric,
                occurredAtMillis,
                capabilities);
    }

    static String normalizeUri(String value) {
        String normalized = nullToEmpty(value).trim();
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
