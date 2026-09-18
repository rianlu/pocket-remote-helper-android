package com.pocketremote.helper.store;

import java.util.Random;
import com.pocketremote.helper.protocol.Constants;

/** 6 位 PIN，60 秒有效，错误 3 次关闭。 */
public final class PinSession {
    private final Object lock = new Object();
    private String pin;
    private long expiresAt;
    private int fails;

    public String begin() {
        synchronized (lock) {
            pin = String.format("%06d", new Random().nextInt(1000000));
            expiresAt = System.currentTimeMillis() + Constants.PIN_TTL_MS;
            fails = 0;
            return pin;
        }
    }

    public void clear() {
        synchronized (lock) {
            pin = null;
            expiresAt = 0;
            fails = 0;
        }
    }

    public String current() {
        synchronized (lock) {
            if (pin == null || System.currentTimeMillis() > expiresAt) {
                return "";
            }
            return pin;
        }
    }

    /**
     * @return 1 ok, 0 wrong (still allowed), -1 locked
     */
    public int check(String input) {
        synchronized (lock) {
            if (pin == null || System.currentTimeMillis() > expiresAt) {
                fails++;
                if (fails >= Constants.PIN_MAX_FAIL) {
                    clear();
                    return -1;
                }
                return 0;
            }
            if (pin.equals(input)) {
                clear();
                return 1;
            }
            fails++;
            if (fails >= Constants.PIN_MAX_FAIL) {
                clear();
                return -1;
            }
            return 0;
        }
    }
}
