package it.ampy.ampypad;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** All persistent settings (same pattern as AmpyCam's CamPrefs). */
public final class Prefs {
    private final SharedPreferences sp;
    private Prefs(Context c) { sp = c.getApplicationContext().getSharedPreferences("ampypad", Context.MODE_PRIVATE); }
    public static Prefs get(Context c) { return new Prefs(c); }

    public String s(String k, String d) { return sp.getString(k, d); }
    public int i(String k, int d) { return sp.getInt(k, d); }
    public float f(String k, float d) { return sp.getFloat(k, d); }
    public boolean b(String k, boolean d) { return sp.getBoolean(k, d); }
    public void put(String k, String v) { sp.edit().putString(k, v).apply(); }
    public void put(String k, int v) { sp.edit().putInt(k, v).apply(); }
    public void put(String k, float v) { sp.edit().putFloat(k, v).apply(); }
    public void put(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }

    public String theme() { return s("theme", "cyberpunk"); }
    public boolean haptics() { return b("haptics", true); }
    public boolean sound() { return b("sound", true); }

    /* quill */
    public boolean quillOn() { return b("quillOn", true); }
    public String quillIcon() { return s("quillIcon", "piuma"); }
    public boolean quillFollowStyle() { return b("quillFollow", true); }
    public int quillColor() { return i("quillColor", 0xffffd16a); }
    public String quillEffect() { return s("quillEffect", "scintille"); }
    public boolean quillTrail() { return b("quillTrail", true); }
    public int quillDelay() { return i("quillDelay", 900); }
    public boolean quillAutoBrush() { return b("quillAutoBrush", true); }
    public int quillSize() { return i("quillSize", 100); }

    public String transition() { return s("transition", "sfoglia"); }

    /** Recently picked colours, newest first (max 12). */
    public List<Integer> recent() {
        List<Integer> out = new ArrayList<>();
        String raw = s("recent", "");
        if (raw.isEmpty()) return out;
        for (String t : raw.split(",")) {
            try { out.add((int) Long.parseLong(t, 16)); } catch (Exception ignored) { }
        }
        return out;
    }
    public void pushRecent(int c) {
        List<Integer> r = recent();
        r.remove(Integer.valueOf(c));
        r.add(0, c);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(12, r.size()); i++) {
            if (i > 0) sb.append(',');
            sb.append(Long.toHexString(r.get(i) & 0xffffffffL));
        }
        put("recent", sb.toString());
    }
}
