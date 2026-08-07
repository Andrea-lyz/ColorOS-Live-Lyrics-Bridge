package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.luckypray.dexkit.query.matchers.ClassMatcher;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SystemUiDexKitAdapterTest {

    /** Mirrors the ColorOS 16.0.9.x style RUS manager surface. */
    private static final class OldRusShapes {
        public void saveListToSP(
                Context context,
                Set set,
                Set set2,
                Map map,
                Map map2,
                Map map3,
                Map map4,
                Map map5) {
        }

        public List<String> getRusWhiteList() {
            return null;
        }

        public static void dealEndTag(
                String tag,
                Set set,
                Set set2,
                List list,
                List list2,
                Map map,
                Map map2,
                Map map3,
                Map map4,
                Map map5) {
        }
    }

    /** Mirrors the ColorOS 16.0.10.x style RUS manager surface. */
    private static final class NewRusShapes {
        public static void saveListToSP(
                Context context,
                Set set,
                Set set2,
                Map map,
                Map map2,
                Map map3,
                Map map4,
                Map map5,
                Map map6,
                int version) {
        }

        public List<String> getWhiteList() {
            return null;
        }
    }

    /** Mirrors MediaActionPrioritySelectorImpl before/after the sixth map. */
    private static final class SelectorShapes {
        public int getLyricEnable(String packageName) {
            return 0;
        }

        public int getLyricEntrance(String packageName) {
            return 0;
        }

        public void updatePkgActionsRule(Map map, Map map2, Map map3, Map map4, Map map5) {
        }

        public void updatePkgActionsRule(
                Map map,
                Map map2,
                Map map3,
                Map map4,
                Map map5,
                Map map6) {
        }

        public static void staticUpdatePkgActionsRule(
                Map map,
                Map map2,
                Map map3,
                Map map4,
                Map map5,
                Map map6) {
        }
    }

    private static final class NoWhiteListGetter {
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return owner.getDeclaredMethod(name, parameterTypes);
    }

    @Test
    public void saveListToSpShapeAcceptsOldInstanceEightParameterForm() throws Exception {
        Method oldForm = method(
                OldRusShapes.class,
                "saveListToSP",
                Context.class,
                Set.class,
                Set.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class);
        assertTrue(SystemUiDexKitAdapter.isSaveListToSpShape(oldForm));
    }

    @Test
    public void saveListToSpShapeAcceptsNewStaticTenParameterForm() throws Exception {
        Method newForm = method(
                NewRusShapes.class,
                "saveListToSP",
                Context.class,
                Set.class,
                Set.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                int.class);
        assertTrue(SystemUiDexKitAdapter.isSaveListToSpShape(newForm));
    }

    @Test
    public void saveListToSpShapeRejectsMismatchedShapes() throws Exception {
        assertFalse(SystemUiDexKitAdapter.isSaveListToSpShape(
                method(SelectorShapes.class, "updatePkgActionsRule", Map.class, Map.class, Map.class, Map.class, Map.class)));
        assertFalse(SystemUiDexKitAdapter.isSaveListToSpShape(
                method(OldRusShapes.class, "getRusWhiteList")));
    }

    @Test
    public void updatePkgActionsRuleShapeAcceptsFiveAndSixMaps() throws Exception {
        Method fiveMaps = method(SelectorShapes.class, "updatePkgActionsRule", Map.class, Map.class, Map.class, Map.class, Map.class);
        Method sixMaps = method(SelectorShapes.class, "updatePkgActionsRule", Map.class, Map.class, Map.class, Map.class, Map.class, Map.class);
        assertTrue(SystemUiDexKitAdapter.isUpdatePkgActionsRuleShape(fiveMaps));
        assertTrue(SystemUiDexKitAdapter.isUpdatePkgActionsRuleShape(sixMaps));
        assertFalse(SystemUiDexKitAdapter.isUpdatePkgActionsRuleShape(
                method(SelectorShapes.class, "staticUpdatePkgActionsRule", Map.class, Map.class, Map.class, Map.class, Map.class, Map.class)));
    }

    @Test
    public void lyricEntranceLookupShapeSelectsOnlyEntranceDespiteEnableTwin() throws Exception {
        Method entrance = method(SelectorShapes.class, "getLyricEntrance", String.class);
        Method enable = method(SelectorShapes.class, "getLyricEnable", String.class);
        assertTrue(SystemUiDexKitAdapter.isLyricEntranceLookupShape(entrance));
        assertFalse(SystemUiDexKitAdapter.isLyricEntranceLookupShape(enable));
    }

    @Test
    public void rusWhiteListGetterShapeMatchesOnlyLegacyName() throws Exception {
        assertTrue(SystemUiDexKitAdapter.isRusWhiteListGetterShape(
                method(OldRusShapes.class, "getRusWhiteList")));
        assertFalse(SystemUiDexKitAdapter.isRusWhiteListGetterShape(
                method(NewRusShapes.class, "getWhiteList")));
        assertFalse(SystemUiDexKitAdapter.isRusWhiteListGetterShape(
                method(OldRusShapes.class, "saveListToSP", Context.class, Set.class, Set.class, Map.class, Map.class, Map.class, Map.class, Map.class)));
    }

    @Test
    public void dealEndTagShapeAcceptsSharedStaticForm() throws Exception {
        Method dealEndTag = method(
                OldRusShapes.class,
                "dealEndTag",
                String.class,
                Set.class,
                Set.class,
                List.class,
                List.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class);
        assertTrue(SystemUiDexKitAdapter.isDealEndTagShape(dealEndTag));
        assertFalse(SystemUiDexKitAdapter.isDealEndTagShape(
                method(OldRusShapes.class, "getRusWhiteList")));
    }

    @Test
    public void findOptionalMethodReturnsNullWhenGetterMissing() {
        assertNull(SystemUiDexKitAdapter.findOptionalMethod(
                NoWhiteListGetter.class,
                "test",
                SystemUiDexKitAdapter::isRusWhiteListGetterShape));
    }

    @Test
    public void findOptionalMethodReturnsMatchingGetter() throws Exception {
        Method found = SystemUiDexKitAdapter.findOptionalMethod(
                OldRusShapes.class,
                "test",
                SystemUiDexKitAdapter::isRusWhiteListGetterShape);
        assertEquals(method(OldRusShapes.class, "getRusWhiteList"), found);
    }

    @Test
    public void buildAnchorMatcherSingleSetUsesAndGroup() {
        ClassMatcher matcher = SystemUiDexKitAdapter.buildAnchorMatcher(
                new String[]{"parseSaveXmlValue whiteList: ", "getRusWhiteList: cache is empty"});
        assertNull(matcher.getAnyOfMatchers());
        assertEquals(2, matcher.getUsingStringsMatcher().size());
    }

    @Test
    public void buildAnchorMatcherMultipleSetsCombineWithAnyOf() {
        ClassMatcher matcher = SystemUiDexKitAdapter.buildAnchorMatcher(
                new String[]{"parseSaveXmlValue whiteList: ", "getRusWhiteList: cache is empty"},
                new String[]{"parseSaveXmlValue whiteList: ", "parseSaveXmlValue pkgRuleMap: ", "applyConfig version="});
        assertNull(matcher.getUsingStringsMatcher());
        assertEquals(2, matcher.getAnyOfMatchers().size());
        assertEquals(2, matcher.getAnyOfMatchers().get(0).getUsingStringsMatcher().size());
        assertEquals(3, matcher.getAnyOfMatchers().get(1).getUsingStringsMatcher().size());
    }
}
