package com.nikonovcc.rfh.models;

import android.util.Log;

import java.util.Date;

public class Highlight {
    private String id;
    private String userId;
    private String location;
    private Date timestamp;
    private String imageUrl;
    private int shares;
    private String achievementId;
    private String alertId;

    public Highlight(String id, String userId, String location, Date timestamp,
                     String imageUrl, int shares, String achievementId, String alertId) {
        this.id = id;
        this.userId = userId;
        this.location = location;
        this.timestamp = timestamp;
        this.imageUrl = imageUrl;
        this.shares = shares;
        this.achievementId = achievementId;
        this.alertId = alertId;
    }

    // Getters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getLocation() { return location; }
    public Date getTimestamp() { return timestamp; }
    public String getImageUrl() { return imageUrl; }
    public int getShares() { return shares; }
    public String getAchievementId() { return achievementId; }
    public String getAlertId() { return alertId; }

    public static Highlight fromAlertGroup(AlertGroup group) {
        if (group.alerts == null || group.alerts.isEmpty()) return null;

        com.nikonovcc.rfh.models.Alert alert = group.alerts.get(0);
        String location = (alert.cities != null && !alert.cities.isEmpty()) ? alert.cities.get(0) : "Unknown";

        Log.d("Highlight", "Creating highlight from alert group: " + group.id);
        Log.d("Highlight", "Alert time: " + alert.time);

        return new Highlight(
                "preview",  // fake ID for display only
                "anon",     // no user ID
                location,
                new java.util.Date(alert.time * 1000L),
                "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f0/Red_circle_with_white_border.svg/768px-Red_circle_with_white_border.svg.png",
                0,
                "DefaultAchievementId",
                String.valueOf(group.id)
        );
    }

}