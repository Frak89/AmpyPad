package it.ampy.ampypad;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Block editor: title, date and a column of organisation blocks (text, headings, lists, to-dos, callouts…). */
public final class Editor implements BlockEdit.Host {
    public interface Listener {
        void onChanged();
        void onTyping(float rawX, float rawY);
        void onSlash(Row r);
        void onRowMenu(Row r);
        void onTodo(Row r, boolean checked, float rawX, float rawY);
        void onFocusRow(Row r);
    }

    public static final String[] TYPES = {"p", "h1", "h2", "h3", "bullet", "num", "todo", "quote", "callout", "code", "divider"};
    public static final String[] TYPE_NAMES = {"TESTO", "TITOLO", "SOTTOTITOLO", "INTESTAZIONE", "ELENCO PUNTATO", "ELENCO NUMERATO",
        "COSE DA FARE", "CITAZIONE", "RIQUADRO", "CODICE", "SEPARATORE"};
    public static final String[] TYPE_GLYPHS = {"¶", "H1", "H2", "H3", "•", "1.", "☑", "❝", "✱", "{}", "—"};
    public static final String[] TYPE_HINTS = {"paragrafo", "# spazio", "## spazio", "### spazio", "- spazio", "1. spazio",
        "[] spazio", "> spazio", "! spazio", "``` spazio", "---"};
    public static final String[] COLORS = {"", "yellow", "green", "blue", "pink", "purple", "orange", "gray"};
    public static final int[] COLOR_TINTS = {0, 0x47ffd23c, 0x3a50c878, 0x355096ff, 0x35ff6eaa, 0x35a06eff, 0x3dff963c, 0x2a7f7f7f};
    public static final int[] COLOR_SOLID = {0x00000000, 0xffffd23c, 0xff50c878, 0xff5096ff, 0xffff6eaa, 0xffa06eff, 0xffff963c, 0xff9a9a9a};
    public static final String[] COLOR_NAMES = {"NESSUNO", "GIALLO", "VERDE", "BLU", "ROSA", "VIOLA", "ARANCIO", "GRIGIO"};
    public static final String[] EMOJIS ={"💡", "📌", "⚠️", "✅", "❤️", "⭐", "🔥", "📎", "🧠", "🎯", "🚀", "📅"};
    private static final String[][] MD = {{"### ", "h3"}, {"## ", "h2"}, {"# ", "h1"}, {"- ", "bullet"}, {"* ", "bullet"}, {"• ", "bullet"},
        {"1. ", "num"}, {"1) ", "num"}, {"[] ", "todo"}, {"[ ] ", "todo"}, {"> ", "quote"}, {"! ", "callout"}, {"``` ", "code"}};

