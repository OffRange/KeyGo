package de.davis.passwordmanager.sync;

import static de.davis.passwordmanager.utils.BackgroundUtil.doInBackground;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.core.os.HandlerCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.InputStream;
import java.io.OutputStream;

import javax.crypto.AEADBadTagException;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.dialog.LoadingDialog;

public abstract class DataTransfer {

    private final Context context;

    public static final int TYPE_EXPORT = 0;
    public static final int TYPE_IMPORT = 1;

    @IntDef({TYPE_EXPORT, TYPE_IMPORT})
    public @interface Type{}

    private LoadingDialog loadingDialog;

    private final Handler handler = HandlerCompat.createAsync(Looper.getMainLooper());

    public DataTransfer(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    protected void error(Exception exception){
        handler.post(() -> {
            String msg = exception.getMessage();
            if(exception instanceof AEADBadTagException)
                msg = getContext().getString(R.string.password_does_not_match);

            new MaterialAlertDialogBuilder(getContext())
                    .setTitle(R.string.error_title)
                    .setMessage(msg)
                    .setPositiveButton(R.string.ok,
                            (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }

    protected void handleResult(Result result){
        handler.post(() -> {
            if(result instanceof Result.Error error)
                new MaterialAlertDialogBuilder(getContext())
                        .setTitle(R.string.error_title)
                        .setMessage(error.getMessage())
                        .setPositiveButton(R.string.ok, (dialog, which) -> {})
                        .show();

            else if (result instanceof Result.Duplicate duplicate)
                new MaterialAlertDialogBuilder(getContext())
                        .setTitle(R.string.warning)
                        .setMessage(getContext().getResources().getQuantityString(R.plurals.item_existed, duplicate.getCount(), duplicate.getCount()))
                        .setPositiveButton(R.string.ok, (dialog, which) -> {})
                        .show();

            else if (result instanceof Result.Success success)
                Toast.makeText(getContext(), success.getType() == TYPE_EXPORT ? R.string.backup_stored : R.string.backup_restored, Toast.LENGTH_LONG).show();
        });
    }


    @WorkerThread
    protected abstract Result importElements(InputStream inputStream, String password) throws Exception;
    @WorkerThread
    protected abstract Result exportElements(OutputStream outputStream, String password) throws Exception;

    public void start(@Type int type, Uri uri){
        start(type, uri, null);
    }

    protected void notifyUpdate(int current, int max){
        handler.post(() -> loadingDialog.updateProgress(current, max));
    }

    protected void start(@Type int type, Uri uri, String password) {
        ContentResolver resolver = getContext().getContentResolver();

        loadingDialog = new LoadingDialog(getContext())
                .setTitle(type == TYPE_EXPORT ? R.string.export : R.string.import_str)
                .setMessage(R.string.wait_text);
        AlertDialog alertDialog = loadingDialog.show();

        doInBackground(() -> {
            Result result = null;
            try{
                switch (type){
                    case TYPE_EXPORT -> result = exportElements(resolver.openOutputStream(uri), password);
                    case TYPE_IMPORT -> result = importElements(resolver.openInputStream(uri), password);
                }

                if(result == null)
                    return;

                handleResult(result);
            }catch (Exception e){
                if(e instanceof NullPointerException)
                    return;

                error(e);
            }finally {
                alertDialog.dismiss();
            }
        });
    }
}
