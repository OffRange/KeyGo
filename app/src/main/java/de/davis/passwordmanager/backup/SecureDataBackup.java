package de.davis.passwordmanager.backup;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.textfield.TextInputLayout;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.dialog.EditDialogBuilder;
import de.davis.passwordmanager.ui.views.InformationView;

public abstract class SecureDataBackup extends DataBackup {

    private String password;

    public SecureDataBackup(Context context) {
        super(context);
    }

    public String getPassword() {
        return password;
    }

    private void requestPassword(@Type int type, @Nullable Uri uri, OnSyncedHandler onSyncedHandler){
        InformationView.Information i = new InformationView.Information();
        i.setHint(getContext().getString(R.string.password));
        i.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
        i.setSecret(true);

        AlertDialog alertDialog = new EditDialogBuilder(getContext())
                .setTitle(R.string.password)
                .setPositiveButton(R.string.yes, (dialog, which) -> {})
                .withInformation(i)
                .withStartIcon(AppCompatResources.getDrawable(getContext(), R.drawable.ic_baseline_password_24))
                .setCancelable(type == TYPE_IMPORT)
                .show();

        /*
        Needed for the error message that appears when the password (field) is empty.
        otherwise the dialogue would close itself
        */
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = ((EditText)alertDialog.findViewById(R.id.textInputEditText)).getText().toString();

            if(password.isEmpty()){
                ((TextInputLayout)alertDialog.findViewById(R.id.textInputLayout))
                        .setError(getContext().getString(R.string.is_not_filled_in));
                return;
            }

            alertDialog.dismiss();
            this.password = password;


            super.execute(type, uri, onSyncedHandler);
        });
    }

    @Override
    public void execute(int type, @Nullable Uri uri, OnSyncedHandler onSyncedHandler) {
        requestPassword(type, uri, onSyncedHandler);
    }
}
