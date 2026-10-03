package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import java.util.ArrayDeque;
import java.util.WeakHashMap;

/** Main-thread, bounded early-start observations. Only a claimed object's completed draw permits display. */
public final class ArtworkTransitionRegistry {
    private static final int MAX_STATES = 32;
    private static final int MAX_DRAWS = 8;
    private static final class Entry {
        final ArtworkTransitionState state = new ArtworkTransitionState();
        final long reference;
        boolean claimed;
        Entry(long reference) { this.reference = reference; }
    }
    private record Draw(Object owner, Entry entry, long revision) {}
    private final WeakHashMap<Object, Entry> entries = new WeakHashMap<>();
    private final ArrayDeque<Draw> draws = new ArrayDeque<>();
    private long sequence;

    private Entry entry(Object owner) {
        Entry existing = entries.get(owner);
        if (existing != null) return existing;
        if (entries.size() >= MAX_STATES) {
            var iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                if (!iterator.next().getValue().claimed) { iterator.remove(); break; }
            }
            if (entries.size() >= MAX_STATES) return null;
        }
        Entry created = new Entry(++sequence);
        entries.put(owner, created);
        return created;
    }

    public void start(Object owner) { Entry value = entry(owner); if (value != null) value.state.start(); }
    public void reverseOrReset(Object owner) { Entry value = entry(owner); if (value != null) value.state.reverseOrReset(); }
    public void invalidate(Object owner) { Entry value = entries.get(owner); if (value != null) value.state.invalidate(); }
    public void release(Object owner) { Entry value = entries.get(owner); if (value != null) value.claimed = false; }

    public void beginDraw(Object owner) {
        Entry value = entry(owner);
        if (value == null) return;
        value.claimed = true;
        if (draws.size() >= MAX_DRAWS) { value.state.reverseOrReset(); return; }
        draws.push(new Draw(owner, value, value.state.beginDraw()));
    }
    public void endDraw(Object owner) {
        if (draws.isEmpty() || draws.peek().owner() != owner) return;
        Draw draw = draws.pop();
        draw.entry().state.endDraw(draw.revision());
    }
    public boolean complete(Object owner) {
        Entry value = entries.get(owner);
        return value != null && value.claimed && value.state.complete();
    }
    public String diagnostic(Object owner) {
        Entry value = entries.get(owner);
        return value == null ? "drawableRef=0 observed=false complete=false" : "drawableRef=" + value.reference
                + " observed=true claimed=" + value.claimed + " " + value.state.diagnostic();
    }
    public int size() { return entries.size(); }
    public void clear() { entries.clear(); draws.clear(); }
}
