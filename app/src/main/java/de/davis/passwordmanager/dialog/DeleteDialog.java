package de.davis.passwordmanager.dialog;

import android.content.Context;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.database.SecureElementManager;
import de.davis.passwordmanager.database.dto.SecureElement;

public class DeleteDialog {

    private final MaterialAlertDialogBuilder builder;

    public DeleteDialog(Context context) {
        this.builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.permanently_delete)
                .setMessage(R.string.sure_delete);
    }

    public void show(List<SecureElement> toDelete){ // if toDelete is null -> delete selected
        builder.setNegativeButton(R.string.no, (dialog, which) -> {})
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    toDelete.forEach(SecureElementManager::deleteElement);

                    Toast.makeText(builder.getContext(), R.string.successful_deleted, Toast.LENGTH_LONG).show();
                }).show();
    }
}
