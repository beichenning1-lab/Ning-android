package com.ning.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;

import org.json.JSONObject;

public class DeviceHelper {
    public static String getBattery(Context ctx) {
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            Intent i = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            boolean charging = false;
            if (i != null) {
                int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
            }
            JSONObject o = new JSONObject();
            o.put("level", level);
            o.put("charging", charging);
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    public static String getLocation(Context ctx) {
        try {
            if (ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return "{}";
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (Exception ignored) {}
            }
            if (best == null) return "{}";
            JSONObject o = new JSONObject();
            o.put("lat", best.getLatitude());
            o.put("lng", best.getLongitude());
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    public static String getInfo() {
        try {
            JSONObject o = new JSONObject();
            o.put("platform", "android");
            o.put("version", Build.VERSION.RELEASE);
            o.put("model", Build.MODEL);
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }
}
