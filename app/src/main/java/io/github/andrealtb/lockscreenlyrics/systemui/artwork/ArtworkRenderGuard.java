package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Main-thread render lease. Once rejected, later matching state cannot resurrect it. */
public final class ArtworkRenderGuard implements AutoCloseable {
    private final ArtworkRequestStamp requested;
    private final Supplier<ArtworkRequestStamp> current;
    private final BooleanSupplier eligible;
    private boolean active = true;

    public ArtworkRenderGuard(ArtworkRequestStamp requested, Supplier<ArtworkRequestStamp> current,
            BooleanSupplier eligible) {
        if (requested == null || current == null || eligible == null) throw new IllegalArgumentException("render_guard_missing");
        this.requested = requested;
        this.current = current;
        this.eligible = eligible;
    }

    public boolean permits() {
        if (!active) return false;
        try {
            active = requested.isCurrent(current.get()) && eligible.getAsBoolean();
        } catch (RuntimeException error) {
            active = false;
        }
        return active;
    }

    @Override public void close() { active = false; }
}
