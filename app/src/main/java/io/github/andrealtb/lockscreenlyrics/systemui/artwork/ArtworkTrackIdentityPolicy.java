package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

/** Conservative identity observations, scoped by the registry's exact session epoch. */
public final class ArtworkTrackIdentityPolicy {
    public record Metadata(String mediaId, String title, String artist, String album, long durationMs,
            String displayTitle, String displayArtist) {
        public Metadata(String mediaId, String title, String artist, String album, long durationMs) {
            this(mediaId, title, artist, album, durationMs, "", "");
        }
        public Metadata {
            mediaId = ArtworkCardIdentity.clean(mediaId);
            title = ArtworkCardIdentity.clean(title);
            artist = ArtworkCardIdentity.clean(artist);
            album = ArtworkCardIdentity.clean(album);
            durationMs = Math.max(0, durationMs);
            displayTitle = ArtworkCardIdentity.clean(displayTitle);
            displayArtist = ArtworkCardIdentity.clean(displayArtist);
        }
    }
    public record Update(long generation, long revision, boolean ready, boolean changed) {}

    /**
     * Song generations shared by every host bound to one session. A host's own counter starts with the
     * host, so a card that lived through earlier songs and a freshly attached large cover would number
     * the same song differently. Hosts only accept the session's current metadata, so the latest
     * accepted song is the session's song.
     */
    public static final class SessionSongs {
        private String key;
        private long generation;

        public long generation(Metadata accepted) {
            String next = songKey(accepted);
            if (!next.equals(key)) { key = next; generation++; }
            return generation;
        }
    }

    /** The identity observe() treats as one song: the stable ID, or title and artist without one. */
    static String songKey(Metadata metadata) {
        return metadata.mediaId().isEmpty() ? "fallback\n" + metadata.title() + "\n" + metadata.artist()
                : "id\n" + metadata.mediaId();
    }
    private Metadata accepted;
    private Metadata candidate;
    private long candidateSince;
    private long generation;
    private long revision;
    private boolean ready;
    private final boolean allowStableFallback;
    private record SourceSong(String title, String artist, long durationMs) {
        boolean matches(Metadata metadata) {
            return metadata != null && title.equals(metadata.title()) && artist.equals(metadata.artist())
                    && durationMs > 0 && metadata.durationMs() > 0 && Math.abs(durationMs - metadata.durationMs()) <= 3000;
        }
    }
    private SourceSong sourceSong;
    private SourceSong replacementSource;
    private Metadata freshCandidate;

    public ArtworkTrackIdentityPolicy() { this(false); }

    /** Only a separately verified stable metadata origin may enable fallback observations. */
    public ArtworkTrackIdentityPolicy(boolean allowStableFallback) {
        this.allowStableFallback = allowStableFallback;
    }

    public boolean stableFallbackAllowed() { return allowStableFallback; }

    /** Called only after unique, same-session association, before checking the card/controller agreement. */
    public boolean observeSource(String title, String artist, long durationMs) {
        SourceSong next = new SourceSong(ArtworkCardIdentity.clean(title), ArtworkCardIdentity.clean(artist), durationMs);
        if (next.title().isEmpty() || next.artist().isEmpty() || durationMs <= 0) {
            sourceSong = null; replacementSource = null; freshCandidate = null;
            return false;
        }
        boolean changed = sourceSong != null && (!sourceSong.title().equals(next.title()) || !sourceSong.artist().equals(next.artist()));
        boolean anchored = accepted != null && accepted.mediaId().isEmpty()
                && (sourceSong != null && sourceSong.matches(accepted) || sourceSong != null && sourceSong.equals(replacementSource));
        if (changed) {
            replacementSource = anchored && !next.matches(accepted) ? next : null;
            freshCandidate = null;
            candidate = null;
        } else if (replacementSource != null && replacementSource.equals(sourceSong) && !next.equals(sourceSong)) {
            replacementSource = next;
            freshCandidate = null;
        }
        sourceSong = next;
        return changed && replacementSource != null;
    }

    /** A timed off-main controller read must independently confirm the exact pending snapshot. */
    public void confirmFreshMetadata(Metadata metadata, long now) {
        if (metadata != null && metadata.equals(candidate) && sourceReplacement(metadata)
                && now >= candidateSince && now - candidateSince >= 500) freshCandidate = metadata;
    }

