package io.github.andrealtb.lockscreenlyrics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Versioned settings for display-only cleanup of timed lines at the beginning of a song. */
final class LyricContentCleanupConfig {
    static final int SCHEMA_VERSION = 1;
    static final int MAX_LEARNED_RULES = 32;
    static final int MAX_TRACK_OVERRIDES = 24;
    static final int MAX_PREFIX_CHARS = 40;
    static final int MAX_EXACT_CHARS = 160;
    static final int MAX_SERIALIZED_CHARS = 4 * 1024;

    enum LearnedType {
        PREFIX("prefix"),
        EXACT("exact");

        final String id;

        LearnedType(String id) {
            this.id = id;
        }

        static LearnedType fromId(String id) {
            for (LearnedType value : values()) {
                if (value.id.equals(id)) return value;
            }
            return null;
        }
    }

    static final class LearnedRule {
        final LearnedType type;
        final String value;

        LearnedRule(LearnedType type, String value) {
            this.type = type;
            this.value = sanitizeRuleValue(type, value);
            if (this.value.isEmpty()) {
                throw new IllegalArgumentException("清理格式不能为空");
            }
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof LearnedRule)) return false;
            LearnedRule other = (LearnedRule) object;
            return type == other.type && value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, value);
        }
    }

    final int schemaVersion;
    final boolean copyrightNoticesEnabled;
    final boolean productionCreditsEnabled;
    final boolean titleArtistLeadEnabled;
    final List<LearnedRule> learnedRules;
    final Map<String, String> firstFormalLineByTrack;

    private LyricContentCleanupConfig(Builder builder) {
        schemaVersion = SCHEMA_VERSION;
        copyrightNoticesEnabled = builder.copyrightNoticesEnabled;
        productionCreditsEnabled = builder.productionCreditsEnabled;
        titleArtistLeadEnabled = builder.titleArtistLeadEnabled;
        learnedRules = Collections.unmodifiableList(sanitizeRules(builder.learnedRules));
        firstFormalLineByTrack = Collections.unmodifiableMap(
                sanitizeOverrides(builder.firstFormalLineByTrack));
    }

    static LyricContentCleanupConfig defaults() {
        return new Builder().build();
    }

    Builder buildUpon() {
        return new Builder(this);
    }

    String encode() {
        try {
            JSONObject object = new JSONObject();
            object.put("schema", SCHEMA_VERSION);
            object.put("copyright", copyrightNoticesEnabled);
            object.put("production", productionCreditsEnabled);
            object.put("titleArtist", titleArtistLeadEnabled);
            JSONArray learned = new JSONArray();
            for (LearnedRule rule : learnedRules) {
                JSONObject encoded = new JSONObject();
                encoded.put("type", rule.type.id);
                encoded.put("value", rule.value);
                learned.put(encoded);
            }
            object.put("learned", learned);
            JSONArray overrides = new JSONArray();
            for (Map.Entry<String, String> entry : firstFormalLineByTrack.entrySet()) {
                JSONObject encoded = new JSONObject();
                encoded.put("track", entry.getKey());
                encoded.put("first", entry.getValue());
                overrides.put(encoded);
            }
            object.put("tracks", overrides);
            String encoded = object.toString();
            if (encoded.length() > MAX_SERIALIZED_CHARS) {
                throw new IllegalArgumentException("歌词开头清理设置超过 4KiB 限制");
            }
            return encoded;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalArgumentException("无法保存歌词开头清理设置", error);
        }
    }

    static LyricContentCleanupConfig decode(String encoded) {
        if (encoded == null) return defaults();
        if (encoded.length() > MAX_SERIALIZED_CHARS) return null;
        if (encoded.trim().isEmpty()) return defaults();
        try {
            JSONObject object = new JSONObject(encoded);
            if (object.optInt("schema", -1) != SCHEMA_VERSION) return null;
            Builder builder = new Builder()
                    .copyrightNoticesEnabled(object.optBoolean("copyright", true))
                    .productionCreditsEnabled(object.optBoolean("production", true))
                    .titleArtistLeadEnabled(object.optBoolean("titleArtist", true));
            JSONArray learned = object.optJSONArray("learned");
            if (learned != null) {
                for (int index = 0;
                        index < learned.length() && index < MAX_LEARNED_RULES;
                        index++) {
                    JSONObject item = learned.optJSONObject(index);
                    if (item == null) continue;
                    LearnedType type = LearnedType.fromId(item.optString("type", ""));
                    if (type == null) continue;
                    try {
                        builder.addLearnedRule(new LearnedRule(
                                type,
                                item.optString("value", "")));
                    } catch (IllegalArgumentException ignored) {
                        // Ignore a malformed individual rule while keeping the valid snapshot.
                    }
                }
            }
            JSONArray tracks = object.optJSONArray("tracks");
            if (tracks != null) {
                for (int index = 0;
                        index < tracks.length() && index < MAX_TRACK_OVERRIDES;
                        index++) {
                    JSONObject item = tracks.optJSONObject(index);
                    if (item == null) continue;
                    builder.firstFormalLine(
                            item.optString("track", ""),
                            item.optString("first", ""));
                }
            }
            return builder.build();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ArrayList<LearnedRule> sanitizeRules(List<LearnedRule> source) {
        ArrayList<LearnedRule> result = new ArrayList<>();
        if (source == null) return result;
        for (LearnedRule rule : source) {
            if (rule == null || result.contains(rule)) continue;
            result.add(rule);
            if (result.size() >= MAX_LEARNED_RULES) break;
        }
        return result;
    }

    private static LinkedHashMap<String, String> sanitizeOverrides(Map<String, String> source) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (source == null) return result;
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String track = trimToLimit(entry.getKey(), 256);
            String fingerprint = sanitizeFingerprint(entry.getValue());
            if (track.isEmpty() || fingerprint.isEmpty()) continue;
            result.put(track, fingerprint);
            if (result.size() >= MAX_TRACK_OVERRIDES) break;
        }
        return result;
    }

    private static String sanitizeRuleValue(LearnedType type, String value) {
        if (type == null) return "";
        int max = type == LearnedType.PREFIX ? MAX_PREFIX_CHARS : MAX_EXACT_CHARS;
        String normalized = LyricOpeningCleanup.normalizeForComparison(value);
        if (type == LearnedType.PREFIX && normalized.length() < 2) return "";
        return trimToLimit(normalized, max);
    }

    private static String sanitizeFingerprint(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) return "";
        return normalized;
    }

    private static String trimToLimit(String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof LyricContentCleanupConfig)) return false;
        LyricContentCleanupConfig other = (LyricContentCleanupConfig) object;
        return copyrightNoticesEnabled == other.copyrightNoticesEnabled
                && productionCreditsEnabled == other.productionCreditsEnabled
                && titleArtistLeadEnabled == other.titleArtistLeadEnabled
                && learnedRules.equals(other.learnedRules)
                && firstFormalLineByTrack.equals(other.firstFormalLineByTrack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                copyrightNoticesEnabled,
                productionCreditsEnabled,
                titleArtistLeadEnabled,
                learnedRules,
                firstFormalLineByTrack);
    }

    static final class Builder {
        private boolean copyrightNoticesEnabled = true;
        private boolean productionCreditsEnabled = true;
        private boolean titleArtistLeadEnabled = true;
        private final ArrayList<LearnedRule> learnedRules = new ArrayList<>();
        private final LinkedHashMap<String, String> firstFormalLineByTrack =
                new LinkedHashMap<>();

        Builder() {
        }

        Builder(LyricContentCleanupConfig source) {
            copyrightNoticesEnabled = source.copyrightNoticesEnabled;
            productionCreditsEnabled = source.productionCreditsEnabled;
            titleArtistLeadEnabled = source.titleArtistLeadEnabled;
            learnedRules.addAll(source.learnedRules);
            firstFormalLineByTrack.putAll(source.firstFormalLineByTrack);
        }

        Builder copyrightNoticesEnabled(boolean value) {
            copyrightNoticesEnabled = value;
            return this;
        }

        Builder productionCreditsEnabled(boolean value) {
            productionCreditsEnabled = value;
            return this;
        }

        Builder titleArtistLeadEnabled(boolean value) {
            titleArtistLeadEnabled = value;
            return this;
        }

        Builder addLearnedRule(LearnedRule rule) {
            if (rule != null && !learnedRules.contains(rule)
                    && learnedRules.size() < MAX_LEARNED_RULES) {
                learnedRules.add(rule);
            }
            return this;
        }

        Builder removeLearnedRule(LearnedRule rule) {
            learnedRules.remove(rule);
            return this;
        }

        Builder clearLearnedRules() {
            learnedRules.clear();
            return this;
        }

        Builder firstFormalLine(String trackKey, String fingerprint) {
            String track = trimToLimit(trackKey, 256);
            String cleanFingerprint = sanitizeFingerprint(fingerprint);
            if (!track.isEmpty() && !cleanFingerprint.isEmpty()) {
                firstFormalLineByTrack.remove(track);
                firstFormalLineByTrack.put(track, cleanFingerprint);
                while (firstFormalLineByTrack.size() > MAX_TRACK_OVERRIDES) {
                    String first = firstFormalLineByTrack.keySet().iterator().next();
                    firstFormalLineByTrack.remove(first);
                }
            }
            return this;
        }

        Builder removeTrackOverride(String trackKey) {
            if (trackKey != null) firstFormalLineByTrack.remove(trackKey);
            return this;
        }

        Builder clearTrackOverrides() {
            firstFormalLineByTrack.clear();
            return this;
        }

        LyricContentCleanupConfig build() {
            return new LyricContentCleanupConfig(this);
        }
    }
}
