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

// Moved verbatim out of LockscreenLyricsModule (E-2a, plan §15.8.3); the static import keeps
// the bodies unchanged.
import static io.github.andrealtb.lockscreenlyrics.LockscreenLyricsModule.*;

/** Last row a ColorOS 17 recycler was sent, with a cheap identity of the lines it applies to. */
final class ImmersiveEntryIndex {
    final int index;
    final int lineCount;
    final long firstStartMs;
    final long lastStartMs;

    ImmersiveEntryIndex(int index, long[] startTimes) {
        this.index = index;
        this.lineCount = startTimes == null ? 0 : startTimes.length;
        this.firstStartMs = lineCount == 0 ? -1L : startTimes[0];
        this.lastStartMs = lineCount == 0 ? -1L : startTimes[lineCount - 1];
    }

    boolean sameLines(long[] startTimes) {
        int count = startTimes == null ? 0 : startTimes.length;
        return count > 0
                && count == lineCount
                && startTimes[0] == firstStartMs
                && startTimes[count - 1] == lastStartMs;
    }
}
