package com.pocketremote.helper.ui;

import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 后台线程把 PIN/状态抛到主线程刷新界面。 */
public final class UiBus {
    public interface Listener {
        void onPinChanged(String pin);

        void onStatus(String status);

        void onInjectMode(String mode);
    }

    private static final UiBus INSTANCE = new UiBus();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private String lastPin = "";
    private String lastStatus = "";
    private String lastInject = "";

    public static UiBus get() {
        return INSTANCE;
    }

    public void add(Listener listener) {
        listeners.add(listener);
        listener.onStatus(lastStatus);
        listener.onPinChanged(lastPin);
        listener.onInjectMode(lastInject);
    }

    public void remove(Listener listener) {
        listeners.remove(listener);
    }

    public void postPin(final String pin) {
        lastPin = pin == null ? "" : pin;
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : listeners) {
                    l.onPinChanged(lastPin);
                }
            }
        });
    }

    public void postStatus(final String status) {
        lastStatus = status == null ? "" : status;
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : listeners) {
                    l.onStatus(lastStatus);
                }
            }
        });
    }

    public void postInjectMode(final String mode) {
        lastInject = mode == null ? "" : mode;
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : listeners) {
                    l.onInjectMode(lastInject);
                }
            }
        });
    }
}
