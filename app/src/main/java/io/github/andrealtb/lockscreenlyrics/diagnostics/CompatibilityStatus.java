package io.github.andrealtb.lockscreenlyrics.diagnostics;

import android.os.Bundle;
import android.os.SystemClock;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.LongSupplier;

/** Bounded, process-local observations. Queries neither resolve targets nor run business code. */
public final class CompatibilityStatus {
    public enum Feature { CUSTOM_DRAW, COVER_GLOW, CHAR_LIFT, LINE_PROGRESS, TRANSLATION_PROGRESS,
        TRANSLATION_BUTTON, DYNAMIC_ARTWORK, WORD_PROGRESS }
    public enum Truth { UNKNOWN, YES, NO }
    public enum Path { UNKNOWN, NATIVE, CUSTOM, MANUAL_COLOR, COVER_COLOR, CHAR_LIFT, DYNAMIC_ARTWORK }
    public enum Outcome { NOT_OBSERVED, DISABLED, WAITING, FALLBACK, REJECTED, PREPARED,
        DISPATCHED, DRAW_COMMANDS, PRESENTATION_ASSIGNED }
    public enum Reason { NONE, CONFIG_DISABLED, RAW_LYRIC_MISSING, LINE_PROGRESS_DISABLED,
        AOD_LOW_FRAME_RATE, HOST_NOT_FOUND, SNAPSHOT_PENDING, PALETTE_UNAVAILABLE,
        DATASET_UNSUPPORTED, FRAME_REJECTED, NATIVE_FALLBACK, TARGET_NOT_OBSERVED, RUNTIME_EXCEPTION }
    public enum Scope { NOT_OBSERVED, CONFIG, LYRIC_ROW, RENDER_DISPATCH, PALETTE_SAMPLE,
        CHAR_LIFT_GATE, GLYPH_COMMANDS, VIEW_VISIBILITY, ARTWORK_CARD_BINDING, ARTWORK_IMMERSIVE_BINDING, ARTWORK_LIFECYCLE }
    public static final String SCHEMA = "cll_capability_schema";
    public static final String ROWS = "cll_capability_rows";
    private static final WeakReference<Object> NO_OWNER = new WeakReference<>(null);
    private static final CompatibilityStatus GLOBAL = new CompatibilityStatus(SystemClock::elapsedRealtime, 60_000L);
    private final LongSupplier clock;
    private final long freshnessMs;
    /**
     * Owners (lyric rows, artwork cards, ...) kept per feature. Several rows and surfaces report the
     * same feature at once; a single slot let an off-screen row overwrite the focused row. Each owner
     * now keeps its own latest observation and the snapshot projects the most recent live one plus
     * a per-outcome count. The bound keeps memory and lookup constant.
     */
    static final int OWNERS_PER_FEATURE = 8;
    private final EnumMap<Feature, FeatureState> entries = new EnumMap<>(Feature.class);
    private final java.util.WeakHashMap<Object, WeakReference<Object>> owners = new java.util.WeakHashMap<>();

    public CompatibilityStatus(LongSupplier clock, long freshnessMs) {
        this.clock = clock;
        this.freshnessMs = freshnessMs;
        for (Feature feature : Feature.values()) entries.put(feature, new FeatureState());
    }
    public static CompatibilityStatus global() { return GLOBAL; }

    public synchronized void requested(Feature feature, boolean requested) {
        FeatureState state = entries.get(feature);
        Truth value = requested ? Truth.YES : Truth.NO;
        if (state.requested == value) return;
        state.clear();
        state.requested = value;
    }

    /** No event log, string construction or per-frame state allocation on this write path. */
    public synchronized void observe(Feature feature, Object owner, boolean requested, Truth supported,
            boolean eligible, Path path, Outcome outcome, Reason reason, Scope scope) {
        FeatureState state = entries.get(feature);
        Entry e = state.slotFor(owner);
        state.requested = requested ? Truth.YES : Truth.NO;
        e.requested = state.requested;
        e.supported = supported;
        e.eligible = eligible ? Truth.YES : Truth.NO;
        e.path = path;
        e.outcome = outcome;
        e.reason = reason;
        e.scope = scope;
        e.observedAt = clock.getAsLong();
        if (ShortDiagnosticSession.global().active()) {
            ShortDiagnosticSession.global().record(feature, e.requested, e.supported, e.eligible,
                    e.path, e.outcome, e.reason, e.scope, e.observedAt);
        }
        if (e.owner.get() != owner) {
            if (owner == null) e.owner = NO_OWNER;
            else {
                WeakReference<Object> reference = owners.get(owner);
                if (reference == null) {
                    if (owners.size() >= 128) owners.remove(owners.keySet().iterator().next());
                    reference = new WeakReference<>(owner);
                    owners.put(owner, reference);
                }
                e.owner = reference;
            }
        }
    }

