package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

/** Card playerId is a session association key, never a song media ID. */
public record ArtworkCardIdentity(String playerId, String packageName, String title, String artist) {
    public ArtworkCardIdentity {
        playerId = clean(playerId);
        packageName = clean(packageName);
        title = clean(title);
        artist = clean(artist);
    }

    static String clean(String value) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() <= 512 ? text : "";
    }

    public boolean complete() {
        return !playerId.isEmpty() && !packageName.isEmpty() && !title.isEmpty() && !artist.isEmpty();
    }
}
