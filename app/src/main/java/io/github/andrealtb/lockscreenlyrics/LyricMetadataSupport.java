package io.github.andrealtb.lockscreenlyrics;

import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;
import static io.github.andrealtb.lockscreenlyrics.LyricLogDescriptions.*;
import static io.github.andrealtb.lockscreenlyrics.LyricsRecyclerViews.*;

import android.app.KeyguardManager;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.app.Notification;
import android.service.notification.StatusBarNotification;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import org.json.JSONObject;
import io.github.andrealtb.lockscreenlyrics.render.LineTimedRevealPolicy;
import io.github.andrealtb.lockscreenlyrics.render.CachedDrawFrame;
import io.github.andrealtb.lockscreenlyrics.render.CharLiftGeometry;
import io.github.andrealtb.lockscreenlyrics.render.DrawFrame;
import io.github.andrealtb.lockscreenlyrics.render.LyricDrawLine;
import io.github.andrealtb.lockscreenlyrics.render.LyricDrawLayoutEngine;
import io.github.andrealtb.lockscreenlyrics.render.LyricTimingMode;
import io.github.andrealtb.lockscreenlyrics.render.LyricsRecyclerGeometry;
import io.github.andrealtb.lockscreenlyrics.render.OfficialLyricFrameResolver;
import io.github.andrealtb.lockscreenlyrics.diagnostics.CompatibilityStatus;
import io.github.andrealtb.lockscreenlyrics.render.OfficialLyricDataset;
import io.github.andrealtb.lockscreenlyrics.render.LyricRenderTransactions;
import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderConstants;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderSupport;
import io.github.andrealtb.lockscreenlyrics.render.WordRange;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import io.github.andrealtb.lockscreenlyrics.bootstrap.SystemUiRuntimeBootstrap;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.DynamicArtworkRuntime;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkTrace;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugApplyPolicy;
import io.github.andrealtb.lockscreenlyrics.SystemUiPlaybackController.PlaybackControllerBinding;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugArea;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugConfig;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeEvents;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgePerformanceSampler;
import io.github.andrealtb.lockscreenlyrics.diagnostics.ArtworkDiagnostics;
import io.github.andrealtb.lockscreenlyrics.diagnostics.SensitiveFieldRedactor;
import io.github.andrealtb.lockscreenlyrics.diagnostics.StructuredBridgeLog;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoCoverPolicy;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoMediaIdentityPolicy;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoPluginMediaModelReader;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoSameTrackLyricRetention;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoSystemUiRuntime;
import io.github.andrealtb.lockscreenlyrics.systemui.lyrics.LyricsRecyclerFieldAccessor;
import io.github.andrealtb.lockscreenlyrics.systemui.lyrics.LyricsRecyclerPolicy;
import io.github.andrealtb.lockscreenlyrics.systemui.lyrics.SystemUiLoadLyricPolicy;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Stateless metadata, payload and lyric-text helpers (F-2b, plan §15.8.19): track keys, model
 * signatures, MediaMetadata identity and artwork copies, metadata argument lookup, loose text
 * normalisation, package-name checks and short-line tests. Moved verbatim from
 * {@code LockscreenLyricsModule}; every member is static and stateless.
 */
final class LyricMetadataSupport {
    private LyricMetadataSupport() {
    }

    static boolean isKuwoSeedlingData(Object data) {
        try {
            return KuWoMediaIdentityPolicy.isPlayerPackage(
                    invokeStringGetter(data, "getPackageName"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isLxSeedlingData(Object data) {
        return data != null && isLxPlayerPackage(invokeStringGetter(data, "getPackageName"));
    }

    static Object metadataChangedOwner(XposedInterface.Chain chain, Object[] args) {
        Object owner = chain.getThisObject();
        if (owner != null) {
            return owner;
        }
        int metadataIndex = metadataArgIndex(args);
        if (metadataIndex >= 2 && args[0] != null && args[metadataIndex - 1] instanceof String) {
            return args[0];
        }
        return null;
    }

    static int metadataArgIndex(Object[] args) {
        int index = -1;
        for (int i = 0; i < args.length; i++) {
            if (!(args[i] instanceof MediaMetadata)) {
                continue;
            }
            if (index >= 0) {
                return -1;
            }
            index = i;
        }
        if (index < 1 || !(args[index - 1] instanceof String)) {
            return -1;
        }
        return index;
    }

    static boolean isNativeV5Payload(LyricInfoContract.Payload payload) {
        return payload != null;
    }

    static String findPlayerPackageInArgs(List<Object> args) {
        for (int i = 0; i < args.size(); i++) {
            Object arg = args.get(i);
            if (arg instanceof String && looksLikePackageName((String) arg)) {
                return (String) arg;
            }
        }
        return "";
    }

    static boolean looksLikePackageName(String value) {
        if (TextUtils.isEmpty(value) || !value.equals(value.toLowerCase(Locale.ROOT))) {
            return false;
        }
        int dots = 0;
        boolean segmentHasCharacter = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '.') {
                if (!segmentHasCharacter) {
                    return false;
                }
                dots++;
                segmentHasCharacter = false;
            } else if (Character.isLetterOrDigit(character) || character == '_') {
                segmentHasCharacter = true;
            } else {
                return false;
            }
        }
        return dots >= 1 && segmentHasCharacter;
    }

    static boolean payloadMatchesTrack(
            LyricInfoContract.Payload payload, String title, String artist) {
        return LyricInfoTrackMatcher.payloadMatchesTrack(payload, title, artist);
    }

    static String payloadTrackKey(LyricInfoContract.Payload payload) {
        if (payload == null) {
            return "";
        }
        if (!TextUtils.isEmpty(payload.trackKey)) {
            return payload.trackKey;
        }
        if (TextUtils.isEmpty(payload.songName)) {
            return "";
        }
        return buildTrackKey(payload.songName, payload.artist);
    }

    static String charSequenceToString(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    static int charSequenceContentHash(CharSequence value) {
        if (value == null) {
            return 0;
        }
        int hash = 0;
        for (int i = 0; i < value.length(); i++) {
            hash = 31 * hash + value.charAt(i);
        }
        return hash;
    }

    static int countLyricModelLines(Object lyricModel) {
        if (lyricModel == null) {
            return 0;
        }
        Class<?> current = lyricModel.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() != List.class) {
                    continue;
                }
                Object value = readField(lyricModel, field);
                if (value instanceof List) {
                    return ((List<?>) value).size();
                }
            }
            current = current.getSuperclass();
        }
        return 0;
    }

