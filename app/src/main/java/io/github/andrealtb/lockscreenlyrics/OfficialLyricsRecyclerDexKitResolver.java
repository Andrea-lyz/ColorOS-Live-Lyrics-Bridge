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

    @SuppressLint({"DuplicateCreateDexKit", "PrivateApi"})
    static OfficialLyricsRecyclerCompatibility.Binding resolve(View recycler)
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

            List<FieldData> spacingCandidates = new ArrayList<>();
            List<FieldData> durationCandidates = new ArrayList<>();
            List<FieldData> scaleCandidates = new ArrayList<>();
            for (FieldData field : lyricsRecycler.getFields()) {
                switch (field.getTypeName()) {
                    case "int":
                        if (isLineSpacingField(field)) {
                            spacingCandidates.add(field);
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
                    spacingCandidates,
                    "dimen",
                    LINE_SPACING_RESOURCE);
            durationCandidates = narrowByResourceValue(
                    recycler,
                    durationCandidates,
                    "integer",
                    SCROLL_DURATION_RESOURCE);

            FieldData spacing = unique(spacingCandidates);
            FieldData duration = unique(durationCandidates);
            FieldData scale = unique(scaleCandidates);
            if (spacing == null || duration == null || scale == null) {
                return null;
            }

            Field spacingField = spacing.getFieldInstance(recyclerClass.getClassLoader());
            Field durationField = duration.getFieldInstance(recyclerClass.getClassLoader());
            Field scaleField = scale.getFieldInstance(recyclerClass.getClassLoader());
            return OfficialLyricsRecyclerCompatibility.fromResolvedFields(
                    recyclerClass,
                    spacingField,
                    durationField,
                    scaleField,
                    "dexkit:" + spacing.getName()
                            + "/" + duration.getName()
                            + "/" + scale.getName());
        }
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
