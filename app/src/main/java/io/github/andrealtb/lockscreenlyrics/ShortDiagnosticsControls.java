package io.github.andrealtb.lockscreenlyrics;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Settings-owned controls for the opt-in recorder. No player commands and no automatic retries. */
final class ShortDiagnosticsControls implements AutoCloseable {
    private static final int EXPORT = 0x5344;
    private final WeakReference<Activity> activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Supplier<Map<String, String>> environment;
    private TextView state;
    private String session = "unknown", json = "";
    private int generation;
    private boolean closed;
    ShortDiagnosticsControls(Activity activity, Supplier<Map<String, String>> environment) {
        this.activity = new WeakReference<>(activity); this.environment = environment;
    }
    LinearLayout create(Bundle saved) {
        Activity owner = activity.get();
        if (saved != null) { session = saved.getString("short_diag_session", session); json = saved.getString("short_diag_json", json); }
        LinearLayout root = new LinearLayout(owner); root.setOrientation(LinearLayout.VERTICAL);
        state = new TextView(owner); state.setTextSize(13f); state.setTextIsSelectable(true);
        state.setText("短时诊断默认关闭。仅记录最多 60 秒的能力状态/通用标记，不记录歌词、媒体身份或私人路径。\n不自动开启日志开关，不改变增强行为。"); root.addView(state);
        button(root, "开始新的 60 秒诊断（替换上轮缓存）", () -> command(LyricUiSettings.ACTION_DIAGNOSTIC_START));
        button(root, "标记当前问题（不输入私人内容）", () -> command(LyricUiSettings.ACTION_DIAGNOSTIC_MARK));
        button(root, "停止并读取诊断快照", () -> command(LyricUiSettings.ACTION_DIAGNOSTIC_STOP));
        button(root, "只读取当前快照", () -> command(LyricUiSettings.ACTION_DIAGNOSTIC_SNAPSHOT));
        button(root, "导出诊断 JSON（先预览）", () -> {
            Activity current = activity.get(); if (current == null || json.isEmpty()) return;
            current.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json").putExtra(Intent.EXTRA_TITLE, "bridge-runtime-diagnostics.json"), EXPORT);
        });
        return root;
    }
    private void button(LinearLayout root, String text, Runnable click) {
        Button button = new Button(root.getContext()); button.setText(text); button.setOnClickListener(v -> click.run()); root.addView(button);
    }
    private void command(String action) {
        Activity owner = activity.get(); if (owner == null || closed) return;
        int request = ++generation;
        state.setText("正在请求已加载 SystemUI 诊断；没有回包将保留 unknown，不重启或启动任何播放器。");
        ResultReceiver callback = new ResultReceiver(main) {
            @Override protected void onReceiveResult(int code, Bundle data) {
                if (closed || request != generation || state == null || data == null || code != 0) return;
                generation++;
                try {
                    if (data.getInt("schema", -1) != 1) throw new IllegalArgumentException();
                    String received = data.getString("diagnostic_json", "");
                    if (received.length() > 128 * 1024) throw new IllegalArgumentException();
                    JSONObject document = new JSONObject(received);
                    if (!"cll-bridge-diagnostics-v1".equals(document.optString("schema"))) throw new IllegalArgumentException();
                    session = data.getString("sessionId", "unknown");
                    JSONObject app = new JSONObject();
                    if (environment != null) {
                        Map<String, String> facts = environment.get();
                        for (String key : new String[]{"settings_code_build", "source_commit", "source_state", "source_digest",
                                "variant", "resolver_revision", "version", "model", "os_build", "android_release", "sdk",
                                "coloros_version", "font_scale", "density_dpi", "resolution", "orientation", "fold_posture",
                                "aod", "aosp_doze_always_on_raw", "navigation_mode_raw", "installed_build", "systemui", "plugin", "component_scope",
                                "hinge_sensor", "hinge_angle_degrees"}) {
                            if (facts.containsKey(key)) app.put(key, facts.get(key));
                        }
                    }
                    document.put("appEnvironment", app); json = document.toString(2);
                    state.setText(data.getString("status", "unknown") + "\n会话：" + session
                            + "\n以下仅为运行观测，不是视觉验收。可先检查后再导出。\n" + json);
                } catch (RuntimeException | org.json.JSONException incompatible) {
                    state.setText("诊断：unknown（旧进程或回包不兼容）");
                }
            }
        };
        Intent intent = new Intent(action).setPackage("com.android.systemui")
                .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(LyricUiSettings.EXTRA_DIAGNOSTIC_SESSION, session)
                .putExtra(LyricUiSettings.EXTRA_RESULT_RECEIVER, ResultReceiverTransport.forRemoteProcess(callback));
        try { owner.sendBroadcast(intent); } catch (RuntimeException ignored) { }
        main.postDelayed(() -> {
            if (!closed && request == generation && state != null) { generation++; state.setText("诊断：unknown（未收到已加载进程回包；不能判为模块未启用）"); }
        }, 2500L);
    }
    void save(Bundle out) { out.putString("short_diag_session", session); out.putString("short_diag_json", json); }
    boolean activityResult(int request, int result, Intent data) {
        if (request != EXPORT) return false;
        Activity owner = activity.get();
        if (result != Activity.RESULT_OK || data == null || data.getData() == null || owner == null || json.isEmpty() || closed) return true;
        android.content.Context context = owner.getApplicationContext(); android.net.Uri uri = data.getData(); String contents = json;
        worker.submit(() -> {
            boolean done = false;
            try (java.io.OutputStream output = context.getContentResolver().openOutputStream(uri)) {
                if (output != null) { output.write(contents.getBytes(StandardCharsets.UTF_8)); done = true; }
            } catch (Exception ignored) { }
            boolean success = done;
            main.post(() -> { Activity current = activity.get(); if (!closed && current != null) Toast.makeText(current, success ? "诊断已导出" : "导出失败", Toast.LENGTH_SHORT).show(); });
        });
        return true;
    }
    @Override public void close() {
        closed = true; generation++; main.removeCallbacksAndMessages(null); worker.shutdownNow();
        environment = null; state = null; activity.clear();
    }
}
