package com.pocketremote.helper.net;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.util.Log;
import com.pocketremote.helper.protocol.Constants;
import com.pocketremote.helper.store.Prefs;

/** NSD 宣告 _pocketremote._tcp.；TXT 仅 API 21+。 */
public final class NsdAdvertiser {
    private static final String TAG = "PocketRemoteNsd";
    private final Context app;
    private final Prefs prefs;
    private NsdManager nsd;
    private NsdManager.RegistrationListener listener;

    public NsdAdvertiser(Context context, Prefs prefs) {
        this.app = context.getApplicationContext();
        this.prefs = prefs;
    }

    public void start() {
        nsd = (NsdManager) app.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) {
            return;
        }
        NsdServiceInfo info = new NsdServiceInfo();
        String model = Build.MODEL;
        if (model == null || model.length() == 0) {
            model = Constants.NSD_NAME;
        }
        info.setServiceName(model);
        info.setServiceType(Constants.NSD_TYPE);
        info.setPort(Constants.CONTROL_PORT);
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                info.setAttribute("id", prefs.deviceId());
                info.setAttribute("sdk", String.valueOf(Build.VERSION.SDK_INT));
                info.setAttribute("v", String.valueOf(Constants.PROTOCOL_V));
            } catch (Exception e) {
                Log.w(TAG, "txt", e);
            }
        }
        listener = new NsdManager.RegistrationListener() {
            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.w(TAG, "reg fail " + errorCode);
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }

            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                Log.i(TAG, "nsd registered");
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            }
        };
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (Exception e) {
            Log.w(TAG, "nsd", e);
        }
    }

    public void stop() {
        if (nsd != null && listener != null) {
            try {
                nsd.unregisterService(listener);
            } catch (Exception ignored) {
            }
        }
    }
}
