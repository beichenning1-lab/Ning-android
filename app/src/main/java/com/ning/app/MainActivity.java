package com.ning.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
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
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " Polaris/3.0");

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });
        webView.addJavascriptInterface(new JsBridge(), "Android");
        webView.loadUrl(START_URL);

        requestAllPermissions();
        startPolarisService();
        askIgnoreBatteryOptimization();
    }

    private void requestAllPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT >= 29) perms.add(Manifest.permission.ACTIVITY_RECOGNITION);
        List<String> need = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        }
        if (!need.isEmpty()) ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 1001);
    }

    private void startPolarisService() {
        Intent i = new Intent(this, PolarisService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void askIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT < 23) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception ignored) {}
        }
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
        public void openBatterySettings() { askIgnoreBatteryOptimization(); }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
