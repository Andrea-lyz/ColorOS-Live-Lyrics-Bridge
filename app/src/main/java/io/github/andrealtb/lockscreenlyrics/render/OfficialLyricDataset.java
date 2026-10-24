package io.github.andrealtb.lockscreenlyrics.render;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.Objects;

/** Immutable, complete native display input. Individual matching rows never establish identity. */
public final class OfficialLyricDataset {
    private final List<Row> rows;
    private final UnaryOperator<String> expectedTextNormalizer;

    private OfficialLyricDataset(List<Row> rows) {
        this(rows, null);
    }

    private OfficialLyricDataset(List<Row> rows, UnaryOperator<String> expectedTextNormalizer) {
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.expectedTextNormalizer = expectedTextNormalizer;
    }

    /** Mirrors the verified OPlus display parser, including its centisecond LRC precision. */
    public static OfficialLyricDataset fromDisplaySource(String source) {
        ArrayList<Row> rows = new ArrayList<>();
        if (source != null) {
            for (String raw : source.split("\\r\\n|\\n|\\r")) {
                String line = trim(raw);
                if (line.isEmpty()) continue;
                if (line.charAt(0) == '{') {
                    try {
                        JSONObject object = new JSONObject(line);
                        long time = object.optLong("t", -1L);
                        JSONArray content = object.optJSONArray("c");
                        if (time < 0 || content == null) continue;
                        StringBuilder text = new StringBuilder();
                        ArrayList<String> segments = new ArrayList<>();
                        for (int i = 0; i < content.length(); i++) {
                            JSONObject segment = content.optJSONObject(i);
                            if (segment != null) {
                                String value = segment.optString("tx", "");
                                segments.add(value);
                                text.append(value);
                            }
                        }
                        if (text.length() > 0) rows.add(new Row(time, text.toString(), segments));
                    } catch (Exception ignored) {
                        // Invalid native JSON lines are ignored by SystemUI too.
                    }
                } else if (line.charAt(0) == '[') {
                    ArrayList<Long> times = new ArrayList<>();
                    int cursor = 0;
                    while (cursor < line.length() && line.charAt(cursor) == '[') {
                        int close = line.indexOf(']', cursor + 1);
                        if (close < 0) break;
                        String[] parts = line.substring(cursor + 1, close).split("[:.]", 3);
                        if (parts.length == 3) {
                            try {
                                long minutes = Long.parseLong(parts[0]);
                                long seconds = Long.parseLong(parts[1]);
                                String fraction = parts[2].substring(0, Math.min(2, parts[2].length()));
                                times.add(minutes * 60_000L + seconds * 1_000L
                                        + Long.parseLong(fraction) * 10L);
                            } catch (NumberFormatException ignored) {
                                // Non-time metadata tags do not create native display rows.
                            }
                        }
                        cursor = close + 1;
                    }
                    String text = trim(line.substring(cursor));
                    if (!text.isEmpty()) {
                        for (long time : times) rows.add(new Row(time, text));
                    }
                }
            }
        }
        rows.sort(Comparator.comparingLong(row -> row.timeMillis));
        return new OfficialLyricDataset(rows);
    }

    /** Null denotes an unsupported row layout; an empty list is a real, committed empty dataset. */
    public static OfficialLyricDataset fromNativeRows(List<?> nativeRows) {
        if (nativeRows == null) return null;
        ArrayList<Row> rows = new ArrayList<>(nativeRows.size());
        Class<?> rowClass = null;
        Field textField = null;
        Field timeField = null;
        try {
            for (Object row : nativeRows) {
                if (row == null) return null;
                if (row.getClass() != rowClass) {
                    rowClass = row.getClass();
                    textField = null;
                    timeField = null;
                    for (Class<?> type = rowClass; type != null && type != Object.class;
                            type = type.getSuperclass()) {
                        for (Field field : type.getDeclaredFields()) {
                            if (Modifier.isStatic(field.getModifiers())) continue;
                            if (field.getType() == String.class) {
                                if (textField != null) return null;
                                textField = field;
                            } else if (field.getType() == long.class || field.getType() == Long.class) {
                                if (timeField != null) return null;
                                timeField = field;
                            }
                        }
                    }
                    if (textField == null || timeField == null) return null;
                    textField.setAccessible(true);
                    timeField.setAccessible(true);
                }
                Object text = textField.get(row);
                Object time = timeField.get(row);
                if (!(text instanceof String) || !(time instanceof Number)) return null;
                rows.add(new Row(((Number) time).longValue(), (String) text));
            }
        } catch (Throwable ignored) {
            return null;
        }
        return new OfficialLyricDataset(rows);
    }

    public int size() { return rows.size(); }

