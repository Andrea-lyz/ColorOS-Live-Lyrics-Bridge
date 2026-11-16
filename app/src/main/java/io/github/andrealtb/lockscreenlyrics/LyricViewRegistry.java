package io.github.andrealtb.lockscreenlyrics;

import android.view.View;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Weak-reference registry of the lockscreen LyricsRecyclerViews and their root views (E-6a, plan
 * §15.3): membership, attached / proximity queries and pruned snapshots. Moved verbatim from
 * {@code LockscreenLyricsModule}; no method body changed and it needs no host.
 *
 * <p>Registration of a recycler ({@code rememberLyricsRecyclerView}) stays in the entry class
 * because it also binds the recycler adapter and logs the attachment; it adds to this registry's
 * list under the registry's lock. The Recycler hooks and controller follow in later E-6 steps.
 */
final class LyricViewRegistry {
    final Object lyricsRecyclerViewsLock = new Object();
    final ArrayList<WeakReference<View>> lyricsRecyclerViews = new ArrayList<>();
    private final Object lyricRootViewsLock = new Object();
    private final ArrayList<WeakReference<View>> lyricRootViews = new ArrayList<>();

    boolean isNearRememberedLyricsRecyclerView(View candidate) {
        if (candidate == null) {
            return false;
        }
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View recycler = lyricsRecyclerViews.get(i).get();
                if (recycler == null) {
                    lyricsRecyclerViews.remove(i);
                    continue;
                }
                View candidateAncestor = candidate;
                for (int candidateDepth = 0;
                        candidateAncestor != null && candidateDepth < 9;
                        candidateDepth++) {
                    View recyclerAncestor = recycler;
                    for (int recyclerDepth = 0;
                            recyclerAncestor != null && recyclerDepth < 9;
                            recyclerDepth++) {
                        if (candidateAncestor == recyclerAncestor) {
                            return true;
                        }
                        Object recyclerParent = recyclerAncestor.getParent();
                        recyclerAncestor = recyclerParent instanceof View
                                ? (View) recyclerParent
                                : null;
                    }
                    Object candidateParent = candidateAncestor.getParent();
                    candidateAncestor = candidateParent instanceof View
                            ? (View) candidateParent
                            : null;
                }
            }
        }
        return false;
    }

    void rememberLyricRootView(TextView textView) {
        if (textView == null) {
            return;
        }
        View root = textView.getRootView();
        if (root == null) {
            return;
        }
        synchronized (lyricRootViewsLock) {
            for (int i = lyricRootViews.size() - 1; i >= 0; i--) {
                View existing = lyricRootViews.get(i).get();
                if (existing == null) {
                    lyricRootViews.remove(i);
                } else if (existing == root) {
                    return;
                }
            }
            if (lyricRootViews.size() >= 4) {
                lyricRootViews.remove(0);
            }
            lyricRootViews.add(new WeakReference<>(root));
        }
    }

    boolean hasAttachedLyricsRecyclerView() {
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View recycler = lyricsRecyclerViews.get(i).get();
                if (recycler == null) {
                    lyricsRecyclerViews.remove(i);
                } else if (recycler.isAttachedToWindow()) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isRememberedLyricsRecyclerView(View candidate) {
        if (candidate == null) {
            return false;
        }
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View recycler = lyricsRecyclerViews.get(i).get();
                if (recycler == null) {
                    lyricsRecyclerViews.remove(i);
                } else if (recycler == candidate) {
                    return true;
                }
            }
        }
        return false;
    }

    ArrayList<View> snapshotLyricRootViews() {
        ArrayList<View> result = new ArrayList<>();
        synchronized (lyricRootViewsLock) {
            for (int i = lyricRootViews.size() - 1; i >= 0; i--) {
                View root = lyricRootViews.get(i).get();
                if (root == null) {
                    lyricRootViews.remove(i);
                } else {
                    result.add(root);
                }
            }
        }
        return result;
    }

    ArrayList<View> snapshotLyricsRecyclerViews() {
        ArrayList<View> result = new ArrayList<>();
        synchronized (lyricsRecyclerViewsLock) {
            for (int i = lyricsRecyclerViews.size() - 1; i >= 0; i--) {
                View recycler = lyricsRecyclerViews.get(i).get();
                if (recycler == null) {
                    lyricsRecyclerViews.remove(i);
                } else if (recycler.isAttachedToWindow()) {
                    result.add(recycler);
                }
            }
        }
        return result;
    }

    View firstAttachedLyricsRecyclerView() {
        for (View recycler : snapshotLyricsRecyclerViews()) {
            if (recycler != null && recycler.isAttachedToWindow()) {
                return recycler;
            }
        }
        return null;
    }
}
