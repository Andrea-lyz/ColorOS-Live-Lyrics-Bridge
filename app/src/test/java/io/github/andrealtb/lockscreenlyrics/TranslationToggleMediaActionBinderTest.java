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
}
