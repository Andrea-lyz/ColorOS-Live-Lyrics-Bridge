package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class TranslationActionRulePolicyTest {

    @Test
    public void patchesOnlyMainSurfaceAndNeverMutatesQsSurface() {
        Map<String, String> normal = new LinkedHashMap<>(Map.of("native", "1", "existing0", "0", "notif", "2"));
        Map<String, String> qs = Map.of("native", "4", "player", "2");
        Object config = new Object();
        Object[] args = {normal, qs, config, config, config};

        Object[] patched = TranslationActionRulePolicy.patch(args, List.of("player", "native", "existing0", "notif"));

        // args[1] (QS table) must NEVER be modified: same instance, same content
        assertSame(qs, patched[1]);
        assertEquals(Map.of("native", "4", "player", "2"), patched[1]);

        // args[0] (main table):
        // - "player" had no entry -> "0"
        // - "native" had "1" -> "0,1" (prepended)
        // - "existing0" had "0" -> kept "0"
        // - "notif" had "2" -> skipped, kept "2"
        Map<?, ?> patchedMain = (Map<?, ?>) patched[0];
        assertEquals("0", patchedMain.get("player"));
        assertEquals("0,1", patchedMain.get("native"));
        assertEquals("0", patchedMain.get("existing0"));
        assertEquals("2", patchedMain.get("notif"));

        // Original inputs untouched
        assertSame(normal, args[0]);
        for (int i = 2; i < args.length; i++) {
            assertSame(args[i], patched[i]);
        }
    }

    @Test
    public void recognizesNativeRule0Correctly() {
        Map<String, String> rules = Map.of(
                "qq", "0",
                "netease", "0,1",
                "salt", "1",
                "poweramp", "2"
        );
        assertTrue(TranslationActionRulePolicy.isNativeRule0(rules, "qq"));
        assertTrue(TranslationActionRulePolicy.isNativeRule0(rules, "netease"));
        assertFalse(TranslationActionRulePolicy.isNativeRule0(rules, "salt"));
        assertFalse(TranslationActionRulePolicy.isNativeRule0(rules, "poweramp"));
        assertFalse(TranslationActionRulePolicy.isNativeRule0(rules, "unknown"));
    }

    @Test
    public void snapshotRawArgsPerformsDeepCopy() {
        Map<String, String> main = new LinkedHashMap<>();
        main.put("p1", "1");
        Map<String, String> qs = new LinkedHashMap<>();
        qs.put("p1", "2");
        Object[] args = {main, qs};

        Object[] snapshot = TranslationActionRulePolicy.snapshotRawArgs(args);
        assertNotSame(main, snapshot[0]);
        assertNotSame(qs, snapshot[1]);
        assertEquals(main, snapshot[0]);
        assertEquals(qs, snapshot[1]);

        // Modifying main doesn't affect snapshot
        main.put("p2", "0");
        assertFalse(((Map<?, ?>) snapshot[0]).containsKey("p2"));
    }
}
