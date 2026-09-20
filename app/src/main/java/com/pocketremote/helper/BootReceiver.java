package com.pocketremote.helper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/** 开机 / 覆盖安装后拉起 {@link RemoteService}。部分盒子还需在系统里允许自启动。 */
public final class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "PocketRemoteBoot";
    private static final String ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !ACTION_QUICKBOOT.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        startRemote(context);
    }

    static void startRemote(Context context) {
        Intent svc = new Intent(context, RemoteService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception e) {
            Log.w(TAG, "start", e);
        }
    }
}
