package com.ning.app;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONObject;

import java.util.List;

public class UsageHelper {
    public static String getTopApp(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT < 21) return "{}";
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, now - 1000L * 60 * 30, now);
            if (stats == null || stats.isEmpty()) return "{}";
            UsageStats recent = null;
            for (UsageStats u : stats) {
                if (recent == null || u.getLastTimeUsed() > recent.getLastTimeUsed()) recent = u;
            }
            if (recent == null) return "{}";
            String pkg = recent.getPackageName();
            String appName = pkg;
            try {
                PackageManager pm = ctx.getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                appName = pm.getApplicationLabel(ai).toString();
            } catch (Exception ignored) {}
            JSONObject o = new JSONObject();
            o.put("package", pkg);
            o.put("name", appName);
            o.put("lastUsed", recent.getLastTimeUsed());
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }
}
