package com.toddle.bagweigher;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;

/**
 * Weighing screen: one gross weight per bag. Net is computed live and the
 * load is written to the phone after every entry.
 */
public class EntryActivity extends AppCompatActivity implements BagAdapter.Listener {

    public static final String EXTRA_LOAD_ID = "load_id";

    private WeighSession session;
    private EditText etGross;
    private TextView tvBagNo, tvHeader, tvLastNet, tvTotals;
    private RecyclerView list;
    private BagAdapter adapter;
    private Button btnFinish;

    public static void open(Context ctx, String loadId) {
        Intent i = new Intent(ctx, EntryActivity.class);
        i.putExtra(EXTRA_LOAD_ID, loadId);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry);

        session = LoadStore.get(this, getIntent().getStringExtra(EXTRA_LOAD_ID));
        if (session == null || session.totalBags <= 0) {
            Toast.makeText(this, R.string.no_session, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        tvHeader = (TextView) findViewById(R.id.tv_header);
        tvBagNo = (TextView) findViewById(R.id.tv_bag_no);
        tvLastNet = (TextView) findViewById(R.id.tv_last_net);
        tvTotals = (TextView) findViewById(R.id.tv_totals);
        etGross = (EditText) findViewById(R.id.et_gross);
        btnFinish = (Button) findViewById(R.id.btn_finish);
        list = (RecyclerView) findViewById(R.id.list_bags);

        adapter = new BagAdapter(session, this);
        list.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        list.setAdapter(adapter);

        findViewById(R.id.btn_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addWeight();
            }
        });

        findViewById(R.id.btn_undo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undoLast();
            }
        });

        btnFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishLoad();
            }
        });

        etGross.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT
                        || actionId == EditorInfo.IME_ACTION_GO
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    addWeight();
                    return true;
                }
                return false;
            }
        });

        etGross.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { showPreview(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });

        refresh();
    }

    /* ----------------------------------------------------------------- */

    private void showPreview(String text) {
        Double d = parse(text);
        if (d == null) {
            tvLastNet.setText("");
            return;
        }
        double net = WeighSession.round(d.doubleValue() - session.tarePerBag);
        tvLastNet.setText(getString(R.string.preview_net,
                fmt(d.doubleValue()), fmt(session.tarePerBag), fmt(net)));
    }

    private void addWeight() {
        String text = etGross.getText().toString().trim();
        Double d = parse(text);
        if (d == null) {
            etGross.setError(getString(R.string.enter_weight));
            return;
        }
        if (d.doubleValue() <= 0) {
            etGross.setError(getString(R.string.invalid_weight));
            return;
        }
        if (session.weighedBags() >= session.totalBags) {
            Toast.makeText(this, R.string.all_bags_done, Toast.LENGTH_LONG).show();
            return;
        }
        if (d.doubleValue() < session.tarePerBag) {
            Toast.makeText(this, R.string.below_tare, Toast.LENGTH_SHORT).show();
        }

        session.grossWeights.add(d);
        LoadStore.save(this, session);
        etGross.setText("");
        adapter.notifyDataSetChanged();
        list.scrollToPosition(0);
        refresh();

        if (session.isComplete()) {
            Toast.makeText(this, R.string.all_bags_done, Toast.LENGTH_LONG).show();
        }
    }

    private void undoLast() {
        if (session.weighedBags() == 0) return;
        session.grossWeights.remove(session.weighedBags() - 1);
        LoadStore.save(this, session);
        adapter.notifyDataSetChanged();
        refresh();
    }

    @Override
    public void onEditBag(final int index) {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(fmt(session.grossWeights.get(index).doubleValue()));
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.edit_bag, Integer.valueOf(index + 1)))
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.delete, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        session.grossWeights.remove(index);
                        LoadStore.save(EntryActivity.this, session);
                        adapter.notifyDataSetChanged();
                        refresh();
                    }
                })
                .setPositiveButton(R.string.save, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Double nv = parse(input.getText().toString());
                        if (nv == null || nv.doubleValue() <= 0) {
                            Toast.makeText(EntryActivity.this, R.string.invalid_weight, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        session.grossWeights.set(index, nv);
                        LoadStore.save(EntryActivity.this, session);
                        adapter.notifyDataSetChanged();
                        refresh();
                    }
                })
                .show();
    }

    private void finishLoad() {
        if (session.weighedBags() == 0) {
            Toast.makeText(this, R.string.nothing_to_export, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!session.isComplete()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.incomplete_title)
                    .setMessage(getString(R.string.incomplete_message,
                            Integer.valueOf(session.weighedBags()), Integer.valueOf(session.totalBags)))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.continue_anyway, new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            toSummary();
                        }
                    })
                    .show();
            return;
        }
        toSummary();
    }

    private void toSummary() {
        session.finished = true;
        LoadStore.save(this, session);
        SummaryActivity.open(this, session.id);
        finish();
    }

    private void refresh() {
        tvHeader.setText(session.title());

        int next = Math.min(session.weighedBags() + 1, session.totalBags);
        tvBagNo.setText(getString(R.string.bag_x_of_y,
                Integer.valueOf(next), Integer.valueOf(session.totalBags)));

        tvTotals.setText(getString(R.string.totals_line,
                Integer.valueOf(session.weighedBags()), Integer.valueOf(session.totalBags),
                fmt(session.totalGross()), fmt(session.totalTare()), fmt(session.totalNet())));

        btnFinish.setText(session.isComplete()
                ? getString(R.string.generate_sheet)
                : getString(R.string.finish_early));
        etGross.requestFocus();
    }

    static Double parse(String text) {
        if (TextUtils.isEmpty(text)) return null;
        String t = text.trim().replace(',', '.');
        try {
            return Double.valueOf(Double.parseDouble(t));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String fmt(double v) {
        return String.format(Locale.US, "%.2f", Double.valueOf(v));
    }
}
