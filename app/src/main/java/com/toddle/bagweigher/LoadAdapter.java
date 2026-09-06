package com.toddle.bagweigher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** The Past loads list: newest first, tap to open, ✕ to delete. */
public class LoadAdapter extends RecyclerView.Adapter<LoadAdapter.VH> {

    public interface Listener {
        void onOpenLoad(WeighSession load);
        void onDeleteLoad(WeighSession load);
    }

    private final List<WeighSession> loads;
    private final Listener listener;

    public LoadAdapter(List<WeighSession> loads, Listener listener) {
        this.loads = loads;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_load, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final WeighSession l = loads.get(position);
        View item = holder.itemView;

        holder.title.setText(l.title());

        boolean done = l.finished && l.isComplete();
        holder.status.setText(done ? R.string.status_completed : R.string.status_in_progress);
        holder.status.setBackgroundResource(done ? R.drawable.chip_done : R.drawable.chip_open);

        holder.meta.setText(item.getContext().getString(R.string.load_meta,
                l.dateText, Integer.valueOf(l.weighedBags()), Integer.valueOf(l.totalBags),
                EntryActivity.fmt(l.tarePerBag)));

        holder.net.setText(item.getContext().getString(R.string.load_net,
                EntryActivity.fmt(l.totalNet()), EntryActivity.fmt(l.totalGross())));

        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onOpenLoad(l);
            }
        });
        holder.delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onDeleteLoad(l);
            }
        });
    }

    @Override
    public int getItemCount() {
        return loads.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, status, meta, net, delete;

        VH(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.tv_load_title);
            status = (TextView) itemView.findViewById(R.id.tv_load_status);
            meta = (TextView) itemView.findViewById(R.id.tv_load_meta);
            net = (TextView) itemView.findViewById(R.id.tv_load_net);
            delete = (TextView) itemView.findViewById(R.id.tv_load_delete);
        }
    }
}
