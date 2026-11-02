package it.ampy.ampypad;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import java.util.Random;

/** The single "Pennello": one tool, twelve heads for writing, drawing and painting. Renders in sheet units. */
public final class Brushes {
    private Brushes() { }

    public static final String[] KEYS = {"penna", "stilografica", "matita", "pennarello", "evidenziatore", "acquerello",
        "aerografo", "gessetto", "neon", "arcobaleno", "timbro", "gomma"};
    public static final String[] NAMES = {"PENNA", "STILOGRAFICA", "MATITA", "PENNARELLO", "EVIDENZIATORE", "ACQUERELLO",
        "AEROGRAFO", "GESSETTO", "NEON", "ARCOBALENO", "TIMBRI", "GOMMA"};
    public static final String[] DESC = {"tratto che segue velocità e pressione", "pennino calligrafico a 45°", "grafite granulosa",
        "colore pieno e uniforme", "trasparente, sopra il testo", "pittura morbida che sfuma", "spruzzo di particelle",
        "polvere di gesso", "luce al neon Ampy", "colori che scorrono", "emoji lungo il tratto", "cancella il disegno"};
    public static final String[] GLYPHS = {"✒", "✑", "✏", "▮", "▬", "◍", "∷", "▧", "⚡", "◠", "★", "⌫"};

    public static final String[] SYM_NAMES = {"NESSUNA", "SPECCHIO", "4 QUADRANTI", "RADIALE ×6", "RADIALE ×8", "CALEIDOSCOPIO ×12"};
    public static final String[] STAMPS = {"⭐", "❤️", "🌸", "✨", "🔥", "🎈", "🍀", "🦋", "🌙", "☀️", "🎵", "💎", "🐾", "🍓", "🌈", "👑", "⚡", "🎯", "📌", "✅"};

