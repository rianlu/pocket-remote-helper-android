package com.pockettv.tv.protocol;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.UUID;
import com.pockettv.tv.inject.Injector;
import com.pockettv.tv.store.Prefs;
import com.pockettv.tv.store.PinSession;
import com.pockettv.tv.store.AppCatalog;
import com.pockettv.tv.ui.UiBus;
import com.pockettv.tv.R;

/** 解析 WebSocket JSON，按 type 分发给注入/应用/配对逻辑。 */
public final class CommandProcessor {
    private final Context app;
    private final Prefs prefs;
    private final Injector injector;
    private final PinSession pins;
    private final AppCatalog apps;
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
        return error(id, Constants.ERR_PROTOCOL, type);
    }

    public String hello(String id, JSONObject payload) throws Exception {
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
        p.put("injectOk", injector.probe());
        return p;
    }

    private String helloOk(String id, String token) throws Exception {
        return msg(id, Constants.TYPE_HELLO_OK, helloOkPayload(token));
    }

    public boolean installUploaded(File file) {
        return apps.installApk(file);
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
