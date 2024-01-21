package de.davis.passwordmanager.ui.elements.password;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.ElementType;
import de.davis.passwordmanager.database.dtos.SecureElement;
import de.davis.passwordmanager.database.entities.Tag;
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails;
import de.davis.passwordmanager.databinding.ActivityCreatePasswordBinding;
import de.davis.passwordmanager.manager.ActivityResultManager;
import de.davis.passwordmanager.ui.elements.CreateSecureElementActivity;

public class CreatePasswordActivity extends CreateSecureElementActivity {

    private ActivityCreatePasswordBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityResultManager activityResultManager = ActivityResultManager.getOrCreateManager(getClass(), this);
        activityResultManager.registerGeneratePassword(result -> binding.textInputLayoutPassword.getEditText().setText(result));
        binding.generate.setOnClickListener(v -> activityResultManager.launchGeneratePassword(this));
    }

    @Override
    public View getContentView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if(binding == null)
            binding = ActivityCreatePasswordBinding.inflate(inflater, container, false);

        binding.textInputLayoutPassword.getEditText().addTextChangedListener(binding.strengthBar);
        return binding.getRoot();
    }

    @Override
    public void fillInElement(@NonNull SecureElement element) {
        super.fillInElement(element);
        binding.textInputLayoutTitle.getEditText().setText(element.getTitle());
        binding.textInputLayoutPassword.getEditText().setText(((PasswordDetails)element.getDetail()).getPassword());
        binding.textInputLayoutUsername.getEditText().setText(((PasswordDetails)element.getDetail()).getUsername());
        binding.textInputLayoutOrigin.getEditText().setText(((PasswordDetails)element.getDetail()).getOrigin());
    }

    @Override
    public CreateSecureElementActivity.Result check() {
        Result result = new Result();
        result.setSuccess(true);

        if(Objects.requireNonNull(binding.textInputLayoutTitle.getEditText()).getText().toString().isBlank()){
            binding.textInputLayoutTitle.setError(getString(R.string.is_not_filled_in));
            binding.textInputLayoutTitle.requestFocus();
            result.setSuccess(false);
        }else
            binding.textInputLayoutTitle.setErrorEnabled(false);

        if(Objects.requireNonNull(binding.textInputLayoutPassword.getEditText()).getText().toString().isBlank()){
            binding.textInputLayoutPassword.setError(getString(R.string.is_not_filled_in));
            binding.textInputLayoutPassword.requestFocus();
            result.setSuccess(false);
        }else
            binding.textInputLayoutPassword.setErrorEnabled(false);


        if(result.isSuccess())
            result.setElement(toElement());

        return result;
    }

    @Override
    protected SecureElement toElement(){
        String title = Objects.requireNonNull(binding.textInputLayoutTitle.getEditText()).getText().toString().trim();
        String password = Objects.requireNonNull(binding.textInputLayoutPassword.getEditText()).getText().toString();
        String user = Objects.requireNonNull(binding.textInputLayoutUsername.getEditText()).getText().toString().trim();
        String origin = Objects.requireNonNull(binding.textInputLayoutOrigin.getEditText()).getText().toString().trim();


        List<Tag> tags = binding.tagView.getTags();
        PasswordDetails details = new PasswordDetails(password, origin, user);
        if(getElement() == null)
            return new SecureElement(title, details, tags);

        getElement().setTags(tags);
        getElement().setTitle(title);
        getElement().setDetail(details);
        return getElement();
    }

    @Override
    public ElementType getSecureElementType() {
        return ElementType.PASSWORD;
    }
}
