package de.davis.passwordmanager.ui.elements.password;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.databinding.ActivityCreatePasswordBinding;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.password.PasswordDetails;
import de.davis.passwordmanager.ui.elements.CreateSecureElementActivity;

public class CreatePasswordActivity extends CreateSecureElementActivity {

    private ActivityCreatePasswordBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Objects.requireNonNull(binding.textInputLayoutPassword.getEditText()).addTextChangedListener(binding.strengthBar);

        binding.textInputLayoutPassword.getEditText().addTextChangedListener(binding.strengthBar);

        ActivityResultManager activityResultManager = ActivityResultManager.getOrCreateManager(getClass(), this);
        activityResultManager.registerGeneratePassword(result -> binding.textInputLayoutPassword.getEditText().setText(result));
        binding.generate.setOnClickListener(v -> activityResultManager.launchGeneratePassword(this));
    }

    @Override
    public View getContentView() {
        if(binding == null)
            binding = ActivityCreatePasswordBinding.inflate(getLayoutInflater());

        return binding.getRoot();
    }

    @Override
    protected void fillInElement(@NonNull SecureElement element) {
        binding.textInputLayoutTitle.getEditText().setText(element.getTitle());
        binding.textInputLayoutPassword.getEditText().setText(((PasswordDetails)element.getDetail()).getPassword());
        binding.textInputLayoutUsername.getEditText().setText(((PasswordDetails)element.getDetail()).getUsername());
        binding.textInputLayoutOrigin.getEditText().setText(((PasswordDetails)element.getDetail()).getOrigin());
    }

    @Override
    public CreateSecureElementActivity.Result check() {
        Result result = new Result();
        result.setSuccess(true);

        if(Objects.requireNonNull(binding.textInputLayoutTitle.getEditText()).getText().toString().trim().isEmpty()){
            binding.textInputLayoutTitle.setError(getString(R.string.is_not_filled_in));
            binding.textInputLayoutTitle.requestFocus();
            result.setSuccess(false);
        }else
            binding.textInputLayoutTitle.setErrorEnabled(false);

        if(Objects.requireNonNull(binding.textInputLayoutPassword.getEditText()).getText().toString().trim().isEmpty()){
            binding.textInputLayoutPassword.setError(getString(R.string.is_not_filled_in));
            binding.textInputLayoutPassword.requestFocus();
            result.setSuccess(false);
        }else
            binding.textInputLayoutPassword.setErrorEnabled(false);


        if(result.isSuccess())
            result.setElement(toPassword());

        return result;
    }

    private SecureElement toPassword(){
        String title = Objects.requireNonNull(binding.textInputLayoutTitle.getEditText()).getText().toString();
        String password = Objects.requireNonNull(binding.textInputLayoutPassword.getEditText()).getText().toString();
        String user = Objects.requireNonNull(binding.textInputLayoutUsername.getEditText()).getText().toString();
        String origin = Objects.requireNonNull(binding.textInputLayoutOrigin.getEditText()).getText().toString();


        PasswordDetails details = new PasswordDetails(password, origin, user);
        if(getElement() == null)
            return new SecureElement(details, title);

        getElement().setTitle(title);
        getElement().encrypt(details);
        return getElement();
    }
}
