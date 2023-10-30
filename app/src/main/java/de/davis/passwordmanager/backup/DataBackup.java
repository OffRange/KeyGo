package de.davis.passwordmanager.backup;

import static de.davis.passwordmanager.utils.BackgroundUtil.doInBackground;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.os.HandlerCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.InputStream;
import java.io.OutputStream;

import javax.crypto.AEADBadTagException;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.dialog.LoadingDialog;

public abstract class DataBackup {


    public static final int TYPE_EXPORT = 0;
    public static final int TYPE_IMPORT = 1;

    @IntDef({TYPE_EXPORT, TYPE_IMPORT})
    public @interface Type{}


    private final Context context;
    private LoadingDialog loadingDialog;
    private final Handler handler = HandlerCompat.createAsync(Looper.getMainLooper());

    public DataBackup(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    @Nullable
    protected abstract Result runExport(OutputStream outputStream) throws Exception;
    @Nullable
    protected abstract Result runImport(InputStream inputStream) throws Exception;

    public void execute(@Type int type, @Nullable Uri uri){
        execute(type, uri, null);
    }

    public void execute(@Type int type, @Nullable Uri uri, OnSyncedHandler onSyncedHandler){
        ContentResolver resolver = getContext().getContentResolver();

        loadingDialog = new LoadingDialog(getContext())
                .setTitle(type == TYPE_EXPORT ? R.string.export : R.string.import_str)
                .setMessage(R.string.wait_text);
        AlertDialog alertDialog = loadingDialog.show();

        doInBackground(() -> {
            Result result = null;
            try{
                switch (type){
                    case TYPE_EXPORT -> result = runExport(resolver.openOutputStream(uri));
                    case TYPE_IMPORT -> result = runImport(resolver.openInputStream(uri));
                }

                handleResult(result, onSyncedHandler);
            }catch (Exception e){
                e.printStackTrace();
                if(e instanceof NullPointerException)
                    return;

                error(e);
            }finally {
                alertDialog.dismiss();
            }
        });
    }

    protected void error(Exception exception){
        handler.post(() -> {
            String msg = exception.getMessage();
            if(exception instanceof AEADBadTagException)
                msg = getContext().getString(R.string.password_does_not_match);

            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(R.string.error_title)
                    .setMessage(msg)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {})
                    .show();
        });
        exception.printStackTrace();
    }

    protected void handleResult(Result result, OnSyncedHandler onSyncedHandler){
        handler.post(() -> {
            if(result instanceof Result.Error error)
                new MaterialAlertDialogBuilder(getContext())
                        .setTitle(R.string.error_title)
                        .setMessage(error.getMessage())
                        .setPositiveButton(R.string.ok, (dialog, which) -> handleSyncHandler(onSyncedHandler, result))
                        .show();

            else if (result instanceof Result.Duplicate duplicate)
                new MaterialAlertDialogBuilder(getContext())
                        .setTitle(R.string.warning)
                        .setMessage(getContext().getResources().getQuantityString(R.plurals.item_existed, duplicate.getCount(), duplicate.getCount()))
                        .setPositiveButton(R.string.ok, (dialog, which) -> handleSyncHandler(onSyncedHandler, result))
                        .show();

            else if (result instanceof Result.Success success) {
                Toast.makeText(getContext(), success.getType() == TYPE_EXPORT ? R.string.backup_stored : R.string.backup_restored, Toast.LENGTH_LONG).show();
                handleSyncHandler(onSyncedHandler, result);
            }
        });
    }

    private void handleSyncHandler(OnSyncedHandler onSyncedHandler, Result result){
        if(onSyncedHandler != null)
            onSyncedHandler.onSynced(result);
    }

    protected void notifyUpdate(int current, int max){
        handler.post(() -> loadingDialog.updateProgress(current, max));
    }

    public interface OnSyncedHandler {
        void onSynced(Result result);
    }
}
