package io.github.andrealtb.lockscreenlyrics.diagnostics;

import java.util.Locale;
import java.util.regex.Pattern;

public final class SensitiveFieldRedactor {
    private static final Pattern BEARER = Pattern.compile(
            "(?i)(bearer\\s+)[a-z0-9_\\-.]{16,}");
    private static final Pattern TOKEN = Pattern.compile(
            "(?i)(token\\s*[:=]\\s*)[a-z0-9_\\-.]{16,}");
    private static final Pattern COOKIE = Pattern.compile(
            "(?i)(cookie\\s*[:=]\\s*)[^\\s;]+");
    private static final Pattern PASSWORD = Pattern.compile(
            "(?i)(password\\s*[:=]\\s*)[^\\s,]+");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*)[^\\s,]+");
    private static final Pattern CLIENT_TOKEN = Pattern.compile(
            "(?i)(client-token\\s*[:=]\\s*)[^\\s,]+");
    private static final Pattern DATA_PATH = Pattern.compile(
            "/data/user/\\d+/[a-zA-Z0-9_.]+");
    private static final Pattern STORAGE_PATH = Pattern.compile(
            "(?i)(/storage/emulated/\\d+/|/sdcard/)[^\\s,]+");

    private SensitiveFieldRedactor() {
    }

    static String redact(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        String result = BEARER.matcher(message).replaceAll("$1<REDACTED_TOKEN>");
        result = TOKEN.matcher(result).replaceAll("$1<REDACTED_TOKEN>");
        result = COOKIE.matcher(result).replaceAll("$1<REDACTED_COOKIE>");
        result = PASSWORD.matcher(result).replaceAll("$1<REDACTED_PWD>");
        result = AUTHORIZATION.matcher(result).replaceAll("$1<REDACTED_TOKEN>");
        result = CLIENT_TOKEN.matcher(result).replaceAll("$1<REDACTED_TOKEN>");
        result = DATA_PATH.matcher(result).replaceAll("/data/user/<USER>/<PKG>");
        result = STORAGE_PATH.matcher(result).replaceAll("$1<REDACTED_PATH>");
        return result;
    }

    public static String trackHash(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int index = 0; index < 6 && index < hashed.length; index++) {
                hex.append(String.format(Locale.ROOT, "%02x", hashed[index]));
            }
            return "sha256:" + hex;
        } catch (Exception ignored) {
            return "sha256:unavailable";
        }
    }

    /** Returns a query-free URI diagnostic containing only origin and a path hash. */
    public static String uriSummary(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            java.net.URI uri = new java.net.URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme();
            String host = uri.getHost() == null ? "" : uri.getHost();
            return scheme + "://" + host + "/" + trackHash(uri.getPath());
        } catch (Exception ignored) {
            return "uri:" + trackHash(value);
        }
    }
}
