package it.ampy.ampypad;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Random;

/** Paper patterns, textures, fonts and the predefined sheet styles. */
public final class PaperStyle {
    private PaperStyle() { }

    public static final String[] PATTERNS = {"blank", "lined", "margin", "grid", "dots", "mm", "music"};
    public static final String[] PATTERN_NAMES = {"BIANCO", "RIGHE", "QUADERNO", "QUADRETTI", "PUNTINI", "MILLIMETRATA", "PENTAGRAMMA"};

    public static final String[] TEXTURES = {"none", "paper", "parchment", "watercolor", "chalk", "linen"};
    public static final String[] TEXTURE_NAMES = {"NESSUNA", "CARTA", "PERGAMENA", "ACQUERELLO", "LAVAGNA", "LINO"};

    public static final String[] FONTS = {"casual", "cursive", "serif", "sans", "mono", "condensed", "smallcaps", "serifmono"};
    public static final String[] FONT_NAMES = {"A MANO", "CALLIGRAFICO", "CLASSICO", "MODERNO", "MACCHINA DA SCRIVERE", "STRETTO", "MAIUSCOLETTO", "TELESCRIVENTE"};

    public static final String[] EFFECTS = {"scintille", "inchiostro", "piume", "polvere", "petali", "coriandoli", "bolle", "neon", "nessuno"};
    public static final String[] EFFECT_NAMES = {"✦ SCINTILLE", "● GOCCE D'INCHIOSTRO", "❦ PIUME", "∴ POLVERE MAGICA", "❀ PETALI", "▰ CORIANDOLI", "○ BOLLE", "⚡ GLITCH NEON", "— NESSUNO"};

    public static final String[] TRANSITIONS = {"sfoglia", "scivola", "dissolvi", "caduta", "vortice", "glitch"};
    public static final String[] TRANSITION_NAMES = {"SFOGLIA (pagina che gira)", "SCIVOLA", "DISSOLVI", "CADUTA SULLA PILA", "VORTICE", "GLITCH NEON"};

    public static Typeface typeface(String key) {
        switch (key) {
            case "cursive": return Typeface.create("cursive", Typeface.NORMAL);
            case "serif": return Typeface.SERIF;
            case "sans": return Typeface.SANS_SERIF;
            case "mono": return Typeface.MONOSPACE;
            case "condensed": return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
            case "smallcaps": return Typeface.create("sans-serif-smallcaps", Typeface.NORMAL);
            case "serifmono": return Typeface.create("serif-monospace", Typeface.NORMAL);
            default: return Typeface.create("casual", Typeface.NORMAL);
        }
    }

    /* ================================================================ presets */
    public static final class Preset {
        public final String key, name, pattern, font, texture, effect, glyph;
        public final int paper, line, ink, quill, size;
        Preset(String key, String name, String glyph, String pattern, int paper, int line, int ink, int quill, String font, int size, String texture, String effect) {
            this.key = key; this.name = name; this.glyph = glyph; this.pattern = pattern; this.paper = paper; this.line = line; this.ink = ink;
            this.quill = quill; this.font = font; this.size = size; this.texture = texture; this.effect = effect;
        }
        public void apply(Model.Style s) {
            s.preset = key; s.pattern = pattern; s.paper = paper; s.line = line; s.ink = ink; s.quill = quill;
            s.font = font; s.fontSize = size; s.texture = texture; s.effect = effect;
        }
    }

