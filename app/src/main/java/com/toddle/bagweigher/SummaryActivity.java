package com.toddle.bagweigher;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.toddle.bagweigher.xlsx.BagSheetGenerator;

import java.io.ByteArrayOutputStream;

/**
 * Totals for one load, plus what the sheet is for: saving it to Downloads and
 * sharing it. Reached from the weighing screen and from Past loads, so it also
 * carries edit / duplicate / delete.
 */
public class SummaryActivity extends AppCompatActivity {

    public static final String EXTRA_LOAD_ID = "load_id";
    private static final int REQ_WRITE = 42;

    private WeighSession session;
    private byte[] workbook;
    private boolean saveAfterPermission;

    public static void open(Context ctx, String loadId) {
        Intent i = new Intent(ctx, SummaryActivity.class);
        i.putExtra(EXTRA_LOAD_ID, loadId);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);
        WindowPadding.apply(findViewById(R.id.root));

        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveWithPermission(); }
        });
        findViewById(R.id.btn_share).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { share(); }
        });
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { continueEditing(); }
        });
        findViewById(R.id.btn_duplicate).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { duplicate(); }
        });
        findViewById(R.id.btn_delete).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmDelete(); }
        });
        findViewById(R.id.btn_history).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toHistory(); }
        });
        findViewById(R.id.btn_new_load).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toHome(); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        session = LoadStore.get(this, getIntent().getStringExtra(EXTRA_LOAD_ID));
        if (session == null || session.weighedBags() == 0) {
            Toast.makeText(this, R.string.no_session, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        workbook = null;   // the load may have been edited since we were last shown
        render();
    }

    private void render() {
        ((TextView) findViewById(R.id.tv_sum_header)).setText(session.title());
        ((TextView) findViewById(R.id.tv_sum_bags)).setText(getString(R.string.sum_bags,
                Integer.valueOf(session.weighedBags()), Integer.valueOf(session.totalBags),
                session.dateText));
        ((TextView) findViewById(R.id.tv_sum_gross)).setText(EntryActivity.fmt(session.totalGross()));
        ((TextView) findViewById(R.id.tv_sum_tare)).setText(EntryActivity.fmt(session.totalTare()));
        ((TextView) findViewById(R.id.tv_sum_net)).setText(EntryActivity.fmt(session.totalNet()));
        ((TextView) findViewById(R.id.tv_file_name)).setText(session.fileName());
    }

    /* ----------------------------------------------------------------- */

    private byte[] workbook() {
        if (workbook != null) return workbook;
        try {
            BagSheetGenerator.Params p = new BagSheetGenerator.Params();
            p.seqNo = session.seqNo > 0 ? String.valueOf(session.seqNo) : "";
            p.logoPng = readLogo();
            p.count = session.count;
            p.lotNo = session.lotNo;
            p.vehicleNo = session.vehicleNo;
            p.date = session.dateText;
            p.totalBags = session.totalBags;
            p.tarePerBag = session.tarePerBag;

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            BagSheetGenerator.write(p, session.weights(), bos);
            workbook = bos.toByteArray();
            return workbook;
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.generate_failed, e.getMessage()), Toast.LENGTH_LONG).show();
            return null;
        }
    }

    /** the company logo that goes at the top of every sheet */
    private byte[] readLogo() {
        java.io.InputStream in = null;
        try {
            in = getResources().openRawResource(R.raw.logo);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;   // a missing logo just means a plainer header
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) { }
            }
        }
    }

    private void saveWithPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            saveAfterPermission = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE);
            return;
        }
        save();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE && saveAfterPermission) {
            saveAfterPermission = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                save();
            } else {
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void save() {
        byte[] data = workbook();
        if (data == null) return;
        try {
            String where = FileExporter.saveToDownloads(this, session.fileName(),
                    FileExporter.XLSX_MIME, data);
            if (where == null) {
                Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, getString(R.string.saved_to, where), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.generate_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void share() {
        byte[] data = workbook();
        if (data == null) return;
        try {
            Uri uri = FileExporter.stageForSharing(this, session.fileName(), data);
            startActivity(Intent.createChooser(
                    FileExporter.shareIntent(uri, FileExporter.XLSX_MIME, session.fileName()),
                    getString(R.string.share_sheet)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.generate_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void continueEditing() {
        EntryActivity.open(this, session.id);
        finish();
    }

    private void duplicate() {
        WeighSession copy = session.duplicate(MainActivity.today());
        copy.seqNo = LoadStore.nextSeqNo(this, MainActivity.thisYear());
        LoadStore.save(this, copy);
        Toast.makeText(this, R.string.duplicated, Toast.LENGTH_SHORT).show();
        EntryActivity.open(this, copy.id);
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_load_title)
                .setMessage(getString(R.string.delete_load_message, session.title(), session.dateText))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        LoadStore.delete(SummaryActivity.this, session.id);
                        Toast.makeText(SummaryActivity.this, R.string.load_deleted, Toast.LENGTH_SHORT).show();
                        toHistory();
                        finish();
                    }
                })
                .show();
    }

    private void toHistory() {
        Intent i = new Intent(this, HistoryActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    private void toHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
