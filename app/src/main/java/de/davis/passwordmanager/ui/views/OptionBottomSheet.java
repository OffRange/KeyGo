package de.davis.passwordmanager.ui.views;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.databinding.MoreBottomSheetContentBinding;
import de.davis.passwordmanager.dialog.DeleteDialog;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.SecureElementManager;
import de.davis.passwordmanager.ui.dashboard.DashboardFragment;

public class OptionBottomSheet extends BottomSheetDialog {

    private final SecureElement element;

    public OptionBottomSheet(Context context, SecureElement element) {
        super(context);
        this.element = element;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MoreBottomSheetContentBinding binding = MoreBottomSheetContentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.title.setText(element == null ? getContext().getString(R.string.options) : element.getTitle());

        binding.edit.setOnClickListener(v -> {
            ActivityResultManager.getOrCreateManager(DashboardFragment.class, null).launchEdit(element, getContext());
            dismiss();
        });


        binding.favorite.setOnClickListener(v -> {
            if(element == null)
                return;

            element.setFavorite(!element.isFavorite());

            SecureElementManager.getInstance().editElement(element);
            dismiss();
        });

        if(element == null) {
            binding.edit.setVisibility(View.GONE);
            binding.favorite.setVisibility(View.GONE);
        }else {
            binding.favorite.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    element.isFavorite() ?
                            R.drawable.baseline_star_24
                            : R.drawable.baseline_star_outline_24,
                    0, 0, 0);

            binding.favorite.setText(element.isFavorite() ? R.string.remove_from_favorite : R.string.mark_as_favorite);
        }


        binding.delete.setOnClickListener(v -> {
            new DeleteDialog(getContext()).show(null, element);
            dismiss();
        });

    }
}
