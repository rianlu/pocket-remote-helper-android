package com.pockettv.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class Prefs {
    private static final String FILE = "pockettv_helper";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_TOKENS = "tokens";
    private static final String SEP = ",";

    private final SharedPreferences prefs;

    public Prefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public synchronized String deviceId() {
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (TextUtils.isEmpty(id)) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).commit();
        }
        return id;
    }

    public synchronized boolean isTokenValid(String token) {
        if (TextUtils.isEmpty(token)) {
            return false;
        }
        return tokens().contains(token);
    }

    public synchronized void addToken(String token) {
        if (TextUtils.isEmpty(token)) {
            return;
        }
        Set<String> set = tokens();
        set.add(token);
        prefs.edit().putString(KEY_TOKENS, TextUtils.join(SEP, set)).commit();
    }

    private Set<String> tokens() {
        String raw = prefs.getString(KEY_TOKENS, "");
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        if (!TextUtils.isEmpty(raw)) {
            String[] parts = raw.split(SEP);
            Collections.addAll(set, parts);
        }
        return set;
    }
}
