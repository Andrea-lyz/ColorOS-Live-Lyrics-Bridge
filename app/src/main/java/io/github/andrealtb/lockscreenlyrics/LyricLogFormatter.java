package io.github.andrealtb.lockscreenlyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class LyricLogFormatter {
    static final int MAX_MESSAGE_CHARS = 2_200;

    enum Area {
        LIFECYCLE("Lifecycle"),
        HOOK("Hook"),
        PLAYER("Player"),
        PROVIDER("Provider"),
        TRANSACTION("Transaction"),
        SYSTEM_UI("SystemUI"),
        RENDER("Render"),
        RECYCLER("Recycler"),
        AOD("AOD"),
        SETTINGS("Settings"),
        SCREEN("Screen"),
        PARSER("Parser"),
        TRANSLATION("Translation");

        final String label;

        Area(String label) {
            this.label = label;
        }
    }

    private LyricLogFormatter() {
    }

    static String format(String process, Area area, String event, String message) {
        String safeProcess = sanitize(process, 80);
        String safeEvent = sanitize(event, 64);
        String safeMessage = sanitize(message, MAX_MESSAGE_CHARS);
        int fieldStart = findFieldStart(safeMessage);
        String text = fieldStart < 0 ? safeMessage : safeMessage.substring(0, fieldStart);
        String fields = fieldStart < 0 ? "" : safeMessage.substring(fieldStart + 2);
        StringBuilder formatted = new StringBuilder(safeMessage.length() + 80)
                .append('[').append(safeProcess.isEmpty() ? "unknown" : safeProcess).append(']')
                .append('[').append(area == null ? Area.SYSTEM_UI.label : area.label).append(']')
                .append('[').append(safeEvent.isEmpty() ? "event" : safeEvent).append("] ")
                .append(text);
        if (!fields.isEmpty()) {
            formatted.append(" | ").append(fields);
        }
        return formatted.toString();
    }

    static Area classifyArea(String message) {
        String value = safeLower(message);
        if (containsAny(value, "aod", "ambient")) return Area.AOD;
        if (containsAny(value, "recyclerview", "recycler", "scroll", "prime")) return Area.RECYCLER;
        if (containsAny(value, "draw", "render", "frame", "glow", "scale", "layout height")) return Area.RENDER;
        if (containsAny(value, "translation", "翻译")) return Area.TRANSLATION;
        if (containsAny(value, "provider", "external lyric", "bridge lyric")) return Area.PROVIDER;
        if (containsAny(value, "transaction", "generation", "track handoff")) return Area.TRANSACTION;
        if (containsAny(value, "screen timeout", "wake lock", "user activity", "screen-state")) return Area.SCREEN;
        if (containsAny(value, "parser", "parse", "lrc", "ttml", "yrc")) return Area.PARSER;
        if (containsAny(value, "setting", "preference", "style")) return Area.SETTINGS;
        if (containsAny(value, "hooked", "hook ", "dexkit", "classloader")) return Area.HOOK;
        if (containsAny(value, "player", "playback", "mediasession", "poweramp", "salt", "qq music")) return Area.PLAYER;
        if (containsAny(value, "systemui", "official lyric", "seedling", "oplus")) return Area.SYSTEM_UI;
        if (containsAny(value, "loaded in", "skip process", "module")) return Area.LIFECYCLE;
        return Area.SYSTEM_UI;
    }

    static String classifyEvent(String message) {
        String value = safeLower(message);
        if (value.contains("attachment")) return "attachment";
        if (value.contains("primed")) return "prime";
        if (value.contains("layout height changed")) return "layout-height";
        if (value.contains("row scale")) return "row-scale";
        if (value.contains("setcurrentlyric geometry")) return "set-current-geometry";
        if (value.contains("stabilized") && value.contains("scroll")) return "scroll-stabilized";
        if (value.startsWith("hooked")) return "hook-installed";
        if (value.startsWith("failed")) return "failure";
        if (value.startsWith("ignored")) return "ignored";
        if (value.startsWith("accepted")) return "accepted";
        if (value.startsWith("loaded")) return "loaded";
        if (value.contains("broadcast")) return "broadcast";
        if (value.contains("refresh")) return "refresh";
        return "detail";
    }

    static String sanitize(String value, int maxChars) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder safe = new StringBuilder(Math.min(value.length(), maxChars));
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\r') {
                safe.append("\\r");
            } else if (character == '\n') {
                safe.append("\\n");
            } else if (character == '\t') {
                safe.append("\\t");
            } else if (Character.isISOControl(character)) {
                safe.append("<U+")
                        .append(String.format(Locale.ROOT, "%04X", (int) character))
                        .append('>');
            } else {
                safe.append(character);
            }
            if (maxChars > 0 && safe.length() > maxChars) {
                safe.setLength(Math.max(0, maxChars - 3));
                safe.append("...");
                break;
            }
        }
        return safe.toString();
    }

    static List<String> chunks(String value, int chunkSize) {
        String safe = value == null ? "" : value;
        if (chunkSize <= 0 || safe.length() <= chunkSize) {
            return Collections.singletonList("chunk=1/1 " + safe);
        }
        int total = (safe.length() + chunkSize - 1) / chunkSize;
        ArrayList<String> chunks = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            int start = index * chunkSize;
            int end = Math.min(safe.length(), start + chunkSize);
            chunks.add("chunk=" + (index + 1) + "/" + total + " " + safe.substring(start, end));
        }
        return Collections.unmodifiableList(chunks);
    }

    private static int findFieldStart(String message) {
        int start = 0;
        while (true) {
            int candidate = message.indexOf(", ", start);
            if (candidate < 0) return -1;
            int equals = message.indexOf('=', candidate + 2);
            int nextComma = message.indexOf(", ", candidate + 2);
            if (equals >= 0 && (nextComma < 0 || equals < nextComma)) return candidate;
            start = candidate + 2;
        }
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }
}
