package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.content.Context;
import android.media.session.MediaSession;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.graphics.Canvas;
import android.graphics.drawable.TransitionDrawable;
import android.view.ViewTreeObserver;

import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugArea;
import io.github.andrealtb.lockscreenlyrics.diagnostics.StructuredBridgeLog;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Independent bootstrap and passive host/session binding. No FD requests or display takeover yet. */
public final class DynamicArtworkRuntime {
    @FunctionalInterface public interface AfterCall {
        void observe(Object receiver, Object[] arguments, Object result) throws Exception;
        default void before(Object receiver, Object[] arguments) throws Exception {}
    }
    @FunctionalInterface public interface HookInstaller {
        Runnable install(Executable target, String id, AfterCall after) throws Exception;
    }
    private final HookInstaller hooks;
    private final ArtworkTrace trace = new ArtworkTrace("runtime");
    private final Handler main;
    private final ArtworkSessionRegistry registry;
    private final List<Runnable> sourceHandles = new ArrayList<>();
    private final List<Runnable> pluginHandles = new ArrayList<>();
    private final Map<Method, Runnable> flowHandles = new HashMap<>();
    private final Map<Object, List<WeakReference<Host>>> flowOwners = new WeakHashMap<>();
    private final List<Host> hosts = new ArrayList<>();
    private final ThreadLocal<Boolean> reading = ThreadLocal.withInitial(() -> false);
    private final AtomicLong sourceSequence = new AtomicLong();
    private final AtomicLong pluginEpoch = new AtomicLong();
    private final AtomicLong surfaceEpoch = new AtomicLong();
    private record SourceUpdate(long sequence, List<ArtworkSessionAssociation.Entry<MediaSession.Token>> entries) {}
    private final AtomicReference<SourceUpdate> pendingSource = new AtomicReference<>();
    private final AtomicBoolean sourcePosted = new AtomicBoolean();
    private volatile WeakReference<Object> source = new WeakReference<>(null);
    private volatile ArtworkSeedlingAccess seedling;
    private volatile WeakReference<ClassLoader> pluginLoader = new WeakReference<>(null);
    /** A playback is running, from the saved settings or a debug test. */
    private volatile boolean displayEnabled;
    /** Saved settings as SystemUI last received them; a debug test overrides them while enabled. */
    private ArtworkDisplaySettings settings = ArtworkDisplaySettings.defaults();
    private ArtworkPlaybackConfig testConfig;
    private ArtworkPlaybackConfig active = ArtworkPlaybackConfig.DISABLED;
    private String activeSource = "off";
    private final String settingsPermission;
    private boolean settingsStarted;
    private int settingsAttempts;
    private final java.util.function.Supplier<Context> context;
    private ArtworkDrawableTransition transition;
    private ArtworkImmersivePlayback playback;
    private volatile boolean shapeObservationReady;
    private volatile ArtworkCardEffectAccess cardEffects;
    private final AtomicBoolean displayPosted = new AtomicBoolean();
    private boolean systemInstalled;

    public DynamicArtworkRuntime(HookInstaller hooks, Handler main, java.util.function.Supplier<Context> context,
            String settingsPermission) {
        this.hooks = hooks;
        this.main = main;
        this.context = context;
        this.settingsPermission = settingsPermission;
        registry = new ArtworkSessionRegistry(main, context);
        main.post(() -> registry.setSourceReader(this::readCurrentSource));
    }

    /** Debug fixture test: overrides the saved settings until it is switched off. */
    public void applyImmersiveTest(ArtworkPlaybackConfig config) {
        main.post(() -> {
            testConfig = config.enabled() ? config : null;
            activate();
        });
    }

    /** Starts or changes the playback for the effective config; presentation-only changes keep the decoder. */
    private void activate() {
        ArtworkPlaybackConfig next = testConfig != null ? testConfig : settings.playback();
        String source = testConfig != null ? "debug_test" : next.enabled() ? "settings" : "off";
        if (next.equals(active) && source.equals(activeSource)) return;
        if (playback != null && next.sameSession(active)) {
            active = next;
            activeSource = source;
            playback.setKeepAwake(next.keepAwake());
            traceConfig("decoderKept=true");
            updateDisplay();
            return;
        }
        if (playback != null) { playback.close(); playback = null; }
        displayEnabled = false;
        active = next;
        activeSource = source;
        // Transition knowledge outlives a playback: clearing it forgets starts the native cover will not repeat.
        try {
            if (next.enabled()) {
                Context application = context.get();
                if (application == null) throw new IllegalStateException("context_missing");
                if (transition == null) installTransitionObservation();
                playback = new ArtworkImmersivePlayback(application, transition, next, this::updateDisplay);
                displayEnabled = true;
            }
            traceConfig("decoderKept=false");
        } catch (Exception error) {
            active = ArtworkPlaybackConfig.DISABLED;
            activeSource = "off";
            trace.state("ARTWORK_TEST_FALLBACK", () -> "reason=test_setup_failed errorType=" + ArtworkTrace.errorType(error));
        }
        refreshHosts();
        if (observing()) refreshSource();
        else suspendObservations();
    }

