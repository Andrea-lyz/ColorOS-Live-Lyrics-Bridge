package io.github.andrealtb.lockscreenlyrics.render;

import java.util.WeakHashMap;
import java.util.HashMap;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/** Pairs a published model, native dataset commit and real adapter binds as one render owner. */
public final class LyricRenderTransactions<M> {
    public enum Status { READY, WAITING, UNSUPPORTED }
    public enum RowStatus { CURRENT, RETIRED, WAITING }

    private final WeakHashMap<Object, Surface> surfaces = new WeakHashMap<>();
    private final WeakHashMap<Object, Surface> adapters = new WeakHashMap<>();
    private final WeakHashMap<Object, RowRecord> rows = new WeakHashMap<>();
    private Publication<M> publication = new Publication<>(null, null);

    /** The host commits publication on its UI thread, between complete draw traversals. */
    public synchronized void publish(M model, OfficialLyricDataset expectedDataset) {
        publication = new Publication<>(model, expectedDataset);
    }

    public synchronized M currentModel() { return publication.model; }

    public synchronized OfficialLyricDataset currentDataset() { return publication.dataset; }

    public synchronized void registerAdapter(Object surface, Object adapter) {
        if (surface == null || adapter == null) return;
        Surface state = surfaces.get(surface);
        if (state == null) {
            state = new Surface();
            surfaces.put(surface, state);
        }
        adapters.put(adapter, state);
    }

    public synchronized Object surfaceForAdapter(Object adapter) {
        Surface state = adapters.get(adapter);
        if (state == null) return null;
        for (java.util.Map.Entry<Object, Surface> entry : surfaces.entrySet()) {
            if (entry.getValue() == state) return entry.getKey();
        }
        return null;
    }

    /** Observes committed adapter data, never the requested target of an asynchronous diff. */
    public synchronized boolean observeAdapterDataset(Object adapter, OfficialLyricDataset dataset) {
        Surface state = adapters.get(adapter);
        if (state == null || (state.epoch != null && !state.epoch.committed)) return false;
        if (state.epoch != null && Objects.equals(state.epoch.dataset, dataset)) return false;
        state.epoch = new Epoch(dataset);
        state.epoch.committed = true;
        return true;
    }

    public synchronized DatasetChange beginDataset(
            Object surface, Object adapter, OfficialLyricDataset dataset) {
        Surface state = surfaces.get(surface);
        if (state == null) {
            state = new Surface();
            surfaces.put(surface, state);
        }
        Epoch next = new Epoch(dataset);
        DatasetChange change = new DatasetChange(state, state.epoch, next);
        state.epoch = next;
        if (adapter != null) adapters.put(adapter, state);
        return change;
    }

    public synchronized void commitDataset(DatasetChange change, Object adapter) {
        if (change == null || change.surface.epoch != change.next) return;
        change.next.committed = true;
        if (adapter != null) adapters.put(adapter, change.surface);
    }

    public synchronized void abortDataset(DatasetChange change) {
        if (change != null && change.surface.epoch == change.next) {
            change.surface.epoch = change.previous;
        }
    }

    /** Captured before adapter.bindViewHolder; completion cannot adopt a newer dataset epoch. */
    public synchronized RowBinding beginRowBind(Object adapter) {
        return beginRowBind(adapter, 0);
    }

    public synchronized RowBinding beginRowBind(Object adapter, int position) {
        Surface state = adapters.get(adapter);
        if (state == null || state.epoch == null || position < 0) return null;
        RowBinding binding = new RowBinding(state, state.epoch, position, null);
        state.epoch.owners.put(position, binding);
        state.bindGeneration++;
        return binding;
    }

    public synchronized void completeRowBind(RowBinding binding, Object row) {
        if (binding != null && row != null && binding.surface.epoch == binding.epoch
                && binding.epoch.owners.get(binding.position) == binding) {
            rows.put(row, new RowRecord(binding));
        }
    }

    public synchronized Status surfaceStatus(Object surface, M model) {
        if (model == null || model != publication.model) return Status.WAITING;
        Surface state = surfaces.get(surface);
        if (state == null || state.epoch == null || !state.epoch.committed) return Status.WAITING;
        if (state.evaluatedPublication != publication || state.evaluatedEpoch != state.epoch) {
            state.evaluatedPublication = publication;
            state.evaluatedEpoch = state.epoch;
            state.status = publication.dataset == null || state.epoch.dataset == null
                    ? Status.UNSUPPORTED
                    : publication.dataset.matchesNative(state.epoch.dataset) ? Status.READY : Status.WAITING;
        }
        return state.status;
    }

