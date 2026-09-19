package io.github.andrealtb.lockscreenlyrics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Copies and patches only the OPlus main action-rule table (args[0]);
 * NEVER mutates or overrides the QS action-rule table (args[1]).
 * This guarantees the status bar / notification center media card always preserves
 * 100% native layout and button order across all players.
 */
final class TranslationActionRulePolicy {
    private TranslationActionRulePolicy() {}

    /**
     * Checks if the player natively contains Rule0 on the main surface.
     */
    static boolean isNativeRule0(Map<?, ?> rawMainRules, String packageName) {
        if (rawMainRules == null || packageName == null || packageName.isEmpty()) {
            return false;
        }
        Object rule = rawMainRules.get(packageName);
        if (rule == null) {
            return false;
        }
        String ruleStr = rule.toString().trim();
        for (String part : ruleStr.split(",")) {
            if ("0".equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deep-copies the raw args so that native rule maps can be cleanly restored without contamination.
     */
    static Object[] snapshotRawArgs(Object[] args) {
        if (args == null) {
            return null;
        }
        Object[] snapshot = args.clone();
        for (int i = 0; i < Math.min(2, args.length); i++) {
            if (args[i] instanceof Map) {
                snapshot[i] = new LinkedHashMap<>((Map<?, ?>) args[i]);
            }
        }
        return snapshot;
    }

    /**
     * Patches only args[0] (main action rule table) for supported players:
     * - If the player already contains Rule0 (e.g. QQ Music, NetEase): leaves unchanged.
     * - If the player natively uses specific notification rules (2/3/4): skipped to protect native behavior.
     * - If the player natively uses Rule1: prepends "0," (e.g. "0,1") so Rule0 takes precedence on lockscreen.
     * - If the player has no entry: sets "0".
     *
     * args[1] (QS table) is NEVER modified.
     */
    static Object[] patch(Object[] args, Iterable<String> packages) {
        if (args == null || args.length == 0) {
            return args;
        }
        Object[] result = args.clone();
        if (args[0] instanceof Map) {
            Map<Object, Object> mainRules = new LinkedHashMap<>((Map<?, ?>) args[0]);
            for (String packageName : packages) {
                if (packageName == null || packageName.isEmpty()) {
                    continue;
                }
                Object existing = mainRules.get(packageName);
                if (existing == null) {
                    mainRules.put(packageName, "0");
                } else {
                    String existingStr = existing.toString().trim();
                    if (containsRule0(existingStr)) {
                        // Already has Rule0, keep as-is
                        continue;
                    }
                    if (isNotificationSpecificRule(existingStr)) {
                        // Notification-specific priority (2/3/4), do not mutate
                        continue;
                    }
                    // e.g. Rule1, prepend "0,"
                    mainRules.put(packageName, "0," + existingStr);
                }
            }
            result[0] = mainRules;
        }
        // args[1] (QS table) remains untouched: result[1] is already args[1]
        return result;
    }

    private static boolean containsRule0(String ruleStr) {
        for (String part : ruleStr.split(",")) {
            if ("0".equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNotificationSpecificRule(String ruleStr) {
        // Rules 2, 3, 4 are notification-specific action rules in ColorOS RUS
        for (String part : ruleStr.split(",")) {
            String trimmed = part.trim();
            if ("2".equals(trimmed) || "3".equals(trimmed) || "4".equals(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