    public void draw(Object owner, Path path, Outcome outcome, Reason reason) {
        Truth supported = reason == Reason.DATASET_UNSUPPORTED ? Truth.NO
                : outcome == Outcome.DISPATCHED ? Truth.YES : Truth.UNKNOWN;
        observe(Feature.CUSTOM_DRAW, owner, true, supported, outcome == Outcome.DISPATCHED,
                path, outcome, reason, Scope.RENDER_DISPATCH);
    }
    public void palette(Object owner, boolean requested, boolean available) {
        observe(Feature.COVER_GLOW, owner, requested, available ? Truth.YES : Truth.UNKNOWN,
                available, available ? Path.COVER_COLOR : Path.MANUAL_COLOR,
                !requested ? Outcome.DISABLED : available ? Outcome.PREPARED : Outcome.FALLBACK,
                !requested ? Reason.CONFIG_DISABLED : available ? Reason.NONE : Reason.PALETTE_UNAVAILABLE,
                Scope.PALETTE_SAMPLE);
    }
    public void liftBlocked(Object owner, boolean requested, boolean unavailable, boolean aod,
            boolean rawMissing, boolean lineProgressBlocked) {
        Reason reason = !requested ? Reason.CONFIG_DISABLED : unavailable ? Reason.RUNTIME_EXCEPTION
                : aod ? Reason.AOD_LOW_FRAME_RATE : rawMissing ? Reason.RAW_LYRIC_MISSING
                : lineProgressBlocked ? Reason.LINE_PROGRESS_DISABLED : Reason.FRAME_REJECTED;
        observe(Feature.CHAR_LIFT, owner, requested, Truth.UNKNOWN, false, Path.CUSTOM,
                !requested ? Outcome.DISABLED : unavailable ? Outcome.FALLBACK : Outcome.WAITING,
                reason, Scope.CHAR_LIFT_GATE);
    }
    public void liftPrepared(Object owner) {
        observe(Feature.CHAR_LIFT, owner, true, Truth.YES, true, Path.CHAR_LIFT,
                Outcome.PREPARED, Reason.NONE, Scope.CHAR_LIFT_GATE);
    }
    public void liftDrawn(Object owner, boolean failed) {
        observe(Feature.CHAR_LIFT, owner, true, Truth.YES, !failed, failed ? Path.CUSTOM : Path.CHAR_LIFT,
                failed ? Outcome.FALLBACK : Outcome.DRAW_COMMANDS,
                failed ? Reason.RUNTIME_EXCEPTION : Reason.NONE, Scope.GLYPH_COMMANDS);
    }
    public void progress(Object owner, Feature feature, boolean requested, boolean eligible,
            boolean aod, boolean lineDependency) {
        Reason reason = !requested ? Reason.CONFIG_DISABLED : aod ? Reason.AOD_LOW_FRAME_RATE
                : lineDependency ? Reason.LINE_PROGRESS_DISABLED
                : eligible ? Reason.NONE : Reason.TARGET_NOT_OBSERVED;
        observe(feature, owner, requested, eligible ? Truth.YES : Truth.UNKNOWN, eligible,
                Path.CUSTOM, !requested ? Outcome.DISABLED : eligible ? Outcome.PREPARED : Outcome.WAITING,
                reason, Scope.LYRIC_ROW);
    }

    /**
     * Word-timed (karaoke) fill on the active line, recorded only for word-timed lines at the
     * normal draw decision (plan §14.17). There is no setting for it, so it is always requested.
     */
    public void wordProgress(Object owner, boolean drawn, boolean aod) {
        observe(Feature.WORD_PROGRESS, owner, true, drawn ? Truth.YES : Truth.UNKNOWN, drawn, Path.CUSTOM,
                drawn ? Outcome.PREPARED : Outcome.WAITING,
                drawn ? Reason.NONE : aod ? Reason.AOD_LOW_FRAME_RATE : Reason.TARGET_NOT_OBSERVED, Scope.LYRIC_ROW);
    }