    private void traceConfig(String detail) {
        ArtworkPlaybackConfig config = active;
        String source = activeSource;
        trace.state("ARTWORK_DISPLAY_CONFIG", () -> "source=" + source + " enabled=" + displayEnabled
                + " revision=" + config.revision() + " mode=" + (config.localFixture() ? "local_fixture" : "live_query")
                + " card=" + config.cardEnabled() + " largeCover=" + config.immersiveEnabled()
                + " keepAwake=" + config.keepAwake() + " " + detail);
    }

    /**
     * The saved settings channel belongs to the artwork runtime itself, so the display neither waits
     * for nor depends on the lyric bootstrap. Retries until SystemUI has an application context.
     * Exported but protected by the Bridge signature permission, like the lyric settings receiver.
     */
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void startSettings() {
        if (settingsStarted) return;
        Context application = context.get();
        if (application == null) {
            if (++settingsAttempts <= 60) main.postDelayed(this::startSettings, 1_000);
            return;
        }
        settingsStarted = true;
        try {
            settings = ArtworkDisplaySettings.load(application.getSharedPreferences(ArtworkDisplaySettings.PREFERENCES, Context.MODE_PRIVATE));
            log("ARTWORK_SETTINGS_LOADED", "revision=" + settings.revision() + " active=" + settings.active());
        } catch (RuntimeException error) {
            log("ARTWORK_SETTINGS_LOADED", "failed=true errorType=" + ArtworkTrace.errorType(error));
        }
        activate();
        android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context receiverContext, android.content.Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                try {
                    if (ArtworkDisplaySettings.ACTION_CHANGED.equals(action)) receiveSettings(application, intent);
                    else if (ArtworkDisplaySettings.ACTION_REQUEST_STATUS.equals(action)) replyStatus(intent);
                    else if (ArtworkPlaybackConfig.ACTION_TEST.equals(action)) applyImmersiveTest(ArtworkPlaybackConfig.readTest(intent));
                } catch (RuntimeException error) {
                    ArtworkTrace.observerFailure("artwork-settings", error);
                }
            }
        };
        android.content.IntentFilter filter = new android.content.IntentFilter(ArtworkDisplaySettings.ACTION_CHANGED);
        filter.addAction(ArtworkDisplaySettings.ACTION_REQUEST_STATUS);
        filter.addAction(ArtworkPlaybackConfig.ACTION_TEST);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                application.registerReceiver(receiver, filter, settingsPermission, main, Context.RECEIVER_EXPORTED);
            } else {
                application.registerReceiver(receiver, filter, settingsPermission, main);
            }
        } catch (RuntimeException error) {
            log("ARTWORK_SETTINGS_CHANNEL", "registered=false errorType=" + ArtworkTrace.errorType(error));
        }
    }

    private void receiveSettings(Context application, android.content.Intent intent) {
        ArtworkDisplaySettings next = ArtworkDisplaySettings.fromIntent(intent);
        try { next.persist(application.getSharedPreferences(ArtworkDisplaySettings.PREFERENCES, Context.MODE_PRIVATE)); }
        catch (RuntimeException error) { log("ARTWORK_SETTINGS_SAVED", "failed=true errorType=" + ArtworkTrace.errorType(error)); }
        settings = next;
        activate();
    }

    @SuppressWarnings("deprecation")
    private void replyStatus(android.content.Intent intent) {
        android.os.ResultReceiver receiver = intent.getParcelableExtra(ArtworkDisplaySettings.EXTRA_RESULT_RECEIVER);
        if (receiver == null) return;
        android.os.Bundle result = new android.os.Bundle();
        result.putLong(ArtworkDisplaySettings.STATUS_REVISION, settings.revision());
        result.putString(ArtworkDisplaySettings.STATUS_SOURCE, activeSource);
        result.putBoolean(ArtworkDisplaySettings.STATUS_ACTIVE, displayEnabled);
        result.putBoolean(ArtworkDisplaySettings.STATUS_HOOKS, systemInstalled);
        result.putString(ArtworkDisplaySettings.STATUS_STATE, playback == null ? "off" : playback.status());
        result.putLong(ArtworkDisplaySettings.STATUS_AGE_MS, playback == null ? -1 : playback.statusAgeMs());
        receiver.send(0, result);
    }

    private void installTransitionObservation() throws Exception {
        ArtworkDrawableTransition reader = new ArtworkDrawableTransition();
        List<Runnable> installed = new ArrayList<>();
        try {
            for (Method method : new Method[]{TransitionDrawable.class.getDeclaredMethod("draw", Canvas.class),
                    android.graphics.drawable.Drawable.class.getDeclaredMethod("invalidateSelf"),
                    TransitionDrawable.class.getDeclaredMethod("startTransition", int.class),
                    TransitionDrawable.class.getDeclaredMethod("reverseTransition", int.class),
                    TransitionDrawable.class.getDeclaredMethod("resetTransition")}) {
                installed.add(hooks.install(method, "artwork-transition-" + method.getName(), new AfterCall() {
                    private boolean owned(Object receiver) {
                        for (Host host : hosts) {
                            ImageView image = host.image.get();
                            if (host.attached && host.surface == ArtworkPlaybackPolicy.Surface.IMMERSIVE
                                    && image != null && image.getDrawable() == receiver) return true;
                        }
                        return false;
                    }
                    @Override public void before(Object receiver, Object[] args) {
                        if (!displayEnabled || android.os.Looper.myLooper() != main.getLooper()) return;
                        if (method.getName().equals("draw") && owned(receiver)) {
                            for (Host host : hosts) {
                                ImageView image = host.image.get();
                                if (host.attached && host.surface == ArtworkPlaybackPolicy.Surface.IMMERSIVE
                                        && image != null && image.getDrawable() == receiver) {
                                    android.graphics.drawable.Drawable previous = host.trackedDrawable.get();
                                    if (previous != receiver) {
                                        if (previous != null && !owned(previous)) reader.release(previous);
                                        host.trackedDrawable = new WeakReference<>((android.graphics.drawable.Drawable) receiver);
                                    }
                                }
                            }
                            reader.beginDraw((TransitionDrawable) receiver);
                        }
                    }
                    @Override public void observe(Object receiver, Object[] args, Object result) {
                        if (android.os.Looper.myLooper() != main.getLooper()) return;
                        String stage = method.getName();
                        // Starts and resets are rare and recorded even while the display is off. Device log
                        // 033854: a song changed while both surfaces were off, its crossfade start went
                        // unrecorded, and the large cover then stayed static for that drawable for good.
                        if (!displayEnabled && (stage.equals("draw") || stage.equals("invalidateSelf"))) return;
                        if (stage.equals("invalidateSelf")) {
                            reader.invalidate((android.graphics.drawable.Drawable) receiver);
                            return;
                        }
                        if (stage.equals("draw")) {
                            // Always finish a frame we began, even if the native owner changed during draw.
                            reader.endDraw((TransitionDrawable) receiver);
                            if (!owned(receiver)) return;
                            if (displayPosted.compareAndSet(false, true)) main.post(() -> { displayPosted.set(false); updateDisplay(); });
                        } else {
                            // Starts can precede section registration or attach. This cache is weak and bounded;
                            // only later exact image/draw ownership can grant completion.
                            if (receiver.getClass() != TransitionDrawable.class) return;
                            if (stage.equals("startTransition")) reader.start((TransitionDrawable) receiver);
                            else reader.reverseOrReset((TransitionDrawable) receiver);
                            if (owned(receiver)) updateDisplay();
                        }
                        trace.state("ARTWORK_TEST_TRANSITION", () -> "type=drawable_crossfade stage=" + stage
                                + " ownerAttached=" + owned(receiver) + " "
                                + reader.diagnostic((TransitionDrawable) receiver));
                    }
                }));
            }
            sourceHandles.addAll(installed);
            transition = reader;
        } catch (Exception error) {
            for (Runnable handle : installed) safeUnhook(handle);
            throw error;
        }
    }

    private void updateDisplay() {
        if (playback == null) return;
        List<ArtworkImmersivePlayback.Candidate> candidates = new ArrayList<>();
        for (Host host : hosts) {
            ImageView image = host.image.get();
            boolean surfaceEnabled = host.surface == ArtworkPlaybackPolicy.Surface.IMMERSIVE
                    ? active.immersiveEnabled() : active.cardEnabled();
            if (surfaceEnabled && host.attached && host.epoch == pluginEpoch.get() && image != null && !host.model.splitModel
                    && host.displayBinding != null && (host.surface == ArtworkPlaybackPolicy.Surface.IMMERSIVE
                        ? shapeObservationReady : cardEffects != null)) {
                candidates.add(new ArtworkImmersivePlayback.Candidate(image, host.displayBinding, host.surface, cardEffects));
            }
        }
        trace.state("ARTWORK_TEST_HOST_CHECK", () -> "shapeObservationReady=" + shapeObservationReady
                + " cardProfileReady=" + (cardEffects != null)
                + " candidateCount=" + candidates.size() + " mode=card_and_single_slot_immersive");
        playback.observe(candidates);
    }

    public void onDiagnosticsChanged() {
        main.post(() -> {
            trace.state("ARTWORK_RUNTIME_STATE", () -> "observing=" + observing() + " displaySource=" + activeSource
                    + " sourceInstalled=" + systemInstalled + " sourceAlive=" + (source.get() != null)
                    + " pluginEpoch=" + pluginEpoch.get() + " hostCount=" + hosts.size() + " flowHookCount=" + flowHandles.size());
            if (!observing()) { pendingSource.set(null); suspendObservations(); }
            else { refreshSource(); refreshHosts(); }
        });
    }

    private boolean observing() {
        // With product enablement off, MEDIA debug permits read-only binding diagnostics only.
        return displayEnabled || StructuredBridgeLog.isAreaEnabled(BridgeDebugArea.MEDIA);
    }

    public synchronized void initializeSystemUi(ClassLoader loader) {
        main.post(this::startSettings);
        if (systemInstalled) return;
        List<Runnable> installed = new ArrayList<>();
        try {
            ArtworkSeedlingAccess access = new ArtworkSeedlingAccess(loader);
            installed.add(hooks.install(access.sortedEntries, "artwork-source-sorted", (receiver, args, result) -> {
                source = new WeakReference<>(receiver);
                if (!reading.get() && observing()) captureSource(access, result);
            }));
            installed.add(hooks.install(access.release, "artwork-source-release", (receiver, args, result) -> {
                if (source.get() != receiver) return;
                source = new WeakReference<>(null);
                trace.state("ARTWORK_SOURCE_RELEASED", () -> "reason=vendor_release");
                long sequence = sourceSequence.incrementAndGet();
                main.post(() -> registry.updateSource(sequence, List.of()));
            }));
            for (Method update : new Method[]{access.loaded, access.removed}) {
                installed.add(hooks.install(update, "artwork-source-" + update.getName(), (receiver, args, result) -> {
                    source = new WeakReference<>(receiver);
                    if (!observing()) return;
                    boolean previous = reading.get();
                    reading.set(true);
                    try { captureSource(access, access.sortedEntries.invoke(receiver)); }
                    finally { reading.set(previous); }
                }));
            }
            seedling = access;
            sourceHandles.addAll(installed);
            systemInstalled = true;
            log("ARTWORK_CAPABILITY_RESOLVED", "source=pre_ums_seedling");
        } catch (Exception error) {
            for (Runnable handle : installed) safeUnhook(handle);
            log("ARTWORK_CAPABILITY_UNSUPPORTED", "source=unavailable errorType=" + ArtworkTrace.errorType(error));
        }
    }

    public synchronized void onPluginClassLoader(ClassLoader loader) {
        if (pluginLoader.get() == loader) return;
        pluginLoader = new WeakReference<>(loader);
        long epoch = pluginEpoch.incrementAndGet();
        for (Runnable handle : pluginHandles) safeUnhook(handle);
        pluginHandles.clear();
        main.post(this::clearPlugin);
        try {
            // Install before the ready-loader callback returns: asynchronous scanning can miss
            // every initial section constructor. This is one local bootstrap scan, never remote IPC.
            ArtworkCompatibilityResolver.Targets targets = ArtworkCompatibilityResolver.resolve(loader);
            installPlugin(epoch, targets);
        } catch (Exception | LinkageError error) {
            log("ARTWORK_CAPABILITY_UNSUPPORTED", "model=unavailable errorType=" + ArtworkTrace.errorType(error));
        }
    }

    private void installPlugin(long epoch, ArtworkCompatibilityResolver.Targets targets) {
        if (epoch != pluginEpoch.get()) return;
        shapeObservationReady = false;
        cardEffects = null;
        if (!targets.model().splitModel && android.os.Build.VERSION.SDK_INT >= 33) installCardObservation();
        installSurface(epoch, targets.model(), targets.card());
        if (targets.immersive() != null) installSurface(epoch, targets.model(), targets.immersive());
        if (!targets.model().splitModel) installShapeObservation();
        log("ARTWORK_CAPABILITY_RESOLVED", "model=" + (targets.model().splitModel ? "info_progress" : "flat")
                + " immersiveResolved=" + (targets.immersive() != null) + " display=disabled");
    }

    private void installShapeObservation() {
        List<Runnable> installed = new ArrayList<>();
        try {
            Class<?> material = pluginLoader.get().loadClass("com.google.android.material.imageview.ShapeableImageView");
            for (Method method : material.getDeclaredMethods()) {
                if (!method.getName().equals("setShapeAppearanceModel") && !method.getName().equals("setStrokeWidth")
                        && !method.getName().equals("setStrokeColor")) continue;
                installed.add(hooks.install(method, "artwork-material-" + method.getName(), (receiver, args, result) -> {
                    if (playback != null && receiver instanceof ImageView image && android.os.Looper.myLooper() == main.getLooper()) {
                        playback.invalidate(image);
                    }
                }));
            }
            if (installed.size() != 3) throw new NoSuchMethodException("shape_observer_contract");
            pluginHandles.addAll(installed);
            shapeObservationReady = true;
        } catch (Exception error) {
            for (Runnable handle : installed) safeUnhook(handle);
            log("ARTWORK_CAPABILITY_UNSUPPORTED", "shapeObservation=false errorType=" + ArtworkTrace.errorType(error));
        }
    }

    @androidx.annotation.RequiresApi(33)
    private void installCardObservation() {
        List<Runnable> installed = new ArrayList<>();
        try {
            ArtworkCardEffectAccess access = new ArtworkCardEffectAccess(pluginLoader.get());
            for (Method method : new Method[]{access.bitmapSetter, access.angleSetter}) {
                installed.add(hooks.install(method, "artwork-card-effect-" + method.getName(), new AfterCall() {
                    @Override public void before(Object receiver, Object[] args) {
                        if (!displayEnabled || playback == null || android.os.Looper.myLooper() != main.getLooper()) return;
                        if (receiver instanceof ImageView image) playback.invalidate(image);
                    }
                    @Override public void observe(Object receiver, Object[] args, Object result) {
                        if (!displayEnabled || android.os.Looper.myLooper() != main.getLooper()) return;
                        for (Host host : hosts) if (host.attached && host.surface == ArtworkPlaybackPolicy.Surface.LOCKSCREEN_CARD
                                && host.image.get() == receiver) {
                            trace.state("ARTWORK_TEST_CARD_EFFECT", () -> "stage=" + method.getName()
                                    + " settled=" + access.settled((ImageView) receiver));
                            break;
                        }
                    }
                }));
            }
            pluginHandles.addAll(installed);
            cardEffects = access;
            log("ARTWORK_CAPABILITY_RESOLVED", "cardProfile=c16_user_shader cardHooks=true");
        } catch (Exception | LinkageError error) {
            for (Runnable handle : installed) safeUnhook(handle);
            log("ARTWORK_CAPABILITY_UNSUPPORTED", "cardProfile=unavailable errorType=" + ArtworkTrace.errorType(error));
        }
    }

    private void installSurface(long epoch, ArtworkModelAccess model,
            ArtworkCompatibilityResolver.SurfaceTargets target) {
        List<Runnable> installed = new ArrayList<>();
        try {
            for (Constructor<?> constructor : target.constructors()) {
                int vmIndex = ArtworkCompatibilityResolver.viewModelIndex(constructor.getParameterTypes());
                installed.add(hooks.install(constructor, "artwork-section-" + target.surface() + "-" + constructor.getParameterCount(),
                        (receiver, args, result) -> {
                            ArtworkTrace attempt = new ArtworkTrace("host");
                            attempt.state("ARTWORK_SECTION_SEEN", () -> "surface=" + target.surface()
                                    + " pluginEpoch=" + epoch + " sectionType=" + receiver.getClass().getName());
                            Object vm = target.sectionViewModel().get(receiver);
                            Object root = args[vmIndex + 1];
                            if (!(root instanceof ViewGroup group) || vm != args[vmIndex]) {
                                attempt.state("ARTWORK_HOST_REJECTED", () -> "surface=" + target.surface() + " reason=constructor_owner_mismatch");
                                return;
                            }
                            WeakReference<Object> section = new WeakReference<>(receiver);
                            WeakReference<Object> viewModel = new WeakReference<>(vm);
                            WeakReference<ViewGroup> container = new WeakReference<>(group);
                            main.post(() -> registerHost(epoch, section, viewModel, container, model, target, attempt));
                        }));
            }
            installed.add(hooks.install(target.dispose(), "artwork-dispose-" + target.surface(),
                    (receiver, args, result) -> {
                        WeakReference<Object> section = new WeakReference<>(receiver);
                        main.post(() -> {
                            for (Host host : new ArrayList<>(hosts)) if (host.section.get() == section.get()) removeHost(host, "section_disposed");
                        });
                    }));
            pluginHandles.addAll(installed);
            log("ARTWORK_SECTION_HOOKS_INSTALLED", "surface=" + target.surface() + " pluginEpoch=" + epoch
                    + " constructorCount=" + target.constructors().size() + " dispose=true");
        } catch (Exception error) {
            for (Runnable handle : installed) safeUnhook(handle);
            log("ARTWORK_CAPABILITY_UNSUPPORTED", "surface=" + target.surface() + " reason=hook_failed errorType=" + ArtworkTrace.errorType(error));
        }
    }

    private void registerHost(long epoch, WeakReference<Object> section, WeakReference<Object> vm,
            WeakReference<ViewGroup> container, ArtworkModelAccess model,
            ArtworkCompatibilityResolver.SurfaceTargets target, ArtworkTrace diagnostic) {
        ViewGroup root = container.get();
        if (epoch != pluginEpoch.get() || root == null || section.get() == null || vm.get() == null) {
            diagnostic.state("ARTWORK_HOST_REJECTED", () -> "surface=" + target.surface() + " reason="
                    + (epoch != pluginEpoch.get() ? "stale_plugin" : root == null ? "root_gone" : section.get() == null ? "section_gone" : "vm_gone"));
            return;
        }
        for (Host host : hosts) if (host.section.get() == section.get()) return;
        if (hosts.size() >= 8) {
            diagnostic.state("ARTWORK_HOST_REJECTED", () -> "surface=" + target.surface() + " reason=host_budget");
            return;
        }
        int id = root.getResources().getIdentifier(target.imageResource(), "id", "com.oplus.systemui.plugins");
        View candidate = id == 0 ? null : root.findViewById(id);
        if (!(candidate instanceof ImageView image) || !(image.getParent() instanceof ViewGroup)
                || !target.imageResource().equals(image.getResources().getResourceEntryName(image.getId()))) {
            diagnostic.state("ARTWORK_HOST_REJECTED", () -> "surface=" + target.surface()
                    + " reason=image_contract resourceResolved=" + (id != 0)
                    + " imageFound=" + (candidate instanceof ImageView)
                    + " candidateType=" + (candidate == null ? "none" : candidate.getClass().getName()));
            return;
        }
        Host host = new Host(epoch, section, vm, container, new WeakReference<>(image), model, target.surface(), diagnostic);
        hosts.add(host);
        root.addOnAttachStateChangeListener(host);
        diagnostic.state("ARTWORK_HOST_REGISTERED", () -> "surface=" + target.surface() + " pluginEpoch=" + epoch
                + " rootType=" + root.getClass().getName() + " imageType=" + image.getClass().getName()
                + " parentType=" + image.getParent().getClass().getName() + " attached=" + root.isAttachedToWindow());
        if (root.isAttachedToWindow()) host.attach();
    }

    private void captureSource(ArtworkSeedlingAccess access, Object value) {
        long sequence = sourceSequence.incrementAndGet();
        List<ArtworkSessionAssociation.Entry<MediaSession.Token>> entries;
        try { entries = access.read(value); } catch (Exception error) {
            trace.state("ARTWORK_SOURCE_READ", () -> "reason=decode_failed errorType=" + ArtworkTrace.errorType(error));
            entries = List.of();
        }
        SourceUpdate update = new SourceUpdate(sequence, entries);
        pendingSource.accumulateAndGet(update, (previous, next) ->
                previous == null || next.sequence() > previous.sequence() ? next : previous);
        scheduleSource();
    }

    private void scheduleSource() {
        if (!sourcePosted.compareAndSet(false, true)) return;
        main.post(() -> {
            SourceUpdate update = pendingSource.get();
            try {
                if (!observing() || update == null) { registry.close(); return; }
                registry.updateSource(update.sequence(), update.entries());
                trace.state("ARTWORK_SOURCE_SNAPSHOT", () -> sourceSummary(update.entries()));
                refreshHosts();
            } finally {
                sourcePosted.set(false);
                if (pendingSource.get() != update) scheduleSource();
            }
        });
    }

    private List<ArtworkSessionAssociation.Entry<MediaSession.Token>> readCurrentSource() {
        Object instance = source.get();
        ArtworkSeedlingAccess access = seedling;
        if (instance == null || access == null) {
            trace.state("ARTWORK_SOURCE_READ", () -> "reason=source_unavailable sourceAlive=" + (instance != null) + " accessReady=" + (access != null));
            return List.of();
        }
        boolean previous = reading.get();
        reading.set(true);
        try { return access.read(access.sortedEntries.invoke(instance)); }
        catch (Exception error) {
            trace.state("ARTWORK_SOURCE_READ", () -> "reason=invoke_failed errorType=" + ArtworkTrace.errorType(error));
            return List.of();
        }
        finally { reading.set(previous); }
    }

    private void refreshSource() {
        if (source.get() != null && seedling != null) {
            registry.updateSource(sourceSequence.incrementAndGet(), readCurrentSource());
        }
    }

    private void refreshHosts() {
        for (Host host : new ArrayList<>(hosts)) {
            ViewGroup root = host.root.get();
            if (root == null || host.section.get() == null) { removeHost(host, "host_collected"); continue; }
            if (host.attached && observing()) host.refresh();
            else { host.releaseInput(); registry.detachHost(host); }
        }
    }

    private void suspendObservations() {
        for (Host host : hosts) host.releaseInput();
        registry.close();
    }

    private void clearPlugin() {
        for (Host host : new ArrayList<>(hosts)) removeHost(host, "loader_changed");
        synchronized (flowOwners) { flowOwners.clear(); }
        for (Runnable handle : flowHandles.values()) safeUnhook(handle);
        flowHandles.clear();
        registry.close();
        if (transition != null) transition.clear();
        updateDisplay();
    }

    private void removeHost(Host host, String reason) {
        host.detach(reason);
        hosts.remove(host);
        ViewGroup root = host.root.get();
        if (root != null) root.removeOnAttachStateChangeListener(host);
    }

    private void watchFlow(Object flow, Host host) throws Exception {
        synchronized (flowOwners) {
            List<WeakReference<Host>> owners = flowOwners.computeIfAbsent(flow, ignored -> new ArrayList<>());
            for (WeakReference<Host> owner : owners) if (owner.get() == host) return;
            owners.add(new WeakReference<>(host));
        }
        Method getter = stateGetter(flow);
        List<Method> methods = new ArrayList<>();
        methods.add(getter);
        for (Method method : flow.getClass().getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == Object.class
                    && (method.getReturnType() == void.class || method.getReturnType() == boolean.class)) methods.add(method);
        }
        for (Method method : methods) {
            if (flowHandles.containsKey(method)) continue;
            method.setAccessible(true);
            Runnable handle = hooks.install(method, "artwork-flow-" + flowHandles.size(), (receiver, args, result) -> {
                if (reading.get() || !observing()) return;
                List<WeakReference<Host>> owners;
                synchronized (flowOwners) {
                    List<WeakReference<Host>> found = flowOwners.get(receiver);
                    if (found == null) return;
                    owners = new ArrayList<>(found);
                }
                Object value = result;
                if (!method.equals(getter)) {
                    reading.set(true);
                    try { value = getter.invoke(receiver); } finally { reading.set(false); }
                }
                for (WeakReference<Host> owner : owners) {
                    Host current = owner.get();
                    ArtworkFlowBinding input = current == null ? null : current.input;
                    if (input != null && input.owns(receiver)) {
                        try { current.observe(input.payload(value), input); }
                        catch (IllegalStateException error) {
                            if (current.input == input && input.owns(receiver)) throw error;
                            // A detached/replaced lease raced the callback; never revive its old payload.
                        }
                    }
                }
            });
            flowHandles.put(method, handle);
        }
    }

    private static void safeUnhook(Runnable handle) {
        if (handle == null) return;
        try { handle.run(); } catch (RuntimeException ignored) {}
    }

    private static Method stateGetter(Object flow) throws NoSuchMethodException {
        return ArtworkModuleSource.stateGetter(flow);
    }

    private void log(String event, String message) {
        trace.state(event, () -> message);
    }

    private static String sourceSummary(List<ArtworkSessionAssociation.Entry<MediaSession.Token>> entries) {
        int active = 0;
        int missingToken = 0;
        StringBuilder references = new StringBuilder();
        for (var entry : entries) {
            if (entry.active()) active++;
            if (entry.token() == null) missingToken++;
            if (references.length() > 0) references.append(',');
            references.append(ArtworkTrace.tokenRef(entry.token()));
        }
        return "sourceCount=" + entries.size() + " activeCount=" + active
                + " missingTokenCount=" + missingToken + " tokenRefs=" + references;
    }

    private final class Host implements View.OnAttachStateChangeListener {
        private record Observation(ArtworkFlowBinding input, long surfaceEpoch, ArtworkCardIdentity card) {}
        final long epoch;
        final WeakReference<Object> section;
        final WeakReference<Object> viewModel;
        final WeakReference<ViewGroup> root;
        final WeakReference<ImageView> image;
        final ArtworkModelAccess model;
        final ArtworkPlaybackPolicy.Surface surface;
        final ArtworkTrace trace;
        final ArtworkHostProbe probe;
        ArtworkSessionRegistry.Binding displayBinding;
        WeakReference<android.graphics.drawable.Drawable> trackedDrawable = new WeakReference<>(null);
        ViewTreeObserver displayTree;
        final ViewTreeObserver.OnPreDrawListener displayLayout = () -> { updateDisplay(); return true; };
        private ArtworkCardIdentity diagnosticCard;
        private long diagnosticModelRevision;
        final AtomicBoolean posted = new AtomicBoolean();
        volatile boolean attached;
        volatile Observation pending;
        volatile long surfaceVersion;
        volatile ArtworkFlowBinding input;

        Host(long epoch, WeakReference<Object> section, WeakReference<Object> viewModel,
                WeakReference<ViewGroup> root, WeakReference<ImageView> image,
                ArtworkModelAccess model, ArtworkPlaybackPolicy.Surface surface, ArtworkTrace trace) {
            this.epoch = epoch;
            this.section = section;
            this.viewModel = viewModel;
            this.root = root;
            this.image = image;
            this.model = model;
            this.surface = surface;
            this.trace = trace;
            this.probe = new ArtworkHostProbe(image.get(), root.get(), trace, surface);
        }

        void attach() {
            attached = true;
            surfaceVersion = surfaceEpoch.incrementAndGet();
            trace.state("ARTWORK_HOST_ATTACHED", () -> "surface=" + surface + " surfaceEpoch=" + surfaceVersion
                    + " pluginEpoch=" + epoch + " observing=" + observing());
            if (observing()) { refreshSource(); refresh(); }
        }

        void detach(String reason) {
            attached = false;
            surfaceVersion = surfaceEpoch.incrementAndGet();
            trace.state("ARTWORK_HOST_DETACHED", () -> "surface=" + surface + " surfaceEpoch=" + surfaceVersion + " reason=" + reason);
            registry.detachHost(this);
            releaseInput();
        }

        void releaseInput() {
            android.graphics.drawable.Drawable drawable = trackedDrawable.get();
            if (transition != null && drawable != null) transition.release(drawable);
            trackedDrawable = new WeakReference<>(null);
            displayBinding = null;
            if (displayTree != null && displayTree.isAlive()) displayTree.removeOnPreDrawListener(displayLayout);
            displayTree = null;
            updateDisplay();
            probe.close();
            ArtworkFlowBinding previous = input;
            input = null;
            pending = null;
            if (previous == null) return;
            synchronized (flowOwners) {
                for (Object flow : previous.watched()) {
                    List<WeakReference<Host>> owners = flowOwners.get(flow);
                    if (owners != null) owners.removeIf(owner -> owner.get() == this || owner.get() == null);
                    if (owners != null && owners.isEmpty()) flowOwners.remove(flow);
                }
            }
            int released = previous.watched().size();
            previous.close();
            trace.state("ARTWORK_FLOW_RELEASED", () -> "surface=" + surface + " surfaceEpoch=" + surfaceVersion
                    + " releasedCount=" + released + " watchedCount=0");
        }

        void refresh() {
            if (!attached || epoch != pluginEpoch.get()) return;
            if (ArtworkTrace.enabled()) probe.start();
            else probe.close();
            if (!displayEnabled && displayTree != null) {
                if (displayTree.isAlive()) displayTree.removeOnPreDrawListener(displayLayout);
                displayTree = null;
            }
            if (displayEnabled && displayTree == null) {
                ImageView nativeImage = image.get();
                if (nativeImage != null) {
                    displayTree = nativeImage.getViewTreeObserver();
                    displayTree.addOnPreDrawListener(displayLayout);
                    nativeImage.invalidate();
                }
            }
            boolean previous = reading.get();
            reading.set(true);
            try {
                ArtworkFlowBinding bound = input;
                if (bound == null) {
                    Object vm = viewModel.get();
                    if (vm == null) {
                        trace.state("ARTWORK_FLOW_REJECTED", () -> "surface=" + surface + " reason=vm_gone");
                        return;
                    }
                    List<Object> candidates = new ArrayList<>();
                    String accessPath;
                    ArtworkModuleSource.Input module = null;
                    Field[] fields = vm.getClass().getDeclaredFields();
                    if (surface == ArtworkPlaybackPolicy.Surface.IMMERSIVE) {
                        module = ArtworkModuleSource.bind(vm, model.modulePayloadType(surface));
                        candidates.add(module.flow());
                        accessPath = "stable_section_input";
                    } else {
                        accessPath = "declared_card_flow";
                        if (fields.length > 96) {
                            trace.state("ARTWORK_FLOW_REJECTED", () -> "surface=" + surface + " reason=field_budget fieldCount=" + fields.length);
                            return;
                        }
                        for (Field field : fields) {
                            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                            field.setAccessible(true);
                            Object candidate = field.get(vm);
                            if (candidate == null) continue;
                            try {
                                Method value = stateGetter(candidate);
                                if (model.isCard(value.invoke(candidate))) candidates.add(candidate);
                            } catch (NoSuchMethodException ignored) { /* not a state-flow input */ }
                        }
                    }
                    if (candidates.size() != 1) {
                        trace.state("ARTWORK_FLOW_REJECTED", () -> "surface=" + surface + " reason=card_flow_count candidateCount=" + candidates.size()
                                + " fieldCount=" + fields.length + " vmType=" + vm.getClass().getName());
                        registry.detachHost(this);
                        return;
                    }
                    Object flow = candidates.get(0);
                    Method getter = stateGetter(flow);
                    List<Object> delegates = new ArrayList<>();
                    Object currentValue = getter.invoke(flow);
                    // Verified C16/C17 read-only wrappers each hold a single underlying state flow.
                    for (Field field : flow.getClass().getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                        field.setAccessible(true);
                        Object delegate = field.get(flow);
                        if (delegate == null) continue;
                        try {
                            Method value = stateGetter(delegate);
                            if (value.invoke(delegate) == currentValue) delegates.add(delegate);
                        } catch (NoSuchMethodException ignored) { /* not a flow delegate */ }
                    }
                    if (delegates.size() > 1) throw new IllegalArgumentException("flow_delegate_ambiguous");
                    bound = new ArtworkFlowBinding(flow, getter, module, delegates);
                    input = bound;
                    watchFlow(flow, this);
                    for (Object delegate : delegates) {
                        watchFlow(delegate, this);
                    }
                    Object selectedFlow = flow;
                    int watchedCount = bound.watched().size();
                    trace.state("ARTWORK_FLOW_BOUND", () -> "surface=" + surface + " flowType=" + selectedFlow.getClass().getName()
                            + " access=" + accessPath + " delegateCount=" + delegates.size() + " watchedCount=" + watchedCount);
                }
                observe(bound.read(), bound);
            } catch (Exception error) {
                releaseInput();
                registry.detachHost(this);
                trace.state("ARTWORK_FLOW_REJECTED", () -> "surface=" + surface + " reason=host_binding_unavailable errorType=" + ArtworkTrace.errorType(error));
            } finally { reading.set(previous); }
        }

        void observe(Object value, ArtworkFlowBinding expected) throws ReflectiveOperationException {
            if (!attached || epoch != pluginEpoch.get() || input != expected) return;
            ArtworkCardIdentity identity = model.readCard(value);
            if (input != expected) return;
            pending = new Observation(expected, surfaceVersion, identity);
            if (ArtworkTrace.enabled()) synchronized (this) {
                if (!java.util.Objects.equals(identity, diagnosticCard)) diagnosticModelRevision++;
                diagnosticCard = identity;
                trace.state("ARTWORK_HOST_MODEL", () -> "surface=" + surface + " surfaceEpoch=" + surfaceVersion
                        + " modelRevision=" + diagnosticModelRevision + " cardPresent=" + (identity != null)
                        + " cardComplete=" + (identity != null && identity.complete()));
            }
            if (!posted.compareAndSet(false, true)) return;
            main.post(() -> {
                posted.set(false);
                Observation latest = pending;
                if (!attached || epoch != pluginEpoch.get() || !observing()) { registry.detachHost(this); return; }
                if (latest == null || latest.input() != input || latest.surfaceEpoch() != surfaceVersion) return;
                registry.observeHost(this, epoch, surfaceVersion, latest.card(), trace, binding -> {
                    displayBinding = binding;
                    updateDisplay();
                    probe.sample();
                    trace.state(
                        binding.ready() ? "ARTWORK_SESSION_BOUND" : "ARTWORK_FALLBACK",
                        () -> "surface=" + surface + " reason=" + binding.reason()
                                + " sessionEpoch=" + (binding.stamp() == null ? 0 : binding.stamp().sessionEpoch())
                                + " trackGeneration=" + (binding.stamp() == null ? 0 : binding.stamp().trackGeneration())
                                + " requestRevision=" + (binding.stamp() == null ? 0 : binding.stamp().requestRevision())
                                + " surfaceEpoch=" + surfaceVersion
                                + " pluginEpoch=" + epoch
                                + " playing=" + binding.playing()
                                + " display=disabled");
                });
            });
        }

        @Override public void onViewAttachedToWindow(View view) { attach(); }
        @Override public void onViewDetachedFromWindow(View view) { detach("view_detached"); }
    }
}
