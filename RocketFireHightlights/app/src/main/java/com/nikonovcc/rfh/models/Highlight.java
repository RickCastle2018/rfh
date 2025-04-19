package com.nikonovcc.rfh.models;

import android.app.Activity;
import android.util.Log;

import com.nikonovcc.rfh.utils.GeoCityGetter;

import java.util.Date;
import java.util.List;

public class Highlight {
    private String id;
    private String userId;
    private String location;
    private Date timestamp;
    private String imageUrl;
    private int shares;
    private String achievementId;
    private String alertId;

    public interface HighlightCallback {
        void onHighlightReady(Highlight highlight);
    }

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
    public void setShares(int shares) {
        this.shares = shares;
    }

    public static Highlight fromAlertGroup(AlertGroup group, String location) {
        if (group.alerts == null || group.alerts.isEmpty()) return null;

        com.nikonovcc.rfh.models.Alert alert = group.alerts.get(0);
//        String location = (alert.cities != null && !alert.cities.isEmpty()) ? alert.cities.get(0) : "Unknown";

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

    public static void createHighlightWithGeo(Activity activity, AlertGroup group, HighlightCallback callback) {
        GeoCityGetter.getUserCity(activity, new GeoCityGetter.CityCallback() {
            @Override
            public void onCityFound(String userCity) {
                Log.d("USERCITY", userCity);
                String finalCity = "Unknown";

                if (group.alerts != null && !group.alerts.isEmpty()) {
                    List<String> cities = group.alerts.get(0).cities;

                    if (cities != null && !cities.isEmpty()) {
                        // Try to find a match (case-insensitive, trims whitespace)
                        for (String alertCity : cities) {
                            if (alertCity != null && alertCity.trim().equalsIgnoreCase(userCity.trim())) {
                                finalCity = alertCity;
                                break;
                            }
                        }

                        // If no match, fallback to first city in alert
                        if (finalCity.equals("Unknown")) {
                            String displayLocation = cities.get(0); // primary
                            if (cities.size() > 1) {
                                displayLocation += ", " + cities.get(1); // optional second
                            }
                            if (cities.size() > 2) {
                                displayLocation += " +" + (cities.size() - 2) + " more";
                            };
                            finalCity = displayLocation;
                        }
                    }
                }

                Highlight highlight = Highlight.fromAlertGroup(group, finalCity);
                callback.onHighlightReady(highlight);
            }

            @Override
            public void onError(String error) {
                // Use first city from alert, or fallback
                String fallbackCity = "Unknown";
                if (group.alerts != null && !group.alerts.isEmpty()) {
                    List<String> cities = group.alerts.get(0).cities;
                    if (cities != null && !cities.isEmpty()) {
                        fallbackCity = cities.get(0);
                    }
                }

                Highlight highlight = Highlight.fromAlertGroup(group, fallbackCity);
                callback.onHighlightReady(highlight);
            }
        });
    }



}