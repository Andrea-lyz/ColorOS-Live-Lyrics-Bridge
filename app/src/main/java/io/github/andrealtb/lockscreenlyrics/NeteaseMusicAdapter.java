package io.github.andrealtb.lockscreenlyrics;

import android.text.TextUtils;

final class NeteaseMusicAdapter extends FirstBatchMediaSessionAdapter {
    private static final String PACKAGE_NAME = "com.netease.cloudmusic";

    NeteaseMusicAdapter() {
        super(PACKAGE_NAME, "NetEase Cloud Music", "NetEase YRC");
    }

    @Override
    protected boolean shouldLookupLyrics(TrackMetadata track) {
        return false;
    }

    @Override
    protected void onLookupNotAvailable(LockscreenLyricsModule module, TrackMetadata track) {
        String identity = TextUtils.isEmpty(track.mediaId) ? track.trackHintKey() : track.mediaId;
        module.info("NetEase first-batch adapter observed track=" + identity
                + "; YRC/EAPI resolver is pending. Romanized lyric lanes are intentionally "
                + "excluded from first-batch output.");
    }
}
