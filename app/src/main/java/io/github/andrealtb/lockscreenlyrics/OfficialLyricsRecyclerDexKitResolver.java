package io.github.andrealtb.lockscreenlyrics;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.view.View;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.FieldUsingType;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.UsingFieldData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Semantic fallback for SystemUIPlugin builds whose obfuscated field names are unknown. */
final class OfficialLyricsRecyclerDexKitResolver {
    private static final Object DEXKIT_LOAD_LOCK = new Object();
    private static final int MAX_INVOKE_DEPTH = 4;
    private static final String MARGIN_LAYOUT_PARAMS =
            "android.view.ViewGroup$MarginLayoutParams";
    private static final String PLUGIN_RESOURCE_PACKAGE = "com.oplus.systemui.plugins";
    private static final String LINE_SPACING_RESOURCE =
            "media_card_lyrics_recycler_view_line_spacing";
    private static final String SCROLL_DURATION_RESOURCE =
            "lyrics_recycler_view_scroll_duration";

    private static volatile boolean dexKitLoaded;

    private OfficialLyricsRecyclerDexKitResolver() {}

    /** Layout binding and, on builds with the immersive entry, the official current row field. */
    static final class Resolution {
        final OfficialLyricsRecyclerCompatibility.Binding layout;
        final Field currentIndex;

        Resolution(OfficialLyricsRecyclerCompatibility.Binding layout, Field currentIndex) {
            this.layout = layout;
            this.currentIndex = currentIndex;
        }
    }

    @SuppressLint({"DuplicateCreateDexKit", "PrivateApi"})
    static Resolution resolve(View recycler)
            throws ReflectiveOperationException {
        Class<?> recyclerClass = recycler == null ? null : recycler.getClass();
        if (recyclerClass == null || recyclerClass.getClassLoader() == null) {
            return null;
        }
        ensureDexKitLoaded();
        //noinspection DuplicateCreateDexKit -- only reached once for an unknown plugin layout.
        try (DexKitBridge bridge = DexKitBridge.create(recyclerClass.getClassLoader(), true)) {
            ClassData lyricsRecycler = findLyricsRecyclerClass(bridge, recyclerClass.getName());
            if (lyricsRecycler == null) {
                return null;
            }
            Method immersiveEntry = uniqueImmersiveEntry(recyclerClass);
            Method rowStyle = uniqueRowStyleMethod(recyclerClass);

            List<FieldData> spacingCandidates = new ArrayList<>();
            List<FieldData> durationCandidates = new ArrayList<>();
            List<FieldData> scaleCandidates = new ArrayList<>();
            List<FieldData> indexCandidates = new ArrayList<>();
            for (FieldData field : lyricsRecycler.getFields()) {
                switch (field.getTypeName()) {
                    case "int":
                        if (isLineSpacingField(field)) {
                            spacingCandidates.add(field);
                        }
                        if (isImmersiveCurrentIndexField(
                                field,
                                immersiveEntry,
                                rowStyle,
                                recyclerClass.getName())) {
                            indexCandidates.add(field);
                        }
                        break;
                    case "long":
                        if (fieldReachesMethod(field, "setDuration", "long")) {
                            durationCandidates.add(field);
                        }
                        break;
                    case "float":
                        if (isInactiveScaleField(field, recyclerClass.getName())) {
                            scaleCandidates.add(field);
                        }
                        break;
                    default:
                        break;
                }
            }

            spacingCandidates = narrowByResourceValue(
                    recycler,
                    preferFinalFields(spacingCandidates),
                    "dimen",
                    LINE_SPACING_RESOURCE);
            durationCandidates = narrowByResourceValue(
                    recycler,
                    durationCandidates,
                    "integer",
                    SCROLL_DURATION_RESOURCE);

            FieldData index = unique(indexCandidates);
            Field indexField = index == null
                    ? null
                    : index.getFieldInstance(recyclerClass.getClassLoader());

            FieldData spacing = unique(spacingCandidates);
            FieldData duration = unique(durationCandidates);
            FieldData scale = unique(scaleCandidates);
            if (spacing == null || duration == null || scale == null) {
                return new Resolution(null, indexField);
            }

            Field spacingField = spacing.getFieldInstance(recyclerClass.getClassLoader());
            Field durationField = duration.getFieldInstance(recyclerClass.getClassLoader());
            Field scaleField = scale.getFieldInstance(recyclerClass.getClassLoader());
            return new Resolution(
                    OfficialLyricsRecyclerCompatibility.fromResolvedFields(
                            recyclerClass,
                            spacingField,
                            durationField,
                            scaleField,
                            "dexkit:" + spacing.getName()
                                    + "/" + duration.getName()
                                    + "/" + scale.getName()),
                    indexField);
        }
    }

