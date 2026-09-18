package com.pocketremote.helper.store;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.view.Display;
import android.view.WindowManager;

import org.json.JSONObject;

import com.pocketremote.helper.BuildConfig;
import com.pocketremote.helper.net.NetInfo;

/** 电视设备配置，给手机「设备」页。 */
public final class SysInfo {
    private SysInfo() {}

    public static JSONObject snapshot(Context ctx, String injectMode, boolean injectOk) throws Exception {
        JSONObject o = new JSONObject();
        o.put("tvName", Build.MODEL != null ? Build.MODEL : "");
        o.put("manufacturer", Build.MANUFACTURER != null ? Build.MANUFACTURER : "");
        o.put("brand", Build.BRAND != null ? Build.BRAND : "");
        o.put("android", Build.VERSION.RELEASE != null ? Build.VERSION.RELEASE : "");
        o.put("sdk", Build.VERSION.SDK_INT);
        o.put("abi", cpuAbi());
        o.put("soc", prop("ro.board.platform"));
        o.put("cpu", cpuModel());
        o.put("ip", NetInfo.ipv4());
        o.put("mac", mac());
        o.put("firmware", Build.DISPLAY != null ? Build.DISPLAY : "");
        o.put("hardware", Build.HARDWARE != null ? Build.HARDWARE : "");
        o.put("injectMode", injectMode != null ? injectMode : "");
        o.put("injectOk", injectOk);
        o.put("helper", BuildConfig.VERSION_NAME);
        o.put("cacheMb", dirSize(ctx.getCacheDir()) / (1024 * 1024));
        Point size = screen(ctx);
        o.put("width", size.x);
        o.put("height", size.y);
        o.put("density", ctx.getResources().getDisplayMetrics().densityDpi);
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long total = 0;
            if (Build.VERSION.SDK_INT >= 16) {
                total = mi.totalMem;
            }
            o.put("ramMb", total > 0 ? total / (1024 * 1024) : 0);
            o.put("ramAvailMb", mi.availMem / (1024 * 1024));
        }
        StatFs st = new StatFs(Environment.getDataDirectory().getPath());
        long bs;
        long avail;
        long blocks;
        if (Build.VERSION.SDK_INT >= 18) {
            bs = st.getBlockSizeLong();
            avail = st.getAvailableBlocksLong();
            blocks = st.getBlockCountLong();
        } else {
            bs = st.getBlockSize();
            avail = st.getAvailableBlocks();
            blocks = st.getBlockCount();
        }
        o.put("storageFreeMb", (bs * avail) / (1024 * 1024));
        o.put("storageTotalMb", (bs * blocks) / (1024 * 1024));
        return o;
    }

    static long dirSize(java.io.File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }
        if (dir.isFile()) {
            return dir.length();
        }
        long n = 0;
        java.io.File[] kids = dir.listFiles();
        if (kids == null) {
            return 0;
        }
        for (int i = 0; i < kids.length; i++) {
            n += dirSize(kids[i]);
        }
        return n;
    }

    private static String mac() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                java.net.NetworkInterface ni = en.nextElement();
                byte[] hw = ni.getHardwareAddress();
                if (hw == null || hw.length < 6 || ni.isLoopback()) {
                    continue;
                }
                StringBuilder b = new StringBuilder(17);
                for (int i = 0; i < hw.length; i++) {
                    if (i > 0) {
                        b.append(':');
                    }
                    b.append(String.format("%02X", hw[i] & 0xff));
                }
                return b.toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String cpuModel() {
        String hardware = "";
        String model = "";
        java.io.BufferedReader br = null;
        try {
            br = new java.io.BufferedReader(new java.io.FileReader("/proc/cpuinfo"));
            String line;
            while ((line = br.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String val = line.substring(colon + 1).trim();
                if ("Hardware".equals(key) && val.length() > 0) {
                    hardware = val;
                } else if ("model name".equals(key) && model.length() == 0 && val.length() > 0) {
                    model = val;
                } else if ("Processor".equals(key) && model.length() == 0 && val.length() > 0) {
                    model = val;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Exception ignored) {
                }
            }
        }
        if (hardware.length() > 0 && model.length() > 0 && !hardware.equals(model)) {
            return hardware + " / " + model;
        }
        if (hardware.length() > 0) {
            return hardware;
        }
        if (model.length() > 0) {
            return model;
        }
        return Build.HARDWARE != null ? Build.HARDWARE : "";
    }

    private static String prop(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method m = c.getMethod("get", String.class, String.class);
            Object v = m.invoke(null, key, "");
            return v != null ? v.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String cpuAbi() {
        if (Build.VERSION.SDK_INT >= 21 && Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
            return Build.SUPPORTED_ABIS[0];
        }
        return Build.CPU_ABI != null ? Build.CPU_ABI : "";
    }

    private static Point screen(Context ctx) {
        Point p = new Point();
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return p;
        }
        Display d = wm.getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= 17) {
            d.getRealSize(p);
        } else {
            p.x = d.getWidth();
            p.y = d.getHeight();
        }
        return p;
    }
}
