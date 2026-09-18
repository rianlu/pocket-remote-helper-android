package com.pocketremote.helper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.pocketremote.helper.net.NetInfo;
import com.pocketremote.helper.protocol.Constants;
import com.pocketremote.helper.ui.UiBus;

/** 显示连接状态、本机 IP 与配对 PIN。 */
public final class MainActivity extends Activity implements UiBus.Listener {
    private TextView textStatus;
    private TextView textIp;
    private TextView textInject;
    private Button btnDebug;
    private final TextView[] pinDigits = new TextView[6];
    private String shownStatus = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textStatus = (TextView) findViewById(R.id.text_status);
        textIp = (TextView) findViewById(R.id.text_ip);
        pinDigits[0] = (TextView) findViewById(R.id.pin_d0);
        pinDigits[1] = (TextView) findViewById(R.id.pin_d1);
        pinDigits[2] = (TextView) findViewById(R.id.pin_d2);
        pinDigits[3] = (TextView) findViewById(R.id.pin_d3);
        pinDigits[4] = (TextView) findViewById(R.id.pin_d4);
        pinDigits[5] = (TextView) findViewById(R.id.pin_d5);
        textInject = (TextView) findViewById(R.id.text_inject);
        btnDebug = (Button) findViewById(R.id.btn_debug);
        btnDebug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDeveloperSettings();
            }
        });
        textIp.setText(getString(R.string.label_ip) + "  " + NetInfo.ipv4()
                + ":" + Constants.CONTROL_PORT);
        startService(new Intent(this, RemoteService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiBus.get().add(this);
        textIp.setText(getString(R.string.label_ip) + "  " + NetInfo.ipv4()
                + ":" + Constants.CONTROL_PORT);
        Intent reprobe = new Intent(this, RemoteService.class);
        reprobe.setAction(RemoteService.ACTION_REPROBE);
        startService(reprobe);
    }

    @Override
    protected void onPause() {
        UiBus.get().remove(this);
        super.onPause();
    }

    @Override
    public void onPinChanged(String pin) {
        boolean ready = !TextUtils.isEmpty(pin) && pin.length() == 6;
        for (int i = 0; i < 6; i++) {
            if (pinDigits[i] == null) {
                continue;
            }
            pinDigits[i].setText(ready ? String.valueOf(pin.charAt(i)) : "·");
        }
    }

    @Override
    public void onStatus(String status) {
        if (!TextUtils.isEmpty(status)) {
            textStatus.setText(status);
        }
        boolean wasPin = getString(R.string.status_pin).equals(shownStatus);
        if (!TextUtils.isEmpty(status)) {
            shownStatus = status;
        }
        // 只在刚配对成功时让出焦点；从桌面再打开不要立刻藏起来
        if (wasPin && getString(R.string.status_connected).equals(status)) {
            moveTaskToBack(true);
        }
    }

    @Override
    public void onInjectMode(String mode) {
        if (textInject == null) {
            return;
        }
        int res = R.string.inject_unknown;
        if (Constants.INJECT_PLUGIN.equals(mode)) {
            res = R.string.inject_plugin;
        } else if (Constants.INJECT_ADB.equals(mode)) {
            res = R.string.inject_adb;
        } else if (Constants.INJECT_INPUT.equals(mode)) {
            res = R.string.inject_input;
        } else if (Constants.INJECT_NONE.equals(mode)) {
            res = R.string.inject_none;
        }
        textInject.setText(getString(res));
        if (btnDebug != null) {
            btnDebug.setVisibility(Constants.INJECT_NONE.equals(mode) ? View.VISIBLE : View.GONE);
        }
    }

    private void openDeveloperSettings() {
        Intent[] intents = new Intent[] {
            new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
            new Intent(Settings.ACTION_SETTINGS),
        };
        for (int i = 0; i < intents.length; i++) {
            try {
                startActivity(intents[i]);
                return;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(this, R.string.toast_open_debug, Toast.LENGTH_LONG).show();
    }
}
