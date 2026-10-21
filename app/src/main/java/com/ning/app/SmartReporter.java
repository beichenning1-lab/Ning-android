package com.ning.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmartReporter implements SensorEventListener {
    private static final String API = "https://zyn0130.top:8443";
    private final Context ctx;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final SensorManager sm;
    private long lastMoveTs = 0;
    private boolean moving = false;

    public SmartReporter(Context ctx) {
        this.ctx = ctx;
        this.sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        try {
            Sensor acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (acc != null) sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL);
        } catch (Exception ignored) {}
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float x = e.values[0], y = e.values[1], z = e.values[2];
        double mag = Math.sqrt(x * x + y * y + z * z);
        double delta = Math.abs(mag - SensorManager.GRAVITY_EARTH);
        long now = System.currentTimeMillis();
        if (delta > 1.2) { lastMoveTs = now; moving = true; }
        else if (now - lastMoveTs > 60_000) moving = false;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public long nextInterval() {
        int level = 100;
        boolean charging = false;
        try {
            JSONObject o = new JSONObject(DeviceHelper.getBattery(ctx));
            level = o.optInt("level", 100);
            charging = o.optBoolean("charging", false);
        } catch (Exception ignored) {}
        long base = moving ? 30_000 : 5 * 60_000;
        if (charging) base = Math.max(15_000, base / 2);
        if (level < 20) base = base * 2;
        return base;
    }

    public void tick() {
        pool.execute(() -> {
            try {
                SharedPreferences sp = ctx.getSharedPreferences("polaris", Context.MODE_PRIVATE);
                String token = sp.getString("token", "");
                if (token.isEmpty()) return;

                JSONObject body = new JSONObject();
                try {
                    JSONObject bat = new JSONObject(DeviceHelper.getBattery(ctx));
                    body.put("battery", bat.optInt("level", -1));
                    body.put("charging", bat.optBoolean("charging", false));
                } catch (Exception ignored) {}
                try {
                    JSONObject loc = new JSONObject(DeviceHelper.getLocation(ctx));
                    if (loc.has("lat")) {
                        body.put("lat", loc.getDouble("lat"));
                        body.put("lng", loc.getDouble("lng"));
                    }
                } catch (Exception ignored) {}
                try {
                    JSONObject top = new JSONObject(UsageHelper.getTopApp(ctx));
                    if (top.has("name")) {
                        body.put("topApp", top.getString("name"));
                        body.put("topPkg", top.getString("package"));
                    }
                } catch (Exception ignored) {}

                postJson(API + "/api/status", token, body);

                sp.edit().putString("self_status", body.toString()).apply();

                String friendsRaw = httpGet(API + "/api/friends", token);
                if (friendsRaw != null && friendsRaw.startsWith("[")) {
                    sp.edit().putString("friends", friendsRaw).apply();
                }

                PolarisWidget.updateAll(ctx);
            } catch (Exception e) { Log.e("Polaris", "tick failed", e); }
        });
    }

    private void postJson(String urlStr, String token, JSONObject body) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception ignored) {}
    }

    private String httpGet(String urlStr, String token) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Authorization", "Bearer " + token);
            int code = conn.getResponseCode();
            if (code != 200) { conn.disconnect(); return null; }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            conn.disconnect();
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    public void destroy() {
        try { if (sm != null) sm.unregisterListener(this); } catch (Exception ignored) {}
        pool.shutdownNow();
    }
}
