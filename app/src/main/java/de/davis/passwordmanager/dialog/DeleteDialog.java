package de.davis.passwordmanager.dialog;

import android.content.Context;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.security.element.SecureElement;
import de.davis.passwordmanager.security.element.SecureElementManager;

public class DeleteDialog {

    private final MaterialAlertDialogBuilder builder;

    public DeleteDialog(Context context) {
        this.builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.permanently_delete)
                .setMessage(R.string.sure_delete);
    }

    public void show(OnClickedListener onClickedListener, SecureElement toDelete){ // if toDelete is null -> delete selected
        builder.setNegativeButton(R.string.no, (dialog, which) -> {
            if(onClickedListener != null)
                onClickedListener.onClicked(false);

            dialog.dismiss();
        }).setPositiveButton(R.string.yes, (dialog, which) -> {
            if(toDelete == null)
                SecureElementManager.getInstance().deleteSelected();
            else
                SecureElementManager.getInstance().delete(toDelete);

            Toast.makeText(builder.getContext(), R.string.successful_deleted, Toast.LENGTH_LONG).show();

            if(onClickedListener != null)
                onClickedListener.onClicked(true);

            dialog.dismiss();
        }).show();
    }

    public interface OnClickedListener{
        void onClicked(boolean deleted);
    }
}
