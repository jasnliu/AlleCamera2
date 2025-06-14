package com.example.mycamera2;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;

import java.lang.Character; // Added for UnicodeBlock
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class CameraFragment extends Fragment {

    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private PreviewView previewView;
    private TextView ocrTextView;
    private ImageCapture imageCapture;
    private ImageButton captureButton;

    private static final String PREFS_NAME = "AllergyPrefs";
    private static final String KEY_ALLERGIES = "allergies";
    private List<String> defaultAllergies;
    private Translator chineseToEnglishTranslator; // Added


    public CameraFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        defaultAllergies = new ArrayList<>(Arrays.asList("almond", "pistachio", "peanut", "fish", "pecan"));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        previewView = view.findViewById(R.id.previewView);
        ocrTextView = view.findViewById(R.id.ocrTextView);
        captureButton = view.findViewById(R.id.captureButton);

        if (previewView != null) {
            previewView.setClickable(false);
            previewView.setFocusable(false);
        } else {
            Log.e("CameraFragment", "PreviewView is null in onViewCreated");
        }

        if (captureButton != null) {
            captureButton.setOnClickListener(v -> {
                v.playSoundEffect(SoundEffectConstants.CLICK);
                v.animate()
                        .scaleX(0.85f)
                        .scaleY(0.85f)
                        .setDuration(100)
                        .withEndAction(() -> {
                            v.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(100)
                                    .start();
                        })
                        .start();
                capturePhotoAndRunOCR();
            });
        } else {
            Log.e("CameraFragment", "CaptureButton is null in onViewCreated");
        }

        requestCameraPermission();

        // Get the translator from MainActivity
        if (getActivity() instanceof MainActivity) {
            chineseToEnglishTranslator = ((MainActivity) getActivity()).getChineseToEnglishTranslator();
            if (chineseToEnglishTranslator == null) {
                Log.e("CameraFragment", "Translator is null. Check MainActivity initialization.");
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Translator service not available.", Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Log.e("CameraFragment", "Parent activity is not MainActivity or is null, cannot get translator.");
             if (getContext() != null) {
                Toast.makeText(getContext(), "Translator setup error.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.CAMERA)) {
                Toast.makeText(requireContext(), "Camera permission is needed to use the app", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        if (!isAdded() || getView() == null || getContext() == null) {
            Log.w("CameraFragment", "startCamera: Aborting, Fragment not in a valid state.");
            return;
        }
        if (getLifecycle().getCurrentState() == androidx.lifecycle.Lifecycle.State.DESTROYED) {
            Log.w("CameraFragment", "startCamera: Aborting, Fragment lifecycle is already DESTROYED.");
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                if (!isAdded() || getView() == null || getContext() == null) {
                    Log.w("CameraFragment", "startCamera listener: Aborting, Fragment became invalid.");
                    return;
                }
                LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner();
                if (viewLifecycleOwner.getLifecycle().getCurrentState() == androidx.lifecycle.Lifecycle.State.DESTROYED) {
                    Log.w("CameraFragment", "startCamera listener: Aborting, View lifecycle is DESTROYED.");
                    return;
                }

                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                if (previewView != null) {
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());
                } else {
                    Log.e("CameraFragment", "startCamera listener: PreviewView is null.");
                    if (isAdded() && getContext() != null) {
                        Toast.makeText(requireContext(), "Error: Preview surface unavailable.", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                imageCapture = new ImageCapture.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageCapture);
                Log.d("CameraFragment", "Camera bound to lifecycle successfully.");

            } catch (IllegalStateException e) {
                Log.e("CameraFragment", "startCamera listener: IllegalStateException: " + e.getMessage(), e);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(requireContext(), "Error configuring camera lifecycle.", Toast.LENGTH_SHORT).show();
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraFragment", "startCamera listener: Error with CameraProvider: " + e.getMessage(), e);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(requireContext(), "Could not initialize camera provider.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e("CameraFragment", "startCamera listener: Unexpected error: " + e.getMessage(), e);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(requireContext(), "Unexpected camera error.", Toast.LENGTH_SHORT).show();
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void capturePhotoAndRunOCR() {
        if (imageCapture == null) {
            if (getContext() != null) { // Check context
                 Toast.makeText(requireContext(), getString(R.string.image_capture_not_ready), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        imageCapture.takePicture(
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        Bitmap bitmap = ImageUtils.imageProxyToBitmap(imageProxy);
                        imageProxy.close();
                        if (bitmap != null) {
                            runTextRecognition(bitmap);
                        } else {
                            Log.e("CameraFragment", "Bitmap is null after conversion from ImageProxy.");
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        super.onError(exception);
                        if (getContext() != null) { // Check context
                            Toast.makeText(requireContext(),
                                    String.format(getString(R.string.capture_failed), exception.getMessage()),
                                    Toast.LENGTH_SHORT).show();
                        }
                        Log.e("CameraFragment", "Capture failed", exception);
                    }
                }
        );
    }

    private List<String> loadKeywordsFromPreferences() {
        if (getContext() == null) return defaultAllergies; 
        SharedPreferences sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> savedAllergies = sharedPreferences.getStringSet(KEY_ALLERGIES, null);
        if (savedAllergies == null) {
            return defaultAllergies;
        }
        return new ArrayList<>(savedAllergies);
    }

    private void highlightKeywords(String text) {
        if (ocrTextView == null) {
            Log.e("CameraFragment", "ocrTextView is null in highlightKeywords.");
            return;
        }
        SpannableStringBuilder spannable = new SpannableStringBuilder(text);
        List<String> keywords = loadKeywordsFromPreferences();

        for (String keyword : keywords) {
            if (keyword == null || keyword.trim().isEmpty()) continue; 
            int index = text.toLowerCase().indexOf(keyword.toLowerCase());
            while (index >= 0) {
                spannable.setSpan(new ForegroundColorSpan(Color.RED),
                        index, index + keyword.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                index = text.toLowerCase().indexOf(keyword.toLowerCase(), index + keyword.length());
            }
        }
        ocrTextView.setText(spannable);
    }

    private void runTextRecognition(Bitmap bitmap) {
        if (ocrTextView == null) {
            Log.e("CameraFragment", "ocrTextView is null in runTextRecognition.");
        }
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(new com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build());

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    if (ocrTextView != null) {
                        translateChineseTextIfPresent(visionText.getText()); // Modified
                    } else {
                        Log.w("CameraFragment", "ocrTextView became null before displaying OCR results.");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("CameraFragment", "Text recognition failed", e);
                    if (ocrTextView != null) {
                        ocrTextView.setText(String.format(getString(R.string.failed_to_recognize_text), e.getMessage()));
                    } else {
                        Log.w("CameraFragment", "ocrTextView became null before displaying OCR failure.");
                    }
                });
    }

    // New method to translate text if it contains Chinese
    private void translateChineseTextIfPresent(String originalText) {
        if (chineseToEnglishTranslator == null) {
            Log.w("CameraFragment", "Translator not available, skipping translation.");
            highlightKeywords(originalText);
            return;
        }

        boolean containsChinese = false;
        if (originalText != null && !originalText.isEmpty()) {
            for (char c : originalText.toCharArray()) {
                if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                    containsChinese = true;
                    break;
                }
            }
        }

        if (containsChinese) {
            chineseToEnglishTranslator.translate(originalText)
                    .addOnSuccessListener(translatedText -> {
                        String combinedText = translatedText + "\n\n" + originalText;
                        highlightKeywords(combinedText);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("CameraFragment", "Translation failed", e);
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Translation failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                        highlightKeywords(originalText); // Show original text on translation failure
                    });
        } else {
            highlightKeywords(originalText);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isAdded() && getView() != null && getContext() != null) {
                    startCamera();
                } else {
                    Log.w("CameraFragment", "onRequestPermissionsResult: Cannot start camera, fragment not in valid state.");
                    if (getContext() != null) {
                        Toast.makeText(requireContext(), "Could not restart camera, please try again.", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                if (getContext() != null) {
                    Toast.makeText(requireContext(), getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
