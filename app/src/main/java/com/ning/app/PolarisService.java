package com.ning.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class PolarisService extends Service {
    public static final String CHANNEL_ID = "polaris_service";
    public static final int NOTIF_ID = 1001;
    private Handler handler;
    private SmartReporter reporter;
    private Runnable loop;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        handler = new Handler(Looper.getMainLooper());
        reporter = new SmartReporter(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification("正在守护你的连接"));
        if (loop == null) {
            loop = new Runnable() {
                @Override
                public void run() {
                    try { reporter.tick(); } catch (Exception ignored) {}
                    try { PolarisWidget.updateAll(PolarisService.this); } catch (Exception ignored) {}
                    handler.postDelayed(this, reporter.nextInterval());
                }
            };
            handler.post(loop);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (handler != null && loop != null) handler.removeCallbacks(loop);
        if (reporter != null) reporter.destroy();
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel c = new NotificationChannel(
                        CHANNEL_ID, "北极星", NotificationManager.IMPORTANCE_MIN);
                c.setShowBadge(false);
                nm.createNotificationChannel(c);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private Notification buildNotification(String text) {
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("北极星")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setOngoing(true)
                    .build();
        } else {
            return new Notification.Builder(this)
                    .setContentTitle("北极星")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setOngoing(true)
                    .build();
        }
    }
}
