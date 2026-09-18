package com.pocketremote.helper.inject;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import com.pocketremote.helper.protocol.Constants;

/** 本机按键与文本注入。可按 hello.inject 锁定 plugin 或 adb；未锁定时自动探测。 */
public final class Injector {
    private static final String TAG = "PocketRemoteInject";
    private final Context app;
    private final PluginClient plugin;
    private String mode = Constants.INJECT_NONE;
    /** 空=自动；plugin / adb 为本连接锁定，不改走另一通道。 */
    private String preferred = "";

    public Injector(Context context) {
        this.app = context.getApplicationContext();
        this.plugin = new PluginClient(this.app);
        this.plugin.bindAsync();
    }

    /** 连接握手指定通道。仅 plugin / adb 生效，其它值恢复自动。 */
    public void setPreferred(String want) {
        if (Constants.INJECT_PLUGIN.equals(want) || Constants.INJECT_ADB.equals(want)) {
            preferred = want;
        } else {
            preferred = "";
        }
    }

    /** 静默装完插件后再绑一次。 */
    public void rebindPlugin() {
        plugin.bindAsync();
    }

    /** 当前通道：plugin / adb / input / none，与 PROTOCOL injectMode 一致。先 probe。 */
    public String mode() {
        return mode;
    }

    /** 探测注入是否可用。已锁定时只测该通道。 */
    public boolean probe() {
        if (Constants.INJECT_PLUGIN.equals(preferred)) {
            if (plugin.ping()) {
                mode = Constants.INJECT_PLUGIN;
                return true;
            }
            mode = Constants.INJECT_NONE;
            return false;
        }
        if (Constants.INJECT_ADB.equals(preferred)) {
            if (adbReady()) {
                mode = Constants.INJECT_ADB;
                return true;
            }
            mode = Constants.INJECT_NONE;
            return false;
        }
        if (plugin.ping()) {
            mode = Constants.INJECT_PLUGIN;
            return true;
        }
        if (adbReady()) {
            mode = Constants.INJECT_ADB;
            return true;
        }
        // 普通 input 只在 Android 4.x 上经常真能打进其它窗口；5+ 探测成功也不当可用
        if (Build.VERSION.SDK_INT < 21 && runInput("keyevent", "0")) {
            mode = Constants.INJECT_INPUT;
            return true;
        }
        mode = Constants.INJECT_NONE;
        return false;
    }

    /** @param code Android KeyEvent 数值，见 PROTOCOL.md */
    public boolean key(int code) {
        if (code == 24 || code == 25 || code == 164) {
            return volume(code);
        }
        if (code == Constants.KEY_SETTINGS) {
            return openSettings();
        }
        if (Constants.INJECT_PLUGIN.equals(preferred)) {
            if (plugin.key(code)) {
                mode = Constants.INJECT_PLUGIN;
                return true;
            }
            return false;
        }
        if (Constants.INJECT_ADB.equals(preferred)) {
            if (adbKey(code)) {
                mode = Constants.INJECT_ADB;
                return true;
            }
            return false;
        }
        if (plugin.key(code)) {
            mode = Constants.INJECT_PLUGIN;
            return true;
        }
        if (adbKey(code)) {
            mode = Constants.INJECT_ADB;
            return true;
        }
        if (runInput("keyevent", String.valueOf(code))) {
            mode = Constants.INJECT_INPUT;
            return true;
        }
        return false;
    }

    /** 本机 adbd 结束后台进程。 */
    public boolean killBackgroundShell() {
        return LocalAdb.shell("am kill-all");
    }

    public boolean copyFile(String src, String dest) {
        if (src == null || dest == null) {
            return false;
        }
        return LocalAdb.shell("cp \"" + src + "\" \"" + dest + "\"");
    }

    public boolean pointer(String action, int dx, int dy) {
        if (Constants.INJECT_ADB.equals(preferred)) {
            return false;
        }
        if (plugin.pointer(action, dx, dy)) {
            mode = Constants.INJECT_PLUGIN;
            return true;
        }
        return false;
    }

    public boolean text(String raw) {
        if (raw == null) {
            raw = "";
        }
        if (Constants.INJECT_PLUGIN.equals(preferred)) {
            if (plugin.text(raw)) {
                mode = Constants.INJECT_PLUGIN;
                return true;
            }
            return false;
        }
        if (Constants.INJECT_ADB.equals(preferred)) {
            if (pasteClipboard(raw) && adbKey(279)) {
                mode = Constants.INJECT_ADB;
                return true;
            }
            return false;
        }
        if (plugin.text(raw)) {
            mode = Constants.INJECT_PLUGIN;
            return true;
        }
        if (pasteClipboard(raw) && adbKey(279)) {
            mode = Constants.INJECT_ADB;
            return true;
        }
        return false;
    }

    /** 本机 adbd 可用则预热长连接。 */
    private boolean adbReady() {
        LocalAdb.tryEnableTcp();
        if (!LocalAdb.available()) {
            return false;
        }
        LocalAdb.sendLine("true");
        return true;
    }

    private boolean adbKey(int code) {
        return LocalAdb.keyevent(code);
    }

    private boolean pasteClipboard(String raw) {
        try {
            ClipboardManager cm = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                return false;
            }
            cm.setPrimaryClip(ClipData.newPlainText("pocketremote", raw));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "clipboard", t);
            return false;
        }
    }

    /** KEYCODE_SETTINGS 在多数盒子上无焦点处理，改为拉起系统设置页。 */
    private boolean openSettings() {
        Intent[] intents = new Intent[] {
            new Intent(Settings.ACTION_SETTINGS),
            new Intent("android.settings.TV_SETTINGS"),
            new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
        };
        for (int i = 0; i < intents.length; i++) {
            try {
                intents[i].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                app.startActivity(intents[i]);
                return true;
            } catch (Throwable ignored) {
            }
        }
        Log.w(TAG, "open settings");
        return false;
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
