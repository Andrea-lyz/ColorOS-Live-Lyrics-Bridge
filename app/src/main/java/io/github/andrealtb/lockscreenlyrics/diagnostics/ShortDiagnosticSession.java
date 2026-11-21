package io.github.andrealtb.lockscreenlyrics.diagnostics;

import android.os.Bundle;
import android.os.SystemClock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit opt-in, bounded metadata window. Never toggles logging preferences or business paths. */
public final class ShortDiagnosticSession {
    private static final ShortDiagnosticSession GLOBAL = new ShortDiagnosticSession(SystemClock::elapsedRealtime);
    private final LongSupplier clock;
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private final EnumMap<CompatibilityStatus.Feature, Event> last = new EnumMap<>(CompatibilityStatus.Feature.class);
    private String id = "unknown";
    private long start = -1L, deadline, stoppedAt = -1L;
    private volatile long recordingDeadline;
    private int truncated, suppressed, marks;
    public ShortDiagnosticSession(LongSupplier clock) { this.clock = clock; }
    public static ShortDiagnosticSession global() { return GLOBAL; }
    public boolean active() {
        long end = recordingDeadline;
        if (end <= 0L) return false;
        if (clock.getAsLong() <= end) return true;
        synchronized (this) { if (recordingDeadline == end) recordingDeadline = 0L; }
        return false;
    }
    public synchronized String start() {
        if (active()) return id;
        start = clock.getAsLong(); deadline = start + 60_000L; stoppedAt = -1L;
        id = UUID.randomUUID().toString(); truncated = 0; suppressed = 0; marks = 0;
        events.clear(); last.clear(); recordingDeadline = deadline; return id;
    }
    public synchronized String stop(String expected) {
        if (!id.equals(expected) || start < 0) return "STALE_OR_MISSING_SESSION";
        if (stoppedAt < 0) stoppedAt = Math.min(clock.getAsLong(), deadline);
        recordingDeadline = 0L; return "STOPPED";
    }
    public synchronized String mark(String expected) {
        if (!id.equals(expected) || !active()) return "STALE_OR_EXPIRED_SESSION";
        if (marks >= 16) { suppressed++; return "MARK_BUDGET"; }
        Event event = new Event(); event.at = clock.getAsLong(); event.marker = ++marks; append(event); return "MARKED";
    }
    /** Primitive/enum arguments avoid allocating a per-frame snapshot when recording is off. */
    public synchronized void record(CompatibilityStatus.Feature feature, CompatibilityStatus.Truth requested,
            CompatibilityStatus.Truth supported, CompatibilityStatus.Truth eligible,
            CompatibilityStatus.Path path, CompatibilityStatus.Outcome outcome,
            CompatibilityStatus.Reason reason, CompatibilityStatus.Scope scope, long at) {
        if (!active()) return;
        Event previous = last.get(feature);
        if (previous != null) {
            if (previous.requested == requested && previous.supported == supported && previous.eligible == eligible
                    && previous.path == path && previous.outcome == outcome && previous.reason == reason && previous.scope == scope) {
                previous.repeated++; return;
            }
            if (at - previous.at < 500L) { suppressed++; return; }
        }
        Event event = new Event(); event.at = at; event.feature = feature;
        event.requested = requested; event.supported = supported; event.eligible = eligible;
        event.path = path; event.outcome = outcome; event.reason = reason; event.scope = scope;
        append(event); last.put(feature, event);
    }
    private void append(Event event) {
        if (events.size() == 128) { events.removeFirst(); truncated++; }
        events.addLast(event);
    }
    /** The artwork runtime registers itself; the entry class does not grow for it. */
    private volatile java.util.function.Consumer<Bundle> artworkStatus;
    public void artworkStatus(java.util.function.Consumer<Bundle> source) { artworkStatus = source; }
    public synchronized int retainedEvents() { return events.size(); }
    public synchronized int droppedEvents() { return truncated; }
    public synchronized String sessionId() { return id; }
    public synchronized String summaryJson(Bundle runtime) {
        try {
            JSONObject root = new JSONObject(); root.put("schema", "cll-bridge-diagnostics-v1"); root.put("sessionId", id);
            root.put("active", active()); root.put("startElapsed", start);
            root.put("endElapsed", stoppedAt >= 0 ? stoppedAt : deadline > 0 ? Math.min(clock.getAsLong(), deadline) : -1L);
            root.put("truncatedEvents", truncated); root.put("suppressedTransitions", suppressed);
            root.put("coverage", start < 0 ? "NO_SESSION" : truncated > 0 || suppressed > 0 ? "SAMPLED_PARTIAL" : "BOUNDED_OBSERVATIONS");
            root.put("evidenceScope", "LAST_OBSERVED_ITEMS_NOT_VISUAL_ACCEPTANCE");
            JSONObject identity = new JSONObject();
            for (String key : new String[]{BuildIdentity.RUNTIME_BUILD_ID, BuildIdentity.RUNTIME_PROCESS,
                    BuildIdentity.RUNTIME_EPOCH, BuildIdentity.RUNTIME_FRAMEWORK, "cll_observed_aod_low_frame_rate",
                    AodModeObservation.CURRENT})
                identity.put(key, runtime.getString(key, "unknown"));
            for (String key : new String[]{BuildIdentity.RUNTIME_PROPERTIES, BuildIdentity.UI_CONFIG_REVISION}) identity.put(key, runtime.getLong(key, -1L));
            identity.put(BuildIdentity.RUNTIME_API, runtime.getInt(BuildIdentity.RUNTIME_API, -1));
            identity.put(AodModeObservation.TRANSITIONS, runtime.getInt(AodModeObservation.TRANSITIONS, -1));
            identity.put(AodModeObservation.CHANGED_AT, runtime.getLong(AodModeObservation.CHANGED_AT, -1L));
            root.put("runtime", identity);
            JSONObject config = new JSONObject();
            for (String key : new String[]{"lineProgress", "translationProgress", "charLift", "coverGlow"})
                config.put(key, runtime.containsKey("diag_config_" + key) ? runtime.getBoolean("diag_config_" + key) : JSONObject.NULL);
            config.put("debugRevision", runtime.getLong(BridgeDebugConfig.KEY_REVISION, -1L));
            config.put("debugEnabled", runtime.getBoolean(BridgeDebugConfig.KEY_MASTER, false));
            root.put("effectiveConfig", config);
            java.util.function.Consumer<Bundle> artworkSource = artworkStatus;
            if (artworkSource != null) artworkSource.accept(runtime);
            JSONObject artwork = new JSONObject();
            artwork.put("state", runtime.getString("cll_artwork_state", "unknown"));
            artwork.put("stateAgeMs", runtime.getLong("cll_artwork_state_age_ms", -1L));
            artwork.put("source", runtime.getString("cll_artwork_source", "unknown"));
            root.put("artwork", artwork);
            root.put("capabilities", capabilities(runtime));
            root.put("loadedComponents", loaded(runtime));
            JSONArray recorded = new JSONArray();
            for (Event event : events) {
                JSONObject row = new JSONObject(); row.put("elapsed", event.at);
                if (event.marker > 0) { row.put("event", "USER_MARK"); row.put("marker", event.marker); }
                else {
                    row.put("event", "FEATURE_OBSERVATION"); row.put("feature", event.feature.name());
                    row.put("requested", event.requested.name()); row.put("supported", event.supported.name());
                    row.put("eligible", event.eligible.name()); row.put("selectedPath", event.path.name());
                    row.put("lastOutcome", event.outcome.name()); row.put("reason", event.reason.name());
                    row.put("evidenceScope", event.scope.name()); row.put("repeated", event.repeated);
                }
                recorded.put(row);
            }
            root.put("events", recorded); return root.toString(2);
        } catch (Exception unavailable) {
            return "{\"schema\":\"cll-bridge-diagnostics-v1\",\"coverage\":\"SERIALIZATION_FAILED\"}";
        }
    }
    @SuppressWarnings("deprecation")
    private static JSONArray capabilities(Bundle runtime) throws org.json.JSONException {
        JSONArray result = new JSONArray(); ArrayList<Bundle> rows = runtime.getParcelableArrayList(CompatibilityStatus.ROWS);
        if (rows == null) return result;
        for (int i = 0; i < Math.min(rows.size(), CompatibilityStatus.Feature.values().length); i++) {
            Bundle row = rows.get(i); JSONObject json = new JSONObject();
            for (String key : new String[]{"feature", "requested", "supported", "eligible", "selectedPath", "lastOutcome", "reason", "evidenceScope"}) json.put(key, row.getString(key, "unknown"));
            json.put("observedAt", row.getLong("observedAt", -1L));
            json.put("liveOwners", row.getInt("liveOwners", -1)); result.put(json);
        }
        return result;
    }
    @SuppressWarnings("deprecation")
    private static JSONArray loaded(Bundle runtime) throws org.json.JSONException {
        JSONArray result = new JSONArray(); ArrayList<Bundle> rows = runtime.getParcelableArrayList(LoadedComponentFingerprints.KEY);
        if (rows == null) return result;
        for (int i = 0; i < Math.min(rows.size(), 2); i++) {
            Bundle row = rows.get(i); JSONObject json = new JSONObject();
            for (String key : new String[]{"kind", "state", "contentSetSha256", "evidenceScope"}) json.put(key, row.getString(key, "unknown"));
            for (String key : new String[]{"loaderEpoch", "loaderObservedAt", "fingerprintedAt"}) json.put(key, row.getLong(key, -1L));
            json.put("apkCount", row.getInt("apkCount", 0)); result.put(json);
        }
        return result;
    }
    private static final class Event {
        long at, repeated = 1L; int marker;
        CompatibilityStatus.Feature feature;
        CompatibilityStatus.Truth requested, supported, eligible;
        CompatibilityStatus.Path path;
        CompatibilityStatus.Outcome outcome;
        CompatibilityStatus.Reason reason;
        CompatibilityStatus.Scope scope;
    }
}
