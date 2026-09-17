package com.pockettv.tv.inject;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** 本机按键与文本注入。先 exec input，失败再试 127.0.0.1:5555。 */
public final class Injector {
    private static final String TAG = "PocketTvInject";
    private final Context app;
    private Boolean shellOk;

    public Injector(Context context) {
        this.app = context.getApplicationContext();
    }

    /** 探测 input 是否可用；模拟器上常为 false。 */
    public boolean probe() {
        if (shellOk == null) {
            shellOk = Boolean.valueOf(runInput("keyevent", "0") || LocalAdb.shell("input keyevent 0"));
        }
        return shellOk.booleanValue();
    }

    /** @param code Android KeyEvent 数值，见 PROTOCOL.md */
    public boolean key(int code) {
        if (code == 24 || code == 25 || code == 164) {
            return volume(code);
        }
        if (runInput("keyevent", String.valueOf(code))) {
            return true;
        }
        return LocalAdb.shell("input keyevent " + code);
    }

    public boolean text(String raw) {
        if (raw == null) {
            raw = "";
        }
        String escaped = raw.replace(" ", "%s").replace("'", "'\\''");
        if (runInput("text", escaped)) {
            return true;
        }
        return LocalAdb.shell("input text " + escaped);
    }

    private boolean volume(int code) {
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return false;
        }
        try {
            if (code == 24) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
            } else if (code == 25) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
            } else {
                am.setStreamMute(AudioManager.STREAM_MUSIC, true);
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "volume", t);
            return false;
        }
    }

    private boolean runInput(String... args) {
        StringBuilder cmd = new StringBuilder("input");
        for (int i = 0; i < args.length; i++) {
            cmd.append(' ').append(args[i]);
        }
        try {
            Process p = Runtime.getRuntime().exec(new String[] {"sh", "-c", cmd.toString()});
            int code = p.waitFor();
            InputStream err = p.getErrorStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = err.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            String e = bos.toString("UTF-8");
            if (code != 0) {
                Log.w(TAG, "input exit=" + code + " " + e);
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "input", e);
            return false;
        }
    }
}
