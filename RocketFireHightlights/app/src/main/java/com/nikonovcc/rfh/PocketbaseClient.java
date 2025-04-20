package com.nikonovcc.rfh;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.google.gson.Gson;
import com.nikonovcc.rfh.models.Achievement;
import com.nikonovcc.rfh.models.AlertGroup;
import com.nikonovcc.rfh.models.Highlight;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.*;
import android.content.Intent;

public class PocketbaseClient {
    private final String baseUrl;
    private final OkHttpClient client;
    private String authToken;
    private final Context context;

    public PocketbaseClient(Context context, String baseUrl) {
        this.context = context.getApplicationContext(); // Safe version for storing
        this.baseUrl = baseUrl;
        this.client = new OkHttpClient();
        this.authToken = loadToken(); // Load token at init
    }

    public void setAuthToken(String token) {
        this.authToken = token;
        SharedPreferences sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("auth_token", token);
        editor.apply();
    }

    public void register(String username, String email, String password, final ApiCallback<String> callback) {
        JSONObject json = new JSONObject();
        try {
            json.put("username", username);
            json.put("email", email);
            json.put("password", password);
            json.put("passwordConfirm", password);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
        Request request = new Request.Builder()
                .url(baseUrl + "/api/collections/users/records")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess("Registration successful");
                } else {
                    callback.onFailure(new Exception("Registration failed: " + response.body().string()));
                }
            }
        });
    }

    public void login(String usernameOrEmail, String password, final ApiCallback<String> callback) {
        JSONObject json = new JSONObject();
        try {
            json.put("identity", usernameOrEmail);
            json.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
        Request request = new Request.Builder()
                .url(baseUrl + "/api/collections/users/auth-with-password")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonResponse = new JSONObject(response.body().string());
                        authToken = jsonResponse.getString("token");
                        callback.onSuccess("Login successful");
                    } catch (JSONException e) {
                        callback.onFailure(e);
                    }
                } else {
                    callback.onFailure(new Exception("Login failed: " + response.body().string()));
                }
            }
        });
    }

    public void getAchievements(final ApiCallback<List<Achievement>> callback) {
        Request achievementsRequest = new Request.Builder()
                .url(baseUrl + "/api/collections/achievements/records")
                .header("Authorization", authToken)
                .build();

        client.newCall(achievementsRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure(new Exception("Failed to fetch achievements: " + response.body().string()));
                    return;
                }

                try {
                    JSONObject jsonResponse = new JSONObject(response.body().string());
                    JSONArray items = jsonResponse.getJSONArray("items");
                    List<Achievement> achievements = new ArrayList<>();

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        Achievement achievement = new Achievement(
                                item.getString("id"),
                                item.getString("title"),
                                item.getString("description"),
                                baseUrl + "/api/files/" + item.getString("collectionId") + "/" + item.getString("id") + "/" + item.getString("icon"),
                                item.getString("type"),
                                0  // Count will be filled in after we fetch counts
                        );
                        achievements.add(achievement);
                    }

                    // Now fetch counts
                    fetchAchievementCounts(achievements, callback, authToken);

                } catch (JSONException e) {
                    callback.onFailure(e);
                }
            }
        });
    }

    private void fetchAchievementCounts(List<Achievement> achievements, final ApiCallback<List<Achievement>> callback, String token) {
        Request countRequest = new Request.Builder()
                .url(baseUrl + "/api/collections/achievement_counts/records?perPage=1000")
                .header("Authorization", token)
                .build();

        Log.d("PB", token);
        Log.d("PB", baseUrl);

        client.newCall(countRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure(new Exception("Failed to fetch counts: " + response.body().string()));
                    return;
                }

                try {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray items = json.getJSONArray("items");

                    Log.d("PB", json.toString());
                    Log.d("PB", "Counts fetched, sending to callback");

                    // Map of achievement_id to count
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        String achievementId = item.getString("achievement_id");
                        int count = item.optInt("achievement_count", 0);

                        Log.d("PB", String.valueOf(count));

                        for (Achievement a : achievements) {
                            if (a.getId().equals(achievementId)) {
                                a.setCount(count); // ← update model
                                break;
                            }
                        }
                    }

                    callback.onSuccess(achievements);

                } catch (JSONException e) {
                    callback.onFailure(e);
                }
            }
        });
    }

    public void getHighlightsFromAlerts(Activity activity, final ApiCallback<List<Highlight>> callback) {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/collections/alerts/records?perPage=1000")
                .header("Authorization", authToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure(new IOException("Failed to fetch alerts: " + response.body().string()));
                    return;
                }

                try {
                    JSONObject jsonResponse = new JSONObject(response.body().string());
                    JSONArray items = jsonResponse.getJSONArray("items");
                    List<AlertGroup> alertGroups = new ArrayList<>();

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        JSONObject bodyJson = item.getJSONObject("body");
                        AlertGroup alertGroup = new Gson().fromJson(bodyJson.toString(), AlertGroup.class);
                        alertGroups.add(alertGroup);
                    }

                    // Now process all alertGroups with geo, then return list
                    buildHighlightsWithGeo(activity, alertGroups, callback);

                } catch (JSONException e) {
                    callback.onFailure(e);
                }
            }
        });
    }

    private void buildHighlightsWithGeo(Activity activity, List<AlertGroup> groups, ApiCallback<List<Highlight>> callback) {
        List<Highlight> highlights = new ArrayList<>();
        int total = groups.size();

        if (total == 0) {
            callback.onSuccess(highlights);
            return;
        }

        // Track how many are finished
        final int[] completed = {0};

        for (AlertGroup group : groups) {
            Highlight.createHighlightWithGeo(activity, group, new Highlight.HighlightCallback() {
                @Override
                public void onHighlightReady(Highlight highlight) {
                    if (highlight != null) {
                        highlights.add(highlight);
                    }

                    completed[0]++;
                    if (completed[0] == total) {
                        // All done
                        callback.onSuccess(highlights);
                    }
                }
            });
        }
    }

    public void getHighlights(final ApiCallback<List<Highlight>> callback) {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/collections/highlights/records")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonResponse = new JSONObject(response.body().string());
                        JSONArray items = jsonResponse.getJSONArray("items");
                        List<Highlight> highlights = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            Highlight highlight = new Highlight(
                                    item.getString("id"),
                                    item.getString("user"),
                                    item.getString("location"),
                                    new java.util.Date(Calendar.getInstance().getTimeInMillis()),
                                    baseUrl + "/api/files/" + item.getString("collectionId") + "/" + item.getString("id") + "/" + item.getString("image"),
                                    item.getInt("likes"),
                                    item.getString("achievement"),
                                    item.getString("alert")
                            );
                            highlights.add(highlight);
                        }
                        callback.onSuccess(highlights);
                    } catch (JSONException e) {
                        callback.onFailure(e);
                    }
                } else {
                    callback.onFailure(new Exception("Failed to get highlights: " + response.body().string()));
                }
            }
        });
    }

    public void uploadImage(File imageFile, final ApiCallback<String> callback) {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", imageFile.getName(),
                        RequestBody.create(MediaType.parse("image/*"), imageFile))
                .build();

        Request request = new Request.Builder()
                .url(baseUrl + "/api/collections/highlights/records")
                .header("Authorization", authToken)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonResponse = new JSONObject(response.body().string());
