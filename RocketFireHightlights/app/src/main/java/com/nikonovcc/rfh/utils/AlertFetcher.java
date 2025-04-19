package com.nikonovcc.rfh.utils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nikonovcc.rfh.models.AlertGroup;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.OkHttpClient;

public class AlertFetcher {

    private static final String TAG = "AlertFetcher";
    private static final String URL = "https://api.tzevaadom.co.il/alerts-history/";

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    public interface AlertCallback {
        void onSuccess(List<AlertGroup> alertGroups);
        void onError(Exception e);
    }

    public void fetchAlerts(AlertCallback callback) {
        Request request = new Request.Builder()
                .url(URL)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
                Log.e(TAG, "Request failed", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError(new IOException("Unexpected code " + response));
                    return;
                }

                String json = response.body().string();
                Type listType = new TypeToken<List<AlertGroup>>() {}.getType();
                List<AlertGroup> alerts = gson.fromJson(json, listType);

                callback.onSuccess(alerts);
            }
        });
    }
}
