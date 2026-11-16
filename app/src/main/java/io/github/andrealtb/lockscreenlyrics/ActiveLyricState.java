package io.github.andrealtb.lockscreenlyrics;

import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;
import static io.github.andrealtb.lockscreenlyrics.LyricsRecyclerViews.*;

import android.view.View;
import android.widget.TextView;

import io.github.andrealtb.lockscreenlyrics.render.WordLine;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Active lyric state (E-7b, plan §15.8.12): the active line text and time, the active renderer
 * word line, the active renderer TextView and per-recycler render targets, and the registry of
 * active lyric TextViews. This class is the only writer of that state; the entry class reads it and
 * calls {@link #setActiveLine}, {@link #clearActiveLine} and {@link #setActiveRendererWordLine} where
 * it used to assign the fields inline.
 *
 * <p>The refresh scheduling itself (cadence, posted flag, refresh runnable, mode-rebind runnable)
 * stays in the entry class for the next step; it drives the renderer and the recycler.
 */
final class ActiveLyricState {
    private final LyricViewRegistry lyricViews;
    volatile String activeLyricLine = "";
    volatile long activeLyricLineTimeMs = -1L;
    private WordLine activeRendererWordLine;
    WeakReference<TextView> activeRendererTextView = new WeakReference<>(null);
    private final Object activeRendererTargetsLock = new Object();
    private final WeakHashMap<View, WeakReference<TextView>> activeRendererTargetsByRecycler =
            new WeakHashMap<>();
    private final Object activeLyricTextViewsLock = new Object();
    private final ArrayList<WeakReference<TextView>> activeLyricTextViews = new ArrayList<>();

    ActiveLyricState(LyricViewRegistry lyricViews) {
        this.lyricViews = lyricViews;
    }

    void setActiveLine(String normalizedText, long timeMillis) {
        activeLyricLine = normalizedText;
        activeLyricLineTimeMs = timeMillis;
    }

    void clearActiveLine() {
        activeLyricLine = "";
        activeLyricLineTimeMs = -1L;
    }

    void setActiveRendererWordLine(WordLine line) {
        activeRendererWordLine = line;
    }

    boolean isRememberedActiveTextView(TextView target) {
        synchronized (activeLyricTextViewsLock) {
            for (int i = activeLyricTextViews.size() - 1; i >= 0; i--) {
                TextView textView = activeLyricTextViews.get(i).get();
                if (textView == null) {
                    activeLyricTextViews.remove(i);
                } else if (textView == target) {
                    return true;
                }
            }
        }
        return false;
    }

    void rememberActiveTextViewReference(TextView textView) {
        lyricViews.rememberLyricRootView(textView);
        synchronized (activeLyricTextViewsLock) {
            for (int i = activeLyricTextViews.size() - 1; i >= 0; i--) {
                TextView existing = activeLyricTextViews.get(i).get();
                if (existing == null) {
                    activeLyricTextViews.remove(i);
                } else if (existing == textView) {
                    return;
                }
            }
            if (activeLyricTextViews.size() >= 8) {
                activeLyricTextViews.remove(0);
            }
            activeLyricTextViews.add(new WeakReference<>(textView));
        }
    }

    void rememberActiveRendererTextView(TextView textView) {
        if (textView == null) {
            return;
        }
        if (activeRendererTextView.get() != textView) {
            activeRendererTextView = new WeakReference<>(textView);
        }
        View recycler = findContainingLyricsRecyclerView(textView);
        if (recycler != null) {
            synchronized (activeRendererTargetsLock) {
                activeRendererTargetsByRecycler.put(recycler, new WeakReference<>(textView));
            }
        }
    }

    void forgetActiveRendererTarget(TextView textView) {
        if (textView == null) {
            return;
        }
        if (activeRendererTextView.get() == textView) {
            activeRendererTextView = new WeakReference<>(null);
        }
        synchronized (activeRendererTargetsLock) {
            java.util.Iterator<Map.Entry<View, WeakReference<TextView>>> iterator =
                    activeRendererTargetsByRecycler.entrySet().iterator();
            while (iterator.hasNext()) {
                WeakReference<TextView> reference = iterator.next().getValue();
                if (reference == null || reference.get() == null || reference.get() == textView) {
                    iterator.remove();
                }
            }
        }
    }

    void forgetActiveRendererTarget(View recycler) {
        if (recycler == null) {
            return;
        }
        TextView removed = null;
        synchronized (activeRendererTargetsLock) {
            WeakReference<TextView> reference = activeRendererTargetsByRecycler.remove(recycler);
            removed = reference == null ? null : reference.get();
        }
        if (removed != null && activeRendererTextView.get() == removed) {
            activeRendererTextView = new WeakReference<>(null);
        }
    }

    ArrayList<TextView> snapshotActiveRendererTargets() {
        ArrayList<TextView> targets = new ArrayList<>(4);
        synchronized (activeRendererTargetsLock) {
            java.util.Iterator<Map.Entry<View, WeakReference<TextView>>> iterator =
                    activeRendererTargetsByRecycler.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<View, WeakReference<TextView>> entry = iterator.next();
                View recycler = entry.getKey();
                WeakReference<TextView> reference = entry.getValue();
                TextView textView = reference == null ? null : reference.get();
                if (recycler == null
                        || textView == null
                        || !recycler.isAttachedToWindow()
                        || !textView.isAttachedToWindow()
                        || findContainingLyricsRecyclerView(textView) != recycler) {
                    iterator.remove();
                    continue;
                }
                targets.add(textView);
            }
        }
        return targets;
    }

    void clearActiveRendererTargets() {
        activeRendererTextView = new WeakReference<>(null);
        synchronized (activeRendererTargetsLock) {
            activeRendererTargetsByRecycler.clear();
        }
    }

    ArrayList<TextView> snapshotActiveTextViews() {
        ArrayList<TextView> result = new ArrayList<>();
        collectActiveTextViews(result);
        return result;
    }

    void collectActiveTextViews(ArrayList<TextView> result) {
        if (result == null) {
            return;
        }
        synchronized (activeLyricTextViewsLock) {
            for (int i = activeLyricTextViews.size() - 1; i >= 0; i--) {
                TextView textView = activeLyricTextViews.get(i).get();
                if (textView == null
                        || !textView.isAttachedToWindow()
                        || !isInLyricsRecyclerView(textView)) {
                    activeLyricTextViews.remove(i);
                } else {
                    result.add(textView);
                }
            }
        }
    }

    TextView firstActiveLyricTextView() {
        synchronized (activeLyricTextViewsLock) {
            for (int i = activeLyricTextViews.size() - 1; i >= 0; i--) {
                TextView textView = activeLyricTextViews.get(i).get();
                if (textView == null
                        || !textView.isAttachedToWindow()
                        || !isInLyricsRecyclerView(textView)) {
                    activeLyricTextViews.remove(i);
                } else if (isEffectivelyVisible(textView)) {
                    return textView;
                }
            }
        }
        return null;
    }
}
