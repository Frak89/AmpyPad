package it.ampy.ampypad;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Minimal file provider (no AndroidX): camera output and PDF attachments live in cache/scan. */
public class ScanProvider extends ContentProvider {
    public static final String AUTH = "it.ampy.ampypad.scan";

    public static File dir(Context c) {
        File d = new File(c.getCacheDir(), "scan");
        if (!d.exists()) d.mkdirs();
        return d;
    }
    public static Uri uri(File f) { return Uri.parse("content://" + AUTH + "/" + Uri.encode(f.getName())); }

    private File file(Uri u) throws FileNotFoundException {
        String name = u.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) throw new FileNotFoundException("nome non valido");
        return new File(dir(getContext()), name);
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        int m = mode.contains("w") ? ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE
            : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(file(u), m);
    }

    @Override public Cursor query(Uri u, String[] proj, String sel, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        try { File f = file(u); c.addRow(new Object[]{f.getName(), f.length()}); } catch (FileNotFoundException ignored) { }
        return c;
    }

    @Override public String getType(Uri u) {
        String n = u.getLastPathSegment();
        return n != null && n.endsWith(".pdf") ? "application/pdf" : "image/jpeg";
    }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
