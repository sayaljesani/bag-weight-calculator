package com.toddle.bagweigher;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Load screen: the details that are entered once, before weighing starts.
 */
public class MainActivity extends AppCompatActivity {

    private EditText etSeq, etCount, etLot, etVehicle, etBags, etTare, etDate;

    public static int thisYear() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
    }

    public static String today() {
        return new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(new Date());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etSeq = (EditText) findViewById(R.id.et_seq);
        etCount = (EditText) findViewById(R.id.et_count);
        etLot = (EditText) findViewById(R.id.et_lot);
        etVehicle = (EditText) findViewById(R.id.et_vehicle);
        etBags = (EditText) findViewById(R.id.et_bags);
        etTare = (EditText) findViewById(R.id.et_tare);
        etDate = (EditText) findViewById(R.id.et_date);
        etDate.setText(today());
        etSeq.setText(String.valueOf(LoadStore.nextSeqNo(this, thisYear())));

        findViewById(R.id.btn_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startNewLoad();
            }
        });

        findViewById(R.id.btn_history).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateResumeBanner();
    }

    private void updateResumeBanner() {
        final WeighSession open = LoadStore.current(this);
        View banner = findViewById(R.id.resume_banner);
        if (open == null || open.totalBags <= 0) {
            banner.setVisibility(View.GONE);
            return;
        }
        banner.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tv_resume)).setText(getString(R.string.resume_message,
                open.title(), Integer.valueOf(open.weighedBags()), Integer.valueOf(open.totalBags)));

        ((Button) findViewById(R.id.btn_resume)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EntryActivity.open(MainActivity.this, open.id);
            }
        });
        ((Button) findViewById(R.id.btn_discard)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.discard_title)
                        .setMessage(R.string.discard_message)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.discard, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                LoadStore.delete(MainActivity.this, open.id);
                                updateResumeBanner();
                            }
                        })
                        .show();
            }
        });
    }

    private void startNewLoad() {
        String count = etCount.getText().toString().trim();
        String bagsText = etBags.getText().toString().trim();
        String tareText = etTare.getText().toString().trim();

        if (TextUtils.isEmpty(count)) {
            etCount.setError(getString(R.string.required));
            etCount.requestFocus();
            return;
        }
        int bags;
        try {
            bags = Integer.parseInt(bagsText);
        } catch (NumberFormatException e) {
            etBags.setError(getString(R.string.required));
            etBags.requestFocus();
            return;
        }
        if (bags <= 0 || bags > 100000) {
            etBags.setError(getString(R.string.invalid_bags));
            etBags.requestFocus();
            return;
        }
        double tare;
        try {
            tare = Double.parseDouble(tareText.replace(',', '.'));
        } catch (NumberFormatException e) {
            etTare.setError(getString(R.string.required));
            etTare.requestFocus();
            return;
        }
        if (tare < 0) {
            etTare.setError(getString(R.string.invalid_tare));
            etTare.requestFocus();
            return;
        }

        WeighSession s = new WeighSession();
        try {
            s.seqNo = Integer.parseInt(etSeq.getText().toString().trim());
        } catch (NumberFormatException e) {
            s.seqNo = LoadStore.nextSeqNo(this, thisYear());
        }
        s.count = count;
        s.lotNo = etLot.getText().toString().trim();
        s.vehicleNo = etVehicle.getText().toString().trim();
        String date = etDate.getText().toString().trim();
        s.dateText = TextUtils.isEmpty(date) ? today() : date;
        s.totalBags = bags;
        s.tarePerBag = tare;
        LoadStore.save(this, s);

        clearForm();
        EntryActivity.open(this, s.id);
    }

    private void clearForm() {
        etSeq.setText(String.valueOf(LoadStore.nextSeqNo(this, thisYear())));
        etCount.setText("");
        etLot.setText("");
        etVehicle.setText("");
        etBags.setText("");
        etTare.setText("");
        etDate.setText(today());
    }
}
