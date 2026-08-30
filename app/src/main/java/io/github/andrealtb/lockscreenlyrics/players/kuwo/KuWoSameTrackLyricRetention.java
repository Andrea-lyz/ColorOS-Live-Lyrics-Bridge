package io.github.andrealtb.lockscreenlyrics.players.kuwo;

/**
 * Same-track KuWo plugin lyric retention. Callers that also mutate the plugin
 * model must hold {@link #lock()} across {@link #decide}, model writes, and
 * {@link #commit} so remembered state and the live model stay aligned.
 *
 * <p>{@link #commit} still runs when album-art repair returns {@code false}
 * (the repair helper swallows failures). Skip {@link #commit} only when a
 * plugin-model write throws before it is reached. REMEMBER/CLEAR must update
 * memory in that false-return path, matching the previous in-module order.
 */
public final class KuWoSameTrackLyricRetention {
    public enum Action {
        NOOP,
        RETAIN_CAR_LYRIC_MUTATION,
        CLEAR_FOR_TRACK_CHANGE,
        RESTORE_EMPTY_MODEL,
        REMEMBER_CURRENT
    }

    public static final class Result {
        public static final Result NOOP = new Result(
                Action.NOOP,
                null,
                false,
                null,
                null);

        public final Action action;
        public final Object lyricToRestore;
        public final boolean repairAlbumArt;
        public final String repairTitle;
        public final String repairArtist;

        Result(
                Action action,
                Object lyricToRestore,
                boolean repairAlbumArt,
                String repairTitle,
                String repairArtist) {
            this.action = action;
            this.lyricToRestore = lyricToRestore;
            this.repairAlbumArt = repairAlbumArt;
            this.repairTitle = repairTitle;
            this.repairArtist = repairArtist;
        }
    }

    private final Object lock = new Object();
    private String trackKey;
    private String title;
    private String artist;
    private Object lastLyric;

    public Object lock() {
        return lock;
    }

    public Result decide(
            String incomingTrackKey,
            String incomingTitle,
            String incomingArtist,
            Object incomingLyric,
            int incomingLineCount) {
        synchronized (lock) {
            return decideUnlocked(
                    incomingTrackKey,
                    incomingTitle,
                    incomingArtist,
                    incomingLyric,
                    incomingLineCount);
        }
    }

    public void commit(
            Result result,
            String incomingTrackKey,
            String incomingTitle,
            String incomingArtist,
            Object incomingLyric) {
        synchronized (lock) {
            commitUnlocked(
                    result,
                    incomingTrackKey,
                    incomingTitle,
                    incomingArtist,
                    incomingLyric);
        }
    }

    public Result evaluateAndCommit(
            String incomingTrackKey,
            String incomingTitle,
            String incomingArtist,
            Object incomingLyric,
            int incomingLineCount) {
        synchronized (lock) {
            Result result = decideUnlocked(
                    incomingTrackKey,
                    incomingTitle,
                    incomingArtist,
                    incomingLyric,
                    incomingLineCount);
            commitUnlocked(
                    result,
                    incomingTrackKey,
                    incomingTitle,
                    incomingArtist,
                    incomingLyric);
            return result;
        }
    }

    public String rememberedTrackKey() {
        synchronized (lock) {
            return trackKey;
        }
    }

    public String rememberedTitle() {
        synchronized (lock) {
            return title;
        }
    }

    public String rememberedArtist() {
        synchronized (lock) {
            return artist;
        }
    }

    public Object rememberedLyric() {
        synchronized (lock) {
            return lastLyric;
        }
    }

    private Result decideUnlocked(
            String incomingTrackKey,
            String incomingTitle,
            String incomingArtist,
            Object incomingLyric,
            int incomingLineCount) {
        boolean trackChanged = !isEmpty(incomingTrackKey)
                && !incomingTrackKey.equals(trackKey);
        if (trackChanged) {
            if (KuWoMediaIdentityPolicy.isCarLyricMetadataMutation(
                    title,
                    artist,
                    incomingTitle,
                    incomingArtist)) {
                return new Result(
                        Action.RETAIN_CAR_LYRIC_MUTATION,
                        lastLyric,
                        true,
                        title,
                        artist);
            }
            return new Result(
                    Action.CLEAR_FOR_TRACK_CHANGE,
                    null,
                    false,
                    incomingTitle,
                    incomingArtist);
        }
        if (incomingLineCount <= 0 && lastLyric != null) {
            return new Result(
                    Action.RESTORE_EMPTY_MODEL,
                    lastLyric,
                    true,
                    incomingTitle,
                    incomingArtist);
        }
        if (incomingLineCount > 0 && incomingLyric != null) {
            return new Result(
                    Action.REMEMBER_CURRENT,
                    null,
                    true,
                    incomingTitle,
                    incomingArtist);
        }
        return Result.NOOP;
    }

    private void commitUnlocked(
            Result result,
            String incomingTrackKey,
            String incomingTitle,
            String incomingArtist,
            Object incomingLyric) {
        if (result == null) {
            return;
        }
        if (result.action == Action.CLEAR_FOR_TRACK_CHANGE) {
            trackKey = incomingTrackKey;
            title = incomingTitle;
            artist = incomingArtist;
            lastLyric = null;
            return;
        }
        if (result.action == Action.REMEMBER_CURRENT) {
            if (!isEmpty(incomingTrackKey)) {
                trackKey = incomingTrackKey;
                title = incomingTitle;
                artist = incomingArtist;
            }
            lastLyric = incomingLyric;
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
