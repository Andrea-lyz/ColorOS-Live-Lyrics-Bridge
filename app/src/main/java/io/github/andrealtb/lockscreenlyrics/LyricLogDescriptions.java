package io.github.andrealtb.lockscreenlyrics;

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

// Entry-class statics the moved bodies use unqualified.
import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;

/**
 * Text for diagnostic log lines: views, layout params, artwork, probe results and lyric-model
 * windows. Pure functions of their arguments; nothing here reads runtime state. Moved verbatim
 * out of {@code LockscreenLyricsModule} in E-2c (plan §15.8.6).
 */
final class LyricLogDescriptions {
    private LyricLogDescriptions() {
    }

    static String describeViewForLog(View view) {
        if (view == null) {
            return "null";
        }
        return view.getClass().getName()
                + "{attached=" + view.isAttachedToWindow()
                + ", visibility=" + view.getVisibility()
                + ", windowVisibility=" + view.getWindowVisibility()
                + ", alpha=" + view.getAlpha()
                + ", size=" + view.getWidth() + "x" + view.getHeight()
                + ", measured=" + view.getMeasuredWidth() + "x" + view.getMeasuredHeight()
                + ", pos=" + view.getX() + "," + view.getY()
                + ", top=" + view.getTop()
                + ", left=" + view.getLeft()
                + ", scroll=" + view.getScrollX() + "," + view.getScrollY()
                + ", translation=" + view.getTranslationX() + "," + view.getTranslationY()
                + ", layout=" + describeLayoutParamsForLog(view)
                + "}";
    }

    static String describeLayoutParamsForLog(View view) {
        if (view == null) {
            return "null";
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) {
            return "null";
        }
        String value = params.width + "x" + params.height;
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            value += ",margins="
                    + margins.leftMargin + ","
                    + margins.topMargin + ","
                    + margins.rightMargin + ","
                    + margins.bottomMargin;
        }
        return value;
    }

    static String describeSeedlingIcon(Icon icon) {
        if (icon == null) {
            return "null";
        }
        Bitmap bitmap = extractIconBitmap(icon);
        return bitmap == null
                ? "icon"
                : bitmap.getWidth() + "x" + bitmap.getHeight()
                        + ":generation-" + bitmap.getGenerationId()
                        + ":identity-" + System.identityHashCode(bitmap);
    }

    /**
     * A null list is the vendor's empty model ({@code h()} delegates to {@code h(null,-1)}),
     * so it must be reported instead of being filtered out as an unreadable probe.
     */
    static String describeProbeLines(Object lines) {
        if (lines == PROBE_FIELD_MISSING) {
            return "unreadable";
        }
        if (lines == null) {
            return "null";
        }
        if (lines instanceof List) {
            return "count=" + ((List<?>) lines).size();
        }
        return "type=" + lines.getClass().getSimpleName();
    }

    static String describeMetadataArtwork(MediaMetadata metadata) {
        if (metadata == null) return "metadata-null";
        Bitmap bitmap = firstNonNullBitmap(
                metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
                metadata.getBitmap(MediaMetadata.METADATA_KEY_ART),
                metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART));
        String uri = firstNonEmpty(
                metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
                metadata.getString(MediaMetadata.METADATA_KEY_ART_URI),
                metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI));
        return (bitmap == null
                ? "bitmap=null"
                : "bitmap=" + bitmap.getWidth() + 'x' + bitmap.getHeight()
                + ":generation-" + bitmap.getGenerationId()
                + ":identity-" + System.identityHashCode(bitmap))
                + ", uri=" + SensitiveFieldRedactor.uriSummary(uri);
    }

    static String describeDecisionLine(WordLyricModel model, WordLine line) {
        if (line == null) {
            return "null";
        }
        return "idx=" + (model == null ? -1 : model.indexOfLine(line))
                + "#"
                + objectId(line)
                + " time=" + LyricModelTraceSupport.formatLrcTime(line.timeMillis)
                + " text=" + shortenForLog(line.text);
    }

    static String describeModelWindow(WordLyricModel model, int centerIndex, int radius) {
        if (model == null || model.lines.isEmpty()) {
            return "";
        }
        int center = Math.max(0, Math.min(model.lines.size() - 1, centerIndex));
        int start = Math.max(0, center - Math.max(0, radius));
        int end = Math.min(model.lines.size() - 1, center + Math.max(0, radius));
        StringBuilder builder = new StringBuilder();
        for (int i = start; i <= end; i++) {
            WordLine line = model.lineAt(i);
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(i)
                    .append("=>")
                    .append(describeDecisionLine(model, line));
        }
        return builder.toString();
    }

    static String shortenForLog(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 77) + "...";
    }

    static BridgeDebugArea mapLogArea(LyricLogFormatter.Area area) {
        if (area == null) {
            return BridgeDebugArea.MEDIA;
        }
        switch (area) {
            case LIFECYCLE:
            case HOOK:
            case SETTINGS:
                return BridgeDebugArea.BOOTSTRAP;
            case PLAYER:
            case TRANSACTION:
            case SYSTEM_UI:
            case SCREEN:
                return BridgeDebugArea.MEDIA;
            case PROVIDER:
            case PARSER:
                return BridgeDebugArea.LYRIC;
            case RENDER:
            case RECYCLER:
                return BridgeDebugArea.RENDERER;
            case AOD:
                return BridgeDebugArea.AOD;
            case TRANSLATION:
                return BridgeDebugArea.PLAYER_SPECIAL;
            default:
                return BridgeDebugArea.MEDIA;
        }
    }
}
