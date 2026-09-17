package com.pockettv.tv.store;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;
import com.pockettv.tv.protocol.Constants;

/** 已装应用列表、打开、卸载、调起系统安装页。 */
public final class AppCatalog {
    private final Context app;

    public AppCatalog(Context context) {
        this.app = context.getApplicationContext();
    }

    public JSONArray list() {
        JSONArray arr = new JSONArray();
        PackageManager pm = app.getPackageManager();
        List<PackageInfo> pkgs = pm.getInstalledPackages(0);
        for (int i = 0; i < pkgs.size(); i++) {
            PackageInfo pi = pkgs.get(i);
            if (pi.packageName == null) {
                continue;
            }
            boolean system = false;
            if (pi.applicationInfo != null) {
                system = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            }
            String label = pi.packageName;
            try {
                CharSequence cs = pm.getApplicationLabel(pi.applicationInfo);
                if (cs != null) {
                    label = cs.toString();
                }
            } catch (Exception ignored) {
            }
            try {
                JSONObject o = new JSONObject();
                o.put("name", label);
                o.put("pkg", pi.packageName);
                o.put("system", system);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr;
    }

    public boolean open(String pkg) {
        Intent launch = app.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            return false;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        app.startActivity(launch);
        return true;
    }

    public boolean uninstall(String pkg) {
        Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        app.startActivity(intent);
        return true;
    }

    public boolean installApk(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri uri;
        if (Build.VERSION.SDK_INT >= 24) {
            uri = Uri.parse("content://" + Constants.FILE_AUTHORITY + "/apk/" + file.getName());
        } else {
            uri = Uri.fromFile(file);
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        app.startActivity(intent);
        return true;
    }
}
