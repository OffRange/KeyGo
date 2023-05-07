package de.davis.passwordmanager.updater;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class AppInstallerClass extends Service {

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int extraStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);

        if(extraStatus == PackageInstaller.STATUS_PENDING_USER_ACTION){
            startActivity(((Intent)intent.getParcelableExtra(Intent.EXTRA_INTENT)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }else if(extraStatus == PackageInstaller.STATUS_SUCCESS){
            Updater.getVersionApkFile().deleteOnExit();
        }
        stopSelf();
        return START_NOT_STICKY;
    }
}
