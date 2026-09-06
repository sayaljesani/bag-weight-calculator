package com.toddle.bagweigher;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Every load the phone has ever weighed, kept in one JSON file in the app's
 * private storage, plus a backup file that can be carried to another phone.
 */
public final class LoadStore {

    private static final String FILE_NAME = "loads.json";
    private static final String LEGACY_FILE = "current_session.json";

    private LoadStore() { }

    /* ----------------------------------------------------------------- */
    /* reading                                                            */
    /* ----------------------------------------------------------------- */

    /** all loads, most recently touched first */
    public static ArrayList<WeighSession> all(Context ctx) {
        return read(ctx).loads;
    }

    public static WeighSession get(Context ctx, String id) {
        if (id == null) return null;
        ArrayList<WeighSession> loads = all(ctx);
        for (int i = 0; i < loads.size(); i++) {
            if (id.equals(loads.get(i).id)) return loads.get(i);
        }
        return null;
    }

    /**
     * The number to put on the next sheet: one more than the highest used in
     * that year, so numbering restarts at 1 every January.
     */
    public static int nextSeqNo(Context ctx, int year) {
        int max = 0;
        ArrayList<WeighSession> loads = all(ctx);
        for (int i = 0; i < loads.size(); i++) {
            WeighSession l = loads.get(i);
            if (l.year() == year && l.seqNo > max) max = l.seqNo;
        }
        return max + 1;
    }

    /** the load still being weighed, or null */
    public static WeighSession current(Context ctx) {
        Data d = read(ctx);
        return d.currentId == null ? null : find(d.loads, d.currentId);
    }

    /* ----------------------------------------------------------------- */
    /* writing                                                            */
    /* ----------------------------------------------------------------- */

    /** inserts or replaces the load and marks it as the one in progress */
    public static void save(Context ctx, WeighSession s) {
        if (s == null) return;
        Data d = read(ctx);
        s.updatedAt = System.currentTimeMillis();
        replace(d.loads, s);
        d.currentId = s.finished ? null : s.id;
        write(ctx, d);
    }

    public static void delete(Context ctx, String id) {
        if (id == null) return;
        Data d = read(ctx);
        for (int i = d.loads.size() - 1; i >= 0; i--) {
            if (id.equals(d.loads.get(i).id)) d.loads.remove(i);
        }
        if (id.equals(d.currentId)) d.currentId = null;
        write(ctx, d);
    }

    /* ----------------------------------------------------------------- */
    /* backup / restore                                                   */
    /* ----------------------------------------------------------------- */

    public static byte[] backupBytes(Context ctx) {
        try {
            JSONObject root = new JSONObject();
            root.put("app", "bag-weight-calculator");
            root.put("version", 2);
            root.put("exportedAt", System.currentTimeMillis());
            JSONArray arr = new JSONArray();
            ArrayList<WeighSession> loads = all(ctx);
            for (int i = 0; i < loads.size(); i++) arr.put(loads.get(i).toJson());
            root.put("loads", arr);
            return root.toString(1).getBytes("UTF-8");
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static String backupFileName() {
        return "bag-weighments-backup-"
                + new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US)
                    .format(new java.util.Date())
                + ".json";
    }

    /**
     * Merges a backup into the store.
     *
     * @return {added, updated}, or null if the file could not be read
     */
    public static int[] restore(Context ctx, InputStream in) {
        try {
            String text = readAll(in);
            JSONObject root = new JSONObject(text);
            JSONArray arr = root.optJSONArray("loads");
            if (arr == null) return null;

            Data d = read(ctx);
            int added = 0, updated = 0;
            for (int i = 0; i < arr.length(); i++) {
                WeighSession s = WeighSession.fromJson(arr.getJSONObject(i));
                if (s.totalBags <= 0) continue;
                WeighSession existing = find(d.loads, s.id);
                if (existing == null) {
                    d.loads.add(s);
                    added++;
                } else if (s.updatedAt >= existing.updatedAt) {
                    replace(d.loads, s);
                    updated++;
                }
            }
            write(ctx, d);
            return new int[]{added, updated};
        } catch (Exception e) {
            return null;
        }
    }

    /* ----------------------------------------------------------------- */
    /* file plumbing                                                      */
    /* ----------------------------------------------------------------- */

    private static final class Data {
        String currentId;
        ArrayList<WeighSession> loads = new ArrayList<WeighSession>();
    }

    private static WeighSession find(ArrayList<WeighSession> loads, String id) {
        for (int i = 0; i < loads.size(); i++) {
            if (loads.get(i).id.equals(id)) return loads.get(i);
        }
        return null;
    }

    private static void replace(ArrayList<WeighSession> loads, WeighSession s) {
        for (int i = 0; i < loads.size(); i++) {
            if (loads.get(i).id.equals(s.id)) { loads.set(i, s); return; }
        }
        loads.add(s);
    }

    private static Data read(Context ctx) {
        Data d = new Data();
        File f = new File(ctx.getFilesDir(), FILE_NAME);
        if (f.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(f);
                JSONObject root = new JSONObject(readAll(fis));
                d.currentId = root.isNull("currentId") ? null : root.optString("currentId", null);
                JSONArray arr = root.optJSONArray("loads");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        d.loads.add(WeighSession.fromJson(arr.getJSONObject(i)));
                    }
                }
            } catch (Exception e) {
                // unreadable store: start clean rather than crash
            } finally {
                closeQuietly(fis);
            }
        } else {
            migrateLegacy(ctx, d);
        }
        sort(d.loads);
        return d;
    }

    /** picks up the single session saved by the first version of the app */
    private static void migrateLegacy(Context ctx, Data d) {
        File old = new File(ctx.getFilesDir(), LEGACY_FILE);
        if (!old.exists()) return;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(old);
            WeighSession s = WeighSession.fromJson(new JSONObject(readAll(fis)));
            if (s.totalBags > 0) {
                d.loads.add(s);
                d.currentId = s.id;
            }
        } catch (Exception e) {
            // nothing to carry over
        } finally {
            closeQuietly(fis);
            // noinspection ResultOfMethodCallIgnored
            old.delete();
        }
        write(ctx, d);
    }

    private static void write(Context ctx, Data d) {
        FileOutputStream fos = null;
        try {
            JSONObject root = new JSONObject();
            if (d.currentId != null) root.put("currentId", d.currentId);
            JSONArray arr = new JSONArray();
            for (int i = 0; i < d.loads.size(); i++) arr.put(d.loads.get(i).toJson());
            root.put("loads", arr);

            fos = ctx.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
            fos.write(root.toString().getBytes("UTF-8"));
        } catch (Exception e) {
            // best effort; the in-memory copy is still valid
        } finally {
            closeQuietly(fos);
        }
    }

    private static void sort(ArrayList<WeighSession> loads) {
        Collections.sort(loads, new Comparator<WeighSession>() {
            @Override
            public int compare(WeighSession a, WeighSession b) {
                return (b.updatedAt < a.updatedAt) ? -1 : (b.updatedAt > a.updatedAt ? 1 : 0);
            }
        });
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }
}