    public static int typeIndex(String t) { for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(t)) return i; return 0; }

    private final Context ctx;
    private final Listener l;
    public final LinearLayout content, box;
    public final BlockEdit title;
    public final TextView date;
    private final View tail;
    public final List<Row> rows = new ArrayList<>();
    public Model.Page page;
    public Row focused;
    public float u = 2.6f, fs = 46;
    public int lh = 78, base = 44, ink = 0xff000000, selColor = 0x5500ffe2;
    public Typeface tf = Typeface.DEFAULT;
    public boolean textMode = true;
    private final float[] tmp = new float[2];

    /* ================================================================ row */
    public final class Row extends LinearLayout {
        public final Model.Block b;
        final BlockViews.Handle handle;
        final BlockViews.Marker marker;
        public BlockEdit edit;
        BlockViews.Divider divider;

        Row(Model.Block block) {
            super(ctx);
            b = block;
            setOrientation(HORIZONTAL);
            handle = new BlockViews.Handle(ctx);
            addView(handle, new LayoutParams(Math.round(12 * u), LayoutParams.MATCH_PARENT));
            marker = new BlockViews.Marker(ctx);
            addView(marker, new LayoutParams(0, LayoutParams.MATCH_PARENT));
            if ("divider".equals(b.type)) {
                divider = new BlockViews.Divider(ctx);
                addView(divider, new LayoutParams(0, lh, 1f));
                divider.setOnClickListener(v -> l.onRowMenu(this));
            } else {
                edit = new BlockEdit(ctx);
                edit.host = Editor.this; edit.row = this;
                addView(edit, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
                edit.setQuiet(build(b.text, b.spans));
            }
            marker.setOnClickListener(v -> onMarker(this));
            handle.setOnTouchListener(new DragTouch(this));
        }
    }

    public Editor(Context c, Listener l) {
        ctx = c; this.l = l;
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        title = new BlockEdit(c);
        title.host = this;
        title.setHint("Senza titolo");
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        date = new TextView(c);
        date.setTypeface(Ui.MONO_BOLD); date.setLetterSpacing(0.14f); date.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(date, new LinearLayout.LayoutParams(-1, lh));
        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        content.addView(box, new LinearLayout.LayoutParams(-1, -2));
        tail = new View(c);
        tail.setOnClickListener(v -> focusEnd());
        content.addView(tail, new LinearLayout.LayoutParams(-1, lh * 6));
    }

    /* ================================================================ load / metrics */
    public void load(Model.Page p) {
        page = p;
        title.setQuiet(p.title);
        SimpleDateFormat f = new SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN);
        date.setText(f.format(new Date(p.created)).toUpperCase(Locale.ITALIAN));
        box.removeAllViews(); rows.clear(); focused = null;
        if (p.blocks.isEmpty()) p.blocks.add(new Model.Block());
        for (Model.Block b : p.blocks) addRow(rows.size(), b, false);
        renumber();
    }

    public void setMetrics(float u, float fs, int lh, int base, Typeface tf, int ink) {
        this.u = u; this.fs = fs; this.lh = lh; this.base = base; this.tf = tf; this.ink = ink;
        content.setPadding(Math.round(14 * u), Math.round(16 * u), Math.round(14 * u), Math.round(10 * u));
        int hw = Math.round(12 * u);
        LinearLayout.LayoutParams tl = (LinearLayout.LayoutParams) title.getLayoutParams();
        tl.leftMargin = hw; title.setLayoutParams(tl);
        title.setTypeface(Typeface.create(tf, Typeface.BOLD));
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, fs * 1.6f);
        title.applyLineHeight(lh * 2);
        title.minRows = 1;
        title.shift = lh + base + title.getPaint().getFontMetricsInt().ascent;
        title.setTextColor(ink); title.setHintTextColor(Theme.a(ink, 70)); title.setHighlightColor(selColor);
        LinearLayout.LayoutParams dl = (LinearLayout.LayoutParams) date.getLayoutParams();
        dl.height = lh; dl.leftMargin = hw; date.setLayoutParams(dl);
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, fs * 0.42f);
        date.setTextColor(Theme.a(ink, 120));
        LinearLayout.LayoutParams ta = (LinearLayout.LayoutParams) tail.getLayoutParams();
        ta.height = lh * 6; tail.setLayoutParams(ta);
        for (Row r : rows) restyle(r);
    }

    public void setTextMode(boolean on) {
        textMode = on;
        for (Row r : rows) r.handle.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        if (!on) {
            View f = content.findFocus();
            if (f != null) f.clearFocus();
            hideKeyboard();
        }
    }
    public void setHandlesVisible(boolean v) { for (Row r : rows) r.handle.setVisibility(v && textMode ? View.VISIBLE : View.INVISIBLE); }

    /** Hides handles, hints and cursors so the sheet can be drawn into PNG / PDF. */
    public void exportMode(boolean on) {
        setHandlesVisible(!on);
        title.setHint(on ? "" : "Senza titolo");
        title.setCursorVisible(!on);
        for (Row r : rows) if (r.edit != null) { r.edit.setHint(on ? "" : hint(r.b.type, r.edit.hasFocus())); r.edit.setCursorVisible(!on); }
    }

    public void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(content.getWindowToken(), 0);
    }

    /* ================================================================ rows */
    private Row addRow(int idx, Model.Block b, boolean anim) {
        Row r = new Row(b);
        rows.add(idx, r);
        box.addView(r, idx, new LinearLayout.LayoutParams(-1, -2));
        restyle(r);
        if (anim) {
            r.setAlpha(0f); r.setTranslationY(-lh * 0.4f);
            r.animate().alpha(1f).translationY(0).setDuration(260).start();
        }
        return r;
    }

    private Row insertAt(int i, Model.Block nb) {
        page.blocks.add(Math.min(i, page.blocks.size()), nb);
        return addRow(i, nb, true);
    }
    private Row insertAfter(Row r, Model.Block nb) { return insertAt(rows.indexOf(r) + 1, nb); }

    private Row replaceRow(Row r) {
        int i = rows.indexOf(r);
        rows.remove(i); box.removeView(r);
        return addRow(i, r.b, false);
    }

    private void removeRow(Row r) {
        int i = rows.indexOf(r);
        if (i < 0) return;
        rows.remove(i); box.removeView(r); page.blocks.remove(r.b);
        if (rows.isEmpty()) { Model.Block nb = new Model.Block(); page.blocks.add(nb); addRow(0, nb, true); }
    }

    private int tint(Model.Block b) {
        int ci = 0;
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i].equals(b.color)) ci = i;
        if (ci > 0) return COLOR_TINTS[ci];
        if ("callout".equals(b.type)) return 0x33d4a24c;
        if ("code".equals(b.type)) return 0x22808080;
        return 0;
    }

    public void restyle(Row r) {
        Model.Block b = r.b;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) r.getLayoutParams();
        if (lp != null) { lp.leftMargin = Math.round(b.indent * 12 * u); r.setLayoutParams(lp); }
        int t = tint(b);
        r.setBackground(t != 0 ? Ui.round(t, 0, 0, u * 2.5f) : null);
        r.setPadding(0, 0, t != 0 ? Math.round(u * 2) : 0, 0);
        LinearLayout.LayoutParams hl = (LinearLayout.LayoutParams) r.handle.getLayoutParams();
        hl.width = Math.round(12 * u); r.handle.setLayoutParams(hl);
        r.handle.lh = "h1".equals(b.type) ? lh * 2 : lh; r.handle.color = ink;
        r.handle.setVisibility(textMode ? View.VISIBLE : View.INVISIBLE);
        float mw;
        switch (b.type) {
            case "bullet": mw = 10; break;
            case "num": mw = 14; break;
            case "todo": case "callout": mw = 13; break;
            case "quote": mw = 7; break;
            default: mw = 0;
        }
        LinearLayout.LayoutParams ml = (LinearLayout.LayoutParams) r.marker.getLayoutParams();
        ml.width = Math.round(mw * u); r.marker.setLayoutParams(ml);
        r.marker.setVisibility(mw > 0 ? View.VISIBLE : View.GONE);
        r.marker.type = b.type; r.marker.emoji = b.emoji; r.marker.indent = b.indent; r.marker.lh = lh; r.marker.base = base;
        r.marker.ink = ink; r.marker.fs = fs; r.marker.tf = tf; r.marker.checked = b.checked;
        r.marker.invalidate();
        if (r.divider != null) {
            r.divider.ink = ink;
            LinearLayout.LayoutParams dl = (LinearLayout.LayoutParams) r.divider.getLayoutParams();
            dl.height = lh; r.divider.setLayoutParams(dl); r.divider.invalidate();
        }
        BlockEdit e = r.edit;
        if (e == null) return;
        float size = fs; Typeface face = tf; int rowH = lh, target = base;
        switch (b.type) {
            case "h1": size = fs * 1.7f; face = Typeface.create(tf, Typeface.BOLD); rowH = lh * 2; target = lh + base; break;
            case "h2": size = fs * 1.32f; face = Typeface.create(tf, Typeface.BOLD); break;
            case "h3": size = fs * 1.12f; face = Typeface.create(tf, Typeface.BOLD); break;
            case "quote": face = Typeface.create(tf, Typeface.ITALIC); break;
            case "code": size = fs * 0.8f; face = Typeface.MONOSPACE; break;
            default: break;
        }
        e.setTypeface(face);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        e.applyLineHeight(rowH);
        e.shift = target + e.getPaint().getFontMetricsInt().ascent;
        e.multiline = "code".equals(b.type);
        e.setTextColor(ink); e.setHintTextColor(Theme.a(ink, 80)); e.setHighlightColor(selColor);
        int g = b.align == 1 ? Gravity.CENTER_HORIZONTAL : b.align == 2 ? Gravity.END : Gravity.START;
        e.setGravity(Gravity.TOP | g);
        boolean done = "todo".equals(b.type) && b.checked;
        e.setAlpha(done ? 0.5f : 1f);
        e.setPaintFlags(done ? e.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG : e.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        e.setHint(hint(b.type, e.hasFocus()));
        e.requestLayout();
    }

    private static String hint(String type, boolean focus) {
        switch (type) {
            case "h1": return "Titolo";
            case "h2": return "Sottotitolo";
            case "h3": return "Intestazione";
            case "todo": return "Cosa c'è da fare?";
            case "quote": return "Citazione";
            case "callout": return "Nota importante";
            case "code": return "codice";
            case "bullet": case "num": return focus ? "Elenco" : "";
            default: return focus ? "Scrivi… oppure / per i blocchi" : "";
        }
    }

    public void renumber() {
        int n = 0;
        for (Row r : rows) {
            if ("num".equals(r.b.type)) { n++; r.marker.number = n; r.marker.invalidate(); } else n = 0;
        }
    }

    /* ================================================================ rich text */
    static CharSequence build(String text, List<Model.SpanRec> spans) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        int len = text.length();
        for (Model.SpanRec s : spans) {
            int a = Math.max(0, Math.min(len, s.start)), z = Math.max(0, Math.min(len, s.end));
            if (z <= a) continue;
            Object o = make(s.type, s.value);
            if (o != null) sb.setSpan(o, a, z, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }
    static Object make(String type, int v) {
        switch (type) {
            case "b": return new StyleSpan(Typeface.BOLD);
            case "i": return new StyleSpan(Typeface.ITALIC);
            case "u": return new UnderlineSpan();
            case "s": return new StrikethroughSpan();
            case "fg": return new ForegroundColorSpan(v);
            case "bg": return new BackgroundColorSpan(v);
            default: return null;
        }
    }
    static List<Model.SpanRec> extract(Spanned ed, int from, int to) {
        List<Model.SpanRec> out = new ArrayList<>();
        for (Object o : ed.getSpans(from, to, Object.class)) {
            if ((ed.getSpanFlags(o) & Spanned.SPAN_COMPOSING) != 0) continue;
            int a = Math.max(from, ed.getSpanStart(o)), z = Math.min(to, ed.getSpanEnd(o));
            if (z <= a) continue;
            a -= from; z -= from;
            if (o instanceof StyleSpan) {
                int st = ((StyleSpan) o).getStyle();
                if (st == Typeface.BOLD || st == Typeface.BOLD_ITALIC) out.add(new Model.SpanRec("b", a, z, 0));
                if (st == Typeface.ITALIC || st == Typeface.BOLD_ITALIC) out.add(new Model.SpanRec("i", a, z, 0));
            } else if (o.getClass() == UnderlineSpan.class) out.add(new Model.SpanRec("u", a, z, 0));
            else if (o instanceof StrikethroughSpan) out.add(new Model.SpanRec("s", a, z, 0));
            else if (o instanceof ForegroundColorSpan) out.add(new Model.SpanRec("fg", a, z, ((ForegroundColorSpan) o).getForegroundColor()));
            else if (o instanceof BackgroundColorSpan) out.add(new Model.SpanRec("bg", a, z, ((BackgroundColorSpan) o).getBackgroundColor()));
        }
        return out;
    }
    private static CharSequence slice(Editable ed, int a, int z) {
        return build(ed.subSequence(a, z).toString(), extract(ed, a, z));
    }
    private static boolean matches(Object o, String type) {
        switch (type) {
            case "b": return o instanceof StyleSpan && (((StyleSpan) o).getStyle() & Typeface.BOLD) != 0;
            case "i": return o instanceof StyleSpan && (((StyleSpan) o).getStyle() & Typeface.ITALIC) != 0;
            case "u": return o.getClass() == UnderlineSpan.class;
            case "s": return o instanceof StrikethroughSpan;
            case "fg": return o instanceof ForegroundColorSpan;
            case "bg": return o instanceof BackgroundColorSpan;
            default: return false;
        }
    }
    private static Object same(Object o) {
        if (o instanceof StyleSpan) return new StyleSpan(((StyleSpan) o).getStyle());
        if (o instanceof ForegroundColorSpan) return new ForegroundColorSpan(((ForegroundColorSpan) o).getForegroundColor());
        if (o instanceof BackgroundColorSpan) return new BackgroundColorSpan(((BackgroundColorSpan) o).getBackgroundColor());
        if (o instanceof StrikethroughSpan) return new StrikethroughSpan();
        return new UnderlineSpan();
    }

    public void sync(Row r) {
        if (r == null || r.edit == null) return;
        Editable ed = r.edit.getText();
        r.b.text = ed.toString();
        r.b.spans = extract(ed, 0, ed.length());
    }
    public void syncAll() {
        if (page == null) return;
        page.title = title.getText().toString();
        for (Row r : rows) sync(r);
    }

    private BlockEdit target() { return focused != null && focused.edit != null && focused.edit.hasFocus() ? focused.edit : (focused != null ? focused.edit : null); }
    public boolean hasSelection() { BlockEdit e = target(); return e != null && e.getSelectionStart() != e.getSelectionEnd(); }

    /** Bold / italic / underline / strike toggle on the selection (whole block if nothing is selected). */
    public boolean toggle(String type) {
        BlockEdit e = target();
        if (e == null) return false;
        int a = Math.min(e.getSelectionStart(), e.getSelectionEnd()), z = Math.max(e.getSelectionStart(), e.getSelectionEnd());
        if (a == z) { a = 0; z = e.length(); }
        if (a == z || a < 0) return false;
        Editable ed = e.getText();
        boolean all = true;
        for (int i = a; i < z && all; i++) {
            boolean hit = false;
            for (Object o : ed.getSpans(i, i + 1, Object.class)) if (matches(o, type) && ed.getSpanStart(o) <= i && ed.getSpanEnd(o) > i) { hit = true; break; }
            all = hit;
        }
        clearRange(ed, a, z, type);
        if (!all) ed.setSpan(make(type, 0), a, z, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        sync(focused); l.onChanged();
        return true;
    }
    /** Text colour ("fg") or highlight ("bg"); value 0 removes it. */
    public boolean color(String type, int value) {
        BlockEdit e = target();
        if (e == null) return false;
        int a = Math.min(e.getSelectionStart(), e.getSelectionEnd()), z = Math.max(e.getSelectionStart(), e.getSelectionEnd());
        if (a == z) { a = 0; z = e.length(); }
        if (a == z || a < 0) return false;
        Editable ed = e.getText();
        clearRange(ed, a, z, type);
        if (value != 0) ed.setSpan(make(type, value), a, z, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        sync(focused); l.onChanged();
        return true;
    }
    public boolean clearFormatting() {
        BlockEdit e = target();
        if (e == null) return false;
        int a = Math.min(e.getSelectionStart(), e.getSelectionEnd()), z = Math.max(e.getSelectionStart(), e.getSelectionEnd());
        if (a == z) { a = 0; z = e.length(); }
        Editable ed = e.getText();
        for (String t : new String[]{"b", "i", "u", "s", "fg", "bg"}) clearRange(ed, a, z, t);
        sync(focused); l.onChanged();
        return true;
    }
    private static void clearRange(Editable ed, int a, int z, String type) {
        for (Object o : ed.getSpans(a, z, Object.class)) {
            if (!matches(o, type)) continue;
            int st = ed.getSpanStart(o), en = ed.getSpanEnd(o);
            if (en <= a || st >= z) continue;
            ed.removeSpan(o);
            if (st < a) ed.setSpan(same(o), st, a, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (en > z) ed.setSpan(same(o), z, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public void insertText(String s) {
        BlockEdit e = target();
        if (e == null) { focusEnd(); e = target(); }
        if (e == null) return;
        int a = Math.max(0, e.getSelectionStart());
        e.getText().insert(a, s);
    }

    /* ================================================================ structure ops */
    private static boolean isList(String t) { return "bullet".equals(t) || "num".equals(t) || "todo".equals(t) || "quote".equals(t) || "callout".equals(t); }
    private static String continueType(String t) { return "bullet".equals(t) || "num".equals(t) || "todo".equals(t) ? t : "p"; }

    public void setType(Row r, String type) {
        sync(r);
        boolean wasDiv = r.edit == null, isDiv = "divider".equals(type);
        r.b.type = type;
        if (isDiv) { r.b.text = ""; r.b.spans.clear(); }
        if (wasDiv != isDiv) {
            Row nr = replaceRow(r);
            if (isDiv) {
                int i = rows.indexOf(nr);
                Row next = i + 1 < rows.size() ? rows.get(i + 1) : null;
                if (next == null || next.edit == null) next = insertAfter(nr, new Model.Block());
                focus(next, 0);
            } else focus(nr, 0);
        } else {
            restyle(r);
            if (r.edit != null) { r.edit.requestFocus(); r.edit.setSelection(r.edit.length()); }
        }
        renumber();
        l.onChanged();
    }

    public void focus(Row r, int pos) {
        if (r == null || r.edit == null) return;
        r.edit.requestFocus();
        r.edit.setSelection(Math.max(0, Math.min(pos, r.edit.length())));
        InputMethodManager im = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.showSoftInput(r.edit, 0);
    }

    public void focusEnd() {
        if (!textMode) return;
        Row last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        if (last == null || last.edit == null || last.edit.length() > 0 || !"p".equals(last.b.type)) last = insertAt(rows.size(), new Model.Block());
        focus(last, last.edit.length());
        l.onChanged();
    }

    /** Adds a new block after the focused one (or at the end) and returns it. */
    public Row addBlock(String type) {
        Model.Block nb = new Model.Block(type, "");
        Row r;
        if (focused != null && rows.contains(focused)) {
            sync(focused);
            if (focused.edit != null && focused.edit.length() == 0 && !"divider".equals(type)) { setType(focused, type); return focused; }
            nb.indent = focused.b.indent;
            r = insertAfter(focused, nb);
        } else r = insertAt(rows.size(), nb);
        if (r.edit != null) focus(r, 0);
        else { Row p = insertAfter(r, new Model.Block()); focus(p, 0); }
        renumber(); l.onChanged();
        return r;
    }

    public void applySlash(Row r, String type) {
        if (r.edit != null) {
            String t = r.edit.getText().toString();
            if (t.startsWith("/")) r.edit.deleteQuiet(0, 1);
        }
        setType(r, type);
    }

    public void move(Row r, int d) {
        int i = rows.indexOf(r), j = i + d;
        if (i < 0 || j < 0 || j >= rows.size()) return;
        sync(r);
        Row other = rows.get(j);
        // move the neighbour, never the row that may own the focus / touch
        box.removeView(other); box.addView(other, i);
        rows.remove(j); rows.add(i, other);
        page.blocks.remove(other.b); page.blocks.add(i, other.b);
        renumber(); l.onChanged();
    }
    public void duplicate(Row r) { sync(r); Model.Block c = r.b.copy(); insertAfter(r, c); renumber(); l.onChanged(); }
    public void delete(Row r) {
        int i = rows.indexOf(r);
        removeRow(r);
        renumber(); l.onChanged();
        if (!rows.isEmpty()) { Row f = rows.get(Math.max(0, Math.min(i - 1, rows.size() - 1))); if (f.edit != null && textMode) focus(f, f.edit.length()); }
    }
    public void indent(Row r, int d) { r.b.indent = Math.max(0, Math.min(6, r.b.indent + d)); restyle(r); l.onChanged(); }
    public void setColor(Row r, String c) { r.b.color = c; restyle(r); l.onChanged(); }
    public void setAlign(Row r, int a) { r.b.align = a; restyle(r); l.onChanged(); }

    private void onMarker(Row r) {
        Ui.tick(r.marker);
        if ("todo".equals(r.b.type)) {
            r.b.checked = !r.b.checked;
            r.marker.checkAnim = System.currentTimeMillis();
            restyle(r);
            int[] loc = new int[2]; r.marker.getLocationOnScreen(loc);
            l.onTodo(r, r.b.checked, loc[0] + r.marker.getWidth() / 2f, loc[1] + lh / 2f);
            l.onChanged();
        } else if ("callout".equals(r.b.type)) {
            int i = 0; for (int k = 0; k < EMOJIS.length; k++) if (EMOJIS[k].equals(r.b.emoji)) i = k;
            r.b.emoji = EMOJIS[(i + 1) % EMOJIS.length];
            restyle(r); l.onChanged();
        } else l.onRowMenu(r);
    }

    /* ================================================================ BlockEdit.Host */
    @Override public void onSplit(BlockEdit e, int at) {
        if (e == title) {
            String t = title.getText().toString().replace('\n', ' ');
            title.setQuiet(t); page.title = t;
            if (!rows.isEmpty()) focus(rows.get(0), 0);
            l.onChanged();
            return;
        }
        Row r = (Row) e.row;
        Editable ed = e.getText();
        if (at == 0 && ed.length() > 0) {
            Model.Block nb = new Model.Block(continueType(r.b.type), ""); nb.indent = r.b.indent;
            insertAt(rows.indexOf(r), nb);
            focus(r, 0); renumber(); l.onChanged();
            return;
        }
        if (ed.length() == 0 && isList(r.b.type)) { setType(r, "p"); return; }
        CharSequence rest = slice(ed, at, ed.length());
        e.deleteQuiet(at, ed.length());
        sync(r);
        Model.Block nb = new Model.Block(continueType(r.b.type), ""); nb.indent = r.b.indent;
        Row nr = insertAfter(r, nb);
        nr.edit.setQuiet(rest);
        sync(nr);
        int nl = rest.toString().indexOf('\n');
        if (nl >= 0) { nr.edit.deleteQuiet(nl, nl + 1); onSplit(nr.edit, nl); return; }
        focus(nr, 0);
        renumber(); l.onChanged();
        typing(nr.edit);
    }

    @Override public void onBackAtStart(BlockEdit e) {
        if (e == title) return;
        Row r = (Row) e.row;
        Model.Block b = r.b;
        if (!"p".equals(b.type)) { setType(r, "p"); return; }
        if (b.indent > 0) { indent(r, -1); return; }
        int i = rows.indexOf(r);
        if (i <= 0) {
            if (e.length() == 0) { title.requestFocus(); title.setSelection(title.length()); }
            return;
        }
        Row prev = rows.get(i - 1);
        if (prev.edit == null) { removeRow(prev); renumber(); l.onChanged(); return; }
        Editable pe = prev.edit.getText();
        int len = pe.length();
        CharSequence mine = slice(e.getText(), 0, e.length());
        prev.edit.requestFocus();
        prev.edit.getText().append(mine);
        prev.edit.setSelection(len);
        sync(prev);
        removeRow(r);
        renumber(); l.onChanged();
    }

    @Override public void onDeleteAtEnd(BlockEdit e) {
        if (e == title) return;
        Row r = (Row) e.row;
        int i = rows.indexOf(r);
        if (i < 0 || i + 1 >= rows.size()) return;
        Row next = rows.get(i + 1);
        if (next.edit == null) { removeRow(next); l.onChanged(); return; }
        int len = e.length();
        e.getText().append(slice(next.edit.getText(), 0, next.edit.length()));
        e.setSelection(len);
        sync(r); removeRow(next); renumber(); l.onChanged();
    }

    @Override public void onEdited(BlockEdit e) {
        if (e == title) { page.title = title.getText().toString(); l.onChanged(); typing(e); return; }
        Row r = (Row) e.row;
        String t = e.getText().toString();
        if ("p".equals(r.b.type)) {
            if (t.equals("/")) { sync(r); l.onSlash(r); }
            else if (t.equals("---") || t.equals("___") || t.equals("***")) { e.setQuiet(""); setType(r, "divider"); return; }
            else for (String[] m : MD) if (t.startsWith(m[0])) { e.deleteQuiet(0, m[0].length()); setType(r, m[1]); return; }
        }
        if ("code".equals(r.b.type) && t.endsWith("\n\n") && e.getSelectionEnd() == t.length()) {
            e.deleteQuiet(t.length() - 2, t.length());
            sync(r);
            Row nr = insertAfter(r, new Model.Block());
            focus(nr, 0); l.onChanged();
            return;
        }
        sync(r);
        l.onChanged();
        typing(e);
    }

    private void typing(BlockEdit e) { if (e != null && e.caretOnScreen(tmp)) l.onTyping(tmp[0], tmp[1]); }

    @Override public void onFocused(BlockEdit e, boolean f) {
        if (e == title) { if (f) focused = null; return; }
        Row r = (Row) e.row;
        if (f) { focused = r; l.onFocusRow(r); }
        e.setHint(hint(r.b.type, f));
    }

    /* ================================================================ drag & drop */
    private final class DragTouch implements View.OnTouchListener {
        final Row row; float downRaw, accum; boolean dragging, moved; final int slop;
        final Runnable start;
        DragTouch(Row r) {
            row = r; slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
            start = () -> {
                dragging = true; accum = 0;
                if (row.handle.getParent() != null) row.handle.getParent().requestDisallowInterceptTouchEvent(true);
                Ui.clock(row);
                row.handle.lifted = true; row.handle.invalidate();
                row.animate().scaleX(1.02f).scaleY(1.02f).alpha(0.85f).setDuration(140).start();
                row.setElevation(u * 4);
            };
        }
        @Override public boolean onTouch(View v, MotionEvent e) {
            if (!textMode) return false;
            float raw = e.getRawY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRaw = raw; dragging = false; moved = false;
                    v.postDelayed(start, 320);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging) { if (Math.abs(raw - downRaw) > slop) { moved = true; v.removeCallbacks(start); } return true; }
                    float dy = raw - downRaw - accum;
                    int i = rows.indexOf(row);
                    if (dy > 0 && i < rows.size() - 1) {
                        Row nx = rows.get(i + 1);
                        if (dy > nx.getHeight() / 2f) { move(row, 1); accum += nx.getHeight(); dy = raw - downRaw - accum; Ui.clock(row); }
                    } else if (dy < 0 && i > 0) {
                        Row pv = rows.get(i - 1);
                        if (-dy > pv.getHeight() / 2f) { move(row, -1); accum -= pv.getHeight(); dy = raw - downRaw - accum; Ui.clock(row); }
                    }
                    row.setTranslationY(dy);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(start);
                    if (dragging) {
                        dragging = false;
                        row.handle.lifted = false; row.handle.invalidate();
                        row.animate().translationY(0).scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).start();
                        row.setElevation(0);
                        l.onChanged();
                    } else if (e.getActionMasked() == MotionEvent.ACTION_UP && !moved) l.onRowMenu(row);
                    return true;
                default: return true;
            }
        }
    }
}
