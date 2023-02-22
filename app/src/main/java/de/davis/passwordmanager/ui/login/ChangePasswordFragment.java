package de.davis.passwordmanager.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import java.util.Objects;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.databinding.FragmentChangePasswordBinding;
import de.davis.passwordmanager.security.Authentication;
import de.davis.passwordmanager.security.Cryptography;
import de.davis.passwordmanager.security.MasterPassword;
import de.davis.passwordmanager.ui.MainActivity;
import de.davis.passwordmanager.utils.PreferenceUtil;

public class ChangePasswordFragment extends Fragment {

    private FragmentChangePasswordBinding binding;

    private boolean shouldShowFingerprintSwitch;

    public ChangePasswordFragment() {
        this(false);
    }

    public ChangePasswordFragment(boolean shouldShowFingerprintSwitch) {
        super();
        this.shouldShowFingerprintSwitch = shouldShowFingerprintSwitch;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().setTitle(R.string.change_master_password);

        if(savedInstanceState != null)
            shouldShowFingerprintSwitch = savedInstanceState.getBoolean("shouldShowFingerprintSwitch");

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.continue_menu, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if(menuItem.getItemId() != R.id.menu_continue)
                    return true;

                String password = Objects.requireNonNull(binding.textInputLayoutPassword.getEditText()).getText().toString();
                String newPassword = Objects.requireNonNull(binding.textInputLayoutNewPassword.getEditText()).getText().toString();
                String confirmedPassword = Objects.requireNonNull(binding.textInputLayoutConfirmPassword.getEditText()).getText().toString();

                boolean hasErrors = false;

                if(binding.textInputLayoutPassword.getVisibility() != View.GONE){
                    if(password.trim().isEmpty()){
                        binding.textInputLayoutPassword.setError(getString(R.string.is_not_filled_in));
                        hasErrors = true;
                    }else if(!Cryptography.checkBcryptHash(password, MasterPassword.getOne().blockingGet().getHash())){
                        binding.textInputLayoutPassword.setError(getString(R.string.password_does_not_match));
                        hasErrors = true;
                    }else
                        binding.textInputLayoutPassword.setErrorEnabled(false);
                }else
                    binding.textInputLayoutPassword.setErrorEnabled(false);

                if(newPassword.trim().isEmpty()){
                    binding.textInputLayoutNewPassword.setError(getString(R.string.is_not_filled_in));
                    hasErrors = true;
                }else
                    binding.textInputLayoutNewPassword.setErrorEnabled(false);

                if(!confirmedPassword.equals(newPassword)){
                    binding.textInputLayoutConfirmPassword.setError(getString(R.string.password_does_not_match));
                    hasErrors = true;
                }else
                    binding.textInputLayoutConfirmPassword.setErrorEnabled(false);

                if(hasErrors)
                    return true;

                if(MasterPassword.changeMasterPassword(password, newPassword)){
                    PreferenceUtil.putBoolean(getContext(), R.string.preference_fingerprint, binding.fingerprint.isChecked());
                    Toast.makeText(getContext(), R.string.master_password_changed, Toast.LENGTH_LONG).show();

                    if(requireActivity().getIntent().getExtras() == null)
                        startActivity(new Intent(requireContext(), MainActivity.class));
                }

                return true;
            }
        }, this, Lifecycle.State.STARTED);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("shouldShowFingerprintSwitch", shouldShowFingerprintSwitch);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentChangePasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Objects.requireNonNull(binding.textInputLayoutNewPassword.getEditText()).addTextChangedListener(binding.strengthBar);
        binding.textInputLayoutPassword.setVisibility(MasterPassword.getOne().isEmpty().blockingGet() ? View.GONE : View.VISIBLE);

        binding.textInputLayoutNewPassword.getEditText().addTextChangedListener(binding.strengthBar);

        binding.fingerprint.setVisibility(shouldShowFingerprintSwitch && Authentication.isAvailable(getContext()) ? View.VISIBLE : View.GONE);
        binding.fingerprint.setChecked(PreferenceUtil.getBoolean(getContext(), R.string.preference_fingerprint, false));
    }
}
