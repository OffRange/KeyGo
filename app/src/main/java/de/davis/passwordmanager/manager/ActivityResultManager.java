package de.davis.passwordmanager.manager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import de.davis.passwordmanager.Keys;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.SecureElementDetail;
import de.davis.passwordmanager.security.element.SecureElementManager;
import de.davis.passwordmanager.ui.elements.password.GeneratePasswordActivity;

public class ActivityResultManager {

    private static final Map<Class<?>, ActivityResultManager> managers = new HashMap<>();

    private ActivityResultCaller resultCaller;

    private ActivityResultLauncher<Intent> editElement, createElement, generatePassword;

    private ActivityResultManager(ActivityResultCaller resultCaller){
        this.resultCaller = resultCaller;
    }

    public void registerEdit(@Nullable OnResult<SecureElement> onResult){
        editElement = resultCaller.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent intent = checkAndGetData(result);
            if(intent == null)
                return;

            SecureElement element = (SecureElement) intent.getExtras().getSerializable(Keys.KEY_NEW);

            SecureElementManager.getInstance().editElement(element);

            if(onResult != null)
                onResult.onResult(element);
        });
    }

    public void registerCreate(){
        createElement = resultCaller.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent intent = checkAndGetData(result);
            if(intent == null)
                return;

            SecureElement element = (SecureElement) intent.getExtras().getSerializable(Keys.KEY_NEW);

            SecureElementManager.getInstance().createElement(element);
        });
    }

    public void registerGeneratePassword(@NonNull OnResult<String> onResult){
        generatePassword = resultCaller.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent intent = checkAndGetData(result);
            if(intent == null)
                return;

            String password = intent.getExtras().getString(Keys.KEY_NEW);

            onResult.onResult(password);
        });
    }

    public void launchEdit(SecureElement element, Context context){
        if(editElement == null)
            throw new NullPointerException("registerEdit() must be called before launchEdit()");

        editElement.launch(new Intent(context, SecureElementDetail.getFor(element).getCreateActivityClass()).putExtra(Keys.KEY_OLD, element));
    }

    public void launchCreate(SecureElementDetail detail, Context context){
        if(createElement == null)
            throw new NullPointerException("registerCreate() must be called before launchCreate()");

        createElement.launch(new Intent(context, detail.getCreateActivityClass()));
    }

    public void launchGeneratePassword(Context context){
        if(generatePassword == null)
            throw new NullPointerException("registerGeneratePassword() must be called before launchGeneratePassword()");

        generatePassword.launch(new Intent(context, GeneratePasswordActivity.class));
    }

    public static ActivityResultManager getOrCreateManager(Class<?> clazz, ActivityResultCaller resultCaller){
        if(!managers.containsKey(clazz))
            managers.put(clazz, new ActivityResultManager(resultCaller));

        ActivityResultManager arm = managers.get(clazz);
        arm.resultCaller = resultCaller;
        return arm;
    }

    private static Intent checkAndGetData(ActivityResult result){
        if(result.getResultCode() != Activity.RESULT_OK)
            return null;

        Intent intent = result.getData();
        if(intent == null)
            return null;

        if(!intent.hasExtra(Keys.KEY_NEW))
            return null;

        return intent;
    }

    public interface OnResult<E>{
        void onResult(E result);
    }
}
