package de.davis.passwordmanager.ui.views;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import de.davis.passwordmanager.database.ElementType;
import de.davis.passwordmanager.databinding.AddBottomSheetContentBinding;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.ui.dashboard.DashboardFragment;

public class AddBottomSheet extends BottomSheetDialogFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        AddBottomSheetContentBinding binding = AddBottomSheetContentBinding.inflate(inflater, container, false);

        for (ElementType type : ElementType.values()){
            AddButton btn = new AddButton(requireContext());
            btn.setText(type.getTitle());
            btn.setIcon(type.getIcon());
            btn.setOnClickListener(v -> {
                ActivityResultManager.getOrCreateManager(DashboardFragment.class, null).launchCreate(type.getCreateActivityClass(), requireContext());
                dismiss();
            });

            binding.linearLayout.addView(btn);
        }

        return binding.getRoot();
    }
}
