package it.ampy.ampypad;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** ⚙ AmpyPad settings, same look as AmpyCam's. */
public class SettingsActivity extends Activity {
    private Prefs p;
    private Theme th;
    private LinearLayout list;
    private ScrollView sv;

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = Prefs.get(this);
        th = Theme.load(this);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(th.bg);
        getWindow().setNavigationBarColor(th.bg);
        sv = new ScrollView(this);
        sv.setBackgroundColor(th.bg);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(16), dp(16), dp(32));
        sv.addView(list);
        sv.setOnApplyWindowInsetsListener((v, ins) -> {
            int t, bt;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = ins.getInsets(android.view.WindowInsets.Type.systemBars());
                t = s.top; bt = s.bottom;
            } else { t = ins.getSystemWindowInsetTop(); bt = ins.getSystemWindowInsetBottom(); }
            v.setPadding(0, t, 0, bt);
            return ins;
        });
        setContentView(sv);
        build();
    }

    private TextView text(String s, float sp, int c, boolean bold) { return Ui.text(this, s, sp, c, bold); }

    private void section(String s) {
        TextView t = text("■ " + s, 12, th.secondary, true);
        t.setPadding(0, dp(20), 0, dp(4));
        list.addView(t);
    }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(); g.setColor(th.panel); g.setStroke(dp(1), th.border); g.setCornerRadius(dp(th.round));
        c.setBackground(g);
        c.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8);
        list.addView(c, lp);
        return c;
    }
    interface IntCb { void on(int i); }
    private void choice(String title, String[] labels, int sel, IntCb cb) {
        LinearLayout c = card();
        c.addView(text(title, 11, th.muted, false));
        c.addView(text(labels[Math.max(0, Math.min(labels.length - 1, sel))] + "  ▾", 15, th.accent, true));
        c.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle(title)
            .setSingleChoiceItems(labels, sel, (d, i) -> { cb.on(i); d.dismiss(); rebuild(); }).show());
    }
    private void toggle(String key, String title, String sub, boolean def) {
        LinearLayout c = card();
        Switch s = new Switch(this);
        s.setText(title); s.setTextColor(th.fg); s.setTextSize(14); s.setTypeface(Ui.MONO);
        s.setChecked(p.b(key, def));
        s.setOnCheckedChangeListener((b, v) -> p.put(key, v));
        c.addView(s);
        if (sub != null) c.addView(text(sub, 11, th.muted, false));
    }
    private void rebuild() { list.removeAllViews(); build(); }

    private void build() {
        list.addView(text("// AMPYPAD", 22, th.accent, true));
        list.addView(text("Blocco note per scrivere, disegnare e dipingere", 12, th.muted, false));

        section("ASPETTO");
        int ti = 0; for (int i = 0; i < Theme.KEYS.length; i++) if (Theme.KEYS[i].equals(p.theme())) ti = i;
        choice("TEMA (come AmpyCam / AmpyRocket / AmpyRAW)", Theme.NAMES, ti, i -> {
            p.put("theme", Theme.KEYS[i]); th = Theme.load(this); sv.setBackgroundColor(th.bg);
            getWindow().setStatusBarColor(th.bg); getWindow().setNavigationBarColor(th.bg);
        });
        choice("ANIMAZIONE NUOVI FOGLI", PaperStyle.TRANSITION_NAMES, PaperStyle.indexOf(PaperStyle.TRANSITIONS, p.transition()), i -> p.put("transition", PaperStyle.TRANSITIONS[i]));

        section("PIUMA");
        toggle("quillOn", "Piuma attiva", "segue il cursore e la punta del pennello", true);
        choice("ICONA", QuillView.ICON_NAMES, PaperStyle.indexOf(QuillView.ICONS, p.quillIcon()), i -> p.put("quillIcon", QuillView.ICONS[i]));
        choice("EFFETTO QUANDO SI POSA", PaperStyle.EFFECT_NAMES, PaperStyle.indexOf(PaperStyle.EFFECTS, p.quillEffect()), i -> { p.put("quillEffect", PaperStyle.EFFECTS[i]); p.put("quillFollow", false); });
        toggle("quillFollow", "Colore ed effetto dallo stile del foglio", null, true);
        toggle("quillTrail", "Scia mentre scrivi", null, true);
        toggle("quillAutoBrush", "Pennello intinto in modalità pennello", null, true);

        section("GENERALE");
        toggle("sound", "Suoni", "graffio della piuma, pagine che girano, effetti", true);
        toggle("haptics", "Vibrazione", null, true);

        section("FILE");
        LinearLayout c = card();
        c.addView(text("PNG → Immagini/AmpyPad\nPDF → Download/AmpyPad (A4, testo vettoriale)\nBackup → file .json scelto da te (menu ☰ o ⇩ ESPORTA)", 12, th.fg, false));

        section("ABOUT");
        String ver = "?";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        TextView about = text("AmpyPad " + ver + "\nCreatore: Davide Fraccaro · GitHub Frak89\nFamiglia Ampy: AmpyCam · AmpyRocket · AmpyLink · AmpyRAW Cyberdark", 12, th.muted, false);
        about.setGravity(Gravity.START);
        card().addView(about);
    }
}