    private static Method uniqueImmersiveEntry(Class<?> recyclerClass) {
        Method match = null;
        for (Method method : recyclerClass.getDeclaredMethods()) {
            if (OfficialImmersiveLyricEntryPolicy.matches(method, recyclerClass)) {
                if (match != null) {
                    return null;
                }
                match = method;
            }
        }
        return match;
    }

    private static Method uniqueRowStyleMethod(Class<?> recyclerClass) {
        Method match = null;
        for (Method method : recyclerClass.getDeclaredMethods()) {
            if (OfficialLyricRowStyleMethodPolicy.matches(method, recyclerClass)) {
                if (match != null) {
                    return null;
                }
                match = method;
            }
        }
        return match;
    }

    /**
     * Line spacing is a final layout attribute. On ColorOS 17 the same bind method also reads
     * the mutable current row and transition generation, whose values can equal the spacing in
     * pixels, so mutable candidates are dropped whenever a final one exists.
     */
    private static List<FieldData> preferFinalFields(List<FieldData> candidates) {
        if (candidates.size() <= 1) {
            return candidates;
        }
        ArrayList<FieldData> finals = new ArrayList<>();
        for (FieldData candidate : candidates) {
            if (Modifier.isFinal(candidate.getModifiers())) {
                finals.add(candidate);
            }
        }
        return finals.isEmpty() ? candidates : finals;
    }

