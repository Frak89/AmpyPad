package it.ampy.ampypad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

/**
 * Text field of a block. Rows snap to the sheet's line height, the baseline is shifted onto
 * the ruled line, and Enter / Backspace-at-start are turned into block split / merge.
 */
public class BlockEdit extends EditText {
    public interface Host {
        void onSplit(BlockEdit e, int at);
        void onBackAtStart(BlockEdit e);
        void onDeleteAtEnd(BlockEdit e);
        void onEdited(BlockEdit e);
        void onFocused(BlockEdit e, boolean focus);
    }

    public Host host;
    public Object row;          // Editor.Row (or null for the title)
    public boolean multiline;   // code blocks keep their newlines
    public int lineH;           // px, height of one row
    public int minRows = 1;
    public float shift;         // px, baseline correction
    private boolean muting;
    private int pendingNl = -1;

    public BlockEdit(Context c) {
        super(c);
        setBackground(null);
        setPadding(0, 0, 0, 0);
        setIncludeFontPadding(false);
        setGravity(Gravity.TOP | Gravity.START);
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        setHorizontallyScrolling(false);
        if (Build.VERSION.SDK_INT >= 28) setFallbackLineSpacing(false);
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int cnt, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (muting || multiline || count <= 0) return;
                for (int i = start; i < start + count && i < s.length(); i++) if (s.charAt(i) == '\n') { pendingNl = i; break; }
            }
            @Override public void afterTextChanged(Editable s) {
                if (muting || host == null) return;
                if (pendingNl >= 0) {
                    int at = pendingNl; pendingNl = -1;
                    if (at < s.length() && s.charAt(at) == '\n') {
                        muting = true; s.delete(at, at + 1); muting = false;
                        host.onSplit(BlockEdit.this, at);
                        return;
                    }
                }
                host.onEdited(BlockEdit.this);
            }
        });
        setOnFocusChangeListener((v, f) -> { if (host != null) host.onFocused(this, f); });
    }

    /** Replaces the text without firing split / edit callbacks. */
    public void setQuiet(CharSequence t) { muting = true; setText(t); muting = false; }
    public void deleteQuiet(int a, int b) { muting = true; getText().delete(a, b); muting = false; }
    public boolean quiet() { return muting; }

    public void applyLineHeight(int px) {
        lineH = px;
        if (Build.VERSION.SDK_INT >= 28) setLineHeight(px);
        else {
            Paint.FontMetricsInt fm = getPaint().getFontMetricsInt();
            setLineSpacing(px - (fm.descent - fm.ascent), 1f);
        }
    }

    boolean atStart() { return getSelectionStart() == 0 && getSelectionEnd() == 0; }
    boolean atEnd() { int l = length(); return getSelectionStart() == l && getSelectionEnd() == l; }

    @Override public InputConnection onCreateInputConnection(EditorInfo out) {
        InputConnection ic = super.onCreateInputConnection(out);
        if (ic == null) return null;
        return new InputConnectionWrapper(ic, true) {
            @Override public boolean deleteSurroundingText(int before, int after) {
                if (before == 1 && after == 0 && atStart() && host != null) { host.onBackAtStart(BlockEdit.this); return true; }
                return super.deleteSurroundingText(before, after);
            }
            @Override public boolean sendKeyEvent(KeyEvent ev) {
                if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getKeyCode() == KeyEvent.KEYCODE_DEL && atStart() && host != null) {
                    host.onBackAtStart(BlockEdit.this); return true;
                }
                return super.sendKeyEvent(ev);
            }
        };
    }

    @Override public boolean onKeyDown(int code, KeyEvent ev) {
        if (host != null) {
            if (code == KeyEvent.KEYCODE_DEL && atStart()) { host.onBackAtStart(this); return true; }
            if (code == KeyEvent.KEYCODE_FORWARD_DEL && atEnd()) { host.onDeleteAtEnd(this); return true; }
        }
        return super.onKeyDown(code, ev);
    }

    @Override protected void onMeasure(int w, int h) {
        super.onMeasure(w, h);
        if (lineH > 0) {
            int mh = getMeasuredHeight();
            int rows = Math.max(minRows, (int) Math.ceil((mh - 2) / (float) lineH));
            setMeasuredDimension(getMeasuredWidth(), rows * lineH);
        }
    }

    @Override protected void onDraw(Canvas c) {
        if (shift != 0) { c.save(); c.translate(0, shift); super.onDraw(c); c.restore(); }
        else super.onDraw(c);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (shift == 0) return super.onTouchEvent(e);
        e.offsetLocation(0, -shift);
        boolean r = super.onTouchEvent(e);
        e.offsetLocation(0, shift);
        return r;
    }

    /** Caret position in screen coordinates (tip of the quill). Returns false if unknown. */
    public boolean caretOnScreen(float[] out) {
        android.text.Layout lay = getLayout();
        if (lay == null) return false;
        int off = Math.max(0, getSelectionEnd());
        int line = lay.getLineForOffset(off);
        float x = lay.getPrimaryHorizontal(off) + getTotalPaddingLeft() - getScrollX();
        float y = lay.getLineBaseline(line) + getTotalPaddingTop() - getScrollY() + shift;
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        out[0] = loc[0] + x; out[1] = loc[1] + y;
        return true;
    }
}
