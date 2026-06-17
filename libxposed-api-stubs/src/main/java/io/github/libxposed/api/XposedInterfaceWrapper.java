package io.github.libxposed.api;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;

public class XposedInterfaceWrapper implements XposedInterface {
    private XposedInterface base;
    private Runnable detachImpl;

    public final void attachFramework(XposedInterface base, Runnable detachImpl) {
        this.base = base;
        this.detachImpl = detachImpl;
    }

    public final void detach() {
        if (detachImpl != null) {
            detachImpl.run();
        }
    }

    @Override
    public final int getApiVersion() {
        return XposedInterface.super.getApiVersion();
    }

    @Override
    public final String getFrameworkName() {
        return base.getFrameworkName();
    }

    @Override
    public final String getFrameworkVersion() {
        return base.getFrameworkVersion();
    }

    @Override
    public final long getFrameworkVersionCode() {
        return base.getFrameworkVersionCode();
    }

    @Override
    public final long getFrameworkProperties() {
        return base.getFrameworkProperties();
    }

    @Override
    public final HookBuilder hook(Executable origin) {
        return base.hook(origin);
    }

    @Override
    public final HookBuilder hookClassInitializer(Class<?> origin) {
        return base.hookClassInitializer(origin);
    }

    @Override
    public final boolean deoptimize(Executable executable) {
        return base.deoptimize(executable);
    }

    @Override
    public final Invoker<?, Method> getInvoker(Method method) {
        return base.getInvoker(method);
    }

    @Override
    public final <T> CtorInvoker<T> getInvoker(Constructor<T> constructor) {
        return base.getInvoker(constructor);
    }

    @Override
    public final void log(int priority, String tag, String msg) {
        base.log(priority, tag, msg);
    }

    @Override
    public final void log(int priority, String tag, String msg, Throwable tr) {
        base.log(priority, tag, msg, tr);
    }

    @Override
    public final ApplicationInfo getModuleApplicationInfo() {
        return base.getModuleApplicationInfo();
    }

    @Override
    public final SharedPreferences getRemotePreferences(String group) {
        return base.getRemotePreferences(group);
    }

    @Override
    public final String[] listRemoteFiles() {
        return base.listRemoteFiles();
    }

    @Override
    public final ParcelFileDescriptor openRemoteFile(String name) throws FileNotFoundException {
        return base.openRemoteFile(name);
    }
}
