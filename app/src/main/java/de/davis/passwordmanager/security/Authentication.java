package de.davis.passwordmanager.security;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import javax.crypto.Cipher;

import de.davis.passwordmanager.Keys;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.utils.KeyUtil;

public class Authentication {

    private static Authentication instance;


    private Authentication(){}

    public static Authentication getInstance(){
        if(instance == null)
            instance = new Authentication();

        return instance;
    }

    public void auth(String negativeButtonText, @NonNull Fragment fragment, @NonNull BiometricPrompt.AuthenticationCallback callback) {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(fragment.getString(R.string.authentication_title))
                .setDescription(fragment.getString(R.string.authentication_description))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(negativeButtonText)
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(fragment, ContextCompat.getMainExecutor(fragment.requireContext()), callback);
        biometricPrompt.authenticate(promptInfo);
    }

    public void auth(@NonNull Fragment fragment, @NonNull BiometricPrompt.AuthenticationCallback callback) {
        auth(fragment.getString(R.string.use_password), fragment, callback);
    }

    public static boolean isAvailable(Context context){
        BiometricManager manager = BiometricManager.from(context);
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS;
    }
}
