package io.github.libxposed.api;

import android.app.AppComponentFactory;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import java.util.List;

public interface XposedModuleInterface {
    interface ModuleLoadedParam {
        boolean isSystemServer();

        String getProcessName();
    }

    interface PackageLoadedParam {
        String getPackageName();

        ApplicationInfo getApplicationInfo();

        boolean isFirstPackage();

        ClassLoader getDefaultClassLoader();
    }

    interface PackageReadyParam extends PackageLoadedParam {
        ClassLoader getClassLoader();

        AppComponentFactory getAppComponentFactory();
    }

    interface SystemServerStartingParam {
        ClassLoader getClassLoader();
    }

    interface HotReloadingParam {
        Bundle getExtras();

        void setSavedInstanceState(Object outState);
    }

    interface HotReloadedParam extends ModuleLoadedParam {
        Bundle getExtras();

        Object getSavedInstanceState();

        List<XposedInterface.HookHandle> getOldHookHandles();
    }

    default void onModuleLoaded(ModuleLoadedParam param) {
    }

    default void onPackageLoaded(PackageLoadedParam param) {
    }

    default void onPackageReady(PackageReadyParam param) {
    }

    default void onSystemServerStarting(SystemServerStartingParam param) {
    }

    default boolean onHotReloading(HotReloadingParam param) {
        return false;
    }

    default void onHotReloaded(HotReloadedParam param) {
        for (XposedInterface.HookHandle handle : param.getOldHookHandles()) {
            handle.unhook();
        }
    }
}
