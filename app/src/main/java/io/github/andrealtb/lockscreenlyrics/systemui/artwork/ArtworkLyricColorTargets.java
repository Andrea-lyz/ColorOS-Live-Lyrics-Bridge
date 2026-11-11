package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.view.View;
import java.util.Iterator;
import java.util.WeakHashMap;

/** Main-thread weak subscribers, registered only by the scoped lyric draw path. */
final class ArtworkLyricColorTargets {
    private final WeakHashMap<View, Boolean> targets = new WeakHashMap<>();

    void watch(View lyric) {
        if (lyric != null) targets.put(lyric, Boolean.TRUE);
    }

    void invalidate(View root) {
        Iterator<View> iterator = targets.keySet().iterator();
        while (iterator.hasNext()) {
            View lyric = iterator.next();
            if (root == null || !belongsTo(lyric, root)) {
                iterator.remove();
            } else {
                // Invalidating only the parent reuses the child's hardware display list.
                lyric.postInvalidateOnAnimation();
            }
        }
    }

    void clear() {
        targets.clear();
    }

    private static boolean belongsTo(View lyric, View root) {
        for (View node = lyric; node != null;
                node = node.getParent() instanceof View parent ? parent : null) {
            if (node == root) return true;
        }
        return false;
    }
}
