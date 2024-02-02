package de.davis.passwordmanager.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import de.davis.passwordmanager.databinding.LoadingLayoutBinding;

public class LoadingDialog extends BaseDialogBuilder<LoadingDialog> {

    private LoadingLayoutBinding binding;
    private AlertDialog alertDialog;

    public LoadingDialog(@NonNull Context context) {
        super(context);
        setCancelable(false);
    }

    public void dismiss(){
        alertDialog.dismiss();
    }

    public void setMax(int max){
        binding.progress.setMax(max);
    }

    public void updateProgress(int progress) {
        binding.progress.setProgressCompat(progress, true);
        binding.progress.setIndeterminate(false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater) {
        binding = LoadingLayoutBinding.inflate(inflater);
        return binding.getRoot();
    }

    @NonNull
    @Override
    public AlertDialog create() {
        return alertDialog = super.create();
    }
}
