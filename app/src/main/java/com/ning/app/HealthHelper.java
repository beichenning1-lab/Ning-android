package com.ning.app;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;

import org.json.JSONObject;

public class HealthHelper {
    private static long cachedSteps = 0;
    private static long lastUpdate = 0;

    public static String getHealth(Context ctx) {
        try {
            JSONObject o = new JSONObject();
            o.put("steps", getSteps(ctx));
            o.put("sleepMinutes", estimateSleep(ctx));
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    public static int getSteps(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT < 19) return 0;
            SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
            Sensor step = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (step == null) return 0;
            final long[] result = new long[1];
            SensorEventListener l = new SensorEventListener() {
                @Override public void onSensorChanged(SensorEvent e) {
                    if (e.values.length > 0) result[0] = (long) e.values[0];
                }
                @Override public void onAccuracyChanged(Sensor s, int a) {}
            };
            sm.registerListener(l, step, SensorManager.SENSOR_DELAY_NORMAL);
            Thread.sleep(300);
            sm.unregisterListener(l);
            if (result[0] > 0) {
                cachedSteps = result[0];
                lastUpdate = System.currentTimeMillis();
            }
            return (int) Math.min(cachedSteps, 999999);
        } catch (Exception e) { return (int) cachedSteps; }
    }

    public static int estimateSleep(Context ctx) {
        try {
            long now = System.currentTimeMillis();
            long sinceBoot = android.os.SystemClock.elapsedRealtime();
            long bootTime = now - sinceBoot;
            if (bootTime < 2 * 3600 * 1000L) return 0;
            long uptime = sinceBoot / 60000;
            if (uptime > 16 * 60) return 7 * 60;
            return (int) Math.max(0, (uptime * 3) / 10);
        } catch (Exception e) { return 0; }
    }
}
