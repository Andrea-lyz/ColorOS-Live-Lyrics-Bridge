package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Stub-based tests for the media-card model operations. The binder reaches the stubs through the
 * same name-based reflection it uses against SystemUI, so the fake classes intentionally mirror
 * OplusMediaButtonEx / MediaAction method names.
 *
 * <p>The PlaybackState.CustomAction-mapped branch needs a real CustomAction instance and is
 * covered by device validation; the fallback locator added for QQ Music / KuGou (original) is
 * fully covered here.</p>
 */
public class TranslationToggleMediaActionBinderTest {

    public static final class FakeMediaAction {
        Runnable action;
        CharSequence contentDescription;
        final FakeMediaActionEx ex = new FakeMediaActionEx();

        public Runnable getAction() {
            return action;
        }

        public void setAction(Runnable action) {
            this.action = action;
        }

        public CharSequence getContentDescription() { return contentDescription; }

        public void setContentDescription(CharSequence value) {
            this.contentDescription = value;
        }

        public Object getIcon() {
            return null;
        }

        public FakeMediaActionEx getMediaActionEx() {
            return ex;
        }
    }

    public static final class FakeMediaActionEx {
        Object icon;
    }

    public static final class FakeMediaButtonEx {
        final List<Object> rule0CustomActions = new ArrayList<>();
        Object heartAction;

        public List<Object> getRule0CustomActions() {
            return rule0CustomActions;
        }

        public void setRule0CustomActions(List<?> actions) {
            rule0CustomActions.clear();
            rule0CustomActions.addAll(actions);
        }

        public Object getHeartAction() {
            return heartAction;
        }
    }

    public static final class FakeMediaButton {
        final FakeMediaButtonEx ex = new FakeMediaButtonEx();

        public FakeMediaButtonEx getMediaButtonEx() {
            return ex;
        }
    }

    private static final class FakeHost implements TranslationToggleMediaActionBinder.Host {
        final Map<String, Boolean> translationEnabled = new HashMap<>();
        int toggleClicks;

        @Override
        public Context currentApplicationContext() {
            return null;
        }

        @Override
        public String logProcessName() {
            return "test";
        }

        @Override
        public boolean isLyricInfoTranslationEnabled(String packageName) {
            return translationEnabled.getOrDefault(packageName, Boolean.TRUE);
        }

        @Override
        public void rememberTranslationIconFingerprint(Drawable drawable) {
        }

        @Override
        public void onTranslationToggleClicked(String packageName) {
            toggleClicks++;
        }
    }

    private static TranslationToggleMediaActionBinder binder(FakeHost host) {
        return new TranslationToggleMediaActionBinder(
                host,
                (context, packageName) ->
                        new TranslationToggleMediaActionBinder.TranslationIcon(
                                new Object(), null));
    }

    @Test
    public void singleActionFallbackBindsTranslationToggleClick() {
        FakeMediaButton button = new FakeMediaButton();
        FakeMediaAction action = new FakeMediaAction();
        button.ex.rule0CustomActions.add(action);
        FakeHost host = new FakeHost();

        binder(host).applyTranslationToggle(
                "com.example.player", button, true, true, "com.example.player", 5, 120);

        assertNotNull(action.action);
        action.action.run();
        assertEquals(1, host.toggleClicks);
        assertEquals("翻译：开启", action.contentDescription.toString());
    }

    @Test
    public void heartMarkerFallbackPromotesFavoriteAction() {
        FakeMediaButton button = new FakeMediaButton();
        FakeMediaAction other = new FakeMediaAction();
        FakeMediaAction heart = new FakeMediaAction();
        button.ex.heartAction = heart;
        button.ex.rule0CustomActions.add(other);
        button.ex.rule0CustomActions.add(heart);
        FakeHost host = new FakeHost();

        binder(host).applyTranslationToggle(
                "com.example.player", button, true, true, "com.example.player", 5, 120);

        assertSame(heart, button.ex.rule0CustomActions.get(0));
        assertNotNull(heart.action);
        assertNull(other.action);
    }

    @Test
    public void overrideSkippedWhenNotAllowedLeavesPlayerActionUntouched() {
        FakeMediaButton button = new FakeMediaButton();
        FakeMediaAction action = new FakeMediaAction();
        button.ex.rule0CustomActions.add(action);
        FakeHost host = new FakeHost();

        binder(host).applyTranslationToggle(
                "com.example.player", button, false, true, "com.example.player", 5, 120);

        assertNull(action.action);
        assertEquals(0, host.toggleClicks);
    }

    @Test
    public void emptyActionListLeavesEverythingUntouched() {
        FakeMediaButton button = new FakeMediaButton();
        FakeHost host = new FakeHost();

        binder(host).applyTranslationToggle(
                "com.example.player", button, true, true, "com.example.player", 5, 120);

        assertEquals(0, host.toggleClicks);
    }

    @Test
    public void missingRule0ActionsAreSkippedQuietly() {
        FakeHost host = new FakeHost();
        Object mediaButton = new Object();

        binder(host).applyTranslationToggle(
                "com.example.player", mediaButton, true, true, "com.example.player", 5, 120);

        assertEquals(0, host.toggleClicks);
    }

