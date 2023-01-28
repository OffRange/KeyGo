package de.davis.passwordmanager.ui.elements.password;

import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.textfield.TextInputLayout;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.databinding.ActivityViewPasswordBinding;
import de.davis.passwordmanager.listeners.OnInformationChangedListener;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.password.PasswordDetails;
import de.davis.passwordmanager.ui.elements.ViewSecureElementActivity;
import de.davis.passwordmanager.ui.views.PasswordStrengthBar;
import de.davis.passwordmanager.utils.BrowserUtil;

public class ViewPasswordActivity extends ViewSecureElementActivity {

    private ActivityViewPasswordBinding binding;
    private EditText editText;

    @Override
    protected void fillInElement(@NonNull SecureElement password) {
        super.fillInElement(password);

        ActivityResultManager activityResultManager = ActivityResultManager.getOrCreateManager(getClass(), this);
        activityResultManager.registerGeneratePassword(result -> editText.setText(result));

        PasswordDetails details = (PasswordDetails) password.getDetail();

        binding.password.setInformation(details.getPassword());
        binding.password.setOnChangedListener(new OnInformationChangedListener<>(password, (element, changes) -> {
            details.setPassword(changes);
            setStrengthValues(details);
            return details;
        }));
        binding.password.setOnEditDialogViewCreatedListener(view -> {
            editText = ((TextInputLayout) view.findViewById(R.id.textInputLayout)).getEditText();
            PasswordStrengthBar passwordStrengthBar = view.findViewById(R.id.strengthBar);
            passwordStrengthBar.update(editText.getText().toString(), false);
            editText.addTextChangedListener(passwordStrengthBar);

            view.findViewById(R.id.generate).setOnClickListener(v -> activityResultManager.launchGeneratePassword(this));
        });

        binding.origin.setInformation(details.getOrigin());
        binding.origin.setOnChangedListener(new OnInformationChangedListener<>(password, (element, changes) -> {
            details.setOrigin(changes);
            manageOrigin(details);
            return details;
        }));

        binding.username.setInformation(details.getUsername());
        binding.username.setOnChangedListener(new OnInformationChangedListener<>(password, (element, changes) -> {
            details.setUsername(changes);
            return details;
        }));

        setStrengthValues(details);
        manageOrigin(details);
    }

    private void setStrengthValues(PasswordDetails details){
        binding.strength.setInformation(details.getStrength().getString());
        binding.strength.setInformationTextColor(details.getStrength().getColor(this));
    }

    private void manageOrigin(PasswordDetails details){
        if(BrowserUtil.isValidURL(BrowserUtil.ensureProtocol(details.getOrigin()))) {
            binding.origin.setOnEndButtonClickListener(v -> BrowserUtil.open(details.getOrigin(), this));
            binding.origin.setEndButtonDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_open_in_new_24));
        }
    }

    @Override
    public View getContentView() {
        if(binding == null)
            binding = ActivityViewPasswordBinding.inflate(getLayoutInflater());

        return binding.getRoot();
    }
}
