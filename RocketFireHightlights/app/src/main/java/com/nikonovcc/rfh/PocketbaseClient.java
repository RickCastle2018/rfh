package com.nikonovcc.rfh;

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
import java.util.ArrayList;
import java.util.List;
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
                                    new java.util.Date(item.getLong("timestamp")),
                                    baseUrl + "/api/files/" + item.getString("collectionId") + "/" + item.getString("id") + "/" + item.getString("image"),
                                    item.getInt("shares"),
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
                        String imageUrl = baseUrl + "/api/files/" + jsonResponse.getString("collectionId") + "/" + jsonResponse.getString("id") + "/" + jsonResponse.getString("image");
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
                                new java.util.Date(jsonResponse.getLong("timestamp")),
                                imageUrl,
                                jsonResponse.getInt("shares"),
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

    private void createAlertInPB(AlertGroup alertGroup) {
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

    public void createAlertIfMissing(AlertGroup group) {
        // Check if it exists (by ID maybe?)
        Request check = new Request.Builder()
                .url(baseUrl + "/api/collections/alerts/records?filter=id=" + group.id)
                .header("Authorization", authToken)
                .build();

        client.newCall(check).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("PBClient", "Check failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body().string().contains("\"items\":[]")) {
                    // Alert doesn't exist → create it
                    JSONObject json = new JSONObject();
                    try {
                        json.put("id", group.id);
                        json.put("body", new Gson().toJson(group));
                    } catch (JSONException e) {
                        Log.e("PBClient", "JSON error", e);
                    }

                    RequestBody body = RequestBody.create(
                            MediaType.parse("application/json"), json.toString());

                    Request post = new Request.Builder()
                            .url(baseUrl + "/api/collections/alerts/records")
                            .header("Authorization", authToken)
                            .post(body)
                            .build();

                    client.newCall(post).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            Log.e("PBClient", "Alert create failed", e);
                        }

                        @Override
                        public void onResponse(Call call, Response response) {
                            Log.d("PBClient", "Alert created: " + response.code());
                        }
                    });
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

    private void redirectToLogin() {
        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

}