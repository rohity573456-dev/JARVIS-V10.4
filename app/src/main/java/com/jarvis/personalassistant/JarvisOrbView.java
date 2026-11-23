package com.jarvis.personalassistant;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/** Lightweight animated JARVIS energy core; uses Canvas only, no external assets. */
public final class JarvisOrbView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private long startedAt = SystemClock.uptimeMillis();
    private volatile String state = "STANDBY";

    public JarvisOrbView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        setContentDescription("Animated JARVIS energy core");
    }

    public void setState(String value) {
        state = value == null ? "STANDBY" : value;
        invalidate();
        if ("SPEAKING".equals(state) || "THINKING".equals(state)) {
            final String current = state;
            handler.postDelayed(() -> {
                if (current.equals(state)) setState("STANDBY");
            }, 2400);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(), h=getHeight();
        if (w<=0 || h<=0) return;
        float cx=w*0.5f, cy=h*0.48f;
        float radius=Math.min(w*0.24f,h*0.34f);
        float t=(SystemClock.uptimeMillis()-startedAt)/1000f;
        float pulse=1.0f+0.035f*(float)Math.sin(t*2.35f);
        boolean active="LISTENING".equals(state) || "THINKING".equals(state);
        int core= "SPEAKING".equals(state) ? Color.rgb(190,170,255) :
            ("LISTENING".equals(state) ? Color.rgb(130,225,255) : Color.rgb(110,170,255));

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx,cy,radius*1.55f,
            new int[]{0x4430dfff,0x1820aaff,0x00000000},null,Shader.TileMode.CLAMP));
        canvas.drawCircle(cx,cy,radius*1.55f*pulse,paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.1f);
        paint.setColor(0x3330dfff);
        canvas.drawCircle(cx,cy,radius*1.38f*pulse,paint);
        paint.setStrokeWidth(2.0f);
        paint.setColor(0x6630dfff);
        canvas.drawCircle(cx,cy,radius*1.22f,paint);

        paint.setStrokeWidth(2.5f);
        paint.setColor(core);
        oval.set(cx-radius*1.08f,cy-radius*1.08f,cx+radius*1.08f,cy+radius*1.08f);
        canvas.drawArc(oval,(t*52f)%360f,112,false,paint);
        canvas.drawArc(oval,180f+(t*52f)%360f,72,false,paint);

        paint.setStrokeWidth(1.4f);
        paint.setColor(0x8860dfff);
        oval.set(cx-radius*0.91f,cy-radius*0.91f,cx+radius*0.91f,cy+radius*0.91f);
        canvas.save();
        canvas.rotate(-t*82f,cx,cy);
        canvas.drawArc(oval,20,95,false,paint);
        canvas.drawArc(oval,205,70,false,paint);
        canvas.restore();

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx-radius*0.28f,cy-radius*0.30f,radius*1.15f,
            new int[]{0xffd9ffff,core,0xff075078,0xff020b20},
            new float[]{0f,0.22f,0.68f,1f},Shader.TileMode.CLAMP));
        canvas.drawCircle(cx,cy,radius*0.78f,paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0x99bafaff);
        canvas.drawCircle(cx,cy,radius*0.78f,paint);

        paint.setColor(0xffb9f7ff);
        for(int i=0;i<12;i++) {
            double a=t*(active?1.5:0.75)+i*Math.PI/6.0;
            float orbit=radius*(1.13f+(i%3)*0.055f);
            float px=cx+(float)Math.cos(a)*orbit;
            float py=cy+(float)Math.sin(a)*orbit*0.72f;
            paint.setAlpha(115+(int)(100*(0.5+0.5*Math.sin(t*2.0+i))));
            canvas.drawCircle(px,py,1.4f+(i%3)*0.55f,paint);
        }
        paint.setAlpha(255);
        // Follow the display vsync instead of a fixed 32 ms timer (about 31 FPS).
        // Android will schedule this at the supported panel cadence, including 90 Hz.
        if (isAttachedToWindow()) postInvalidateOnAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
