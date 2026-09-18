package com.pocketremote.helper.plugin;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.pocketremote.helper.inject.IPocketInject;

/** 仅接受 com.pocketremote.helper 的绑定，转发按键到 InjectEngine。 */
public final class InjectService extends Service {
    private static final String TAG = "PocketRemotePlugin";
    private static final String HELPER_PKG = "com.pocketremote.helper";

    private final IPocketInject.Stub binder = new IPocketInject.Stub() {
        @Override
        public boolean key(int code) throws RemoteException {
            assertHelper();
            return InjectEngine.key(code);
        }

        @Override
        public boolean text(String raw) throws RemoteException {
            assertHelper();
            return InjectEngine.text(getApplicationContext(), raw);
        }

        @Override
        public boolean ping() throws RemoteException {
            assertHelper();
            return true;
        }

        @Override
        public boolean pointer(String action, int dx, int dy) throws RemoteException {
            assertHelper();
            return InjectEngine.pointer(getApplicationContext(), action, dx, dy);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /** 拒绝其它应用调用，避免系统 uid 注入口被滥用。 */
    void assertHelper() {
        int uid = Binder.getCallingUid();
        String[] pkgs = getPackageManager().getPackagesForUid(uid);
        if (pkgs != null) {
            for (int i = 0; i < pkgs.length; i++) {
                if (HELPER_PKG.equals(pkgs[i])) {
                    return;
                }
            }
        }
        Log.w(TAG, "deny uid=" + uid);
        throw new SecurityException("not helper");
    }
}
