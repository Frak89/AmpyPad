package it.ampy.ampypad;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.WindowInsets;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** AmpyPad: notes + drawing + painting on the same sheet, Ampy neon style. */
public class PadActivity extends Activity {
    private static final int RC_SPEECH = 11, RC_BACKUP = 12, RC_IMPORT = 13, RC_PERM = 14;

    private Theme th;
    private Prefs pr;
    private Model.Data data;
    private Model.Page page;

    private FrameLayout root;
    private LinearLayout main, topBar, bottomRow, statsRow, drawer, drawerList;
    private Views.Desk desk;
    private ScrollView scroll;
    private SheetView sheet;
    private Editor ed;
    private QuillView quill;
    private TextView subTitle, stats, saved, toastView, focusExit, blockBtn, textColorBar, undoBtn, redoBtn, symBtn, layerBtn, brushLbl;
    private HorizontalScrollView formatBar, brushBar;
    private Views.ModeSwitch modeSw;
    private Views.Thumb brushThumb;
    private Views.Dot brushDot;
    private SeekBar sizeBar, opBar;
    private View scrim;
    private EditText search;

    private boolean brushMode, drawerOpen, focusMode;
    private int sheetW, lastTextColor = 0xffff2b4e, lastHilite = 0xffffe08a;
    private Runnable pendingPerm;
    private Object backCb;
    private final Random rnd = new Random();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable saver = () -> { if (ed != null) ed.syncAll(); Store.save(this, data); if (saved != null) { saved.setText("● SALVATO"); saved.setTextColor(th.accent); } };
    private final Runnable statsRun = this::updateStats;
    private final Runnable hideToast = () -> { if (toastView != null) toastView.animate().alpha(0f).translationY(dp(12)).setDuration(220).withEndAction(() -> toastView.setVisibility(View.GONE)).start(); };

    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    /* ================================================================ lifecycle */
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pr = Prefs.get(this);
        th = Theme.load(this);
        Sfx.on = pr.sound();
        QuillView.initPreview(this);
        data = Store.load(this);
        page = data.page(data.current);
        if (page == null) page = data.pages.get(0);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(th.bg);
        getWindow().setNavigationBarColor(th.bg);
        computeSheetW();
        build();
        loadBrush();
        openPage(page, false);
        setMode(false, false);
        handleIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handleIntent(i); }

    @Override protected void onResume() {
        super.onResume();
        if (!pr.theme().equals(th.key)) { saveNow(); recreate(); return; }
        Sfx.on = pr.sound();
        applyQuill();
    }

    @Override protected void onPause() { super.onPause(); saveNow(); }

    private void saveNow() { h.removeCallbacks(saver); saver.run(); }
    private void scheduleSave() {
        if (page != null) page.updated = System.currentTimeMillis();
        if (saved != null) { saved.setText("○ …"); saved.setTextColor(th.muted); }
        h.removeCallbacks(saver); h.postDelayed(saver, 900);
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        computeSheetW();
        FrameLayout.LayoutParams sl = (FrameLayout.LayoutParams) sheet.getLayoutParams();
        sl.width = sheetW; sheet.setLayoutParams(sl);
        applyStyle();
    }

    private void computeSheetW() {
        int sw = getResources().getDisplayMetrics().widthPixels;
        sheetW = Math.min(sw - dp(16), dp(760));
    }

    private void handleIntent(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return;
        String txt = i.getStringExtra(Intent.EXTRA_TEXT);
        if (txt == null || txt.trim().isEmpty()) return;
        ed.syncAll();
        Model.Page p = new Model.Page();
        p.notebookId = page.notebookId;
        p.style = data.defaults.copy();
        String sub = i.getStringExtra(Intent.EXTRA_SUBJECT);
        p.title = sub != null ? sub : "Condiviso";
        for (String line : txt.split("\n")) p.blocks.add(new Model.Block("p", line));
        data.pages.add(p);
        openPage(p, true);
        i.setAction(null);
    }

    /* ================================================================ UI */
    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(th.bg);
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        // ---------- top bar
        topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(4), dp(4), dp(4), dp(4));
        topBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{th.top, th.bottom}));
        topBar.addView(Ui.icon(this, th, "☰", v -> openDrawer()));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        tcol.setPadding(dp(4), 0, dp(4), 0);
        TextView brand = Ui.text(this, "// AMPYPAD", 15, th.accent, true);
        brand.setShadowLayer(dp(8), 0, 0, Theme.a(th.accent, 160));
        tcol.addView(brand);
        subTitle = Ui.text(this, "", 11, th.muted, false);
        subTitle.setSingleLine(true); subTitle.setEllipsize(TextUtils.TruncateAt.END);
        tcol.addView(subTitle);
        tcol.setOnClickListener(v -> openDrawer());
        topBar.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1f));
        topBar.addView(Ui.icon(this, th, "▤", v -> sheetPanel()));
        topBar.addView(Ui.icon(this, th, "✒", v -> quillPanel()));
        topBar.addView(Ui.icon(this, th, "⇩", v -> exportPanel()));
        topBar.addView(Ui.icon(this, th, "◱", v -> toggleFocus()));
        topBar.addView(Ui.icon(this, th, "⚙", v -> startActivity(new Intent(this, SettingsActivity.class))));
        main.addView(topBar, new LinearLayout.LayoutParams(-1, -2));
        View line = new View(this); line.setBackgroundColor(Theme.a(th.accent, 90));
        main.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));

        // ---------- desk + sheet
        scroll = new ScrollView(this) {
            /** In brush mode a finger on the sheet always paints (two fingers scroll, handled by the ink layer). */
            @Override public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
                if (brushMode && sheet != null) {
                    float y = e.getY() + getScrollY() - desk.getTop(), x = e.getX();
                    if (x >= sheet.getLeft() && x <= sheet.getRight() && y >= sheet.getTop() && y <= sheet.getBottom()) return false;
                }
                return super.onInterceptTouchEvent(e);
            }
        };
        scroll.setFillViewport(true);
        desk = new Views.Desk(this, th);
        desk.setClipChildren(false);
        desk.setPadding(0, dp(16), 0, dp(180));
        sheet = new SheetView(this);
        sheet.setElevation(dp(8));
        ed = new Editor(this, editorListener);
        sheet.setContent(ed.content, ed.box);
        sheet.ink.setListener(inkListener);
        sheet.ink.accent = th.accent;
        desk.sheet = sheet;
        desk.addView(sheet, new FrameLayout.LayoutParams(sheetW, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(desk, new FrameLayout.LayoutParams(-1, -2));
        main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---------- stats line
        statsRow = new LinearLayout(this);
        statsRow.setGravity(Gravity.CENTER_VERTICAL);
        statsRow.setPadding(dp(12), dp(3), dp(12), dp(3));
        statsRow.setBackgroundColor(th.bottom);
        stats = Ui.text(this, "", 10, th.muted, false);
        statsRow.addView(stats, new LinearLayout.LayoutParams(0, -2, 1f));
        saved = Ui.text(this, "● SALVATO", 10, th.accent, true);
        statsRow.addView(saved);
        main.addView(statsRow, new LinearLayout.LayoutParams(-1, -2));

        // ---------- tool bars
        formatBar = buildFormatBar();
        brushBar = buildBrushBar();
        main.addView(formatBar, new LinearLayout.LayoutParams(-1, -2));
        main.addView(brushBar, new LinearLayout.LayoutParams(-1, -2));

        // ---------- bottom: mode switch + sheets
        bottomRow = new LinearLayout(this);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomRow.setPadding(dp(8), dp(4), dp(8), dp(8));
        bottomRow.setBackgroundColor(th.bottom);
        modeSw = new Views.ModeSwitch(this, th);
        modeSw.l = this::setModeAnimated;
        modeSw.setContentDescription("modalita testo pennello");
        bottomRow.addView(modeSw, new LinearLayout.LayoutParams(0, dp(46), 1f));
        bottomRow.addView(Ui.icon(this, th, "◀", v -> neighbour(-1)));
        TextView plus = Ui.chip(this, th, "＋ FOGLIO", this::newPageFrom);
        Ui.chipSel(this, th, plus, true);
        bottomRow.addView(plus);
        bottomRow.addView(Ui.icon(this, th, "▶", v -> neighbour(1)));
        main.addView(bottomRow, new LinearLayout.LayoutParams(-1, -2));

        // ---------- overlays
        quill = new QuillView(this);
        quill.sound = fx -> { if ("scintille".equals(fx) || "neon".equals(fx)) Sfx.chime(); else if (!"nessuno".equals(fx)) Sfx.pop(); };
        root.addView(quill, new FrameLayout.LayoutParams(-1, -1));

        focusExit = Ui.chip(this, th, "◱ ESCI DA CONCENTRAZIONE", v -> toggleFocus());
        focusExit.setVisibility(View.GONE);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        fl.topMargin = dp(10); fl.rightMargin = dp(10);
        root.addView(focusExit, fl);

        scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setAlpha(0f); scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(v -> closeDrawer());
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        buildDrawer();

        toastView = Ui.text(this, "", 12, th.fg, true);
        toastView.setGravity(Gravity.CENTER);
        toastView.setPadding(dp(16), dp(10), dp(16), dp(10));
        toastView.setBackground(Ui.round(Theme.a(th.panel, 245), th.accent, dp(1), dp(th.round + 6)));
        toastView.setElevation(dp(12));
        toastView.setVisibility(View.GONE);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tl.bottomMargin = dp(170); tl.leftMargin = dp(20); tl.rightMargin = dp(20);
        root.addView(toastView, tl);

        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int l, t, r, bt;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                l = s.left; t = s.top; r = s.right; bt = s.bottom;
            } else {
                l = ins.getSystemWindowInsetLeft(); t = ins.getSystemWindowInsetTop(); r = ins.getSystemWindowInsetRight(); bt = ins.getSystemWindowInsetBottom();
            }
            main.setPadding(l, t, r, bt);
            drawer.setPadding(l + dp(12), t + dp(12), dp(12), bt + dp(8));
            FrameLayout.LayoutParams fx = (FrameLayout.LayoutParams) focusExit.getLayoutParams();
            fx.topMargin = t + dp(10); focusExit.setLayoutParams(fx);
            return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : ins.consumeSystemWindowInsets();
        });
        setContentView(root);
    }

    private HorizontalScrollView bar(LinearLayout row) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setBackgroundColor(th.bottom);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(4), dp(6), dp(2));
        hs.addView(row);
        return hs;
    }

    private TextView styled(String s, int style, int flags, View.OnClickListener l) {
        TextView t = Ui.icon(this, th, s, l);
        t.setTypeface(Typeface.create(Typeface.SERIF, style));
        t.setPaintFlags(t.getPaintFlags() | flags);
        return t;
    }

    private HorizontalScrollView buildFormatBar() {
        LinearLayout row = new LinearLayout(this);
        blockBtn = Ui.chip(this, th, "¶ TESTO ▾", v -> blockMenu(ed.focused, 0));
        row.addView(blockBtn);
        row.addView(Ui.icon(this, th, "＋", v -> blockMenu(null, 2)));
        row.addView(styled("B", Typeface.BOLD, 0, v -> fmt("b")));
        row.addView(styled("I", Typeface.ITALIC, 0, v -> fmt("i")));
        row.addView(styled("U", Typeface.NORMAL, Paint.UNDERLINE_TEXT_FLAG, v -> fmt("u")));
        row.addView(styled("S", Typeface.NORMAL, Paint.STRIKE_THRU_TEXT_FLAG, v -> fmt("s")));
        LinearLayout ccol = new LinearLayout(this);
        ccol.setOrientation(LinearLayout.VERTICAL); ccol.setGravity(Gravity.CENTER);
        TextView a = Ui.text(this, "A", 17, th.fg, true); a.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD)); a.setGravity(Gravity.CENTER);
        ccol.addView(a);
        textColorBar = new TextView(this); textColorBar.setBackgroundColor(lastTextColor);
        ccol.addView(textColorBar, new LinearLayout.LayoutParams(dp(18), dp(4)));
        ccol.setMinimumWidth(dp(42)); ccol.setMinimumHeight(dp(42));
        ccol.setOnClickListener(v -> { Ui.tick(v); pickTextColor(); });
        row.addView(ccol);
        row.addView(Ui.icon(this, th, "▰", v -> pickHilite()));
        row.addView(Ui.icon(this, th, "⌀", v -> { if (ed.clearFormatting()) toast("FORMATTAZIONE RIMOSSA"); else toast("TOCCA UN BLOCCO"); }));
        row.addView(Ui.icon(this, th, "◧", v -> align(0)));
        row.addView(Ui.icon(this, th, "▣", v -> align(1)));
        row.addView(Ui.icon(this, th, "◨", v -> align(2)));
        row.addView(Ui.icon(this, th, "«", v -> { if (ed.focused != null) ed.indent(ed.focused, -1); }));
        row.addView(Ui.icon(this, th, "»", v -> { if (ed.focused != null) ed.indent(ed.focused, 1); }));
        row.addView(Ui.icon(this, th, "🎙", v -> dictate()));
        return bar(row);
    }

    private HorizontalScrollView buildBrushBar() {
        LinearLayout row = new LinearLayout(this);
        LinearLayout pick = new LinearLayout(this);
        pick.setGravity(Gravity.CENTER_VERTICAL);
        pick.setPadding(dp(6), dp(4), dp(10), dp(4));
        pick.setBackground(Ui.round(Theme.a(th.panel, 230), Theme.a(th.accent, 110), dp(1), dp(16)));
        brushThumb = new Views.Thumb(this);
        pick.addView(brushThumb, new LinearLayout.LayoutParams(dp(70), dp(28)));
        brushLbl = Ui.text(this, "", 11, th.fg, true);
        brushLbl.setPadding(dp(6), 0, 0, 0);
        pick.addView(brushLbl);
        pick.setOnClickListener(v -> { Ui.tick(v); brushPanel(); });
        row.addView(pick);
        row.addView(Ui.text(this, "  ⌀", 14, th.muted, true));
        sizeBar = new SeekBar(this); sizeBar.setMax(79);
        sizeBar.setOnSeekBarChangeListener(seek(v -> { sheet.ink.size = (v + 1) / 2f; pr.put("brushSize", sheet.ink.size); refreshBrushUi(); }));
        row.addView(sizeBar, new LinearLayout.LayoutParams(dp(110), -2));
        row.addView(Ui.text(this, "◐", 14, th.muted, true));
        opBar = new SeekBar(this); opBar.setMax(95);
        opBar.setOnSeekBarChangeListener(seek(v -> { sheet.ink.opacity = (v + 5) / 100f; pr.put("brushOpacity", sheet.ink.opacity); refreshBrushUi(); }));
        row.addView(opBar, new LinearLayout.LayoutParams(dp(100), -2));
        brushDot = new Views.Dot(this, th, sheet.ink.color);
        brushDot.setOnClickListener(v -> { Ui.tick(v); pickBrushColor(); });
        brushDot.setContentDescription("colore pennello");
        row.addView(brushDot, new LinearLayout.LayoutParams(dp(42), dp(42)));
        symBtn = Ui.icon(this, th, "✣", v -> symPanel());
        row.addView(symBtn);
        layerBtn = Ui.icon(this, th, "⧉", v -> toggleLayer());
        row.addView(layerBtn);
        undoBtn = Ui.icon(this, th, "↶", v -> { if (sheet.ink.undo()) Ui.clock(v); });
        redoBtn = Ui.icon(this, th, "↷", v -> { if (sheet.ink.redo()) Ui.clock(v); });
        row.addView(undoBtn); row.addView(redoBtn);
        row.addView(Ui.icon(this, th, "⌧", v -> clearInk()));
        return bar(row);
    }

    private SeekBar.OnSeekBarChangeListener seek(Panel.IntCb cb) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { if (u) cb.on(p); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        };
    }

    /* ================================================================ drawer */
    private void buildDrawer() {
        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{th.panel, th.bg}));
        drawer.setElevation(dp(16));
        drawer.addView(Ui.text(this, "// QUADERNI", 18, th.accent, true));
        drawer.addView(Ui.text(this, "fogli · blocchi · disegni", 11, th.muted, false));
        search = new EditText(this);
        search.setHint("⌕  cerca nei fogli…"); search.setHintTextColor(th.muted); search.setTextColor(th.fg);
        search.setTypeface(Ui.MONO); search.setTextSize(14); search.setSingleLine(true);
        search.setBackground(Ui.round(th.bg, Theme.a(th.accent, 90), dp(1), dp(th.round + 4)));
        search.setPadding(dp(12), dp(9), dp(12), dp(9));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refreshDrawer(); }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2); sl.topMargin = dp(12);
        drawer.addView(search, sl);
        LinearLayout acts = new LinearLayout(this);
        TextView np = Ui.chip(this, th, "＋ FOGLIO", v -> { closeDrawer(); newPageFrom(v); });
        Ui.chipSel(this, th, np, true);
        acts.addView(np);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, -2); al.leftMargin = dp(8);
        acts.addView(Ui.chip(this, th, "＋ QUADERNO", v -> newNotebook()), al);
        LinearLayout.LayoutParams acl = new LinearLayout.LayoutParams(-1, -2); acl.topMargin = dp(10);
        drawer.addView(acts, acl);
        ScrollView sv = new ScrollView(this);
        drawerList = new LinearLayout(this);
        drawerList.setOrientation(LinearLayout.VERTICAL);
        drawerList.setPadding(0, dp(8), 0, dp(8));
        sv.addView(drawerList);
        drawer.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.addView(Ui.chip(this, th, "⇩ BACKUP", v -> backup()));
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-2, -2); fl.leftMargin = dp(6);
        foot.addView(Ui.chip(this, th, "⇪ IMPORTA", v -> importBackup()), fl);
        foot.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        foot.addView(Ui.icon(this, th, "⚙", v -> { closeDrawer(); startActivity(new Intent(this, SettingsActivity.class)); }));
        drawer.addView(foot);
        int w = Math.min(dp(320), getResources().getDisplayMetrics().widthPixels - dp(48));
        drawer.setTranslationX(-w - dp(20));
        drawer.setVisibility(View.GONE);
        root.addView(drawer, new FrameLayout.LayoutParams(w, -1, Gravity.START));
    }

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        ed.syncAll(); ed.hideKeyboard();
        refreshDrawer();
        drawer.setVisibility(View.VISIBLE); scrim.setVisibility(View.VISIBLE);
        drawer.animate().translationX(0).setDuration(320).setInterpolator(new DecelerateInterpolator(2f)).start();
        scrim.animate().alpha(1f).setDuration(260).start();
        updateBack();
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        drawer.animate().translationX(-drawer.getWidth() - dp(20)).setDuration(260).setInterpolator(new AccelerateInterpolator(1.5f))
            .withEndAction(() -> drawer.setVisibility(View.GONE)).start();
        scrim.animate().alpha(0f).setDuration(240).withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        updateBack();
    }

    private void refreshDrawer() {
        drawerList.removeAllViews();
        String q = search.getText().toString().trim().toLowerCase(Locale.ITALIAN);
        int[] k = {0};
        if (!q.isEmpty()) {
            int found = 0;
            for (Model.Page p : data.pages) {
                String all = p.plainText().toLowerCase(Locale.ITALIAN);
                int at = all.indexOf(q);
                if (at < 0) continue;
                String txt = p.plainText().replace('\n', ' ');
                int s0 = Math.max(0, at - 18), s1 = Math.min(txt.length(), at + q.length() + 30);
                drawerList.addView(pageRow(p, "…" + txt.substring(s0, s1).trim() + "…", k[0]++));
                found++;
            }
            if (found == 0) drawerList.addView(Ui.text(this, "Nessun foglio contiene «" + q + "»", 12, th.muted, false));
            return;
        }
        for (Model.Notebook nb : data.notebooks) {
            List<Model.Page> ps = data.pagesOf(nb.id);
            LinearLayout hd = new LinearLayout(this);
            hd.setGravity(Gravity.CENTER_VERTICAL);
            hd.setPadding(dp(4), dp(10), 0, dp(4));
            TextView chev = Ui.text(this, nb.collapsed ? "▸" : "▾", 13, th.muted, true);
            hd.addView(chev, new LinearLayout.LayoutParams(dp(18), -2));
            View dot = new View(this); dot.setBackground(Ui.round(nb.color, 0, 0, dp(5)));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(10), dp(10)); dl.rightMargin = dp(8);
            hd.addView(dot, dl);
            TextView nm = Ui.text(this, nb.name.toUpperCase(Locale.ITALIAN) + "  " + ps.size(), 12, th.secondary, true);
            nm.setSingleLine(true); nm.setEllipsize(TextUtils.TruncateAt.END);
            hd.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView more = Ui.text(this, "⋯", 18, th.muted, true);
            more.setPadding(dp(10), 0, dp(6), 0);
            more.setOnClickListener(v -> { Ui.tick(v); notebookMenu(nb); });
            hd.addView(more);
            hd.setOnClickListener(v -> { Ui.tick(v); nb.collapsed = !nb.collapsed; scheduleSave(); refreshDrawer(); });
            drawerList.addView(hd);
            if (!nb.collapsed) for (Model.Page p : ps) drawerList.addView(pageRow(p, null, k[0]++));
        }
    }

    private View pageRow(Model.Page p, String snippet, int i) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(8), dp(7), dp(8), dp(7));
        boolean active = page != null && p.id.equals(page.id);
        r.setBackground(Ui.round(active ? Theme.a(th.accent, 34) : 0, Theme.a(th.accent, active ? 200 : 0), active ? dp(1) : 0, dp(th.round + 2)));
        final Model.Style st = p.style;
        View thumb = new View(this) {
            @Override protected void onDraw(Canvas c) {
                float u = getWidth() / 400f * 4f;
                PaperStyle.draw(c, getWidth(), getHeight(), st, u, u * 18, u * 6, u * 12);
                Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG); pp.setStyle(Paint.Style.STROKE); pp.setColor(Theme.a(th.fg, 60));
                c.drawRect(0.5f, 0.5f, getWidth() - 0.5f, getHeight() - 0.5f, pp);
            }
        };
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(dp(30), dp(40)); tl.rightMargin = dp(10);
        r.addView(thumb, tl);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, (p.pinned ? "★ " : "") + p.displayTitle(), 14, active ? th.accent : th.fg, true);
        t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t);
        String meta = snippet != null ? snippet : new java.text.SimpleDateFormat("d MMM yyyy · HH:mm", Locale.ITALIAN).format(new java.util.Date(p.updated))
            + " · " + words(p.plainText()) + " parole" + (p.strokes.isEmpty() ? "" : " · ✎ " + p.strokes.size());
        TextView m = Ui.text(this, meta, 10.5f, th.muted, false);
        m.setSingleLine(true); m.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(m);
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        r.setOnClickListener(v -> { Ui.tick(v); closeDrawer(); if (!p.id.equals(page.id)) h.postDelayed(() -> openPage(p, true), 120); });
        r.setOnLongClickListener(v -> { Ui.clock(v); pageMenu(p); return true; });
        r.setAlpha(0f); r.setTranslationX(-dp(16));
        r.animate().alpha(1f).translationX(0).setStartDelay(Math.min(400, 30L * i)).setDuration(260).start();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(3);
        r.setLayoutParams(lp);
        return r;
    }

    private static int words(String s) {
        String t = s.trim();
        if (t.isEmpty()) return 0;
        return t.split("\\s+").length;
    }

    /* ================================================================ pages */
    private void openPage(Model.Page p, boolean animate) {
        if (ed.page != null) ed.syncAll();
        Bitmap snap = null;
        if (animate && sheet.getWidth() > 0 && sheet.getHeight() > 0) {
            View f = getCurrentFocus(); if (f != null) f.clearFocus();
            scroll.scrollTo(0, 0);
            snap = snapshot(0);
        }
        page = p;
        data.current = p.id;
        sheet.extraUnits = p.extra;
        sheet.ink.setPage(p);
        ed.load(p);
        applyStyle();
        refreshBrushUi();
        updateStats();
        subTitle.setText(p.displayTitle().toUpperCase(Locale.ITALIAN));
        scheduleSave();
        if (snap != null) runTransition(snap);
    }

    private Bitmap snapshot(int top) {
        int hh = Math.max(1, Math.min(sheet.getHeight() - top, scroll.getHeight()));
        try {
            Bitmap b = Bitmap.createBitmap(sheet.getWidth(), hh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.translate(0, -top);
            sheet.draw(c);
            return b;
        } catch (OutOfMemoryError e) { return null; }
    }

    private ImageView ghost(Bitmap snap, int top) {
        ImageView g = new ImageView(this);
        g.setImageBitmap(snap);
        g.setScaleType(ImageView.ScaleType.FIT_XY);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(sheet.getWidth(), snap.getHeight(), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        gl.topMargin = top;
        g.setElevation(sheet.getElevation() + dp(2));
        desk.addView(g, gl);
        return g;
    }

    private void runTransition(Bitmap snap) {
        ImageView g = ghost(snap, 0);
        Runnable rm = () -> desk.removeView(g);
        float w = sheet.getWidth();
        sheet.animate().cancel();
        sheet.setTranslationX(0); sheet.setTranslationY(0); sheet.setRotation(0); sheet.setScaleX(1); sheet.setScaleY(1); sheet.setAlpha(1);
        Sfx.swish();
        switch (pr.transition()) {
            case "scivola":
                g.animate().translationX(-w * 1.25f).rotation(-6).alpha(0f).setDuration(520).setInterpolator(new AccelerateInterpolator(1.3f)).withEndAction(rm).start();
                sheet.setTranslationX(w * 1.1f); sheet.setRotation(5);
                sheet.animate().translationX(0).rotation(0).setDuration(620).setStartDelay(80).setInterpolator(new DecelerateInterpolator(2f)).start();
                break;
            case "dissolvi":
                g.animate().alpha(0f).scaleX(1.05f).scaleY(1.05f).setDuration(480).withEndAction(rm).start();
                sheet.setAlpha(0f); sheet.setScaleX(0.97f); sheet.setScaleY(0.97f);
                sheet.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520).setStartDelay(100).start();
                break;
            case "caduta":
                g.animate().translationY(dp(90)).rotation(-7).scaleX(0.9f).scaleY(0.9f).alpha(0f).setDuration(560).setInterpolator(new AccelerateInterpolator()).withEndAction(rm).start();
                sheet.setTranslationY(-scroll.getHeight() * 1.1f); sheet.setRotation(4);
                sheet.animate().translationY(0).rotation(0).setDuration(720).setInterpolator(new OvershootInterpolator(0.9f)).start();
                break;
            case "vortice":
                g.animate().rotation(-28).scaleX(0.15f).scaleY(0.15f).alpha(0f).setDuration(600).withEndAction(rm).start();
                sheet.setRotation(20); sheet.setScaleX(0.4f); sheet.setScaleY(0.4f); sheet.setAlpha(0f);
                sheet.animate().rotation(0).scaleX(1f).scaleY(1f).alpha(1f).setDuration(700).setStartDelay(120).setInterpolator(new OvershootInterpolator(1.2f)).start();
                break;
            case "glitch": {
                ValueAnimator va = ValueAnimator.ofFloat(0, 1);
                va.setDuration(560);
                va.addUpdateListener(x -> {
                    float f = (float) x.getAnimatedValue();
                    g.setTranslationX((rnd.nextFloat() - 0.5f) * dp(46) * (1 - f));
                    g.setAlpha(rnd.nextFloat() > 0.3f ? 1 - f : 0.15f);
                    g.setScaleY(1 + (rnd.nextFloat() - 0.5f) * 0.05f);
                    sheet.setAlpha(f > 0.4f && rnd.nextFloat() > 0.25f ? f : 0.1f);
                });
                va.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { rm.run(); sheet.setAlpha(1f); } });
                va.start();
                int[] loc = new int[2]; scroll.getLocationOnScreen(loc);
                quill.burst("neon", loc[0] + scroll.getWidth() / 2f, loc[1] + scroll.getHeight() / 3f, 40, th.accent);
                break;
            }
            default: // sfoglia
                g.setPivotX(0); g.setPivotY(snap.getHeight() / 2f);
                g.setCameraDistance(12000 * getResources().getDisplayMetrics().density);
                g.animate().rotationY(-110).alpha(0.1f).setDuration(760).setInterpolator(new AccelerateDecelerateInterpolator()).withEndAction(rm).start();
                sheet.setAlpha(0.45f); sheet.setScaleX(0.985f); sheet.setScaleY(0.985f);
                sheet.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600).setStartDelay(160).start();
                break;
        }
    }

    private void newPageFrom(View from) {
        ed.syncAll();
        Model.Page p = Store.newPage(data, page.notebookId);
        int at = data.pages.indexOf(page);
        data.pages.add(at < 0 ? data.pages.size() : at + 1, p);
        openPage(p, true);
        if (from != null) {
            int[] loc = new int[2]; from.getLocationOnScreen(loc);
            quill.burst(effect(), loc[0] + from.getWidth() / 2f, loc[1], 26, quillColor());
        }
        h.postDelayed(() -> { if (!brushMode) { ed.title.requestFocus(); ed.focus(ed.rows.isEmpty() ? null : ed.rows.get(0), 0); ed.title.requestFocus(); } }, 780);
    }

    private void neighbour(int d) {
        ed.syncAll();
        List<Model.Page> ps = data.pagesOf(page.notebookId);
        int i = ps.indexOf(page), j = i + d;
        if (j < 0) { toast("◀ PRIMO FOGLIO DEL QUADERNO"); return; }
        if (j >= ps.size()) { toast("ULTIMO FOGLIO ▶  ·  TOCCA ＋ FOGLIO"); return; }
        openPage(ps.get(j), true);
    }

    private void newNotebook() {
        prompt("NUOVO QUADERNO", "Quaderno " + (data.notebooks.size() + 1), name -> {
            Model.Notebook nb = new Model.Notebook();
            nb.name = name;
            int[] cols = {0xff00ffe2, 0xffff3ac8, 0xffffd16a, 0xff7a96ff, 0xff50c878, 0xffff6177};
            nb.color = cols[data.notebooks.size() % cols.length];
            data.notebooks.add(nb);
            Model.Page p = Store.newPage(data, nb.id);
            data.pages.add(p);
            closeDrawer();
            openPage(p, true);
        });
    }

    interface StrCb { void on(String s); }
    private void prompt(String title, String val, StrCb cb) {
        EditText e = new EditText(this);
        e.setText(val); e.setSelectAllOnFocus(true); e.setSingleLine(true);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle(title).setView(e)
            .setPositiveButton("OK", (d, w) -> { String s = e.getText().toString().trim(); if (!s.isEmpty()) cb.on(s); })
            .setNegativeButton("ANNULLA", null).show();
    }
    private void confirm(String title, String msg, String ok, Runnable r) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle(title).setMessage(msg)
            .setPositiveButton(ok, (d, w) -> r.run()).setNegativeButton("ANNULLA", null).show();
    }

    private void pageMenu(Model.Page p) {
        Panel pn = new Panel(this, th, "FOGLIO · " + p.displayTitle().toUpperCase(Locale.ITALIAN));
        pn.action("✎", "RINOMINA", null, () -> { pn.dismiss(); prompt("TITOLO DEL FOGLIO", p.title, s -> { p.title = s; if (p == page) ed.title.setQuiet(s); afterPagesChanged(); }); });
        pn.action(p.pinned ? "☆" : "★", p.pinned ? "SGANCIA" : "FISSA IN ALTO", "i fogli fissati restano in cima al quaderno", () -> { pn.dismiss(); p.pinned = !p.pinned; afterPagesChanged(); });
        pn.action("⧉", "DUPLICA", null, () -> {
            pn.dismiss(); ed.syncAll();
            try {
                Model.Page c = Model.Page.from(p.json());
                c.id = Model.uid(); c.title = p.displayTitle() + " (copia)"; c.created = c.updated = System.currentTimeMillis(); c.pinned = false;
                for (Model.Block b : c.blocks) b.id = Model.uid();
                data.pages.add(data.pages.indexOf(p) + 1, c);
                afterPagesChanged();
            } catch (Exception ignored) { }
        });
        if (data.notebooks.size() > 1) pn.action("⇄", "SPOSTA IN UN ALTRO QUADERNO", null, () -> {
            pn.dismiss();
            String[] names = new String[data.notebooks.size()];
            for (int i = 0; i < names.length; i++) names[i] = data.notebooks.get(i).name;
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("SPOSTA IN…")
                .setItems(names, (d, w) -> { p.notebookId = data.notebooks.get(w).id; afterPagesChanged(); }).show();
        });
        pn.action("⌧", "ELIMINA", "non si può annullare", () -> { pn.dismiss(); confirm("Eliminare il foglio?", "«" + p.displayTitle() + "» verrà eliminato.", "ELIMINA", () -> deletePage(p)); });
        pn.show();
    }

    private void deletePage(Model.Page p) {
        boolean cur = p == page;
        List<Model.Page> ps = data.pagesOf(p.notebookId);
        int i = ps.indexOf(p);
        data.pages.remove(p);
        if (cur) {
            Model.Page next = null;
            List<Model.Page> rest = data.pagesOf(p.notebookId);
            if (!rest.isEmpty()) next = rest.get(Math.max(0, Math.min(i, rest.size() - 1)));
            else if (!data.pages.isEmpty()) next = data.pages.get(0);
            if (next == null) { next = Store.newPage(data, p.notebookId); data.pages.add(next); }
            ed.page = null;
            openPage(next, true);
        }
        afterPagesChanged();
    }

    private void notebookMenu(Model.Notebook nb) {
        Panel pn = new Panel(this, th, "QUADERNO · " + nb.name.toUpperCase(Locale.ITALIAN));
        pn.action("✎", "RINOMINA", null, () -> { pn.dismiss(); prompt("NOME DEL QUADERNO", nb.name, s -> { nb.name = s; afterPagesChanged(); }); });
        pn.action("●", "COLORE", null, () -> { pn.dismiss(); ColorPicker.show(this, th, "COLORE DEL QUADERNO", nb.color, false, null, c -> { nb.color = c; afterPagesChanged(); }); });
        pn.action("＋", "NUOVO FOGLIO QUI", null, () -> {
            pn.dismiss(); closeDrawer(); ed.syncAll();
            Model.Page p = Store.newPage(data, nb.id); data.pages.add(p); openPage(p, true);
        });
        if (data.notebooks.size() > 1) pn.action("⌧", "ELIMINA QUADERNO", "elimina anche tutti i suoi fogli", () -> {
            pn.dismiss();
            confirm("Eliminare il quaderno?", "«" + nb.name + "» e i suoi " + data.pagesOf(nb.id).size() + " fogli verranno eliminati.", "ELIMINA", () -> {
                boolean cur = page.notebookId.equals(nb.id);
                List<Model.Page> rm = data.pagesOf(nb.id);
                data.pages.removeAll(rm);
                data.notebooks.remove(nb);
                if (data.pages.isEmpty()) data.pages.add(Store.newPage(data, data.notebooks.get(0).id));
                if (cur) { ed.page = null; openPage(data.pages.get(0), true); }
                afterPagesChanged();
            });
        });
        pn.show();
    }

    private void afterPagesChanged() {
        subTitle.setText(page.displayTitle().toUpperCase(Locale.ITALIAN));
        scheduleSave();
        if (drawerOpen) refreshDrawer();
    }

    /* ================================================================ style */
    private void applyStyle() {
        Model.Style s = page.style;
        float u = sheetW / 400f;
        int[] m = SheetView.metricsFor(s, u);
        sheet.style = s;
        sheet.setMetrics(u, m[1], m[2]);
        ed.selColor = Theme.a(Ui.luminance(s.paper) > 0.5f ? 0xff3b5bdb : th.accent, 90);
        ed.setMetrics(u, m[0], m[1], m[2], PaperStyle.typeface(s.font), s.ink);
        desk.paper = s.paper;
        sheet.invalidate(); desk.invalidate();
        applyQuill();
        refreshBrushUi();
    }

    private void styleChanged(boolean custom) {
        if (custom) page.style.preset = "custom";
        applyStyle();
        scheduleSave();
    }

    private int quillColor() { return pr.quillFollowStyle() ? page.style.quill : pr.quillColor(); }
    private String effect() { return pr.quillFollowStyle() ? page.style.effect : pr.quillEffect(); }

    private void applyQuill() {
        if (quill == null || page == null) return;
        quill.enabled = pr.quillOn();
        quill.trail = pr.quillTrail();
        quill.delay = pr.quillDelay();
        quill.scale = pr.quillSize() / 100f;
        quill.effect = effect();
        if (brushMode && pr.quillAutoBrush()) {
            quill.icon = "gomma".equals(sheet.ink.brush) ? "matita" : "pennello";
            quill.color = "gomma".equals(sheet.ink.brush) ? 0xffff8fab : sheet.ink.color;
        } else {
            quill.icon = pr.quillIcon();
            quill.color = quillColor();
        }
    }

    private void applyPresetAnimated(PaperStyle.Preset p, View card) {
        int top = Math.max(0, scroll.getScrollY() - sheet.getTop());
        Bitmap snap = snapshot(top);
        p.apply(page.style);
        applyStyle();
        scheduleSave();
        if (snap == null) return;
        ImageView g = ghost(snap, top);
        g.post(() -> {
            try {
                float cx = g.getWidth() / 2f, cy = Math.min(g.getHeight(), scroll.getHeight()) / 2f;
                Animator a = ViewAnimationUtils.createCircularReveal(g, (int) cx, (int) cy, (float) Math.hypot(g.getWidth(), g.getHeight()), 0);
                a.setDuration(700); a.setInterpolator(new AccelerateDecelerateInterpolator());
                a.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator x) { desk.removeView(g); } });
                a.start();
            } catch (Exception e) { desk.removeView(g); }
        });
        int[] loc = new int[2]; scroll.getLocationOnScreen(loc);
        quill.burst(p.effect, loc[0] + scroll.getWidth() / 2f, loc[1] + scroll.getHeight() / 2.5f, 30, p.quill);
    }

    /* ================================================================ mode */
    private void setModeAnimated(boolean b) { setMode(b, true); }

    private void setMode(boolean b, boolean anim) {
        brushMode = b;
        sheet.ink.enabled = b;
        ed.setTextMode(!b);
        modeSw.set(b, anim);
        View show = b ? brushBar : formatBar, hide = b ? formatBar : brushBar;
        hide.setVisibility(View.GONE);
        show.setVisibility(View.VISIBLE);
        if (anim) {
            show.setAlpha(0f); show.setTranslationY(dp(14));
            show.animate().alpha(1f).translationY(0).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
            if (b) toast("✎ PENNELLO · due dita per scorrere · tieni fermo per una linea dritta");
        }
        if (b && sheet.ink.sym != 0) centerSymmetry();
        applyQuill();
        sheet.ink.invalidate();
        refreshBrushUi();
    }

    /* ================================================================ text formatting */
    private void fmt(String type) { if (!ed.toggle(type)) toast("TOCCA UN BLOCCO E SELEZIONA IL TESTO"); }
    private void align(int a) { if (ed.focused != null) ed.setAlign(ed.focused, a); else toast("TOCCA UN BLOCCO"); }

    private void pickTextColor() {
        if (ed.focused == null) { toast("TOCCA UN BLOCCO E SELEZIONA IL TESTO"); return; }
        ColorPicker.show(this, th, ed.hasSelection() ? "COLORE DEL TESTO SELEZIONATO" : "COLORE DEL BLOCCO", lastTextColor, true, null, c -> {
            ed.color("fg", c);
            if (c != 0) { lastTextColor = c; textColorBar.setBackgroundColor(c); }
        });
    }
    private void pickHilite() {
        if (ed.focused == null) { toast("TOCCA UN BLOCCO E SELEZIONA IL TESTO"); return; }
        ColorPicker.show(this, th, "EVIDENZIATORE DEL TESTO", lastHilite, true, null, c -> {
            ed.color("bg", c == 0 ? 0 : Theme.a(c, 150));
            if (c != 0) lastHilite = c;
        });
    }

    private void dictate() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Detta, AmpyPad scrive…");
        try { startActivityForResult(i, RC_SPEECH); } catch (Exception e) { toast("DETTATURA NON DISPONIBILE SU QUESTO TELEFONO"); }
    }

    /* ================================================================ blocks panels */
    /** mode 0 = convert focused, 1 = from "/", 2 = add new block. */
    private void blockMenu(Editor.Row r, int mode) {
        if (mode == 0 && r == null) mode = 2;
        final int md = mode;
        Panel pn = new Panel(this, th, md == 2 ? "AGGIUNGI BLOCCO" : md == 1 ? "BLOCCHI" : "TRASFORMA BLOCCO");
        pn.note("Blocchi per organizzare il foglio · funzionano anche le scorciatoie (es. «- » o «[] »)");
        for (int i = 0; i < Editor.TYPES.length; i++) {
            final String type = Editor.TYPES[i];
            pn.action(Editor.TYPE_GLYPHS[i], Editor.TYPE_NAMES[i], Editor.TYPE_HINTS[i], () -> {
                pn.dismiss();
                h.postDelayed(() -> {
                    if (md == 2 || r == null || !ed.rows.contains(r)) ed.addBlock(type);
                    else if (md == 1) ed.applySlash(r, type);
                    else ed.setType(r, type);
                    updateBlockBtn(ed.focused);
                }, 230);
            });
        }
        pn.show();
    }

    private void rowMenu(Editor.Row r) {
        Panel pn = new Panel(this, th, "BLOCCO · " + Editor.TYPE_NAMES[Editor.typeIndex(r.b.type)]);
        pn.section("TRASFORMA IN");
        pn.chips(Editor.TYPE_NAMES, Editor.typeIndex(r.b.type), i -> { pn.dismiss(); h.postDelayed(() -> { if (ed.rows.contains(r)) ed.setType(r, Editor.TYPES[i]); }, 220); });
        pn.section("COLORE DEL BLOCCO");
        LinearLayout strip = pn.strip();
        for (int i = 0; i < Editor.COLORS.length; i++) {
            final int k = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER_HORIZONTAL);
            Views.Dot d = new Views.Dot(this, th, Editor.COLOR_SOLID[i]);
            d.set(Editor.COLOR_SOLID[i]);
            cell.addView(d, new LinearLayout.LayoutParams(dp(40), dp(40)));
            cell.addView(Ui.text(this, Editor.COLOR_NAMES[i], 8.5f, Editor.COLORS[i].equals(r.b.color) ? th.accent : th.muted, true));
            cell.setPadding(dp(4), 0, dp(4), 0);
            cell.setOnClickListener(v -> { Ui.tick(v); ed.setColor(r, Editor.COLORS[k]); pn.dismiss(); });
            strip.addView(cell);
        }
        if (r.edit != null) {
            pn.section("ALLINEAMENTO");
            pn.chips(new String[]{"◧ SINISTRA", "▣ CENTRO", "◨ DESTRA"}, r.b.align, i -> ed.setAlign(r, i));
        }
        pn.section("AZIONI");
        pn.action("⇧", "SPOSTA SU", "oppure tieni premuto ⠿ e trascina", () -> ed.move(r, -1));
        pn.action("⇩", "SPOSTA GIÙ", null, () -> ed.move(r, 1));
        pn.action("»", "RIENTRA", "per sotto-elenchi e gerarchie", () -> ed.indent(r, 1));
        pn.action("«", "RIDUCI RIENTRO", null, () -> ed.indent(r, -1));
        pn.action("⧉", "DUPLICA", null, () -> { pn.dismiss(); ed.duplicate(r); });
        pn.action("⌧", "ELIMINA BLOCCO", null, () -> { pn.dismiss(); ed.delete(r); Sfx.pop(); });
        pn.show();
    }

    private void updateBlockBtn(Editor.Row r) {
        if (blockBtn == null) return;
        String t = r == null ? "p" : r.b.type;
        int i = Editor.typeIndex(t);
        blockBtn.setText(Editor.TYPE_GLYPHS[i] + " " + Editor.TYPE_NAMES[i] + " ▾");
    }

    /* ================================================================ sheet panel */
    private void sheetPanel() {
        ed.syncAll();
        Panel pn = new Panel(this, th, "STILE DEL FOGLIO");
        pn.section("STILI PREDEFINITI");
        LinearLayout strip = pn.strip();
        List<Views.PresetCard> cards = new ArrayList<>();
        for (PaperStyle.Preset p : PaperStyle.PRESETS) {
            Views.PresetCard c = new Views.PresetCard(this, th, p);
            c.setContentDescription("stile " + p.name);
            c.sel = p.key.equals(page.style.preset);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(74), dp(112)); lp.rightMargin = dp(8);
            strip.addView(c, lp);
            cards.add(c);
            c.setOnClickListener(v -> {
                Ui.tick(v);
                for (Views.PresetCard o : cards) { o.sel = o == c; o.invalidate(); }
                v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(100).withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
                applyPresetAnimated(p, v);
            });
        }
        pn.section("MOTIVO DEL FOGLIO");
        pn.chips(PaperStyle.PATTERN_NAMES, PaperStyle.indexOf(PaperStyle.PATTERNS, page.style.pattern), i -> { page.style.pattern = PaperStyle.PATTERNS[i]; styleChanged(true); });
        pn.section("COLORI (tocca per la tavolozza)");
        LinearLayout cs = pn.strip();
        String[] names = {"FOGLIO", "RIGHE", "INCHIOSTRO", "PIUMA"};
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(8), 0, dp(8), 0);
            Views.Dot d = new Views.Dot(this, th, styleColor(k));
            cell.addView(d, new LinearLayout.LayoutParams(dp(48), dp(48)));
            cell.addView(Ui.text(this, names[i], 9.5f, th.muted, true));
            cell.setOnClickListener(v -> {
                Ui.tick(v);
                int old = styleColor(k);
                ColorPicker.show(this, th, "COLORE " + names[k], old, false,
                    c -> { setStyleColor(k, c); d.set(c); styleChanged(false); },
                    c -> { setStyleColor(k, c); d.set(c); styleChanged(true); });
            });
            cs.addView(cell);
        }
        pn.section("TEXTURE DELLA CARTA");
        pn.chips(PaperStyle.TEXTURE_NAMES, PaperStyle.indexOf(PaperStyle.TEXTURES, page.style.texture), i -> { page.style.texture = PaperStyle.TEXTURES[i]; styleChanged(true); });
        pn.section("CARATTERE");
        Typeface[] faces = new Typeface[PaperStyle.FONTS.length];
        for (int i = 0; i < faces.length; i++) faces[i] = PaperStyle.typeface(PaperStyle.FONTS[i]);
        pn.chips(PaperStyle.FONT_NAMES, PaperStyle.indexOf(PaperStyle.FONTS, page.style.font), faces, i -> { page.style.font = PaperStyle.FONTS[i]; styleChanged(true); });
        pn.slider("DIMENSIONE", 12, 30, page.style.fontSize, " pt", v -> { page.style.fontSize = v; styleChanged(true); });
        pn.section("DISEGNO");
        pn.toggle("Disegno sotto il testo", "il testo resta sempre leggibile sopra la pittura", pr.b("inkBelow", false), v -> { pr.put("inkBelow", v); sheet.setInkBelow(v); refreshBrushUi(); });
        pn.section("FOGLIO");
        pn.action("↧", "ALLUNGA IL FOGLIO", "aggiunge mezza pagina in fondo", () -> { pn.dismiss(); extendSheet(); });
        pn.action("★", "USA QUESTO STILE PER I NUOVI FOGLI", null, () -> { data.defaults = page.style.copy(); scheduleSave(); toast("★ STILE PREDEFINITO SALVATO"); });
        pn.action("⧉", "APPLICA A TUTTO IL QUADERNO", null, () -> {
            for (Model.Page p : data.pagesOf(page.notebookId)) p.style = page.style.copy();
            scheduleSave(); toast("⧉ STILE APPLICATO A " + data.pagesOf(page.notebookId).size() + " FOGLI");
        });
        pn.show();
    }

    private int styleColor(int k) {
        Model.Style s = page.style;
        return k == 0 ? s.paper : k == 1 ? s.line : k == 2 ? s.ink : s.quill;
    }
    private void setStyleColor(int k, int c) {
        Model.Style s = page.style;
        if (k == 0) s.paper = c; else if (k == 1) s.line = c; else if (k == 2) s.ink = c; else s.quill = c;
    }

    private void extendSheet() {
        page.extra += 280;
        sheet.extraUnits = page.extra;
        sheet.requestLayout();
        scheduleSave();
        h.postDelayed(() -> scroll.smoothScrollTo(0, desk.getHeight()), 120);
        toast("↧ FOGLIO ALLUNGATO");
    }

    /* ================================================================ quill panel */
    private void quillPanel() {
        Panel pn = new Panel(this, th, "PIUMA");
        pn.note("Mentre scrivi o disegni la piuma resta sollevata; quando ti fermi si posa sul foglio e scompare con un effetto.");
        pn.toggle("Piuma attiva", null, pr.quillOn(), v -> { pr.put("quillOn", v); applyQuill(); });
        pn.section("ICONA");
        LinearLayout strip = pn.strip();
        List<Views.IconCard> cards = new ArrayList<>();
        for (int i = 0; i < QuillView.ICONS.length; i++) {
            final String ic = QuillView.ICONS[i];
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER_HORIZONTAL);
            Views.IconCard c = new Views.IconCard(this, th, ic, quillColor());
            c.setContentDescription("icona " + QuillView.ICON_NAMES[i]);
            c.sel = ic.equals(pr.quillIcon());
            cell.addView(c, new LinearLayout.LayoutParams(dp(66), dp(66)));
            cell.addView(Ui.text(this, QuillView.ICON_NAMES[i], 8, th.muted, true));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(82), -2);
            strip.addView(cell, lp);
            cards.add(c);
            cell.setOnClickListener(v -> {
                Ui.tick(v); pr.put("quillIcon", ic);
                for (Views.IconCard o : cards) { o.sel = o == c; o.invalidate(); }
                applyQuill(); demoQuill(v);
            });
        }
        pn.section("COLORE");
        pn.toggle("Colore ed effetto dallo stile del foglio", "disattiva per scegliere tu", pr.quillFollowStyle(), v -> { pr.put("quillFollow", v); applyQuill(); });
        pn.action("●", "SCEGLI IL COLORE DELLA PIUMA", null, () -> ColorPicker.show(this, th, "COLORE PIUMA", quillColor(), false, null, c -> {
            pr.put("quillColor", c); pr.put("quillFollow", false);
            for (Views.IconCard o : cards) { o.color = c; o.invalidate(); }
            applyQuill();
        }));
        pn.section("EFFETTO QUANDO SI POSA");
        pn.chips(PaperStyle.EFFECT_NAMES, PaperStyle.indexOf(PaperStyle.EFFECTS, effect()), i -> {
            pr.put("quillEffect", PaperStyle.EFFECTS[i]); pr.put("quillFollow", false); applyQuill();
            int[] loc = new int[2]; scroll.getLocationOnScreen(loc);
            quill.burst(PaperStyle.EFFECTS[i], loc[0] + scroll.getWidth() / 2f, loc[1] + scroll.getHeight() / 3f, 24, quillColor());
        });
        pn.toggle("Scia mentre scrivi", null, pr.quillTrail(), v -> { pr.put("quillTrail", v); applyQuill(); });
        pn.toggle("Icona pennello in modalità pennello", "la piuma diventa un pennello intinto nel colore scelto", pr.quillAutoBrush(), v -> { pr.put("quillAutoBrush", v); applyQuill(); });
        pn.toggle("Suoni (graffio della piuma, pagine)", null, pr.sound(), v -> { pr.put("sound", v); Sfx.on = v; });
        pn.slider("ATTESA PRIMA DI POSARSI", 300, 2500, pr.quillDelay(), " ms", v -> { pr.put("quillDelay", v); applyQuill(); });
        pn.slider("DIMENSIONE PIUMA", 60, 170, pr.quillSize(), " %", v -> { pr.put("quillSize", v); applyQuill(); });
        pn.action("✦", "PROVA L'EFFETTO", null, () -> demoQuill(null));
        pn.show();
    }

    private void demoQuill(View from) {
        int[] loc = new int[2]; scroll.getLocationOnScreen(loc);
        float x = loc[0] + scroll.getWidth() * 0.35f, y = loc[1] + scroll.getHeight() * 0.3f;
        applyQuill();
        quill.demo(x, y);
    }

    /* ================================================================ brush */
    private void loadBrush() {
        InkView k = sheet.ink;
        k.brush = pr.s("brush", "penna");
        k.color = pr.i("brushColor", 0xff1f3a93);
        k.size = pr.f("brushSize", 3f);
        k.opacity = pr.f("brushOpacity", 1f);
        k.sym = pr.i("sym", 0);
        k.emoji = pr.s("stamp", "⭐");
        sheet.setInkBelow(pr.b("inkBelow", false));
    }

    private void refreshBrushUi() {
        if (brushThumb == null || page == null) return;
        InkView k = sheet.ink;
        brushThumb.set(Brushes.preview(k.brush, k.color, Math.max(2f, k.size), Math.max(0.3f, k.opacity), k.emoji, dp(70), dp(28), page.style.paper));
        brushLbl.setText(Brushes.NAMES[Brushes.index(k.brush)]);
        brushDot.set(k.color);
        sizeBar.setProgress(Math.round(k.size * 2) - 1);
        opBar.setProgress(Math.round(k.opacity * 100) - 5);
        Ui.iconOn(this, th, symBtn, k.sym != 0);
        Ui.iconOn(this, th, layerBtn, sheet.inkBelow);
        refreshUndo();
    }
    private void refreshUndo() {
        if (undoBtn == null) return;
        undoBtn.setAlpha(sheet.ink.canUndo() ? 1f : 0.35f);
        redoBtn.setAlpha(sheet.ink.canRedo() ? 1f : 0.35f);
    }

    private void brushPanel() {
        Panel pn = new Panel(this, th, "PENNELLO UNICO");
        pn.note("Un solo strumento per scrivere, disegnare e dipingere: scegli la punta.");
        List<LinearLayout> cells = pn.grid(Brushes.KEYS.length, 3);
        List<LinearLayout> all = new ArrayList<>(cells);
        for (int i = 0; i < Brushes.KEYS.length; i++) {
            final String key = Brushes.KEYS[i];
            LinearLayout cell = cells.get(i);
            cell.setPadding(dp(6), dp(6), dp(6), dp(6));
            boolean sel = key.equals(sheet.ink.brush);
            cell.setBackground(Ui.round(sel ? Theme.a(th.accent, 40) : th.panel2, sel ? th.accent : th.border, dp(sel ? 2 : 1), dp(th.round + 2)));
            Views.Thumb t = new Views.Thumb(this);
            t.set(Brushes.preview(key, sheet.ink.color, 3.2f, 1f, sheet.ink.emoji, dp(96), dp(34), page.style.paper));
            cell.addView(t, new LinearLayout.LayoutParams(-1, dp(34)));
            TextView n = Ui.text(this, Brushes.GLYPHS[i] + " " + Brushes.NAMES[i], 10.5f, th.fg, true);
            n.setPadding(0, dp(4), 0, 0);
            cell.addView(n);
            TextView ds = Ui.text(this, Brushes.DESC[i], 8.5f, th.muted, false);
            ds.setMaxLines(2);
            cell.addView(ds);
            cell.setAlpha(0f); cell.setTranslationY(dp(10));
            cell.animate().alpha(1f).translationY(0).setStartDelay(25L * i).setDuration(240).start();
            cell.setOnClickListener(v -> {
                Ui.tick(v);
                sheet.ink.brush = key; pr.put("brush", key);
                for (int j = 0; j < all.size(); j++) {
                    boolean s = Brushes.KEYS[j].equals(key);
                    all.get(j).setBackground(Ui.round(s ? Theme.a(th.accent, 40) : th.panel2, s ? th.accent : th.border, dp(s ? 2 : 1), dp(th.round + 2)));
                }
                refreshBrushUi(); applyQuill();
                if (!"timbro".equals(key)) pn.dismiss();
            });
        }
        pn.section("TIMBRI (pennello ★ TIMBRI)");
        LinearLayout st = pn.strip();
        for (String e : Brushes.STAMPS) {
            TextView t = Ui.text(this, e, 24, th.fg, false);
            t.setPadding(dp(6), dp(2), dp(6), dp(2));
            t.setOnClickListener(v -> {
                Ui.tick(v);
                sheet.ink.emoji = e; sheet.ink.brush = "timbro"; pr.put("stamp", e); pr.put("brush", "timbro");
                refreshBrushUi(); applyQuill(); pn.dismiss();
            });
            st.addView(t);
        }
        pn.show();
    }

    private void pickBrushColor() {
        int old = sheet.ink.color;
        ColorPicker.show(this, th, "COLORE PENNELLO", old, false,
            c -> { sheet.ink.color = c; brushDot.set(c); },
            c -> { sheet.ink.color = c; pr.put("brushColor", c); if ("gomma".equals(sheet.ink.brush)) { sheet.ink.brush = "penna"; pr.put("brush", "penna"); } refreshBrushUi(); applyQuill(); });
    }

    private void centerSymmetry() {
        float u = sheet.u;
        float cy = (scroll.getScrollY() + scroll.getHeight() / 2f - sheet.getTop()) / u;
        sheet.ink.symCx = sheet.getWidth() / u / 2f;
        sheet.ink.symCy = Ui.clamp(cy, 20, sheet.getHeight() / u - 20);
        sheet.ink.invalidate();
    }

    private void symPanel() {
        Panel pn = new Panel(this, th, "SIMMETRIA");
        pn.note("Disegna una volta, il foglio ripete il tratto: specchio, quadranti o caleidoscopio (mandala). Il centro è al centro della parte visibile.");
        pn.chips(Brushes.SYM_NAMES, sheet.ink.sym, i -> {
            sheet.ink.sym = i; pr.put("sym", i);
            centerSymmetry(); refreshBrushUi();
        });
        pn.action("⌖", "CENTRA QUI", "sposta il centro al centro della vista", () -> { centerSymmetry(); pn.dismiss(); });
        pn.show();
    }

    private void toggleLayer() {
        boolean b = !sheet.inkBelow;
        pr.put("inkBelow", b);
        sheet.setInkBelow(b);
        refreshBrushUi();
        toast(b ? "⧉ DISEGNO SOTTO IL TESTO" : "⧉ DISEGNO SOPRA IL TESTO");
    }

    private void clearInk() {
        if (!sheet.ink.canUndo()) { toast("NESSUN DISEGNO DA CANCELLARE"); return; }
        List<Model.Stroke> old = sheet.ink.clearAll();
        Sfx.swish();
        int[] loc = new int[2]; scroll.getLocationOnScreen(loc);
        quill.burst("polvere", loc[0] + scroll.getWidth() / 2f, loc[1] + scroll.getHeight() / 2f, 60, sheet.ink.color);
        toast("⌧ DISEGNO CANCELLATO", "ANNULLA", () -> sheet.ink.restore(old));
    }

    /* ================================================================ export */
    private void exportPanel() {
        Panel pn = new Panel(this, th, "ESPORTA");
        pn.action("▣", "PNG · FOGLIO", "immagine in Immagini/AmpyPad", () -> { pn.dismiss(); h.postDelayed(() -> exportPng(1f, false, false), 230); });
        pn.action("▣", "PNG · ALTA RISOLUZIONE ×2", "per stampe e sfondi", () -> { pn.dismiss(); h.postDelayed(() -> exportPng(2f, false, false), 230); });
        pn.action("✎", "PNG · SOLO DISEGNO", "sfondo trasparente, senza testo né carta", () -> { pn.dismiss(); h.postDelayed(() -> exportPng(1f, true, false), 230); });
        pn.action("⎙", "PDF · FOGLIO", "A4 in Download/AmpyPad, testo vettoriale", () -> { pn.dismiss(); h.postDelayed(() -> exportPdf(false), 230); });
        pn.action("⎘", "PDF · TUTTO IL QUADERNO", "un fascicolo con tutti i fogli", () -> { pn.dismiss(); h.postDelayed(() -> exportPdf(true), 230); });
        pn.action("⇪", "CONDIVIDI COME IMMAGINE", null, () -> { pn.dismiss(); h.postDelayed(() -> exportPng(1f, false, true), 230); });
        pn.section("BACKUP");
        pn.action("⇩", "BACKUP DI TUTTI I QUADERNI (.json)", null, () -> { pn.dismiss(); backup(); });
        pn.action("⇪", "IMPORTA UN BACKUP", "aggiunge i quaderni senza cancellare i tuoi", () -> { pn.dismiss(); importBackup(); });
        pn.show();
    }

    private boolean needsPerm() {
        if (Build.VERSION.SDK_INT >= 29) return false;
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
    }
    private boolean ensurePerm(Runnable r) {
        if (!needsPerm()) return true;
        pendingPerm = r;
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, RC_PERM);
        return false;
    }
    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (rc == RC_PERM && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED && pendingPerm != null) pendingPerm.run();
        pendingPerm = null;
    }

    private boolean prepareExport() {
        ed.syncAll();
        View f = getCurrentFocus(); if (f != null) f.clearFocus();
        ed.hideKeyboard();
        ed.exportMode(true);
        boolean was = sheet.ink.enabled;
        sheet.ink.enabled = false;
        sheet.flat = true;
        layoutSheetNow();
        return was;
    }
    private void finishExport(boolean was) {
        ed.exportMode(false);
        sheet.ink.enabled = was;
        sheet.flat = false;
        sheet.requestLayout(); sheet.invalidate();
    }
    private void layoutSheetNow() {
        sheet.measure(View.MeasureSpec.makeMeasureSpec(sheetW, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        sheet.layout(sheet.getLeft(), sheet.getTop(), sheet.getLeft() + sheet.getMeasuredWidth(), sheet.getTop() + sheet.getMeasuredHeight());
    }

    private void exportPng(float scale, boolean inkOnly, boolean share) {
        if (!ensurePerm(() -> exportPng(scale, inkOnly, share))) return;
        Bitmap b;
        boolean was = prepareExport();
        try {
            if (inkOnly) {
                Bitmap ink = sheet.ink.inkBitmap();
                b = ink;
            } else b = Exporter.render(sheet, scale);
        } catch (OutOfMemoryError e) { b = null; }
        finishExport(was);
        if (b == null) { toast("MEMORIA INSUFFICIENTE"); return; }
        toast("▣ SALVATAGGIO PNG…");
        final Bitmap bmp = b;
        final String name = Exporter.fileName(page.displayTitle() + (inkOnly ? "_disegno" : ""), "png");
        new Thread(() -> {
            try {
                Uri u = Exporter.savePng(this, bmp, name);
                runOnUiThread(() -> {
                    Sfx.chime();
                    if (share) shareUri(u, "image/png");
                    else toast("▣ SALVATO IN IMMAGINI/AMPYPAD", "CONDIVIDI", () -> shareUri(u, "image/png"));
                });
            } catch (Exception e) { runOnUiThread(() -> toast("ERRORE: " + e.getMessage())); }
        }).start();
    }

    private void exportPdf(boolean whole) {
        if (!ensurePerm(() -> exportPdf(whole))) return;
        toast(whole ? "⎘ CREAZIONE DEL FASCICOLO…" : "⎙ CREAZIONE PDF…");
        h.postDelayed(() -> {
            PdfDocument doc = new PdfDocument();
            Model.Page cur = page;
            boolean was = prepareExport();
            int n = 1;
            try {
                if (whole) {
                    for (Model.Page p : data.pagesOf(page.notebookId)) {
                        loadForExport(p);
                        n += Exporter.addToPdf(doc, sheet, n);
                    }
                    loadForExport(cur);
                } else n += Exporter.addToPdf(doc, sheet, n);
            } catch (Exception e) { toast("ERRORE PDF: " + e.getMessage()); }
            finishExport(was);
            if (whole) { page = cur; ed.load(cur); sheet.ink.setPage(cur); sheet.extraUnits = cur.extra; applyStyle(); }
            final int pages = n - 1;
            Model.Notebook nb = data.notebook(page.notebookId);
            final String name = Exporter.fileName(whole && nb != null ? nb.name : page.displayTitle(), "pdf");
            new Thread(() -> {
                try {
                    Uri u = Exporter.savePdf(this, doc, name);
                    doc.close();
                    runOnUiThread(() -> { Sfx.chime(); toast("⎙ PDF " + pages + " PAG. IN DOWNLOAD/AMPYPAD", "APRI", () -> openUri(u, "application/pdf")); });
                } catch (Exception e) { doc.close(); runOnUiThread(() -> toast("ERRORE: " + e.getMessage())); }
            }).start();
        }, 60);
    }

    private void loadForExport(Model.Page p) {
        page = p;
        sheet.extraUnits = p.extra;
        sheet.ink.setPage(p);
        ed.load(p);
        Model.Style s = p.style;
        float u = sheetW / 400f;
        int[] m = SheetView.metricsFor(s, u);
        sheet.style = s;
        sheet.setMetrics(u, m[1], m[2]);
        ed.setMetrics(u, m[0], m[1], m[2], PaperStyle.typeface(s.font), s.ink);
        ed.exportMode(true);
        layoutSheetNow();
        layoutSheetNow(); // second pass: ink layer resized → rebuilt
    }

    private void shareUri(Uri u, String mime) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime); i.putExtra(Intent.EXTRA_STREAM, u); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(i, "Condividi")); } catch (Exception e) { toast("NESSUNA APP PER CONDIVIDERE"); }
    }
    private void openUri(Uri u, String mime) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, mime); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(i); } catch (Exception e) { toast("NESSUNA APP PER APRIRE IL PDF"); }
    }

    private void backup() {
        saveNow();
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, Exporter.fileName("AmpyPad_backup", "json"));
        try { startActivityForResult(i, RC_BACKUP); } catch (Exception e) { toast("IMPOSSIBILE APRIRE IL SELETTORE FILE"); }
    }
    private void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try { startActivityForResult(i, RC_IMPORT); } catch (Exception e) { toast("IMPOSSIBILE APRIRE IL SELETTORE FILE"); }
    }

    @Override protected void onActivityResult(int rc, int res, Intent d) {
        super.onActivityResult(rc, res, d);
        if (res != RESULT_OK || d == null) return;
        if (rc == RC_SPEECH) {
            ArrayList<String> r = d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) { setMode(false, true); ed.insertText(r.get(0) + " "); }
        } else if (rc == RC_BACKUP && d.getData() != null) {
            try (OutputStream o = getContentResolver().openOutputStream(d.getData())) {
                ed.syncAll();
                if (o != null) o.write(data.json().toString(1).getBytes(StandardCharsets.UTF_8));
                toast("⇩ BACKUP SALVATO · " + data.pages.size() + " FOGLI");
            } catch (Exception e) { toast("ERRORE BACKUP: " + e.getMessage()); }
        } else if (rc == RC_IMPORT && d.getData() != null) {
            try (InputStream in = getContentResolver().openInputStream(d.getData())) {
                if (in == null) throw new java.io.IOException("file vuoto");
                Model.Data imp = Model.Data.from(new org.json.JSONObject(Store.readAll(in)));
                int nbs = 0, pgs = 0;
                for (Model.Notebook nb : imp.notebooks) if (data.notebook(nb.id) == null) { data.notebooks.add(nb); nbs++; }
                for (Model.Page p : imp.pages) {
                    if (data.page(p.id) != null) continue;
                    if (data.notebook(p.notebookId) == null) p.notebookId = page.notebookId;
                    data.pages.add(p); pgs++;
                }
                scheduleSave();
                if (drawerOpen) refreshDrawer();
                toast("⇪ IMPORTATI " + nbs + " QUADERNI · " + pgs + " FOGLI");
            } catch (Exception e) { toast("FILE NON VALIDO: " + e.getMessage()); }
        }
    }

    /* ================================================================ misc */
    private void toggleFocus() {
        focusMode = !focusMode;
        int v = focusMode ? View.GONE : View.VISIBLE;
        topBar.setVisibility(v); statsRow.setVisibility(v); bottomRow.setVisibility(v);
        if (focusMode) { formatBar.setVisibility(View.GONE); brushBar.setVisibility(View.GONE); }
        else (brushMode ? brushBar : formatBar).setVisibility(View.VISIBLE);
        focusExit.setVisibility(focusMode ? View.VISIBLE : View.GONE);
        if (focusMode) { focusExit.setAlpha(0f); focusExit.animate().alpha(0.85f).setDuration(400).start(); toast("◱ CONCENTRAZIONE · solo tu e il foglio"); }
        updateBack();
    }

    private void updateBack() {
        if (Build.VERSION.SDK_INT < 33) return;
        boolean need = drawerOpen || focusMode;
        if (need && backCb == null) {
            android.window.OnBackInvokedCallback cb = this::onBackCustom;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
            backCb = cb;
        } else if (!need && backCb != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((android.window.OnBackInvokedCallback) backCb);
            backCb = null;
        }
    }
    private void onBackCustom() { if (drawerOpen) closeDrawer(); else if (focusMode) toggleFocus(); }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (drawerOpen) { closeDrawer(); return; }
        if (focusMode) { toggleFocus(); return; }
        super.onBackPressed();
    }

    private void updateStats() {
        if (page == null || stats == null) return;
        String txt = page.plainText();
        int w = words(txt), ch = txt.replace("\n", "").length();
        int min = Math.max(1, Math.round(w / 200f));
        stats.setText(w + " PAROLE · " + ch + " CAR · ~" + min + " MIN · " + page.strokes.size() + " TRATTI");
        subTitle.setText(page.displayTitle().toUpperCase(Locale.ITALIAN));
    }

    private void toast(String msg) { toast(msg, null, null); }
    private void toast(String msg, String action, Runnable r) {
        h.removeCallbacks(hideToast);
        toastView.setText(action == null ? msg : msg + "   ▸ " + action);
        toastView.setOnClickListener(r == null ? null : v -> { Ui.tick(v); r.run(); hideToast.run(); });
        toastView.setVisibility(View.VISIBLE);
        toastView.animate().cancel();
        toastView.setAlpha(0f); toastView.setTranslationY(dp(14));
        toastView.animate().alpha(1f).translationY(0).setDuration(260).setInterpolator(new OvershootInterpolator(1.3f)).start();
        h.postDelayed(hideToast, action == null ? 2200 : 4200);
    }

    /* ================================================================ listeners */
    private final Editor.Listener editorListener = new Editor.Listener() {
        @Override public void onChanged() {
            scheduleSave();
            h.removeCallbacks(statsRun); h.postDelayed(statsRun, 350);
        }
        @Override public void onTyping(float x, float y) { quill.write(x, y); Sfx.scratch(); }
        @Override public void onSlash(Editor.Row r) { blockMenu(r, 1); }
        @Override public void onRowMenu(Editor.Row r) { rowMenu(r); }
        @Override public void onTodo(Editor.Row r, boolean checked, float x, float y) {
            if (checked) { quill.burst(effect(), x, y, 22, quillColor()); Sfx.chime(); } else Sfx.pop();
        }
        @Override public void onFocusRow(Editor.Row r) { updateBlockBtn(r); }
    };

    private final InkView.Listener inkListener = new InkView.Listener() {
        @Override public void onInk(float x, float y) { quill.write(x, y); Sfx.scratch(); }
        @Override public void onInkDone() { }
        @Override public void onStrokesChanged() { scheduleSave(); refreshUndo(); updateStats(); sheet.requestLayout(); }
        @Override public void onScrollBy(float dy) { scroll.scrollBy(0, Math.round(dy)); }
        @Override public void onStraightened() { toast("— LINEA DRITTA"); }
    };
}
