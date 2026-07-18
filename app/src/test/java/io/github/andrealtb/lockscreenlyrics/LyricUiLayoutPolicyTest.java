package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LyricUiLayoutPolicyTest {
    @Test
    public void configuredSpacingIsUsedDirectly() {
        LyricUiConfig defaults = LyricUiConfig.defaults();

        assertEquals(0, LyricUiLayoutPolicy.lineSpacingTenthsDp(defaults));
        assertEquals(0, LyricUiLayoutPolicy.lineSpacingTenthsDp(
                defaults.buildUpon().lineSpacingTenthsDp(0).build()));
        assertEquals(-50, LyricUiLayoutPolicy.lineSpacingTenthsDp(
                defaults.buildUpon().lineSpacingTenthsDp(-999).build()));
        assertEquals(75, LyricUiLayoutPolicy.lineSpacingTenthsDp(
                defaults.buildUpon().lineSpacingTenthsDp(73).build()));
        assertEquals(200, LyricUiLayoutPolicy.lineSpacingTenthsDp(
                defaults.buildUpon().lineSpacingTenthsDp(999).build()));
    }

    @Test
    public void wrappedLineSpacingIsAnIndependentNonNegativeIncrement() {
        LyricUiConfig defaults = LyricUiConfig.defaults();

        assertEquals(0, LyricUiLayoutPolicy.wrappedLineSpacingTenthsDp(defaults));
        assertEquals(25, LyricUiLayoutPolicy.wrappedLineSpacingTenthsDp(
                defaults.buildUpon().wrappedLineSpacingTenthsDp(23).build()));
        assertEquals(-10, LyricUiLayoutPolicy.wrappedLineSpacingTenthsDp(
                defaults.buildUpon().wrappedLineSpacingTenthsDp(-10).build()));
        assertEquals(80, LyricUiLayoutPolicy.wrappedLineSpacingTenthsDp(
                defaults.buildUpon().wrappedLineSpacingTenthsDp(999).build()));
        assertEquals(5f, LyricUiLayoutPolicy.addWrappedLineSpacing(2f, 3f), 0.0001f);
        assertEquals(0f, LyricUiLayoutPolicy.addWrappedLineSpacing(2f, -3f), 0.0001f);
    }

    @Test
    public void mainFontSizeAddsTwoSpOnlyForUntranslatedLayout() {
        LyricUiConfig maximum = LyricUiConfig.defaults()
                .buildUpon()
                .mainFontTenthsSp(280)
                .build();

        assertEquals(28f, LyricUiLayoutPolicy.mainTextSizeSp(maximum, false), 0.0001f);
        assertEquals(30f, LyricUiLayoutPolicy.mainTextSizeSp(maximum, true), 0.0001f);
    }

    @Test
    public void presetsOwnTheirDocumentedSpacing() {
        LyricUiConfig soft = LyricUiPreset.SOFT.apply(LyricUiConfig.defaults())
                .buildUpon()
                .mainFontTenthsSp(180)
                .build();
        LyricUiConfig vivid = LyricUiPreset.VIVID.apply(LyricUiConfig.defaults())
                .buildUpon()
                .mainFontTenthsSp(260)
                .build();

        assertEquals(0, LyricUiLayoutPolicy.lineSpacingTenthsDp(soft));
        assertEquals(0, LyricUiLayoutPolicy.lineSpacingTenthsDp(vivid));
        assertEquals(0, LyricUiLayoutPolicy.lineSpacingTenthsDp(
                LyricUiPreset.DEFAULT.apply(LyricUiConfig.defaults())));
    }

    @Test
    public void legacySpacingMigrationPreservesPreviousPresetBehavior() {
        LyricUiConfig defaults = LyricUiConfig.defaults();
        assertEquals(90, LyricUiLayoutPolicy.legacyLineSpacingTenthsDp(
                defaults.buildUpon().mainFontTenthsSp(180).build()));
        assertEquals(110, LyricUiLayoutPolicy.legacyLineSpacingTenthsDp(defaults));
        assertEquals(130, LyricUiLayoutPolicy.legacyLineSpacingTenthsDp(
                LyricUiPreset.SOFT.apply(defaults)));
    }

    @Test
    public void outerFontBoundsReserveDescendersAndAccents() {
        assertEquals(-25f, LyricUiLayoutPolicy.outwardFontTop(-24.4f), 0.0001f);
        assertEquals(32f, LyricUiLayoutPolicy.fontOuterHeight(-24.4f, 6.2f), 0.0001f);
        assertEquals(58f, LyricUiLayoutPolicy.mainTextBlockHeight(
                -24.4f,
                -20f,
                5f,
                6.2f,
                2,
                1f), 0.0001f);
    }

    @Test
    public void slotHeightRoundsOutwardAndAddsBottomSafety() {
        assertEquals(72, LyricUiLayoutPolicy.requiredSlotHeight(58.1f, 12, 1, 56));
        assertEquals(56, LyricUiLayoutPolicy.requiredSlotHeight(20f, 12, 1, 56));
    }

    @Test
    public void translationContributionContractsContinuouslyWithAnimation() {
        assertEquals(40f, LyricUiLayoutPolicy.translatedGroupHeight(40f, 20f, 0f), 0.0001f);
        assertEquals(50f, LyricUiLayoutPolicy.translatedGroupHeight(40f, 20f, 0.5f), 0.0001f);
        assertEquals(60f, LyricUiLayoutPolicy.translatedGroupHeight(40f, 20f, 1f), 0.0001f);
    }

    @Test
    public void mainLyricViewportNeverExceedsTwoLines() {
        assertEquals(1, LyricUiLayoutPolicy.visibleMainLineCount(1, 2));
        assertEquals(2, LyricUiLayoutPolicy.visibleMainLineCount(2, 2));
        assertEquals(2, LyricUiLayoutPolicy.visibleMainLineCount(3, 2));
        assertEquals(2, LyricUiLayoutPolicy.visibleMainLineCount(5, 2));
        assertEquals(2, LyricUiLayoutPolicy.visibleMainLineCount(16, 2));
    }

    @Test
    public void rowScalePivotPreservesConfiguredAlignment() {
        assertEquals(20f, LyricUiLayoutPolicy.horizontalScalePivot(
                LyricUiConfig.ALIGN_START, false, 20f, 300f), 0.0001f);
        assertEquals(170f, LyricUiLayoutPolicy.horizontalScalePivot(
                LyricUiConfig.ALIGN_CENTER, false, 20f, 300f), 0.0001f);
        assertEquals(320f, LyricUiLayoutPolicy.horizontalScalePivot(
                LyricUiConfig.ALIGN_END, false, 20f, 300f), 0.0001f);
        assertEquals(320f, LyricUiLayoutPolicy.horizontalScalePivot(
                LyricUiConfig.ALIGN_START, true, 20f, 300f), 0.0001f);
        assertEquals(20f, LyricUiLayoutPolicy.horizontalScalePivot(
                LyricUiConfig.ALIGN_END, true, 20f, 300f), 0.0001f);
    }

    @Test
    public void rowScalePivotUsesMeasuredSizeBeforeTheFirstLayout() {
        assertEquals(320, LyricUiLayoutPolicy.resolvedViewDimension(320, 280));
        assertEquals(280, LyricUiLayoutPolicy.resolvedViewDimension(0, 280));
        assertEquals(0, LyricUiLayoutPolicy.resolvedViewDimension(0, -1));
    }

    @Test
    public void opticalCenterUsesVisibleInkBoundsAroundContentCenter() {
        assertEquals(118f, LyricUiLayoutPolicy.opticallyCenteredBaselineX(
                20f, 300f, 2f, 102f), 0.0001f);
        assertEquals(125f, LyricUiLayoutPolicy.opticallyCenteredBaselineX(
                20f, 300f, -5f, 95f), 0.0001f);
    }

    @Test
    public void floatingPreviewUsesTheBoundaryInRootCoordinates() {
        assertEquals(420f, LyricUiLayoutPolicy.floatingPreviewTopInRoot(
                415f, 0f, 5f), 0.0001f);
        assertEquals(320f, LyricUiLayoutPolicy.floatingPreviewTopInRoot(
                415f, 100f, 5f), 0.0001f);
        assertEquals(5f, LyricUiLayoutPolicy.floatingPreviewTopInRoot(
                80f, 100f, 5f), 0.0001f);
    }
}
