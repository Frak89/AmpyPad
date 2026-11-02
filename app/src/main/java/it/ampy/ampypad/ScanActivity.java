package it.ampy.ampypad;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** ⎚ Document scanner: photo → page detection → perspective & curve correction → clean-up → PDF → email. */
public class ScanActivity extends Activity {
    private static final int RC_CAM = 31, RC_GAL = 32;

    static final class Page { File src, out; float[] q; String filter = "documento"; int rot; Bitmap thumb; }
    static final List<Page> PAGES = new ArrayList<>();

    private Theme th;
    private Prefs pr;
    private FrameLayout root, content;
    private LinearLayout main, actions;
    private TextView title, toastView;
    private Busy busy;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final Handler h = new Handler(Looper.getMainLooper());
    private int state;
    private Page cur;
    private boolean curIsNew;
    private Bitmap curSrc, curWarp, curPreview;
    private CropView crop;
    private ImageView preview;
    private Object backCb;

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pr = Prefs.get(this);
        th = Theme.load(this);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(th.bg);
        getWindow().setNavigationBarColor(th.bg);
        root = new FrameLayout(this);
        root.setBackgroundColor(th.bg);
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(4), dp(8), dp(4));
        bar.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{th.top, th.bottom}));
        bar.addView(Ui.icon(this, th, "←", v -> onBack()));
        title = Ui.text(this, "// SCANNER", 15, th.accent, true);
        title.setShadowLayer(dp(8), 0, 0, Theme.a(th.accent, 160));
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        main.addView(bar);
        View line = new View(this); line.setBackgroundColor(Theme.a(th.accent, 90));
        main.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));

        content = new FrameLayout(this);
        main.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(dp(10), dp(8), dp(10), dp(10));
        actions.setBackgroundColor(th.bottom);
        main.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        busy = new Busy(this, th);
        busy.setVisibility(View.GONE);
        root.addView(busy, new FrameLayout.LayoutParams(-1, -1));

        toastView = Ui.text(this, "", 12, th.fg, true);
        toastView.setGravity(Gravity.CENTER);
        toastView.setPadding(dp(16), dp(10), dp(16), dp(10));
        toastView.setBackground(Ui.round(Theme.a(th.panel, 245), th.accent, dp(1), dp(th.round + 6)));
        toastView.setElevation(dp(12));
        toastView.setVisibility(View.GONE);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tl.bottomMargin = dp(190); tl.leftMargin = dp(20); tl.rightMargin = dp(20);
        root.addView(toastView, tl);

        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int l, t, r, bt;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                l = s.left; t = s.top; r = s.right; bt = s.bottom;
            } else { l = ins.getSystemWindowInsetLeft(); t = ins.getSystemWindowInsetTop(); r = ins.getSystemWindowInsetRight(); bt = ins.getSystemWindowInsetBottom(); }
            main.setPadding(l, t, r, bt);
            return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : ins.consumeSystemWindowInsets();
        });
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 33) {
            android.window.OnBackInvokedCallback cb = this::onBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCb = cb;
        }
        showList();
    }

    @Override protected void onDestroy() { super.onDestroy(); ex.shutdown(); }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { onBack(); }

    private void onBack() {
        if (busy.getVisibility() == View.VISIBLE) return;
        if (state == 2) { showCropFromFilter(); return; }
        if (state == 1) { releaseCurrent(); showList(); return; }
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void releaseCurrent() { curSrc = null; curWarp = null; curPreview = null; cur = null; }

    /* ================================================================ helpers */
    private TextView primary(String s, View.OnClickListener l) { TextView t = Ui.chip(this, th, s, l); Ui.chipSel(this, th, t, true); t.setTextSize(13); t.setPadding(dp(16), dp(11), dp(16), dp(11)); return t; }
    private TextView secondary(String s, View.OnClickListener l) { TextView t = Ui.chip(this, th, s, l); t.setTextSize(13); t.setPadding(dp(14), dp(11), dp(14), dp(11)); return t; }

    private LinearLayout row(View... vs) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER);
        for (View v : vs) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.leftMargin = dp(4); lp.rightMargin = dp(4);
            r.addView(v, lp);
        }
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2); rl.topMargin = dp(6);
        r.setLayoutParams(rl);
        return r;
    }

    private void toast(String m) {
        toastView.setText(m);
        toastView.setVisibility(View.VISIBLE);
        toastView.setAlpha(0f); toastView.setTranslationY(dp(14));
        toastView.animate().alpha(1f).translationY(0).setDuration(260).setInterpolator(new OvershootInterpolator(1.3f)).start();
        h.removeCallbacksAndMessages("toast");
        h.postAtTime(() -> toastView.animate().alpha(0f).setDuration(220).withEndAction(() -> toastView.setVisibility(View.GONE)).start(), "toast", SystemClock.uptimeMillis() + 2600);
    }

    /* ================================================================ list */
    private void showList() {
        state = 0;
        content.removeAllViews(); actions.removeAllViews();
        title.setText("// SCANNER · " + PAGES.size() + (PAGES.size() == 1 ? " PAGINA" : " PAGINE"));
        if (PAGES.isEmpty()) {
            LinearLayout e = new LinearLayout(this);
            e.setOrientation(LinearLayout.VERTICAL); e.setGravity(Gravity.CENTER);
            e.setPadding(dp(32), 0, dp(32), 0);
            TextView ic = Ui.text(this, "⎚", 72, th.accent, true);
            ic.setShadowLayer(dp(18), 0, 0, th.accent); ic.setGravity(Gravity.CENTER);
            e.addView(ic);
            TextView tx = Ui.text(this, "Inquadra un foglio su uno sfondo che contrasta.\nAmpyPad trova i bordi, raddrizza prospettiva e pagine curve, toglie le ombre e crea un PDF da inviare per email.", 13, th.muted, false);
            tx.setGravity(Gravity.CENTER); tx.setLineSpacing(0, 1.25f);
            e.addView(tx);
            content.addView(e, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ScrollView sv = new ScrollView(this);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(10), dp(10), dp(10), dp(10));
            LinearLayout r = null;
            for (int i = 0; i < PAGES.size(); i++) {
                if (i % 2 == 0) {
                    r = new LinearLayout(this);
                    col.addView(r, new LinearLayout.LayoutParams(-1, dp(250)));
                }
                final Page p = PAGES.get(i);
                FrameLayout cell = new FrameLayout(this);
                cell.setBackground(Ui.round(th.panel, Theme.a(th.accent, 110), dp(1), dp(th.round + 4)));
                cell.setPadding(dp(8), dp(8), dp(8), dp(8));
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(p.thumb);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cell.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                TextView n = Ui.text(this, " " + (i + 1) + " ", 12, th.bg, true);
                n.setBackground(Ui.round(th.accent, 0, 0, dp(8)));
                cell.addView(n, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
                cell.setContentDescription("pagina " + (i + 1));
                cell.setOnClickListener(v -> { Ui.tick(v); pageMenu(p); });
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -1, 1f);
                cl.setMargins(dp(5), dp(5), dp(5), dp(5));
                r.addView(cell, cl);
                cell.setAlpha(0f); cell.setScaleX(0.9f); cell.setScaleY(0.9f);
                cell.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(40L * i).setDuration(260).start();
            }
            if (PAGES.size() % 2 == 1 && r != null) r.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f));
            sv.addView(col);
            content.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        }
        actions.addView(row(primary("◉ SCATTA", v -> camera()), secondary("▦ GALLERIA", v -> gallery())));
        if (!PAGES.isEmpty()) {
            actions.addView(row(secondary("⎙ SALVA PDF", v -> buildPdf(false, null, null)), primary("✉ INVIA EMAIL", v -> emailDialog())));
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout q = new LinearLayout(this);
            q.setGravity(Gravity.CENTER_VERTICAL);
            q.addView(Ui.text(this, "QUALITÀ PDF  ", 10, th.secondary, true));
            String[] ql = {"LEGGERA", "MEDIA", "ALTA"};
            List<TextView> qc = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                final int k = i;
                TextView c = Ui.chip(this, th, ql[i], null);
                Ui.chipSel(this, th, c, pr.i("scanQuality", 1) == i);
                c.setOnClickListener(v -> { Ui.tick(v); pr.put("scanQuality", k); for (int j = 0; j < qc.size(); j++) Ui.chipSel(this, th, qc.get(j), j == k); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = dp(6);
                q.addView(c, lp); qc.add(c);
            }
            hs.addView(q);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2); hl.topMargin = dp(8);
            actions.addView(hs, hl);
        }
    }

    private void pageMenu(Page p) {
        Panel pn = new Panel(this, th, "PAGINA " + (PAGES.indexOf(p) + 1));
        pn.action("⬚", "MODIFICA BORDI", "angoli e lati curvi", () -> { pn.dismiss(); openCrop(p, false); });
        pn.action("◐", "FILTRO E ROTAZIONE", null, () -> { pn.dismiss(); openFilter(p); });
        int i = PAGES.indexOf(p);
        if (i > 0) pn.action("⇦", "SPOSTA PRIMA", null, () -> { pn.dismiss(); PAGES.remove(p); PAGES.add(i - 1, p); showList(); });
        if (i < PAGES.size() - 1) pn.action("⇨", "SPOSTA DOPO", null, () -> { pn.dismiss(); PAGES.remove(p); PAGES.add(i + 1, p); showList(); });
        pn.action("⌧", "ELIMINA PAGINA", null, () -> { pn.dismiss(); PAGES.remove(p); showList(); });
        pn.show();
    }

    /* ================================================================ capture */
    private void camera() {
        File f = new File(ScanProvider.dir(this), "cam_" + System.currentTimeMillis() + ".jpg");
        pr.put("scanPending", f.getAbsolutePath());
        Uri u = ScanProvider.uri(f);
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, u);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri("", u));
        try { startActivityForResult(i, RC_CAM); } catch (Exception e) { toast("NESSUNA APP FOTOCAMERA · USA GALLERIA"); }
    }

    private void gallery() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(i, RC_GAL); } catch (Exception e) { toast("IMPOSSIBILE APRIRE LA GALLERIA"); }
    }

    @Override protected void onActivityResult(int rc, int res, Intent d) {
        super.onActivityResult(rc, res, d);
        if (rc == RC_CAM) {
            File f = new File(pr.s("scanPending", ""));
            if (res == RESULT_OK && f.exists() && f.length() > 0) {
                Page p = new Page(); p.src = f;
                openCrop(p, true);
            } else toast("SCATTO ANNULLATO");
        } else if (rc == RC_GAL && res == RESULT_OK && d != null) {
            List<Uri> us = new ArrayList<>();
            if (d.getClipData() != null) for (int i = 0; i < d.getClipData().getItemCount(); i++) us.add(d.getClipData().getItemAt(i).getUri());
            else if (d.getData() != null) us.add(d.getData());
            if (us.isEmpty()) return;
            importUris(us);
        }
    }

    private void importUris(List<Uri> us) {
        busy.show("IMPORTAZIONE…");
        ex.execute(() -> {
            List<Page> ps = new ArrayList<>();
            int k = 0;
            for (Uri u : us) {
                try {
                    File f = new File(ScanProvider.dir(this), "gal_" + System.currentTimeMillis() + "_" + (k++) + ".jpg");
                    try (InputStream in = getContentResolver().openInputStream(u); OutputStream o = new FileOutputStream(f)) {
                        if (in == null) continue;
                        byte[] buf = new byte[65536]; int n;
                        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                    }
                    Page p = new Page(); p.src = f; ps.add(p);
                } catch (Exception ignored) { }
            }
            if (ps.size() == 1) { h.post(() -> { busy.hide(); openCrop(ps.get(0), true); }); return; }
            // several photos: fully automatic
            for (int i = 0; i < ps.size(); i++) {
                final int n = i + 1;
                h.post(() -> busy.label("PAGINA " + n + " / " + ps.size()));
                try {
                    Page p = ps.get(i);
                    Bitmap src = ScanCore.load(p.src, 2400);
                    p.q = ScanCore.detect(src);
                    Bitmap w = ScanCore.warp(src, p.q, 2400);
                    finishPage(p, w);
                    h.post(() -> PAGES.add(p));
                } catch (Throwable ignored) { }
            }
            h.post(() -> { busy.hide(); showList(); toast("⎚ " + ps.size() + " PAGINE RADDRIZZATE"); });
        });
    }

    /** Filter + JPEG + thumbnail (background thread). */
    private void finishPage(Page p, Bitmap warped) throws java.io.IOException {
        Bitmap full = ScanCore.filter(ScanCore.rotate(warped, p.rot), p.filter);
        File out = new File(ScanProvider.dir(this), "page_" + System.currentTimeMillis() + ".jpg");
        try (OutputStream o = new FileOutputStream(out)) { full.compress(Bitmap.CompressFormat.JPEG, 90, o); }
        p.out = out;
        p.thumb = ScanCore.fit(full, 420);
    }

    /* ================================================================ crop */
    private void openCrop(Page p, boolean isNew) {
        busy.show("RILEVAMENTO BORDI…");
        ex.execute(() -> {
            try {
                Bitmap b = ScanCore.load(p.src, 2400);
                if (p.q == null) p.q = ScanCore.detect(b);
                h.post(() -> { busy.hide(); showCrop(p, b, isNew); });
            } catch (Throwable e) { h.post(() -> { busy.hide(); toast("FOTO NON LEGGIBILE"); }); }
        });
    }

    private void showCrop(Page p, Bitmap src, boolean isNew) {
        state = 1; cur = p; curSrc = src; curIsNew = isNew;
        content.removeAllViews(); actions.removeAllViews();
        title.setText("// BORDI DEL FOGLIO");
        crop = new CropView(this, th);
        crop.set(src, p.q.clone(), true);
        content.addView(crop, new FrameLayout.LayoutParams(-1, -1));
        TextView hint = Ui.text(this, "Trascina gli angoli ○ · i punti a metà lato curvano i bordi (pagine di libri)", 10.5f, th.muted, false);
        hint.setGravity(Gravity.CENTER);
        actions.addView(hint);
        actions.addView(row(
            secondary("⟲ RILEVA", v -> { busy.show("RILEVAMENTO…"); ex.execute(() -> { float[] q = ScanCore.detect(src); h.post(() -> { busy.hide(); crop.setQuad(q, true); }); }); }),
            secondary("▭ DRITTI", v -> { float[] q = crop.quad().clone(); ScanCore.straighten(q); crop.setQuad(q, true); }),
            secondary("⛶ TUTTA", v -> crop.setQuad(ScanCore.full(src.getWidth(), src.getHeight(), 0f), true))));
        actions.addView(row(primary("✓ RADDRIZZA", v -> doWarp())));
    }

    private void doWarp() {
        final float[] q = crop.quad().clone();
        cur.q = q;
        final Bitmap src = curSrc;
        busy.show("RADDRIZZAMENTO…");
        ex.execute(() -> {
            try {
                Bitmap w = ScanCore.warp(src, q, 2400);
                Bitmap pv = ScanCore.fit(w, 1100);
                h.post(() -> { busy.hide(); curWarp = w; curPreview = pv; showFilter(); });
            } catch (Throwable e) { h.post(() -> { busy.hide(); toast("MEMORIA INSUFFICIENTE"); }); }
        });
    }

    private void showCropFromFilter() {
        if (curSrc != null) showCrop(cur, curSrc, curIsNew);
        else { Page p = cur; boolean n = curIsNew; releaseCurrent(); if (p != null) openCrop(p, n); else showList(); }
    }

    private void openFilter(Page p) {
        busy.show("CARICAMENTO…");
        ex.execute(() -> {
            try {
                Bitmap b = ScanCore.load(p.src, 2400);
                if (p.q == null) p.q = ScanCore.detect(b);
                Bitmap w = ScanCore.warp(b, p.q, 2400);
                Bitmap pv = ScanCore.fit(w, 1100);
                h.post(() -> { busy.hide(); cur = p; curIsNew = false; curSrc = b; curWarp = w; curPreview = pv; showFilter(); });
            } catch (Throwable e) { h.post(() -> { busy.hide(); toast("FOTO NON LEGGIBILE"); }); }
        });
    }

    /* ================================================================ filter */
    private void showFilter() {
        state = 2;
        content.removeAllViews(); actions.removeAllViews();
        title.setText("// PULIZIA E FILTRO");
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setPadding(dp(16), dp(16), dp(16), dp(16));
        content.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout fr = new LinearLayout(this);
        List<TextView> chips = new ArrayList<>();
        for (int i = 0; i < ScanCore.FILTERS.length; i++) {
            final String key = ScanCore.FILTERS[i];
            TextView c = Ui.chip(this, th, ScanCore.FILTER_NAMES[i], null);
            Ui.chipSel(this, th, c, key.equals(cur.filter));
            c.setOnClickListener(v -> {
                Ui.tick(v); cur.filter = key;
                for (int j = 0; j < chips.size(); j++) Ui.chipSel(this, th, chips.get(j), ScanCore.FILTERS[j].equals(key));
                renderPreview();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = dp(6);
            fr.addView(c, lp); chips.add(c);
        }
        hs.addView(fr);
        actions.addView(hs);
        actions.addView(row(
            secondary("↺", v -> { cur.rot = (cur.rot + 270) % 360; renderPreview(); }),
            secondary("↻", v -> { cur.rot = (cur.rot + 90) % 360; renderPreview(); }),
            secondary("⬚ BORDI", v -> showCropFromFilter())));
        actions.addView(row(primary(curIsNew ? "✓ AGGIUNGI PAGINA" : "✓ SALVA PAGINA", v -> savePage())));
        renderPreview();
    }

    private void renderPreview() {
        final String f = cur.filter; final int r = cur.rot; final Bitmap pv = curPreview;
        if (pv == null) return;
        ex.execute(() -> {
            Bitmap b = ScanCore.filter(ScanCore.rotate(pv, r), f);
            h.post(() -> {
                if (preview == null) return;
                preview.setImageBitmap(b);
                preview.setAlpha(0.4f);
                preview.animate().alpha(1f).setDuration(220).start();
            });
        });
    }

    private void savePage() {
        final Page p = cur; final Bitmap w = curWarp; final boolean isNew = curIsNew;
        busy.show("PULIZIA DELLA PAGINA…");
        ex.execute(() -> {
            try {
                finishPage(p, w);
                h.post(() -> {
                    busy.hide();
                    if (isNew) PAGES.add(p);
                    releaseCurrent();
                    showList();
                    toast(isNew ? "✓ PAGINA " + PAGES.size() + " AGGIUNTA" : "✓ PAGINA AGGIORNATA");
                });
            } catch (Throwable e) { h.post(() -> { busy.hide(); toast("ERRORE: " + e.getMessage()); }); }
        });
    }

    /* ================================================================ PDF + email */
    private void emailDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText to = new EditText(this);
        to.setHint("destinatario@email.it"); to.setSingleLine(true);
        to.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        to.setText(pr.s("scanEmail", ""));
        EditText sub = new EditText(this);
        sub.setSingleLine(true);
        sub.setText("Scansione " + new SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN).format(new Date()));
        box.addView(to); box.addView(sub);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("✉ INVIA PDF PER EMAIL").setView(box)
            .setPositiveButton("INVIA", (dd, w) -> {
                String t = to.getText().toString().trim();
                pr.put("scanEmail", t);
                buildPdf(true, t, sub.getText().toString().trim());
            })
            .setNegativeButton("ANNULLA", null).show();
    }

    private void buildPdf(boolean send, String to, String subject) {
        if (PAGES.isEmpty()) { toast("NESSUNA PAGINA"); return; }
        final List<Page> ps = new ArrayList<>(PAGES);
        final int qual = pr.i("scanQuality", 1);
        busy.show("CREAZIONE PDF…");
        ex.execute(() -> {
            try {
                List<byte[]> jp = new ArrayList<>();
                List<int[]> sizes = new ArrayList<>();
                for (Page p : ps) {
                    if (qual == 2) {
                        BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(p.out.getAbsolutePath(), o);
                        jp.add(ScanCore.readFile(p.out)); sizes.add(new int[]{o.outWidth, o.outHeight});
                    } else {
                        Bitmap b = ScanCore.fit(BitmapFactory.decodeFile(p.out.getAbsolutePath()), qual == 0 ? 1300 : 1800);
                        jp.add(ScanCore.jpeg(b, qual == 0 ? 60 : 80)); sizes.add(new int[]{b.getWidth(), b.getHeight()});
                    }
                }
                byte[] pdf = ScanCore.pdf(jp, sizes);
                String name = "Scansione_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf";
                File f = new File(ScanProvider.dir(this), name);
                try (OutputStream o = new FileOutputStream(f)) { o.write(pdf); }
                boolean saved = false;
                if (Build.VERSION.SDK_INT >= 29 || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    try { Exporter.savePdfBytes(this, pdf, name); saved = true; } catch (Exception ignored) { }
                }
                final boolean ok = saved;
                final String kb = pdf.length > 1048576 ? String.format(Locale.ITALIAN, "%.1f MB", pdf.length / 1048576f) : (pdf.length / 1024) + " KB";
                h.post(() -> {
                    busy.hide();
                    Sfx.chime();
                    if (send) email(f, to, subject, ps.size());
                    else toast("⎙ PDF " + ps.size() + " PAG. · " + kb + (ok ? " · DOWNLOAD/AMPYPAD" : ""));
                });
            } catch (Throwable e) { h.post(() -> { busy.hide(); toast("ERRORE PDF: " + e.getMessage()); }); }
        });
    }

    private void email(File f, String to, String subject, int pages) {
        Uri u = ScanProvider.uri(f);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/pdf");
        if (to != null && !to.isEmpty()) send.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
        send.putExtra(Intent.EXTRA_SUBJECT, subject == null || subject.isEmpty() ? "Scansione AmpyPad" : subject);
        send.putExtra(Intent.EXTRA_TEXT, "Ciao,\nin allegato la scansione (" + pages + (pages == 1 ? " pagina" : " pagine") + ") creata con AmpyPad.\n");
        send.putExtra(Intent.EXTRA_STREAM, u);
        send.setClipData(ClipData.newRawUri("", u));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent mailOnly = new Intent(send);
        mailOnly.setSelector(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")));
        try { startActivity(mailOnly); }
        catch (Exception e) {
            try { startActivity(Intent.createChooser(send, "Invia il PDF con…")); } catch (Exception e2) { toast("NESSUNA APP EMAIL"); }
        }
    }

    /* ================================================================ busy overlay */
    static final class Busy extends View {
        final Theme th; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); String text = ""; long t0;
        Busy(Context c, Theme th) { super(c); this.th = th; setClickable(true); }
        void show(String s) { text = s; t0 = SystemClock.uptimeMillis(); setVisibility(VISIBLE); setAlpha(0f); animate().alpha(1f).setDuration(180).start(); invalidate(); }
        void label(String s) { text = s; invalidate(); }
        void hide() { animate().alpha(0f).setDuration(180).withEndAction(() -> setVisibility(GONE)).start(); }
        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density, w = getWidth(), h = getHeight();
            c.drawColor(Theme.a(th.bg, 215));
            float pw = 120 * d, ph = 160 * d, l = (w - pw) / 2, t = h / 2 - ph * 0.75f;
            p.setStyle(Paint.Style.FILL); p.setColor(Theme.a(th.panel2, 255));
            c.drawRoundRect(new RectF(l, t, l + pw, t + ph), 10 * d, 10 * d, p);
            p.setColor(Theme.a(th.fg, 60)); p.setStrokeWidth(2 * d);
            for (int i = 0; i < 7; i++) c.drawLine(l + 16 * d, t + 26 * d + i * 18 * d, l + pw - (i % 3 == 2 ? 50 : 16) * d, t + 26 * d + i * 18 * d, p);
            float k = ((SystemClock.uptimeMillis() - t0) % 1400) / 1400f;
            float y = t + ph * (k < 0.5f ? k * 2 : 2 - k * 2);
            p.setColor(th.accent); p.setStrokeWidth(3 * d);
            p.setShadowLayer(16 * d, 0, 0, th.accent);
            c.drawLine(l - 14 * d, y, l + pw + 14 * d, y, p);
            p.clearShadowLayer();
            p.setStyle(Paint.Style.STROKE); p.setColor(th.secondary); p.setStrokeWidth(3 * d);
            float br = 18 * d;
            c.drawLine(l - 8 * d, t - 8 * d, l - 8 * d + br, t - 8 * d, p); c.drawLine(l - 8 * d, t - 8 * d, l - 8 * d, t - 8 * d + br, p);
            c.drawLine(l + pw + 8 * d, t + ph + 8 * d, l + pw + 8 * d - br, t + ph + 8 * d, p); c.drawLine(l + pw + 8 * d, t + ph + 8 * d, l + pw + 8 * d, t + ph + 8 * d - br, p);
            p.setStyle(Paint.Style.FILL); p.setColor(th.accent); p.setTypeface(Ui.MONO_BOLD); p.setTextSize(14 * d); p.setTextAlign(Paint.Align.CENTER);
            c.drawText(text, w / 2, t + ph + 44 * d, p);
            if (getVisibility() == VISIBLE) postInvalidateOnAnimation();
        }
    }
}
