package de.davis.passwordmanager.ui.elements.password;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.HandlerCompat;

import com.google.android.material.slider.Slider;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.davis.passwordmanager.Keys;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.entities.details.password.EstimationHandler;
import de.davis.passwordmanager.database.entities.details.password.Strength;
import de.davis.passwordmanager.databinding.ActivityGeneratePasswordBinding;
import de.davis.passwordmanager.utils.AssetsUtil;
import de.davis.passwordmanager.utils.GeneratorUtil;
import de.davis.passwordmanager.utils.TimeoutUtil;

public class GeneratePasswordActivity extends AppCompatActivity implements Slider.OnChangeListener, Slider.OnSliderTouchListener, RadioGroup.OnCheckedChangeListener, CompoundButton.OnCheckedChangeListener {

    private static final int LENGTH_THRESHOLD = 25;

    private static final int DEFAULT_PASSWORD_MAX_LENGTH = 100;
    private static final int DEFAULT_PASSWORD_MIN_LENGTH = 8;
    private static final int DEFAULT_PASSPHRASE_MAX_LENGTH = 15;
    private static final int DEFAULT_PASSPHRASE_MIN_LENGTH = 3;

    private ActivityGeneratePasswordBinding binding;
    private Handler handler;
    private List<String> words;

    private TimeoutUtil estimationTimeout;

    private TextView passwordView;
    private Slider slider;
    private RadioGroup radioGroup;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGeneratePasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        try {
            words = AssetsUtil.open("30k.txt", this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        handler = HandlerCompat.createAsync(getMainLooper());

        for (int i = 0; i < binding.gridLayout.getChildCount(); i++) {
            binding.gridLayout.getChildAt(i).setOnClickListener(v -> ((Checkable)v).setChecked(!((Checkable)v).isChecked()));
        }

        passwordView = binding.password.getContent().findViewById(android.R.id.text1);
        binding.password.preventInterceptingTouchEvents(passwordView);
        passwordView.setMovementMethod(new ScrollingMovementMethod());


        slider = binding.generatorSlider.getContent().findViewById(R.id.slider);
        slider.addOnChangeListener(this);
        slider.addOnSliderTouchListener(this);
        binding.generatorSlider.setTitle(getString(R.string.length_param, (int)slider.getValue()));

        radioGroup = binding.radioGroupInformationView.getContent().findViewById(R.id.radiogroup);
        radioGroup.setOnCheckedChangeListener(this);


        binding.useDigits.setOnCheckedChangeListener(this);
        binding.useLowercase.setOnCheckedChangeListener(this);
        binding.useUppercase.setOnCheckedChangeListener(this);
        binding.usePunctuations.setOnCheckedChangeListener(this);

        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        binding.toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);

        estimationTimeout = new TimeoutUtil();
        setPassword(generate(), false);
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_continue) {
            setResult(RESULT_OK, new Intent().putExtra(Keys.KEY_NEW, passwordView.getText().toString()));
            finish();
        } else if (itemId == R.id.menu_generate)
            setPassword(generate(), true);


        return true;
    }

    private void setPassword(String password, boolean force){
        if(passwordView.getText().toString().equals(password))
            return;

        passwordView.setText(password);
        if(!force && !estimationTimeout.hasDelayMet(150))
            return;

        estimateStrength(password);
    }

    ExecutorService estimationExecutor;
    private void estimateStrength(String password){
        //The password strength estimation must be done in a background thread, otherwise the
        // main thread would be blocked.
        if(estimationExecutor != null && !estimationExecutor.isShutdown())
            estimationExecutor.shutdownNow();

        if(password.length() > LENGTH_THRESHOLD){
            binding.strength.setInformationText(R.string.loading);
            binding.strength.setInformationTextColor(getColor(android.R.color.darker_gray));
        }

        estimationExecutor = Executors.newSingleThreadExecutor();
        estimationExecutor.execute(()->{
            Strength strength = EstimationHandler.estimate(password);
            handler.post(()->{
                binding.strength.setInformationText(strength.getString());
                binding.strength.setInformationTextColor(strength.getColor(this));
            });
        });

        estimationExecutor.shutdown();
    }

    @Override
    public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
        binding.generatorSlider.setTitle(getString(R.string.length_param, (int)value));
        setPassword(generate(), false);
    }

    @Override
    public void onStartTrackingTouch(@NonNull Slider slider) {}

    @Override
    public void onStopTrackingTouch(@NonNull Slider slider) {
        if(passwordView.length() == slider.getValue())
            return;

        setPassword(generate(), true);
    }

    @Override
    @Deprecated
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        boolean shouldGeneratePassword = shouldGeneratePassword();
        slider.setValueFrom(shouldGeneratePassword ? DEFAULT_PASSWORD_MIN_LENGTH : DEFAULT_PASSPHRASE_MIN_LENGTH);
        slider.setValueTo(shouldGeneratePassword ? DEFAULT_PASSWORD_MAX_LENGTH : DEFAULT_PASSPHRASE_MAX_LENGTH);

        binding.usePunctuations.setVisibility(shouldGeneratePassword ? View.VISIBLE : View.GONE);
        binding.useLowercase.setVisibility(shouldGeneratePassword ? View.VISIBLE : View.GONE);

        boolean uppercase = binding.useUppercase.isChecked();
        boolean lowercase = binding.useLowercase.isChecked();
        boolean digits = binding.useUppercase.isChecked();
        if(!(uppercase | digits) && !shouldGeneratePassword && !lowercase){
            binding.useLowercase.setChecked(true);
        }

        int value = (int)slider.getValue();
        slider.setValue(shouldGeneratePassword() ? Math.max(value, DEFAULT_PASSWORD_MIN_LENGTH) : Math.min(value, DEFAULT_PASSPHRASE_MAX_LENGTH));

        binding.toolbar.setSubtitle(shouldGeneratePassword ? R.string.password : R.string.passphrase);

        setPassword(generate(), true);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if(!(binding.useDigits.isChecked() || binding.useLowercase.isChecked() || binding.useUppercase.isChecked() || binding.usePunctuations.isChecked())){
            buttonView.setChecked(true);
            return;
        }

        setPassword(generate(), true);
    }

    private boolean shouldGeneratePassword(){
        return radioGroup.getCheckedRadioButtonId() == R.id.password;
    }

    @GeneratorUtil.Types
    private int getGenerationChars(){
        int chars = 0;
        if(binding.useDigits.isChecked())
            chars |= GeneratorUtil.USE_DIGITS;

        if(binding.useLowercase.isChecked())
            chars |= GeneratorUtil.USE_LOWERCASE;

        if(binding.useUppercase.isChecked())
            chars |= GeneratorUtil.USE_UPPERCASE;

        if(binding.usePunctuations.isChecked() && shouldGeneratePassword())
            chars |= GeneratorUtil.USE_PUNCTUATION;

        return chars;
    }

    private String generate(){
        int chars = getGenerationChars();
        if(shouldGeneratePassword())
            return GeneratorUtil.generatePassword((int)slider.getValue(), chars);


        return GeneratorUtil.generatePassphrase((int)slider.getValue(), words, chars);
    }
}
