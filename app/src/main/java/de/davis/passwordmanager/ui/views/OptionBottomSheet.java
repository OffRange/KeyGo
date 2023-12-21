package de.davis.passwordmanager.ui.views;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.SecureElementManager;
import de.davis.passwordmanager.database.dtos.SecureElement;
import de.davis.passwordmanager.databinding.MoreBottomSheetContentBinding;
import de.davis.passwordmanager.dialog.DeleteDialog;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.ui.dashboard.DashboardFragment;

public class OptionBottomSheet extends BottomSheetDialogFragment {

    private MoreBottomSheetContentBinding binding;
    private final List<SecureElement> elements;

    public OptionBottomSheet(List<SecureElement> elements) {
        this.elements = elements;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return (binding = MoreBottomSheetContentBinding.inflate(getLayoutInflater())).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(elements.isEmpty())
            return;

        SecureElement firstElement = elements.get(0);

        binding.title.setText(elements.size() > 1 ? requireContext().getString(R.string.options) : firstElement.getTitle());

        if(elements.size() > 1) {
            binding.edit.setVisibility(View.GONE);
            binding.favorite.setVisibility(View.GONE);
        }else {
            binding.edit.setOnClickListener(v -> {
                ActivityResultManager.getOrCreateManager(DashboardFragment.class, null).launchEdit(firstElement, getContext());
                dismiss();
            });

            binding.favorite.setOnClickListener(v -> {
                SecureElementManager.switchFavState(firstElement);
                dismiss();
            });

            binding.favorite.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    firstElement.getFavorite() ?
                            R.drawable.baseline_star_24
                            : R.drawable.baseline_star_outline_24,
                    0, 0, 0);

            binding.favorite.setText(firstElement.getFavorite() ? R.string.remove_from_favorite : R.string.mark_as_favorite);
        }

        binding.delete.setOnClickListener(v -> {
            new DeleteDialog(getContext()).show(elements);
            dismiss();
        });
    }
}
