package it.ampy.ampypad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Small painted parts of a block row: drag handle, list markers / checkbox, divider. */
public final class BlockViews {
    private BlockViews() { }

    /** Six-dot drag handle ("⠿"), drawn on the first row of the block. */
    public static final class Handle extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int lh; int color; boolean lifted;
        public Handle(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = lh / 2f, g = Math.max(2.5f, getWidth() * 0.16f), r = Math.max(1.2f, getWidth() * 0.065f);
            p.setColor(lifted ? color : Theme.a(color, 70));
            for (int i = -1; i <= 1; i++) { c.drawCircle(cx - g / 2, cy + i * g, r, p); c.drawCircle(cx + g / 2, cy + i * g, r, p); }
        }
    }

    /** Bullet, number, checkbox, callout emoji or quote bar. */
    public static final class Marker extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        String type = "p", emoji = "💡"; int number = 1, indent, lh, base, ink = 0xff000000; boolean checked; float fs = 30; Typeface tf = Typeface.DEFAULT;
        long checkAnim;
        public Marker(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), mid = lh / 2f;
            p.setStyle(Paint.Style.FILL); p.setColor(ink); p.setTypeface(tf);
            switch (type) {
                case "bullet":
                    if (indent % 2 == 1) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(fs * 0.08f); }
                    c.drawCircle(w * 0.45f, base - fs * 0.32f, fs * 0.15f, p);
                    break;
                case "num":
                    p.setTextSize(fs); p.setTextAlign(Paint.Align.RIGHT);
                    c.drawText(number + ".", w - fs * 0.25f, base, p);
                    break;
                case "todo": {
                    float s = Math.min(w * 0.72f, fs * 0.95f), x = (w - s) / 2f, y = base - s * 0.88f;
                    RectF r = new RectF(x, y, x + s, y + s);
                    float k = Math.min(1f, (System.currentTimeMillis() - checkAnim) / 260f);
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, s * 0.11f));
                    c.drawRoundRect(r, s * 0.24f, s * 0.24f, p);
                    if (checked) {
                        p.setStyle(Paint.Style.FILL);
                        float sc = 0.6f + 0.4f * k + (k < 1 ? 0.15f * (float) Math.sin(k * Math.PI) : 0);
                        c.save(); c.scale(sc, sc, r.centerX(), r.centerY());
                        c.drawRoundRect(r, s * 0.24f, s * 0.24f, p);
                        p.setColor(Ui.luminance(ink) > 0.5f ? 0xff101010 : 0xffffffff);
                        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(s * 0.14f); p.setStrokeCap(Paint.Cap.ROUND);
                        c.drawLine(x + s * 0.24f, y + s * 0.52f, x + s * 0.43f, y + s * 0.72f, p);
                        c.drawLine(x + s * 0.43f, y + s * 0.72f, x + s * 0.78f, y + s * 0.3f, p);
                        c.restore();
                        if (k < 1) postInvalidateOnAnimation();
                    }
                    break;
                }
                case "callout":
                    p.setTextSize(fs * 0.95f); p.setTextAlign(Paint.Align.CENTER);
                    c.drawText(emoji, w / 2f, base, p);
                    break;
                case "quote":
                    p.setAlpha(140);
                    c.drawRoundRect(new RectF(w * 0.3f, mid * 0.25f, w * 0.3f + Math.max(3f, fs * 0.12f), getHeight() - mid * 0.25f), 4, 4, p);
                    break;
                default: break;
            }
        }
    }

    /** Horizontal rule block. */
    public static final class Divider extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int ink = 0xff000000;
        public Divider(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float y = getHeight() / 2f, w = getWidth();
            p.setColor(Theme.a(ink, 110)); p.setStrokeWidth(Math.max(2f, getHeight() * 0.06f)); p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(w * 0.04f, y, w * 0.96f, y, p);
            p.setStyle(Paint.Style.FILL); p.setColor(ink);
            c.drawCircle(w / 2f, y, getHeight() * 0.09f, p);
        }
    }
}
