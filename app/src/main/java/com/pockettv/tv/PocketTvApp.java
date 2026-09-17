package com.pockettv.tv;

import android.app.Application;

public final class PocketTvApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        new Prefs(this).deviceId();
        new TransferStore().root();
    }
}