    public void translationButton(Object owner, boolean requested, int translationCount, boolean selectedVisible) {
        observe(Feature.TRANSLATION_BUTTON, owner, requested, Truth.YES, selectedVisible, Path.NATIVE,
                requested ? Outcome.PRESENTATION_ASSIGNED : Outcome.DISABLED,
                !requested ? Reason.CONFIG_DISABLED : translationCount < 0 ? Reason.SNAPSHOT_PENDING
                : translationCount == 0 ? Reason.RAW_LYRIC_MISSING : Reason.NONE, Scope.VIEW_VISIBILITY);
    }
    public void translationPending(Object owner, boolean requested) {
        observe(Feature.TRANSLATION_BUTTON, owner, requested, Truth.UNKNOWN, false, Path.NATIVE,
                requested ? Outcome.WAITING : Outcome.DISABLED,
                requested ? Reason.SNAPSHOT_PENDING : Reason.CONFIG_DISABLED, Scope.VIEW_VISIBILITY);
    }
    public void artworkLifecycle(Object owner, boolean requested, boolean setupFailed) {
        observe(Feature.DYNAMIC_ARTWORK, owner, requested, Truth.UNKNOWN, false, Path.NATIVE,
                !requested ? Outcome.DISABLED : setupFailed ? Outcome.FALLBACK : Outcome.WAITING,
                !requested ? Reason.CONFIG_DISABLED : setupFailed ? Reason.RUNTIME_EXCEPTION : Reason.TARGET_NOT_OBSERVED,
                Scope.ARTWORK_LIFECYCLE);
    }
    public void artworkBinding(Object owner, boolean requested, boolean bindingReady, boolean displayReady, boolean immersive) {
        boolean eligible = bindingReady && displayReady;
        observe(Feature.DYNAMIC_ARTWORK, owner, requested, bindingReady ? Truth.YES : Truth.UNKNOWN,
                eligible, eligible ? Path.DYNAMIC_ARTWORK : Path.NATIVE,
                !requested ? Outcome.DISABLED : eligible ? Outcome.PREPARED : Outcome.WAITING,
                !requested ? Reason.CONFIG_DISABLED : eligible ? Reason.NONE : Reason.SNAPSHOT_PENDING,
                immersive ? Scope.ARTWORK_IMMERSIVE_BINDING : Scope.ARTWORK_CARD_BINDING);
    }

    public synchronized void forgetOwner(Object owner) {
        if (owner == null) return;
        for (FeatureState state : entries.values()) {
            for (Entry e : state.slots) if (e.owner.get() == owner) e.clear();
        }
    }
    public synchronized void invalidateAll() { for (FeatureState state : entries.values()) state.clearObservations(); }
    public synchronized void invalidateLyricObservations() {
        for (Feature f : Feature.values()) if (f != Feature.DYNAMIC_ARTWORK) entries.get(f).clearObservations();
    }
    synchronized int retainedOwners() { return owners.size(); }

    /** Snapshot-only projection; stale/collected targets become unknown without rewriting history. */
    public synchronized List<Snapshot> snapshot() {
        long now = clock.getAsLong();
        List<Snapshot> rows = new ArrayList<>(Feature.values().length);
        for (Feature f : Feature.values()) {
            FeatureState state = entries.get(f);
            Entry latest = null;
            int live = 0;
            int[] outcomes = new int[Outcome.values().length];
            for (Entry e : state.slots) {
                boolean observed = e.observedAt >= 0L && now >= e.observedAt
                        && now - e.observedAt <= freshnessMs && e.owner.get() != null;
                if (!observed) continue;
                live++;
                outcomes[e.outcome.ordinal()]++;
                if (latest == null || e.observedAt > latest.observedAt) latest = e;
            }
            Truth requested = latest == null ? state.requested : latest.requested;
            if (requested == Truth.NO) {
                rows.add(new Snapshot(f, requested, Truth.UNKNOWN, Truth.NO, Path.UNKNOWN,
                        Outcome.DISABLED, Reason.CONFIG_DISABLED, Scope.CONFIG,
                        latest == null ? -1L : latest.observedAt, live, outcomes));
            } else if (latest == null) {
                rows.add(new Snapshot(f, requested, Truth.UNKNOWN, Truth.UNKNOWN, Path.UNKNOWN,
                        Outcome.NOT_OBSERVED, Reason.TARGET_NOT_OBSERVED, Scope.NOT_OBSERVED, -1L,
                        0, outcomes));
            } else {
                rows.add(new Snapshot(f, requested, latest.supported, latest.eligible, latest.path,
                        latest.outcome, latest.reason, latest.scope, latest.observedAt, live, outcomes));
            }
        }
        return rows;
    }

    public void putStatus(Bundle result) {
        ArrayList<Bundle> rows = new ArrayList<>();
        for (Snapshot s : snapshot()) {
            Bundle row = new Bundle();
            row.putString("feature", s.feature.name());
            row.putString("requested", s.requested.name());
            row.putString("supported", s.supported.name());
            row.putString("eligible", s.eligible.name());
            row.putString("selectedPath", s.path.name());
            row.putString("lastOutcome", s.outcome.name());
            row.putString("reason", s.reason.name());
            row.putString("evidenceScope", s.scope.name());
            row.putLong("observedAt", s.observedAt);
            row.putInt("liveOwners", s.liveOwners);
            row.putString("outcomeSummary", s.outcomeSummary());
            rows.add(row);
        }
        result.putInt(SCHEMA, 1);
        result.putParcelableArrayList(ROWS, rows);
    }

