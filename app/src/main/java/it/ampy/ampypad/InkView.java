package it.ampy.ampypad;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Ink layer of the sheet: vector strokes cached in a bitmap, live stroke on top,
 * pressure / velocity width, symmetry, two-finger scroll, hold-to-straighten.
 */
public final class InkView extends View {
    public interface Listener {
        void onInk(float rawX, float rawY);
        void onInkDone();
        void onStrokesChanged();
        void onScrollBy(float dx, float dy);
        void onShapeHint(String msg);
    }

    private Model.Page page;
    private final List<Model.Stroke> redo = new ArrayList<>();
    private static final int TILE = 2048;
    private final List<Bitmap> tiles = new ArrayList<>();
    private int tilesW, tilesH;
    private float u = 1;
    private Listener l;

    public boolean enabled;
    public String brush = "penna", emoji = "⭐", shape = "rettangolo";
    public int fill;
    public int color = 0xff1f3a93, sym, accent = 0xff00ffe2;
    public float size = 3, opacity = 1, symCx = 200, symCy = 280;

    private Model.Stroke cur;
    private long lastT, lastMoveAt;
    private float holdX, holdY, scrollLastY, scrollLastX;
    private boolean scrolling, straight, shaped;
    private Model.Stroke active;      // shape still editable with handles
    private int edit = -1;            // handle being dragged
    private float lastX, lastY;
    private final float dens;
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Runnable holdCheck = new Runnable() {
        @Override public void run() {
            if (cur == null || straight || shaped || cur.n < 6 || "forma".equals(cur.brush)) return;
            if (!"gomma".equals(cur.brush) && !"timbro".equals(cur.brush)) {
                String sh = Brushes.recognize(cur);
                if (sh != null) {
                    float minX = 1e9f, minY = 1e9f, maxX = -1e9f, maxY = -1e9f;
                    for (int i = 0; i < cur.n; i++) {
                        minX = Math.min(minX, cur.pts[i * 3]); maxX = Math.max(maxX, cur.pts[i * 3]);
                        minY = Math.min(minY, cur.pts[i * 3 + 1]); maxY = Math.max(maxY, cur.pts[i * 3 + 1]);
                    }
                    cur.brush = "forma"; cur.shape = sh; cur.fill = 0; cur.n = 0;
                    cur.add(minX, minY, cur.size); cur.add(maxX, maxY, cur.size);
                    shaped = true;
                    Ui.clock(InkView.this);
                    if (l != null) l.onShapeHint("◇ FORMA PERFETTA: " + Brushes.SHAPE_NAMES[java.util.Arrays.asList(Brushes.SHAPES).indexOf(sh)]);
                    invalidate();
                    return;
                }
            }
            float[] p = cur.pts; int n = cur.n;
            float x0 = p[0], y0 = p[1], x1 = p[(n - 1) * 3], y1 = p[(n - 1) * 3 + 1];
            if (Math.hypot(x1 - x0, y1 - y0) < 6) return;
            cur.n = 0;
            cur.add(x0, y0, cur.size); cur.add(x1, y1, cur.size);
            straight = true;
            Ui.clock(InkView.this);
            if (l != null) l.onShapeHint("— LINEA DRITTA");
            invalidate();
        }
    };

    public InkView(Context c) {
        super(c);
        dens = c.getResources().getDisplayMetrics().density;
        guide.setStyle(Paint.Style.STROKE);
    }

    public void setListener(Listener l) { this.l = l; }
    public void setUnit(float u) { if (u != this.u) { this.u = u; rebuild(); } }
    public Model.Page page() { return page; }
    public void setPage(Model.Page p) { page = p; redo.clear(); cur = null; active = null; edit = -1; rebuild(); }

    /** Bakes the shape under edit into the sheet (tool change, mode change, export…). */
    public void commitActive() {
        if (active == null) return;
        Model.Stroke a = active; active = null; edit = -1;
        paint(a);
        invalidate();
    }
    public boolean canUndo() { return page != null && !page.strokes.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

    public void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (w != tilesW || h != tilesH || tiles.isEmpty()) {
            tiles.clear(); tilesW = w; tilesH = h;
            try {
                for (int top = 0; top < h; top += TILE) tiles.add(Bitmap.createBitmap(w, Math.min(TILE, h - top), Bitmap.Config.ARGB_8888));
            } catch (OutOfMemoryError e) { tiles.clear(); tilesW = 0; return; }
        }
        for (Bitmap b : tiles) b.eraseColor(0);
        if (page != null) for (Model.Stroke s : page.strokes) if (s != active) paint(s);
        invalidate();
    }

