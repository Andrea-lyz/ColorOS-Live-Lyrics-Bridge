package io.github.andrealtb.lockscreenlyrics;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class QqMusicLyricProbe {
    private static final int MAX_FIELD_COUNT = 14;
    private static final int MAX_TEXT_LENGTH = 96;

    private QqMusicLyricProbe() {
    }

    static void logInternalLyric(
            LockscreenLyricsModule module,
            String reason,
            QqMusicInternalLyricExtractor.SongMetadata songMetadata,
            FirstBatchMediaSessionAdapter.TrackMetadata observedTrack,
            Object songInfo,
            Object lyric,
            TimedLyricDocument document) {
        if (module == null) {
            return;
        }
        try {
            module.info("QQ Lyric Probe hit"
                    + ", reason=" + nullToEmpty(reason)
                    + ", songInfoClass=" + className(songInfo)
                    + ", lyricClass=" + className(lyric)
                    + ", song=" + describeSong(songMetadata)
                    + ", observed=" + describeObservedTrack(observedTrack)
                    + ", document=" + describeDocument(document));
            module.info("QQ Lyric Probe lyric fields: " + describeFields(lyric));
            module.info("QQ Lyric Probe primary line route: e="
                    + describeValue(readField(lyric, "e"))
                    + ", firstLine=" + describeLine(readFirstIterable(readField(lyric, "e"))));
        } catch (Throwable t) {
            module.error("QQ Lyric Probe failed while describing internal lyric object", t);
        }
    }

    static String buildSignature(
            String reason,
            QqMusicInternalLyricExtractor.SongMetadata songMetadata,
            FirstBatchMediaSessionAdapter.TrackMetadata observedTrack,
            Object lyric,
            TimedLyricDocument document) {
        String identity = firstNonEmpty(
                observedTrack == null ? "" : observedTrack.identityKey(),
                songMetadata == null ? "" : songMetadata.songId,
                songMetadata == null ? "" : songMetadata.trackHintKey());
        return nullToEmpty(reason)
                + '|'
                + identity
                + '|'
                + className(lyric)
                + '|'
                + (document == null ? -1 : document.lineCount())
                + '|'
                + (document != null && document.hasWordTiming())
                + '|'
                + (document == null ? -1 : document.translationCount());
    }

    static String describeMethod(Method method) {
        if (method == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(method.getDeclaringClass().getName())
                .append('#')
                .append(method.getName())
                .append('(');
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(parameterTypes[i].getName());
        }
        builder.append("):")
                .append(method.getReturnType().getName());
        return builder.toString();
    }

    static void logDecryptedLyric(
            LockscreenLyricsModule module,
            String reason,
            String rawLyric,
            TimedLyricDocument document,
            String candidateTrackKey,
            boolean compatibleWithCurrentTrack,
            int usableMatches) {
        if (module == null) {
            return;
        }
        String raw = nullToEmpty(rawLyric);
        module.info("QQ Lyric Probe decrypted lyric"
                + ", reason=" + nullToEmpty(reason)
                + ", rawChars=" + raw.length()
                + ", rawHash=" + Integer.toHexString(raw.hashCode())
                + ", usableMatches=" + usableMatches
                + ", candidateTrackKey=" + shorten(candidateTrackKey)
                + ", compatible=" + compatibleWithCurrentTrack
                + ", document=" + describeDocument(document)
                + ", head=" + shorten(raw));
    }

    private static String describeSong(QqMusicInternalLyricExtractor.SongMetadata metadata) {
        if (metadata == null) {
            return "null";
        }
        return "{id=" + shorten(metadata.songId)
                + ", title=" + shorten(metadata.title)
                + ", artist=" + shorten(metadata.artist)
                + ", trackKey=" + shorten(metadata.trackHintKey())
                + '}';
    }

    private static String describeObservedTrack(
            FirstBatchMediaSessionAdapter.TrackMetadata track) {
        if (track == null) {
            return "null";
        }
        return "{mediaId=" + shorten(track.mediaId)
                + ", mediaUri=" + shorten(track.mediaUri)
                + ", title=" + shorten(track.title)
                + ", artist=" + shorten(track.artist)
                + ", duration=" + track.durationMillis
                + ", key=" + shorten(track.identityKey())
                + '}';
    }

    private static String describeDocument(TimedLyricDocument document) {
        if (document == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("{lines=")
                .append(document.lineCount())
                .append(", wordTiming=")
                .append(document.hasWordTiming())
                .append(", translations=")
                .append(document.translationCount());
        List<TimedLyricDocument.Line> lines = document.lines();
        if (!lines.isEmpty()) {
            TimedLyricDocument.Line first = lines.get(0);
            builder.append(", first=")
                    .append(first.startMillis)
                    .append('-')
                    .append(first.endMillis)
                    .append(':')
                    .append(shorten(first.text))
                    .append(", words=")
                    .append(first.words.size());
            if (!isEmpty(first.translation)) {
                builder.append(", firstTranslation=")
                        .append(shorten(first.translation));
            }
        }
        builder.append('}');
        return builder.toString();
    }

    private static String describeFields(Object target) {
        if (target == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        Class<?> current = target.getClass();
        while (current != null && current != Object.class && count < MAX_FIELD_COUNT) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                if (count >= MAX_FIELD_COUNT) {
                    break;
                }
                if (builder.length() > 0) {
                    builder.append("; ");
                }
                builder.append(current.getSimpleName())
                        .append('.')
                        .append(field.getName())
                        .append(':')
                        .append(field.getType().getSimpleName())
                        .append('=');
                Object value = null;
                boolean readable = false;
                try {
                    field.setAccessible(true);
                    value = field.get(target);
                    readable = true;
                } catch (Throwable ignored) {
                    // Field names and types are still useful when value access is blocked.
                }
                builder.append(readable ? describeValue(value) : "<inaccessible>");
                count++;
            }
            current = current.getSuperclass();
        }
        if (current != null && current != Object.class) {
            builder.append("; ...");
        }
        return builder.length() == 0 ? className(target) + " has no declared fields" : builder.toString();
    }

    private static String describeLine(Object line) {
        if (line == null) {
            return "null";
        }
        Object words = readField(line, "g");
        Object firstWord = readFirstIterable(words);
        return "{class=" + className(line)
                + ", a=" + describeValue(readField(line, "a"))
                + ", b=" + describeValue(readField(line, "b"))
                + ", c=" + describeValue(readField(line, "c"))
                + ", g=" + describeValue(words)
                + ", firstWord=" + describeWord(firstWord)
                + ", fields=" + describeFields(line)
                + '}';
    }

    private static String describeWord(Object word) {
        if (word == null) {
            return "null";
        }
        return "{class=" + className(word)
                + ", a=" + describeValue(readField(word, "a"))
                + ", b=" + describeValue(readField(word, "b"))
                + ", text="
                + firstNonEmpty(
                valueText(readField(word, "e")),
                valueText(readField(word, "c")),
                valueText(readField(word, "d")),
                valueText(readField(word, "text")),
                valueText(readField(word, "word")))
                + ", fields=" + describeFields(word)
                + '}';
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null || isEmpty(fieldName)) {
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
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object readFirstIterable(Object value) {
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                if (item != null) {
                    return item;
                }
            }
            return null;
        }
        if (value != null && value.getClass().isArray() && Array.getLength(value) > 0) {
            return Array.get(value, 0);
        }
        return null;
    }

    private static String describeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence) {
            String text = value.toString();
            return "String(len=" + text.length() + ", " + shorten(text) + ")";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return String.valueOf(value);
        }
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            return value.getClass().getName() + "(size=" + collection.size() + ")";
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            return value.getClass().getName() + "(size=" + map.size() + ")";
        }
        if (value.getClass().isArray()) {
            return value.getClass().getComponentType().getName()
                    + "[](len=" + Array.getLength(value) + ")";
        }
        if (value instanceof Iterable) {
            int count = 0;
            for (Object ignored : (Iterable<?>) value) {
                count++;
                if (count >= 8) {
                    return value.getClass().getName() + "(iterable>=8)";
                }
            }
            return value.getClass().getName() + "(iterable=" + count + ")";
        }
        return className(value) + '@' + Integer.toHexString(System.identityHashCode(value));
    }

    private static String valueText(Object value) {
        return value == null ? "" : shorten(String.valueOf(value));
    }

    private static String className(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static String shorten(String value) {
        String clean = LyricTextSanitizer.removeIgnorableCharacters(nullToEmpty(value))
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        if (clean.length() <= MAX_TEXT_LENGTH) {
            return clean;
        }
        return clean.substring(0, MAX_TEXT_LENGTH)
                + "...("
                + String.format(Locale.ROOT, "%d", clean.length())
                + ")";
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
