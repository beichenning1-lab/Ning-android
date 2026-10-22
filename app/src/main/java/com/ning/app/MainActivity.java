package com.ning.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import android.webkit.ValueCallback;

public class MainActivity extends Activity {
    private ValueCallback<Uri[]> mFilePathCallback;

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
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " Polaris/5.0");

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (mFilePathCallback != null) mFilePathCallback.onReceiveValue(null);
                mFilePathCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), 1002);
                } catch (Exception e) {
                    mFilePathCallback = null;
                    return false;
                }
                return true;
            }
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        try { request.grant(request.getResources()); } catch (Exception ignored) {}
                    }
                });
            }
        });
        webView.addJavascriptInterface(new JsBridge(), "Android");
        webView.loadUrl(START_URL);

        requestAllPermissions();
        startPolarisService();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1002) {
            if (mFilePathCallback == null) { super.onActivityResult(req, res, data); return; }
            Uri[] results = null;
            if (res == RESULT_OK) {
                if (data == null || data.getDataString() == null) {
                    if (data != null && data.getClipData() != null) {
                        int n = data.getClipData().getItemCount();
                        results = new Uri[n];
                        for (int i = 0; i < n; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else {
                    results = new Uri[]{ Uri.parse(data.getDataString()) };
                }
            }
            mFilePathCallback.onReceiveValue(results);
            mFilePathCallback = null;
        } else {
            super.onActivityResult(req, res, data);
        }
    }

    private void requestAllPermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        perms.add(Manifest.permission.CAMERA);
        perms.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT >= 29) perms.add(Manifest.permission.ACTIVITY_RECOGNITION);
        List<String> need = new ArrayList<>();
        for (String p : perms) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), 1001);
    }

    private void startPolarisService() {
        try {
            Intent i = new Intent(this, PolarisService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) {}
    }

    public class JsBridge {
        @JavascriptInterface
        public String getBattery() { return DeviceHelper.getBattery(MainActivity.this); }

        @JavascriptInterface
        public String getLocation() { return DeviceHelper.getLocation(MainActivity.this); }

        @JavascriptInterface
        public String getTopApp() { return UsageHelper.getTopApp(MainActivity.this); }

        @JavascriptInterface
        public String getInfo() { return DeviceHelper.getInfo(); }

        @JavascriptInterface
        public String getHealth() { return HealthHelper.getHealth(MainActivity.this); }

        @JavascriptInterface
        public String checkPermissions() {
            try {
                JSONObject o = new JSONObject();
                o.put("location", Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
                o.put("notification", Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
                o.put("activity", Build.VERSION.SDK_INT < 29 || checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED);
                o.put("camera", Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
                o.put("microphone", Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
                boolean usage = false;
                try {
                    UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
                    long now = System.currentTimeMillis();
                    List<?> list = usm == null ? null : usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000L * 60 * 60 * 24, now);
                    usage = list != null && !list.isEmpty();
                } catch (Exception ignored) {}
                o.put("usage", usage);
                boolean battery = false;
                try {
                    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                    battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
                } catch (Exception ignored) {}
                o.put("battery", battery);
                o.put("platform", "android");
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void saveToken(String t) {
            getSharedPreferences("polaris", MODE_PRIVATE).edit().putString("token", t).apply();
        }

        @JavascriptInterface
        public void saveFriends(String json) {
            try {
                getSharedPreferences("polaris", MODE_PRIVATE).edit().putString("friends", json).apply();
                PolarisWidget.updateAll(MainActivity.this);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void refreshWidget() { PolarisWidget.updateAll(MainActivity.this); }

        @JavascriptInterface
        public void openUsageSettings() {
            try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void openBatterySettings() {
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void openAppSettings() {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void openLocationSettings() {
            try { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void requestPermissionsAgain() { runOnUiThread(new Runnable() { public void run() { requestAllPermissions(); } }); }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
