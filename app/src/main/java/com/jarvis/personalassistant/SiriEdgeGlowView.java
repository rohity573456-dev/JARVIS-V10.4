package com.jarvis.personalassistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

/**
 * Native Android recreation of a Siri-inspired full-screen edge glow.
 * Draws only while the assistant is listening, thinking, or speaking.
 */
public final class SiriEdgeGlowView extends View {
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame = new RectF();
    private final Matrix shaderMatrix = new Matrix();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;
    private final long startedAt = SystemClock.uptimeMillis();
    private SweepGradient rainbow;
    private final int[] rainbowColors = new int[] {
        0xff42d9ff, 0xff617bff, 0xffbd65ff, 0xffff68c8,
        0xffffa45b, 0xffffe17a, 0xff65f0d0, 0xff42d9ff
    };
    private final float[] rainbowStops = new float[] {0f, .15f, .30f, .45f, .60f, .74f, .88f, 1f};
    private String state = "STANDBY";

    private final Runnable thinkingTimeout = () -> setState("STANDBY");

    public SiriEdgeGlowView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setContentDescription(null);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rainbow = new SweepGradient(w * .5f, h * .5f, rainbowColors, rainbowStops);
        glowPaint.setShader(rainbow);
    }

    public void setState(String nextState) {
        String next = nextState == null ? "STANDBY" : nextState;
        if (next.equals(state)) return;
        state = next;
        handler.removeCallbacks(thinkingTimeout);
        if ("THINKING".equals(state)) {
            handler.postDelayed(thinkingTimeout, 9000L);
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if ("STANDBY".equals(state) || rainbow == null || getWidth() <= 0 || getHeight() <= 0) return;

        float t = (SystemClock.uptimeMillis() - startedAt) / 1000f;
        float speed = "THINKING".equals(state) ? 145f :
                ("SPEAKING".equals(state) ? 92f : 48f);
        float pulse = .88f + .12f * (float)Math.sin(t *
                ("SPEAKING".equals(state) ? 5.2f : 2.8f));
        float rotation = (t * speed) % 360f;
        shaderMatrix.setRotate(rotation, getWidth() * .5f, getHeight() * .5f);
        rainbow.setLocalMatrix(shaderMatrix);

        float inset = 3.5f * density;
        frame.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float radius = 27f * density;

        glowPaint.setAlpha((int)(42 * pulse));
        glowPaint.setStrokeWidth(22f * density);
        canvas.drawRoundRect(frame, radius, radius, glowPaint);

        glowPaint.setAlpha((int)(94 * pulse));
        glowPaint.setStrokeWidth(11f * density);
        canvas.drawRoundRect(frame, radius, radius, glowPaint);

        glowPaint.setAlpha((int)(235 * pulse));
        glowPaint.setStrokeWidth(3.2f * density);
        canvas.drawRoundRect(frame, radius, radius, glowPaint);

        if (isAttachedToWindow()) postInvalidateOnAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
