package com.nikonovcc.rfh;

import android.net.Uri;
import android.util.Log;

import com.nikonovcc.rfh.models.Achievement;
import com.nikonovcc.rfh.models.Highlight;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.*;

public class PocketbaseClient {
    private final String baseUrl;
    private final OkHttpClient client;
    private String authToken;

    public PocketbaseClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = new OkHttpClient();
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
        Request request = new Request.Builder()
                .url(baseUrl + "/api/collections/achievements/records")
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
                        List<Achievement> achievements = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            Achievement achievement = new Achievement(
                                    item.getString("id"),
                                    item.getString("title"),
                                    item.getString("description"),
                                    baseUrl + "/api/files/" + item.getString("collectionId") + "/" + item.getString("id") + "/" + item.getString("icon"),
                                    item.getString("type"),
                                    0  // Count will be updated separately
                            );
                            achievements.add(achievement);
                        }
                        callback.onSuccess(achievements);
                    } catch (JSONException e) {
                        callback.onFailure(e);
                    }
                } else {
                    callback.onFailure(new Exception("Failed to get achievements: " + response.body().string()));
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
                                    item.getString("achievement")
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
                                jsonResponse.getString("achievement")
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

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onFailure(Exception e);
    }

    public String getAuthToken() {
        return authToken;
    }
}