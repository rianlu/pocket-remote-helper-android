package com.pocketremote.helper.protocol;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import com.pocketremote.helper.MainActivity;

import org.json.JSONObject;

import java.io.File;
import java.util.UUID;
import com.pocketremote.helper.inject.Injector;
import com.pocketremote.helper.store.Prefs;
import com.pocketremote.helper.store.PinSession;
import com.pocketremote.helper.store.AppCatalog;
import com.pocketremote.helper.store.TransferStore;
import com.pocketremote.helper.store.SysInfo;
import com.pocketremote.helper.store.Cleaner;
import com.pocketremote.helper.ui.UiBus;
import com.pocketremote.helper.R;

/** 解析 WebSocket JSON，按 type 分发给注入/应用/配对逻辑。 */
public final class CommandProcessor {
    private final Context app;
    private final Prefs prefs;
    private final Injector injector;
    private final PinSession pins;
    private final AppCatalog apps;
    private final TransferStore store = new TransferStore();
    private final Handler main = new Handler(Looper.getMainLooper());

    public CommandProcessor(Context context, Prefs prefs, Injector injector, PinSession pins) {
        this.app = context.getApplicationContext();
        this.prefs = prefs;
        this.injector = injector;
        this.pins = pins;
        this.apps = new AppCatalog(app);
    }

    /**
     * @param authorized 本条 WebSocket 连接是否已 hello_ok
     * @return 要回给客户端的 JSON；无需回复时返回 null
     */
    public String handle(String raw, boolean authorized) throws Exception {
        JSONObject in = new JSONObject(raw);
        int v = in.optInt("v", 0);
        String id = in.optString("id", "");
        String type = in.optString("type", "");
        JSONObject payload = in.optJSONObject("payload");
        if (payload == null) {
            payload = new JSONObject();
        }
        if (v != Constants.PROTOCOL_V) {
            return error(id, Constants.ERR_PROTOCOL, "v");
        }
        if (Constants.TYPE_HELLO.equals(type)) {
            return hello(id, payload);
        }
        if (!authorized) {
            return error(id, Constants.ERR_AUTH, "token");
        }
        if (Constants.TYPE_KEY.equals(type)) {
            int code = payload.optInt("code", 0);
            if (!injector.key(code)) {
                return error(id, Constants.ERR_INJECT, "key");
            }
            return null;
        }
        if (Constants.TYPE_POINTER.equals(type)) {
            String action = payload.optString("action", "move");
            int dx = payload.optInt("dx", 0);
            int dy = payload.optInt("dy", 0);
            if (!injector.pointer(action, dx, dy)) {
                return error(id, Constants.ERR_INJECT, "pointer");
            }
            return null;
        }
        if (Constants.TYPE_INFO.equals(type)) {
            boolean ok = injector.probe();
            return msg(id, Constants.TYPE_INFO_OK, SysInfo.snapshot(app, injector.mode(), ok));
        }
        if (Constants.TYPE_CLEAN.equals(type)) {
            try {
                return msg(id, Constants.TYPE_CLEAN_OK, Cleaner.run(app, injector));
            } catch (Exception e) {
                return error(id, Constants.ERR_FS, "clean");
            }
        }
        if (Constants.TYPE_TEXT.equals(type)) {
            if (!injector.text(payload.optString("text", ""))) {
                return error(id, Constants.ERR_TEXT, "text");
            }
            return null;
        }
        if (Constants.TYPE_APPS.equals(type)) {
            JSONObject p = new JSONObject();
            p.put("apps", apps.list());
            return msg(id, Constants.TYPE_APPS_OK, p);
        }
        if (Constants.TYPE_APP_OPEN.equals(type)) {
            if (!apps.open(payload.optString("pkg", ""))) {
                return error(id, Constants.ERR_FS, "open");
            }
            return null;
        }
        if (Constants.TYPE_APP_UNINSTALL.equals(type)) {
            apps.uninstall(payload.optString("pkg", ""));
            return null;
        }
        if (Constants.TYPE_APK_INSTALL.equals(type)) {
            File f = null;
            String path = payload.optString("path", "");
            if (path.length() > 0) {
                f = store.resolveInstallable(path);
            }
            if (f == null) {
                String name = payload.optString("name", "");
                if (name.length() > 0 && name.toLowerCase(java.util.Locale.US).endsWith(".apk")) {
                    f = store.findByName(name);
                }
            }
            if (f == null || !f.isFile()) {
                return error(id, Constants.ERR_FS, "apk");
            }
            apps.installApk(f);
            return null;
        }
        return error(id, Constants.ERR_PROTOCOL, type);
    }

