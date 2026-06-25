package io.github.andrealtb.lockscreenlyrics;

final class AppleMusicAdapter extends FirstBatchMediaSessionAdapter {
    private static final String PACKAGE_NAME = "com.apple.android.music";

    AppleMusicAdapter() {
        super(PACKAGE_NAME, "Apple Music", "Apple Music lyrics");
    }

    @Override
    protected void onLookupNotAvailable(LockscreenLyricsModule module, TrackMetadata track) {
        module.info("Apple Music first-batch adapter observed metadata; native song parser is "
                + "pending. Background vocals, duet lanes, and romanized lyric lanes are "
                + "intentionally excluded from first-batch output, title=" + track.title);
    }
}
