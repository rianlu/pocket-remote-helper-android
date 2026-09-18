package com.pocketremote.helper.store;

import android.app.ActivityManager;
import android.content.Context;

import org.json.JSONObject;

import java.util.List;

import com.pocketremote.helper.inject.Injector;

/** 结束后台应用，给小内存盒子腾 RAM。不删缓存、不删文件。 */
public final class Cleaner {
    private Cleaner() {}

    public static JSONObject run(Context ctx, Injector injector) throws Exception {
        int killed = killBackground(ctx);
        injector.killBackgroundShell();
        JSONObject o = new JSONObject();
        o.put("killed", killed);
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            o.put("ramAvailMb", mi.availMem / (1024 * 1024));
        }
        return o;
    }

    private static int killBackground(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return 0;
        }
        String self = ctx.getPackageName();
        List<ActivityManager.RunningAppProcessInfo> list = am.getRunningAppProcesses();
        if (list == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < list.size(); i++) {
            ActivityManager.RunningAppProcessInfo p = list.get(i);
            if (p.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                continue;
            }
            if (p.pkgList == null) {
                continue;
            }
            for (int j = 0; j < p.pkgList.length; j++) {
                String pkg = p.pkgList[j];
                if (pkg == null || pkg.equals(self) || pkg.startsWith("android") || pkg.startsWith("com.android")) {
                    continue;
                }
                am.killBackgroundProcesses(pkg);
                n++;
            }
        }
        return n;
    }
}
