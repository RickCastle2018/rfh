package com.nikonovcc.rfh.adapters;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.nikonovcc.rfh.PocketbaseClient;
import com.nikonovcc.rfh.R;
import com.nikonovcc.rfh.models.Highlight;
import com.nikonovcc.rfh.utils.TimeUtils;
import java.util.ArrayList;
import java.util.List;

public class HighlightsAdapter extends RecyclerView.Adapter<HighlightsAdapter.ViewHolder> {
    private List<Highlight> highlights = new ArrayList<>();

    public List<Highlight> getHighlights() {
        return highlights;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView locationView;
        TextView timeView;
        TextView sharesView;
        View seeHighlightsButton;
        RecyclerView galleryView;

        boolean expanded = false;

        public ViewHolder(View view) {
            super(view);
            locationView = view.findViewById(R.id.highlight_location);
            timeView = view.findViewById(R.id.highlight_time);
            sharesView = view.findViewById(R.id.highlight_shares);
            seeHighlightsButton = view.findViewById(R.id.see_highlights_button);
            galleryView = view.findViewById(R.id.highlight_gallery);
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
        holder.sharesView.setText(highlight.getShares() + " highlights shared");

// Handle see highlights click
        holder.seeHighlightsButton.setOnClickListener(v -> {
            Log.d("GalleryClick", "See highlights clicked for alertId: " + highlight.getAlertId());
            if (holder.expanded) {
                holder.galleryView.setVisibility(View.GONE);
                holder.expanded = false;
            } else {
                // Fetch all highlights with same alert ID
                PocketbaseClient client = new PocketbaseClient(v.getContext(), "https://rocket-fire-highlights.pockethost.io");

                try {
                    int alertId = Integer.parseInt(highlight.getAlertId());

                    client.getHighlights(new PocketbaseClient.ApiCallback<List<Highlight>>() {
                        @Override
                        public void onSuccess(List<Highlight> allHighlights) {
                            Log.d("GalleryLoad", "Loaded highlights: " + allHighlights.size());
                            List<String> imageUrls = new ArrayList<>();
                            for (Highlight h : allHighlights) {
                                Log.d("IMGURL", h.getImageUrl());
                                if (h.getAlertId().equals(highlight.getAlertId()) && h.getImageUrl() != null && !h.getImageUrl().isEmpty()) {
                                    imageUrls.add(h.getImageUrl());
                                }
                            }

                            // 💡 Fix: run UI updates on the main thread
                            ((Activity) v.getContext()).runOnUiThread(() -> {
                                holder.galleryView.setLayoutManager(new LinearLayoutManager(v.getContext(), LinearLayoutManager.HORIZONTAL, false));
                                holder.galleryView.setAdapter(new GalleryAdapter(imageUrls));
                                holder.galleryView.setVisibility(View.VISIBLE);
                                holder.expanded = true;
                            });
                        }

                        @Override
                        public void onFailure(Exception e) {
                            Log.e("GalleryLoad", "Failed to load gallery: " + e.getMessage());
                        }
                    });

                } catch (NumberFormatException e) {
                    Log.e("GalleryLoad", "Invalid alertId");
                }
            }
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

    public void addHighlight(Highlight highlight) {
        for (Highlight h : highlights) {
            if (h.getAlertId() != null && h.getAlertId().equals(highlight.getAlertId())) {
                return; // Already exists based on alertId
            }
        }

        highlights.add(0, highlight);
        notifyItemInserted(0);
    }

}