    public static int index(String k) { for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(k)) return i; return 0; }

    static boolean layered(String k) {
        switch (k) {
            case "penna": case "stilografica": case "pennarello": case "evidenziatore": case "acquerello": case "arcobaleno": return true;
            default: return false;
        }
    }
    static float layerAlpha(String k) {
        switch (k) {
            case "pennarello": return 0.92f;
            case "evidenziatore": return 0.38f;
            case "acquerello": return 0.55f;
            default: return 1f;
        }
    }

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
    private static final PorterDuffXfermode CLEAR = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);

    /** Renders a whole stroke (with symmetry copies) onto a canvas already scaled to sheet units. */
    public static void render(Canvas c, Model.Stroke s) {
        if (s == null || s.n == 0) return;
        Matrix[] ms = symmetry(s);
        boolean lay = layered(s.brush);
        if (lay) c.saveLayerAlpha(null, Math.round(255 * layerAlpha(s.brush) * Ui.clamp(s.opacity, 0.03f, 1f)));
        for (Matrix m : ms) {
            c.save();
            if (m != null) c.concat(m);
            drawOne(c, s, lay ? 1f : s.opacity);
            c.restore();
        }
        if (lay) c.restore();
    }

    public static Matrix[] symmetry(Model.Stroke s) {
        float cx = s.cx, cy = s.cy;
        switch (s.sym) {
            case 1: { Matrix a = new Matrix(); a.setScale(-1, 1, cx, cy); return new Matrix[]{null, a}; }
            case 2: {
                Matrix a = new Matrix(); a.setScale(-1, 1, cx, cy);
                Matrix b = new Matrix(); b.setScale(1, -1, cx, cy);
                Matrix d = new Matrix(); d.setScale(-1, -1, cx, cy);
                return new Matrix[]{null, a, b, d};
            }
            case 3: case 4: {
                int n = s.sym == 3 ? 6 : 8;
                Matrix[] out = new Matrix[n];
                for (int i = 0; i < n; i++) { Matrix m = new Matrix(); m.setRotate(360f * i / n, cx, cy); out[i] = m; }
                return out;
            }
            case 5: {
                Matrix[] out = new Matrix[12];
                for (int i = 0; i < 6; i++) {
                    Matrix m = new Matrix(); m.setRotate(60f * i, cx, cy); out[i * 2] = m;
                    Matrix f = new Matrix(); f.setScale(-1, 1, cx, cy); f.postRotate(60f * i, cx, cy); out[i * 2 + 1] = f;
                }
                return out;
            }
            default: return new Matrix[]{null};
        }
    }

    /* ================================================================ geometry helpers */
    static final class Dense {
        float[] a = new float[600]; int n;
        void add(float x, float y, float w) {
            if ((n + 1) * 3 > a.length) { float[] b = new float[a.length * 2]; System.arraycopy(a, 0, b, 0, n * 3); a = b; }
            a[n * 3] = x; a[n * 3 + 1] = y; a[n * 3 + 2] = w; n++;
        }
    }

    private static void line(Dense d, float ax, float ay, float aw, float bx, float by, float bw, float step) {
        float len = (float) Math.hypot(bx - ax, by - ay);
        int k = Math.max(1, (int) Math.ceil(len / step));
        for (int i = 1; i <= k; i++) { float t = i / (float) k; d.add(ax + (bx - ax) * t, ay + (by - ay) * t, aw + (bw - aw) * t); }
    }
    private static void quad(Dense d, float ax, float ay, float aw, float cx, float cy, float bx, float by, float bw, float step) {
        float len = (float) (Math.hypot(cx - ax, cy - ay) + Math.hypot(bx - cx, by - cy));
        int k = Math.max(1, (int) Math.ceil(len / step));
        for (int i = 1; i <= k; i++) {
            float t = i / (float) k, it = 1 - t;
            d.add(it * it * ax + 2 * it * t * cx + t * t * bx, it * it * ay + 2 * it * t * cy + t * t * by, aw + (bw - aw) * t);
        }
    }
    /** Smooth (quadratic through mid-points) and resample the stroke every {@code step} units. */
    static Dense dense(Model.Stroke s, float step) {
        Dense d = new Dense();
        float[] p = s.pts; int n = s.n;
        d.add(p[0], p[1], p[2]);
        if (n == 1) return d;
        if (n == 2) { line(d, p[0], p[1], p[2], p[3], p[4], p[5], step); return d; }
        float ax = (p[0] + p[3]) / 2, ay = (p[1] + p[4]) / 2, aw = (p[2] + p[5]) / 2;
        line(d, p[0], p[1], p[2], ax, ay, aw, step);
        for (int k = 1; k < n - 1; k++) {
            int i = k * 3, j = (k + 1) * 3;
            float bx = (p[i] + p[j]) / 2, by = (p[i + 1] + p[j + 1]) / 2, bw = (p[i + 2] + p[j + 2]) / 2;
            quad(d, ax, ay, aw, p[i], p[i + 1], bx, by, bw, step);
            ax = bx; ay = by; aw = bw;
        }
        int l = (n - 1) * 3;
        line(d, ax, ay, aw, p[l], p[l + 1], p[l + 2], step);
        return d;
    }
    private static Path smoothPath(Model.Stroke s) {
        Path path = PATH; path.reset();
        float[] p = s.pts; int n = s.n;
        path.moveTo(p[0], p[1]);
        if (n == 1) { path.lineTo(p[0] + 0.01f, p[1]); return path; }
        if (n == 2) { path.lineTo(p[3], p[4]); return path; }
        for (int k = 1; k < n - 1; k++) {
            int i = k * 3, j = (k + 1) * 3;
            path.quadTo(p[i], p[i + 1], (p[i] + p[j]) / 2, (p[i + 1] + p[j + 1]) / 2);
        }
        path.lineTo(p[(n - 1) * 3], p[(n - 1) * 3 + 1]);
        return path;
    }

    private static int alpha(int color, float a) { return Theme.a(color, Math.round(((color >>> 24) & 255) * Ui.clamp(a, 0, 1))); }

    /* ================================================================ brush heads */
    private static void drawOne(Canvas c, Model.Stroke s, float op) {
        Paint p = P;
        p.reset(); p.setAntiAlias(true); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(alpha(s.color, op));
        Random r = new Random(s.seed);
        float sz = s.size;
        switch (s.brush) {
            case "stilografica": {
                Dense d = dense(s, 0.5f);
                p.setStyle(Paint.Style.FILL);
                float nx = 0.7071f, ny = -0.7071f;
                Path q = new Path();
                for (int i = 1; i < d.n; i++) {
                    float x0 = d.a[i * 3 - 3], y0 = d.a[i * 3 - 2], h0 = d.a[i * 3 - 1] * 0.95f;
                    float x1 = d.a[i * 3], y1 = d.a[i * 3 + 1], h1 = d.a[i * 3 + 2] * 0.95f;
                    q.reset();
                    q.moveTo(x0 + nx * h0, y0 + ny * h0); q.lineTo(x1 + nx * h1, y1 + ny * h1);
                    q.lineTo(x1 - nx * h1, y1 - ny * h1); q.lineTo(x0 - nx * h0, y0 - ny * h0); q.close();
                    c.drawPath(q, p);
                }
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(0.35f, sz * 0.18f));
                c.drawPath(smoothPath(s), p);
                break;
            }
            case "matita": {
                Dense d = dense(s, 0.45f);
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < d.n; i++) {
                    float x = d.a[i * 3], y = d.a[i * 3 + 1], w = Math.max(0.6f, d.a[i * 3 + 2]);
                    for (int j = 0; j < 2; j++) {
                        float jx = (r.nextFloat() - 0.5f) * w * 0.8f, jy = (r.nextFloat() - 0.5f) * w * 0.8f, g = w * (0.25f + r.nextFloat() * 0.3f);
                        p.setColor(alpha(s.color, op * (0.25f + r.nextFloat() * 0.5f)));
                        c.drawRect(x + jx - g / 2, y + jy - g / 2, x + jx + g / 2, y + jy + g / 2, p);
                    }
                }
                break;
            }
            case "pennarello": {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sz * 1.15f);
                c.drawPath(smoothPath(s), p);
                break;
            }
            case "evidenziatore": {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sz * 2.8f); p.setStrokeCap(Paint.Cap.BUTT);
                c.drawPath(smoothPath(s), p);
                break;
            }
            case "acquerello": {
                Path base = new Path(smoothPath(s));
                p.setStyle(Paint.Style.STROKE);
                p.setMaskFilter(new BlurMaskFilter(Math.max(0.8f, sz * 1.1f), BlurMaskFilter.Blur.NORMAL));
                for (int k = 0; k < 4; k++) {
                    c.save();
                    c.translate((r.nextFloat() - 0.5f) * sz * 1.2f, (r.nextFloat() - 0.5f) * sz * 1.2f);
                    p.setStrokeWidth(sz * (3.2f + r.nextFloat() * 1.8f));
                    p.setColor(alpha(s.color, 0.55f));
                    c.drawPath(base, p);
                    c.restore();
                }
                p.setMaskFilter(null);
                p.setStrokeWidth(sz * 3.8f); p.setColor(alpha(s.color, 0.25f));
                c.drawPath(base, p);
                break;
            }
            case "aerografo": {
                Dense d = dense(s, 1.1f);
                p.setStyle(Paint.Style.FILL);
                float R = sz * 2.4f; int per = (int) (5 + sz * 0.9f);
                for (int i = 0; i < d.n; i++) {
                    float x = d.a[i * 3], y = d.a[i * 3 + 1];
                    for (int j = 0; j < per; j++) {
                        double ang = r.nextFloat() * Math.PI * 2; float rr = R * (float) Math.sqrt(r.nextFloat());
                        p.setColor(alpha(s.color, op * (0.35f + r.nextFloat() * 0.5f)));
                        c.drawCircle(x + (float) Math.cos(ang) * rr, y + (float) Math.sin(ang) * rr, 0.28f + r.nextFloat() * 0.32f, p);
                    }
                }
                break;
            }
            case "gessetto": {
                Dense d = dense(s, 0.6f);
                p.setStyle(Paint.Style.FILL);
                float w = sz * 1.3f; int per = (int) (2 + w * 1.1f);
                for (int i = 0; i < d.n; i++) {
                    float x = d.a[i * 3], y = d.a[i * 3 + 1];
                    for (int j = 0; j < per; j++) {
                        float a = r.nextFloat();
                        if (a < 0.3f) { r.nextFloat(); r.nextFloat(); continue; }
                        float ox = (r.nextFloat() - 0.5f) * w, oy = (r.nextFloat() - 0.5f) * w, g = 0.35f + a * 0.45f;
                        p.setColor(alpha(s.color, op * (0.35f + a * 0.6f)));
                        c.drawRect(x + ox, y + oy, x + ox + g, y + oy + g, p);
                    }
                }
                break;
            }
            case "neon": {
                Path base = new Path(smoothPath(s));
                p.setStyle(Paint.Style.STROKE);
                p.setMaskFilter(new BlurMaskFilter(Math.max(1f, sz * 1.8f), BlurMaskFilter.Blur.NORMAL));
                p.setStrokeWidth(sz * 2.4f); p.setColor(alpha(s.color, op * 0.85f));
                c.drawPath(base, p); c.drawPath(base, p);
                p.setMaskFilter(null);
                p.setStrokeWidth(sz); p.setColor(alpha(s.color, op));
                c.drawPath(base, p);
                p.setStrokeWidth(Math.max(0.3f, sz * 0.36f)); p.setColor(alpha(0xffffffff, op * 0.9f));
                c.drawPath(base, p);
                break;
            }
            case "arcobaleno": {
                Dense d = dense(s, 0.7f);
                p.setStyle(Paint.Style.STROKE);
                float hue = s.seed % 360, dist = 0;
                float[] hsv = {0, 0.82f, 1f};
                if (d.n == 1) { p.setStyle(Paint.Style.FILL); hsv[0] = hue; p.setColor(Color.HSVToColor(hsv)); c.drawCircle(d.a[0], d.a[1], d.a[2] / 2, p); break; }
                for (int i = 1; i < d.n; i++) {
                    float x0 = d.a[i * 3 - 3], y0 = d.a[i * 3 - 2], x1 = d.a[i * 3], y1 = d.a[i * 3 + 1];
                    dist += (float) Math.hypot(x1 - x0, y1 - y0);
                    hsv[0] = (hue + dist * 1.6f) % 360f;
                    p.setColor(Color.HSVToColor(hsv));
                    p.setStrokeWidth(Math.max(0.4f, d.a[i * 3 + 2]));
                    c.drawLine(x0, y0, x1, y1, p);
                }
                break;
            }
            case "timbro": {
                p.setStyle(Paint.Style.FILL); p.setColor(alpha(0xff000000, op));
                float ts = sz * 3.2f + 9f;
                p.setTextSize(ts); p.setTextAlign(Paint.Align.CENTER);
                Paint.FontMetrics fm = p.getFontMetrics();
                float off = -(fm.ascent + fm.descent) / 2f;
                Dense d = dense(s, 1f);
                float spacing = ts * 1.15f, acc = spacing;
                for (int i = 0; i < d.n; i++) {
                    if (i > 0) acc += (float) Math.hypot(d.a[i * 3] - d.a[i * 3 - 3], d.a[i * 3 + 1] - d.a[i * 3 - 2]);
                    if (acc >= spacing) {
                        acc = 0;
                        c.save();
                        c.rotate((r.nextFloat() - 0.5f) * 40f, d.a[i * 3], d.a[i * 3 + 1]);
                        c.drawText(s.emoji, d.a[i * 3], d.a[i * 3 + 1] + off, p);
                        c.restore();
                    }
                }
                break;
            }
            case "gomma": {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sz * 2.6f); p.setXfermode(CLEAR); p.setColor(0xff000000);
                c.drawPath(smoothPath(s), p);
                p.setXfermode(null);
                break;
            }
            default: { // penna: variable width segments
                Dense d = dense(s, 0.7f);
                if (d.n == 1) { p.setStyle(Paint.Style.FILL); c.drawCircle(d.a[0], d.a[1], Math.max(0.3f, d.a[2] / 2), p); break; }
                p.setStyle(Paint.Style.STROKE);
                for (int i = 1; i < d.n; i++) {
                    p.setStrokeWidth(Math.max(0.3f, d.a[i * 3 + 2]));
                    c.drawLine(d.a[i * 3 - 3], d.a[i * 3 - 2], d.a[i * 3], d.a[i * 3 + 1], p);
                }
                break;
            }
        }
    }

    /** Small preview of a brush head (wave), used by the brush picker and the bottom bar. */
    public static Bitmap preview(String brush, int color, float size, float opacity, String emoji, int w, int h, int bg) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (bg != 0) c.drawColor(bg);
        float u = w / 100f;
        c.save(); c.scale(u, u);
        float hh = h / u;
        Model.Stroke s = new Model.Stroke();
        s.brush = "gomma".equals(brush) ? "penna" : brush; s.color = "gomma".equals(brush) ? 0x55888888 : color;
        s.size = Ui.clamp(size, 1.2f, hh / 5f); s.opacity = opacity; s.emoji = emoji; s.seed = 42;
        for (int i = 0; i <= 26; i++) {
            float x = 8 + i * (84f / 26f);
            float y = hh / 2f + (float) Math.sin(i / 26f * Math.PI * 2) * hh * 0.24f;
            float w0 = "penna".equals(brush) ? s.size * (0.55f + 0.6f * (float) Math.sin(i / 26f * Math.PI)) : s.size;
            s.add(x, y, w0);
        }
        render(c, s);
        if ("gomma".equals(brush)) {
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG); t.setColor(0xffff6177); t.setStrokeWidth(1.4f); t.setStyle(Paint.Style.STROKE);
            c.drawLine(40, hh * 0.2f, 60, hh * 0.8f, t); c.drawLine(60, hh * 0.2f, 40, hh * 0.8f, t);
        }
        c.restore();
        return b;
    }
}
