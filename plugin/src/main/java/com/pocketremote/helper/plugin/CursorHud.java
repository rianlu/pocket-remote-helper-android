package com.pocketremote.helper.plugin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/** 盒子通常不画系统鼠标指针，用系统窗叠一层可见光标。 */
final class CursorHud {
    private static final String TAG = "PocketRemotePlugin";
    private static final int SIZE_PX = 56;
    private static final long HIDE_MS = 8000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static WindowManager wm;
    private static WindowManager.LayoutParams lp;
    private static CursorView view;
    private static boolean attached;
    private static int typeIndex;
    private static final Runnable HIDE = new Runnable() {
        @Override
        public void run() {
            detach();
        }
    };

    static void showAt(final Context ctx, final float x, final float y) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                ensure(ctx.getApplicationContext());
                if (lp == null || view == null) {
                    return;
                }
                lp.x = Math.round(x);
                lp.y = Math.round(y);
                if (attached) {
                    try {
                        wm.updateViewLayout(view, lp);
                    } catch (Throwable t) {
                        Log.w(TAG, "cursor update", t);
                    }
                    MAIN.removeCallbacks(HIDE);
                    MAIN.postDelayed(HIDE, HIDE_MS);
                    return;
                }
                int[] types = windowTypes();
                while (typeIndex < types.length) {
                    lp.type = types[typeIndex];
                    try {
                        wm.addView(view, lp);
                        attached = true;
                        Log.i(TAG, "cursor type=" + lp.type);
                        break;
                    } catch (Throwable t) {
                        Log.w(TAG, "cursor type=" + lp.type, t);
                        typeIndex++;
                    }
                }
                if (attached) {
                    MAIN.removeCallbacks(HIDE);
                    MAIN.postDelayed(HIDE, HIDE_MS);
                }
            }
        });
    }

    private static void ensure(Context ctx) {
        if (view != null) {
            return;
        }
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return;
        }
        view = new CursorView(ctx);
        lp = new WindowManager.LayoutParams(
                SIZE_PX,
                SIZE_PX,
                windowTypes()[0],
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = 0;
        lp.y = 0;
    }

    private static int[] windowTypes() {
        if (Build.VERSION.SDK_INT >= 26) {
            return new int[] { 2038, 2010, 2006, 2003 };
        }
        return new int[] { 2010, 2006, 2002 };
    }

    private static void detach() {
        if (!attached || wm == null || view == null) {
            return;
        }
        try {
            wm.removeView(view);
        } catch (Throwable ignored) {
        }
        attached = false;
    }

    private static final class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        CursorView(Context context) {
            super(context);
            fill.setColor(0xFFFFC14A);
            fill.setStyle(Paint.Style.FILL);
            stroke.setColor(0xFF2A1C00);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(2.5f);
            stroke.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            path.reset();
            path.moveTo(3f, 3f);
            path.lineTo(3f, h * 0.92f);
            path.lineTo(w * 0.38f, h * 0.68f);
            path.lineTo(w * 0.92f, h * 0.92f * 0.55f);
            path.close();
            canvas.drawPath(path, fill);
            canvas.drawPath(path, stroke);
        }
    }
}
