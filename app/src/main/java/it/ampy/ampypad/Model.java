package it.ampy.ampypad;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Notebooks, sheets, blocks and vector ink strokes. Everything serialises to plain JSON. */
public final class Model {
    private Model() { }
    private static final Random RND = new Random();

    public static String uid() { return Long.toString(System.currentTimeMillis(), 36) + Integer.toString(RND.nextInt(1 << 24), 36); }

    /* ================================================================ notebook */
    public static final class Notebook {
        public String id = uid(), name = "Quaderno";
        public int color = 0xff00ffe2;
        public boolean collapsed;
        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("name", name).put("color", color).put("collapsed", collapsed);
        }
        static Notebook from(JSONObject o) {
            Notebook n = new Notebook();
            n.id = o.optString("id", n.id); n.name = o.optString("name", n.name);
            n.color = o.optInt("color", n.color); n.collapsed = o.optBoolean("collapsed");
            return n;
        }
    }

    /* ================================================================ rich text span */
    public static final class SpanRec {
        public int start, end, value;
        public String type; // b i u s fg bg
        public SpanRec(String type, int start, int end, int value) { this.type = type; this.start = start; this.end = end; this.value = value; }
    }

    /* ================================================================ block */
    public static final class Block {
        public String id = uid(), type = "p", text = "", color = "", emoji = "💡";
        public List<SpanRec> spans = new ArrayList<>();
        public boolean checked;
        public int indent, align; // align 0 left 1 center 2 right
        public Block() { }
        public Block(String type, String text) { this.type = type; this.text = text; }
        public Block copy() {
            Block b = new Block(type, text);
            b.color = color; b.emoji = emoji; b.checked = checked; b.indent = indent; b.align = align;
            for (SpanRec s : spans) b.spans.add(new SpanRec(s.type, s.start, s.end, s.value));
            return b;
        }
        JSONObject json() throws JSONException {
            JSONObject o = new JSONObject().put("id", id).put("type", type).put("text", text);
            if (!color.isEmpty()) o.put("color", color);
            if (checked) o.put("checked", true);
            if (indent != 0) o.put("indent", indent);
            if (align != 0) o.put("align", align);
            if ("callout".equals(type)) o.put("emoji", emoji);
            if (!spans.isEmpty()) {
                JSONArray a = new JSONArray();
                for (SpanRec s : spans) a.put(new JSONArray().put(s.type).put(s.start).put(s.end).put(s.value));
                o.put("spans", a);
            }
            return o;
        }
        static Block from(JSONObject o) {
            Block b = new Block(o.optString("type", "p"), o.optString("text", ""));
            b.id = o.optString("id", b.id); b.color = o.optString("color", "");
            b.checked = o.optBoolean("checked"); b.indent = o.optInt("indent"); b.align = o.optInt("align");
            b.emoji = o.optString("emoji", "💡");
            JSONArray a = o.optJSONArray("spans");
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONArray s = a.optJSONArray(i);
                if (s != null && s.length() >= 4) b.spans.add(new SpanRec(s.optString(0), s.optInt(1), s.optInt(2), s.optInt(3)));
            }
            return b;
        }
    }

    /* ================================================================ ink stroke (sheet units, 400 = sheet width) */
    public static final class Stroke {
        public String brush = "penna", emoji = "⭐", shape = "rettangolo";
        public int color = 0xff1f3a93, sym, seed = RND.nextInt(1 << 30), fill;
        public float size = 3, opacity = 1, cx, cy;
        public float[] pts = new float[96];
        public int n; // number of points (each point = x, y, w)
        public void add(float x, float y, float w) {
            if ((n + 1) * 3 > pts.length) {
                float[] np = new float[pts.length * 2];
                System.arraycopy(pts, 0, np, 0, n * 3);
                pts = np;
            }
            pts[n * 3] = x; pts[n * 3 + 1] = y; pts[n * 3 + 2] = w; n++;
        }
        public Stroke copy() {
            Stroke c = new Stroke();
            c.brush = brush; c.emoji = emoji; c.shape = shape; c.color = color; c.sym = sym; c.seed = seed; c.fill = fill;
            c.size = size; c.opacity = opacity; c.cx = cx; c.cy = cy; c.pts = pts.clone(); c.n = n;
            return c;
        }
        public float maxY() { float m = 0; for (int i = 0; i < n; i++) m = Math.max(m, pts[i * 3 + 1]); return m; }
        JSONObject json() throws JSONException {
            StringBuilder sb = new StringBuilder(n * 12);
            for (int i = 0; i < n * 3; i++) {
                if (i > 0) sb.append(',');
                sb.append(Math.round(pts[i] * 10));
            }
            JSONObject o = new JSONObject().put("b", brush).put("c", color).put("s", size).put("o", opacity)
                .put("seed", seed).put("p", sb.toString());
            if (sym != 0) o.put("sym", sym).put("cx", cx).put("cy", cy);
            if ("timbro".equals(brush)) o.put("e", emoji);
            if ("forma".equals(brush)) o.put("sh", shape).put("fi", fill);
            return o;
        }
        static Stroke from(JSONObject o) {
            Stroke s = new Stroke();
            s.brush = o.optString("b", "penna"); s.color = o.optInt("c", s.color);
            s.size = (float) o.optDouble("s", 3); s.opacity = (float) o.optDouble("o", 1);
            s.seed = o.optInt("seed", 1); s.sym = o.optInt("sym"); s.cx = (float) o.optDouble("cx", 200); s.cy = (float) o.optDouble("cy", 280);
            s.emoji = o.optString("e", "⭐");
            s.shape = o.optString("sh", "rettangolo"); s.fill = o.optInt("fi");
            String p = o.optString("p", "");
            if (!p.isEmpty()) {
                String[] t = p.split(",");
                int cnt = t.length / 3;
                s.pts = new float[Math.max(3, cnt * 3)];
                for (int i = 0; i < cnt * 3; i++) {
                    try { s.pts[i] = Integer.parseInt(t[i]) / 10f; } catch (NumberFormatException ignored) { }
                }
                s.n = cnt;
            }
            return s;
        }
    }

    /* ================================================================ sheet style */
    public static final class Style {
        public String preset = "quaderno", pattern = "margin", font = "casual", texture = "paper", effect = "inchiostro";
        public int paper = 0xfffdfdf8, line = 0xffb9cde6, ink = 0xff1f3a93, quill = 0xff1f3a93, fontSize = 18;
        public Style copy() {
            Style s = new Style();
            s.preset = preset; s.pattern = pattern; s.font = font; s.texture = texture; s.effect = effect;
            s.paper = paper; s.line = line; s.ink = ink; s.quill = quill; s.fontSize = fontSize;
            return s;
        }
        JSONObject json() throws JSONException {
            return new JSONObject().put("preset", preset).put("pattern", pattern).put("font", font).put("texture", texture)
                .put("effect", effect).put("paper", paper).put("line", line).put("ink", ink).put("quill", quill).put("fontSize", fontSize);
        }
        static Style from(JSONObject o) {
            Style s = new Style();
            if (o == null) return s;
            s.preset = o.optString("preset", s.preset); s.pattern = o.optString("pattern", s.pattern);
            s.font = o.optString("font", s.font); s.texture = o.optString("texture", s.texture); s.effect = o.optString("effect", s.effect);
            s.paper = o.optInt("paper", s.paper); s.line = o.optInt("line", s.line); s.ink = o.optInt("ink", s.ink);
            s.quill = o.optInt("quill", s.quill); s.fontSize = o.optInt("fontSize", s.fontSize);
            return s;
        }
    }

    /* ================================================================ page */
    public static final class Page {
        public String id = uid(), notebookId = "", title = "";
        public long created = System.currentTimeMillis(), updated = created;
        public boolean pinned;
        public int extra; // extra sheet length in sheet units
        public Style style = new Style();
        public List<Block> blocks = new ArrayList<>();
        public List<Stroke> strokes = new ArrayList<>();

        public String plainText() {
            StringBuilder sb = new StringBuilder(title).append('\n');
            for (Block b : blocks) sb.append(b.text).append('\n');
            return sb.toString();
        }
        public String displayTitle() {
            if (!title.trim().isEmpty()) return title.trim();
            for (Block b : blocks) if (!b.text.trim().isEmpty()) return b.text.trim();
            return "Senza titolo";
        }
        JSONObject json() throws JSONException {
            JSONObject o = new JSONObject().put("id", id).put("nb", notebookId).put("title", title)
                .put("created", created).put("updated", updated).put("pinned", pinned).put("extra", extra).put("style", style.json());
            JSONArray b = new JSONArray(); for (Block x : blocks) b.put(x.json()); o.put("blocks", b);
            JSONArray s = new JSONArray(); for (Stroke x : strokes) s.put(x.json()); o.put("strokes", s);
            return o;
        }
        static Page from(JSONObject o) {
            Page p = new Page();
            p.id = o.optString("id", p.id); p.notebookId = o.optString("nb", ""); p.title = o.optString("title", "");
            p.created = o.optLong("created", p.created); p.updated = o.optLong("updated", p.created);
            p.pinned = o.optBoolean("pinned"); p.extra = o.optInt("extra"); p.style = Style.from(o.optJSONObject("style"));
            JSONArray b = o.optJSONArray("blocks");
            if (b != null) for (int i = 0; i < b.length(); i++) { JSONObject x = b.optJSONObject(i); if (x != null) p.blocks.add(Block.from(x)); }
            JSONArray s = o.optJSONArray("strokes");
            if (s != null) for (int i = 0; i < s.length(); i++) { JSONObject x = s.optJSONObject(i); if (x != null) p.strokes.add(Stroke.from(x)); }
            if (p.blocks.isEmpty()) p.blocks.add(new Block());
            return p;
        }
    }

    /* ================================================================ everything */
    public static final class Data {
        public List<Notebook> notebooks = new ArrayList<>();
        public List<Page> pages = new ArrayList<>();
        public String current = "";
        public Style defaults = new Style();

        public Page page(String id) { for (Page p : pages) if (p.id.equals(id)) return p; return null; }
        public Notebook notebook(String id) { for (Notebook n : notebooks) if (n.id.equals(id)) return n; return null; }
        public List<Page> pagesOf(String nb) {
            List<Page> out = new ArrayList<>();
            for (Page p : pages) if (p.notebookId.equals(nb) && p.pinned) out.add(p);
            for (Page p : pages) if (p.notebookId.equals(nb) && !p.pinned) out.add(p);
            return out;
        }
        public JSONObject json() throws JSONException {
            JSONObject o = new JSONObject().put("app", "AmpyPad").put("version", 1).put("current", current).put("defaults", defaults.json());
            JSONArray n = new JSONArray(); for (Notebook x : notebooks) n.put(x.json()); o.put("notebooks", n);
            JSONArray p = new JSONArray(); for (Page x : pages) p.put(x.json()); o.put("pages", p);
            return o;
        }
        public static Data from(JSONObject o) {
            Data d = new Data();
            d.current = o.optString("current", "");
            d.defaults = Style.from(o.optJSONObject("defaults"));
            JSONArray n = o.optJSONArray("notebooks");
            if (n != null) for (int i = 0; i < n.length(); i++) { JSONObject x = n.optJSONObject(i); if (x != null) d.notebooks.add(Notebook.from(x)); }
            JSONArray p = o.optJSONArray("pages");
            if (p != null) for (int i = 0; i < p.length(); i++) { JSONObject x = p.optJSONObject(i); if (x != null) d.pages.add(Page.from(x)); }
            return d;
        }
    }
}
