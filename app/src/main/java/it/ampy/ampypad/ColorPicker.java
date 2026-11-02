package it.ampy.ampypad;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** The colour palette: opens every time a colour is chosen (ink, paper, lines, brush, quill, text). */
public final class ColorPicker {
    private ColorPicker() { }
    public interface Cb { void on(int color); }

    public static final int[] PRESETS = {
        0xff101010, 0xff3a3a3a, 0xff6e6e6e, 0xffa8a8a8, 0xffd6d6d6, 0xffffffff, 0xfffdfdf8, 0xfff3e5c0, 0xffe6cfa7, 0xffc19a6b,
        0xff1f3a93, 0xff2b6cb0, 0xff3fa7d6, 0xff7fd1e8, 0xff00ffe2, 0xff1b7f5a, 0xff2fa36b, 0xff7ccf8a, 0xffc3e88d, 0xff9be7a8,
        0xffb8860b, 0xffe0a526, 0xfff6c445, 0xffffd16a, 0xffffe08a, 0xffa23b2a, 0xffd9534f, 0xffff2b4e, 0xfff08a5d, 0xffffb4a2,
        0xff6b2d8c, 0xff9b59b6, 0xffc39bd3, 0xffff3ac8, 0xffe8a0bf, 0xff4b2e14, 0xff8b5a2b, 0xff2e4a3d, 0xff141a33, 0xff070b13,
    };

    public static void show(Activity a, Theme th, String title, int initial, boolean allowNone, Cb live, Cb done) {
        Dialog dlg = new Dialog(a);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final float dd = a.getResources().getDisplayMetrics().density;
        final int[] cur = {initial == 0 ? 0xff000000 : initial | 0xff000000};
        final float[] hsv = new float[3];
        Color.colorToHSV(cur[0], hsv);
        final boolean[] committed = {false};
        Prefs pr = Prefs.get(a);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * dd);
        root.setPadding(pad, pad, pad, pad);
        root.setBackground(Ui.round(th.panel, Theme.a(th.accent, 140), Math.round(dd), th.round * dd + 6 * dd));

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(a, "■ " + title, 13, th.accent, true);
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        final Preview prev = new Preview(a, cur[0], cur[0]);
        head.addView(prev, new LinearLayout.LayoutParams(Math.round(76 * dd), Math.round(30 * dd)));
        root.addView(head);

        final SV sv = new SV(a);
        final Hue hue = new Hue(a);
        final EditText hex = new EditText(a);
        final Swatches harmony = new Swatches(a, new int[6], 6, th);
        final boolean[] mute = {false};

        final Runnable[] apply = new Runnable[1];
        apply[0] = () -> {
            cur[0] = Color.HSVToColor(hsv);
            sv.h = hsv[0]; sv.s = hsv[1]; sv.v = hsv[2]; sv.invalidate();
            hue.h = hsv[0]; hue.invalidate();
            prev.now = cur[0]; prev.invalidate();
            harmony.colors = harmonies(hsv); harmony.invalidate();
            if (!hex.hasFocus()) { mute[0] = true; hex.setText(Ui.hex(cur[0])); mute[0] = false; }
            if (live != null) live.on(cur[0]);
        };

        sv.h = hsv[0]; sv.s = hsv[1]; sv.v = hsv[2];
        sv.cb = (s, v) -> { hsv[1] = s; hsv[2] = v; apply[0].run(); };
        LinearLayout.LayoutParams svl = new LinearLayout.LayoutParams(-1, Math.round(160 * dd)); svl.topMargin = Math.round(12 * dd);
        root.addView(sv, svl);