    /** Renders a stroke into every cache tile it may touch. */
    private void paint(Model.Stroke s) {
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < s.n; i++) { float y = s.pts[i * 3 + 1]; if (y < minY) minY = y; if (y > maxY) maxY = y; }
        float pad = s.size * 4 + 30;
        for (int t = 0; t < tiles.size(); t++) {
            Bitmap b = tiles.get(t);
            int top = t * TILE;
            if (s.sym == 0 && ((maxY + pad) * u < top || (minY - pad) * u > top + b.getHeight())) continue;
            Canvas c = new Canvas(b);
            c.translate(0, -top);
            c.scale(u, u);
            Brushes.render(c, s);
        }
    }

    private void drawTiles(Canvas c) {
        for (int t = 0; t < tiles.size(); t++) c.drawBitmap(tiles.get(t), 0, t * TILE, null);
    }

    public float maxStrokeY() {
        float m = 0;
        if (page != null) for (Model.Stroke s : page.strokes) m = Math.max(m, s.maxY() + s.size * 3);
        return m;
    }

    @Override protected void onDraw(Canvas c) {
        if (cur != null && "gomma".equals(cur.brush)) {
            int sc = c.saveLayer(null, null);
            drawTiles(c);
            c.scale(u, u); Brushes.render(c, cur);
            c.restoreToCount(sc);
        } else {
            drawTiles(c);
            if (cur != null) { c.save(); c.scale(u, u); Brushes.render(c, cur); c.restore(); }
        }
        if (active != null) {
            c.save(); c.scale(u, u); Brushes.render(c, active); c.restore();
            if (enabled) drawHandles(c);
        }
        if (enabled && sym != 0) {
            float x = symCx * u, y = symCy * u;
            guide.setColor(Theme.a(accent, 120)); guide.setStrokeWidth(u * 0.5f);
            guide.setPathEffect(new DashPathEffect(new float[]{u * 3, u * 3}, 0));
            c.drawLine(x, 0, x, getHeight(), guide);
            if (sym != 1) c.drawLine(0, y, getWidth(), y, guide);
            guide.setPathEffect(null); guide.setColor(accent); guide.setStrokeWidth(u * 0.7f);
            c.drawCircle(x, y, u * 4, guide);
            c.drawCircle(x, y, u * 1.2f, guide);
        }
    }

    private boolean isLine(Model.Stroke s) { return "linea".equals(s.shape) || "freccia".equals(s.shape); }

    /** Handle positions (sheet units): 0..3 corners (lines: 0 start, 2 end), 5 delete. */
    private float[] handles() {
        Model.Stroke a = active;
        float x0 = a.pts[0], y0 = a.pts[1], x1 = a.pts[3], y1 = a.pts[4];
        float r = Math.max(x0, x1), t = Math.min(y0, y1);
        float off = 9 * dens / u;
        return new float[]{x0, y0, x1, y0, x1, y1, x0, y1, 0, 0, r + off, t - off};
    }

    private void drawHandles(Canvas c) {
        Model.Stroke a = active;
        float[] h = handles();
        float x0 = Math.min(a.pts[0], a.pts[3]) * u, x1 = Math.max(a.pts[0], a.pts[3]) * u;
        float y0 = Math.min(a.pts[1], a.pts[4]) * u, y1 = Math.max(a.pts[1], a.pts[4]) * u;
        guide.setStyle(Paint.Style.STROKE); guide.setStrokeWidth(dens * 1.2f); guide.setColor(accent);
        if (!isLine(a)) {
            guide.setPathEffect(new DashPathEffect(new float[]{dens * 5, dens * 4}, 0));
            c.drawRect(x0, y0, x1, y1, guide);
            guide.setPathEffect(null);
        }
        float hr = dens * 7;
        for (int i = 0; i < 4; i++) {
            if (isLine(a) && (i == 1 || i == 3)) continue;
            float x = h[i * 2] * u, y = h[i * 2 + 1] * u;
            guide.setStyle(Paint.Style.FILL); guide.setColor(0xffffffff); c.drawCircle(x, y, hr, guide);
            guide.setStyle(Paint.Style.STROKE); guide.setColor(accent); guide.setStrokeWidth(dens * 2.2f); c.drawCircle(x, y, hr, guide);
        }
        float dx = h[10] * u, dy = h[11] * u;
        guide.setStyle(Paint.Style.FILL); guide.setColor(0xffff2b4e); c.drawCircle(dx, dy, hr * 1.25f, guide);
        guide.setStyle(Paint.Style.STROKE); guide.setColor(0xffffffff); guide.setStrokeWidth(dens * 2);
        float k = hr * 0.5f;
        c.drawLine(dx - k, dy - k, dx + k, dy + k, guide); c.drawLine(dx + k, dy - k, dx - k, dy + k, guide);
        guide.setStyle(Paint.Style.STROKE);
    }

    private int hitActive(float px, float py) {
        Model.Stroke a = active;
        float[] h = handles();
        float x = px / u, y = py / u, rad = 20 * dens / u;
        if (Math.hypot(x - h[10], y - h[11]) < rad) return 5;
        for (int i = 0; i < 4; i++) {
            if (isLine(a) && (i == 1 || i == 3)) continue;
            if (Math.hypot(x - h[i * 2], y - h[i * 2 + 1]) < rad) return i;
        }
        float x0 = a.pts[0], y0 = a.pts[1], x1 = a.pts[3], y1 = a.pts[4];
        if (isLine(a)) {
            float dx = x1 - x0, dy = y1 - y0, L = dx * dx + dy * dy;
            float t = L == 0 ? 0 : Ui.clamp(((x - x0) * dx + (y - y0) * dy) / L, 0, 1);
            if (Math.hypot(x - (x0 + t * dx), y - (y0 + t * dy)) < rad) return 4;
            return -1;
        }
        float pad = 6 * dens / u;
        if (x > Math.min(x0, x1) - pad && x < Math.max(x0, x1) + pad && y > Math.min(y0, y1) - pad && y < Math.max(y0, y1) + pad) return 4;
        return -1;
    }

    private void dragActive(float dx, float dy) {
        float[] p = active.pts;
        switch (edit) {
            case 0: p[0] += dx; p[1] += dy; break;
            case 1: p[3] += dx; p[1] += dy; break;
            case 2: p[3] += dx; p[4] += dy; break;
            case 3: p[0] += dx; p[4] += dy; break;
            default: p[0] += dx; p[1] += dy; p[3] += dx; p[4] += dy; break;
        }
    }

    /* ================================================================ touch */
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!enabled || page == null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                scrolling = false;
                if (active != null) {
                    int hit = hitActive(e.getX(), e.getY());
                    if (hit == 5) {
                        page.strokes.remove(active); active = null; rebuild(); Ui.clock(this);
                        if (l != null) { l.onStrokesChanged(); l.onShapeHint("⌧ FORMA ELIMINATA"); }
                        edit = -2;
                        return true;
                    }
                    if (hit >= 0) { edit = hit; lastX = e.getX(); lastY = e.getY(); Ui.tick(this); return true; }
                    commitActive();
                }
                edit = -1;
                begin(e);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() >= 2) {
                    cur = null; straight = false; shaped = false; edit = -1; removeCallbacks(holdCheck);
                    scrolling = true; scrollLastY = e.getRawY(); scrollLastX = e.getRawX();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (scrolling) {
                    float y = e.getRawY(), x = e.getRawX();
                    if (l != null) l.onScrollBy(scrollLastX - x, scrollLastY - y);
                    scrollLastY = y; scrollLastX = x;
                    return true;
                }
                if (edit >= 0 && active != null) {
                    dragActive((e.getX() - lastX) / u, (e.getY() - lastY) / u);
                    lastX = e.getX(); lastY = e.getY();
                    if (l != null) l.onInk(e.getRawX(), e.getRawY());
                    invalidate();
                    return true;
                }
                if (cur == null) return true;
                int tool = e.getToolType(0);
                for (int h = 0; h < e.getHistorySize(); h++)
                    add(e.getHistoricalX(h), e.getHistoricalY(h), e.getHistoricalPressure(h), e.getHistoricalEventTime(h), tool);
                add(e.getX(), e.getY(), e.getPressure(), e.getEventTime(), tool);
                float sx = e.getX() / u, sy = e.getY() / u;
                if (Math.hypot(sx - holdX, sy - holdY) > 1.6f) {
                    holdX = sx; holdY = sy; lastMoveAt = e.getEventTime();
                    removeCallbacks(holdCheck); if (!straight) postDelayed(holdCheck, 560);
                }
                if (l != null) l.onInk(e.getRawX(), e.getRawY());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(holdCheck);
                if (edit >= 0) { edit = -1; if (l != null) { l.onStrokesChanged(); l.onInkDone(); } return true; }
                if (edit == -2) { edit = -1; return true; }
                if (cur != null && !scrolling) commit();
                cur = null; scrolling = false;
                if (l != null) l.onInkDone();
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(holdCheck);
                cur = null; scrolling = false; invalidate();
                if (l != null) l.onInkDone();
                return true;
            default:
                return true;
        }
    }

    private void begin(MotionEvent e) {
        cur = new Model.Stroke();
        int tool = e.getToolType(0);
        boolean eraserTip = tool == MotionEvent.TOOL_TYPE_ERASER || (e.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0;
        cur.brush = eraserTip ? "gomma" : brush;
        cur.color = color; cur.size = size; cur.opacity = opacity; cur.emoji = emoji;
        cur.sym = "gomma".equals(cur.brush) ? 0 : sym; cur.cx = symCx; cur.cy = symCy;
        cur.shape = shape; cur.fill = fill;
        straight = false; shaped = false;
        holdX = e.getX() / u; holdY = e.getY() / u; lastMoveAt = e.getEventTime();
        lastT = e.getEventTime();
        add(e.getX(), e.getY(), e.getPressure(), e.getEventTime(), tool);
        removeCallbacks(holdCheck); postDelayed(holdCheck, 560);
        if (l != null) l.onInk(e.getRawX(), e.getRawY());
        invalidate();
    }

    private void add(float x, float y, float pressure, long t, int tool) {
        Model.Stroke s = cur;
        float sx = x / u, sy = y / u;
        int n = s.n;
        if (shaped) return;
        if (straight || ("forma".equals(s.brush) && n >= 1)) {
            s.n = 1; s.add(sx, sy, s.size); return;
        }
        float w = s.size;
        boolean stylus = tool == MotionEvent.TOOL_TYPE_STYLUS;
        if (n > 0) {
            float lx = s.pts[(n - 1) * 3], ly = s.pts[(n - 1) * 3 + 1];
            float d = (float) Math.hypot(sx - lx, sy - ly);
            if (d < 0.35f) return;
            if ("penna".equals(s.brush)) {
                if (stylus) w = s.size * (0.25f + pressure * 1.5f);
                else {
                    float dt = Math.max(1, t - lastT);
                    float v = d / dt;
                    float target = s.size * Ui.clamp(1.28f - v * 0.3f, 0.42f, 1.28f);
                    w = s.pts[(n - 1) * 3 + 2] * 0.72f + target * 0.28f;
                }
            } else if ("stilografica".equals(s.brush) && stylus) w = s.size * (0.5f + pressure);
        } else if ("penna".equals(s.brush) && stylus) w = s.size * (0.25f + pressure * 1.5f);
        lastT = t;
        s.add(sx, sy, w);
    }

    private void commit() {
        Model.Stroke s = cur;
        if ("forma".equals(s.brush) && s.n < 2) { cur = null; invalidate(); return; }
        page.strokes.add(s);
        redo.clear();
        if ("forma".equals(s.brush)) active = s; else paint(s);
        cur = null;
        invalidate();
        if (l != null) l.onStrokesChanged();
    }

    public boolean undo() {
        if (!canUndo()) return false;
        active = null; edit = -1;
        redo.add(page.strokes.remove(page.strokes.size() - 1));
        rebuild();
        if (l != null) l.onStrokesChanged();
        return true;
    }
    public boolean redo() {
        if (redo.isEmpty()) return false;
        commitActive();
        page.strokes.add(redo.remove(redo.size() - 1));
        rebuild();
        if (l != null) l.onStrokesChanged();
        return true;
    }
    public List<Model.Stroke> clearAll() {
        active = null; edit = -1;
        List<Model.Stroke> old = new ArrayList<>(page.strokes);
        page.strokes.clear(); redo.clear();
        rebuild();
        if (l != null) l.onStrokesChanged();
        return old;
    }
    public void restore(List<Model.Stroke> old) {
        page.strokes.addAll(0, old);
        rebuild();
        if (l != null) l.onStrokesChanged();
    }
    /** Ink-only bitmap (transparent), for "export drawing only". */
    public Bitmap inkBitmap() {
        commitActive();
        if (tiles.isEmpty()) return null;
        Bitmap out = Bitmap.createBitmap(tilesW, tilesH, Bitmap.Config.ARGB_8888);
        drawTiles(new Canvas(out));
        return out;
    }
}
