package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class TranslationActionRulePolicyTest {
    @Test public void patchesBothSurfacesWithoutMutatingRusOrOtherConfiguration() {
        Map<String, String> normal = Map.of("native", "2");
        Map<String, String> qs = Map.of("native", "4");
        Object config = new Object();
        Object[] args = {normal, qs, config, config, config};
        Object[] patched = TranslationActionRulePolicy.patch(args, List.of("player"));
        assertEquals(Map.of("native", "2", "player", "0"), patched[0]);
        assertEquals(Map.of("native", "4", "player", "0"), patched[1]);
        assertSame(normal, args[0]);
        assertSame(qs, args[1]);
        for (int i = 2; i < args.length; i++) assertSame(args[i], patched[i]);
        assertArrayEquals(patched, TranslationActionRulePolicy.patch(patched, List.of("player")));
    }
}
