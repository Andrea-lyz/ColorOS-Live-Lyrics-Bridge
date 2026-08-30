package io.github.andrealtb.lockscreenlyrics.diagnostics;

final class BridgeLogFormatter {
    static final int MAX_FIELD_CHARS = 2_200;

    private BridgeLogFormatter() {
    }

    static String format(
            String level,
            String component,
            String area,
            String event,
            String process,
            String session,
            Long generation,
            String track,
            String reason,
            Long durationMs,
            Integer queueDepth,
            Integer payloadChars,
            Integer parcelBytes,
            Integer suppressed,
            String message) {
        StringBuilder formatted = new StringBuilder(160);
        formatted.append("[CLL]");
        appendField(formatted, "level", level);
        appendField(formatted, "component", component);
        appendField(formatted, "area", area);
        appendField(formatted, "event", event);
        appendField(formatted, "process", process);
        appendField(formatted, "session", session);
        if (generation != null) {
            appendField(formatted, "generation", Long.toString(generation));
        }
        appendField(formatted, "track", track);
        appendField(formatted, "reason", reason);
        if (durationMs != null) {
            appendField(formatted, "durationMs", Long.toString(durationMs));
        }
        if (queueDepth != null) {
            appendField(formatted, "queueDepth", Integer.toString(queueDepth));
        }
        if (payloadChars != null) {
            appendField(formatted, "payloadChars", Integer.toString(payloadChars));
        }
        if (parcelBytes != null) {
            appendField(formatted, "parcelBytes", Integer.toString(parcelBytes));
        }
        if (suppressed != null && suppressed > 0) {
            appendField(formatted, "suppressed", Integer.toString(suppressed));
        }
        appendField(formatted, "message", message);
        return formatted.toString();
    }

    private static void appendField(StringBuilder target, String name, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        target.append(' ').append(name).append('=').append(quoteIfNeeded(value));
    }

    static String quoteIfNeeded(String value) {
        String safe = sanitize(value, MAX_FIELD_CHARS);
        boolean needsQuote = false;
        for (int index = 0; index < safe.length(); index++) {
            char character = safe.charAt(index);
            if (Character.isWhitespace(character) || character == '=' || character == '"') {
                needsQuote = true;
                break;
            }
        }
        if (!needsQuote) {
            return safe;
        }
        StringBuilder quoted = new StringBuilder(safe.length() + 2);
        quoted.append('"');
        for (int index = 0; index < safe.length(); index++) {
            char character = safe.charAt(index);
            if (character == '\\' || character == '"') {
                quoted.append('\\');
            }
            quoted.append(character);
        }
        quoted.append('"');
        return quoted.toString();
    }

    static String sanitize(String value, int maxChars) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder safe = new StringBuilder(Math.min(value.length(), maxChars));
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\r' || character == '\n') {
                safe.append(' ');
            } else if (character == '\t') {
                safe.append(' ');
            } else if (Character.isISOControl(character)) {
                continue;
            } else {
                safe.append(character);
            }
            if (maxChars > 0 && safe.length() > maxChars) {
                safe.setLength(Math.max(0, maxChars - 3));
                safe.append("...");
                break;
            }
        }
        return safe.toString().trim();
    }
}