        hue.h = hsv[0];
        hue.cb = h -> { hsv[0] = h; apply[0].run(); };
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, Math.round(26 * dd)); hl.topMargin = Math.round(12 * dd);
        root.addView(hue, hl);

        LinearLayout hexRow = new LinearLayout(a);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        hex.setText(Ui.hex(cur[0])); hex.setTypeface(Ui.MONO_BOLD); hex.setTextColor(th.fg); hex.setTextSize(15);
        hex.setSingleLine(true); hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setBackground(Ui.round(th.bg, th.border, Math.round(dd), 10 * dd));
        hex.setPadding(pad / 2 + 4, pad / 3, pad / 2, pad / 3);
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (mute[0]) return;
                String v = s.toString().trim().replace("#", "");
                if (v.length() == 6) {
                    try { Color.colorToHSV(0xff000000 | Integer.parseInt(v, 16), hsv); apply[0].run(); } catch (Exception ignored) { }
                }
            }
        });
        hexRow.addView(hex, new LinearLayout.LayoutParams(0, -2, 1f));
        if (allowNone) {
            TextView none = Ui.chip(a, th, "NESSUNO", v -> { committed[0] = true; if (done != null) done.on(0); dlg.dismiss(); });
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2); nl.leftMargin = Math.round(8 * dd);
            hexRow.addView(none, nl);
        }
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(-1, -2); xl.topMargin = Math.round(12 * dd);
        root.addView(hexRow, xl);

        root.addView(label(a, th, "■ TAVOLOZZA"));
        Swatches pal = new Swatches(a, PRESETS, 10, th);
        pal.cb = c -> { Color.colorToHSV(c, hsv); apply[0].run(); spill(pal); };
        root.addView(pal, new LinearLayout.LayoutParams(-1, -2));

        root.addView(label(a, th, "■ ARMONIE (complementare · triade · analoghi)"));
        harmony.colors = harmonies(hsv);
        harmony.cb = c -> { Color.colorToHSV(c, hsv); apply[0].run(); };
        root.addView(harmony, new LinearLayout.LayoutParams(-1, -2));

        List<Integer> rec = pr.recent();
        if (!rec.isEmpty()) {
            root.addView(label(a, th, "■ RECENTI"));
            int[] rc = new int[rec.size()];
            for (int i = 0; i < rc.length; i++) rc[i] = rec.get(i);
            Swatches rs = new Swatches(a, rc, 10, th);
            rs.cb = c -> { Color.colorToHSV(c, hsv); apply[0].run(); };
            root.addView(rs, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout btns = new LinearLayout(a);
        btns.setGravity(Gravity.END);
        TextView cancel = Ui.chip(a, th, "ANNULLA", v -> dlg.cancel());
        TextView ok = Ui.chip(a, th, "APPLICA", v -> {
            committed[0] = true;
            pr.pushRecent(cur[0]);
            if (done != null) done.on(cur[0]);
            dlg.dismiss();
        });
        Ui.chipSel(a, th, ok, true);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2); bl.leftMargin = Math.round(8 * dd);
        btns.addView(cancel, bl); btns.addView(ok, bl);
        LinearLayout.LayoutParams bll = new LinearLayout.LayoutParams(-1, -2); bll.topMargin = Math.round(14 * dd);
        root.addView(btns, bll);

        ScrollView sc = new ScrollView(a);
        sc.addView(root);
        dlg.setContentView(sc);
        dlg.setOnCancelListener(x -> { if (!committed[0] && live != null) live.on(initial); });
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(Math.min(Math.round(380 * dd), a.getResources().getDisplayMetrics().widthPixels - Math.round(24 * dd)), -2);
            w.setDimAmount(0.45f);
        }
        root.setScaleX(0.86f); root.setScaleY(0.86f); root.setAlpha(0f);
        dlg.show();
        root.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320).setInterpolator(new OvershootInterpolator(1.6f)).start();
    }

    private static void spill(View v) { v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(80).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start(); }

    private static TextView label(Context a, Theme th, String s) {
        TextView t = Ui.text(a, s, 10, th.secondary, true);
        float dd = a.getResources().getDisplayMetrics().density;
        t.setPadding(0, Math.round(14 * dd), 0, Math.round(6 * dd));
        return t;
    }

    static int[] harmonies(float[] hsv) {
        float[] q = hsv.clone();
        float[] offs = {180, 120, 240, 30, -30, 0};
        int[] out = new int[6];
        for (int i = 0; i < 6; i++) {
            q[0] = ((hsv[0] + offs[i]) % 360 + 360) % 360;
            q[1] = i == 5 ? hsv[1] * 0.45f : hsv[1];
            q[2] = i == 5 ? Math.min(1, hsv[2] * 0.6f + 0.4f) : hsv[2];
            out[i] = Color.HSVToColor(q);
        }
        return out;
    }

    /* ================================================================ widgets */
    interface SvCb { void on(float s, float v); }
    interface HueCb { void on(float h); }

    static final class SV extends View {
        float h, s, v; SvCb cb;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        SV(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), ht = getHeight(), r = 14 * getResources().getDisplayMetrics().density;
            RectF rc = new RectF(0, 0, w, ht);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, w, 0, 0xffffffff, Color.HSVToColor(new float[]{h, 1, 1}), Shader.TileMode.CLAMP));
            c.drawRoundRect(rc, r, r, p);
            p.setShader(new LinearGradient(0, 0, 0, ht, 0x00000000, 0xff000000, Shader.TileMode.CLAMP));
            c.drawRoundRect(rc, r, r, p);
            p.setShader(null);
            float x = s * w, y = (1 - v) * ht, rr = r * 0.75f;
            p.setColor(Color.HSVToColor(new float[]{h, s, v})); c.drawCircle(x, y, rr, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.25f); p.setColor(0xffffffff); c.drawCircle(x, y, rr, p);
            p.setStrokeWidth(1); p.setColor(0x66000000); c.drawCircle(x, y, rr + r * 0.14f, p);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            s = Ui.clamp(e.getX() / getWidth(), 0, 1); v = 1 - Ui.clamp(e.getY() / getHeight(), 0, 1);
            if (cb != null) cb.on(s, v);
            invalidate();
            return true;
        }
    }

    static final class Hue extends View {
        float h; HueCb cb;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Hue(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), ht = getHeight(), r = ht / 2f;
            int[] cols = new int[7];
            for (int i = 0; i < 7; i++) cols[i] = Color.HSVToColor(new float[]{i * 60f % 360f, 1, 1});
            cols[6] = 0xffff0000;
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(r, 0, w - r, 0, cols, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(0, ht * 0.2f, w, ht * 0.8f), r, r, p);
            p.setShader(null);
            float x = r + (w - 2 * r) * h / 360f;
            p.setColor(Color.HSVToColor(new float[]{h, 1, 1})); c.drawCircle(x, ht / 2, r * 0.85f, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(r * 0.25f); p.setColor(0xffffffff); c.drawCircle(x, ht / 2, r * 0.85f, p);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            float r = getHeight() / 2f;
            h = Ui.clamp((e.getX() - r) / (getWidth() - 2 * r), 0, 1) * 359.9f;
            if (cb != null) cb.on(h);
            invalidate();
            return true;
        }
    }

    static final class Swatches extends View {
        int[] colors; final int cols; final Theme th; Cb cb; int sel = -1;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final long born = SystemClock.uptimeMillis();
        Swatches(Context c, int[] colors, int cols, Theme th) { super(c); this.colors = colors; this.cols = cols; this.th = th; }
        @Override protected void onMeasure(int w, int h) {
            int ww = MeasureSpec.getSize(w);
            int cell = ww / cols;
            int rows = (colors.length + cols - 1) / cols;
            setMeasuredDimension(ww, Math.max(1, rows) * cell);
        }
        @Override protected void onDraw(Canvas c) {
            float cell = getWidth() / (float) cols;
            long t = SystemClock.uptimeMillis() - born;
            boolean anim = false;
            for (int i = 0; i < colors.length; i++) {
                float k = Ui.clamp((t - i * 12) / 260f, 0, 1);
                if (k < 1) anim = true;
                float e = 1 + 2.2f * (float) Math.pow(k - 1, 3) + 1.2f * (float) Math.pow(k - 1, 2);
                float cx = (i % cols + 0.5f) * cell, cy = (i / cols + 0.5f) * cell, r = cell * 0.4f * e;
                p.setStyle(Paint.Style.FILL); p.setColor(colors[i]);
                c.drawRoundRect(new RectF(cx - r, cy - r, cx + r, cy + r), r * 0.45f, r * 0.45f, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f); p.setColor(0x40ffffff);
                c.drawRoundRect(new RectF(cx - r, cy - r, cx + r, cy + r), r * 0.45f, r * 0.45f, p);
                if (i == sel) { p.setStrokeWidth(cell * 0.07f); p.setColor(th.accent); float rr = r + cell * 0.07f; c.drawRoundRect(new RectF(cx - rr, cy - rr, cx + rr, cy + rr), rr * 0.45f, rr * 0.45f, p); }
            }
            if (anim) postInvalidateOnAnimation();
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                float cell = getWidth() / (float) cols;
                int i = (int) (e.getY() / cell) * cols + (int) (e.getX() / cell);
                if (i >= 0 && i < colors.length) { sel = i; Ui.tick(this); if (cb != null) cb.on(colors[i]); invalidate(); }
            }
            return true;
        }
    }

    static final class Preview extends View {
        int old, now; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Preview(Context c, int old, int now) { super(c); this.old = old; this.now = now; }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h * 0.3f;
            p.setColor(old); c.drawRoundRect(new RectF(0, 0, w / 2 + r, h), r, r, p);
            p.setColor(now); c.drawRoundRect(new RectF(w / 2, 0, w, h), r, r, p);
            c.drawRect(w / 2, 0, w / 2 + r, h, p);
        }
    }
}
