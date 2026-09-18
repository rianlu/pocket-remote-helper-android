package com.pocketremote.helper.inject;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 用户只装助手。插件 APK 打在 assets，启动时尽力静默装上（与悟空 assets/plugin.apk 相同）。
 * 不静默安装用户上传的文件。
 */
public final class PluginInstaller {
    private static final String TAG = "PocketRemotePlugin";
    private static final String ASSET = "plugin.apk";
    private static final String FILE_NAME = "inject-plugin.apk";
    /** 与 plugin/build.gradle versionCode 同步，低于此值会静默覆盖安装。 */
    private static final int PLUGIN_VERSION = 11;

    public static boolean ensure(Context context) {
        Context app = context.getApplicationContext();
        if (installedCurrent(app)) {
            return true;
        }
        File apk = extract(app);
        if (apk == null) {
            Log.w(TAG, "extract failed");
            return false;
        }
        String path = apk.getAbsolutePath();
        if (execPm(path)) {
            return installed(app);
        }
        String out = LocalAdb.shellOutput("pm install -r " + path, 20000);
        Log.i(TAG, "adb pm install: " + out);
        if (out != null && out.toLowerCase().contains("success")) {
            return installed(app);
        }
        String tmp = "/data/local/tmp/pocketremote-plugin.apk";
        String copy = "cat " + path + " > " + tmp + " ; pm install -r " + tmp;
        File filesDir = app.getFilesDir();
        if (filesDir != null && filesDir.equals(apk.getParentFile())) {
            copy = "run-as " + app.getPackageName() + " cat files/" + apk.getName()
                    + " > " + tmp + " ; pm install -r " + tmp;
        }
        out = LocalAdb.shellOutput(copy, 20000);
        Log.i(TAG, "adb copy+install: " + out);
        return installed(app);
    }

    static boolean installedCurrent(Context app) {
        try {
            android.content.pm.PackageInfo info =
                    app.getPackageManager().getPackageInfo(PluginClient.PLUGIN_PKG, 0);
            return info.versionCode >= PLUGIN_VERSION;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static boolean installed(Context app) {
        try {
            app.getPackageManager().getPackageInfo(PluginClient.PLUGIN_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static File extract(Context app) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = app.getAssets().open(ASSET);
            File dir = app.getExternalFilesDir(null);
            if (dir == null) {
                dir = app.getFilesDir();
            }
            if (!dir.exists() && !dir.mkdirs()) {
                dir = app.getFilesDir();
            }
            File dest = new File(dir, FILE_NAME);
            out = new FileOutputStream(dest);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            return dest;
        } catch (Exception e) {
            Log.w(TAG, "extract", e);
            return null;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean execPm(String path) {
        try {
            Process p = Runtime.getRuntime().exec(new String[] {"pm", "install", "-r", path});
            int code = p.waitFor();
            Log.i(TAG, "pm install exit=" + code);
            return code == 0;
        } catch (Exception e) {
            Log.i(TAG, "pm install skip: " + e.getMessage());
            return false;
        }
    }
}
