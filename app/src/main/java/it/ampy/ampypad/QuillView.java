package it.ampy.ampypad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The quill: it hovers over the caret (or the brush tip) while you write/draw, and when you stop
 * it lays down on the paper and vanishes with an effect. Also hosts every particle effect of the app.
 */
public final class QuillView extends View {
    public static final String[] ICONS = {"piuma", "stilografica", "pennello", "matita", "bacchetta"};
    public static final String[] ICON_NAMES = {"PIUMA D'OCA", "STILOGRAFICA", "PENNELLO", "MATITA", "BACCHETTA MAGICA"};

    private static final int HIDDEN = 0, WRITING = 1, LANDING = 2, VANISH = 3;
    private static final int STAR = 0, DROP = 1, SPLAT = 2, FEATHER = 3, DUST = 4, PETAL = 5, CONFETTI = 6, BUBBLE = 7, RING = 8, GLITCH = 9;
    private static final float LEN = 90f, LAND_ROT = 38f;

    public interface SoundHook { void onVanish(String effect); }

    private int state = HIDDEN;
    private long stateAt, lastWrite, appearAt, lastFrame, lastTrail;
    private float x, y, tx, ty;
    public String icon = "piuma", effect = "scintille";
    public int color = 0xffffd16a;
    public boolean trail = true, enabled = true;
    public int delay = 900;
    public float scale = 1f;
    public SoundHook sound;

    private final List<P> parts = new ArrayList<>();
    private final Random rnd = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tmp = new Path();
    private final int[] loc = new int[2];
    private final float d;

    private static final class P {
        int type, color; float x, y, vx, vy, life, max, size, rot, vr, phase;
    }

    public QuillView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setClickable(false); setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override public boolean onTouchEvent(MotionEvent e) { return false; }

    private float k() { return d * 0.62f * scale; }

    /* ================================================================ API */
    /** Called on every keystroke / brush move with the tip position in screen coordinates. */
    public void write(float rawX, float rawY) {
        if (!enabled) return;
        getLocationOnScreen(loc);
        tx = rawX - loc[0]; ty = rawY - loc[1];
        long now = SystemClock.uptimeMillis();
        if (state == HIDDEN || state == VANISH) {
            state = WRITING; appearAt = now; stateAt = now;
            x = tx + 30 * d; y = ty - 40 * d;
        } else if (state == LANDING) { state = WRITING; stateAt = now; }
        lastWrite = now;
        if (trail && now - lastTrail > 45) { lastTrail = now; trailAt(tx, ty); }
        kick();
    }

    /** Demo of the landing + effect at a point (quill panel "PROVA"). */
    public void demo(float rawX, float rawY) {
        boolean was = enabled; enabled = true;
        write(rawX, rawY);
        lastWrite = SystemClock.uptimeMillis() - delay + 350;
        enabled = was;
    }

    public void hideNow() { state = HIDDEN; invalidate(); }

    /** Generic burst of an effect around a point (todo checked, new sheet, colour pick…). */
    public void burst(String fx, float rawX, float rawY, int n, int col) {
        getLocationOnScreen(loc);
        float bx = rawX - loc[0], by = rawY - loc[1];
        for (int i = 0; i < n; i++) spawn(fx, bx, by, col, 1f, (float) (rnd.nextFloat() * Math.PI * 2));
        kick();
    }

    private void kick() { if (lastFrame == 0) lastFrame = SystemClock.uptimeMillis(); postInvalidateOnAnimation(); }

    /* ================================================================ particles */
    private int fxColor(int base) {
        switch (rnd.nextInt(3)) {
            case 0: return base;
            case 1: return Ui.mix(base, 0xffffffff, 0.5f);
            default: return Ui.mix(base, 0xffffd16a, 0.35f);
        }
    }

    private P add(int type, float px, float py, float vx, float vy, float max, float size, int col) {
        if (parts.size() > 700) parts.remove(0);
        P q = new P();
        q.type = type; q.x = px; q.y = py; q.vx = vx; q.vy = vy; q.max = max; q.size = size; q.color = col;
        q.rot = rnd.nextFloat() * 360; q.vr = (rnd.nextFloat() - 0.5f) * 400; q.phase = rnd.nextFloat() * 6.28f;
        parts.add(q);
        return q;
    }

