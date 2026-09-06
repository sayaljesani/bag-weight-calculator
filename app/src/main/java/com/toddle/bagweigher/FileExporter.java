package com.toddle.bagweigher;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Saving the generated workbook to the phone's Downloads folder and handing it
 * to WhatsApp / Gmail / Drive through the system share sheet.
 */
public final class FileExporter {

    public static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String JSON_MIME = "application/json";

    private FileExporter() { }

    /**
     * Writes the file into the public Downloads folder.
     *
     * @return a human-readable location, or null if it could not be written
     */
    public static String saveToDownloads(Context ctx, String fileName, String mimeType, byte[] data) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = ctx.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, Integer.valueOf(1));

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return null;

            OutputStream os = null;
            try {
                os = resolver.openOutputStream(uri);
                if (os == null) return null;
                os.write(data);
                os.flush();
            } finally {
                if (os != null) {
                    try { os.close(); } catch (IOException ignored) { }
                }
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, Integer.valueOf(0));
            resolver.update(uri, values, null, null);
            return "Downloads/" + fileName;
        }

        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dir != null && !dir.exists()) {
            // noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        File out = new File(dir, fileName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out);
            fos.write(data);
            fos.flush();
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) { }
            }
        }
        return out.getAbsolutePath();
    }

    /** Writes to app cache and returns a content:// uri other apps can read. */
    public static Uri stageForSharing(Context ctx, String fileName, byte[] data) throws IOException {
        File dir = new File(ctx.getCacheDir(), "shared");
        if (!dir.exists()) {
            // noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        File f = new File(dir, fileName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            fos.write(data);
            fos.flush();
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) { }
            }
        }
        return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
    }

    public static Intent shareIntent(Uri uri, String mimeType, String subject) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mimeType);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_SUBJECT, subject);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return share;
    }

    public static Intent openIntent(Uri uri) {
        Intent open = new Intent(Intent.ACTION_VIEW);
        open.setDataAndType(uri, XLSX_MIME);
        open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return open;
    }
}
