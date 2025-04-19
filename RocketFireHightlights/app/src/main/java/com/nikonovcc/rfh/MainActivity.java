package com.nikonovcc.rfh;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.nikonovcc.rfh.adapters.AchievementsAdapter;
import com.nikonovcc.rfh.adapters.HighlightsAdapter;
import com.nikonovcc.rfh.models.Achievement;
import com.nikonovcc.rfh.models.Highlight;
import com.nikonovcc.rfh.work.AlertSyncWorker;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(AlertSyncWorker.class, 1, TimeUnit.MINUTES).build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "alert_worker",
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );

        // Initialize Pocketbase client
        pocketbaseClient = new PocketbaseClient(getApplicationContext(),"https://rocket-fire-highlights.pockethost.io");

        // Initialize views
        achievementsRecyclerView = findViewById(R.id.achievements_recycler_view);
        highlightsRecyclerView = findViewById(R.id.highlights_recycler_view);
        shareButton = findViewById(R.id.share_button);

        // Set up achievements RecyclerView
        achievementsRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        achievementsAdapter = new AchievementsAdapter();
        achievementsRecyclerView.setAdapter(achievementsAdapter);

        // Set up highlights RecyclerView
        highlightsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        highlightsAdapter = new HighlightsAdapter();
        highlightsRecyclerView.setAdapter(highlightsAdapter);

        // Set up share button
        shareButton.setOnClickListener(v -> openShareDialog());

        // Load data
        loadAchievements();
        loadHighlights();
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
        pocketbaseClient.getHighlights(new PocketbaseClient.ApiCallback<List<Highlight>>() {
            @Override
            public void onSuccess(List<Highlight> highlights) {
                highlights.add(new Highlight(
                        "123",
                        "test",
                        "testcity",
                        Calendar.getInstance().getTime(),
                        "test",
                        10,
                        "testID1",
                        "testID2"
                ));
                runOnUiThread(() -> highlightsAdapter.setHighlights(highlights));
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
}