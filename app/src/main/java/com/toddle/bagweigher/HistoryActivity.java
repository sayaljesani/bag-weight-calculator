package com.toddle.bagweigher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.InputStream;
import java.util.ArrayList;

/**
 * Every load ever weighed on this phone: open one to edit or re-send it, or
 * back the whole lot up to a file that can be restored on another phone.
 */
public class HistoryActivity extends AppCompatActivity implements LoadAdapter.Listener {

    private static final int REQ_PICK_BACKUP = 71;

    private final ArrayList<WeighSession> loads = new ArrayList<WeighSession>();
    private LoadAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        tvEmpty = (TextView) findViewById(R.id.tv_empty);
        RecyclerView list = (RecyclerView) findViewById(R.id.list_loads);
        adapter = new LoadAdapter(loads, this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.btn_backup).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { backup(); }
        });
        findViewById(R.id.btn_restore).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickBackup(); }
        });
        findViewById(R.id.btn_close).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        loads.clear();
        loads.addAll(LoadStore.all(this));
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(loads.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /* ----------------------------------------------------------------- */

    @Override
    public void onOpenLoad(WeighSession load) {
        if (load.weighedBags() == 0) {
            EntryActivity.open(this, load.id);
        } else {
            SummaryActivity.open(this, load.id);
        }
    }

    @Override
    public void onDeleteLoad(final WeighSession load) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_load_title)
                .setMessage(getString(R.string.delete_load_message, load.title(), load.dateText))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        LoadStore.delete(HistoryActivity.this, load.id);
                        Toast.makeText(HistoryActivity.this, R.string.load_deleted, Toast.LENGTH_SHORT).show();
                        reload();
                    }
                })
                .show();
    }

    /* ----------------------------------------------------------------- */
    /* backup / restore                                                   */
    /* ----------------------------------------------------------------- */

    private void backup() {
        if (loads.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_backup, Toast.LENGTH_SHORT).show();
            return;
        }
        byte[] data = LoadStore.backupBytes(this);
        String name = LoadStore.backupFileName();
        try {
            String where = FileExporter.saveToDownloads(this, name, FileExporter.JSON_MIME, data);
            Uri uri = FileExporter.stageForSharing(this, name, data);
            Toast.makeText(this, getString(R.string.backup_saved,
                    Integer.valueOf(loads.size()), where == null ? name : where), Toast.LENGTH_LONG).show();
            startActivity(Intent.createChooser(
                    FileExporter.shareIntent(uri, FileExporter.JSON_MIME, name),
                    getString(R.string.share_backup)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.generate_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void pickBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(Intent.createChooser(i, getString(R.string.pick_backup)), REQ_PICK_BACKUP);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_file_picker, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_BACKUP || data == null || data.getData() == null) return;

        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(data.getData());
            if (in == null) {
                Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_LONG).show();
                return;
            }
            int[] result = LoadStore.restore(this, in);
            if (result == null) {
                Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, getString(R.string.restored,
                    Integer.valueOf(result[0]), Integer.valueOf(result[1])), Toast.LENGTH_LONG).show();
            reload();
        } catch (Exception e) {
            Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_LONG).show();
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) { }
            }
        }
    }
}
