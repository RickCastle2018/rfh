package com.nikonovcc.rfh.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class GeoCityGetter {

    public interface CityCallback {
        void onCityFound(String city);
        void onError(String error);
    }

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    public static void getUserCity(Activity activity, @NonNull CityCallback callback) {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);

            callback.onError("Location permission not granted.");
            return;
        }

        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(activity, location -> {
                    if (location != null) {
                        fetchCityFromCoordinates(activity, location, callback);
                    } else {
                        callback.onError("Location is null.");
                    }
                })
                .addOnFailureListener(e -> callback.onError("Failed to get location: " + e.getMessage()));
    }

    private static void fetchCityFromCoordinates(Activity activity, Location location, CityCallback callback) {
        Geocoder geocoder = new Geocoder(activity, new Locale("iw", "IL")); // "iw" = Hebrew, "IL" = Israel

        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                String city = addresses.get(0).getLocality();
                callback.onCityFound(city != null ? city : "Unknown");
            } else {
                callback.onError("No address found.");
            }
        } catch (IOException e) {
            callback.onError("Geocoder failed: " + e.getMessage());
        }
    }

    public static int getPermissionRequestCode() {
        return LOCATION_PERMISSION_REQUEST_CODE;
    }
}