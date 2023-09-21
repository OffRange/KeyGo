package de.davis.passwordmanager.ui.login;

import static android.view.autofill.AutofillManager.EXTRA_ASSIST_STRUCTURE;
import static android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT;
import static de.davis.passwordmanager.service.Response.EXTRA_FILL_REQUEST;

import android.app.Activity;
import android.app.assist.AssistStructure;
import android.content.Intent;
import android.os.Bundle;
import android.service.autofill.FillRequest;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.databinding.FragmentEnterPasswordBinding;
import de.davis.passwordmanager.security.Authentication;
import de.davis.passwordmanager.security.Cryptography;
import de.davis.passwordmanager.security.MasterPassword;
import de.davis.passwordmanager.service.ParsedStructure;
import de.davis.passwordmanager.service.Response;
import de.davis.passwordmanager.ui.MainActivity;
import de.davis.passwordmanager.utils.PreferenceUtil;

public class EnterPasswordFragment extends Fragment {

    private FragmentEnterPasswordBinding binding;

    private AuthenticationHandler authenticationHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authenticationHandler = new AuthenticationHandler() {

            @Override
            public Intent onSuccess() {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    AssistStructure structure = requireActivity().getIntent().getParcelableExtra(EXTRA_ASSIST_STRUCTURE);
                    FillRequest request = requireActivity().getIntent().getParcelableExtra(EXTRA_FILL_REQUEST);

                    if(structure != null)
                        return new Intent().putExtra(EXTRA_AUTHENTICATION_RESULT, new Response(getContext(), ParsedStructure.parse(structure, requireContext()), request).createRealResponse());

                }

                Intent intent = requireActivity().getIntent().getParcelableExtra(getString(R.string.authentication_destination));
                if(intent != null){
                    startActivity(intent);
                    return null;
                }

                startActivity(new Intent(getContext(), MainActivity.class)
                        .setData(requireActivity().getIntent().getData()));
                return null;
            }
        };
    }

    private abstract static class AuthenticationHandler {

        public AuthenticationHandler() {}

        protected abstract Intent onSuccess();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentEnterPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding.btnFingerprint.setVisibility(Authentication.isAvailable(getContext()) && PreferenceUtil.getBoolean(requireContext(), R.string.preference_fingerprint, false) ? View.VISIBLE : View.GONE);
        binding.btnFingerprint.setOnClickListener(v -> authenticate());

        binding.btnConfirm.setOnClickListener(v -> {
            byte[] hash = MasterPassword.getOne().blockingGet().getHash();

            String password = Objects.requireNonNull(binding.textInputLayoutPassword.getEditText()).getText().toString();
            if(password.trim().isEmpty()){
                binding.textInputLayoutPassword.setError(getString(R.string.is_not_filled_in));
                return;
            }

            if(!Cryptography.checkBcryptHash(password, hash)){
                binding.textInputLayoutPassword.setError(getString(R.string.password_does_not_match));
                return;
            }

            handleSuccess();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        authenticate();
    }

    private void authenticate(){
        if(PreferenceUtil.getBoolean(requireContext(), R.string.preference_fingerprint, false) && Authentication.isAvailable(getContext()))
            Authentication.getInstance().auth(this, new Callback());
    }

    private void handleSuccess(){
        Intent a = authenticationHandler.onSuccess();
        if(a != null){
            requireActivity().setResult(Activity.RESULT_OK, a);
        }
        requireActivity().finish();
    }

    private class Callback extends BiometricPrompt.AuthenticationCallback{
        @Override
        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
            super.onAuthenticationSucceeded(result);
            handleSuccess();
        }

        @Override
        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
            super.onAuthenticationError(errorCode, errString);
        }
    }
}