    public String hello(String id, JSONObject payload) throws Exception {
        injector.setPreferred(payload.optString("inject", ""));
        String token = payload.optString("token", "");
        String pin = payload.optString("pin", "");
        if (prefs.isTokenValid(token)) {
            UiBus.get().postPin("");
            UiBus.get().postStatus(app.getString(R.string.status_connected));
            return helloOk(id, token);
        }
        if (!TextUtils.isEmpty(pin)) {
            int r = pins.check(pin);
            if (r == 1) {
                String neu = UUID.randomUUID().toString();
                prefs.addToken(neu);
                UiBus.get().postPin("");
                UiBus.get().postStatus(app.getString(R.string.status_connected));
                return helloOk(id, neu);
            }
            UiBus.get().postPin(pins.current());
            return error(id, Constants.ERR_AUTH, r < 0 ? "locked" : "pin");
        }
        String shown = pins.begin();
        UiBus.get().postPin(shown);
        UiBus.get().postStatus(app.getString(R.string.status_pin));
        // 手机发起配对时，自动把 TV 端界面拉到前台显示配对码
        main.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent bring = new Intent(app, MainActivity.class);
                    bring.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    app.startActivity(bring);
                } catch (Exception e) {
                    android.util.Log.w("PocketRemote", "bring to front failed", e);
                }
            }
        });
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (TextUtils.isEmpty(pins.current())) {
                    UiBus.get().postPin("");
                    UiBus.get().postStatus(app.getString(R.string.status_waiting));
                }
            }
        }, Constants.PIN_TTL_MS);
        return msg(id, Constants.TYPE_NEED_PIN, new JSONObject());
    }

    public JSONObject helloOkPayload(String token) throws Exception {
        JSONObject p = new JSONObject();
        p.put("token", token);
        p.put("tvName", Build.MODEL);
        p.put("sdk", Build.VERSION.SDK_INT);
        boolean ok = injector.probe();
        String mode = injector.mode();
        p.put("injectOk", ok);
        p.put("injectMode", mode);
        UiBus.get().postInjectMode(mode);
        return p;
    }

    private String helloOk(String id, String token) throws Exception {
        return msg(id, Constants.TYPE_HELLO_OK, helloOkPayload(token));
    }

    public boolean installUploaded(File file) {
        return apps.installApk(file);
    }

    public byte[] appIcon(String pkg) {
        return apps.iconPng(pkg);
    }

    public byte[] archiveIcon(String path) {
        File f = store.resolveInstallable(path);
        return apps.archiveIcon(f);
    }

    /** 把应用 APK 拷到可读目录。系统应用与分体包拒绝。调用方读完应删除。 */
    public java.io.File extractApk(String pkg) {
        if (!apps.canExtract(pkg)) {
            return null;
        }
        java.io.File src = apps.apkSource(pkg);
        if (src == null) {
            return null;
        }
        java.io.File dest = new java.io.File(
                android.os.Environment.getExternalStorageDirectory(),
                Constants.ROOT_DIR_NAME + "/" + Constants.DIR_APK + "/" + pkg + ".apk");
        dest.getParentFile().mkdirs();
        try {
            if (src.canRead()) {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(dest);
                try {
                    AppCatalog.copyFile(src, fos);
                } finally {
                    fos.close();
                }
                return dest;
            }
        } catch (Exception ignored) {
        }
        if (injector.copyFile(src.getAbsolutePath(), dest.getAbsolutePath()) && dest.isFile()) {
            return dest;
        }
        return null;
    }

    public static String error(String id, String code, String message) throws Exception {
        JSONObject p = new JSONObject();
        p.put("code", code);
        p.put("message", message);
        return msg(id, Constants.TYPE_ERROR, p);
    }

    public static String msg(String id, String type, JSONObject payload) throws Exception {
        JSONObject o = new JSONObject();
        o.put("v", Constants.PROTOCOL_V);
        o.put("id", id);
        o.put("type", type);
        o.put("payload", payload);
        return o.toString();
    }
}