    private void spawn(String fx, float px, float py, int col, float power, float ang) {
        float sp = (40 + rnd.nextFloat() * 120) * d * power;
        float vx = (float) Math.cos(ang) * sp, vy = (float) Math.sin(ang) * sp;
        switch (fx) {
            case "scintille": add(STAR, px, py, vx, vy - 30 * d, 0.6f + rnd.nextFloat() * 0.7f, (3 + rnd.nextFloat() * 4) * d, fxColor(col)); break;
            case "inchiostro": add(DROP, px, py, vx * 0.7f, -Math.abs(vy) * 0.8f - 60 * d, 0.35f + rnd.nextFloat() * 0.3f, (1.5f + rnd.nextFloat() * 2.5f) * d, col); break;
            case "piume":
                if (rnd.nextInt(3) == 0) add(FEATHER, px, py, vx * 0.3f, -20 * d, 1.6f + rnd.nextFloat(), (8 + rnd.nextFloat() * 6) * d, Ui.mix(col, 0xffffffff, 0.3f));
                else add(DUST, px, py, vx * 0.4f, vy * 0.4f - 20 * d, 0.8f + rnd.nextFloat() * 0.6f, (1 + rnd.nextFloat() * 1.5f) * d, Ui.mix(col, 0xffffffff, 0.6f));
                break;
            case "polvere": add(DUST, px, py, vx * 0.5f + 25 * d, vy * 0.5f - 30 * d, 0.7f + rnd.nextFloat() * 0.9f, (0.8f + rnd.nextFloat() * 1.6f) * d, fxColor(col)); break;
            case "petali": {
                int[] pc = {0xffffb7c5, 0xffff8fab, 0xfffbd3e9, col};
                add(PETAL, px, py, vx * 0.5f, vy * 0.3f - 40 * d, 1.4f + rnd.nextFloat() * 0.8f, (5 + rnd.nextFloat() * 4) * d, pc[rnd.nextInt(pc.length)]);
                break;
            }
            case "coriandoli": {
                int[] cc = {0xffff3ac8, 0xff00ffe2, 0xffffd16a, 0xff7a96ff, 0xff50c878, 0xffff6177};
                add(CONFETTI, px, py, vx * 1.2f, -Math.abs(vy) * 1.4f - 120 * d, 1.2f + rnd.nextFloat() * 0.8f, (4 + rnd.nextFloat() * 4) * d, cc[rnd.nextInt(cc.length)]);
                break;
            }
            case "bolle": add(BUBBLE, px, py, vx * 0.3f, -40 * d - rnd.nextFloat() * 50 * d, 0.9f + rnd.nextFloat() * 0.9f, (4 + rnd.nextFloat() * 7) * d, Ui.mix(col, 0xffffffff, 0.4f)); break;
            case "neon": {
                int[] nc = {0xff00ffe2, 0xffff3ac8, 0xffffd16a};
                if (rnd.nextInt(3) == 0) add(GLITCH, px + (rnd.nextFloat() - 0.5f) * 40 * d, py + (rnd.nextFloat() - 0.5f) * 20 * d, 0, 0, 0.18f + rnd.nextFloat() * 0.25f, (10 + rnd.nextFloat() * 40) * d, nc[rnd.nextInt(3)]);
                else add(STAR, px, py, vx, vy, 0.4f + rnd.nextFloat() * 0.5f, (2 + rnd.nextFloat() * 3) * d, nc[rnd.nextInt(3)]);
                break;
            }
            default: break;
        }
    }

    private void trailAt(float px, float py) {
        switch (effect) {
            case "nessuno": return;
            case "inchiostro": add(SPLAT, px + (rnd.nextFloat() - 0.5f) * 6 * d, py + 2 * d, 0, 0, 0.9f, (0.8f + rnd.nextFloat()) * d, Theme.a(color, 160)); break;
            case "scintille": add(STAR, px, py, (rnd.nextFloat() - 0.5f) * 40 * d, -30 * d, 0.5f, 2.5f * d, fxColor(color)); break;
            case "neon": add(STAR, px, py, (rnd.nextFloat() - 0.5f) * 50 * d, -20 * d, 0.35f, 2f * d, rnd.nextBoolean() ? 0xff00ffe2 : 0xffff3ac8); break;
            case "bolle": add(BUBBLE, px, py, 0, -30 * d, 0.6f, 2.5f * d, Ui.mix(color, 0xffffffff, 0.4f)); break;
            default: add(DUST, px, py, (rnd.nextFloat() - 0.2f) * 30 * d, -25 * d, 0.6f, 1.2f * d, Ui.mix(color, 0xffffffff, 0.35f)); break;
        }
    }

