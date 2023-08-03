package de.davis.passwordmanager.updater.installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;

public class InstallBroadcastReceiver extends BroadcastReceiver {

    public static final String ACTION_INSTALL = "de.davis.passwordmanager.action.INSTALL";
    public static final String EXTRA_FILE = "de.davis.passwordmanager.extra.FILE";

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void onReceive(Context context, Intent intent) {
        int extraStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);

        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

        if(extraStatus == PackageInstaller.STATUS_PENDING_USER_ACTION){
            Intent startIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(startIntent == null)
                return;

            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(startIntent);
        }else if(extraStatus == PackageInstaller.STATUS_SUCCESS){
            File apkFile = (File) intent.getSerializableExtra(EXTRA_FILE);
            if(apkFile != null)
                apkFile.delete();
        }
    }
}
