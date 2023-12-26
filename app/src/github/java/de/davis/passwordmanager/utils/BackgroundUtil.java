package de.davis.passwordmanager.utils;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class BackgroundUtil {

    private static final Executor executor = Executors.newSingleThreadExecutor();

    public static void doInBackground(Runnable runnable){
        executor.execute(runnable);
    }
}
