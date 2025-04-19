package com.nikonovcc.rfh;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.nikonovcc.rfh.models.Achievement;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class ShareDialogFragment extends DialogFragment {
    private static final int PICK_IMAGE = 100;
    private static final int TAKE_PHOTO = 101;
    private static final int CAMERA_PERMISSION_CODE = 200;

    private Spinner spinner;
    private List<Achievement> achievements = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private List<String> achievementTitles = new ArrayList<>();
    private String selectedAchievementId = "DefaultAchievementId";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_share, container, false);
        String alertId = getArguments() != null ? getArguments().getString("alert_id", "0") : "0";

        Button cameraButton = view.findViewById(R.id.camera_button);
        Button galleryButton = view.findViewById(R.id.gallery_button);
        spinner = view.findViewById(R.id.achievement_spinner);

        // Setup spinner
        spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, achievementTitles);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedAchievementId = achievements.get(position).getId();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedAchievementId = "DefaultAchievementId";
            }
        });

        fetchAchievements();

        cameraButton.setOnClickListener(v -> checkCameraPermission());
        galleryButton.setOnClickListener(v -> openGallery());

        return view;
    }

    private void fetchAchievements() {
        PocketbaseClient client = new PocketbaseClient(requireContext(), "https://rocket-fire-highlights.pockethost.io");
        client.getAchievements(new PocketbaseClient.ApiCallback<List<Achievement>>() {
            @Override
            public void onSuccess(List<Achievement> result) {
                achievements.clear();
                achievementTitles.clear();
                achievements.addAll(result);
                for (Achievement a : result) {
                    achievementTitles.add(a.getTitle());
                }

                requireActivity().runOnUiThread(() -> spinnerAdapter.notifyDataSetChanged());
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("ShareDialog", "Failed to fetch achievements", e);
            }
        });
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, TAKE_PHOTO);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(getContext(), "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Uri saveBitmapAndGetUri(Bitmap bitmap) {
        try {
            File file = new File(requireContext().getCacheDir(), "captured_photo.jpg");
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

            return androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".provider",
                    file
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String alertId = getArguments() != null ? getArguments().getString("alert_id", "0") : "0";

        if (getActivity() == null || resultCode != Activity.RESULT_OK) return;

        if (requestCode == PICK_IMAGE && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            ((MainActivity) getActivity()).handleImageSelectedWithAchievement(imageUri, selectedAchievementId, alertId);
            dismiss();

        } else if (requestCode == TAKE_PHOTO && data != null && data.getExtras() != null) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");

            if (photo != null) {
                Uri imageUri = saveBitmapAndGetUri(photo);
                if (imageUri != null) {
                    ((MainActivity) getActivity()).handleImageSelectedWithAchievement(imageUri, selectedAchievementId, alertId);
                } else {
                    Toast.makeText(getContext(), "Error saving photo", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "Captured image unsupported, please try gallery", Toast.LENGTH_SHORT).show();
            }

            dismiss();
        }
    }

}
