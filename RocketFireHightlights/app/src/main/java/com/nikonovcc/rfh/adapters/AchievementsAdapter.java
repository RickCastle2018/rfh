package com.nikonovcc.rfh.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.nikonovcc.rfh.R;
import com.nikonovcc.rfh.models.Achievement;
import java.util.ArrayList;
import java.util.List;

public class AchievementsAdapter extends RecyclerView.Adapter<AchievementsAdapter.ViewHolder> {
    private List<Achievement> achievements = new ArrayList<>();

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView iconView;
        TextView titleView;
        TextView countView;

        public ViewHolder(View view) {
            super(view);
            iconView = view.findViewById(R.id.achievement_icon);
            titleView = view.findViewById(R.id.achievement_title);
            countView = view.findViewById(R.id.achievement_count);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_achievement, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Achievement achievement = achievements.get(position);
        holder.titleView.setText(achievement.getTitle());
        holder.countView.setText(achievement.getCount() + "x");
        
        Glide.with(holder.iconView.getContext())
                .load(achievement.getIconUrl())
                .circleCrop()
                .into(holder.iconView);
    }

    @Override
    public int getItemCount() {
        return achievements.size();
    }

    public void setAchievements(List<Achievement> achievements) {
        this.achievements = achievements;
        notifyDataSetChanged();
    }
}