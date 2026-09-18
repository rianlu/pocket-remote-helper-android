package com.pocketremote.helper.store;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import com.pocketremote.helper.protocol.Constants;

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
            if ("com.pocketremote.helper".equals(pi.packageName) || "com.pocketremote.helper.plugin".equals(pi.packageName)) {
                continue;
            }
            if (!isLaunchable(pm, pi.packageName)) {
                continue;
            }
            ApplicationInfo ai = pi.applicationInfo;
            boolean system = ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            String label = pi.packageName;
            try {
                if (ai != null) {
                    CharSequence cs = pm.getApplicationLabel(ai);
                    if (cs != null && cs.length() > 0) {
                        label = cs.toString();
                    }
                }
            } catch (Exception ignored) {
            }
            try {
                JSONObject o = new JSONObject();
                o.put("name", label);
                o.put("pkg", pi.packageName);
                o.put("system", system);
                o.put("size", ai != null ? usageBytes(ai) : 0L);
                o.put("extractable", canExtract(ai));
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr;
    }

    /** 桌面或 Leanback 能打开的才进列表。 */
    private boolean isLaunchable(PackageManager pm, String pkg) {
        if (pm.getLaunchIntentForPackage(pkg) != null) {
            return true;
        }
        Intent lean = new Intent(Intent.ACTION_MAIN);
        lean.addCategory("android.intent.category.LEANBACK_LAUNCHER");
        lean.setPackage(pkg);
        List<ResolveInfo> list = pm.queryIntentActivities(lean, 0);
        return list != null && !list.isEmpty();
    }

    public boolean open(String pkg) {
        PackageManager pm = app.getPackageManager();
        Intent launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) {
            Intent lean = new Intent(Intent.ACTION_MAIN);
            lean.addCategory("android.intent.category.LEANBACK_LAUNCHER");
            lean.setPackage(pkg);
            List<ResolveInfo> list = pm.queryIntentActivities(lean, 0);
            if (list != null && !list.isEmpty()) {
                ResolveInfo ri = list.get(0);
                launch = new Intent(Intent.ACTION_MAIN);
                launch.addCategory("android.intent.category.LEANBACK_LAUNCHER");
                launch.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
            }
        }
        if (launch == null) {
            return false;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        app.startActivity(launch);
        return true;
    }

    public byte[] iconPng(String pkg) {
        try {
            Drawable d = app.getPackageManager().getApplicationIcon(pkg);
            Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, 96, 96);
            d.draw(c);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 90, bos);
            b.recycle();
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public File apkSource(String pkg) {
        try {
            ApplicationInfo ai = app.getPackageManager().getApplicationInfo(pkg, 0);
            if (!canExtract(ai) || ai.sourceDir == null) {
                return null;
            }
            return new File(ai.sourceDir);
        } catch (Exception e) {
            return null;
        }
    }

    /** 系统应用、分体包不能提取完整可安装 APK。 */
    public boolean canExtract(String pkg) {
        try {
            return canExtract(app.getPackageManager().getApplicationInfo(pkg, 0));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isSystem(String pkg) {
        try {
            ApplicationInfo ai = app.getPackageManager().getApplicationInfo(pkg, 0);
            return ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean canExtract(ApplicationInfo ai) {
        if (ai == null || ai.sourceDir == null || ai.sourceDir.length() == 0) {
            return false;
        }
        if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 21 && ai.splitSourceDirs != null && ai.splitSourceDirs.length > 0) {
            return false;
        }
        return true;
    }

    /** 占用：能读到的应用/数据/缓存，否则 APK 文件体积。 */
    private long usageBytes(ApplicationInfo ai) {
        long stats = queryStats(ai);
        if (stats > 0) {
            return stats;
        }
        return apkBytes(ai);
    }

    private long queryStats(ApplicationInfo ai) {
        if (Build.VERSION.SDK_INT < 26 || ai.packageName == null) {
            return 0;
        }
        try {
            Object ssm = app.getSystemService("storagestats");
            if (ssm == null) {
                return 0;
            }
            Object uuid = ai.getClass().getField("storageUuid").get(ai);
            if (uuid == null) {
                uuid = Class.forName("android.os.storage.StorageManager").getField("UUID_DEFAULT").get(null);
            }
            Object ss = ssm.getClass()
                    .getMethod("queryStatsForPackage", java.util.UUID.class, String.class, android.os.UserHandle.class)
                    .invoke(ssm, uuid, ai.packageName, android.os.Process.myUserHandle());
            long code = ((Long) ss.getClass().getMethod("getAppBytes").invoke(ss)).longValue();
            long data = ((Long) ss.getClass().getMethod("getDataBytes").invoke(ss)).longValue();
            long cache = ((Long) ss.getClass().getMethod("getCacheBytes").invoke(ss)).longValue();
            return code + data + cache;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static long apkBytes(ApplicationInfo ai) {
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<String>();
        if (ai.sourceDir != null) {
            paths.add(ai.sourceDir);
        }
        if (ai.publicSourceDir != null) {
            paths.add(ai.publicSourceDir);
        }
        if (Build.VERSION.SDK_INT >= 21 && ai.splitSourceDirs != null) {
            for (int i = 0; i < ai.splitSourceDirs.length; i++) {
                if (ai.splitSourceDirs[i] != null) {
                    paths.add(ai.splitSourceDirs[i]);
                }
            }
        }
        long n = 0;
        java.util.Iterator<String> it = paths.iterator();
        while (it.hasNext()) {
            File f = new File(it.next());
            if (f.isFile()) {
                n += f.length();
            }
        }
        return n;
    }

    public static void copyFile(File src, OutputStream out) throws Exception {
        InputStream in = new FileInputStream(src);
        try {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } finally {
            in.close();
        }
    }

    public boolean uninstall(String pkg) {
        if (isSystem(pkg)) {
            return false;
        }
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
