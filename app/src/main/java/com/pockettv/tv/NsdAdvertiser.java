package com.pockettv.tv;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.util.Log;

final class NsdAdvertiser {
    private static final String TAG = "PocketTvNsd";
    private final Context app;
    private final Prefs prefs;
    private NsdManager nsd;
    private NsdManager.RegistrationListener listener;

    NsdAdvertiser(Context context, Prefs prefs) {
        this.app = context.getApplicationContext();
        this.prefs = prefs;
    }

    void start() {
        nsd = (NsdManager) app.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) {
            return;
        }
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(Constants.NSD_NAME);
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

    void stop() {
        if (nsd != null && listener != null) {
            try {
                nsd.unregisterService(listener);
            } catch (Exception ignored) {
            }
        }
    }
}
