package com.pocketremote.helper.plugin;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;

import java.lang.reflect.Method;

/** 以 system uid 把按键/字符注入到当前焦点窗口。 */
final class InjectEngine {
    private static final String TAG = "PocketRemotePlugin";
    private static Method injectMethod;
    private static android.hardware.input.InputManager inputManager;
    private static float pointerX = -1f;
    private static float pointerY = -1f;
    private static int screenW;
    private static int screenH;
    private static boolean hoverEntered;

    static boolean key(int code) {
        if (code <= 0) {
            return false;
        }
        if (code == KeyEvent.KEYCODE_CLEAR) {
            return clearField();
        }
        return injectKeyEvent(code);
    }

    /** Ctrl+A 全选后删除，清空当前输入框。 */
    private static boolean clearField() {
        if (!injectCtrlLetter(KeyEvent.KEYCODE_A)) {
            return false;
        }
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return injectKeyEvent(KeyEvent.KEYCODE_DEL);
    }

    /** 一律剪贴板 + 粘贴一次。比 fork `input text` 快，中英文和 Chrome 都能用。 */
    static boolean text(Context ctx, String raw) {
        if (raw == null || raw.length() == 0) {
            return false;
        }
        Log.i(TAG, "text len=" + raw.length());
        return paste(ctx, raw);
    }

