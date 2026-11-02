package it.ampy.ampypad;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

/**
 * Document scanner core, pure Java (no OpenCV):
 * page detection (colour/brightness segmentation + convex hull + max-area quad + curved edge bulges),
 * perspective + curved-edge rectification (homography + Coons-patch correction), clean-up filters, JPEG PDF writer.
 *
 * A quad is float[16]: corners TL, TR, BR, BL (x,y) then edge mid-points top, right, bottom, left (x,y), in image pixels.
 */
public final class ScanCore {
    private ScanCore() { }

    public static final String[] FILTERS = {"documento", "vivido", "grigio", "bn", "originale"};
    public static final String[] FILTER_NAMES = {"DOCUMENTO", "COLORI VIVI", "GRIGIO", "BIANCO E NERO", "ORIGINALE"};

    /* ================================================================ loading */
    public static Bitmap load(Context c, Uri u, int maxSide) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
        int rot = 0;
        try (InputStream in = c.getContentResolver().openInputStream(u)) { if (in != null) rot = exifRotation(new ExifInterface(in)); } catch (Exception ignored) { }
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample(o.outWidth, o.outHeight, maxSide);
        Bitmap b;
        try (InputStream in = c.getContentResolver().openInputStream(u)) { b = BitmapFactory.decodeStream(in, null, d); }
        return fix(b, rot, maxSide);
    }

    public static Bitmap load(File f, int maxSide) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int rot = 0;
        try { rot = exifRotation(new ExifInterface(f.getAbsolutePath())); } catch (Exception ignored) { }
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample(o.outWidth, o.outHeight, maxSide);
        return fix(BitmapFactory.decodeFile(f.getAbsolutePath(), d), rot, maxSide);
    }

    private static int sample(int w, int h, int maxSide) { int s = 1; while (Math.max(w, h) / s > maxSide * 1.5f) s *= 2; return s; }

    private static int exifRotation(ExifInterface ex) {
        switch (ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            case ExifInterface.ORIENTATION_ROTATE_90: return 90;
            case ExifInterface.ORIENTATION_ROTATE_180: return 180;
            case ExifInterface.ORIENTATION_ROTATE_270: return 270;
            default: return 0;
        }
    }

    private static Bitmap fix(Bitmap b, int rot, int maxSide) throws IOException {
        if (b == null) throw new IOException("immagine non leggibile");
        float s = Math.min(1f, maxSide / (float) Math.max(b.getWidth(), b.getHeight()));
        if (rot == 0 && s >= 1f) return b;
        Matrix m = new Matrix();
        m.postScale(s, s);
        m.postRotate(rot);
        Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        if (r != b) b.recycle();
        return r;
    }

    public static Bitmap rotate(Bitmap b, int deg) {
        if (deg % 360 == 0) return b;
        Matrix m = new Matrix(); m.postRotate(deg);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    public static Bitmap fit(Bitmap b, int maxSide) {
        float s = maxSide / (float) Math.max(b.getWidth(), b.getHeight());
        if (s >= 1f) return b;
        return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)), Math.max(1, Math.round(b.getHeight() * s)), true);
    }

    /* ================================================================ detection */
    public static float[] full(int w, int h, float inset) {
        float l = w * inset, t = h * inset, r = w * (1 - inset), b = h * (1 - inset);
        return new float[]{l, t, r, t, r, b, l, b, (l + r) / 2, t, r, (t + b) / 2, (l + r) / 2, b, l, (t + b) / 2};
    }

    /** Straight edges again (mid-points back on the chords). */
    public static void straighten(float[] q) {
        for (int e = 0; e < 4; e++) {
            int a = e, b = (e + 1) % 4;
            q[8 + e * 2] = (q[a * 2] + q[b * 2]) / 2;
            q[9 + e * 2] = (q[a * 2 + 1] + q[b * 2 + 1]) / 2;
        }
    }

    public static float[] detect(Bitmap src) {
        int W = src.getWidth(), H = src.getHeight();
        float sc = 420f / Math.max(W, H);
        int w = Math.max(8, Math.round(W * sc)), h = Math.max(8, Math.round(H * sc));
        Bitmap sm = Bitmap.createScaledBitmap(src, w, h, true);
        int[] px = new int[w * h];
        sm.getPixels(px, 0, w, 0, 0, w, h);
        if (sm != src) sm.recycle();
        // paper = bright and unsaturated
        float[] f = new float[w * h];
        for (int i = 0; i < px.length; i++) {
            int c = px[i], r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
            float L = 0.299f * r + 0.587f * g + 0.114f * b;
            float S = mx == 0 ? 0 : (mx - mn) / (float) mx;
            f[i] = L * (1 - 0.6f * S);
        }
        boxBlur(f, w, h, 2); boxBlur(f, w, h, 2);
        int t = otsu(f);
        boolean[] m = new boolean[w * h];
        for (int i = 0; i < m.length; i++) m[i] = f[i] > t;
        int cnt = 0, tot = 0;
        for (int y = h / 3; y < h * 2 / 3; y++) for (int x = w / 3; x < w * 2 / 3; x++) { tot++; if (m[y * w + x]) cnt++; }
        if (cnt < tot / 2) for (int i = 0; i < m.length; i++) m[i] = !m[i];
        m = morph(m, w, h, 2, false); // open: erode …
        m = morph(m, w, h, 2, true);  // … then dilate
        boolean[] comp = component(m, w, h);
        if (comp == null) return full(W, H, 0.05f);
        boolean[] mask = fillHoles(comp, w, h);
        int area = 0; for (boolean b : mask) if (b) area++;
        if (area < 0.12f * w * h) return full(W, H, 0.05f);

        // boundary samples → convex hull
        List<float[]> pts = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            int l = -1, r = -1;
            for (int x = 0; x < w; x++) if (mask[y * w + x]) { if (l < 0) l = x; r = x; }
            if (l >= 0) { pts.add(new float[]{l, y}); pts.add(new float[]{r, y}); }
        }
        for (int x = 0; x < w; x++) {
            int tp = -1, bt = -1;
            for (int y = 0; y < h; y++) if (mask[y * w + x]) { if (tp < 0) tp = y; bt = y; }
            if (tp >= 0) { pts.add(new float[]{x, tp}); pts.add(new float[]{x, bt}); }
        }
        List<float[]> hull = hull(pts);
        if (hull.size() < 4) return full(W, H, 0.05f);
        float[][] c = new float[4][];
        for (float[] p : hull) {
            if (c[0] == null || p[0] + p[1] < c[0][0] + c[0][1]) c[0] = p;
            if (c[1] == null || p[0] - p[1] > c[1][0] - c[1][1]) c[1] = p;
            if (c[2] == null || p[0] + p[1] > c[2][0] + c[2][1]) c[2] = p;
            if (c[3] == null || p[0] - p[1] < c[3][0] - c[3][1]) c[3] = p;
        }
        for (int pass = 0; pass < 3; pass++) {
            for (int k = 0; k < 4; k++) {
                float best = quadArea(c); float[] bp = c[k];
                for (float[] p : hull) {
                    float[] old = c[k]; c[k] = p;
                    float a = convex(c) ? quadArea(c) : -1;
                    if (a > best) { best = a; bp = p; }
                    c[k] = old;
                }
                c[k] = bp;
            }
        }
        if (!convex(c) || quadArea(c) < 0.12f * w * h) return full(W, H, 0.05f);

        float[] q = new float[16];
        for (int k = 0; k < 4; k++) { q[k * 2] = c[k][0]; q[k * 2 + 1] = c[k][1]; }
        // curved edges: where does the page boundary really cross the perpendicular through each edge midpoint?
        float cx = (q[0] + q[2] + q[4] + q[6]) / 4, cy = (q[1] + q[3] + q[5] + q[7]) / 4;
        for (int e = 0; e < 4; e++) {
            int a = e, b = (e + 1) % 4;
            float ax = q[a * 2], ay = q[a * 2 + 1], bx = q[b * 2], by = q[b * 2 + 1];
            float mx = (ax + bx) / 2, my = (ay + by) / 2, len = (float) Math.hypot(bx - ax, by - ay);
            float nx = -(by - ay) / len, ny = (bx - ax) / len;
            if ((mx - cx) * nx + (my - cy) * ny < 0) { nx = -nx; ny = -ny; }
            float range = len * 0.16f, found = 0;
            boolean hit = false;
            for (float s = range; s >= -range; s -= 0.5f) {
                int x = Math.round(mx + nx * s), y = Math.round(my + ny * s);
                if (x >= 0 && y >= 0 && x < w && y < h && mask[y * w + x]) { found = s; hit = true; break; }
            }
            if (!hit || Math.abs(found) < 1.6f) found = 0;
            q[8 + e * 2] = mx + nx * found; q[9 + e * 2] = my + ny * found;
        }
        for (int i = 0; i < 16; i++) q[i] /= sc;
        return q;
    }

    private static void boxBlur(float[] f, int w, int h, int r) {
        float[] t = new float[f.length];
        for (int y = 0; y < h; y++) {
            float s = 0; int n = 0;
            for (int x = -r; x < w; x++) {
                int add = x + r, rem = x - r - 1;
                if (add < w) { s += f[y * w + add]; n++; }
                if (rem >= 0) { s -= f[y * w + rem]; n--; }
                if (x >= 0) t[y * w + x] = s / n;
            }
        }
        for (int x = 0; x < w; x++) {
            float s = 0; int n = 0;
            for (int y = -r; y < h; y++) {
                int add = y + r, rem = y - r - 1;
                if (add < h) { s += t[add * w + x]; n++; }
                if (rem >= 0) { s -= t[rem * w + x]; n--; }
                if (y >= 0) f[y * w + x] = s / n;
            }
        }
    }

    private static int otsu(float[] f) {
        int[] hist = new int[256];
        for (float v : f) hist[Math.max(0, Math.min(255, (int) v))]++;
        long total = f.length; double sum = 0;
        for (int i = 0; i < 256; i++) sum += i * (double) hist[i];
        double sumB = 0, best = -1; long wB = 0; int t = 128;
        for (int i = 0; i < 256; i++) {
            wB += hist[i]; if (wB == 0) continue;
            long wF = total - wB; if (wF == 0) break;
            sumB += i * (double) hist[i];
            double mB = sumB / wB, mF = (sum - sumB) / wF, between = (double) wB * wF * (mB - mF) * (mB - mF);
            if (between > best) { best = between; t = i; }
        }
        return t;
    }

    private static boolean[] morph(boolean[] m, int w, int h, int r, boolean dilate) {
        boolean[] o = new boolean[m.length];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean v = !dilate;
            outer:
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                int xx = x + dx, yy = y + dy;
                boolean s = xx >= 0 && yy >= 0 && xx < w && yy < h && m[yy * w + xx];
                if (dilate && s) { v = true; break outer; }
                if (!dilate && !s) { v = false; break outer; }
            }
            o[y * w + x] = v;
        }
        return o;
    }

    /** Connected component of the mask nearest to the image centre. */
    private static boolean[] component(boolean[] m, int w, int h) {
        int seed = -1; double bd = 1e18;
        for (int y = 0; y < h; y += 2) for (int x = 0; x < w; x += 2) {
            if (!m[y * w + x]) continue;
            double d = Math.pow(x - w / 2.0, 2) + Math.pow(y - h / 2.0, 2);
            if (d < bd) { bd = d; seed = y * w + x; }
        }
        if (seed < 0) return null;
        boolean[] c = new boolean[m.length];
        ArrayDeque<Integer> st = new ArrayDeque<>();
        st.add(seed); c[seed] = true;
        while (!st.isEmpty()) {
            int i = st.poll(), x = i % w, y = i / w;
            int[] nb = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > 0 ? i - w : -1, y < h - 1 ? i + w : -1};
            for (int j : nb) if (j >= 0 && m[j] && !c[j]) { c[j] = true; st.add(j); }
        }
        return c;
    }

    private static boolean[] fillHoles(boolean[] c, int w, int h) {
        boolean[] out = new boolean[c.length];
        ArrayDeque<Integer> st = new ArrayDeque<>();
        for (int x = 0; x < w; x++) { st.add(x); st.add((h - 1) * w + x); }
        for (int y = 0; y < h; y++) { st.add(y * w); st.add(y * w + w - 1); }
        while (!st.isEmpty()) {
            int i = st.poll();
            if (out[i] || c[i]) continue;
            out[i] = true; // outside
            int x = i % w, y = i / w;
            if (x > 0) st.add(i - 1);
            if (x < w - 1) st.add(i + 1);
            if (y > 0) st.add(i - w);
            if (y < h - 1) st.add(i + w);
        }
        for (int i = 0; i < out.length; i++) out[i] = !out[i];
        return out;
    }

    private static List<float[]> hull(List<float[]> p) {
        p.sort((a, b) -> a[0] != b[0] ? Float.compare(a[0], b[0]) : Float.compare(a[1], b[1]));
        List<float[]> lo = new ArrayList<>(), up = new ArrayList<>();
        for (float[] q : p) { while (lo.size() >= 2 && cross(lo.get(lo.size() - 2), lo.get(lo.size() - 1), q) <= 0) lo.remove(lo.size() - 1); lo.add(q); }
        for (int i = p.size() - 1; i >= 0; i--) { float[] q = p.get(i); while (up.size() >= 2 && cross(up.get(up.size() - 2), up.get(up.size() - 1), q) <= 0) up.remove(up.size() - 1); up.add(q); }
        lo.remove(lo.size() - 1); up.remove(up.size() - 1);
        lo.addAll(up);
        return lo;
    }
    private static float cross(float[] o, float[] a, float[] b) { return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]); }
    private static float quadArea(float[][] c) {
        float s = 0;
        for (int i = 0; i < 4; i++) { float[] a = c[i], b = c[(i + 1) % 4]; s += a[0] * b[1] - b[0] * a[1]; }
        return Math.abs(s) / 2;
    }
    private static boolean convex(float[][] c) {
        int sign = 0;
        for (int i = 0; i < 4; i++) {
            float z = cross(c[i], c[(i + 1) % 4], c[(i + 2) % 4]);
            int sg = z > 0 ? 1 : z < 0 ? -1 : 0;
            if (sg == 0) return false;
            if (sign == 0) sign = sg; else if (sg != sign) return false;
        }
        return true;
    }

    /* ================================================================ rectification */
    private static final class Homo {
        final double a, b, c, d, e, f, g, h;
        Homo(float[] q) {
            double x0 = q[0], y0 = q[1], x1 = q[2], y1 = q[3], x2 = q[4], y2 = q[5], x3 = q[6], y3 = q[7];
            double sx = x0 - x1 + x2 - x3, sy = y0 - y1 + y2 - y3;
            if (Math.abs(sx) < 1e-9 && Math.abs(sy) < 1e-9) {
                a = x1 - x0; b = x3 - x0; c = x0; d = y1 - y0; e = y3 - y0; f = y0; g = 0; h = 0;
            } else {
                double dx1 = x1 - x2, dx2 = x3 - x2, dy1 = y1 - y2, dy2 = y3 - y2, den = dx1 * dy2 - dx2 * dy1;
                if (Math.abs(den) < 1e-9) den = 1e-9;
                g = (sx * dy2 - dx2 * sy) / den; h = (dx1 * sy - sx * dy1) / den;
                a = x1 - x0 + g * x1; b = x3 - x0 + h * x3; c = x0;
                d = y1 - y0 + g * y1; e = y3 - y0 + h * y3; f = y0;
            }
        }
        double x(double u, double v) { return (a * u + b * v + c) / (g * u + h * v + 1); }
        double y(double u, double v) { return (d * u + e * v + f) / (g * u + h * v + 1); }
    }

    /** Point on the quadratic edge through A, mid M, B at parameter t. */
    private static double qb(double a, double m, double b, double t) {
        double c = 2 * m - (a + b) / 2, it = 1 - t;
        return it * it * a + 2 * it * t * c + t * t * b;
    }

    private static float edgeLen(float[] q, int e) {
        int a = e, b = (e + 1) % 4;
        double len = 0, px = q[a * 2], py = q[a * 2 + 1];
        for (int i = 1; i <= 24; i++) {
            double t = i / 24.0;
            double x = qb(q[a * 2], q[8 + e * 2], q[b * 2], t), y = qb(q[a * 2 + 1], q[9 + e * 2], q[b * 2 + 1], t);
            len += Math.hypot(x - px, y - py); px = x; py = y;
        }
        return (float) len;
    }

    /**
     * Normal deviation of a curved edge from the straight perspective edge, sampled n+1 times.
     * Edge e: 0 top (TL→TR), 1 right (TR→BR), 2 bottom (BL→BR), 3 left (TL→BL).
     */
    private static float[][] deviation(float[] q, Homo H, int e, int n) {
        int ia, ib, im;
        switch (e) {
            case 0: ia = 0; ib = 1; im = 0; break;
            case 1: ia = 1; ib = 2; im = 1; break;
            case 2: ia = 3; ib = 2; im = 2; break;
            default: ia = 0; ib = 3; im = 3; break;
        }
        double ax = q[ia * 2], ay = q[ia * 2 + 1], bx = q[ib * 2], by = q[ib * 2 + 1], mx = q[8 + im * 2], my = q[9 + im * 2];
        double len = Math.hypot(bx - ax, by - ay); if (len < 1e-6) len = 1e-6;
        double nx = -(by - ay) / len, ny = (bx - ax) / len;
        float[][] out = new float[2][n + 1];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double hx, hy;
            switch (e) {
                case 0: hx = H.x(t, 0); hy = H.y(t, 0); break;
                case 1: hx = H.x(1, t); hy = H.y(1, t); break;
                case 2: hx = H.x(t, 1); hy = H.y(t, 1); break;
                default: hx = H.x(0, t); hy = H.y(0, t); break;
            }
            double cx = qb(ax, mx, bx, t), cy = qb(ay, my, by, t);
            double dd = (cx - hx) * nx + (cy - hy) * ny;
            out[0][i] = (float) (nx * dd); out[1][i] = (float) (ny * dd);
        }
        return out;
    }

    /** Perspective + curved-edge rectification of the page described by quad q. */
    public static Bitmap warp(Bitmap src, float[] q, int maxSide) {
        float wTop = edgeLen(q, 0), wBot = edgeLen(q, 2), hRight = edgeLen(q, 1), hLeft = edgeLen(q, 3);
        float W0 = Math.max(wTop, wBot), H0 = Math.max(hLeft, hRight);
        float s = Math.min(1.25f, maxSide / Math.max(W0, H0));
        final int W = Math.max(16, Math.round(W0 * s)), H = Math.max(16, Math.round(H0 * s));
        final int sw = src.getWidth(), sh = src.getHeight();
        final int[] sp = new int[sw * sh];
        src.getPixels(sp, 0, sw, 0, 0, sw, sh);
        final Homo hm = new Homo(q);
        final float[][] top = deviation(q, hm, 0, W), bot = deviation(q, hm, 2, W);
        final float[][] lef = deviation(q, hm, 3, H), rig = deviation(q, hm, 1, H);
        final int[] out = new int[W * H];
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        Thread[] ts = new Thread[threads];
        for (int k = 0; k < threads; k++) {
            final int k0 = k, nT = threads;
            ts[k] = new Thread(() -> {
                for (int Y = k0; Y < H; Y += nT) {
                    double v = (Y + 0.5) / H;
                    int vi = Math.min(H, Math.round((float) (v * H)));
                    for (int X = 0; X < W; X++) {
                        double u = (X + 0.5) / W;
                        int ui = Math.min(W, Math.round((float) (u * W)));
                        double x = hm.x(u, v) + (1 - v) * top[0][ui] + v * bot[0][ui] + (1 - u) * lef[0][vi] + u * rig[0][vi];
                        double y = hm.y(u, v) + (1 - v) * top[1][ui] + v * bot[1][ui] + (1 - u) * lef[1][vi] + u * rig[1][vi];
                        out[Y * W + X] = bilinear(sp, sw, sh, x, y);
                    }
                }
            });
            ts[k].start();
        }
        for (Thread t : ts) { try { t.join(); } catch (InterruptedException ignored) { } }
        return Bitmap.createBitmap(out, W, H, Bitmap.Config.ARGB_8888);
    }

    private static int bilinear(int[] p, int w, int h, double x, double y) {
        if (x < 0) x = 0; if (y < 0) y = 0;
        if (x > w - 1.001) x = w - 1.001; if (y > h - 1.001) y = h - 1.001;
        int x0 = (int) x, y0 = (int) y;
        float fx = (float) (x - x0), fy = (float) (y - y0);
        int a = p[y0 * w + x0], b = p[y0 * w + x0 + 1], c = p[(y0 + 1) * w + x0], d = p[(y0 + 1) * w + x0 + 1];
        int r = lerp4((a >> 16) & 255, (b >> 16) & 255, (c >> 16) & 255, (d >> 16) & 255, fx, fy);
        int g = lerp4((a >> 8) & 255, (b >> 8) & 255, (c >> 8) & 255, (d >> 8) & 255, fx, fy);
        int bl = lerp4(a & 255, b & 255, c & 255, d & 255, fx, fy);
        return 0xff000000 | (r << 16) | (g << 8) | bl;
    }
    private static int lerp4(int a, int b, int c, int d, float fx, float fy) {
        float top = a + (b - a) * fx, bot = c + (d - c) * fx;
        return Math.round(top + (bot - top) * fy);
    }

    /* ================================================================ filters */
    public static Bitmap filter(Bitmap in, String f) {
        int w = in.getWidth(), h = in.getHeight();
        int[] p = new int[w * h];
        in.getPixels(p, 0, w, 0, 0, w, h);
        if (!"originale".equals(f)) flatten(p, w, h);
        for (int i = 0; i < p.length; i++) {
            int c = p[i], r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            switch (f) {
                case "documento": r = con(r, 1.12f); g = con(g, 1.12f); b = con(b, 1.12f); break;
                case "vivido": {
                    float L = 0.299f * r + 0.587f * g + 0.114f * b;
                    r = con(Math.round(L + (r - L) * 1.5f), 1.15f); g = con(Math.round(L + (g - L) * 1.5f), 1.15f); b = con(Math.round(L + (b - L) * 1.5f), 1.15f);
                    break;
                }
                case "grigio": { int L = con(Math.round(0.299f * r + 0.587f * g + 0.114f * b), 1.15f); r = g = b = L; break; }
                case "bn": {
                    int L = Math.round(0.299f * r + 0.587f * g + 0.114f * b);
                    int v = L >= 200 ? 255 : L <= 140 ? 0 : (L - 140) * 255 / 60;
                    r = g = b = v; break;
                }
                default: break;
            }
            p[i] = 0xff000000 | (cl(r) << 16) | (cl(g) << 8) | cl(b);
        }
        return Bitmap.createBitmap(p, w, h, Bitmap.Config.ARGB_8888);
    }
    private static int cl(int v) { return v < 0 ? 0 : v > 255 ? 255 : v; }
    private static int con(int v, float k) { return cl(Math.round((v - 128) * k + 128)); }

    /** Removes shadows and uneven lighting: divides by a smooth estimate of the paper brightness. */
    private static void flatten(int[] p, int w, int h) {
        int bs = Math.max(4, Math.max(w, h) / 48);
        int gw = (w + bs - 1) / bs, gh = (h + bs - 1) / bs;
        float[] g = new float[gw * gh];
        for (int gy = 0; gy < gh; gy++) for (int gx = 0; gx < gw; gx++) {
            float mx = 0;
            // brightest ~10% of the block approximates the paper; use max of a sparse sample
            for (int y = gy * bs; y < Math.min(h, gy * bs + bs); y += 2) for (int x = gx * bs; x < Math.min(w, gx * bs + bs); x += 2) {
                int c = p[y * w + x];
                float L = 0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255);
                if (L > mx) mx = L;
            }
            g[gy * gw + gx] = mx;
        }
        boxBlur(g, gw, gh, 1); boxBlur(g, gw, gh, 1);
        for (int y = 0; y < h; y++) {
            float fy = Math.max(0, Math.min(gh - 1.001f, (y + 0.5f) / bs - 0.5f));
            int y0 = (int) fy; float ty = fy - y0;
            for (int x = 0; x < w; x++) {
                float fx = Math.max(0, Math.min(gw - 1.001f, (x + 0.5f) / bs - 0.5f));
                int x0 = (int) fx; float tx = fx - x0;
                int x1 = Math.min(gw - 1, x0 + 1), y1 = Math.min(gh - 1, y0 + 1);
                float bg = (g[y0 * gw + x0] * (1 - tx) + g[y0 * gw + x1] * tx) * (1 - ty) + (g[y1 * gw + x0] * (1 - tx) + g[y1 * gw + x1] * tx) * ty;
                float k = 248f / Math.max(40f, bg);
                int c = p[y * w + x];
                p[y * w + x] = 0xff000000 | (cl(Math.round(((c >> 16) & 255) * k)) << 16) | (cl(Math.round(((c >> 8) & 255) * k)) << 8) | cl(Math.round((c & 255) * k));
            }
        }
    }

    /* ================================================================ PDF (JPEG pages, small files for email) */
    public static byte[] pdf(List<byte[]> jpegs, List<int[]> sizes) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        List<Integer> off = new ArrayList<>();
        int n = jpegs.size(), objs = 2 + n * 3;
        w(o, "%PDF-1.4\n%âãÏÓ\n");
        off.add(o.size()); w(o, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) kids.append(3 + i * 3).append(" 0 R ");
        off.add(o.size()); w(o, "2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>\nendobj\n");
        for (int i = 0; i < n; i++) {
            int base = 3 + i * 3, iw = sizes.get(i)[0], ih = sizes.get(i)[1];
            boolean land = iw > ih;
            float pw = land ? 842 : 595, ph = land ? 595 : 842;
            float s = Math.min(pw / iw, ph / ih), dw = iw * s, dh = ih * s, dx = (pw - dw) / 2, dy = (ph - dh) / 2;
            off.add(o.size());
            w(o, base + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + f(pw) + " " + f(ph) + "] /Resources << /XObject << /Im0 "
                + (base + 2) + " 0 R >> >> /Contents " + (base + 1) + " 0 R >>\nendobj\n");
            String cs = "q " + f(dw) + " 0 0 " + f(dh) + " " + f(dx) + " " + f(dy) + " cm /Im0 Do Q\n";
            off.add(o.size());
            w(o, (base + 1) + " 0 obj\n<< /Length " + cs.length() + " >>\nstream\n" + cs + "endstream\nendobj\n");
            byte[] j = jpegs.get(i);
            off.add(o.size());
            w(o, (base + 2) + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + iw + " /Height " + ih
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + j.length + " >>\nstream\n");
            o.write(j);
            w(o, "\nendstream\nendobj\n");
        }
        int xref = o.size();
        StringBuilder x = new StringBuilder("xref\n0 " + (objs + 1) + "\n0000000000 65535 f \n");
        for (int v : off) x.append(String.format(Locale.US, "%010d 00000 n \n", v));
        x.append("trailer\n<< /Size ").append(objs + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        w(o, x.toString());
        return o.toByteArray();
    }
    private static void w(OutputStream o, String s) throws IOException { o.write(s.getBytes(StandardCharsets.ISO_8859_1)); }
    private static String f(float v) { return String.format(Locale.US, "%.2f", v); }

    public static byte[] jpeg(Bitmap b, int q) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, q, o);
        return o.toByteArray();
    }

    public static byte[] readFile(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            return o.toByteArray();
        }
    }
}
