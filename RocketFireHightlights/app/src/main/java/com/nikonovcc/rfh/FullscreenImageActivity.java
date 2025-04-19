package com.nikonovcc.rfh;

import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

public class FullscreenImageActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        ImageView imageView = findViewById(R.id.fullscreen_image);
        String imageUrl = getIntent().getStringExtra("image_url");

        Glide.with(this).load(imageUrl).into(imageView);

        imageView.setOnClickListener(v -> finish()); // tap to close
    }
}
