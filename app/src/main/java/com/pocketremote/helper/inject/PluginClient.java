package com.pocketremote.helper.inject;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 绑定系统签名插件。固件钥匙不匹配或未安装时保持断开。 */
final class PluginClient {
    static final String PLUGIN_PKG = "com.pocketremote.helper.plugin";
    static final String PLUGIN_SVC = "com.pocketremote.helper.plugin.InjectService";
    static final String ACTION = "com.pocketremote.helper.plugin.BIND";

    private static final String TAG = "PocketRemotePlugin";
    private final Context app;
    private CountDownLatch ready = new CountDownLatch(1);
    private IPocketInject remote;
    private boolean bound;

    PluginClient(Context context) {
        this.app = context.getApplicationContext();
    }

    void bindAsync() {
        if (remote != null) {
            return;
        }
        ready = new CountDownLatch(1);
        Intent intent = new Intent(ACTION);
        intent.setComponent(new ComponentName(PLUGIN_PKG, PLUGIN_SVC));
        try {
            bound = app.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!bound) {
                ready.countDown();
            }
        } catch (Exception e) {
            Log.i(TAG, "bind skip: " + e.getMessage());
            ready.countDown();
        }
    }

    boolean await(long ms) {
        try {
            ready.await(ms, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return remote != null;
    }

    boolean ping() {
        if (remote == null && !await(1500)) {
            return false;
        }
        try {
            return remote != null && remote.ping();
        } catch (Exception e) {
            Log.w(TAG, "ping", e);
            return false;
        }
    }

    boolean key(int code) {
        if (remote == null && !await(1500)) {
            return false;
        }
        try {
            return remote.key(code);
        } catch (Exception e) {
            Log.w(TAG, "key", e);
            return false;
        }
    }

    boolean pointer(String action, int dx, int dy) {
        if (remote == null && !await(1500)) {
            return false;
        }
        try {
            return remote.pointer(action, dx, dy);
        } catch (Exception e) {
            Log.w(TAG, "pointer", e);
            return false;
        }
    }

    boolean text(String raw) {
        if (remote == null && !await(1500)) {
            return false;
        }
        try {
            return remote.text(raw);
        } catch (Exception e) {
            Log.w(TAG, "text", e);
            return false;
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            remote = IPocketInject.Stub.asInterface(service);
            ready.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remote = null;
        }
    };
}