    static WordLine firstLineBeforeDisplayStart(WordLyricModel model, long position) {
        if (model == null || position < 0L || model.lines.isEmpty()) {
            return null;
        }
        WordLine firstLine = model.firstDisplayLine();
        if (firstLine == null || firstLine.timeMillis <= position) {
            return null;
        }
        return firstLine;
    }

    static boolean hasCustomAction(PlaybackState state, String actionId) {
        if (state == null || TextUtils.isEmpty(actionId)) {
            return false;
        }
        List<PlaybackState.CustomAction> actions = state.getCustomActions();
        if (actions == null) {
            return false;
        }
        for (PlaybackState.CustomAction action : actions) {
            if (action != null && actionId.equals(action.getAction())) {
                return true;
            }
        }
        return false;
    }

    static void copyMediaMetadataArtwork(
            MediaMetadata source,
            MediaMetadata.Builder target) {
        if (source == null || target == null) {
            return;
        }
        boolean hasArtworkUri = false;
        String[] uriKeys = {
                MediaMetadata.METADATA_KEY_ART_URI,
                MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
        };
        for (String key : uriKeys) {
            if (!TextUtils.isEmpty(source.getString(key))) {
                hasArtworkUri = true;
                break;
            }
        }
        String[] bitmapKeys = {
                MediaMetadata.METADATA_KEY_ART,
                MediaMetadata.METADATA_KEY_ALBUM_ART,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON
        };
        for (String key : bitmapKeys) {
            Bitmap bitmap = source.getBitmap(key);
            if (bitmap != null) {
                if (hasArtworkUri && bitmap.getWidth() <= 1 && bitmap.getHeight() <= 1) {
                    target.putBitmap(key, null);
                } else {
                    target.putBitmap(key, bitmap);
                }
            }
        }
        for (String key : uriKeys) {
            String uri = source.getString(key);
            if (!TextUtils.isEmpty(uri)) {
                target.putString(key, uri);
            }
        }
    }

    static String buildTrackKey(String title, String artist) {
        return TrackIdentity.buildKey(title, artist);
    }

    static String buildWordLyricModelSignature(
            LyricInfoContract.Payload payload,
            String rawLyric,
            String translationLyric) {
        if (payload == null) {
            return "";
        }
        return firstNonEmpty(payload.trackKey, payload.songId)
                + '|'
                + contentSignature(payload.lyric)
                + '|'
                + contentSignature(rawLyric)
                + '|'
                + contentSignature(translationLyric)
                // Appended only when present so signatures of payloads without TTML are unchanged.
                + (payload.ttml.isEmpty() ? "" : '|' + contentSignature(payload.ttml));
    }

    static String contentSignature(String value) {
        if (value == null) {
            return "0:0";
        }
        return value.length() + ":" + Integer.toHexString(value.hashCode());
    }

    static String sanitizeForOplusLyric(String rawLyric) {
        return OplusLyricNormalizer.normalizeForOfficialList(rawLyric);
    }

    static String normalizeLooseMetadataText(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char character = trimmed.charAt(i);
            if (Character.isLetterOrDigit(character) || character > 0x7F) {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    static boolean isShortStandaloneLyricLine(WordLine line) {
        return line != null && isShortStandaloneLyricText(line.text);
    }

    static boolean isShortStandaloneLyricText(String text) {
        String normalized = WordLyricRenderSupport.normalizeLine(text);
        if (TextUtils.isEmpty(normalized) || normalized.length() > 18) {
            return false;
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (Character.isWhitespace(normalized.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
