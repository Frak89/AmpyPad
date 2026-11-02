package it.ampy.ampypad;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

/** Custom painted widgets of the main screen. */
public final class Views {
    private Views() { }

    static float dp(View v, float x) { return x * v.getResources().getDisplayMetrics().density; }

    /* ================================================================ TESTO | PENNELLO */
    public static final class ModeSwitch extends View {
        public interface L { void onMode(boolean brush); }
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Theme th; boolean brush; float pos; L l;
        final String[] labels = {"Aa  TESTO", "✎  PENNELLO"};
        public ModeSwitch(Context c, Theme th) { super(c); this.th = th; p.setTypeface(Ui.MONO_BOLD); p.setTextAlign(Paint.Align.CENTER); }
        public void set(boolean b, boolean anim) {
            brush = b;
            float to = b ? 1 : 0;
            if (!anim) { pos = to; invalidate(); return; }
            ValueAnimator va = ValueAnimator.ofFloat(pos, to);
            va.setDuration(380); va.setInterpolator(new OvershootInterpolator(1.4f));
            va.addUpdateListener(x -> { pos = (float) x.getAnimatedValue(); invalidate(); });
            va.start();
        }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = Math.min(h / 2f, dp(this, th.round + 8));
            p.setStyle(Paint.Style.FILL); p.setColor(Theme.a(th.panel, 240));
            c.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(this, 1)); p.setColor(Theme.a(th.accent, 90));
            c.drawRoundRect(new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), r, r, p);
            float gw = w / 2f - dp(this, 4), gx = dp(this, 4) + pos * (w / 2f - dp(this, 4));
            p.setStyle(Paint.Style.FILL); p.setColor(th.accent);
            p.setShadowLayer(dp(this, 10), 0, 0, Theme.a(th.accent, 150));
            c.drawRoundRect(new RectF(gx, dp(this, 4), gx + gw, h - dp(this, 4)), r - dp(this, 3), r - dp(this, 3), p);
            p.clearShadowLayer();
            p.setTextSize(dp(this, 12.5f));
            for (int i = 0; i < 2; i++) {
                float cx = w / 4f + i * w / 2f;
                float sel = 1 - Math.abs(pos - i);
                p.setColor(Ui.mix(th.fg, th.bg, Ui.clamp(sel, 0, 1)));
                c.drawText(labels[i], cx, h / 2f + dp(this, 4.5f), p);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                boolean b = e.getX() > getWidth() / 2f;
                if (b != brush) { Ui.tick(this); set(b, true); if (l != null) l.onMode(b); }
            }
            return true;
        }
    }

    /* ================================================================ desk behind the sheet */
    public static final class Desk extends FrameLayout {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Theme th; View sheet; int paper = 0xffffffff;
        public Desk(Context c, Theme th) { super(c); this.th = th; setWillNotDraw(false); }
        @Override protected void onDraw(Canvas c) {
            c.drawColor(th.bg);
            if (th.hasGrid) {
                p.setColor(th.grid); p.setStrokeWidth(1);
                float g = dp(this, 26);
                for (float x = 0; x < getWidth(); x += g) c.drawLine(x, 0, x, getHeight(), p);
                for (float y = 0; y < getHeight(); y += g) c.drawLine(0, y, getWidth(), y, p);
            }
            if (sheet != null && sheet.getWidth() > 0) {
                float l = sheet.getLeft(), t = sheet.getTop(), r = sheet.getRight(), b = Math.min(sheet.getBottom(), t + getHeight() * 3f);
                p.setStyle(Paint.Style.FILL);
                c.save(); c.rotate(0.9f, (l + r) / 2, t); c.translate(dp(this, 5), dp(this, 6));
                p.setColor(Ui.mix(paper, 0xff000000, 0.16f)); c.drawRoundRect(new RectF(l, t, r, b), dp(this, 4), dp(this, 4), p);
                c.restore();
                c.save(); c.rotate(-0.6f, (l + r) / 2, t); c.translate(-dp(this, 4), dp(this, 9));
                p.setColor(Ui.mix(paper, 0xff000000, 0.26f)); c.drawRoundRect(new RectF(l, t, r, b), dp(this, 4), dp(this, 4), p);
                c.restore();
                // neon edge glow of the Ampy family
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(this, 1.2f)); p.setColor(Theme.a(th.accent, 70));
                p.setShadowLayer(dp(this, 12), 0, 0, Theme.a(th.accent, 120));
                c.drawRoundRect(new RectF(l - 1, t - 1, r + 1, sheet.getBottom() + 1), dp(this, 5), dp(this, 5), p);
                p.clearShadowLayer(); p.setStyle(Paint.Style.FILL);
            }
        }
    }

    /* ================================================================ preset card */
    public static final class PresetCard extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final PaperStyle.Preset pr; final Theme th; boolean sel;
        final Model.Style st = new Model.Style();
        public PresetCard(Context c, Theme th, PaperStyle.Preset pr) { super(c); this.th = th; this.pr = pr; pr.apply(st); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight() - dp(this, 16), r = dp(this, 8);
            c.save();
            android.graphics.Path clip = new android.graphics.Path(); clip.addRoundRect(new RectF(0, 0, w, h), r, r, android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            float u = w / 400f * 3.2f;
            PaperStyle.draw(c, (int) w, (int) h, st, u, u * 20, u * 6, u * 13);
            p.setTypeface(PaperStyle.typeface(pr.font)); p.setColor(pr.ink); p.setTextSize(h * 0.3f); p.setTextAlign(Paint.Align.LEFT);
            c.drawText("Aa", w * 0.14f, h * 0.62f, p);
            p.setTypeface(Ui.MONO_BOLD); p.setColor(pr.quill); p.setTextSize(h * 0.22f); p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(pr.glyph, w * 0.9f, h * 0.9f, p);
            c.restore();
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(this, sel ? 2.5f : 1));
            p.setColor(sel ? th.accent : Theme.a(th.fg, 50));
            c.drawRoundRect(new RectF(1, 1, w - 1, h - 1), r, r, p);
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Ui.MONO_BOLD); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(dp(this, 8.5f));
            p.setColor(sel ? th.accent : th.muted);
            c.drawText(pr.name, w / 2f, getHeight() - dp(this, 3), p);
        }
    }

    /* ================================================================ quill icon card */
    public static final class IconCard extends View {
        final Theme th; final String icon; int color; boolean sel;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public IconCard(Context c, Theme th, String icon, int color) { super(c); this.th = th; this.icon = icon; this.color = color; }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = dp(this, 12);
            p.setStyle(Paint.Style.FILL); p.setColor(sel ? Theme.a(th.accent, 40) : th.panel2);
            c.drawRoundRect(new RectF(0, 0, w, h), r, r, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(this, sel ? 2 : 1)); p.setColor(sel ? th.accent : th.border);
            c.drawRoundRect(new RectF(1, 1, w - 1, h - 1), r, r, p);
            c.save(); c.translate(w * 0.1f, h * 0.05f);
            QuillView.drawPreview(c, icon, color, w * 0.85f, h * 0.85f, getResources().getDisplayMetrics().density);
            c.restore();
        }
    }

    /* ================================================================ bitmap thumb */
    public static final class Thumb extends View {
        Bitmap b; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        public Thumb(Context c) { super(c); }
        public void set(Bitmap b) { this.b = b; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            if (b == null) return;
            c.drawBitmap(b, null, new RectF(0, 0, getWidth(), getHeight()), p);
        }
    }

    /* ================================================================ colour dot */
    public static final class Dot extends View {
        int color; final Theme th; boolean none; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public Dot(Context c, Theme th, int color) { super(c); this.th = th; this.color = color; }
        public void set(int c) { color = c; none = c == 0; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - dp(this, 3);
            p.setStyle(Paint.Style.FILL); p.setColor(none ? 0x22ffffff : color); c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(this, 2)); p.setColor(th.fg); c.drawCircle(cx, cy, r + dp(this, 1.5f), p);
            if (none) { p.setColor(0xffff6177); c.drawLine(cx - r * 0.7f, cy + r * 0.7f, cx + r * 0.7f, cy - r * 0.7f, p); }
        }
    }
}
