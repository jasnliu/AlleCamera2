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

import java.util.Map;

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
    private Map<String, Translator> translators; // Added


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

        // Get the translators from MainActivity
        if (getActivity() instanceof MainActivity) {
            translators = ((MainActivity) getActivity()).getTranslators();
            if (translators == null || translators.isEmpty()) {
                Log.e("CameraFragment", "Translators are null or empty. Check MainActivity initialization.");
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Translator service not available.", Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Log.e("CameraFragment", "Parent activity is not MainActivity or is null, cannot get translators.");
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
        
        // Try multiple text recognizers for different languages
        runMultiLanguageTextRecognition(image);
    }
    
    private void runMultiLanguageTextRecognition(InputImage image) {
        // Try different recognizers in sequence
        // Start with Chinese, then Japanese, then general Latin
        runChineseTextRecognition(image);
    }
    
    private void runChineseTextRecognition(InputImage image) {
        TextRecognizer chineseRecognizer = TextRecognition.getClient(new com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build());
        
        chineseRecognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String chineseText = visionText.getText();
                    if (!chineseText.trim().isEmpty()) {
                        detectLanguageAndTranslate(chineseText);
                    } else {
                        // Try Japanese as next fallback
                        runJapaneseTextRecognition(image);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("CameraFragment", "Chinese text recognition failed", e);
                    // Try Japanese as fallback
                    runJapaneseTextRecognition(image);
                });
    }
    
    private void runJapaneseTextRecognition(InputImage image) {
        TextRecognizer japaneseRecognizer = TextRecognition.getClient(new com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions.Builder().build());
        
        japaneseRecognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String japaneseText = visionText.getText();
                    if (!japaneseText.trim().isEmpty()) {
                        detectLanguageAndTranslate(japaneseText);
                    } else {
                        // Try Latin (Spanish/French/English) as final fallback
                        runLatinTextRecognition(image);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("CameraFragment", "Japanese text recognition failed", e);
                    // Try Latin as fallback
                    runLatinTextRecognition(image);
                });
    }
    
    private void runLatinTextRecognition(InputImage image) {
        // Use the Chinese text recognizer which can also detect Latin text
        TextRecognizer latinRecognizer = TextRecognition.getClient(new com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build());
        
        latinRecognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String latinText = visionText.getText();
                    if (!latinText.trim().isEmpty()) {
                        detectLanguageAndTranslate(latinText);
                    } else {
                        // No text found in any language
                        if (ocrTextView != null) {
                            ocrTextView.setText("No text detected in any supported language");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("CameraFragment", "Latin text recognition failed", e);
                    if (ocrTextView != null) {
                        ocrTextView.setText(String.format(getString(R.string.failed_to_recognize_text), e.getMessage()));
                    }
                });
    }

    // New method to detect language and translate text
    private void detectLanguageAndTranslate(String originalText) {
        if (translators == null || translators.isEmpty()) {
            Log.w("CameraFragment", "Translators not available, skipping translation.");
            highlightKeywords(originalText);
            return;
        }

        String detectedLanguage = detectLanguage(originalText);
        Log.d("CameraFragment", "Detected language: " + detectedLanguage);
        
        if (detectedLanguage != null && !detectedLanguage.equals("english")) {
            Translator translator = translators.get(detectedLanguage);
            if (translator != null) {
                translator.translate(originalText)
                        .addOnSuccessListener(translatedText -> {
                            String combinedText = translatedText + "\n\n" + originalText;
                            highlightKeywords(combinedText);
                        })
                        .addOnFailureListener(e -> {
                            Log.e("CameraFragment", "Translation failed for " + detectedLanguage, e);
                            if (getContext() != null) {
                                Toast.makeText(getContext(), "Translation failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                            highlightKeywords(originalText); // Show original text on translation failure
                        });
            } else {
                Log.w("CameraFragment", "No translator available for " + detectedLanguage);
                highlightKeywords(originalText);
            }
        } else {
            // Text is already in English or language not detected
            highlightKeywords(originalText);
        }
    }
    
    // Simple language detection based on character sets
    private String detectLanguage(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        boolean hasChinese = false;
        boolean hasJapanese = false;
        boolean hasKorean = false;
        int latinCount = 0;
        int totalChars = 0;
        
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                totalChars++;
                Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
                
                if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                    hasChinese = true;
                } else if (block == Character.UnicodeBlock.HIRAGANA || 
                          block == Character.UnicodeBlock.KATAKANA || 
                          block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION) {
                    hasJapanese = true;
                } else if (block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                          block == Character.UnicodeBlock.HANGUL_JAMO ||
                          block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) {
                    hasKorean = true;
                } else if (block == Character.UnicodeBlock.BASIC_LATIN ||
                          block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT ||
                          block == Character.UnicodeBlock.LATIN_EXTENDED_A ||
                          block == Character.UnicodeBlock.LATIN_EXTENDED_B) {
                    latinCount++;
                }
            }
        }
        
        // Priority: Japanese > Korean > Chinese > Latin-based languages
        if (hasJapanese) {
            return "japanese";
        } else if (hasKorean) {
            return "korean";
        } else if (hasChinese) {
            return "chinese";
        } else if (latinCount > 0) {
            // For Latin-based languages, we'll need additional logic
            // For now, let's use simple heuristics
            if (containsSpanishWords(text)) {
                return "spanish";
            } else if (containsFrenchWords(text)) {
                return "french";
            } else {
                return "english"; // Default to English for Latin text
            }
        }
        
        return null;
    }
    
    // Simple Spanish detection based on common words/patterns
    private boolean containsSpanishWords(String text) {
        String lowerText = text.toLowerCase();
        String[] spanishWords = {"el", "la", "de", "que", "y", "a", "en", "un", "es", "se", "no", "te", "lo", "le", "da", "su", "por", "son", "con", "para", "al", "una", "del", "todo", "pero", "más", "hacer", "muy", "año", "estar", "tener", "le", "ya", "todo", "esta", "sí", "todo", "ser", "ir", "tiempo", "está", "hasta", "hombre", "vida", "hacer", "pero", "sí", "muy", "mayor", "donde", "cuando", "cómo", "gracias", "español", "méxico", "españa"};
        
        for (String word : spanishWords) {
            if (lowerText.contains(" " + word + " ") || lowerText.startsWith(word + " ") || lowerText.endsWith(" " + word)) {
                return true;
            }
        }
        return false;
    }
    
    // Simple French detection based on common words/patterns
    private boolean containsFrenchWords(String text) {
        String lowerText = text.toLowerCase();
        String[] frenchWords = {"le", "de", "et", "à", "un", "il", "être", "et", "en", "avoir", "que", "pour", "dans", "ce", "son", "une", "sur", "avec", "ne", "se", "pas", "tout", "plus", "par", "grand", "le", "la", "les", "du", "des", "au", "aux", "français", "france", "bonjour", "merci", "oui", "non", "où", "quand", "comment", "pourquoi", "parce", "très", "bien", "mal", "bon", "grande", "petit", "petite"};
        
        for (String word : frenchWords) {
            if (lowerText.contains(" " + word + " ") || lowerText.startsWith(word + " ") || lowerText.endsWith(" " + word)) {
                return true;
            }
        }
        return false;
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
