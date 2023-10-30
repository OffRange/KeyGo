package de.davis.passwordmanager.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import de.davis.passwordmanager.databinding.LoadingLayoutBinding;

public class LoadingDialog extends BaseDialogBuilder<LoadingDialog> {

    private LoadingLayoutBinding binding;

    public LoadingDialog(@NonNull Context context) {
        super(context);
        setCancelable(false);
    }

    public void updateProgress(int current, int max){
        double progress = (current * 100d / max);
        binding.progress.setIndeterminate(false);
        binding.progress.setProgressCompat((int) progress, true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater) {
        binding = LoadingLayoutBinding.inflate(inflater);
        return binding.getRoot();
    }
}