    private static boolean paste(Context ctx, String raw) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                return false;
            }
            cm.setPrimaryClip(ClipData.newPlainText("pocketremote", raw));
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // 只粘贴一次，避免 Chrome 里出现两遍
            if (Build.VERSION.SDK_INT >= 23 && injectKeyEvent(KeyEvent.KEYCODE_PASTE)) {
                Log.i(TAG, "paste KEYCODE_PASTE");
                return true;
            }
            if (injectCtrlLetter(KeyEvent.KEYCODE_V)) {
                Log.i(TAG, "paste CTRL+V");
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "paste", t);
        }
        return false;
    }

    private static boolean injectCtrlLetter(int letter) {
        try {
            Method m = injectMethod();
            if (m == null || inputManager == null) {
                return false;
            }
            long now = SystemClock.uptimeMillis();
            int meta = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
            KeyEvent[] ev = new KeyEvent[] {
                new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta),
                new KeyEvent(now, now, KeyEvent.ACTION_DOWN, letter, 0, meta),
                new KeyEvent(now, now, KeyEvent.ACTION_UP, letter, 0, meta),
                new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0),
            };
            for (int i = 0; i < ev.length; i++) {
                Boolean ok = (Boolean) m.invoke(inputManager, ev[i], 0);
                if (ok != null && !ok.booleanValue()) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "ctrl+letter", t);
            return false;
        }
    }

    private static boolean injectKeyEvent(int code) {
        try {
            Method m = injectMethod();
            if (m == null || inputManager == null) {
                return false;
            }
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0);
            KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0);
            Boolean a = (Boolean) m.invoke(inputManager, down, 0);
            Boolean b = (Boolean) m.invoke(inputManager, up, 0);
            return (a == null || a.booleanValue()) && (b == null || b.booleanValue());
        } catch (Throwable t) {
            Log.w(TAG, "injectInputEvent", t);
            return false;
        }
    }

    private static Method injectMethod() {
        if (injectMethod != null) {
            return injectMethod;
        }
        try {
            inputManager = (android.hardware.input.InputManager) android.hardware.input.InputManager.class
                    .getDeclaredMethod("getInstance")
                    .invoke(null);
            injectMethod = android.hardware.input.InputManager.class.getMethod(
                    "injectInputEvent", InputEvent.class, int.class);
            return injectMethod;
        } catch (Throwable t) {
            Log.w(TAG, "resolve injectInputEvent", t);
            return null;
        }
    }

    /** 相对位移鼠标。盒子不画系统指针，叠光标 + 触控点击。 */
    static boolean pointer(Context ctx, String action, int dx, int dy) {
        ensurePointer(ctx);
        if ("move".equals(action)) {
            pointerX = clamp(pointerX + dx, 0f, screenW - 1);
            pointerY = clamp(pointerY + dy, 0f, screenH - 1);
            CursorHud.showAt(ctx, pointerX, pointerY);
            if (!hoverEntered) {
                injectMouse(MotionEvent.ACTION_HOVER_ENTER, pointerX, pointerY, 0);
                hoverEntered = true;
            }
            return injectMouse(MotionEvent.ACTION_HOVER_MOVE, pointerX, pointerY, 0);
        }
        if ("down".equals(action)) {
            CursorHud.showAt(ctx, pointerX, pointerY);
            return injectMouse(MotionEvent.ACTION_DOWN, pointerX, pointerY, MotionEvent.BUTTON_PRIMARY);
        }
        if ("up".equals(action)) {
            return injectMouse(MotionEvent.ACTION_UP, pointerX, pointerY, 0);
        }
        if ("click".equals(action)) {
            CursorHud.showAt(ctx, pointerX, pointerY);
            boolean hover = injectMouse(MotionEvent.ACTION_HOVER_MOVE, pointerX, pointerY, 0);
            boolean down = injectTouch(MotionEvent.ACTION_DOWN, pointerX, pointerY);
            try {
                Thread.sleep(24);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            boolean up = injectTouch(MotionEvent.ACTION_UP, pointerX, pointerY);
            Log.i(TAG, "click " + (int) pointerX + "," + (int) pointerY + " hover=" + hover + " tap=" + (down && up));
            return down && up;
        }
        return false;
    }

    private static void ensurePointer(Context ctx) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            Display display = wm.getDefaultDisplay();
            if (Build.VERSION.SDK_INT >= 17) {
                Point p = new Point();
                display.getRealSize(p);
                screenW = Math.max(p.x, 1);
                screenH = Math.max(p.y, 1);
            } else {
                screenW = Math.max(display.getWidth(), 1);
                screenH = Math.max(display.getHeight(), 1);
            }
        }
        if (screenW <= 1 || screenH <= 1) {
            screenW = 1920;
            screenH = 1080;
        }
        if (pointerX < 0f) {
            pointerX = screenW / 2f;
            pointerY = screenH / 2f;
        }
    }

    private static float clamp(float v, float min, float max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    private static boolean injectMouse(int action, float x, float y, int buttonState) {
        return injectMotion(InputDevice.SOURCE_MOUSE, MotionEvent.TOOL_TYPE_MOUSE, action, x, y, buttonState);
    }

    private static boolean injectTouch(int action, float x, float y) {
        return injectMotion(InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.TOOL_TYPE_FINGER, action, x, y, 0);
    }

    private static boolean injectMotion(
            int source, int toolType, int action, float x, float y, int buttonState) {
        try {
            Method m = injectMethod();
            if (m == null || inputManager == null) {
                return false;
            }
            long now = SystemClock.uptimeMillis();
            MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
            pp.id = 0;
            pp.toolType = toolType;
            MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
            pc.x = x;
            pc.y = y;
            pc.pressure = (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_HOVER_MOVE
                    || action == MotionEvent.ACTION_HOVER_ENTER) ? 0f : 1f;
            pc.size = 1f;
            MotionEvent ev = MotionEvent.obtain(
                    now,
                    now,
                    action,
                    1,
                    new MotionEvent.PointerProperties[] {pp},
                    new MotionEvent.PointerCoords[] {pc},
                    0,
                    buttonState,
                    1f,
                    1f,
                    0,
                    0,
                    source,
                    0);
            Boolean ok = (Boolean) m.invoke(inputManager, ev, 0);
            ev.recycle();
            return ok == null || ok.booleanValue();
        } catch (Throwable t) {
            Log.w(TAG, "motion source=" + source, t);
            return false;
        }
    }

}
