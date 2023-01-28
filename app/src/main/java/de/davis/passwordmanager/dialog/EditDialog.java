package de.davis.passwordmanager.dialog;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.ui.views.InformationView;

public class EditDialog extends BasicDialog {

    private Configuration configuration = new Configuration();


    public EditDialog(Configuration configuration) {
        this.configuration = configuration;
    }

    public EditDialog() {}

    @Override
    public MaterialAlertDialogBuilder onCreateMaterialAlertDialogBuilder(Bundle savedInstanceState) {
        return super.onCreateMaterialAlertDialogBuilder(savedInstanceState)
                .setTitle(getString(R.string.change_param, configuration.details.getTitle()))
                .setPositiveButton(R.string.text_continue, (dialog, which) -> {
                    if(configuration.onInformationChangeListener == null)
                        return;

                    configuration.onInformationChangeListener.onInformationChanged(this, configuration.details.getInformation());
                    dismiss();
                });
    }

    @Override
    public View viewProvider(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ViewGroup view = (LinearLayout) inflater.inflate(R.layout.dialog_edit_view, container, false);

        if(savedInstanceState != null)
            configuration.details = savedInstanceState.getParcelable("details");

        if(configuration.details == null)
            return view;


        TextInputLayout textInputLayout = view.findViewById(R.id.textInputLayout);
        textInputLayout.getEditText().setText(configuration.initialTextPolicy.initialString(configuration.details.getInformation()));
        textInputLayout.getEditText().addTextChangedListener(new TextChangedListener());
        textInputLayout.setStartIconDrawable(configuration.details.getDrawable());
        textInputLayout.setHint(configuration.details.getTitle());

        textInputLayout.getEditText().setInputType(configuration.details.getInputType());
        textInputLayout.setEndIconMode(configuration.details.isSecret() ? TextInputLayout.END_ICON_PASSWORD_TOGGLE : TextInputLayout.END_ICON_NONE);

        if(configuration.details.getMaxLength() > 0)
            textInputLayout.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(configuration.details.getMaxLength())});

        if(configuration.additionalView == 0)
            return view;


        inflater.inflate(configuration.additionalView, view, true);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if(configuration.onEditDialogViewCreatedListener != null)
            configuration.onEditDialogViewCreatedListener.onViewCreated(view);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("details", configuration.details);
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
            configuration.details.setInformation(s.toString());
        }
    }

    public interface InitialTextPolicy {
        String initialString(String text);
    }

    public static class Configuration {

        private EditDialog.OnInformationChangeListener onInformationChangeListener;
        private EditDialog.OnViewCreatedListener onEditDialogViewCreatedListener;

        private InitialTextPolicy initialTextPolicy = text -> text;

        private InformationView.Details details;

        @LayoutRes
        private int additionalView;

        public void setDetails(InformationView.Details details) {
            this.details = details;
        }

        public OnInformationChangeListener getOnInformationChangeListener() {
            return onInformationChangeListener;
        }

        public void setOnInformationChangeListener(OnInformationChangeListener onInformationChangeListener) {
            this.onInformationChangeListener = onInformationChangeListener;
        }

        public void setAdditionalView(int additionalView) {
            this.additionalView = additionalView;
        }

        public OnViewCreatedListener getOnEditDialogViewCreatedListener() {
            return onEditDialogViewCreatedListener;
        }

        public void setOnEditDialogViewCreatedListener(OnViewCreatedListener onEditDialogViewCreatedListener) {
            this.onEditDialogViewCreatedListener = onEditDialogViewCreatedListener;
        }

        public InitialTextPolicy getInitialTextPolicy() {
            return initialTextPolicy;
        }

        public void setInitialTextPolicy(InitialTextPolicy initialTextPolicy) {
            this.initialTextPolicy = initialTextPolicy;
        }

        public InformationView.Details getDetails() {
            return details;
        }

        public int getAdditionalView() {
            return additionalView;
        }
    }
}