    public synchronized Status rowStatus(Object surface, Object row, M model) {
        Status status = surfaceStatus(surface, model);
        if (status != Status.READY) return status;
        Surface state = surfaces.get(surface);
        RowRecord record = rows.get(row);
        RowBinding binding = record == null ? null : record.binding;
        return binding != null && record.eligible && binding.surface == state
                && binding.epoch == state.epoch && state.epoch.owners.get(binding.position) == binding
                ? Status.READY : Status.WAITING;
    }

    /** Carries unchanged DiffUtil rows only after the complete native dataset is authoritative. */
    public synchronized RowStatus observeRowPosition(Object surface, Object row, int position, M model) {
        if (surfaceStatus(surface, model) != Status.READY) return RowStatus.WAITING;
        Surface state = surfaces.get(surface);
        RowRecord record = rows.get(row);
        if (record == null || record.binding.surface != state) return RowStatus.WAITING;
        if (position < 0) {
            record.eligible = false;
            return RowStatus.RETIRED;
        }
        RowBinding old = record.binding;
        RowBinding owner = state.epoch.owners.get(position);
        if (old.epoch == state.epoch && old.position == position && owner == old) {
            record.eligible = true;
            return RowStatus.CURRENT;
        }
        if (owner != null && owner != old && owner.lineage != old.lineage) {
            record.eligible = false;
            return RowStatus.RETIRED;
        }
        if (old.epoch.dataset != null
                && old.epoch.dataset.sameRow(old.position, state.epoch.dataset, position)) {
            if (owner == null || owner == old) {
                owner = new RowBinding(state, state.epoch, position, old.lineage);
                state.epoch.owners.put(position, owner);
            }
            record.binding = owner;
            record.eligible = true;
            return RowStatus.CURRENT;
        }
        record.eligible = false;
        return RowStatus.WAITING;
    }

    public synchronized List<Object> rowsForSurface(Object surface) {
        Surface state = surfaces.get(surface);
        ArrayList<Object> result = new ArrayList<>();
        if (state == null) return result;
        for (java.util.Map.Entry<Object, RowRecord> entry : rows.entrySet()) {
            if (entry.getValue().binding.surface == state) result.add(entry.getKey());
        }
        return result;
    }

    /** True once per coherent publication/cohort, so stale hardware display lists are rebuilt. */
    public synchronized boolean commitSurfacePixels(Object surface, M model, boolean coherent) {
        Surface state = surfaces.get(surface);
        if (state == null) return false;
        if (!coherent || surfaceStatus(surface, model) != Status.READY) {
            state.visiblePublication = null;
            state.visibleEpoch = null;
            return false;
        }
        if (state.visiblePublication == publication && state.visibleEpoch == state.epoch
                && state.visibleBindGeneration == state.bindGeneration) return false;
        state.visiblePublication = publication;
        state.visibleEpoch = state.epoch;
        state.visibleBindGeneration = state.bindGeneration;
        return true;
    }

    public synchronized void clearBindings() {
        surfaces.clear();
        adapters.clear();
        rows.clear();
    }

    private static final class Publication<M> {
        final M model;
        final OfficialLyricDataset dataset;

        Publication(M model, OfficialLyricDataset dataset) {
            this.model = model;
            this.dataset = dataset;
        }
    }

    private static final class Surface {
        Epoch epoch;
        Object evaluatedPublication;
        Epoch evaluatedEpoch;
        Status status = Status.WAITING;
        Object visiblePublication;
        Epoch visibleEpoch;
        long bindGeneration;
        long visibleBindGeneration = -1L;
    }

    private static final class Epoch {
        final OfficialLyricDataset dataset;
        final HashMap<Integer, RowBinding> owners = new HashMap<>();
        boolean committed;

        Epoch(OfficialLyricDataset dataset) { this.dataset = dataset; }
    }

    public static final class DatasetChange {
        private final Surface surface;
        private final Epoch previous;
        private final Epoch next;

        private DatasetChange(Surface surface, Epoch previous, Epoch next) {
            this.surface = surface;
            this.previous = previous;
            this.next = next;
        }
    }

    public static final class RowBinding {
        private final Surface surface;
        private final Epoch epoch;
        private final int position;
        private final Object lineage;

        private RowBinding(Surface surface, Epoch epoch, int position, Object lineage) {
            this.surface = surface;
            this.epoch = epoch;
            this.position = position;
            this.lineage = lineage == null ? new Object() : lineage;
        }
    }

    private static final class RowRecord {
        RowBinding binding;
        boolean eligible = true;
        RowRecord(RowBinding binding) { this.binding = binding; }
    }
}