    @Test
    public void powerampHeartOverlayBindsVisibleFavoriteWithoutPromotingIntoRule0() {
        FakeMediaButton button = new FakeMediaButton();
        FakeMediaAction shuffle = new FakeMediaAction();
        FakeMediaAction heart = new FakeMediaAction();
        button.ex.rule0CustomActions.add(shuffle);
        button.ex.heartAction = heart;
        FakeHost host = new FakeHost();

        binder(host).bindOplusHeartAlongsidePublicAction(
                button.ex,
                shuffle,
                PlayerSystemUiPolicy.POWERAMP);

        assertEquals(1, button.ex.rule0CustomActions.size());
        assertSame(shuffle, button.ex.rule0CustomActions.get(0));
        assertNull(shuffle.action);
        assertNotNull(heart.action);
        assertEquals("翻译：开启", heart.contentDescription.toString());
        heart.action.run();
        assertEquals(1, host.toggleClicks);
    }

    @Test
    public void powerampHeartOverlaySkipsWhenHeartIsAbsent() {
        FakeMediaButton button = new FakeMediaButton();
        FakeMediaAction publicAction = new FakeMediaAction();
        button.ex.rule0CustomActions.add(publicAction);
        FakeHost host = new FakeHost();

        binder(host).bindOplusHeartAlongsidePublicAction(
                button.ex,
                publicAction,
                PlayerSystemUiPolicy.POWERAMP);

        assertNull(publicAction.action);
        assertEquals(0, host.toggleClicks);
        assertEquals(1, button.ex.rule0CustomActions.size());
    }

    @Test
    public void powerampHeartOverlaySkipsWhenHeartIsThePublicAction() {
        FakeMediaButton button = new FakeMediaButton();
        FakeMediaAction both = new FakeMediaAction();
        button.ex.heartAction = both;
        button.ex.rule0CustomActions.add(both);
        FakeHost host = new FakeHost();

        binder(host).bindOplusHeartAlongsidePublicAction(
                button.ex,
                both,
                PlayerSystemUiPolicy.POWERAMP);

        assertNull(both.action);
        assertEquals(0, host.toggleClicks);
    }

    @Test
    public void onlyResourceBackedIconsAreSafeForOplusSemanticButtons() {
        assertTrue(TranslationToggleMediaActionBinder.isSystemUiSafeSemanticIconType(
                Icon.TYPE_RESOURCE));
        assertFalse(TranslationToggleMediaActionBinder.isSystemUiSafeSemanticIconType(
                Icon.TYPE_BITMAP));
        assertFalse(TranslationToggleMediaActionBinder.isSystemUiSafeSemanticIconType(
                Icon.TYPE_ADAPTIVE_BITMAP));
        assertFalse(TranslationToggleMediaActionBinder.isSystemUiSafeSemanticIconType(
                Icon.TYPE_DATA));
        assertFalse(TranslationToggleMediaActionBinder.isSystemUiSafeSemanticIconType(
                Icon.TYPE_URI));
    }

    @Test
    public void failedSemanticIconWriteDoesNotPromoteOrReplaceAction() {
        FakeMediaButton button = new FakeMediaButton();
        FakeMediaAction first = new FakeMediaAction();
        Object missingSemanticField = new Object() {
            public Runnable getAction() { return null; }
            public Object getIcon() { return null; }
            public Object getMediaActionEx() { return new Object(); }
        };
        button.ex.heartAction = missingSemanticField;
        button.ex.rule0CustomActions.add(first);
        button.ex.rule0CustomActions.add(missingSemanticField);
        FakeHost host = new FakeHost();

        binder(host).applyTranslationToggle(
                "com.example.player", button, true, true, "com.example.player", 5, 120);

        assertSame(first, button.ex.rule0CustomActions.get(0));
        assertEquals(2, button.ex.rule0CustomActions.size());
    }
    private static final class NativeAction implements Runnable {
        final String id;
        int clicks;
        NativeAction(String id) { this.id = id; }
        @Override public void run() { clicks++; }
    }

    private static TranslationToggleMediaActionBinder nativeBinder(FakeHost host) {
        return new TranslationToggleMediaActionBinder(host,
                (context, pkg) -> new TranslationToggleMediaActionBinder.TranslationIcon(new Object(), null),
                action -> {
                    Runnable runnable = ((FakeMediaAction) action).action;
                    return runnable instanceof NativeAction ? ((NativeAction) runnable).id : "";
                });
    }

    private static FakeMediaAction nativeAction(String id) {
        FakeMediaAction action = new FakeMediaAction();
        action.action = new NativeAction(id);
        action.contentDescription = id;
        action.ex.icon = new Object();
        return action;
    }

