package io.github.andrealtb.lockscreenlyrics;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Typed text format for every Bridge-owned SharedPreferences namespace. */
final class BridgeConfigBackupCodec {
    static final String HEADER = "ColorOS-Live-Lyrics-Bridge Backup v1";
    private static final int MAX_BACKUP_CHARS = 2_000_000;
    private static final int MAX_LINES = 10_000;

    private BridgeConfigBackupCodec() {
    }

    static String encode(Map<String, ? extends Map<String, ?>> namespaces) {
        StringBuilder output = new StringBuilder(4096).append(HEADER).append('\n');
        if (namespaces == null) return output.toString();
        for (Map.Entry<String, ? extends Map<String, ?>> namespace : namespaces.entrySet()) {
            output.append("P\t").append(base64(namespace.getKey())).append('\n');
            Map<String, ?> values = namespace.getValue();
            if (values == null) continue;
            List<String> keys = new ArrayList<>(values.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                Object value = values.get(key);
                String type = typeOf(value);
                output.append("E\t")
                        .append(type)
                        .append('\t')
                        .append(base64(key))
                        .append('\t')
                        .append(base64(encodeValue(type, value)))
                        .append('\n');
            }
        }
        return output.toString();
    }

    static Backup decode(String encoded) {
        String text = encoded == null ? "" : encoded.trim();
        if (text.length() > MAX_BACKUP_CHARS) {
            throw new IllegalArgumentException("Backup is too large");
        }
        String[] lines = text.split("\\r?\\n", -1);
        if (lines.length == 0 || !HEADER.equals(lines[0])) {
            throw new IllegalArgumentException("Unsupported Bridge backup header");
        }
        if (lines.length > MAX_LINES) {
            throw new IllegalArgumentException("Backup has too many entries");
        }
        LinkedHashMap<String, Map<String, Object>> namespaces = new LinkedHashMap<>();
        Map<String, Object> current = null;
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\t", -1);
            if (parts.length == 2 && "P".equals(parts[0])) {
                String name = unbase64(parts[1]);
                if (name.isEmpty() || namespaces.containsKey(name)) {
                    throw new IllegalArgumentException("Invalid or duplicate preference namespace");
                }
                current = new LinkedHashMap<>();
                namespaces.put(name, current);
                continue;
            }
            if (parts.length != 4 || !"E".equals(parts[0]) || current == null) {
                throw new IllegalArgumentException("Malformed Bridge backup entry");
            }
            String type = parts[1];
            String key = unbase64(parts[2]);
            if (key.isEmpty() || current.containsKey(key)) {
                throw new IllegalArgumentException("Invalid or duplicate preference key");
            }
            current.put(key, decodeValue(type, unbase64(parts[3])));
        }
        return new Backup(namespaces);
    }

    private static String typeOf(Object value) {
        if (value instanceof Boolean) return "B";
        if (value instanceof Integer) return "I";
        if (value instanceof Long) return "L";
        if (value instanceof Float) return "F";
        if (value instanceof String) return "S";
        if (value instanceof Set) return "T";
        throw new IllegalArgumentException("Unsupported preference value type");
    }

    private static String encodeValue(String type, Object value) {
        if (!"T".equals(type)) return String.valueOf(value);
        List<String> values = new ArrayList<>();
        for (Object item : (Set<?>) value) {
            if (!(item instanceof String)) {
                throw new IllegalArgumentException("Unsupported preference string-set value");
            }
            values.add(base64((String) item));
        }
        Collections.sort(values);
        return String.join(",", values);
    }

    private static Object decodeValue(String type, String value) {
        try {
            switch (type) {
                case "B":
                    if (!"true".equals(value) && !"false".equals(value)) {
                        throw new IllegalArgumentException("Invalid boolean preference");
                    }
                    return Boolean.parseBoolean(value);
                case "I":
                    return Integer.parseInt(value);
                case "L":
                    return Long.parseLong(value);
                case "F":
                    return Float.parseFloat(value);
                case "S":
                    return value;
                case "T":
                    LinkedHashSet<String> set = new LinkedHashSet<>();
                    if (!value.isEmpty()) {
                        for (String item : value.split(",", -1)) set.add(unbase64(item));
                    }
                    return set;
                default:
                    throw new IllegalArgumentException("Unknown preference value type");
            }
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid numeric preference", error);
        }
    }

    private static String base64(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String unbase64(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid Bridge backup encoding", error);
        }
    }

    static final class Backup {
        private final Map<String, Map<String, Object>> namespaces;

        Backup(Map<String, Map<String, Object>> namespaces) {
            this.namespaces = namespaces;
        }

        Map<String, Object> namespace(String name) {
            Map<String, Object> values = namespaces.get(name);
            return values == null ? null : Collections.unmodifiableMap(values);
        }

        Set<String> namespaceNames() {
            return Collections.unmodifiableSet(namespaces.keySet());
        }
    }
}
