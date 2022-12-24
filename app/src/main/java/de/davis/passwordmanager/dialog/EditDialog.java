package de.davis.passwordmanager.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Map;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.ui.views.InformationView;

public class EditDialog extends BasicDialog {

    private InformationView.Details details;

    @LayoutRes
    private int additionalView;

    private OnInformationChangeListener onInformationChangeListener;
    private OnViewCreatedListener onViewCreatedListener;


    public EditDialog(InformationView.Details details) {
        this(details, 0);
    }

    public EditDialog(InformationView.Details details, @LayoutRes int additionalView) {
        this.details = details;
        this.additionalView = additionalView;
    }

    public EditDialog() {}

    public void setOnChangeListener(OnInformationChangeListener onInformationChangeListener) {
        this.onInformationChangeListener = onInformationChangeListener;
    }

    public void setOnViewCreatedListener(OnViewCreatedListener onViewCreatedListener) {
        this.onViewCreatedListener = onViewCreatedListener;
    }

    @Override
    public MaterialAlertDialogBuilder onCreateMaterialAlertDialogBuilder(Bundle savedInstanceState) {
        return super.onCreateMaterialAlertDialogBuilder(savedInstanceState)
                .setTitle(getString(R.string.change_param, details.getTitle()))
                .setPositiveButton(R.string.text_continue, (dialog, which) -> {
                    if(onInformationChangeListener == null)
                        return;

                    onInformationChangeListener.onInformationChanged(this, details.getInformation());
                    dismiss();
                });
    }

    @Override
    public View viewProvider(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ViewGroup view = (LinearLayout) inflater.inflate(R.layout.dialog_edit_view, container, false);

        if(savedInstanceState != null)
            details = savedInstanceState.getParcelable("details");

        if(details == null)
            return view;

        TextInputLayout textInputLayout = view.findViewById(R.id.textInputLayout);
        textInputLayout.getEditText().setTransformationMethod(details.isSecret() ? PasswordTransformationMethod.getInstance() : null);
        textInputLayout.getEditText().setText(details.getInformation());
        textInputLayout.getEditText().addTextChangedListener(new TextChangedListener());
        textInputLayout.setStartIconDrawable(details.getDrawable());
        textInputLayout.setHint(details.getTitle());
        textInputLayout.setEndIconMode(details.isSecret() ? TextInputLayout.END_ICON_PASSWORD_TOGGLE : TextInputLayout.END_ICON_NONE);
        if(details.getMaxLength() > 0)
            textInputLayout.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(details.getMaxLength())});

        textInputLayout.getEditText().setInputType(details.getInputType());

        if(additionalView == 0)
            return view;


        inflater.inflate(additionalView, view, true);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if(onViewCreatedListener != null)
            onViewCreatedListener.onViewCreated(view);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("details", details);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ((ViewGroup)requireView()).removeAllViews();
    }

    public interface OnInformationChangeListener {
        void onInformationChanged(EditDialog dialog, String information);
    }

    public interface OnViewCreatedListener {
        void onViewCreated(View view);
    }

    private class TextChangedListener implements TextWatcher{

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            details.setInformation(s.toString());
        }
    }
}