    public Update observe(ArtworkCardIdentity card, Metadata metadata, long now) {
        if (card == null || metadata == null || !card.complete()
                || !card.title().equals(metadata.title()) || !card.artist().equals(metadata.artist())) {
            return invalidate();
        }
        if (metadata.mediaId().isEmpty()) {
            if (!allowStableFallback) return invalidate();
            if (accepted != null && !accepted.mediaId().isEmpty()) return invalidate();
            if (displayConflict(metadata) || ambiguousFallbackTitle(metadata) && !sourceReplacement(metadata)) return invalidate();
            // No ID: require two separately timed, identical full observations with a known duration.
            if (metadata.durationMs() == 0) return invalidate();
            if (!metadata.equals(candidate)) {
                candidate = metadata;
                candidateSince = now;
                freshCandidate = null;
                return invalidateReadyOnly();
            }
            if (now < candidateSince || now - candidateSince < 500) return invalidateReadyOnly();
            if (ambiguousFallbackTitle(metadata) && !metadata.equals(freshCandidate)) return invalidateReadyOnly();
        } else {
            candidate = null;
        }
        boolean sameSong = accepted != null && (metadata.mediaId().isEmpty()
                ? accepted.mediaId().isEmpty() && accepted.title().equals(metadata.title())
                    && accepted.artist().equals(metadata.artist())
                : metadata.mediaId().equals(accepted.mediaId()));
        // A stable ID with a different title/artist is torn or projected metadata, not a new generation.
        if (sameSong && (!accepted.title().equals(metadata.title())
                || !accepted.artist().equals(metadata.artist()))) return invalidate();
        boolean changed = !ready || !metadata.equals(accepted);
        if (!sameSong) generation++;
        if (changed) revision++;
        accepted = metadata;
        replacementSource = null;
        freshCandidate = null;
        ready = true;
        return new Update(generation, revision, true, changed);
    }

    public Update invalidate() {
        candidate = null;
        freshCandidate = null;
        return invalidateReadyOnly();
    }

    private Update invalidateReadyOnly() {
        boolean changed = ready;
        if (changed) revision++;
        ready = false;
        return new Update(generation, revision, false, changed);
    }

    public Metadata accepted() { return ready ? accepted : null; }

    /** Read-only explanation: does not observe, invalidate, advance a clock or change readiness. */
    public String diagnosticReason(ArtworkCardIdentity card, Metadata metadata, long now) {
        if (card == null || !card.complete()) return "card_incomplete";
        if (metadata == null) return "metadata_missing";
        if (!card.title().equals(metadata.title())) return "card_title_mismatch";
        if (!card.artist().equals(metadata.artist())) return "card_artist_mismatch";
        if (metadata.mediaId().isEmpty()) {
            if (!allowStableFallback) return "media_id_missing_fallback_disabled";
            if (accepted != null && !accepted.mediaId().isEmpty()) return "media_id_temporarily_missing";
            if (displayConflict(metadata)) return "display_metadata_conflict";
            if (ambiguousFallbackTitle(metadata) && !sourceReplacement(metadata)) return "fallback_title_change_ambiguous";
            if (metadata.durationMs() == 0) return "fallback_duration_missing";
            if (!metadata.equals(candidate)) return "fallback_candidate_changed";
            if (now < candidateSince || now - candidateSince < 500) return "fallback_stabilizing";
            if (ambiguousFallbackTitle(metadata) && !metadata.equals(freshCandidate)) return "fallback_source_recheck_required";
        }
        if (accepted != null && !metadata.mediaId().isEmpty() && metadata.mediaId().equals(accepted.mediaId())
                && (!metadata.title().equals(accepted.title()) || !metadata.artist().equals(accepted.artist()))) {
            return "same_id_text_changed";
        }
        return "identity_consistent";
    }

    private static boolean displayConflict(Metadata metadata) {
        return (!metadata.displayTitle().isEmpty() && !metadata.displayTitle().equals(metadata.title()))
                || (!metadata.displayArtist().isEmpty() && !metadata.displayArtist().equals(metadata.artist()));
    }

    private boolean ambiguousFallbackTitle(Metadata metadata) {
        return accepted != null && accepted.mediaId().isEmpty() && metadata.mediaId().isEmpty()
                && !accepted.title().equals(metadata.title()) && accepted.artist().equals(metadata.artist())
                && Math.abs(accepted.durationMs() - metadata.durationMs()) <= 3000;
    }

    private boolean sourceReplacement(Metadata metadata) {
        return allowStableFallback && replacementSource != null && replacementSource.equals(sourceSong)
                && replacementSource.matches(metadata) && !metadata.album().isEmpty() && !displayConflict(metadata);
    }
}
