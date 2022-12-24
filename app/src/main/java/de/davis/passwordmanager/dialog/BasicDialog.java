package de.davis.passwordmanager.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class BasicDialog extends DialogFragment {

    private View view;

    public MaterialAlertDialogBuilder onCreateMaterialAlertDialogBuilder(Bundle savedInstanceState){
        return new MaterialAlertDialogBuilder(requireContext())
                .setView(onCreateView(getLayoutInflater(), null, savedInstanceState));
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return onCreateMaterialAlertDialogBuilder(savedInstanceState).create();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //This class is also called from DialogFragment after onCreateDialog. Since this methode
        //is called to pass the view to the MaterialAlertDialogBuilder in onCreateDialog, this
        //methode does not need to be run twice.
        if(getDialog() != null)
            return null;

        view = viewProvider(inflater, container, savedInstanceState);
        onViewCreated(view, savedInstanceState);
        return view;
    }

    public View viewProvider(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState){
        return super.onCreateView(inflater, container, savedInstanceState);
    }


    @Nullable
    @Override
    public View getView() {
        return view;
    }
}
