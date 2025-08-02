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
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener {

    // We'll keep the translators here for now, but it might be better placed in CameraFragment
    // if only the camera feature uses translation.
    private Map<String, Translator> translators = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);

        // Initialize and download translator models (can be moved if only CameraFragment needs it)
        initializeTranslators();

        // Load the default fragment (CameraFragment)
        if (savedInstanceState == null) {
            loadFragment(new CameraFragment());
            bottomNavigationView.setSelectedItemId(R.id.navigation_camera); // Highlight the camera icon
        }
    }

    private void initializeTranslators() {
        // Initialize translators for different languages
        String[][] languagePairs = {
            {"chinese", TranslateLanguage.CHINESE, TranslateLanguage.ENGLISH},
            {"japanese", TranslateLanguage.JAPANESE, TranslateLanguage.ENGLISH},
            {"spanish", TranslateLanguage.SPANISH, TranslateLanguage.ENGLISH},
            {"french", TranslateLanguage.FRENCH, TranslateLanguage.ENGLISH}
        };

        DownloadConditions conditions = new DownloadConditions.Builder()
                .requireWifi() // Consider making this configurable or prompt user
                .build();

        for (String[] pair : languagePairs) {
            String key = pair[0];
            String sourceLanguage = pair[1];
            String targetLanguage = pair[2];
            
            TranslatorOptions options = new TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLanguage)
                    .setTargetLanguage(targetLanguage)
                    .build();
            
            Translator translator = Translation.getClient(options);
            translators.put(key, translator);
            
            translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener(v -> Log.d("Translator", key + "-English model downloaded."))
                    .addOnFailureListener(e -> {
                        Log.e("Translator", key + " model download failed: " + e.getMessage());
                        Toast.makeText(MainActivity.this, key + " translator model download failed.", Toast.LENGTH_LONG).show();
                    });
            
            // Manage translator lifecycle
            getLifecycle().addObserver(translator);
        }
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

    // Getter for the translators if CameraFragment needs them and they're initialized in MainActivity
    public Map<String, Translator> getTranslators() {
        return translators;
    }
    
    // Getter for specific translator (backward compatibility)
    public Translator getChineseToEnglishTranslator() {
        return translators.get("chinese");
    }
}