    @Test
    public void publicActionCanBeDisabledAndReenabledOnSameModel() {
        FakeHost host = new FakeHost();
        TranslationToggleMediaActionBinder binder = nativeBinder(host);
        FakeMediaButton button = new FakeMediaButton();
        FakeMediaAction desktop = nativeAction("desktop");
        FakeMediaAction translation = nativeAction(LyricInfoContract.ACTION_TOGGLE_TRANSLATION);
        FakeMediaAction favorite = nativeAction("favorite");
        button.ex.rule0CustomActions.addAll(List.of(desktop, translation, favorite));
        for (int i = 0; i < 3; i++) {
            binder.applyTranslationToggle("example", button, false, true, "example", 5, 20);
            assertSame(translation, button.ex.rule0CustomActions.get(0));
            translation.action.run();
            binder.applyTranslationToggle("example", button, false, false, "example", 5, 20);
            assertEquals(List.of(desktop, favorite), button.ex.rule0CustomActions);
        }
        assertEquals(3, host.toggleClicks);
    }

    @Test
    public void disablingOverrideRestoresOriginalClickIconDescriptionAndOrder() {
        FakeHost host = new FakeHost();
        TranslationToggleMediaActionBinder binder = nativeBinder(host);
        FakeMediaButton button = new FakeMediaButton();
        FakeMediaAction desktop = nativeAction("com.md3music.toggle_desktop_lyric");
        FakeMediaAction favorite = nativeAction("com.md3music.toggle_favorite");
        NativeAction original = (NativeAction) desktop.action;
        Object icon = desktop.ex.icon;
        button.ex.rule0CustomActions.addAll(List.of(desktop, favorite));
        String pkg = PlayerSystemUiPolicy.MD3_MUSIC;
        binder.applyTranslationToggle(pkg, button, true, true, pkg, 5, 20);
        desktop.action.run();
        assertEquals(1, host.toggleClicks);
        binder.applyTranslationToggle(pkg, button, true, false, pkg, 5, 20);
        assertSame(original, desktop.action);
        assertSame(icon, desktop.ex.icon);
        assertEquals(original.id, desktop.contentDescription);
        assertEquals(List.of(desktop, favorite), button.ex.rule0CustomActions);
        desktop.action.run();
        assertEquals(1, original.clicks);
        binder.applyTranslationToggle(pkg, button, true, true, pkg, 5, 20);
        binder.applyTranslationToggle(pkg, button, false, true, pkg, 0, 0);
        assertSame(original, desktop.action);
    }

    @Test
    public void disabledButtonNeverTakesOverAnUntouchedFavorite() {
        FakeMediaButton button = new FakeMediaButton();
        FakeMediaAction favorite = nativeAction("favorite");
        Runnable original = favorite.action;
        button.ex.rule0CustomActions.add(favorite);
        nativeBinder(new FakeHost()).applyTranslationToggle("example", button, true, false, "example", 5, 20);
        assertSame(original, favorite.action);
    }

    @Test
    public void md3NeverFallsBackToFavoriteWhenDesktopActionIsMissing() {
        FakeMediaButton button = new FakeMediaButton();
        FakeMediaAction favorite = nativeAction("com.md3music.toggle_favorite");
        Runnable original = favorite.action;
        button.ex.rule0CustomActions.add(favorite);
        button.ex.heartAction = favorite;
        String pkg = PlayerSystemUiPolicy.MD3_MUSIC;
        nativeBinder(new FakeHost()).applyTranslationToggle(pkg, button, true, true, pkg, 5, 20);
        assertSame(original, favorite.action);
    }

    @Test
    public void knownEmptyModelRemovesPublicActionButUnknownModelKeepsIt() {
        FakeMediaButton button = new FakeMediaButton();
        FakeMediaAction desktop = nativeAction("desktop");
        FakeMediaAction translation = nativeAction(LyricInfoContract.ACTION_TOGGLE_TRANSLATION);
        button.ex.rule0CustomActions.addAll(List.of(desktop, translation));
        TranslationToggleMediaActionBinder binder = nativeBinder(new FakeHost());
        binder.applyTranslationToggle("example", button, false, true, "example", 0, 0);
        assertEquals(List.of(desktop), button.ex.rule0CustomActions);
        binder.applyTranslationToggle("example", button, false, true, "example", -1, -1);
        assertSame(translation, button.ex.rule0CustomActions.get(0));
    }

    @Test
    public void md3PublicTranslationReplacesDesktopSlotAndKeepsFavoriteSecond() {
        FakeMediaButton button = new FakeMediaButton();
        FakeMediaAction desktop = nativeAction("com.md3music.toggle_desktop_lyric");
        FakeMediaAction translation = nativeAction(LyricInfoContract.ACTION_TOGGLE_TRANSLATION);
        FakeMediaAction favorite = nativeAction("com.md3music.toggle_favorite");
        button.ex.rule0CustomActions.addAll(List.of(desktop, translation, favorite));
        TranslationToggleMediaActionBinder binder = nativeBinder(new FakeHost());
        String pkg = PlayerSystemUiPolicy.MD3_MUSIC;
        binder.applyTranslationToggle(pkg, button, true, true, pkg, 5, 20);
        assertEquals(List.of(translation, favorite), button.ex.rule0CustomActions);
        binder.applyTranslationToggle(pkg, button, false, true, pkg, 0, 0);
        assertEquals(List.of(desktop, favorite), button.ex.rule0CustomActions);
    }

}
