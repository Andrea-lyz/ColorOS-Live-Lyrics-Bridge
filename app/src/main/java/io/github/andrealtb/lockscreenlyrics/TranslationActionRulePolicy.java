package io.github.andrealtb.lockscreenlyrics;

import java.util.LinkedHashMap;
import java.util.Map;

/** Copies both OPlus action-rule tables; never mutates the RUS-owned inputs. */
final class TranslationActionRulePolicy {
    private TranslationActionRulePolicy() {}

    static Object[] patch(Object[] args, Iterable<String> packages) {
        Object[] result = args.clone();
        for (int index = 0; index < Math.min(2, args.length); index++) {
            if (!(args[index] instanceof Map)) continue;
            Map<Object, Object> rules = new LinkedHashMap<>((Map<?, ?>) args[index]);
            for (String packageName : packages) {
                if (packageName != null && !packageName.isEmpty()) rules.put(packageName, "0");
            }
            result[index] = rules;
        }
        return result;
    }
}
