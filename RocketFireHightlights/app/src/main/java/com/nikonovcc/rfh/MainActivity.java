package com.nikonovcc.rfh;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import android.os.Handler;
import android.os.Looper;
import java.util.Collections;


import com.nikonovcc.rfh.adapters.AchievementsAdapter;
import com.nikonovcc.rfh.adapters.HighlightsAdapter;
import com.nikonovcc.rfh.models.Achievement;
import com.nikonovcc.rfh.models.AlertGroup;
import com.nikonovcc.rfh.models.Highlight;
import com.nikonovcc.rfh.utils.AlertFetcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Time;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private RecyclerView achievementsRecyclerView;
    private RecyclerView highlightsRecyclerView;
    private Button shareButton;
    private PocketbaseClient pocketbaseClient;
    private AchievementsAdapter achievementsAdapter;
    private HighlightsAdapter highlightsAdapter;
    private TextView logoutLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Pocketbase client
        pocketbaseClient = new PocketbaseClient(getApplicationContext(),"https://rocket-fire-highlights.pockethost.io");

        checkAndDisplayLatestAlert(); // this will do both check + create + UI
        startRepeatingAlertCheck();

        // Initialize views
        achievementsRecyclerView = findViewById(R.id.achievements_recycler_view);
        highlightsRecyclerView = findViewById(R.id.highlights_recycler_view);
