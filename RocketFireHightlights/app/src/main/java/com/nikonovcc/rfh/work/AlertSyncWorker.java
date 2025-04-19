package com.nikonovcc.rfh.work;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.nikonovcc.rfh.PocketbaseClient;
import com.nikonovcc.rfh.models.AlertGroup;
import com.nikonovcc.rfh.utils.AlertFetcher;

import java.util.List;

public class AlertSyncWorker extends Worker {

    private static final String TAG = "AlertSyncWorker";

    public AlertSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting background alert sync");

        AlertFetcher fetcher = new AlertFetcher();
        PocketbaseClient pocketbaseClient = new PocketbaseClient(getApplicationContext(),"https://rocket-fire-highlights.pockethost.io");

        fetcher.fetchAlerts(new AlertFetcher.AlertCallback() {
            @Override
            public void onSuccess(List<AlertGroup> alertGroups) {
                for (AlertGroup group : alertGroups) {
                    try {
                        pocketbaseClient.createAlertIfMissing(group);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to sync alert group " + group.id, e);
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Alert fetch failed", e);
            }
        });

        return Result.success();
    }
}
