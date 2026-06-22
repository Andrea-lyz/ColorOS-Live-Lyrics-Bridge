package io.github.andrealtb.lockscreenlyrics;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedInterface;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

final class ConePlayerAdapter implements PlayerAdapter {
    private static final String SOURCE_NAME = "ConePlayer parser";
    private static final String TRACK_METADATA_SOURCE_NAME = "ConePlayer track metadata";
    private static final String MEDIA_PLAYER_SERVICE_CLASS =
            "ink.trantor.android.mediaplayer.MediaPlayerService";
    private static final String MEDIA3_TRACKS_CLASS = "androidx.media3.common.Tracks";
    private static final String METADATA_PATTERN = "\\[(\\w{2,3}):(.+?)]";
    private static final String TIMESTAMP_PATTERN =
            "\\[(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?]";
    private static final String BACKGROUND_LYRIC_PATTERN = "\\[bg:[^\\]]*?\\]";
    private static final Pattern TIMED_LRC_TAG =
            Pattern.compile("[\\[<][0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?[\\]>]");
    private static final Set<String> LYRIC_METADATA_KEYS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "LYRICS",
                    "LYRIC",
                    "UNSYNCEDLYRICS",
                    "USLT",
                    "SYLT")));
    private static final Set<String> EMPTY_LYRIC_TEXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "暂无歌词",
                    "暂无歌词。",
                    "无歌词",
                    "无歌词。",
                    "纯音乐",
                    "纯音乐，请欣赏",
                    "no lyric",
                    "no lyrics",
                    "instrumental")));
    private static final String[] METADATA_VALUE_FIELDS = {"value", "values", "text"};
    private static final Object DEXKIT_LOAD_LOCK = new Object();

    private static volatile boolean dexKitLoaded;

    private final String packageName;

    ConePlayerAdapter(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public String packageName() {
        return packageName;
    }

    @Override
    public String displayName() {
        return "ConePlayer";
    }

    @Override
    public LyricProviderCapabilities lyricCapabilities() {
        return LyricProviderCapabilities.CURRENT_TRACK_SOURCE;
    }

    @Override
    public void installLyricSourceHooks(LockscreenLyricsModule module, ClassLoader classLoader) {
        module.installInjectedTranslationToggleActionHook(packageName);
        installTrackMetadataHook(module, classLoader);

        try {
            ensureDexKitLoaded();
        } catch (Throwable t) {
            module.error("Failed to load DexKit for " + displayName(), t);
            return;
        }

        try (DexKitBridge bridge = DexKitBridge.create(classLoader, true)) {
            Method parserMethod = findLrcParserMethod(bridge, classLoader);
            parserMethod.setAccessible(true);
            module.hook(parserMethod)
                    .setId("cone-player-lrc-parser-" + packageName)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> onParseLyric(
                            module,
                            chain,
                            lyricCapabilities()));
            module.info("Hooked " + displayName() + " lyric parser via DexKit: "
                    + parserMethod.getDeclaringClass().getName() + "#" + parserMethod.getName());
        } catch (Throwable t) {
            module.error("Failed to hook " + displayName() + " lyric parser via DexKit", t);
        }
    }

    private void installTrackMetadataHook(
            LockscreenLyricsModule module,
            ClassLoader classLoader) {
        try {
            Method onTracksChanged = findOnTracksChangedMethod(classLoader);
            onTracksChanged.setAccessible(true);
            module.hook(onTracksChanged)
                    .setId("cone-player-track-metadata-" + packageName)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> onTracksChanged(module, chain));
            module.info("Hooked " + displayName() + " selected audio-track metadata");
        } catch (Throwable t) {
            module.error("Failed to hook " + displayName() + " selected audio-track metadata", t);
        }
    }

    private static Method findOnTracksChangedMethod(ClassLoader classLoader)
            throws ReflectiveOperationException {
        Class<?> serviceClass = Class.forName(
                MEDIA_PLAYER_SERVICE_CLASS,
                false,
                classLoader);
        for (Method method : serviceClass.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if ("onTracksChanged".equals(method.getName())
                    && parameterTypes.length == 2
                    && MEDIA3_TRACKS_CLASS.equals(parameterTypes[1].getName())) {
                return method;
            }
        }
        throw new NoSuchMethodException(
                MEDIA_PLAYER_SERVICE_CLASS + "#onTracksChanged(*, " + MEDIA3_TRACKS_CLASS + ")");
    }

    private static Object onTracksChanged(
            LockscreenLyricsModule module,
            XposedInterface.Chain chain) throws Throwable {
        Object tracks = chain.getArg(1);
        Object result = chain.proceed();
        String rawLyric = findSelectedAudioLyric(tracks);
        if (isUsableTimedLyric(rawLyric)) {
            module.reportLyricSourceEvent(resolvedEvent(
                    TRACK_METADATA_SOURCE_NAME,
                    rawLyric,
                    LyricProviderCapabilities.CURRENT_TRACK_SOURCE));
        }
        return result;
    }

    private static void ensureDexKitLoaded() {
        if (dexKitLoaded) {
            return;
        }
        synchronized (DEXKIT_LOAD_LOCK) {
            if (dexKitLoaded) {
                return;
            }
            System.loadLibrary("dexkit");
            dexKitLoaded = true;
        }
    }

    private static Method findLrcParserMethod(
            DexKitBridge bridge,
            ClassLoader classLoader) throws ReflectiveOperationException {
        ClassDataList parserClasses = bridge.findClass(FindClass.create()
                .matcher(ClassMatcher.create()
                        .usingEqStrings(
                                METADATA_PATTERN,
                                TIMESTAMP_PATTERN,
                                BACKGROUND_LYRIC_PATTERN)));
        ClassData parserClass = requireSingleClass(parserClasses);

        MethodDataList parserMethods = findParserMethods(
                bridge,
                parserClass,
                "java.lang.String",
                "boolean");
        if (parserMethods.isEmpty()) {
            parserMethods = findParserMethods(
                    bridge,
                    parserClass,
                    "java.lang.String");
        }
        MethodData parserMethod = requireSingleMethod(parserMethods);
        return parserMethod.getMethodInstance(classLoader);
    }

    static String findSelectedAudioLyric(Object tracks) throws ReflectiveOperationException {
        Object groups = invoke(tracks, "getGroups");
        if (!(groups instanceof Iterable<?>)) {
            return "";
        }
        for (Object group : (Iterable<?>) groups) {
            if (group == null
                    || asInt(invoke(group, "getType")) != 1
                    || !asBoolean(invoke(group, "isSelected"))) {
                continue;
            }
            int trackCount = readIntField(group, "length");
            for (int index = 0; index < trackCount; index++) {
                if (!asBoolean(invoke(group, "isTrackSelected", int.class, index))) {
                    continue;
                }
                Object format = invoke(group, "getTrackFormat", int.class, index);
                String lyric = findTimedLyricInFormat(format);
                if (isUsableTimedLyric(lyric)) {
                    return lyric;
                }
            }
        }
        return "";
    }

    private static String findTimedLyricInFormat(Object format)
            throws ReflectiveOperationException {
        Object metadata = readField(format, "metadata");
        if (metadata == null) {
            return "";
        }
        int entryCount = asInt(invoke(metadata, "length"));
        for (int index = 0; index < entryCount; index++) {
            Object entry = invoke(metadata, "get", int.class, index);
            String lyric = extractTimedLyricFromMetadataEntry(entry);
            if (isUsableTimedLyric(lyric)) {
                return lyric;
            }
        }
        return "";
    }

    static String extractTimedLyricFromMetadataEntry(Object entry)
            throws ReflectiveOperationException {
        if (entry == null) {
            return "";
        }
        String key = firstNonEmpty(
                readStringField(entry, "key"),
                readStringField(entry, "id"));
        String normalizedKey = key.trim().toUpperCase(Locale.ROOT);
        if (!normalizedKey.isEmpty() && !LYRIC_METADATA_KEYS.contains(normalizedKey)) {
            return "";
        }
        return findTimedStringValue(entry);
    }

    private static String findTimedStringValue(Object target)
            throws ReflectiveOperationException {
        for (String fieldName : METADATA_VALUE_FIELDS) {
            Object value = readField(target, fieldName);
            String lyric = findTimedStringValue(value, 0);
            if (isUsableTimedLyric(lyric)) {
                return lyric;
            }
        }
        return "";
    }

    private static String findTimedStringValue(Object value, int depth) {
        if (value == null || depth > 2) {
            return "";
        }
        if (value instanceof String) {
            return isUsableTimedLyric((String) value) ? (String) value : "";
        }
        if (value instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) value) {
                String lyric = findTimedStringValue(item, depth + 1);
                if (isUsableTimedLyric(lyric)) {
                    return lyric;
                }
            }
        }
        return "";
    }

    static boolean isUsableTimedLyric(String lyric) {
        if (!LyricInfoContract.containsTimedLrc(lyric)) {
            return false;
        }
        String text = lyricDisplayText(lyric);
        if (text.isEmpty()) {
            return false;
        }
        return !EMPTY_LYRIC_TEXTS.contains(text.toLowerCase(Locale.ROOT));
    }

    private static boolean isNoLyricPlaceholder(String lyric) {
        String text = lyricDisplayText(lyric);
        return !text.isEmpty()
                && EMPTY_LYRIC_TEXTS.contains(text.toLowerCase(Locale.ROOT));
    }

    private static String lyricDisplayText(String lyric) {
        if (lyric == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (String line : lyric.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("(?i)^\\[[a-z]{2,8}:.*]$")) {
                continue;
            }
            String visible = TIMED_LRC_TAG.matcher(trimmed).replaceAll("").trim();
            if (!visible.isEmpty()) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(visible);
            }
        }
        return LyricTextSanitizer.removeIgnorableCharacters(text.toString()).trim();
    }

    private static Object invoke(Object target, String methodName)
            throws ReflectiveOperationException {
        return invoke(target, methodName, null, null);
    }

    private static Object invoke(
            Object target,
            String methodName,
            Class<?> parameterType,
            Object argument) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Method method = parameterType == null
                ? target.getClass().getMethod(methodName)
                : target.getClass().getMethod(methodName, parameterType);
        method.setAccessible(true);
        return parameterType == null
                ? method.invoke(target)
                : method.invoke(target, argument);
    }

    private static Object readField(Object target, String fieldName)
            throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String readStringField(Object target, String fieldName)
            throws ReflectiveOperationException {
        Object value = readField(target, fieldName);
        return value instanceof String ? (String) value : "";
    }

    private static int readIntField(Object target, String fieldName)
            throws ReflectiveOperationException {
        return asInt(readField(target, fieldName));
    }

    private static String firstNonEmpty(String first, String second) {
        return first == null || first.isEmpty() ? second : first;
    }

    private static int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private static MethodDataList findParserMethods(
            DexKitBridge bridge,
            ClassData parserClass,
            String... parameterTypes) {
        return bridge.findMethod(FindMethod.create()
                .searchInClass(Collections.singletonList(parserClass))
                .matcher(MethodMatcher.create()
                        .modifiers(Modifier.STATIC)
                        .returnType("java.util.List")
                        .paramTypes(parameterTypes)));
    }

    private static ClassData requireSingleClass(ClassDataList classes) {
        if (classes.size() == 1) {
            return classes.get(0);
        }
        throw new IllegalStateException(
                "Expected one ConePlayer LRC parser class, found " + classes.size()
                        + ": " + classes);
    }

    private static MethodData requireSingleMethod(MethodDataList methods) {
        if (methods.size() == 1) {
            return methods.get(0);
        }
        throw new IllegalStateException(
                "Expected one ConePlayer LRC parser method, found " + methods.size()
                        + ": " + methods);
    }

    private static Object onParseLyric(
            LockscreenLyricsModule module,
            XposedInterface.Chain chain,
            LyricProviderCapabilities capabilities) throws Throwable {
        Object rawLyricArg = chain.getArg(0);
        Object result = chain.proceed();
        if (!(rawLyricArg instanceof String)) {
            return result;
        }
        String rawLyric = (String) rawLyricArg;
        if (isUsableTimedLyric(rawLyric)) {
            module.reportLyricSourceEvent(resolvedEvent(
                    SOURCE_NAME,
                    rawLyric,
                    capabilities));
        } else if (!rawLyric.trim().isEmpty()) {
            LyricSourceEvent.Outcome outcome = isNoLyricPlaceholder(rawLyric)
                    ? LyricSourceEvent.Outcome.NO_LYRIC
                    : LyricSourceEvent.Outcome.PARSE_FAILED;
            module.reportLyricSourceEvent(LyricSourceEvent.terminal(
                    outcome,
                    SOURCE_NAME,
                    "",
                    "",
                    "",
                    LyricInfoTrackMatcher.inferTrackHintKey(rawLyric),
                    rawLyric,
                    System.currentTimeMillis(),
                    capabilities));
        }
        return result;
    }

    private static LyricSourceEvent resolvedEvent(
            String source,
            String rawLyric,
            LyricProviderCapabilities capabilities) {
        return LyricSourceEvent.resolved(
                source,
                "",
                "",
                "",
                LyricInfoTrackMatcher.inferTrackHintKey(rawLyric),
                "",
                rawLyric,
                System.currentTimeMillis(),
                capabilities);
    }
}