    @SuppressWarnings("deprecation")
    public static String formatStatus(Bundle data) {
        if (data == null || data.getInt(SCHEMA, -1) != 1) return "能力状态：unknown（旧进程或未采集）";
        ArrayList<Bundle> rows = data.getParcelableArrayList(ROWS);
        if (rows == null) return "能力状态：unknown（未采集）";
        StringBuilder text = new StringBuilder("能力状态（最近一次观测的对象；owners/summary 为 60 秒内仍存活的各对象，不是视觉验收）：");
        for (int i = 0; i < Math.min(rows.size(), Feature.values().length); i++) {
            Bundle row = rows.get(i);
            if (row == null) continue;
            text.append('\n').append(row.getString("feature", "unknown"))
                    .append(" requested=").append(row.getString("requested", "UNKNOWN"))
                    .append(" supported=").append(row.getString("supported", "UNKNOWN"))
                    .append(" eligible=").append(row.getString("eligible", "UNKNOWN"))
                    .append(" path=").append(row.getString("selectedPath", "UNKNOWN"))
                    .append(" outcome=").append(row.getString("lastOutcome", "NOT_OBSERVED"))
                    .append(" reason=").append(row.getString("reason", "TARGET_NOT_OBSERVED"))
                    .append(" scope=").append(row.getString("evidenceScope", "NOT_OBSERVED"))
                    .append(" observedAt=").append(row.getLong("observedAt", -1L))
                    .append(" owners=").append(row.getInt("liveOwners", -1))
                    .append(" summary=").append(row.getString("outcomeSummary", "unknown"));
        }
        return text.toString();
    }

    private static final class FeatureState {
        Truth requested = Truth.UNKNOWN;
        final Entry[] slots = new Entry[OWNERS_PER_FEATURE];
        FeatureState() { for (int i = 0; i < slots.length; i++) slots[i] = new Entry(); }
        /** Same owner reuses its slot (no allocation); otherwise a free, collected or oldest slot. */
        Entry slotFor(Object owner) {
            Entry free = null, oldest = null;
            for (Entry e : slots) {
                Object current = e.owner.get();
                if (owner != null && current == owner) return e;
                if (current == null && free == null) free = e;
                if (oldest == null || e.observedAt < oldest.observedAt) oldest = e;
            }
            Entry chosen = free != null ? free : oldest;
            chosen.clear();
            return chosen;
        }
        void clearObservations() { for (Entry e : slots) e.clear(); }
        void clear() { clearObservations(); requested = Truth.UNKNOWN; }
    }

    private static final class Entry {
        Truth requested = Truth.UNKNOWN, supported = Truth.UNKNOWN, eligible = Truth.UNKNOWN;
        Path path = Path.UNKNOWN;
        Outcome outcome = Outcome.NOT_OBSERVED;
        Reason reason = Reason.TARGET_NOT_OBSERVED;
        Scope scope = Scope.NOT_OBSERVED;
        long observedAt = -1L;
        WeakReference<Object> owner = NO_OWNER;
        void clear() {
            supported = Truth.UNKNOWN; eligible = Truth.UNKNOWN; path = Path.UNKNOWN;
            outcome = Outcome.NOT_OBSERVED; reason = Reason.TARGET_NOT_OBSERVED;
            scope = Scope.NOT_OBSERVED; observedAt = -1L; owner = NO_OWNER;
        }
    }
    public static final class Snapshot {
        public final Feature feature;
        public final Truth requested, supported, eligible;
        public final Path path;
        public final Outcome outcome;
        public final Reason reason;
        public final Scope scope;
        public final long observedAt;
        /** Owners with a fresh observation of this feature; 0 when none are live. */
        public final int liveOwners;
        private final int[] outcomeCounts;
        Snapshot(Feature feature, Truth requested, Truth supported, Truth eligible, Path path,
                Outcome outcome, Reason reason, Scope scope, long observedAt, int liveOwners,
                int[] outcomeCounts) {
            this.feature = feature; this.requested = requested; this.supported = supported;
            this.eligible = eligible; this.path = path; this.outcome = outcome;
            this.reason = reason; this.scope = scope; this.observedAt = observedAt;
            this.liveOwners = liveOwners; this.outcomeCounts = outcomeCounts.clone();
        }
        public int count(Outcome outcome) { return outcomeCounts[outcome.ordinal()]; }
        /** Enum names and counts only, e.g. DISPATCHED:2,WAITING:1; "none" when empty. */
        public String outcomeSummary() {
            StringBuilder text = new StringBuilder();
            for (Outcome o : Outcome.values()) {
                int n = outcomeCounts[o.ordinal()];
                if (n == 0) continue;
                if (text.length() > 0) text.append(',');
                text.append(o.name()).append(':').append(n);
            }
            return text.length() == 0 ? "none" : text.toString();
        }
    }
}
