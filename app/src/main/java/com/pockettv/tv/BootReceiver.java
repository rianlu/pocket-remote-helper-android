package com.pockettv.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机拉起 {@link RemoteService}。部分 4.x 盒子还需加入自启动白名单。 */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        Intent svc = new Intent(context, RemoteService.class);
        context.startService(svc);
    }
}
