package com.ning.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String START_URL = "https://zyn0130.top:8443";
    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " NingApp/1.0");

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        requestRuntimePermissions();
        webView.loadUrl(START_URL);
    }

    private void requestRuntimePermissions() {
        String[] perms = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        };
        if (Build.VERSION.SDK_INT >= 33) {
            String[] p2 = new String[perms.length + 1];
            System.arraycopy(perms, 0, p2, 0, perms.length);
            p2[perms.length] = Manifest.permission.POST_NOTIFICATIONS;
            perms = p2;
        }
        boolean need = false;
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                need = true;
                break;
            }
        }
        if (need) ActivityCompat.requestPermissions(this, perms, 1001);
    }

    public class AndroidBridge {
        @JavascriptInterface
        public String getBattery() {
            try {
                BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
                int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                boolean charging = false;
                if (i != null) {
                    int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    charging = (status == BatteryManager.BATTERY_STATUS_CHARGING)
                            || (status == BatteryManager.BATTERY_STATUS_FULL);
                }
                JSONObject o = new JSONObject();
                o.put("level", level);
                o.put("charging", charging);
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public String getLocation() {
            try {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) return "{}";
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (loc == null) return "{}";
                JSONObject o = new JSONObject();
                o.put("lat", loc.getLatitude());
                o.put("lng", loc.getLongitude());
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public String getTopApp() {
            try {
                if (Build.VERSION.SDK_INT < 21) return "{}";
                UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                long now = System.currentTimeMillis();
                List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000L * 60 * 10, now);
                if (stats == null || stats.isEmpty()) return "{}";
                UsageStats recent = null;
                for (UsageStats u : stats) {
                    if (recent == null || u.getLastTimeUsed() > recent.getLastTimeUsed()) recent = u;
                }
                if (recent == null) return "{}";
                JSONObject o = new JSONObject();
                o.put("package", recent.getPackageName());
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public String getInfo() {
            try {
                JSONObject o = new JSONObject();
                o.put("platform", "android");
                o.put("version", Build.VERSION.RELEASE);
                o.put("model", Build.MODEL);
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
