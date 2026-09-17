package com.pockettv.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

public final class MainActivity extends Activity implements UiBus.Listener {
    private TextView textStatus;
    private TextView textPin;
    private TextView textIp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textStatus = (TextView) findViewById(R.id.text_status);
        textPin = (TextView) findViewById(R.id.text_pin);
        textIp = (TextView) findViewById(R.id.text_ip);
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
    }

    @Override
    protected void onPause() {
        UiBus.get().remove(this);
        super.onPause();
    }

    @Override
    public void onPinChanged(String pin) {
        if (TextUtils.isEmpty(pin)) {
            textPin.setText("------");
        } else {
            textPin.setText(pin);
        }
    }

    @Override
    public void onStatus(String status) {
        if (!TextUtils.isEmpty(status)) {
            textStatus.setText(status);
        }
    }
}