//        shareButton = findViewById(R.id.share_button);

        // Set up achievements RecyclerView
        achievementsRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        achievementsAdapter = new AchievementsAdapter();
        achievementsRecyclerView.setAdapter(achievementsAdapter);

        // Set up highlights RecyclerView
        highlightsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        highlightsAdapter = new HighlightsAdapter();
        highlightsRecyclerView.setAdapter(highlightsAdapter);

        logoutLink = findViewById(R.id.logout_link);
        logoutLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pocketbaseClient.setAuthToken(null);
                // redirect to login
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });

        // Load data
        loadAchievements();
        loadHighlights();
    }

    public void handleImageSelectedWithAchievement(Uri imageUri, String achievementId, String alertId) {
        if (imageUri != null) {
            try {
                File imageFile = createImageFile(imageUri);

                // Call the new unified method
                pocketbaseClient.createHighlightWithFormData(imageFile, "Current Location", achievementId, alertId, new PocketbaseClient.ApiCallback<Highlight>() {
                    @Override
                    public void onSuccess(Highlight newHighlight) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Highlight shared!", Toast.LENGTH_SHORT).show();
                            loadHighlights();
                        });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.e("HighlightUpload", "Failed to share highlight: " + e.getMessage());
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to share: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });

            } catch (Exception e) {
                Toast.makeText(this, "Image error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadAchievements() {
        pocketbaseClient.getAchievements(new PocketbaseClient.ApiCallback<List<Achievement>>() {
            @Override
            public void onSuccess(List<Achievement> achievements) {
                runOnUiThread(() -> achievementsAdapter.setAchievements(achievements));
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to load achievements: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadHighlights() {
        pocketbaseClient.getHighlightsFromAlerts(this, new PocketbaseClient.ApiCallback<List<Highlight>>() {
            @Override
            public void onSuccess(List<Highlight> highlights) {
                Log.d("LHIGHLIGHTS", "Loaded highlights: " + highlights);
                runOnUiThread(() -> {
                    for (Highlight highlight : highlights) {
                        try {
                            int alertId = Integer.parseInt(highlight.getAlertId());

                            // 🔁 For each highlight, fetch share count
                            pocketbaseClient.getShareCountForAlert(alertId, new PocketbaseClient.ApiCallback<Integer>() {
                                @Override
                                public void onSuccess(Integer count) {
                                    highlight.setShares(count);
                                    runOnUiThread(() -> highlightsAdapter.addHighlight(highlight));
                                }

                                @Override
                                public void onFailure(Exception e) {
                                    Log.e("Highlight", "Failed to load share count for alertId: " + alertId, e);
                                    runOnUiThread(() -> highlightsAdapter.addHighlight(highlight)); // fallback: add without shares
                                }
                            });

                        } catch (NumberFormatException e) {
                            Log.e("Highlight", "Invalid alertId format: " + highlight.getAlertId(), e);
                            highlightsAdapter.addHighlight(highlight);
                        }
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to load highlights: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void openShareDialog() {
        ShareDialogFragment dialog = new ShareDialogFragment();
        dialog.show(getSupportFragmentManager(), "share_dialog");
    }

    public void handleImageSelected(Uri imageUri) {
        if (imageUri != null) {
            try {
                // Convert Uri to File
                File imageFile = createImageFile(imageUri);

                // Upload image to Pocketbase
                pocketbaseClient.uploadImage(imageFile, new PocketbaseClient.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String imageUrl) {
                        // Create new Highlight with the uploaded image URL
                        pocketbaseClient.createHighlight(imageUrl, "Current Location", "DefaultAchievementId", new PocketbaseClient.ApiCallback<Highlight>() {
                            @Override
                            public void onSuccess(Highlight newHighlight) {
                                runOnUiThread(() -> {
                                    Toast.makeText(MainActivity.this, "Highlight created successfully", Toast.LENGTH_SHORT).show();
                                    loadHighlights(); // Refresh the highlights list
                                });
                            }

                            @Override
                            public void onFailure(Exception e) {
                                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to create highlight: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                            }
                        });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to upload image: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
            } catch (Exception e) {
                Toast.makeText(this, "Error processing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private File createImageFile(Uri uri) throws Exception {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        File file = new File(getCacheDir(), "temp_image.jpg");
        OutputStream outputStream = new FileOutputStream(file);
        byte[] buffer = new byte[4 * 1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        outputStream.flush();
        return file;
    }

    private void fetchAndShowLastAlert() {
        AlertFetcher fetcher = new AlertFetcher();
        PocketbaseClient pocketbaseClient = new PocketbaseClient(getApplicationContext(), "https://rocket-fire-highlights.pockethost.io");

        fetcher.fetchAlerts(new AlertFetcher.AlertCallback() {
            @Override
            public void onSuccess(List<AlertGroup> alertGroups) {
                if (alertGroups == null || alertGroups.isEmpty()) return;

                AlertGroup latestGroup = Collections.max(alertGroups, (a, b) -> {
                    long t1 = a.alerts.get(0).time;
                    long t2 = b.alerts.get(0).time;
                    return Long.compare(t1, t2);
                });
                pocketbaseClient.checkAndCreateAlertAndHighlight(MainActivity.this, latestGroup, new PocketbaseClient.ApiCallback<Highlight>() {
                    @Override
                    public void onSuccess(Highlight result) {
                        runOnUiThread(() -> {
                            highlightsAdapter.setHighlights(List.of(result));
                        });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Alert load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "API fetch failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void checkAndDisplayLatestAlert() {
        AlertFetcher fetcher = new AlertFetcher();
        fetcher.fetchAlerts(new AlertFetcher.AlertCallback() {
            @Override
            public void onSuccess(List<AlertGroup> alertGroups) {
                if (alertGroups == null || alertGroups.isEmpty()) return;

                // Find the latest alert group
                AlertGroup latestGroup = Collections.max(alertGroups, (a, b) -> {
                    long t1 = a.alerts.get(0).time;
                    long t2 = b.alerts.get(0).time;
                    return Long.compare(t1, t2);
                });

                pocketbaseClient.checkIfAlertExists(latestGroup.id, new PocketbaseClient.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean exists) {
                        if (!exists) {
                            pocketbaseClient.createAlertInPB(latestGroup);
                            pocketbaseClient.createHighlightFromAlertGroup(latestGroup);
                        }

                        // Always show a preview highlight with current city
                        Highlight.createHighlightWithGeo(MainActivity.this, latestGroup, new Highlight.HighlightCallback() {
                            @Override
                            public void onHighlightReady(Highlight highlight) {
                                pocketbaseClient.getShareCountForAlert(latestGroup.id, new PocketbaseClient.ApiCallback<Integer>() {
                                    @Override
                                    public void onSuccess(Integer count) {
                                        Log.d("ABC", "Share count: " + count);
                                        highlight.setShares(count); // ← set it on the highlight model
                                        runOnUiThread(() -> highlightsAdapter.addHighlight(highlight));
                                    }

                                    @Override
                                    public void onFailure(Exception e) {
                                        Log.e("Highlight", "Failed to get share count", e);
                                        runOnUiThread(() -> highlightsAdapter.addHighlight(highlight)); // fallback to display without shares
                                    }
                                });
                            }
                        });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        runOnUiThread(() ->
                                Toast.makeText(MainActivity.this, "Check failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                        );
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "API error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }


    private void startRepeatingAlertCheck() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                loadAchievements();
                checkAndDisplayLatestAlert();
                startRepeatingAlertCheck(); // run again after 60s
            }
        }, 60 * 1000);
    }
}