    public boolean matchesNative(OfficialLyricDataset nativeDataset) {
        if (nativeDataset == null || rows.size() != nativeDataset.rows.size()) return false;
        for (int i = 0; i < rows.size(); i++) {
            Row expected = rows.get(i);
            Row actual = nativeDataset.rows.get(i);
            String text = expected.text;
            if (nativeDataset.expectedTextNormalizer != null) {
                if (expected.segments == null) {
                    text = nativeDataset.expectedTextNormalizer.apply(text);
                } else {
                    StringBuilder normalized = new StringBuilder();
                    for (String segment : expected.segments) {
                        normalized.append(nativeDataset.expectedTextNormalizer.apply(segment));
                    }
                    text = normalized.toString();
                }
            }
            if (expected.timeMillis != actual.timeMillis || !Objects.equals(text, actual.text)) return false;
        }
        return true;
    }

    OfficialLyricDataset withExpectedTextNormalizer(UnaryOperator<String> normalizer) {
        return new OfficialLyricDataset(rows, normalizer);
    }

    public boolean sameRow(int index, OfficialLyricDataset other, int otherIndex) {
        return other != null && index >= 0 && index < rows.size()
                && otherIndex >= 0 && otherIndex < other.rows.size()
                && rows.get(index).equals(other.rows.get(otherIndex));
    }

    /** Reads the actual committed list, including asynchronous ListAdapter implementations. */
    public static OfficialLyricDataset fromAdapter(Object adapter) {
        if (adapter == null) return null;
        try {
            java.lang.reflect.Method currentList = adapter.getClass().getMethod("getCurrentList");
            currentList.setAccessible(true);
            Object value = currentList.invoke(adapter);
            if (!(value instanceof List)) return null;
            List<?> nativeRows = (List<?>) value;
            OfficialLyricDataset dataset = fromNativeRows(nativeRows);
            if (dataset == null || nativeRows.isEmpty()) return dataset;
            return dataset.withExpectedTextNormalizer(nativeTextNormalizer(nativeRows.get(0).getClass()));
        } catch (NoSuchMethodException ignored) {
            // ColorOS 16 owns an ArrayList directly instead of using AsyncListDiffer.
        } catch (Throwable ignored) {
            return null;
        }
        try {
            java.lang.reflect.Method itemCount = adapter.getClass().getMethod("getItemCount");
            itemCount.setAccessible(true);
            Object countValue = itemCount.invoke(adapter);
            if (!(countValue instanceof Number)) return null;
            int count = ((Number) countValue).intValue();
            OfficialLyricDataset found = null;
            for (Class<?> type = adapter.getClass(); type != null && type != Object.class;
                    type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())
                            || !List.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    Object value = field.get(adapter);
                    if (!(value instanceof List) || ((List<?>) value).size() != count) continue;
                    OfficialLyricDataset candidate = fromNativeRows((List<?>) value);
                    if (candidate == null) continue;
                    if (found != null && !found.equals(candidate)) return null;
                    found = candidate;
                }
            }
            return found;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static UnaryOperator<String> nativeTextNormalizer(Class<?> rowClass) {
        // Verified C17 LyricLine/parser family. Use its own formatter, including long-line
        // handling, rather than duplicating a model/version-dependent truncation algorithm.
        if (!"com.oplus.systemui.plugins.shared.data.media.model.r".equals(rowClass.getName())) return null;
        try {
            Class<?> formatter = rowClass.getClassLoader().loadClass("com.oplus.systemui.plugins.shared.utils.e");
            java.lang.reflect.Method normalize = formatter.getDeclaredMethod("a", String.class);
            if (normalize.getReturnType() != String.class || !Modifier.isStatic(normalize.getModifiers())) return null;
            normalize.setAccessible(true);
            return text -> {
                try { return (String) normalize.invoke(null, text); }
                catch (Throwable ignored) { return text; }
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override public boolean equals(Object other) {
        return other instanceof OfficialLyricDataset && rows.equals(((OfficialLyricDataset) other).rows);
    }

    @Override public int hashCode() { return rows.hashCode(); }

    private static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && whitespace(value.charAt(start))) start++;
        while (end > start && whitespace(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }

    private static boolean whitespace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    private static final class Row {
        final long timeMillis;
        final String text;
        final List<String> segments;

        Row(long timeMillis, String text) {
            this(timeMillis, text, null);
        }

        Row(long timeMillis, String text, List<String> segments) {
            this.timeMillis = timeMillis;
            this.text = text;
            this.segments = segments == null ? null
                    : Collections.unmodifiableList(new ArrayList<>(segments));
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Row)) return false;
            Row row = (Row) other;
            return timeMillis == row.timeMillis && text.equals(row.text)
                    && Objects.equals(segments, row.segments);
        }

        @Override public int hashCode() {
            return 31 * (31 * Long.hashCode(timeMillis) + text.hashCode()) + Objects.hashCode(segments);
        }
    }
}
