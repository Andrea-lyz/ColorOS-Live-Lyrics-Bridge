package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.media.MediaMetadata;

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

    /** PYB110 17.0.0.155 appends the immersive full-background map on the same versionName. */
    private static final class ImmersiveFullBgRusShapes {
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
                Map map5,
                Map immersiveFullBg) {
        }

        public static void saveListToSP(
                Context context,
                Set set,
                Set set2,
                Map map,
                Map map2,
                Map map3,
                Map map4,
                Map map5,
                Map immersiveFullBg,
                Map actionConfig,
                int version) {
        }

        public void instanceDealEndTag(
                String tag,
                Set set,
                Set set2,
                List list,
                List list2,
                Map map,
                Map map2,
                Map map3,
                Map map4,
                Map map5,
                Map immersiveFullBg) {
        }
    }

    /** Mirrors MediaActionPrioritySelectorImpl before/after the sixth map. */
    private static final class SelectorShapes {
        public int getLyricEnable(String packageName) {
            return 0;
        }

        public boolean resolveLyricSupport(String packageName, MediaMetadata metadata) {
            return true;
        }

        public boolean supportOplusActionConfig(String packageName) {
            return false;
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

        public void updatePkgActionsRule(
                Map map,
                Map map2,
                Map map3,
                Map map4,
                Map map5,
                Map map6,
                Map map7) {
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

    private static final class MetadataShapes {
        void onMetaDataChanged(String key, MediaMetadata metadata) {}

        static Object access$onMetaDataChanged(
                MetadataShapes self,
                String key,
                MediaMetadata metadata,
                Object continuation) {
            return null;
        }

        void loadLyricInBg(
                String key,
                MediaMetadata metadata,
                String title,
                String artist,
                String packageName,
                Object lyricData) {}
    }

    /** C17 PYB110-class static scheduler, with the same player-index gate as the vendor body. */
    private static final class MetadataSchedulerShapes {
        static Object lastSelf;
        static String lastKey;
        static MediaMetadata lastMetadata;

        int getMediaPlayerIndex(String key) {
            return 0;
        }

        static void access$updateMediaDataFromMetaData(
                MetadataSchedulerShapes self, String key, MediaMetadata metadata) {
            lastSelf = self;
            lastKey = key;
            lastMetadata = metadata;
        }
    }

    private static final class MissingIndexSchedulerShapes {
        static void access$updateMediaDataFromMetaData(
                MissingIndexSchedulerShapes self, String key, MediaMetadata metadata) {
        }
    }

    private static final class IneligibleIndexSchedulerShapes {
        int getMediaPlayerIndex(String key) {
            return -1;
        }

        static void access$updateMediaDataFromMetaData(
                IneligibleIndexSchedulerShapes self, String key, MediaMetadata metadata) {
        }
    }

    /** PYB110-style: the named instance method is gone and only the synthetic remains. */
    private static final class SyntheticMetadataShapes {
        static Object access$onMetaDataChanged(
                SyntheticMetadataShapes self,
                String key,
                MediaMetadata metadata,
                Object continuation) {
            return null;
        }
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
    public void saveListToSpShapeAcceptsImmersiveFullBgMap() throws Exception {
        assertTrue(SystemUiDexKitAdapter.isSaveListToSpShape(method(
                ImmersiveFullBgRusShapes.class,
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
                Map.class,
                int.class)));
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
        Method sevenMaps = method(
                SelectorShapes.class,
                "updatePkgActionsRule",
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class);
        assertTrue(SystemUiDexKitAdapter.isUpdatePkgActionsRuleShape(fiveMaps));
        assertTrue(SystemUiDexKitAdapter.isUpdatePkgActionsRuleShape(sixMaps));
        assertTrue(SystemUiDexKitAdapter.isUpdatePkgActionsRuleShape(sevenMaps));
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
    public void lyricSupportGateShapeSelectsOnlyTheMetadataGate() throws Exception {
        assertTrue(SystemUiDexKitAdapter.isLyricSupportGateShape(
                method(SelectorShapes.class, "resolveLyricSupport", String.class, MediaMetadata.class)));
        assertFalse(SystemUiDexKitAdapter.isLyricSupportGateShape(
                method(SelectorShapes.class, "supportOplusActionConfig", String.class)));
        assertFalse(SystemUiDexKitAdapter.isLyricSupportGateShape(
                method(SelectorShapes.class, "getLyricEnable", String.class)));
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
        assertTrue(SystemUiDexKitAdapter.isDealEndTagShape(method(
                ImmersiveFullBgRusShapes.class,
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
                Map.class,
                Map.class)));
        assertFalse(SystemUiDexKitAdapter.isDealEndTagShape(
                method(OldRusShapes.class, "getRusWhiteList")));
        assertFalse(SystemUiDexKitAdapter.isDealEndTagShape(method(
                ImmersiveFullBgRusShapes.class,
                "instanceDealEndTag",
                String.class,
                Set.class,
                Set.class,
                List.class,
                List.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class,
                Map.class)));
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
    public void metadataSchedulerAcceptsOnlyStaticOwnerPrefixedForm() throws Exception {
        Method scheduler = method(
                MetadataSchedulerShapes.class,
                "access$updateMediaDataFromMetaData",
                MetadataSchedulerShapes.class,
                String.class,
                MediaMetadata.class);
        assertTrue(SystemUiDexKitAdapter.isMetadataScheduler(scheduler));
        assertTrue(SystemUiDexKitAdapter.isReplayableMetadataRefresh(scheduler));
        assertFalse(SystemUiDexKitAdapter.isMetadataScheduler(
                method(MetadataShapes.class, "onMetaDataChanged", String.class, MediaMetadata.class)));
        assertFalse(SystemUiDexKitAdapter.isMetadataScheduler(
                method(
                        SyntheticMetadataShapes.class,
                        "access$onMetaDataChanged",
                        SyntheticMetadataShapes.class,
                        String.class,
                        MediaMetadata.class,
                        Object.class)));
    }

    @Test
    public void metadataSchedulerResolutionHonoursPlayerIndexGate() throws Exception {
        Method scheduler = method(
                MetadataSchedulerShapes.class,
                "access$updateMediaDataFromMetaData",
                MetadataSchedulerShapes.class,
                String.class,
                MediaMetadata.class);
        assertEquals(scheduler, SystemUiDexKitAdapter.findMetadataSchedulerEntry(MetadataSchedulerShapes.class));
        assertTrue(SystemUiDexKitAdapter.canInvokeMetadataScheduler(
                new MetadataSchedulerShapes(), scheduler, "key"));

        Method ineligible = method(
                IneligibleIndexSchedulerShapes.class,
                "access$updateMediaDataFromMetaData",
                IneligibleIndexSchedulerShapes.class,
                String.class,
                MediaMetadata.class);
        assertFalse(SystemUiDexKitAdapter.canInvokeMetadataScheduler(
                new IneligibleIndexSchedulerShapes(), ineligible, "key"));

        Method noIndex = method(
                MissingIndexSchedulerShapes.class,
                "access$updateMediaDataFromMetaData",
                MissingIndexSchedulerShapes.class,
                String.class,
                MediaMetadata.class);
        assertTrue(SystemUiDexKitAdapter.canInvokeMetadataScheduler(
                new MissingIndexSchedulerShapes(), noIndex, "key"));
        assertTrue(SystemUiDexKitAdapter.isReplayableMetadataRefresh(
                method(MetadataShapes.class, "onMetaDataChanged", String.class, MediaMetadata.class)));
        assertFalse(SystemUiDexKitAdapter.isReplayableMetadataRefresh(
                method(
                        SyntheticMetadataShapes.class,
                        "access$onMetaDataChanged",
                        SyntheticMetadataShapes.class,
                        String.class,
                        MediaMetadata.class,
                        Object.class)));
    }

    @Test
    public void metadataSchedulerInvocationKeepsStaticArgumentOrder() throws Exception {
        MetadataSchedulerShapes owner = new MetadataSchedulerShapes();
        Method scheduler = method(
                MetadataSchedulerShapes.class,
                "access$updateMediaDataFromMetaData",
                MetadataSchedulerShapes.class,
                String.class,
                MediaMetadata.class);
        SystemUiDexKitAdapter.invokeMetadataScheduler(scheduler, owner, "entry-key", null);
        assertSame(owner, MetadataSchedulerShapes.lastSelf);
        assertEquals("entry-key", MetadataSchedulerShapes.lastKey);
        assertNull(MetadataSchedulerShapes.lastMetadata);
    }

    @Test
    public void metadataChangedEntryAcceptsNamedAndSyntheticAccessForms() throws Exception {
        Method named = method(MetadataShapes.class, "onMetaDataChanged", String.class, MediaMetadata.class);
        Method access = method(
                MetadataShapes.class,
                "access$onMetaDataChanged",
                MetadataShapes.class,
                String.class,
                MediaMetadata.class,
                Object.class);
        Method synthetic = method(
                SyntheticMetadataShapes.class,
                "access$onMetaDataChanged",
                SyntheticMetadataShapes.class,
                String.class,
                MediaMetadata.class,
                Object.class);
        Method loader = method(
                MetadataShapes.class,
                "loadLyricInBg",
                String.class,
                MediaMetadata.class,
                String.class,
                String.class,
                String.class,
                Object.class);
        assertTrue(SystemUiDexKitAdapter.isReplayableMetadataChanged(named));
        assertFalse(SystemUiDexKitAdapter.isReplayableMetadataChanged(access));
        assertTrue(SystemUiDexKitAdapter.isMetadataChangedEntry(named));
        assertTrue(SystemUiDexKitAdapter.isMetadataChangedEntry(access));
        assertTrue(SystemUiDexKitAdapter.isMetadataChangedEntry(synthetic));
        assertFalse(SystemUiDexKitAdapter.isMetadataChangedEntry(loader));
        assertEquals(1, SystemUiDexKitAdapter.metadataParameterIndex(named));
        assertEquals(2, SystemUiDexKitAdapter.metadataParameterIndex(access));
        assertEquals(named, SystemUiDexKitAdapter.findMetadataChangedEntry(MetadataShapes.class));
        assertEquals(synthetic, SystemUiDexKitAdapter.findMetadataChangedEntry(SyntheticMetadataShapes.class));
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
