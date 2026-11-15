package io.github.andrealtb.lockscreenlyrics;

import io.github.libxposed.api.XposedInterface;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * The SystemUI plugin ClassLoader generation (E-5c, plan §15.8.12): which plugin loader is ready
 * and which one owns the plugin hooks, plus the hook handles of that generation. A new loader
 * retires the previous generation's handles. This class is the only writer of that state.
 *
 * <p>Per-installer "installed / attempted" flags still live with their installers in the entry
 * class; {@link #activate} runs their reset inside this generation's lock, at the same point the
 * entry class used to. {@link #pluginHookGenerationLock} is also the lock the entry class uses for
 * the per-adapter bind-hook cache, so it stays package-visible.
 */
final class PluginHookGenerationManager {
    final Object pluginHookGenerationLock = new Object();
    private WeakReference<ClassLoader> activePluginClassLoader = new WeakReference<>(null);
    // Last plugin loader whose early hooks ran; the constructor and factory entries may both fire.
    private WeakReference<ClassLoader> readyPluginClassLoader = new WeakReference<>(null);
    private final ArrayList<XposedInterface.HookHandle> activePluginHookHandles =
            new ArrayList<>();

    /** Records {@code pluginLoader} as ready; false when it already was (constructor and factory may both fire). */
    boolean markReady(ClassLoader pluginLoader) {
        synchronized (pluginHookGenerationLock) {
            if (readyPluginClassLoader.get() == pluginLoader) {
                return false;
            }
            readyPluginClassLoader = new WeakReference<>(pluginLoader);
        }
        return true;
    }

    /**
     * Makes {@code pluginLoader} the active generation and runs {@code resetUnderLock} inside the
     * generation lock. Returns the previous generation's handles to unhook, or {@code null} when
     * {@code pluginLoader} is already active.
     */
    ArrayList<XposedInterface.HookHandle> activate(ClassLoader pluginLoader, Runnable resetUnderLock) {
        ArrayList<XposedInterface.HookHandle> staleHandles;
        synchronized (pluginHookGenerationLock) {
            if (activePluginClassLoader.get() == pluginLoader) {
                return null;
            }
            staleHandles = new ArrayList<>(activePluginHookHandles);
            activePluginHookHandles.clear();
            activePluginClassLoader = new WeakReference<>(pluginLoader);
            resetUnderLock.run();
        }
        return staleHandles;
    }

    void rememberPluginHookHandle(
            ClassLoader pluginLoader,
            XposedInterface.HookHandle handle) {
        if (pluginLoader == null || handle == null) {
            return;
        }
        boolean current;
        synchronized (pluginHookGenerationLock) {
            current = activePluginClassLoader.get() == pluginLoader;
            if (current) {
                activePluginHookHandles.add(handle);
            }
        }
        if (!current) {
            try {
                handle.unhook();
            } catch (Throwable ignored) {
                // A newer loader already owns the plugin hooks.
            }
        }
    }
}
