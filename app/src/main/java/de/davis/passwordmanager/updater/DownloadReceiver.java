package de.davis.passwordmanager.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.IOException;

public class DownloadReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Updater.installRelease(context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
