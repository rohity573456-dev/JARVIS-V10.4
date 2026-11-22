package com.jarvis.personalassistant;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * Small Siri-style orb shown only during a wake-word interaction while the
 * Activity is not visible. Android's draw-over-other-apps permission is required.
 */
public final class JarvisWakeOrbOverlay {
    private static WindowManager windowManager;
    private static JarvisOrbView orb;
    private static Context appContext;

    private JarvisWakeOrbOverlay() {}

    public static synchronized boolean show(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 23 ||
                !android.provider.Settings.canDrawOverlays(context)) return false;
        appContext = context.getApplicationContext();
        if (orb != null) {
            orb.setState("LISTENING");
            return true;
        }
        try {
            windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) return false;
            orb = new JarvisOrbView(appContext);
            orb.setState("LISTENING");
            int size = (int)(88 * appContext.getResources().getDisplayMetrics().density);
            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    size, size, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.y = (int)(46 * appContext.getResources().getDisplayMetrics().density);
            params.setTitle("JARVIS wake orb");
            windowManager.addView(orb, params);
            return true;
        } catch (Exception e) {
            orb = null;
            windowManager = null;
            android.util.Log.w("JARVIS-ORB", "Unable to show floating orb", e);
            return false;
        }
    }

    public static synchronized void setState(String state) {
        if (orb != null) orb.setState(state);
    }

    public static synchronized void hide() {
        if (orb == null || windowManager == null) return;
        try {
            if (orb.getParent() != null) windowManager.removeView(orb);
        } catch (Exception e) {
            android.util.Log.w("JARVIS-ORB", "Unable to remove floating orb", e);
        } finally {
            orb = null;
            windowManager = null;
        }
    }
}
