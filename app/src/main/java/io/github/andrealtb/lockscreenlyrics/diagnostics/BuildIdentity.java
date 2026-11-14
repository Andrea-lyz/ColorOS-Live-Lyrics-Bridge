package io.github.andrealtb.lockscreenlyrics.diagnostics;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipFile;

import io.github.andrealtb.lockscreenlyrics.BuildConfig;

/** C-01: baked code identity and installed component observations are deliberately separate. */
public final class BuildIdentity {
    public static final String UNKNOWN = "unknown";
    public static final String RUNTIME_BUILD_ID = "cll_runtime_build_id";
    public static final String RUNTIME_PROCESS = "cll_runtime_process";
    public static final String RUNTIME_EPOCH = "cll_runtime_epoch";
    public static final String RUNTIME_FRAMEWORK = "cll_runtime_framework";
    public static final String RUNTIME_API = "cll_runtime_api";
    public static final String RUNTIME_PROPERTIES = "cll_runtime_properties";
    public static final String UI_CONFIG_REVISION = "cll_ui_config_revision";
    private static final String IDENTITY_ENTRY = "META-INF/cll/build-identity.properties";
    private static final Map<String, String> HASH_CACHE = new LinkedHashMap<>(16, .75f, true);

    private BuildIdentity() { }