    private void bodyPoint(float t, float[] out) {
        double a = Math.toRadians(45 + LAND_ROT);
        float dd = t * LEN * k();
        out[0] = x + dd * (float) Math.sin(a);
        out[1] = y - dd * (float) Math.cos(a);
    }

    private void spawnVanish() {
        if (sound != null) sound.onVanish(effect);
        if ("nessuno".equals(effect)) return;
        float[] pt = new float[2];
        int n = "coriandoli".equals(effect) ? 46 : "polvere".equals(effect) ? 30 : 28;
        for (int i = 0; i < n; i++) {
            bodyPoint(rnd.nextFloat(), pt);
            spawn(effect, pt[0], pt[1], color, 1f, (float) (rnd.nextFloat() * Math.PI * 2));
        }
        if ("neon".equals(effect)) for (int i = 0; i < 8; i++) { bodyPoint(rnd.nextFloat(), pt); add(GLITCH, pt[0], pt[1], 0, 0, 0.3f, (30 + rnd.nextFloat() * 60) * d, i % 2 == 0 ? 0xff00ffe2 : 0xffff3ac8); }
        if ("inchiostro".equals(effect)) add(SPLAT, x, y + 1.5f * d, 0, 0, 1.6f, 5 * d, Theme.a(color, 200));
    }

    private void step(float dt) {
        float g = 1300 * d;
        for (int i = parts.size() - 1; i >= 0; i--) {
            P q = parts.get(i);
            q.life += dt;
            switch (q.type) {
                case STAR: q.vy += 60 * d * dt; q.vx *= 0.97f; q.vy *= 0.97f; break;
                case DROP:
                    q.vy += g * dt;
                    if (q.life >= q.max) { parts.remove(i); add(SPLAT, q.x, q.y, 0, 0, 1.1f + rnd.nextFloat() * 0.6f, q.size * (1.4f + rnd.nextFloat()), q.color); continue; }
                    break;
                case FEATHER:
                    q.vy = 35 * d; q.x += (float) Math.sin(q.life * 3 + q.phase) * 40 * d * dt; q.rot = (float) Math.sin(q.life * 2.2 + q.phase) * 45; break;
                case DUST: q.vy -= 20 * d * dt; q.vx *= 0.99f; break;
                case PETAL: q.vy = Math.min(q.vy + 120 * d * dt, 70 * d); q.x += (float) Math.sin(q.life * 3 + q.phase) * 50 * d * dt; q.rot += q.vr * dt; break;
                case CONFETTI: q.vy += g * 0.45f * dt; q.vx *= 0.985f; q.vy *= 0.99f; q.rot += q.vr * dt; break;
                case BUBBLE:
                    q.x += (float) Math.sin(q.life * 6 + q.phase) * 30 * d * dt;
                    if (q.life >= q.max) { parts.remove(i); add(RING, q.x, q.y, 0, 0, 0.25f, q.size, q.color); continue; }
                    break;
                default: break;
            }
            q.x += q.vx * dt; q.y += q.vy * dt;
            if (q.life >= q.max) parts.remove(i);
        }
    }

