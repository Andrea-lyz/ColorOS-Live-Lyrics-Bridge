package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import android.media.session.MediaSession;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Stable named vendor classes, validated independently of lyric/RUS targets and UMS mode. */
public final class ArtworkSeedlingAccess {
    private static final String SOURCE = "com.oplus.systemui.media.seedling.OplusSeedlingMediaDataCombineLatest";
    private static final String DATA = "com.oplus.systemui.seedlingservice.mediaControl.SeedlingMediaData";
    public final Method sortedEntries;
    public final Method release;
    public final Method loaded;
    public final Method removed;
    private final Class<?> dataClass;
    private final Method playerId;
    private final Method packageName;
    private final Method userId;
    private final Method token;
    private final Method song;
    private final Method artist;
    private final Method duration;
    private final Method active;

    public ArtworkSeedlingAccess(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> source = loader.loadClass(SOURCE);
        dataClass = loader.loadClass(DATA);
        sortedEntries = method(source, "getSortedEntriesExceptNonActive", List.class);
        release = method(source, "release", void.class);
        loaded = source.getDeclaredMethod("onMediaDataLoaded", String.class, dataClass, int.class);
        removed = source.getDeclaredMethod("onMediaDataRemoved", String.class);
        if (loaded.getReturnType() != void.class || removed.getReturnType() != void.class
                || Modifier.isStatic(loaded.getModifiers()) || Modifier.isStatic(removed.getModifiers())) {
            throw new NoSuchMethodException("seedling_updates_shape");
        }
        loaded.setAccessible(true);
        removed.setAccessible(true);
        playerId = method(dataClass, "getPlayerId", String.class);
        packageName = method(dataClass, "getPackageName", String.class);
        userId = method(dataClass, "getUserId", int.class);
        token = method(dataClass, "getToken", MediaSession.Token.class);
        song = method(dataClass, "getSong", CharSequence.class);
        artist = method(dataClass, "getArtist", CharSequence.class);
        duration = method(dataClass, "getDuration", long.class);
        active = method(dataClass, "getActive", boolean.class);
    }

    public List<ArtworkSessionAssociation.Entry<MediaSession.Token>> read(Object value) throws Exception {
        if (!(value instanceof List<?> list) || list.size() > 32) throw new IllegalArgumentException("source_budget");
        List<ArtworkSessionAssociation.Entry<MediaSession.Token>> entries = new ArrayList<>();
        for (Object data : list) {
            if (data == null || data.getClass() != dataClass) throw new IllegalArgumentException("source_type");
            entries.add(new ArtworkSessionAssociation.Entry<>(text(playerId.invoke(data)),
                    text(packageName.invoke(data)), (Integer) userId.invoke(data),
                    (MediaSession.Token) token.invoke(data), text(song.invoke(data)), text(artist.invoke(data)),
                    (Long) duration.invoke(data), (Boolean) active.invoke(data)));
        }
        return List.copyOf(entries);
    }

    private static String text(Object value) {
        return ArtworkCardIdentity.clean(value == null ? "" : value.toString());
    }

    private static Method method(Class<?> owner, String name, Class<?> result) throws ReflectiveOperationException {
        Method method = owner.getDeclaredMethod(name);
        if (method.getReturnType() != result || Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException("seedling_shape");
        }
        method.setAccessible(true);
        return method;
    }
}
