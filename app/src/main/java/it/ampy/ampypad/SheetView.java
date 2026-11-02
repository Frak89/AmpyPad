package it.ampy.ampypad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

/**
 * One sheet of paper: paints pattern + texture, hosts the block editor and the ink layer.
 * All geometry is in "sheet units": u = width / 400.
 */
public final class SheetView extends ViewGroup {
    public final InkView ink;
    private View content;
    private View anchor;
    public Model.Style style = new Model.Style();
    public float u = 2.6f;
    public int lh = 78, base = 44, extraUnits;
    public boolean inkBelow;
    public boolean flat; // no rounded outline (export)

    public SheetView(Context c) {
        super(c);
        setWillNotDraw(false);
        ink = new InkView(c);
        addView(ink);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), flat ? 0 : u * 1.6f); }
        });
    }

    public void setContent(View content, View anchor) {
        if (this.content != null) removeView(this.content);
        this.content = content; this.anchor = anchor;
        addView(content, inkBelow ? 1 : 0);
    }

    public void setInkBelow(boolean b) {
        if (b == inkBelow) return;
        inkBelow = b;
        removeView(ink);
        addView(ink, b ? 0 : getChildCount());
        ink.invalidate();
    }

    /** Line height and baseline (px) for the given unit and font. */
    public void setMetrics(float u, int lh, int base) {
        this.u = u; this.lh = lh; this.base = base;
        ink.setUnit(u);
        requestLayout(); invalidate(); invalidateOutline();
    }

    public int rowsTop() { return anchor != null ? anchor.getTop() + (content != null ? content.getTop() : 0) : Math.round(u * 16 + lh * 3); }

    @Override protected void onMeasure(int wms, int hms) {
        int w = MeasureSpec.getSize(wms);
        int ch = 0;
        if (content != null) {
            content.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            ch = content.getMeasuredHeight();
        }
        int minH = Math.round(w * 1.414f + extraUnits * u);
        int inkH = Math.round((ink.maxStrokeY() + 30) * u);
        int h = Math.max(ch, Math.max(minH, inkH));
        ink.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        setMeasuredDimension(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        if (content != null) content.layout(0, 0, w, content.getMeasuredHeight());
        ink.layout(0, 0, w, h);
        invalidateOutline();
    }

    /** In brush mode the ink layer gets every touch, even when it sits under the text. */
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (ink.enabled) return ink.dispatchTouchEvent(e);
        return super.dispatchTouchEvent(e);
    }

    @Override protected void onDraw(Canvas c) {
        PaperStyle.draw(c, getWidth(), getHeight(), style, u, lh, rowsTop(), base);
    }

    /** Computes font size, line height and baseline for a style at a given sheet width. */
    public static int[] metricsFor(Model.Style s, float u) {
        float fs = s.fontSize * u;
        int lh = Math.round(fs * 1.7f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(PaperStyle.typeface(s.font));
        p.setTextSize(fs);
        Paint.FontMetricsInt fm = p.getFontMetricsInt();
        int base = -fm.ascent + Math.max(0, (lh - (fm.descent - fm.ascent)) / 3);
        return new int[]{Math.round(fs), lh, base};
    }
}
