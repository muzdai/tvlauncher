package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/**
 * tvlauncher fork: a one-line news ticker that scrolls right to left at a set speed, wrapping round. Unlike a
 * TextView marquee its speed and size are the owner's (Launcher settings). It animates only while running
 * (Home resumed) and draws nothing when empty.
 */
final class NewsTicker extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The dark rounded band behind the text; its alpha follows the owner's transparency setting. */
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF bandRect = new android.graphics.RectF();
    private final float density;
    private String text = "";
    private float textWidth;
    private float offset;
    private float speedDpPerSecond = 60f;
    private long lastFrameNanos;
    private boolean running;

    NewsTicker(Context ctx, Typeface typeface) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        paint.setColor(0xE6FFFFFF);
        paint.setTypeface(typeface);
        paint.setShadowLayer(6 * density, 0, 0, 0xCC000000);
        band.setColor(0xFF000000);
        setBandTransparency(50);
        setTextSizeSp(22);
        setFocusable(false);
    }

    void setTextSizeSp(float sp) {
        paint.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        textWidth = paint.measureText(text);
        requestLayout();
        invalidate();
    }

    /** 0 = solid black band, 100 = no band at all. */
    void setBandTransparency(int percent) {
        int p = Math.max(0, Math.min(100, percent));
        band.setAlpha(Math.round(255f * (100 - p) / 100f));
        invalidate();
    }

    void setSpeed(float dpPerSecond) {
        speedDpPerSecond = Math.max(5f, dpPerSecond);
    }

    void setText(String value) {
        String v = value == null ? "" : value;
        if (v.equals(text)) return;
        boolean first = text.isEmpty();
        text = v;
        textWidth = paint.measureText(text);
        if (first) offset = 0;
        invalidate();
    }

    String getText() {
        return text;
    }

    void setRunning(boolean on) {
        if (running == on) return;
        running = on;
        lastFrameNanos = 0;
        if (on) postInvalidateOnAnimation();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        int h = (int) Math.ceil(fm.descent - fm.ascent + 20 * density);
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (text.isEmpty()) return;
        // The band, then the text clipped to its inside so it slides in and out under rounded ends.
        float radius = 16 * density, inset = 20 * density;
        bandRect.set(0, 0, getWidth(), getHeight());
        if (band.getAlpha() > 0) canvas.drawRoundRect(bandRect, radius, radius, band);
        canvas.save();
        canvas.clipRect(inset, 0, getWidth() - inset, getHeight());
        float gap = 120 * density;
        float period = textWidth + gap;
        if (running) {
            long now = System.nanoTime();
            if (lastFrameNanos != 0) {
                float dt = Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
                offset = (offset + speedDpPerSecond * density * dt) % period;
            }
            lastFrameNanos = now;
        }
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = (getHeight() - (fm.descent + fm.ascent)) / 2f;
        // Start just off the right edge, then wrap: as many copies as needed to fill the width.
        for (float x = getWidth() - offset; x < getWidth() + period; x += period) {
            if (x + textWidth > 0) canvas.drawText(text, x, y, paint);
        }
        for (float x = getWidth() - offset - period; x + textWidth > 0; x -= period) {
            canvas.drawText(text, x, y, paint);
        }
        canvas.restore();
        if (running) postInvalidateOnAnimation();
    }
}
