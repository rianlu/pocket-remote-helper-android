package com.pocketremote.helper;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.pocketremote.helper.inject.Injector;
import com.pocketremote.helper.inject.PluginInstaller;
import com.pocketremote.helper.net.NetInfo;
import com.pocketremote.helper.net.NsdAdvertiser;
import com.pocketremote.helper.net.UdpDiscovery;
import com.pocketremote.helper.net.WsHttpServer;
import com.pocketremote.helper.protocol.CommandProcessor;
import com.pocketremote.helper.store.PinSession;
import com.pocketremote.helper.store.Prefs;
import com.pocketremote.helper.store.TransferStore;
import com.pocketremote.helper.ui.UiBus;

/** 前台服务：拉起 WebSocket/HTTP、UDP、NSD。 */
public final class RemoteService extends Service {
    public static final String ACTION_REPROBE = "com.pocketremote.helper.REPROBE";
    private static final String TAG = "PocketRemoteSvc";
    private static final int NOTIFY_ID = 16;
    private static final String CHANNEL_ID = "pocketremote_remote";

    private WsHttpServer server;
    private UdpDiscovery udp;
    private NsdAdvertiser nsd;
    private Injector injector;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int nsdRetries;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundInternal();
        Prefs prefs = new Prefs(this);
        injector = new Injector(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                PluginInstaller.ensure(RemoteService.this);
                injector.rebindPlugin();
                injector.probe();
                UiBus.get().postInjectMode(injector.mode());
            }
        }, "plugin-install").start();
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
        scheduleNsdRetry();
        UiBus.get().postStatus(getString(R.string.status_waiting));
    }

    /** 开机时 Wi-Fi 往往晚于 BOOT_COMPLETED，NSD 需等有 IPv4 后再宣告。 */
    private void scheduleNsdRetry() {
        if (!"0.0.0.0".equals(NetInfo.ipv4()) || nsdRetries >= 15) {
            return;
        }
        nsdRetries++;
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (nsd == null) {
                    return;
                }
                nsd.stop();
                nsd.start();
                scheduleNsdRetry();
            }
        }, 2000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REPROBE.equals(intent.getAction()) && injector != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    injector.probe();
                    UiBus.get().postInjectMode(injector.mode());
                }
            }, "inject-reprobe").start();
        }
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
        mainHandler.removeCallbacksAndMessages(null);
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
        if (Build.VERSION.SDK_INT >= 31) {
            startForeground(
                    NOTIFY_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFY_ID, notification);
        }
    }
}
