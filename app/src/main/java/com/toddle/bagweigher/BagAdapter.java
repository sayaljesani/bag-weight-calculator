package com.toddle.bagweigher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Newest-first list of the bags weighed so far. Tap a row to correct it. */
public class BagAdapter extends RecyclerView.Adapter<BagAdapter.VH> {

    public interface Listener {
        void onEditBag(int index);
    }

    private final WeighSession session;
    private final Listener listener;

    public BagAdapter(WeighSession session, Listener listener) {
        this.session = session;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bag, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final int index = getItemCount() - 1 - position;   // newest bag first
        double gross = session.grossWeights.get(index).doubleValue();
        double net = session.netOf(index);

        holder.no.setText(String.valueOf(index + 1));
        holder.gross.setText(EntryActivity.fmt(gross));
        holder.net.setText(EntryActivity.fmt(net));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onEditBag(index);
            }
        });
    }

    @Override
    public int getItemCount() {
        return session.weighedBags();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView no, gross, net;

        VH(View itemView) {
            super(itemView);
            no = (TextView) itemView.findViewById(R.id.tv_no);
            gross = (TextView) itemView.findViewById(R.id.tv_gross);
            net = (TextView) itemView.findViewById(R.id.tv_net);
        }
    }
}
