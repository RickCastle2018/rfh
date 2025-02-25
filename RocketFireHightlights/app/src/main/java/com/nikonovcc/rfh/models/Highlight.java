package com.nikonovcc.rfh.models;

import java.util.Date;

public class Highlight {
    private String id;
    private String userId;
    private String location;
    private Date timestamp;
    private String imageUrl;
    private int shares;
    private String achievementId;

    public Highlight(String id, String userId, String location, Date timestamp,
                     String imageUrl, int shares, String achievementId) {
        this.id = id;
        this.userId = userId;
        this.location = location;
        this.timestamp = timestamp;
        this.imageUrl = imageUrl;
        this.shares = shares;
        this.achievementId = achievementId;
    }

    // Getters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getLocation() { return location; }
    public Date getTimestamp() { return timestamp; }
    public String getImageUrl() { return imageUrl; }
    public int getShares() { return shares; }
    public String getAchievementId() { return achievementId; }
}