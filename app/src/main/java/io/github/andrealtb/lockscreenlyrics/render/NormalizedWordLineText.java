package io.github.andrealtb.lockscreenlyrics.render;

import java.util.ArrayList;

/**
 * A lyric line together with its normalized form and word ranges. Promoted
 * from {@code LockscreenLyricsModule} in step 3.1.
 */
public final class NormalizedWordLineText {

    public final String text;
    public final ArrayList<WordRange> words;

    public NormalizedWordLineText(String text, ArrayList<WordRange> words) {
        this.text = text;
        this.words = words;
    }
}
