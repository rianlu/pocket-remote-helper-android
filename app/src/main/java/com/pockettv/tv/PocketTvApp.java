package com.pockettv.tv;

import android.app.Application;

import com.pockettv.tv.store.Prefs;
import com.pockettv.tv.store.TransferStore;

/** 启动时确保设备 id 与传输目录存在。 */
public final class PocketTvApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        new Prefs(this).deviceId();
        new TransferStore().root();
    }
}
