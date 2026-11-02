package it.ampy.ampypad;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.TextView;

/** Shared fonts and Ampy-style widgets (neon chips, cards). */
public final class Ui {
    private Ui() { }
    public static final Typeface MONO = Typeface.MONOSPACE;
    public static final Typeface MONO_BOLD = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);

    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setTypeface(bold ? MONO_BOLD : MONO); t.setLetterSpacing(0.05f);
        return t;
    }

    public static GradientDrawable round(int fill, int stroke, int strokeW, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        if (strokeW > 0) g.setStroke(strokeW, stroke);
        g.setCornerRadius(radius);
        return g;
    }

    /** Neon chip like AmpyCam's top bar. */
    public static TextView chip(Context c, Theme th, String s, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(s); t.setTextSize(12); t.setTextColor(th.fg);
        t.setTypeface(MONO_BOLD); t.setLetterSpacing(0.06f);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(c, 11), dp(c, 8), dp(c, 11), dp(c, 8));
        chipSel(c, th, t, false);
        if (l != null) t.setOnClickListener(v -> { tick(v); l.onClick(v); });
        return t;
    }

    public static void chipSel(Context c, Theme th, TextView t, boolean sel) {
        t.setBackground(round(sel ? Theme.a(th.accent, 225) : Theme.a(th.panel, 230), Theme.a(th.accent, sel ? 255 : 110), dp(c, 1), dp(c, 16)));
        t.setTextColor(sel ? th.bg : th.fg);
    }

    /** Square-ish icon button (unicode glyph). */
    public static TextView icon(Context c, Theme th, String glyph, View.OnClickListener l) {
        TextView t = new TextView(c);
        t.setText(glyph); t.setTextSize(18); t.setTextColor(th.fg);
        t.setTypeface(MONO_BOLD);
        t.setGravity(Gravity.CENTER);
        int s = dp(c, 42);
        t.setMinWidth(s); t.setMinHeight(s);
        t.setPadding(dp(c, 6), 0, dp(c, 6), 0);
        t.setBackground(round(0x00000000, 0, 0, dp(c, th.round)));
        if (l != null) t.setOnClickListener(v -> { tick(v); l.onClick(v); });
        return t;
    }

    public static void iconOn(Context c, Theme th, TextView t, boolean on) {
        t.setBackground(round(on ? Theme.a(th.accent, 50) : 0, Theme.a(th.accent, on ? 200 : 0), on ? dp(c, 1) : 0, dp(c, Math.max(8, th.round))));
        t.setTextColor(on ? th.accent : th.fg);
    }

    public static void tick(View v) {
        if (v != null && Prefs.get(v.getContext()).haptics()) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }
    public static void clock(View v) {
        if (v != null && Prefs.get(v.getContext()).haptics()) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    public static int mix(int a, int b, float t) {
        int aa = (a >>> 24), ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24), br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
    public static float luminance(int c) {
        return (0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255)) / 255f;
    }
    public static String hex(int c) { return String.format("#%06X", c & 0xffffff); }
    public static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
}
