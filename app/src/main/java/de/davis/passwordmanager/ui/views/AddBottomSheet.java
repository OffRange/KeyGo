package de.davis.passwordmanager.ui.views;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import de.davis.passwordmanager.databinding.AddBottomSheetContentBinding;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.security.element.SecureElementDetail;
import de.davis.passwordmanager.ui.dashboard.DashboardFragment;

public class AddBottomSheet extends BottomSheetDialogFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        AddBottomSheetContentBinding binding = AddBottomSheetContentBinding.inflate(inflater, container, false);

        for (SecureElementDetail detail : SecureElementDetail.getRegisteredDetails().values()){
            AddButton btn = new AddButton(requireContext());
            btn.setText(detail.getTitle());
            btn.setIcon(detail.getIcon());
            btn.setOnClickListener(v -> {
                ActivityResultManager.getOrCreateManager(DashboardFragment.class, null).launchCreate(detail, requireContext());
                dismiss();
            });

            binding.linearLayout.addView(btn);
        }

        return binding.getRoot();
    }
}
