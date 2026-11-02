package it.ampy.ampypad;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** Page boundary editor: 4 corners + 4 edge bulge handles (curved edges), neon scan animation, magnifier loupe. */
public final class CropView extends View {
    private Bitmap bmp;
    private float[] q, from;
    private final Theme th;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private float scale = 1, ox, oy, touchX, touchY;
    private int drag = -1;
    private long animAt;
    private final float d;
    private BitmapShader loupe;

    public CropView(Context c, Theme th) {
        super(c);
        this.th = th;
        d = c.getResources().getDisplayMetrics().density;
    }

    public void set(Bitmap b, float[] quad, boolean animate) {
        bmp = b; q = quad;
        loupe = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        from = ScanCore.full(b.getWidth(), b.getHeight(), 0f);
        animAt = animate ? SystemClock.uptimeMillis() : 0;
        invalidate();
    }
    public void setQuad(float[] quad, boolean animate) {
        from = q.clone(); q = quad;
        animAt = animate ? SystemClock.uptimeMillis() : 0;
        invalidate();
    }
    public float[] quad() { return q; }

    private void fit() {
        float w = getWidth(), h = getHeight(), pad = 22 * d;
        scale = Math.min((w - 2 * pad) / bmp.getWidth(), (h - 2 * pad) / bmp.getHeight());
        ox = (w - bmp.getWidth() * scale) / 2; oy = (h - bmp.getHeight() * scale) / 2;
    }
    private float vx(float x) { return ox + x * scale; }
    private float vy(float y) { return oy + y * scale; }

    private void buildPath(float[] a) {
        path.reset();
        path.moveTo(vx(a[0]), vy(a[1]));
        int[] order = {0, 1, 2, 3};
        for (int e : order) {
            int b = (e + 1) % 4;
            float ax = a[e * 2], ay = a[e * 2 + 1], bx = a[b * 2], by = a[b * 2 + 1];
            float mx = a[8 + e * 2], my = a[9 + e * 2];
            float cx = 2 * mx - (ax + bx) / 2, cy = 2 * my - (ay + by) / 2;
            path.quadTo(vx(cx), vy(cy), vx(bx), vy(by));
        }
        path.close();
    }

    @Override protected void onDraw(Canvas c) {
        if (bmp == null) return;
        fit();
        RectF dst = new RectF(ox, oy, ox + bmp.getWidth() * scale, oy + bmp.getHeight() * scale);
        c.drawBitmap(bmp, null, dst, p);
        float t = animAt == 0 ? 1 : Ui.clamp((SystemClock.uptimeMillis() - animAt) / 750f, 0, 1);
        float e = 1 - (float) Math.pow(1 - t, 3);
        float[] a = new float[16];
        for (int i = 0; i < 16; i++) a[i] = from[i] + (q[i] - from[i]) * e;
        buildPath(a);
        c.save();
        c.clipOutPath(path);
        p.setStyle(Paint.Style.FILL); p.setColor(0x9a000000);
        c.drawRect(dst, p);
        c.restore();
        p.setStyle(Paint.Style.FILL); p.setColor(Theme.a(th.accent, 26));
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.4f * d); p.setColor(th.accent);
        p.setShadowLayer(9 * d, 0, 0, Theme.a(th.accent, 200));
        c.drawPath(path, p);
        p.clearShadowLayer();
        if (t < 1) {
            float y = dst.top + dst.height() * t;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3 * d); p.setColor(th.secondary);
            p.setShadowLayer(14 * d, 0, 0, th.secondary);
            c.drawLine(dst.left, y, dst.right, y, p);
            p.clearShadowLayer();
            postInvalidateOnAnimation();
            return;
        }
        for (int i = 0; i < 8; i++) {
            float x = vx(a[i < 4 ? i * 2 : 8 + (i - 4) * 2]), y = vy(a[i < 4 ? i * 2 + 1 : 9 + (i - 4) * 2]);
            float r = (i < 4 ? 13 : 9) * d * (drag == i ? 1.3f : 1f);
            p.setStyle(Paint.Style.FILL); p.setColor(i < 4 ? 0xccffffff : Theme.a(th.secondary, 220));
            c.drawCircle(x, y, r, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * d); p.setColor(th.accent);
            c.drawCircle(x, y, r, p);
        }
        if (drag >= 0) drawLoupe(c);
    }

    private void drawLoupe(Canvas c) {
        int i = drag;
        float ix = q[i < 4 ? i * 2 : 8 + (i - 4) * 2], iy = q[i < 4 ? i * 2 + 1 : 9 + (i - 4) * 2];
        float R = 62 * d, z = 2.6f;
        float cx = touchX < getWidth() / 2f ? getWidth() - R - 14 * d : R + 14 * d, cy = R + 14 * d;
        Matrix m = new Matrix();
        m.setScale(scale * z, scale * z);
        m.postTranslate(cx - ix * scale * z, cy - iy * scale * z);
        loupe.setLocalMatrix(m);
        p.setStyle(Paint.Style.FILL); p.setShader(loupe);
        c.drawCircle(cx, cy, R, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3 * d); p.setColor(th.accent);
        c.drawCircle(cx, cy, R, p);
        p.setStrokeWidth(1.5f * d); p.setColor(th.secondary);
        c.drawLine(cx - 14 * d, cy, cx + 14 * d, cy, p); c.drawLine(cx, cy - 14 * d, cx, cy + 14 * d, p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (bmp == null) return false;
        touchX = e.getX(); touchY = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                drag = -1; float best = 44 * d;
                for (int i = 0; i < 8; i++) {
                    float x = vx(q[i < 4 ? i * 2 : 8 + (i - 4) * 2]), y = vy(q[i < 4 ? i * 2 + 1 : 9 + (i - 4) * 2]);
                    float dist = (float) Math.hypot(x - touchX, y - touchY);
                    if (dist < best) { best = dist; drag = i; }
                }
                if (drag >= 0) { Ui.tick(this); getParent().requestDisallowInterceptTouchEvent(true); }
                invalidate();
                return drag >= 0;
            }
            case MotionEvent.ACTION_MOVE: {
                if (drag < 0) return false;
                float nx = Ui.clamp((touchX - ox) / scale, 0, bmp.getWidth() - 1), ny = Ui.clamp((touchY - oy) / scale, 0, bmp.getHeight() - 1);
                if (drag < 4) {
                    float dx = nx - q[drag * 2], dy = ny - q[drag * 2 + 1];
                    q[drag * 2] = nx; q[drag * 2 + 1] = ny;
                    int m1 = drag, m2 = (drag + 3) % 4; // adjacent edges: drag (from this corner) and drag-1 (into it)
                    q[8 + m1 * 2] += dx / 2; q[9 + m1 * 2] += dy / 2;
                    q[8 + m2 * 2] += dx / 2; q[9 + m2 * 2] += dy / 2;
                } else {
                    q[8 + (drag - 4) * 2] = nx; q[9 + (drag - 4) * 2] = ny;
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                drag = -1; invalidate();
                return true;
            default: return true;
        }
    }
}
