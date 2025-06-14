package com.example.mycamera2;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "AllergyPrefs";
    private static final String KEY_ALLERGIES = "allergies";
    private static final int MAX_CUSTOM_ALLERGIES = 10;

    private RecyclerView rvAllergies;
    private EditText etNewAllergy;
    private Button btnAddAllergy;
    private AllergyAdapter adapter;
    private List<String> allergyList; // Current list of allergies displayed and managed
    private final List<String> originalDefaultAllergies = new ArrayList<>(Arrays.asList("almond", "pistachio", "peanut", "fish", "pecan")); // Keep original defaults for logic
    private SharedPreferences sharedPreferences;

    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadAllergies();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvAllergies = view.findViewById(R.id.rvAllergies);
        etNewAllergy = view.findViewById(R.id.etNewAllergy);
        btnAddAllergy = view.findViewById(R.id.btnAddAllergy);

        rvAllergies.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AllergyAdapter(allergyList, this::removeAllergy);
        rvAllergies.setAdapter(adapter);

        btnAddAllergy.setOnClickListener(v -> addNewAllergy());
    }

    private void loadAllergies() {
        Set<String> savedAllergiesSet = sharedPreferences.getStringSet(KEY_ALLERGIES, null);
        allergyList = new ArrayList<>(); // Initialize as empty
        if (savedAllergiesSet == null) {
            // First time run or preferences were cleared: populate with original default values.
            allergyList.addAll(originalDefaultAllergies);
            saveAllergies(); // Save this initial state
        } else {
            // Preferences exist, load them. This list reflects all user additions/deletions.
            allergyList.addAll(savedAllergiesSet);
        }
    }

    private void saveAllergies() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        // Store with original casing
        editor.putStringSet(KEY_ALLERGIES, new HashSet<>(allergyList));
        editor.apply();
    }

    private int getCurrentCustomAllergyCount() {
        int customCount = 0;
        for (String allergy : allergyList) {
            boolean isOriginalDefault = false;
            for (String defaultAllergy : originalDefaultAllergies) {
                if (allergy.equalsIgnoreCase(defaultAllergy)) {
                    isOriginalDefault = true;
                    break;
                }
            }
            if (!isOriginalDefault) {
                customCount++;
            }
        }
        return customCount;
    }

    private void addNewAllergy() {
        String newAllergyInput = etNewAllergy.getText().toString().trim();
        if (newAllergyInput.isEmpty()) {
            Toast.makeText(getContext(), "Allergy name cannot be empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check for duplicates (case-insensitive)
        for (String existingAllergy : allergyList) {
            if (existingAllergy.equalsIgnoreCase(newAllergyInput)) {
                Toast.makeText(getContext(), "'" + newAllergyInput + "' is already in the list.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        boolean isReAddingOriginalDefault = false;
        for (String defaultAllergy : originalDefaultAllergies) {
            if (newAllergyInput.equalsIgnoreCase(defaultAllergy)) {
                isReAddingOriginalDefault = true;
                break;
            }
        }

        if (!isReAddingOriginalDefault) {
            if (getCurrentCustomAllergyCount() >= MAX_CUSTOM_ALLERGIES) {
                Toast.makeText(getContext(), "You can add up to " + MAX_CUSTOM_ALLERGIES + " new custom allergies.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        // Add with the casing the user entered
        allergyList.add(newAllergyInput);
        adapter.notifyItemInserted(allergyList.size() - 1);
        saveAllergies();
        etNewAllergy.setText(""); // Clear input field
    }

    private void removeAllergy(int position) {
        if (position < 0 || position >= allergyList.size()) {
            return; // Should not happen with valid adapter position
        }
        allergyList.remove(position);
        adapter.notifyItemRemoved(position);
        // If items after the removed one need their positions updated for listeners,
        // you might need notifyItemRangeChanged. For simple removal, this is often enough.
        // adapter.notifyItemRangeChanged(position, allergyList.size() - position); 
        saveAllergies();
    }

    // Inner class for RecyclerView Adapter
    private static class AllergyAdapter extends RecyclerView.Adapter<AllergyAdapter.ViewHolder> {
        private final List<String> allergies;
        private final OnAllergyDeleteListener deleteListener;

        interface OnAllergyDeleteListener {
            void onDelete(int position);
        }

        AllergyAdapter(List<String> allergies, OnAllergyDeleteListener listener) {
            this.allergies = allergies;
            this.deleteListener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_allergy, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String allergy = allergies.get(position);
            holder.tvAllergyName.setText(allergy);
            // Delete button is always visible
            holder.btnDeleteAllergy.setVisibility(View.VISIBLE);
            holder.btnDeleteAllergy.setOnClickListener(v -> {
                if (holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                    deleteListener.onDelete(holder.getAdapterPosition());
                }
            });
        }

        @Override
        public int getItemCount() {
            return allergies.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvAllergyName;
            ImageButton btnDeleteAllergy;

            ViewHolder(View itemView) {
                super(itemView);
                tvAllergyName = itemView.findViewById(R.id.tvAllergyName);
                btnDeleteAllergy = itemView.findViewById(R.id.btnDeleteAllergy);
            }
        }
    }
}
