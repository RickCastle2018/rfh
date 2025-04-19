package com.nikonovcc.rfh.models;

public class Achievement {
    private String id;
    private String title;
    private String description;
    private String iconUrl;
    private String type;
    private int count;

    public Achievement(String id, String title, String description, String iconUrl, String type, int count) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.iconUrl = iconUrl;
        this.type = type;
        this.count = count;
    }

    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getIconUrl() { return iconUrl; }
    public String getType() { return type; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count;}
}