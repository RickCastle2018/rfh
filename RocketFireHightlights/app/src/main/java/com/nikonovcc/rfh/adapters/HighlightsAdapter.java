package com.nikonovcc.rfh.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.nikonovcc.rfh.R;
import com.nikonovcc.rfh.models.Highlight;
import com.nikonovcc.rfh.utils.TimeUtils;
import java.util.ArrayList;
import java.util.List;

public class HighlightsAdapter extends RecyclerView.Adapter<HighlightsAdapter.ViewHolder> {
    private List<Highlight> highlights = new ArrayList<>();

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView locationView;
        TextView timeView;
        TextView sharesView;
        ImageView imageView;
        View seeHighlightsButton;

        public ViewHolder(View view) {
            super(view);
            locationView = view.findViewById(R.id.highlight_location);
            timeView = view.findViewById(R.id.highlight_time);
            sharesView = view.findViewById(R.id.highlight_shares);
            imageView = view.findViewById(R.id.highlight_image);
            seeHighlightsButton = view.findViewById(R.id.see_highlights_button);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_highlight, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Highlight highlight = highlights.get(position);
        holder.locationView.setText("Siren in " + highlight.getLocation());
        holder.timeView.setText(TimeUtils.getTimeAgo(highlight.getTimestamp()));
        holder.sharesView.setText(highlight.getShares() + " Shares");
        
        Glide.with(holder.imageView.getContext())
                .load(highlight.getImageUrl())
                .centerCrop()
                .into(holder.imageView);

        holder.seeHighlightsButton.setOnClickListener(v -> {
            // Handle see highlights click
        });
    }

    @Override
    public int getItemCount() {
        return highlights.size();
    }

    public void setHighlights(List<Highlight> highlights) {
        this.highlights = highlights;
        notifyDataSetChanged();
    }
}