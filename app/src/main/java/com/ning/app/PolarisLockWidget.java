package com.ning.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class PolarisLockWidget extends android.appwidget.AppWidgetProvider {
    private static final String API = "https://zyn0130.top:8443";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (final int id : ids) {
            mgr.updateAppWidget(id, build(ctx, "加载中…"));
            new Thread(() -> {
                String info = fetch(ctx);
                mgr.updateAppWidget(id, build(ctx, info));
            }).start();
        }
    }

    private RemoteViews build(Context ctx, String info) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_lock);
        v.setTextViewText(R.id.widget_lock_info, info);
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.widget_lock_root, pi);
        return v;
    }

    private String fetch(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("polaris", Context.MODE_PRIVATE);
        String token = sp.getString("token", "");
        if (token.isEmpty()) return "打开应用登录";
        try {
            URL url = new URL(API + "/api/friends");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            if (c.getResponseCode() != 200) return "打开应用查看";
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONArray arr = new JSONArray(sb.toString());
            if (arr.length() == 0) return "还没有好友";
            StringBuilder out = new StringBuilder();
            int n = Math.min(2, arr.length());
            for (int i = 0; i < n; i++) {
                JSONObject f = arr.getJSONObject(i);
                String name = f.optString("name", "好友");
                int bat = f.optInt("battery", -1);
                boolean ch = f.optBoolean("charging", false);
                if (i > 0) out.append("\n");
                out.append(name);
                if (bat >= 0) out.append(" ").append(ch ? "⚡" : "🔋").append(bat).append("%");
            }
            return out.toString();
        } catch (Exception e) {
            return "打开应用查看";
        }
    }
}