    /** Called only by a lifecycle-owned settings worker, never from injected draw/bootstrap code. */
    public static Snapshot capture(Context context) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Component fingerprints require a background worker");
        }
        Map<String, String> facts = new LinkedHashMap<>();
        PackageManager manager = context.getPackageManager();
        facts.put("settings_code_build", BuildConfig.BUILD_ID);
        facts.put("source_commit", BuildConfig.SOURCE_COMMIT);
        facts.put("source_state", BuildConfig.SOURCE_STATE);
        facts.put("source_digest", BuildConfig.SOURCE_WORKTREE);
        facts.put("variant", BuildConfig.BUILD_TYPE);
        facts.put("resolver_revision", BuildConfig.RESOLVER_REVISION);
        facts.put("version", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        facts.put("model", safe(Build.MODEL));
        facts.put("os_build", safe(Build.DISPLAY));
        facts.put("android_release", safe(Build.VERSION.RELEASE));
        facts.put("sdk", Integer.toString(Build.VERSION.SDK_INT));
        facts.put("coloros_version", vendorVersion()); // Optional raw vendor property, never inferred from SDK.
        Configuration config = context.getResources().getConfiguration();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        facts.put("font_scale", Float.toString(config.fontScale));
        facts.put("density_dpi", Integer.toString(metrics.densityDpi));
        facts.put("resolution", metrics.widthPixels + "x" + metrics.heightPixels);
        facts.put("orientation", Integer.toString(config.orientation));
        facts.put("fold_posture", UNKNOWN); // No fold posture observer exists in this baseline.
        facts.put("aod", UNKNOWN); // Only the injected renderer can observe its actual AOD mode.
        String dozeAlwaysOn = UNKNOWN;
        try {
            // Raw AOSP key only; ColorOS may use vendor keys, so this is never mapped to "AOD on".
            String raw = Settings.Secure.getString(context.getContentResolver(), "doze_always_on");
            if (raw != null && raw.matches("[0-9]{1,2}")) dozeAlwaysOn = raw;
        } catch (RuntimeException ignored) { }
        facts.put("aosp_doze_always_on_raw", dozeAlwaysOn);
        String navigation = UNKNOWN;
        try {
            String raw = Settings.Secure.getString(context.getContentResolver(), "navigation_mode");
            if (raw != null && raw.matches("[0-9]{1,2}")) navigation = raw;
        } catch (RuntimeException ignored) { }
        facts.put("navigation_mode_raw", navigation);
        String installed = UNKNOWN;
        try {
            ApplicationInfo info = manager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0);
            try (ZipFile apk = new ZipFile(info.sourceDir)) {
                java.util.zip.ZipEntry entry = apk.getEntry(IDENTITY_ENTRY);
                if (entry != null && entry.getSize() <= 8192L) {
                    Properties properties = new Properties();
                    try (InputStream input = apk.getInputStream(entry)) { properties.load(input); }
                    installed = safe(properties.getProperty("buildId"));
                }
            }
        } catch (Exception ignored) { }
        facts.put("installed_build", installed);
        facts.put("systemui", component(manager, "com.android.systemui"));
        facts.put("plugin", component(manager, "com.oplus.systemui.plugins"));
        facts.put("component_scope", "installed_package_bytes_not_loaded_classloader");
        return new Snapshot(facts);
    }

    private static String vendorVersion() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method getter = properties.getDeclaredMethod("get", String.class, String.class);
            for (String key : new String[]{"ro.build.version.oplusrom", "ro.build.version.opporom"}) {
                Object value = getter.invoke(null, key, "");
                if (value instanceof String && ((String) value).matches("[A-Za-z0-9_.()/-]{1,80}")) return (String) value;
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) { }
        return UNKNOWN;
    }

    @SuppressWarnings("deprecation")
    private static String component(PackageManager manager, String packageName) {
        try {
            PackageInfo info = manager.getPackageInfo(packageName, 0);
            ApplicationInfo app = info.applicationInfo;
            if (app == null || app.sourceDir == null) return UNKNOWN;
            List<String> paths = new ArrayList<>();
            paths.add(app.sourceDir);
            if (app.splitSourceDirs != null) Collections.addAll(paths, app.splitSourceDirs);
            List<String> hashes = new ArrayList<>();
            for (String path : paths) hashes.add(sha256(new File(path)));
            Collections.sort(hashes); // Split paths/order are not stable component identity.
            if (hashes.contains(UNKNOWN)) return safe(info.versionName) + "; fingerprint=" + UNKNOWN;
            MessageDigest combined = MessageDigest.getInstance("SHA-256");
            for (String hash : hashes) combined.update((hash + "\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            long version = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return safe(info.versionName) + " (" + version + "); content_set_sha256=" + hex(combined.digest())
                    + "; apk_count=" + paths.size();
        } catch (Exception ignored) {
            // Not visible, denied, absent and unreadable are all unknown here, never "unsupported".
            return UNKNOWN;
        }
    }

    static String sha256(File file) {
        String key = file.getAbsolutePath() + ":" + file.length() + ":" + file.lastModified();
        synchronized (HASH_CACHE) {
            String cached = HASH_CACHE.get(key);
            if (cached != null) return cached;
        }
        try (InputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[32 * 1024];
            int length;
            while ((length = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) return UNKNOWN;
                digest.update(buffer, 0, length);
            }
            String hash = hex(digest.digest());
            synchronized (HASH_CACHE) {
                HASH_CACHE.put(key, hash);
                while (HASH_CACHE.size() > 16) HASH_CACHE.remove(HASH_CACHE.keySet().iterator().next());
            }
            return hash;
        } catch (Exception ignored) { return UNKNOWN; }
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = digits[(bytes[i] >>> 4) & 15];
            result[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(result);
    }

    /** Safe to call from a protected status reply: constants and already captured observations only. */
    public static void putRuntimeStatus(Bundle result, String process, String epoch,
            String framework, int api, long properties, long uiRevision) {
        result.putString(RUNTIME_BUILD_ID, BuildConfig.BUILD_ID);
        result.putString(RUNTIME_PROCESS, safe(process));
        result.putString(RUNTIME_EPOCH, safe(epoch));
        result.putString(RUNTIME_FRAMEWORK, safe(framework));
        result.putInt(RUNTIME_API, api);
        result.putLong(RUNTIME_PROPERTIES, properties);
        result.putLong(UI_CONFIG_REVISION, uiRevision);
    }

    public static String runtimeReport(Bundle result) {
        return runtimeReport(result, UNKNOWN);
    }

    public static String runtimeReport(Bundle result, String installedBuildId) {
        if (result == null) return "注入进程构建：unknown（未观测；不等于未启用）";
        String id = result.getString(RUNTIME_BUILD_ID, UNKNOWN);
        return "注入进程构建：" + id
                + "\n安装/注入对照：" + compare(installedBuildId, id)
                + "\n进程：" + result.getString(RUNTIME_PROCESS, UNKNOWN)
                + " · epoch=" + result.getString(RUNTIME_EPOCH, UNKNOWN)
                + "\n框架：" + result.getString(RUNTIME_FRAMEWORK, UNKNOWN)
                + " · API=" + result.getInt(RUNTIME_API, -1)
                + " · properties=" + result.getLong(RUNTIME_PROPERTIES, -1L)
                + "\n外观配置已应用 revision：" + result.getLong(UI_CONFIG_REVISION, -1L)
                + "（-1 为未观测；与 Debug revision 分开）"
                + "\n渲染器观测 AOD 低帧率模式："
                + result.getString("cll_observed_aod_low_frame_rate", UNKNOWN)
                + " · 渲染器切换次数=" + result.getInt(AodModeObservation.TRANSITIONS, -1)
                + " · 最近切换 elapsed=" + result.getLong(AodModeObservation.CHANGED_AT, -1L)
                + "（-1 为旧进程或未观测；低帧率模式由灭屏广播与绘制路径驱动，不等于 AOD 画面已显示）";
    }

    public static String compare(String installed, String observed) {
        if (installed == null || observed == null || UNKNOWN.equals(installed) || UNKNOWN.equals(observed)
                || installed.isEmpty() || observed.isEmpty()) return "unknown";
        return installed.equals(observed) ? "MATCH" : "MISMATCH（需核对旧注入进程）";
    }

    public static String format(Snapshot snapshot) {
        StringBuilder result = new StringBuilder("构建/环境快照（App 侧后台采集）");
        for (Map.Entry<String, String> entry : snapshot.facts.entrySet()) {
            result.append('\n').append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return result.toString();
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return UNKNOWN;
        return value.replace('\n', ' ').replace('\r', ' ').substring(0, Math.min(value.length(), 1024));
    }

    public static final class Snapshot {
        public final Map<String, String> facts;
        public Snapshot(Map<String, String> facts) {
            this.facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
        }
    }
}
