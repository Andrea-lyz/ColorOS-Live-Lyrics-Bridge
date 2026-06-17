package io.github.libxposed.api;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.List;

public interface XposedInterface {
    int API_101 = 101;
    int API_102 = 102;
    int LIB_API = API_102;

    int PRIORITY_DEFAULT = 50;
    int PRIORITY_LOWEST = Integer.MIN_VALUE;
    int PRIORITY_HIGHEST = Integer.MAX_VALUE;

    interface Invoker<T extends Invoker<T, U>, U extends Executable> {
        interface Type {
            Origin ORIGIN = new Origin();

            final class Origin implements Type {
                private Origin() {
                }
            }

            final class Chain implements Type {
                public static final Chain FULL = new Chain(PRIORITY_HIGHEST);
                private final int maxPriority;

                public Chain(int maxPriority) {
                    this.maxPriority = maxPriority;
                }

                public int maxPriority() {
                    return maxPriority;
                }
            }
        }

        T setType(Type type);

        Object invoke(Object thisObject, Object... args) throws Throwable;

        Object invokeSpecial(Object thisObject, Object... args) throws Throwable;
    }

    interface CtorInvoker<T> extends Invoker<CtorInvoker<T>, Constructor<T>> {
        T newInstance(Object... args) throws Throwable;

        <U> U newInstanceSpecial(Class<U> subClass, Object... args) throws Throwable;
    }

    interface Chain {
        Executable getExecutable();

        Object getThisObject();

        List<Object> getArgs();

        Object getArg(int index);

        Object proceed() throws Throwable;

        Object proceed(Object[] args) throws Throwable;

        Object proceedWith(Object thisObject) throws Throwable;

        Object proceedWith(Object thisObject, Object[] args) throws Throwable;
    }

    interface Hooker {
        Object intercept(Chain chain) throws Throwable;
    }

    interface HookHandle {
        Executable getExecutable();

        void unhook();

        String getId();

        HookHandle replaceHook(Hooker hooker);
    }

    enum ExceptionMode {
        DEFAULT,
        PROTECTIVE,
        PASSTHROUGH
    }

    interface HookBuilder {
        HookBuilder setPriority(int priority);

        HookBuilder setExceptionMode(ExceptionMode mode);

        HookBuilder setId(String id);

        HookHandle intercept(Hooker hooker);
    }

    default int getApiVersion() {
        return LIB_API;
    }

    String getFrameworkName();

    String getFrameworkVersion();

    long getFrameworkVersionCode();

    long getFrameworkProperties();

    HookBuilder hook(Executable origin);

    HookBuilder hookClassInitializer(Class<?> origin);

    boolean deoptimize(Executable executable);

    Invoker<?, Method> getInvoker(Method method);

    <T> CtorInvoker<T> getInvoker(Constructor<T> constructor);

    void log(int priority, String tag, String msg);

    void log(int priority, String tag, String msg, Throwable tr);

    ApplicationInfo getModuleApplicationInfo();

    SharedPreferences getRemotePreferences(String group);

    String[] listRemoteFiles();

    ParcelFileDescriptor openRemoteFile(String name) throws FileNotFoundException;
}
