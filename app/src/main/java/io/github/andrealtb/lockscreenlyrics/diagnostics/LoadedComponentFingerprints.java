package io.github.andrealtb.lockscreenlyrics.diagnostics;

import android.os.Bundle;
import android.os.SystemClock;
import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Loader identity observed in existing callbacks. Full backing-file reads require explicit collection. */
public final class LoadedComponentFingerprints {
    private static final LoadedComponentFingerprints GLOBAL = new LoadedComponentFingerprints(
            SystemClock::elapsedRealtime, LoadedComponentFingerprints::discover,
            new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2), task -> {
                Thread thread = new Thread(task, "CLL-component-fingerprints"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy()));
    public static final String KEY = "cll_loaded_component_fingerprints";
    private final LongSupplier clock;
    private final Function<ClassLoader, List<String>> discoverer;
    private final Executor executor;
    private final Slot[] slots = { new Slot("systemui"), new Slot("plugin") };
    public LoadedComponentFingerprints(LongSupplier clock, Function<ClassLoader, List<String>> discoverer, Executor executor) {
        this.clock = clock; this.discoverer = discoverer; this.executor = executor;
    }
    public static LoadedComponentFingerprints global() { return GLOBAL; }
    private Slot slot(String kind) { return "systemui".equals(kind) ? slots[0] : "plugin".equals(kind) ? slots[1] : null; }
    public synchronized void observed(String kind, ClassLoader loader) {
        Slot slot = slot(kind);
        if (slot == null || loader == null || slot.loader.get() == loader) return;
        slot.loader = new WeakReference<>(loader); slot.epoch++; slot.observedAt = clock.getAsLong();
        slot.measuredAt = -1; slot.fingerprint = "unknown"; slot.count = 0; slot.state = "OBSERVED_NOT_HASHED";
    }
    /** User-triggered only; status/snapshot calls never enqueue work or resolve anything. */
    public synchronized void collect() {
        for (Slot slot : slots) {
            ClassLoader loader = slot.loader.get();
            if (loader == null) { slot.state = "NOT_OBSERVED"; continue; }
            if (slot.state.equals("PENDING") || slot.state.equals("READABLE")) continue;
            long epoch = slot.epoch;
            WeakReference<ClassLoader> reference = slot.loader;
            slot.state = "PENDING";
            try { executor.execute(() -> measure(slot.kind, epoch, reference)); }
            catch (RuntimeException rejected) { slot.state = "NOT_COLLECTED"; }
        }
    }
    private void measure(String kind, long epoch, WeakReference<ClassLoader> reference) {
        String fingerprint = "unknown", outcome = "UNKNOWN"; int count = 0;
        try {
            ClassLoader loader = reference.get();
            if (loader != null) {
                List<String> paths = new ArrayList<>(new LinkedHashSet<>(discoverer.apply(loader)));
                if (paths.size() <= 8 && !paths.isEmpty()) {
                    List<String> hashes = new ArrayList<>();
                    for (String path : paths) {
                        File file = new File(path); long length = file.length(), modified = file.lastModified();
                        String hash = BuildIdentity.sha256(file);
                        if (hash.equals("unknown") || length != file.length() || modified != file.lastModified()) {
                            hashes.clear(); outcome = "UNREADABLE_OR_CHANGED"; break;
                        }
                        hashes.add(hash);
                    }
                    if (hashes.size() == paths.size()) {
                        Collections.sort(hashes);
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        for (String hash : hashes) digest.update((hash + "\n").getBytes(StandardCharsets.US_ASCII));
                        StringBuilder hex = new StringBuilder();
                        for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                        fingerprint = hex.toString(); count = paths.size(); outcome = "READABLE";
                    }
                }
            }
        } catch (Exception ignored) { }
        synchronized (this) {
            Slot slot = slot(kind);
            if (slot == null || slot.epoch != epoch || slot.loader != reference) return;
            slot.state = outcome; slot.fingerprint = fingerprint; slot.count = count; slot.measuredAt = clock.getAsLong();
        }
    }
    public synchronized void putStatus(Bundle result) {
        ArrayList<Bundle> rows = new ArrayList<>();
        for (Slot slot : slots) {
            Bundle row = new Bundle(); row.putString("kind", slot.kind); row.putLong("loaderEpoch", slot.epoch);
            row.putLong("loaderObservedAt", slot.observedAt); row.putLong("fingerprintedAt", slot.measuredAt);
            boolean live = slot.loader.get() != null;
            row.putString("state", live ? slot.state : "NOT_OBSERVED");
            row.putString("contentSetSha256", live ? slot.fingerprint : "unknown"); row.putInt("apkCount", slot.count);
            row.putString("evidenceScope", "OBSERVED_LOADER_BACKING_FILES_NOT_INSTRUCTION_MEMORY"); rows.add(row);
        }
        result.putParcelableArrayList(KEY, rows);
    }
    static List<String> discover(ClassLoader observed) {
        try {
            // Scan only the observed loader's own dex path. OPlusPluginClassLoader is itself a
            // PathClassLoader over the plugin APK; its mBase is the SystemUI base loader used for
            // shared packages (ColorOS 16 constructor and ColorOS 17 PluginInstance$Factory), so
            // following mBase would fingerprint SystemUI instead of the plugin. Parents are never
            // scanned either.
            Object pathList = field(observed, "pathList"); Object elements = field(pathList, "dexElements");
            int size = Math.min(java.lang.reflect.Array.getLength(elements), 256);
            LinkedHashSet<String> paths = new LinkedHashSet<>();
            for (int i = 0; i < size; i++) {
                Object element = java.lang.reflect.Array.get(elements, i);
                for (String name : new String[]{"path", "zip"}) {
                    try { Object value = field(element, name); if (value instanceof File && ((File) value).getName().endsWith(".apk")) paths.add(((File) value).getAbsolutePath()); }
                    catch (ReflectiveOperationException ignored) { }
                }
                try {
                    Object dex = field(element, "dexFile");
                    if (dex != null) {
                        Object value = dex.getClass().getMethod("getName").invoke(dex);
                        if (value instanceof String && ((String) value).endsWith(".apk")) paths.add((String) value);
                    }
                } catch (ReflectiveOperationException ignored) { }
            }
            return new ArrayList<>(paths);
        } catch (ReflectiveOperationException | RuntimeException ignored) { return Collections.emptyList(); }
    }
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Class<?> type = owner.getClass();
        for (int i = 0; type != null && i < 12; i++, type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
            catch (NoSuchFieldException missing) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static final class Slot {
        final String kind;
        WeakReference<ClassLoader> loader = new WeakReference<>(null);
        long epoch, observedAt = -1, measuredAt = -1;
        String state = "NOT_OBSERVED", fingerprint = "unknown";
        int count;
        Slot(String kind) { this.kind = kind; }
    }
}
