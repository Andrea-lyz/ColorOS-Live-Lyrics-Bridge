package io.github.andrealtb.lockscreenlyrics;

import io.github.libxposed.api.XposedInterface;

/** Single ownership point for an optional argument rewrite before invoking the host exactly once. */
final class HookProceed {
    private HookProceed() {
    }

    static Object once(XposedInterface.Chain chain, Object[] rewrittenArgs) throws Throwable {
        if (rewrittenArgs == null) {
            return chain.proceed();
        }
        return chain.proceed(rewrittenArgs);
    }
}
