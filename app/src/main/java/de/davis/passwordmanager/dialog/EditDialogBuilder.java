package de.davis.passwordmanager.dialog;

import static com.google.android.material.textfield.TextInputLayout.END_ICON_NONE;
import static com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import de.davis.passwordmanager.databinding.DialogEditViewBinding;
import de.davis.passwordmanager.ui.views.InformationView;

public class EditDialogBuilder extends BaseDialogBuilder<EditDialogBuilder> {

    private InformationView.Information information;

    private Drawable startIcon;

    @LayoutRes
    private int additionalCustomLayout;

    public EditDialogBuilder(@NonNull Context context) {
        super(context);
    }

    public EditDialogBuilder(@NonNull Context context, int overrideThemeResId) {
        super(context, overrideThemeResId);
    }

    @Override
    public View onCreateView(LayoutInflater inflater) {
        DialogEditViewBinding binding = DialogEditViewBinding.inflate(inflater);

        binding.textInputLayout.setEndIconMode(information.isSecret() ? END_ICON_PASSWORD_TOGGLE : END_ICON_NONE);
        binding.textInputEditText.setInputType(information.getInputType());
        binding.textInputEditText.setTypeface(Typeface.DEFAULT);
        binding.textInputEditText.setTransformationMethod(information.isSecret() ?
                information.getTransformationMethod() : null);

        binding.textInputLayout.setHint(information.getHint());
        binding.textInputEditText.setText(information.getText());

        binding.textInputLayout.setStartIconDrawable(startIcon);

        if(information.getMaxLength() >= 0){
            binding.textInputEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(information.getMaxLength())});
        }

        if(additionalCustomLayout != 0){
            inflater.inflate(additionalCustomLayout, binding.getRoot(), true);
        }

        return binding.getRoot();
    }

    public EditDialogBuilder withInformation(InformationView.Information information){
        this.information = information;
        return this;
    }

    public EditDialogBuilder withStartIcon(Drawable startIcon){
        this.startIcon = startIcon;
        return this;
    }

    public EditDialogBuilder withAdditionalCustomLayout(@LayoutRes int additionalCustomLayout){
        this.additionalCustomLayout = additionalCustomLayout;
        return this;
    }
}