    public static final Preset[] PRESETS = {
        new Preset("quaderno", "QUADERNO", "✎", "margin", 0xfffdfdf8, 0xffb9cde6, 0xff1f3a93, 0xff1f3a93, "casual", 18, "paper", "inchiostro"),
        new Preset("cyber", "CYBER NEON", "⌁", "dots", 0xff070b13, 0xff214557, 0xffecfcff, 0xff00ffe2, "mono", 16, "none", "neon"),
        new Preset("pergamena", "PERGAMENA", "❦", "blank", 0xfff1e2bd, 0xffc9a96b, 0xff4b2e14, 0xff8b5a2b, "cursive", 21, "parchment", "inchiostro"),
        new Preset("lavagna", "LAVAGNA", "▦", "blank", 0xff2e4a3d, 0xff4f6b5d, 0xfff4f1e8, 0xffffffff, "casual", 19, "chalk", "polvere"),
        new Preset("notte", "NOTTE STELLATA", "✦", "dots", 0xff141a33, 0xff2f3a66, 0xffe8ecff, 0xffffd36e, "sans", 17, "none", "scintille"),
        new Preset("kraft", "CARTA KRAFT", "▤", "grid", 0xffc9a677, 0xffb08e5f, 0xff2a1c0d, 0xff6b4423, "serifmono", 16, "paper", "piume"),
        new Preset("blueprint", "BLUEPRINT", "⌗", "mm", 0xff1e4f8f, 0xff4a78b5, 0xffffffff, 0xff9fd3ff, "mono", 15, "none", "scintille"),
        new Preset("sakura", "SAKURA", "❀", "dots", 0xfffff0f4, 0xfff5c2d1, 0xff8a2d55, 0xffe86a92, "cursive", 21, "none", "petali"),
        new Preset("acquerello", "ACQUERELLO", "◍", "lined", 0xfffbf8f1, 0xffc9d6e8, 0xff2b4c6f, 0xff5b8bd0, "cursive", 21, "watercolor", "bolle"),
        new Preset("festa", "FESTA", "✺", "grid", 0xfffffbe8, 0xfff0e2a8, 0xff3a3a3a, 0xffff3ac8, "casual", 18, "none", "coriandoli"),
        new Preset("bloodmoon", "BLOOD MOON", "◐", "lined", 0xff120609, 0xff3a1219, 0xffffecef, 0xffff2b4e, "serif", 17, "none", "inchiostro"),
        new Preset("minimal", "MINIMAL", "□", "blank", 0xffffffff, 0xffe6e6e6, 0xff1f1f1f, 0xff333333, "sans", 17, "none", "polvere"),
        new Preset("musica", "SPARTITO", "♪", "music", 0xfffffdf6, 0xff9a9a9a, 0xff202020, 0xff6b2d8c, "serif", 17, "paper", "piume"),
        new Preset("lino", "LINO", "▒", "lined", 0xffefe9de, 0xffd2c6b0, 0xff3d3328, 0xff8b6f47, "serif", 18, "linen", "polvere"),
    };

    public static Preset preset(String key) { for (Preset p : PRESETS) if (p.key.equals(key)) return p; return PRESETS[0]; }
    public static int indexOf(String[] arr, String v) { for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i; return 0; }

    /* ================================================================ drawing */
    private static Bitmap noise, noiseLight;
    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static Bitmap noiseTile(boolean light) {
        Bitmap b = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
        Random r = new Random(light ? 7 : 3);
        int[] px = new int[160 * 160];
        for (int i = 0; i < px.length; i++) {
            float v = r.nextFloat();
            int a = (int) (v * v * (light ? 34 : 30));
            px[i] = light ? Color.argb(a, 255, 255, 255) : Color.argb(a, 60, 40, 15);
        }
        b.setPixels(px, 0, 160, 0, 0, 160, 160);
        return b;
    }

