package io.github.andrealtb.lockscreenlyrics;

final class PowerampLocalAdapter extends FirstBatchMediaSessionAdapter {
    private static final String PACKAGE_NAME = "com.maxmpz.audioplayer";

    PowerampLocalAdapter() {
        super(PACKAGE_NAME, "Poweramp", "Poweramp local lyrics");
    }

    @Override
    protected void onLookupNotAvailable(LockscreenLyricsModule module, TrackMetadata track) {
        module.info("Poweramp first-batch adapter observed metadata; local embedded-lyrics "
                + "reader is pending. Online search is intentionally excluded from first-batch "
                + "output, title=" + track.title);
    }
}
