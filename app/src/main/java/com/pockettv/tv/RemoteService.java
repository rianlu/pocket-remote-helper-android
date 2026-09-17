package com.pockettv.tv;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public final class RemoteService extends Service {
    private static final String TAG = "PocketTvSvc";
    private static final int NOTIFY_ID = 16;
    private static final String CHANNEL_ID = "pockettv_remote";

    private WsHttpServer server;
    private UdpDiscovery udp;
    private NsdAdvertiser nsd;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundInternal();
        Prefs prefs = new Prefs(this);
        Injector injector = new Injector(this);
        PinSession pins = new PinSession();
        CommandProcessor commands = new CommandProcessor(this, prefs, injector, pins);
        TransferStore store = new TransferStore();
        store.root();
        try {
            server = new WsHttpServer(commands, prefs, store);
            server.start();
        } catch (Exception e) {
            Log.e(TAG, "server", e);
        }
        udp = new UdpDiscovery();
        udp.start();
        nsd = new NsdAdvertiser(this, prefs);
        nsd.start();
        UiBus.get().postStatus(getString(R.string.status_waiting));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
        }
        if (udp != null) {
            udp.stop();
        }
        if (nsd != null) {
            nsd.stop();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundInternal() {
        Intent launch = new Intent(this, MainActivity.class);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, launch, piFlags);
        Notification notification;
        if (Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel ch = new android.app.NotificationChannel(
                    CHANNEL_ID, getString(R.string.app_name), android.app.NotificationManager.IMPORTANCE_LOW);
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(ch);
            }
            notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.notify_running))
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.notify_running))
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .getNotification();
        }
        startForeground(NOTIFY_ID, notification);
    }
}
