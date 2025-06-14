package com.example.mycamera2;

import android.Manifest;
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
import androidx.camera.core.Camera;
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

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
// import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions; // Using default for now as per user code

import java.util.concurrent.ExecutionException;

public class CameraFragment extends Fragment {

    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private PreviewView previewView;
    private TextView ocrTextView;
    private ImageCapture imageCapture;
    private ImageButton captureButton;

    public CameraFragment() {
        // Required empty public constructor
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

        // Make sure the PreviewView itself doesn’t steal clicks:
        if (previewView != null) {
            previewView.setClickable(false);
            previewView.setFocusable(false);
        } else {
            Log.e("CameraFragment", "PreviewView is null in onViewCreated");
            // Optionally show a toast or handle error
        }


        if (captureButton != null) {
            captureButton.setOnClickListener(v -> {
                // 1. Play click sound
                v.playSoundEffect(SoundEffectConstants.CLICK);

                // 2. Animate button (scale down then back up)
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

                // 3. Run OCR capture logic
                capturePhotoAndRunOCR();
            });
        } else {
             Log.e("CameraFragment", "CaptureButton is null in onViewCreated");
        }

        requestCameraPermission();
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
        // Initial check: Ensure fragment is in a good state before starting camera setup.
        if (!isAdded() || getView() == null || getContext() == null) {
            Log.w("CameraFragment", "startCamera: Aborting, Fragment not in a valid state (not added, view or context is null).");
            return;
        }
        // Also check if the Fragment's main lifecycle is already destroyed.
        if (getLifecycle().getCurrentState() == androidx.lifecycle.Lifecycle.State.DESTROYED) {
            Log.w("CameraFragment", "startCamera: Aborting, Fragment lifecycle is already DESTROYED.");
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                // Critical check: Ensure fragment and its view are still valid *inside* the listener.
                if (!isAdded() || getView() == null || getContext() == null) {
                    Log.w("CameraFragment", "startCamera listener: Aborting, Fragment became invalid (not added, view or context is null).");
                    return;
                }

                // Attempt to get the ViewLifecycleOwner and immediately check its state.
                LifecycleOwner viewLifecycleOwner = getViewLifecycleOwner(); // This is the call that can throw.
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
                    if (isAdded() && getContext() != null) { // Check before Toast
                        Toast.makeText(requireContext(), "Error: Preview surface unavailable.", Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                imageCapture = new ImageCapture.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                // Use the validated viewLifecycleOwner.
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageCapture);
                Log.d("CameraFragment", "Camera bound to lifecycle successfully.");

            } catch (IllegalStateException e) {
                // Specifically catch issues from getViewLifecycleOwner() or other lifecycle violations.
                Log.e("CameraFragment", "startCamera listener: IllegalStateException during camera setup. Message: " + e.getMessage(), e);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(requireContext(), "Error configuring camera lifecycle.", Toast.LENGTH_SHORT).show();
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraFragment", "startCamera listener: Error with CameraProvider future. Message: " + e.getMessage(), e);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(requireContext(), "Could not initialize camera provider.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) { // Catch-all for any other unexpected errors during setup.
                Log.e("CameraFragment", "startCamera listener: Unexpected error during camera setup. Message: " + e.getMessage(), e);
                if (isAdded() && getContext() != null) {
                    Toast.makeText(requireContext(), "Unexpected camera error.", Toast.LENGTH_SHORT).show();
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void capturePhotoAndRunOCR() {
        if (imageCapture == null) {
            Toast.makeText(requireContext(), getString(R.string.image_capture_not_ready), Toast.LENGTH_SHORT).show();
            return;
        }

        // Take a picture with ImageCapture
        imageCapture.takePicture(
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        // Convert ImageProxy to Bitmap
                        Bitmap bitmap = ImageUtils.imageProxyToBitmap(imageProxy);
                        // Important: Close the imageProxy
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
                        Toast.makeText(requireContext(),
                                String.format(getString(R.string.capture_failed), exception.getMessage()),
                                Toast.LENGTH_SHORT).show();
                         Log.e("CameraFragment", "Capture failed", exception);
                    }
                }
        );
    }

    private void highlightKeywords(String text) {
        if (ocrTextView == null) {
            Log.e("CameraFragment", "ocrTextView is null in highlightKeywords.");
            return;
        }
        SpannableStringBuilder spannable = new SpannableStringBuilder(text);

        // Keywords to highlight
        String[] keywords = {"almond", "pistachio", "peanut"}; // Example keywords

        for (String keyword : keywords) {
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
            Log.e("CameraFragment", "ocrTextView is null in runTextRecognition, cannot display results.");
            // Optionally, show a toast to the user
            // Toast.makeText(requireContext(), "Error: UI element for OCR text not available.", Toast.LENGTH_SHORT).show();
            // return; // Or decide if you want to proceed with recognition even if UI can't be updated.
        }
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        // Using ChineseTextRecognizerOptions as per your provided code snippet
        TextRecognizer recognizer = TextRecognition.getClient(new com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build());

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    // Check if ocrTextView is still available, especially if operations are long
                    if (ocrTextView != null) {
                        // If you intend to use the translation flow, call translateChineseToEnglishAndDisplay here
                        // For now, directly calling highlightKeywords as per your provided code
                        highlightKeywords(visionText.getText());
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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        // It's good practice to call super, though for Fragment it might not do much by default.
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Ensure fragment is still in a valid state to start camera
                if (isAdded() && getView() != null && getContext() != null) {
                    startCamera();
                } else {
                    Log.w("CameraFragment", "onRequestPermissionsResult: Cannot start camera, fragment not in valid state.");
                    // Optionally inform user if context is available
                    if (getContext() != null) {
                        Toast.makeText(requireContext(), "Could not restart camera, please try again.", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                if (getContext() != null) { // Check context before showing Toast
                    Toast.makeText(requireContext(), getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
