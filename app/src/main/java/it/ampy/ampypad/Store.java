package it.ampy.ampypad;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Everything lives in one JSON file in the app's private storage (atomic writes). */
public final class Store {
    private Store() { }
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final String FILE = "ampypad.json";

    public static Model.Data load(Context c) {
        File f = new File(c.getFilesDir(), FILE);
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                Model.Data d = Model.Data.from(new JSONObject(readAll(in)));
                if (!d.pages.isEmpty() && !d.notebooks.isEmpty()) return d;
            } catch (Exception ignored) { }
        }
        return welcome();
    }

    public static String readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384]; int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void save(Context c, Model.Data d) {
        final String json;
        try { json = d.json().toString(); } catch (Exception e) { return; }
        final File dir = c.getFilesDir();
        IO.execute(() -> write(dir, json));
    }

    private static void write(File dir, String json) {
        File tmp = new File(dir, FILE + ".tmp"), dst = new File(dir, FILE);
        try (OutputStream o = new FileOutputStream(tmp)) {
            o.write(json.getBytes(StandardCharsets.UTF_8));
            o.flush();
        } catch (Exception e) { return; }
        if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst); }
    }

    public static Model.Page newPage(Model.Data d, String notebookId) {
        Model.Page p = new Model.Page();
        p.notebookId = notebookId;
        p.style = d.defaults.copy();
        p.blocks.add(new Model.Block());
        return p;
    }

    private static Model.Data welcome() {
        Model.Data d = new Model.Data();
        Model.Notebook nb = new Model.Notebook();
        nb.name = "Il mio quaderno";
        d.notebooks.add(nb);
        PaperStyle.preset("quaderno").apply(d.defaults);
        Model.Page p = new Model.Page();
        p.notebookId = nb.id;
        p.style = d.defaults.copy();
        p.title = "Benvenuto in AmpyPad";
        p.blocks.add(new Model.Block("p", "Scrivi, disegna e dipingi sullo stesso foglio. Mentre scrivi la piuma ti segue; quando ti fermi si posa sul foglio e svanisce."));
        p.blocks.add(new Model.Block("h2", "Come si usa"));
        p.blocks.add(new Model.Block("todo", "In basso scegli Aa TESTO oppure ✎ PENNELLO"));
        p.blocks.add(new Model.Block("todo", "Scrivi / su una riga vuota per scegliere un blocco"));
        p.blocks.add(new Model.Block("todo", "Tieni premuto ⠿ a sinistra di un blocco e trascinalo per riordinare"));
        p.blocks.add(new Model.Block("todo", "Pennello: due dita per scorrere, tieni fermo il dito per una linea dritta"));
        Model.Block c = new Model.Block("callout", "Scorciatoie: # titolo · - elenco · 1. numeri · [] da fare · > citazione · ! riquadro · --- separatore");
        p.blocks.add(c);
        p.blocks.add(new Model.Block("quote", "Le idee migliori nascono a mano libera."));
        d.pages.add(p);
        d.current = p.id;
        return d;
    }
}
