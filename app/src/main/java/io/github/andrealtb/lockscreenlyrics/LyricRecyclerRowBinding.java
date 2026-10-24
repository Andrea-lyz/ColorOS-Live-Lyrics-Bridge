package io.github.andrealtb.lockscreenlyrics;

import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Rejects retired lyric holders without treating unavailable reflection as a removed row. */
final class LyricRecyclerRowBinding {
    private static final Map<Class<?>, WeakReference<OwnerAccess>> OWNER_ACCESS = new WeakHashMap<>();

    private LyricRecyclerRowBinding() { }

    static boolean isUnbound(Object recycler, Object itemView, Method positionMethod) {
        if (recycler == null || itemView == null || positionMethod == null) {
            return false;
        }
        try {
            Object result = positionMethod.invoke(recycler, itemView);
            if (!(result instanceof Number)) return false;
            int position = ((Number) result).intValue();
            if (position == -1) return true;
            if (position < 0 || !"getChildAdapterPosition".equals(positionMethod.getName())) {
                return false;
            }
            // Change animations may keep the old holder bound to a valid position while a new
            // holder already owns that position. Text shared by both songs cannot identify it.
            OwnerAccess access = ownerAccess(recycler.getClass(), positionMethod);
            if (access.childHolder == null || access.positionOwner == null) return false;
            Object holder = access.childHolder.invoke(recycler, itemView);
            Object owner = access.positionOwner.invoke(recycler, position);
            return holder != null && owner != null && holder != owner;
        } catch (Throwable ignored) {
            // Unsupported plugin helpers must retain the existing text-based fallback.
            return false;
        }
    }

    private static OwnerAccess ownerAccess(Class<?> recyclerClass, Method positionMethod) {
        synchronized (OWNER_ACCESS) {
            WeakReference<OwnerAccess> reference = OWNER_ACCESS.get(recyclerClass);
            OwnerAccess cached = reference == null ? null : reference.get();
            if (cached != null) return cached;
            Method childHolder = null;
            Method positionOwner = null;
            try {
                childHolder = recyclerClass.getMethod(
                        "getChildViewHolder", positionMethod.getParameterTypes()[0]);
                positionOwner = recyclerClass.getMethod("findViewHolderForAdapterPosition", int.class);
                childHolder.setAccessible(true);
                positionOwner.setAccessible(true);
            } catch (Throwable ignored) {
                // Some plugin RecyclerView versions only expose the position helper.
            }
            OwnerAccess access = new OwnerAccess(childHolder, positionOwner);
            OWNER_ACCESS.put(recyclerClass, new WeakReference<>(access));
            return access;
        }
    }

    private static final class OwnerAccess {
        final Method childHolder;
        final Method positionOwner;

        OwnerAccess(Method childHolder, Method positionOwner) {
            this.childHolder = childHolder;
            this.positionOwner = positionOwner;
        }
    }
}
