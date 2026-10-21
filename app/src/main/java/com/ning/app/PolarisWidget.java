package com.ning.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PolarisWidget extends AppWidgetProvider {
    public static final String ACTION_UPDATE = "com.ning.app.WIDGET_UPDATE";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateWidget(context, manager, id);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_UPDATE.equals(intent.getAction())) updateAll(context);
    }

    public static void updateAll(Context ctx) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx);
            ComponentName cn = new ComponentName(ctx, PolarisWidget.class);
            int[] ids = m.getAppWidgetIds(cn);
            for (int id : ids) updateWidget(ctx, m, id);
        } catch (Exception ignored) {}
    }

    static void updateWidget(Context ctx, AppWidgetManager m, int id) {
        try {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_polaris);
            SharedPreferences sp = ctx.getSharedPreferences("polaris", Context.MODE_PRIVATE);
            String friendsJson = sp.getString("friends", "[]");
            String selfJson = sp.getString("self_status", "{}");

            String name = "北极星";
            String initial = "★";
            String battery = "--";
            String distance = "等待";
            String mood = "⭐";
            String updated = "";

            JSONArray arr = new JSONArray(friendsJson);
            if (arr.length() > 0) {
                JSONObject f = arr.getJSONObject(0);
                name = f.optString("name", "好友");
                if (!name.isEmpty()) initial = name.substring(0, 1).toUpperCase();
                mood = f.optString("mood", "⭐");
                if (mood.isEmpty()) mood = "⭐";
                int b = f.optInt("battery", -1);
                if (b >= 0) {
                    battery = b + "%";
                    if (f.optBoolean("charging", false)) battery += " ⚡";
                }
                try {
                    JSONObject self = new JSONObject(selfJson);
                    if (self.has("lat") && self.has("lng") && f.has("lat") && f.has("lng")) {
                        double km = haversine(self.getDouble("lat"), self.getDouble("lng"),
                                f.getDouble("lat"), f.getDouble("lng"));
                        distance = fmtDist(km);
                    } else if (f.has("lat")) {
                        distance = "已定位";
                    }
                } catch (Exception ignored) {}
                long t = f.optLong("updatedAt", 0);
                if (t > 0) updated = new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(t));
            } else {
                distance = "点此添加好友";
            }

            rv.setTextViewText(R.id.w_name, name);
            rv.setTextViewText(R.id.w_avatar, initial);
            rv.setTextViewText(R.id.w_battery, "🔋 " + battery);
            rv.setTextViewText(R.id.w_distance, "📍 " + distance);
            rv.setTextViewText(R.id.w_mood, mood);
            rv.setTextViewText(R.id.w_time, updated);

            Intent open = new Intent(ctx, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.w_root, pi);

            Intent refresh = new Intent(ctx, PolarisWidget.class);
            refresh.setAction(ACTION_UPDATE);
            PendingIntent rpi = PendingIntent.getBroadcast(ctx, 1, refresh,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.w_refresh, rpi);

            m.updateAppWidget(id, rv);
        } catch (Exception ignored) {}
    }

    static double haversine(double la1, double lo1, double la2, double lo2) {
        double R = 6371;
        double dLa = Math.toRadians(la2 - la1);
        double dLo = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dLa / 2) * Math.sin(dLa / 2)
                + Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2))
                * Math.sin(dLo / 2) * Math.sin(dLo / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    static String fmtDist(double km) {
        if (km < 1) return Math.round(km * 1000) + "m";
        if (km < 100) return String.format(Locale.CHINA, "%.1fkm", km);
        return Math.round(km) + "km";
    }
}
