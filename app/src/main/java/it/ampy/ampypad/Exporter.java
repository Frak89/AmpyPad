package it.ampy.ampypad;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** PNG (Pictures/AmpyPad) and PDF (Download/AmpyPad) export. PDF keeps text as vectors. */
public final class Exporter {
    private Exporter() { }
    public static final int A4_W = 595, A4_H = 842;

    public static String fileName(String title, String ext) {
        String base = title == null ? "" : title.replaceAll("[^\\p{L}\\p{N} _-]", "").trim().replace(' ', '_');
        if (base.length() > 40) base = base.substring(0, 40);
        if (base.isEmpty()) base = "AmpyPad";
        return base + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "." + ext;
    }

    /** Draws the whole sheet (paper + text + ink) into a bitmap. */
    public static Bitmap render(SheetView sheet, float scale) {
        int w = Math.max(1, Math.round(sheet.getWidth() * scale)), h = Math.max(1, Math.round(sheet.getHeight() * scale));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(scale, scale);
        sheet.draw(c);
        return b;
    }

    /** Appends the sheet to a PDF as A4 pages (long sheets are split). Returns pages added. */
    public static int addToPdf(PdfDocument doc, SheetView sheet, int startNo) {
        float scale = A4_W / (float) sheet.getWidth();
        float pageH = A4_H / scale;
        int n = 0;
        for (float top = 0; top < sheet.getHeight() - 2; top += pageH) {
            PdfDocument.Page pg = doc.startPage(new PdfDocument.PageInfo.Builder(A4_W, A4_H, startNo + n).create());
            Canvas c = pg.getCanvas();
            c.drawColor(sheet.style.paper);
            c.scale(scale, scale);
            c.translate(0, -top);
            c.clipRect(0, top, sheet.getWidth(), top + pageH);
            sheet.draw(c);
            doc.finishPage(pg);
            n++;
        }
        return n;
    }

    public static Uri savePng(Context ctx, Bitmap b, String name) throws IOException {
        ContentResolver cr = ctx.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
        Uri coll;
        if (Build.VERSION.SDK_INT >= 29) {
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AmpyPad");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            coll = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AmpyPad");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("cartella non creata");
            v.put(MediaStore.MediaColumns.DATA, new File(dir, name).getAbsolutePath());
            coll = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        Uri u = cr.insert(coll, v);
        if (u == null) throw new IOException("MediaStore insert fallito");
        try (OutputStream o = cr.openOutputStream(u)) {
            if (o == null) throw new IOException("stream nullo");
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
        if (Build.VERSION.SDK_INT >= 29) { ContentValues d = new ContentValues(); d.put(MediaStore.MediaColumns.IS_PENDING, 0); cr.update(u, d, null, null); }
        return u;
    }

    public static Uri savePdf(Context ctx, PdfDocument doc, String name) throws IOException {
        ContentResolver cr = ctx.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
        Uri coll;
        if (Build.VERSION.SDK_INT >= 29) {
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AmpyPad");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            coll = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AmpyPad");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("cartella non creata");
            v.put(MediaStore.MediaColumns.DATA, new File(dir, name).getAbsolutePath());
            coll = MediaStore.Files.getContentUri("external");
        }
        Uri u = cr.insert(coll, v);
        if (u == null) throw new IOException("MediaStore insert fallito");
        try (OutputStream o = cr.openOutputStream(u)) {
            if (o == null) throw new IOException("stream nullo");
            doc.writeTo(o);
        }
        if (Build.VERSION.SDK_INT >= 29) { ContentValues d = new ContentValues(); d.put(MediaStore.MediaColumns.IS_PENDING, 0); cr.update(u, d, null, null); }
        return u;
    }
}