//                        String imageUrl = baseUrl + "/api/files/" + jsonResponse.getString("collectionId") + "/" + jsonResponse.getString("id") + "/" + jsonResponse.getString("image");
                        String imageUrl = jsonResponse.getString("image");
                        callback.onSuccess(imageUrl);
                    } catch (JSONException e) {
                        callback.onFailure(e);
                    }
                } else {
                    callback.onFailure(new Exception("Failed to upload image: " + response.body().string()));
                }
            }
        });
    }

    public String getUserIdFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            String payloadJson = new String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING));
            JSONObject payload = new JSONObject(payloadJson);

            return payload.getString("id"); // usually user ID is stored as "id"
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void createHighlightWithFormData(File imageFile, String location, String achievementId, String alertId, final ApiCallback<Highlight> callback) {
        // Create multipart form
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);

        builder.addFormDataPart("image", imageFile.getName(),
                RequestBody.create(MediaType.parse("image/*"), imageFile));

        builder.addFormDataPart("location", location);
        builder.addFormDataPart("achievement", achievementId);
        builder.addFormDataPart("timestamp", String.valueOf(System.currentTimeMillis()));
        builder.addFormDataPart("shares", "0");
        // user id
        builder.addFormDataPart("user", getUserIdFromToken(authToken));

        if (alertId != null) {
            builder.addFormDataPart("alert", alertId);
        }

        RequestBody requestBody = builder.build();

        Request request = new Request.Builder()
                .url(baseUrl + "/api/collections/highlights/records")
                .header("Authorization", authToken)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure(new IOException("Highlight creation failed: " + response.body().string()));
                    return;
                }

                try {
                    JSONObject jsonResponse = new JSONObject(response.body().string());
                    String createdString = jsonResponse.getString("created");
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'", Locale.getDefault());
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    Date createdDate = sdf.parse(createdString);

                    Highlight highlight = new Highlight(
                            jsonResponse.getString("id"),
                            jsonResponse.optString("user", "anon"),
                            location,
                            createdDate,
                            baseUrl + "/api/files/" + jsonResponse.getString("collectionId") + "/" + jsonResponse.getString("id") + "/" + jsonResponse.getString("image"),
                            jsonResponse.optInt("shares", 0),
                            achievementId,
                            alertId
                    );
                    callback.onSuccess(highlight);
                } catch (JSONException e) {
                    callback.onFailure(e);
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }


    public void createHighlight(String imageUrl, String location, String achievementId, final ApiCallback<Highlight> callback) {
        JSONObject json = new JSONObject();
        try {
            json.put("image", imageUrl);
            json.put("location", location);
            json.put("achievement", achievementId);
            json.put("timestamp", System.currentTimeMillis());
            json.put("shares", 0);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
        Request request = new Request.Builder()
                .url(baseUrl + "/api/collections/highlights/records")
                .header("Authorization", authToken)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonResponse = new JSONObject(response.body().string());
                        Highlight highlight = new Highlight(
                                jsonResponse.getString("id"),
                                jsonResponse.getString("user"),
                                jsonResponse.getString("location"),
                                new java.util.Date(jsonResponse.getLong("created")),
                                imageUrl,
                                jsonResponse.getInt("likes"),
                                jsonResponse.getString("achievement"),
                                jsonResponse.getString("alert")
                        );
                        callback.onSuccess(highlight);
                    } catch (JSONException e) {
                        callback.onFailure(e);
                    }
                } else {
                    callback.onFailure(new Exception("Failed to create highlight: " + response.body().string()));
                }
            }
        });
    }

    public void syncAlertsIfNeeded(List<AlertGroup> incomingAlerts, final ApiCallback<Void> callback) {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/collections/alerts/records?perPage=10000")
                .header("Authorization", authToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure(new IOException("Failed to fetch PB alerts: " + response.body().string()));
                    return;
                }

                try {
                    JSONObject json = new JSONObject(response.body().string());
                    JSONArray existingItems = json.optJSONArray("items");
                    List<Integer> existingIds = new ArrayList<>();

                    for (int i = 0; i < existingItems.length(); i++) {
                        JSONObject obj = existingItems.getJSONObject(i);
                        existingIds.add(obj.getInt("id"));
                    }

                    for (AlertGroup group : incomingAlerts) {
                        if (!existingIds.contains(group.id)) {
                            createAlertInPB(group); // fire-and-forget; not awaiting
                        }
                    }

                    callback.onSuccess(null);
                } catch (JSONException e) {
                    callback.onFailure(e);
                }
            }
        });
    }

    public void checkIfAlertExists(int alertId, ApiCallback<Boolean> callback) {
        String url = baseUrl + "/api/collections/alerts/records?filter=(id='" + alertId + "')";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", authToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                boolean exists = !responseBody.contains("\"items\":[]");
                callback.onSuccess(exists);
            }
        });
    }

    public void getShareCountForAlert(int alertId, ApiCallback<Integer> callback) {
        String url = baseUrl + "/api/collections/highlights/records?filter=(alert='" + alertId + "')";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", authToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure(new IOException("Failed to count shares: " + response.body().string()));
                    return;
                }

                try {
                    JSONObject json = new JSONObject(response.body().string());
                    int count = json.getInt("totalItems");

                    Log.e("PBClient", "Count: " + count);

                    callback.onSuccess(count);

                } catch (JSONException e) {
                    callback.onFailure(e);
                }
            }
        });
    }


    public void checkAndCreateAlertAndHighlight(Activity activity, AlertGroup group, ApiCallback<Highlight> callback) {
        String url = baseUrl + "/api/collections/alerts/records?filter=(id='" + group.id + "')";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", authToken)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                boolean exists = !responseBody.contains("\"items\":[]");

                if (!exists) {
                    // 🔁 Use async geo-based highlight preview
                    Highlight.createHighlightWithGeo(activity, group, new Highlight.HighlightCallback() {
                        @Override
                        public void onHighlightReady(Highlight highlight) {
                            callback.onSuccess(highlight);
                        }
                    });
                    return;
                }

                // Save alert
                createAlertInPB(group);

                // Save highlight
                createHighlightFromAlertGroup(group, new ApiCallback<Highlight>() {
                    @Override
                    public void onSuccess(Highlight result) {
                        callback.onSuccess(result);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        callback.onFailure(e);
                    }
                });
            }
        });
    }


    public void createHighlightFromAlertGroup(AlertGroup group) {
        if (group.alerts == null || group.alerts.isEmpty()) return;

        com.nikonovcc.rfh.models.Alert alert = group.alerts.get(0);
        String location = (alert.cities != null && !alert.cities.isEmpty()) ? alert.cities.get(0) : "Unknown";

        JSONObject json = new JSONObject();
        try {
            json.put("image", "");
            json.put("location", location);
            json.put("achievement", "DefaultAchievementId");
            json.put("timestamp", alert.time);
            json.put("shares", 0);
            json.put("alert", String.valueOf(group.id));
        } catch (JSONException e) {
            Log.e("PBClient", "Failed to build highlight JSON", e);
            return;
        }

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());

        Request request = new Request.Builder()
                .url(baseUrl + "/api/collections/highlights/records")
                .header("Authorization", authToken)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("PBClient", "Highlight creation failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                Log.d("PBClient", "Highlight created: " + response.code());
            }
        });
    }


    public void createAlertInPB(AlertGroup alertGroup) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("id", alertGroup.id);
            payload.put("body", new JSONObject(new Gson().toJson(alertGroup))); // store entire JSON blob
        } catch (JSONException e) {
            Log.e("PocketbaseClient", "Failed to serialize alertGroup", e);
            return;
        }

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), payload.toString()
        );

        Request request = new Request.Builder()
                .url(baseUrl + "/api/collections/alerts/records")
                .header("Authorization", authToken)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("PocketbaseClient", "Failed to save alert", e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                Log.d("PocketbaseClient", "Alert saved: " + response.code());
            }
        });
    }

    private void createHighlightFromAlertGroup(AlertGroup group, ApiCallback<Highlight> callback) {
        if (group.alerts == null || group.alerts.isEmpty()) {
            callback.onFailure(new Exception("No alerts in group"));
            return;
        }

        com.nikonovcc.rfh.models.Alert alert = group.alerts.get(0);
        String location = (alert.cities != null && !alert.cities.isEmpty()) ? alert.cities.get(0) : "Unknown";

        JSONObject json = new JSONObject();
        try {
            json.put("image", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f0/Red_circle_with_white_border.svg/768px-Red_circle_with_white_border.svg.png");
            json.put("location", location);
            json.put("achievement", "DefaultAchievementId"); // optional
            json.put("timestamp", alert.time);
            json.put("shares", 0);
            json.put("alert", String.valueOf(group.id));
        } catch (JSONException e) {
            callback.onFailure(e);
            return;
        }

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());

        Request request = new Request.Builder()
                .url(baseUrl + "/api/collections/highlights/records")
                .header("Authorization", authToken)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure(new IOException("Highlight creation failed: " + response.body().string()));
                    return;
                }

                try {
                    JSONObject jsonResponse = new JSONObject(response.body().string());
                    Highlight highlight = new Highlight(
                            jsonResponse.getString("id"),
                            jsonResponse.optString("user", "anon"),
                            location,
                            new java.util.Date(alert.time),
                            json.getString("image"),
                            0,
                            json.getString("achievement"),
                            String.valueOf(group.id)
                    );
                    callback.onSuccess(highlight);
                } catch (JSONException e) {
                    callback.onFailure(e);
                }
            }
        });
    }


    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onFailure(Exception e);
    }

    public String getAuthToken() {
        return authToken;
    }

    private String loadToken() {
        SharedPreferences prefs = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        return prefs.getString("auth_token", null);
    }

    public boolean isTokenExpired() {
        if (authToken == null) return true;

        try {
            String[] parts = authToken.split("\\.");
            if (parts.length != 3) return true;

            String payloadJson = new String(android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT));
            JSONObject payload = new JSONObject(payloadJson);
            long exp = payload.getLong("exp");

            long currentTime = System.currentTimeMillis() / 1000; // in seconds
            if (currentTime >= exp) {
                authToken = null;
                SharedPreferences sharedPreferences = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.remove("auth_token");
                editor.apply();
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e("PocketbaseClient", "Token decode error", e);
            return true; // fallback to expired
        }
    }

}