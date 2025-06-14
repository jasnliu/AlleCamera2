package com.example.mycamera2;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    // We'll keep the translator here for now, but it might be better placed in CameraFragment
    // if only the camera feature uses translation.
    private Translator chineseToEnglishTranslator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);

        // Initialize and download translator model (can be moved if only CameraFragment needs it)
        initializeTranslator();

        // Load the default fragment (CameraFragment)
        if (savedInstanceState == null) {
            loadFragment(new CameraFragment());
            bottomNavigationView.setSelectedItemId(R.id.navigation_camera); // Highlight the camera icon
        }
    }

    private void initializeTranslator() {
        TranslatorOptions options =
                new TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.CHINESE)
                        .setTargetLanguage(TranslateLanguage.ENGLISH)
                        .build();
        chineseToEnglishTranslator = Translation.getClient(options);

        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi() // Consider making this configurable or prompt user
                .build();
        chineseToEnglishTranslator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(
                        v -> Log.d("Translator", "Chinese-English model downloaded."))
                .addOnFailureListener(
                        e -> {
                            Log.e("Translator", "Model download failed: " + e.getMessage());
                            Toast.makeText(MainActivity.this, "Translator model download failed.", Toast.LENGTH_LONG).show();
                        });
        // Manage translator lifecycle
        getLifecycle().addObserver(chineseToEnglishTranslator);
    }

    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, fragment);
            // transaction.addToBackStack(null); // Optional: if you want to add fragment to back stack
            transaction.commit();
            return true;
        }
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment fragment = null;
        int itemId = item.getItemId();
        if (itemId == R.id.navigation_camera) {
            fragment = new CameraFragment();
        } else if (itemId == R.id.navigation_settings) {
            fragment = new SettingsFragment();
        }
        return loadFragment(fragment);
    }

    // The following methods related to camera permission and OCR are currently removed from MainActivity.
    // They need to be moved and adapted into CameraFragment.java:
    // - REQUEST_CAMERA_PERMISSION constant
    // - previewView, ocrTextView, imageCapture fields (will be in CameraFragment)
    // - requestCameraPermission()
    // - startCamera()
    // - capturePhotoAndRunOCR()
    // - highlightKeywords() // This might be shared or also in CameraFragment
    // - runTextRecognition()
    // - translateChineseToEnglishAndDisplay() // Or keep a reference to translator if initialized here
    // - onRequestPermissionsResult()

    // Getter for the translator if CameraFragment needs it and it's initialized in MainActivity
    public Translator getChineseToEnglishTranslator() {
        return chineseToEnglishTranslator;
    }
}
