package io.github.andrealtb.lockscreenlyrics.diagnostics;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Optional foreground App-page sensor observation, never installed in a Hook or background service. */
public final class AppEnvironmentObserver implements SensorEventListener {
    private final SensorManager manager;
    private Consumer<Map<String, String>> consumer;
    private boolean registered;
    private float last = Float.NaN;
    public AppEnvironmentObserver(Context context, Consumer<Map<String, String>> consumer) {
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE); this.consumer = consumer;
    }
    public void start() {
        if (registered || consumer == null) return;
        try {
            Sensor hinge = Build.VERSION.SDK_INT >= 30 && manager != null ? manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) : null;
            if (hinge == null) { publish("not_present_or_unavailable", null); return; }
            registered = manager.registerListener(this, hinge, SensorManager.SENSOR_DELAY_UI);
            if (!registered) publish("unavailable", null);
        } catch (RuntimeException unavailable) { publish("unavailable", null); }
    }
    public void stop() {
        if (manager != null && registered) try { manager.unregisterListener(this); } catch (RuntimeException ignored) { }
        registered = false;
    }
    public void close() { stop(); consumer = null; }
    @Override public void onSensorChanged(SensorEvent event) {
        if (consumer == null || event.values.length == 0) return;
        float value = event.values[0];
        if (!Float.isFinite(value) || value < 0f || value > 360f || (!Float.isNaN(last) && Math.abs(last - value) < 1f)) return;
        last = value; publish("observed", value);
    }
    private void publish(String state, Float value) {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("hinge_sensor", state); facts.put("hinge_angle_degrees", value == null ? "unknown" : Float.toString(value));
        facts.put("fold_posture", "unknown (raw hinge angle is not a vendor posture mapping)");
        if (consumer != null) consumer.accept(facts);
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
