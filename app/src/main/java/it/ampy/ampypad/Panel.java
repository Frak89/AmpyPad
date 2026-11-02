package it.ampy.ampypad;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Ampy-style bottom sheet that slides up, with sections, chips, toggles, sliders and action rows. */
public final class Panel {
    public interface IntCb { void on(int i); }
    public interface BoolCb { void on(boolean b); }

    final Activity a; final Theme th; final Dialog dlg; final float d;
    public final LinearLayout body;
    private final LinearLayout root;
    private boolean closing;

    public Panel(Activity a, Theme th, String title) {
        this.a = a; this.th = th;
        d = a.getResources().getDisplayMetrics().density;
        dlg = new Dialog(a);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(th.panel, Theme.a(th.accent, 150), dp(1), dp(th.round + 10)));
        root.setPadding(dp(16), dp(10), dp(16), dp(22));
        View grab = new View(a);
        grab.setBackground(Ui.round(Theme.a(th.fg, 70), 0, 0, dp(3)));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(dp(44), dp(5)); gl.gravity = Gravity.CENTER_HORIZONTAL; gl.bottomMargin = dp(10);
        root.addView(grab, gl);
        TextView t = Ui.text(a, "// " + title, 16, th.accent, true);
        root.addView(t);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(a);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        dlg.setContentView(root);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels, dp(640)), -2);
            w.setDimAmount(0.3f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes(); lp.y = dp(6); w.setAttributes(lp);
        }
    }

    int dp(float v) { return Math.round(v * d); }

    public Panel show() {
        root.setTranslationY(a.getResources().getDisplayMetrics().heightPixels * 0.6f);
        root.setAlpha(0.6f);
        dlg.show();
        root.animate().translationY(0).alpha(1f).setDuration(340).setInterpolator(new DecelerateInterpolator(2.2f)).start();
        return this;
    }

    public void dismiss() {
        if (closing) return;
        closing = true;
        root.animate().translationY(root.getHeight() + dp(40)).alpha(0.4f).setDuration(200)
            .withEndAction(() -> { try { dlg.dismiss(); } catch (Exception ignored) { } }).start();
    }

    public void onDismiss(Runnable r) { dlg.setOnDismissListener(x -> r.run()); }

    public TextView section(String s) {
        TextView t = Ui.text(a, "■ " + s, 11, th.secondary, true);
        t.setPadding(0, dp(16), 0, dp(6));
        body.addView(t);
        return t;
    }

    public TextView note(String s) {
        TextView t = Ui.text(a, s, 11, th.muted, false);
        t.setPadding(0, dp(2), 0, dp(4));
        body.addView(t);
        return t;
    }

    /** Horizontal row of single-choice chips. */
    public List<TextView> chips(String[] labels, int sel, IntCb cb) { return chips(labels, sel, null, cb); }

    public List<TextView> chips(String[] labels, int sel, Typeface[] faces, IntCb cb) {
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(a);
        hs.addView(row);
        List<TextView> out = new ArrayList<>();
        final int[] cur = {sel};
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            TextView c = Ui.chip(a, th, labels[i], null);
            if (faces != null && faces[i] != null) { c.setTypeface(faces[i]); c.setTextSize(14); c.setLetterSpacing(0f); }
            Ui.chipSel(a, th, c, i == sel);
            c.setOnClickListener(v -> {
                Ui.tick(v);
                if (cur[0] >= 0 && cur[0] < out.size()) Ui.chipSel(a, th, out.get(cur[0]), false);
                cur[0] = k; Ui.chipSel(a, th, c, true);
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();
                cb.on(k);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = dp(6);
            row.addView(c, lp);
            out.add(c);
            c.setAlpha(0f); c.setTranslationX(dp(14));
            c.animate().alpha(1f).translationX(0).setStartDelay(25L * i).setDuration(260).start();
        }
        body.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        if (sel > 2) hs.post(() -> { if (sel < out.size()) hs.smoothScrollTo(out.get(sel).getLeft() - dp(40), 0); });
        return out;
    }

    public Switch toggle(String title, String sub, boolean val, BoolCb cb) {
        LinearLayout c = card();
        Switch s = new Switch(a);
        s.setText(title); s.setTextColor(th.fg); s.setTextSize(14); s.setTypeface(Ui.MONO);
        s.setChecked(val);
        s.setOnCheckedChangeListener((b, v) -> { Ui.tick(b); cb.on(v); });
        c.addView(s);
        if (sub != null) c.addView(Ui.text(a, sub, 11, th.muted, false));
        return s;
    }

    public SeekBar slider(String title, int min, int max, int val, String unit, IntCb cb) {
        LinearLayout c = card();
        TextView t = Ui.text(a, title + ": " + val + unit, 11, th.muted, false);
        c.addView(t);
        SeekBar sb = new SeekBar(a);
        sb.setMax(max - min); sb.setProgress(val - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int pr, boolean u) { t.setText(title + ": " + (pr + min) + unit); if (u) cb.on(pr + min); }
            @Override public void onStartTrackingTouch(SeekBar b) { }
            @Override public void onStopTrackingTouch(SeekBar b) { }
        });
        c.addView(sb);
        return sb;
    }

    public LinearLayout card() {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(th.panel2, th.border, dp(1), dp(th.round)));
        c.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(7);
        body.addView(c, lp);
        return c;
    }

    /** Big tappable action row: glyph · title · subtitle. */
    public LinearLayout action(String glyph, String title, String sub, Runnable r) {
        LinearLayout c = card();
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        TextView g = Ui.text(a, glyph, 20, th.accent, true);
        g.setGravity(Gravity.CENTER);
        c.addView(g, new LinearLayout.LayoutParams(dp(40), -2));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(a, title, 14, th.fg, true));
        if (sub != null) col.addView(Ui.text(a, sub, 11, th.muted, false));
        c.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        c.setOnClickListener(v -> { Ui.tick(v); r.run(); });
        return c;
    }

    /** Horizontally scrolling row of arbitrary views (cards, swatches…). */
    public LinearLayout strip() {
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(a);
        row.setPadding(0, dp(2), 0, dp(4));
        hs.addView(row);
        body.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    /** Fixed grid (rows of {@code cols} equal cells). */
    public List<LinearLayout> grid(int count, int cols) {
        List<LinearLayout> cells = new ArrayList<>();
        LinearLayout row = null;
        for (int i = 0; i < count; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(a);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2); rl.topMargin = dp(7);
                body.addView(row, rl);
            }
            LinearLayout cell = new LinearLayout(a);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cl.leftMargin = i % cols == 0 ? 0 : dp(4); cl.rightMargin = i % cols == cols - 1 ? 0 : dp(4);
            row.addView(cell, cl);
            cells.add(cell);
        }
        if (row != null) for (int i = count % cols == 0 ? cols : count % cols; i < cols; i++) row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f));
        return cells;
    }
}
