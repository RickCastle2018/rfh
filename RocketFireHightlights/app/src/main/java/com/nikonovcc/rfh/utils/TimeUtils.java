package com.nikonovcc.rfh.utils;

import java.util.Date;

public class TimeUtils {
    public static String getTimeAgo(Date date) {
        long timeInMillis = date.getTime();
        long now = System.currentTimeMillis();
        long diff = now - timeInMillis;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + " days ago";
        } else if (hours > 0) {
            return hours + " hours ago";
        } else if (minutes > 0) {
            return minutes + " min ago";
        } else {
            return "Just now";
        }
    }
}