    /**
     * ColorOS 17 keeps the official current row in a mutable int that its immersive entry
     * writes and that the recycler's row restyle method (the one calling the row style helper)
     * compares with each adapter position. The entry also writes transition generation counters,
     * which that restyle method never reads, so the intersection needs no obfuscated name.
     */
    private static boolean isImmersiveCurrentIndexField(
            FieldData field,
            Method immersiveEntry,
            Method rowStyle,
            String recyclerClassName) {
        if (immersiveEntry == null
                || rowStyle == null
                || Modifier.isStatic(field.getModifiers())
                || Modifier.isFinal(field.getModifiers())) {
            return false;
        }
        boolean writtenByEntry = false;
        for (MethodData writer : field.getWriters()) {
            if (isSameMethod(writer, immersiveEntry)) {
                writtenByEntry = true;
                break;
            }
        }
        if (!writtenByEntry) {
            return false;
        }
        for (MethodData reader : field.getReaders()) {
            if (!recyclerClassName.equals(reader.getClassName())) {
                continue;
            }
            for (MethodData invoked : reader.getInvokes()) {
                if (isSameMethod(invoked, rowStyle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSameMethod(MethodData method, Method target) {
        if (method == null
                || target == null
                || !target.getDeclaringClass().getName().equals(method.getClassName())
                || !target.getName().equals(method.getName())) {
            return false;
        }
        Class<?>[] parameterTypes = target.getParameterTypes();
        List<String> parameterNames = method.getParamTypeNames();
        if (parameterNames.size() != parameterTypes.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!parameterTypes[i].getTypeName().equals(parameterNames.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static ClassData findLyricsRecyclerClass(DexKitBridge bridge, String className) {
        ClassDataList matches = bridge.findClass(FindClass.create()
                .matcher(ClassMatcher.create().className(className)));
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static boolean isLineSpacingField(FieldData field) {
        for (MethodData reader : field.getReaders()) {
            for (UsingFieldData use : reader.getUsingFields()) {
                FieldData usedField = use.getField();
                if (use.getUsingType() == FieldUsingType.Write
                        && MARGIN_LAYOUT_PARAMS.equals(usedField.getClassName())
                        && "bottomMargin".equals(usedField.getName())
                        && "int".equals(usedField.getTypeName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isInactiveScaleField(FieldData field, String recyclerClassName) {
        for (MethodData reader : field.getReaders()) {
            if (!recyclerClassName.equals(reader.getClassName())) {
                continue;
            }
            boolean scaleX = false;
            boolean scaleY = false;
            for (MethodData invoked : reader.getInvokes()) {
                scaleX |= isScaleSetter(invoked, "setScaleX");
                scaleY |= isScaleSetter(invoked, "setScaleY");
            }
            if (scaleX && scaleY) {
                return true;
            }
        }
        return false;
    }

    private static boolean fieldReachesMethod(
            FieldData field,
            String methodName,
            String parameterType) {
        for (MethodData reader : field.getReaders()) {
            if (reachesMethod(
                    reader,
                    method -> methodName.equals(method.getName())
                            && method.getParamTypeNames().size() == 1
                            && parameterType.equals(method.getParamTypeNames().get(0)),
                    MAX_INVOKE_DEPTH,
                    new HashSet<>())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isScaleSetter(MethodData method, String name) {
        return name.equals(method.getName())
                && method.getParamTypeNames().size() == 1
                && "float".equals(method.getParamTypeNames().get(0));
    }

    private static List<FieldData> narrowByResourceValue(
            View recycler,
            List<FieldData> candidates,
            String resourceType,
            String resourceName) {
        if (recycler == null || candidates.size() <= 1) {
            return candidates;
        }
        Integer expectedValue = readResourceValue(
                recycler.getContext().getResources(),
                resourceType,
                resourceName);
        if (expectedValue == null) {
            return candidates;
        }
        ArrayList<FieldData> matches = new ArrayList<>();
        for (FieldData candidate : candidates) {
            try {
                Field field = candidate.getFieldInstance(recycler.getClass().getClassLoader());
                field.setAccessible(true);
                long actual = field.getType() == long.class
                        ? field.getLong(recycler)
                        : field.getInt(recycler);
                if (actual == expectedValue) {
                    matches.add(candidate);
                }
            } catch (Throwable ignored) {
                // An unreadable candidate cannot be used safely.
            }
        }
        return matches;
    }

    private static Integer readResourceValue(
            Resources resources,
            String resourceType,
            String resourceName) {
        if (resources == null) {
            return null;
        }
        int resourceId = resources.getIdentifier(
                resourceName,
                resourceType,
                PLUGIN_RESOURCE_PACKAGE);
        if (resourceId == 0) {
            return null;
        }
        try {
            return "dimen".equals(resourceType)
                    ? resources.getDimensionPixelSize(resourceId)
                    : resources.getInteger(resourceId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean reachesMethod(
            MethodData source,
            MethodPredicate predicate,
            int remainingDepth,
            Set<String> visited) {
        if (source == null || remainingDepth < 0 || !visited.add(source.getDescriptor())) {
            return false;
        }
        for (MethodData invoked : source.getInvokes()) {
            if (predicate.test(invoked)) {
                return true;
            }
            if (remainingDepth > 0
                    && isTraversablePluginMethod(invoked)
                    && reachesMethod(invoked, predicate, remainingDepth - 1, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTraversablePluginMethod(MethodData method) {
        String className = method.getClassName();
        return className.startsWith("com.oplus.systemui.plugins.")
                || (!className.startsWith("android.")
                && !className.startsWith("androidx.")
                && !className.startsWith("java.")
                && !className.startsWith("kotlin."));
    }

    private static FieldData unique(List<FieldData> candidates) {
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static void ensureDexKitLoaded() {
        if (dexKitLoaded) {
            return;
        }
        synchronized (DEXKIT_LOAD_LOCK) {
            if (!dexKitLoaded) {
                System.loadLibrary("dexkit");
                dexKitLoaded = true;
            }
        }
    }

    private interface MethodPredicate {
        boolean test(MethodData method);
    }
}