    /**
     * Paints the sheet: background colour, pattern and texture.
     * @param lh line height in px, @param y0 y where the first text row starts, @param base baseline offset inside a row.
     */
    public static void draw(Canvas c, int w, int h, Model.Style s, float u, float lh, float y0, float base) {
        c.drawColor(s.paper);
        drawTexture(c, w, h, s, u, true);
        P.setShader(null); P.setStyle(Paint.Style.STROKE); P.setColor(s.line); P.setStrokeWidth(Math.max(1f, u * 0.32f));
        float lineY = base + u * 1.4f; // ruled line sits just under the baseline
        switch (s.pattern) {
            case "lined":
            case "margin": {
                float start = y0 + lineY;
                while (start - lh > 0) start -= lh;
                for (float y = start; y < h; y += lh) c.drawLine(0, y, w, y, P);
                if ("margin".equals(s.pattern)) {
                    P.setColor(0xc8e07070); P.setStrokeWidth(Math.max(1f, u * 0.45f));
                    c.drawLine(u * 23, 0, u * 23, h, P);
                    c.drawLine(u * 24.4f, 0, u * 24.4f, h, P);
                }
                break;
            }
            case "grid": {
                float cell = lh / 2f;
                float sy = y0; while (sy - cell > 0) sy -= cell;
                for (float y = sy; y < h; y += cell) c.drawLine(0, y, w, y, P);
                for (float x = (w % cell) / 2f; x < w; x += cell) c.drawLine(x, 0, x, h, P);
                break;
            }
            case "mm": {
                float minor = lh / 4f;
                int lc = s.line;
                P.setColor(Theme.a(lc, 90)); P.setStrokeWidth(Math.max(1f, u * 0.2f));
                float sy = y0; while (sy - minor > 0) sy -= minor;
                int k = 0; float sy0 = sy;
                for (float y = sy; y < h; y += minor) c.drawLine(0, y, w, y, P);
                for (float x = 0; x < w; x += minor) c.drawLine(x, 0, x, h, P);
                P.setColor(lc); P.setStrokeWidth(Math.max(1f, u * 0.38f));
                float my = y0; while (my - lh > 0) my -= lh;
                for (float y = my; y < h; y += lh) c.drawLine(0, y, w, y, P);
                for (float x = 0; x < w; x += lh) c.drawLine(x, 0, x, h, P);
                break;
            }
            case "dots": {
                float sp = lh * 0.75f;
                P.setStyle(Paint.Style.FILL);
                float r = Math.max(1.2f, u * 0.55f);
                float sy = y0; while (sy - sp > 0) sy -= sp;
                for (float y = sy; y < h; y += sp) for (float x = sp / 2f; x < w; x += sp) c.drawCircle(x, y, r, P);
                break;
            }
            case "music": {
                float gap = lh / 4f, staff = lh * 3f;
                float sy = y0; while (sy - staff > 0) sy -= staff;
                for (float y = sy + lh * 0.75f; y < h; y += staff)
                    for (int i = 0; i < 5; i++) c.drawLine(u * 10, y + i * gap, w - u * 10, y + i * gap, P);
                break;
            }
            default: break;
        }
        drawTexture(c, w, h, s, u, false);
        P.setShader(null);
    }

    private static void drawTexture(Canvas c, int w, int h, Model.Style s, float u, boolean under) {
        String t = s.texture;
        if ("none".equals(t)) return;
        if (noise == null) { noise = noiseTile(false); noiseLight = noiseTile(true); }
        P.setStyle(Paint.Style.FILL);
        if (under) {
            Random r = new Random(w * 31L + 17);
            if ("parchment".equals(t)) {
                for (int i = 0; i < 26; i++) {
                    float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = u * (30 + r.nextFloat() * 90);
                    P.setShader(new RadialGradient(x, y, rad, 0x22a0701e, 0x00a0701e, Shader.TileMode.CLAMP));
                    c.drawCircle(x, y, rad, P);
                }
            } else if ("watercolor".equals(t)) {
                int[] cols = {0x2a7fb4ff, 0x26ff9ec4, 0x24ffd36e, 0x229be7a8, 0x24c39bd3};
                for (int i = 0; i < 22; i++) {
                    float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = u * (40 + r.nextFloat() * 110);
                    int col = cols[i % cols.length];
                    P.setShader(new RadialGradient(x, y, rad, col, col & 0x00ffffff, Shader.TileMode.CLAMP));
                    c.drawCircle(x, y, rad, P);
                }
            } else if ("chalk".equals(t)) {
                for (int i = 0; i < 14; i++) {
                    float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = u * (50 + r.nextFloat() * 120);
                    P.setShader(new RadialGradient(x, y, rad, 0x14ffffff, 0x00ffffff, Shader.TileMode.CLAMP));
                    c.drawCircle(x, y, rad, P);
                }
            }
            P.setShader(null);
            return;
        }
        // over the pattern: grain + vignette
        Bitmap tile = "chalk".equals(t) ? noiseLight : noise;
        P.setShader(new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        P.setAlpha("linen".equals(t) ? 160 : 255);
        c.drawRect(0, 0, w, h, P);
        P.setAlpha(255);
        if ("linen".equals(t)) {
            P.setShader(null); P.setColor(0x0c000000); P.setStrokeWidth(1);
            float step = Math.max(2f, u * 0.9f);
            for (float y = 0; y < h; y += step) c.drawLine(0, y, w, y, P);
            for (float x = 0; x < w; x += step) c.drawLine(x, 0, x, h, P);
        }
        if ("parchment".equals(t)) {
            float rad = (float) Math.hypot(w, h) * 0.62f;
            P.setShader(new RadialGradient(w / 2f, h * 0.45f, rad, new int[]{0x00000000, 0x00000000, 0x40784812}, new float[]{0, 0.62f, 1}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, P);
        }
        P.setShader(null);
    }
}