    private void drawParts(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        for (P q : parts) {
            float t = Ui.clamp(q.life / q.max, 0, 1), a = 1 - t;
            switch (q.type) {
                case STAR: {
                    float s = q.size * (0.6f + 0.4f * (float) Math.abs(Math.sin(q.life * 14 + q.phase)));
                    p.setColor(Theme.a(q.color, (int) (255 * a)));
                    star(c, q.x, q.y, s);
                    break;
                }
                case DROP: p.setColor(q.color); c.drawCircle(q.x, q.y, q.size, p); break;
                case SPLAT: p.setColor(Theme.a(q.color, (int) (((q.color >>> 24) & 255) * a))); c.drawCircle(q.x, q.y, q.size * (0.8f + 0.2f * Math.min(1, t * 6)), p); break;
                case FEATHER: {
                    p.setColor(Theme.a(q.color, (int) (230 * a)));
                    c.save(); c.translate(q.x, q.y); c.rotate(q.rot);
                    tmp.reset(); float s = q.size;
                    tmp.moveTo(0, s); tmp.cubicTo(-s * 0.45f, s * 0.3f, -s * 0.4f, -s * 0.6f, 0, -s); tmp.cubicTo(s * 0.4f, -s * 0.6f, s * 0.45f, s * 0.3f, 0, s); tmp.close();
                    c.drawPath(tmp, p);
                    p.setColor(Theme.a(0xff6b4a1f, (int) (200 * a))); p.setStrokeWidth(d * 0.6f);
                    c.drawLine(0, s * 1.2f, 0, -s * 0.9f, p);
                    c.restore();
                    break;
                }
                case DUST: p.setColor(Theme.a(q.color, (int) (220 * a))); c.drawCircle(q.x, q.y, q.size * (1 - t * 0.6f), p); break;
                case PETAL: {
                    p.setColor(Theme.a(q.color, (int) (240 * Math.min(1, a * 2))));
                    c.save(); c.translate(q.x, q.y); c.rotate(q.rot); c.scale((float) Math.cos(q.life * 4 + q.phase), 1);
                    c.drawOval(new RectF(-q.size, -q.size * 0.55f, q.size, q.size * 0.55f), p);
                    c.restore();
                    break;
                }
                case CONFETTI: {
                    p.setColor(Theme.a(q.color, (int) (255 * Math.min(1, a * 2))));
                    c.save(); c.translate(q.x, q.y); c.rotate(q.rot); c.scale(1, (float) Math.cos(q.life * 9 + q.phase));
                    c.drawRect(-q.size / 2, -q.size * 0.3f, q.size / 2, q.size * 0.3f, p);
                    c.restore();
                    break;
                }
                case BUBBLE: {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d * 1.1f);
                    p.setColor(Theme.a(q.color, (int) (200 * Math.min(1, a * 3))));
                    c.drawCircle(q.x, q.y, q.size, p);
                    p.setStyle(Paint.Style.FILL); p.setColor(Theme.a(0xffffffff, (int) (170 * a)));
                    c.drawCircle(q.x - q.size * 0.35f, q.y - q.size * 0.35f, q.size * 0.22f, p);
                    break;
                }
                case RING:
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d);
                    p.setColor(Theme.a(q.color, (int) (220 * a)));
                    c.drawCircle(q.x, q.y, q.size * (1 + t * 0.9f), p);
                    p.setStyle(Paint.Style.FILL);
                    break;
                case GLITCH:
                    p.setColor(Theme.a(q.color, (int) (200 * a)));
                    c.drawRect(q.x - q.size / 2, q.y, q.x + q.size / 2, q.y + d * (1.5f + rnd.nextFloat() * 2.5f), p);
                    break;
                default: break;
            }
        }
    }

    private void star(Canvas c, float cx, float cy, float s) {
        tmp.reset();
        tmp.moveTo(cx, cy - s); tmp.quadTo(cx, cy, cx + s, cy); tmp.quadTo(cx, cy, cx, cy + s); tmp.quadTo(cx, cy, cx - s, cy); tmp.quadTo(cx, cy, cx, cy - s); tmp.close();
        c.drawPath(tmp, p);
    }

    /* ================================================================ frame */
    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Ui.clamp((now - lastFrame) / 1000f, 0f, 0.05f);
        lastFrame = now;
        step(dt);
        drawParts(c);

        if (state != HIDDEN) {
            x += (tx - x) * 0.38f; y += (ty - y) * 0.38f;
            float extra = 0, lift = 1, alpha = 1, sc = 1, clipT = 0;
            if (state == WRITING) {
                if (now - lastWrite > delay) { state = LANDING; stateAt = now; }
                float a = Ui.clamp((now - appearAt) / 260f, 0, 1);
                extra = (float) Math.sin(now / 55.0) * 4.5f - (1 - a) * 16;
                alpha = a; sc = 0.8f + 0.2f * a;
            }
            if (state == LANDING) {
                float t = Ui.clamp((now - stateAt) / 520f, 0, 1);
                float e = 1 + 2.2f * (float) Math.pow(t - 1, 3) + 1.2f * (float) Math.pow(t - 1, 2); // easeOutBack
                extra = LAND_ROT * e; lift = 1 - t;
                if (t >= 1) { state = VANISH; stateAt = now; spawnVanish(); }
            }
            if (state == VANISH) {
                float t = Ui.clamp((now - stateAt) / 750f, 0, 1);
                extra = LAND_ROT; lift = 0;
                if ("polvere".equals(effect)) {
                    clipT = t; alpha = 1 - t * 0.3f;
                    float[] pt = new float[2];
                    for (int i = 0; i < 3; i++) { bodyPoint(1 - t + rnd.nextFloat() * 0.05f, pt); spawn("polvere", pt[0], pt[1], color, 0.6f, (float) (-Math.PI / 3 + rnd.nextFloat())); }
                } else { alpha = 1 - t; sc = 1 - 0.12f * t; }
                if (t >= 1) state = HIDDEN;
            }
            if (state != HIDDEN) {
                float rot = 45 + extra;
                float ox = (2 + lift * 7) * d, oy = (2 + lift * 11) * d;
                drawIcon(c, x + ox, y + oy, rot, sc, (int) (60 * alpha + 20 * (1 - lift) * alpha), true, clipT);
                drawIcon(c, x, y - lift * 2 * d, rot, sc, (int) (255 * alpha), false, clipT);
            }
        }
        if (state != HIDDEN || !parts.isEmpty()) postInvalidateOnAnimation();
        else lastFrame = 0;
    }

    /* ================================================================ icons (tip at 0,0, body towards -y) */
    private int sh, al; private boolean shadow;
    private void col(int c0) { p.setColor(shadow ? Theme.a(0xff000000, al) : Theme.a(c0, (int) (((c0 >>> 24) & 255) * al / 255f))); }

    private void drawIcon(Canvas c, float px, float py, float rot, float sc, int alpha, boolean isShadow, float clipT) {
        if (alpha <= 0) return;
        shadow = isShadow; al = Math.min(255, alpha);
        float k = k() * sc;
        c.save();
        c.translate(px, py); c.rotate(rot); c.scale(k, k);
        if (clipT > 0) c.clipRect(-40, -LEN * (1 - clipT) - 2, 40, 10);
        p.setStyle(Paint.Style.FILL); p.setStrokeCap(Paint.Cap.ROUND);
        int dark = Ui.mix(color, 0xff000000, 0.6f);
        switch (icon) {
            case "stilografica": {
                col(Ui.mix(color, 0xff000000, 0.35f)); c.drawRoundRect(new RectF(-6.5f, -89, 6.5f, -76), 3, 3, p);
                col(color); c.drawRoundRect(new RectF(-6, -80, 6, -26), 3, 3, p);
                col(0x55ffffff); c.drawRect(-3.5f, -78, -1.5f, -28, p);
                col(0xffd4a84b); c.drawRect(5, -78, 7.5f, -52, p);
                col(0xff222222); c.drawRect(-5, -27, 5, -17, p);
                tmp.reset(); tmp.moveTo(0, 2); tmp.lineTo(-4.5f, -12); tmp.lineTo(-4.5f, -17); tmp.lineTo(4.5f, -17); tmp.lineTo(4.5f, -12); tmp.close();
                col(0xffd4a84b); c.drawPath(tmp, p);
                col(0xff3a2a0a); p.setStrokeWidth(0.8f); c.drawLine(0, 1, 0, -10, p); c.drawCircle(0, -11, 1.1f, p);
                break;
            }
            case "pennello": {
                tmp.reset(); tmp.moveTo(-4.5f, -35); tmp.lineTo(-3, -86); tmp.quadTo(0, -90, 3, -86); tmp.lineTo(4.5f, -35); tmp.close();
                col(0xffb07a45); c.drawPath(tmp, p);
                col(0x40ffffff); c.drawRect(-2.5f, -84, -1, -36, p);
                col(0xffc8ccd4); c.drawRect(-5, -36, 5, -24, p);
                col(0xff8d929c); c.drawRect(-5, -31, 5, -30, p);
                tmp.reset(); tmp.moveTo(0, 2); tmp.cubicTo(-5, -7, -5.5f, -16, -4.5f, -24); tmp.lineTo(4.5f, -24); tmp.cubicTo(5.5f, -16, 5, -7, 0, 2); tmp.close();
                col(color); c.drawPath(tmp, p);
                col(0x50ffffff); c.drawRect(-2.5f, -22, -1.2f, -8, p);
                break;
            }
            case "matita": {
                col(0xffff8fab); c.drawRoundRect(new RectF(-6, -89, 6, -81), 2, 2, p);
                col(0xffc8ccd4); c.drawRect(-6, -82, 6, -76, p);
                col(color); c.drawRect(-6, -76, 6, -17, p);
                col(0x45ffffff); c.drawRect(-2, -76, 2, -17, p);
                tmp.reset(); tmp.moveTo(-2.2f, -6); tmp.lineTo(-6, -17); tmp.lineTo(6, -17); tmp.lineTo(2.2f, -6); tmp.close();
                col(0xfff1cf9b); c.drawPath(tmp, p);
                tmp.reset(); tmp.moveTo(0, 2); tmp.lineTo(-2.2f, -6); tmp.lineTo(2.2f, -6); tmp.close();
                col(0xff2a2a2a); c.drawPath(tmp, p);
                break;
            }
            case "bacchetta": {
                col(0xff1a1a1a); c.drawRoundRect(new RectF(-2.4f, -88, 2.4f, -8), 2.4f, 2.4f, p);
                col(0xffffffff); c.drawRect(-2.4f, -88, 2.4f, -80, p);
                col(Theme.a(color, 70)); c.drawCircle(0, 0, 15, p);
                tmp.reset();
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI * i / 5 - Math.PI / 2; float r = i % 2 == 0 ? 11 : 4.5f;
                    float sx = (float) Math.cos(a) * r, sy = (float) Math.sin(a) * r;
                    if (i == 0) tmp.moveTo(sx, sy); else tmp.lineTo(sx, sy);
                }
                tmp.close();
                col(color); c.drawPath(tmp, p);
                break;
            }
            default: { // piuma d'oca
                tmp.reset(); tmp.moveTo(0, -12); tmp.cubicTo(-15, -26, -17, -58, -2, -88); tmp.cubicTo(11, -64, 15, -34, 0, -12); tmp.close();
                col(color); c.drawPath(tmp, p);
                tmp.reset(); tmp.moveTo(0, -14); tmp.cubicTo(8, -32, 9, -58, -1, -86); tmp.cubicTo(4, -60, 3, -34, 0, -14); tmp.close();
                col(0x46ffffff); c.drawPath(tmp, p);
                col(0x60ffffff); p.setStrokeWidth(0.9f);
                c.drawLine(0, -30, -10, -40, p); c.drawLine(0, -44, -12, -55, p); c.drawLine(0, -58, -10, -69, p);
                c.drawLine(0, -36, 9, -45, p); c.drawLine(0, -52, 10, -62, p); c.drawLine(0, -68, 7, -76, p);
                col(Ui.mix(color, 0xff000000, 0.25f)); // V notch
                c.drawLine(-12, -48, -6, -47, p);
                col(dark); p.setStrokeWidth(1.7f); c.drawLine(0, 1, 0, -86, p);
                tmp.reset(); tmp.moveTo(0, 2.5f); tmp.lineTo(-2.4f, -11); tmp.lineTo(2.4f, -11); tmp.close();
                col(0xff1a1208); c.drawPath(tmp, p);
                break;
            }
        }
        c.restore();
    }

    /** Static icon preview for the quill panel. */
    public static void drawPreview(Canvas c, String icon, int color, float w, float h, float density) {
        QuillView v = PREVIEW;
        if (v == null) return;
        v.icon = icon; v.color = color;
        float k = Math.min(w, h) / (LEN * 0.95f) / (density * 0.62f);
        v.scale = k;
        v.drawIcon(c, w * 0.2f, h * 0.85f, 45, 1, 255, false, 0);
    }
    static QuillView PREVIEW;
    public static void initPreview(Context c) { if (PREVIEW == null) PREVIEW = new QuillView(c.getApplicationContext()); }

    @SuppressWarnings("unused") private static int rgb(int r, int g, int b) { return Color.rgb(r, g, b); }
}
