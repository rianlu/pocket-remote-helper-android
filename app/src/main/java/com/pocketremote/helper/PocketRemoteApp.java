package com.pocketremote.helper;

import android.app.Application;

import com.pocketremote.helper.store.Prefs;
import com.pocketremote.helper.store.TransferStore;

/** 启动时确保设备 id 与传输目录存在。 */
public final class PocketRemoteApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        new Prefs(this).deviceId();
        new TransferStore().root();
    }
}
