package de.davis.passwordmanager.updater.downloader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.text.DecimalFormat;

import de.davis.passwordmanager.App;
import de.davis.passwordmanager.R;
import de.davis.passwordmanager.updater.version.Release;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

public class DownloadService extends Service {

    private static final String ACTION_CANCEL = "de.davis.passwordmanager.action.CANCEL";
    public static final String ACTION_PROGRESS = "de.davis.passwordmanager.action.PROGRESS";
    public static final String ACTION_DESTROY = "de.davis.passwordmanager.action.DESTROY";
    public static final String ACTION_START = "de.davis.passwordmanager.action.START";

    public static final String EXTRA_SUCCESS = "de.davis.passwordmanager.extra.SUCCESS";
    public static final String EXTRA_PROGRESS = "de.davis.passwordmanager.extra.PROGRESS";
    public static final String EXTRA_RELEASE = "de.davis.passwordmanager.extra.RELEASE";


    private static final int NOTIFICATION_ID = 1;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    private long lastUpdateTime = 0;

    private final DecimalFormat decimalFormat = new DecimalFormat("0.00");

    private boolean running;

    private LocalBroadcastManager broadcastManager;

    private Release release;

    private boolean success;

    private final BroadcastReceiver cancelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(!ACTION_CANCEL.equals(intent.getAction()))
                return;

            success = false;
            stopSelf();
        }
    };

    public static void start(Release release, Context context){
        Intent intent = new Intent(context, DownloadService.class);
        intent.putExtra(EXTRA_RELEASE, release);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        }else
            context.startService(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        running = true;

        notificationManager = getSystemService(NotificationManager.class);
        broadcastManager = LocalBroadcastManager.getInstance(this);

        broadcastManager.sendBroadcast(new Intent(ACTION_START));

        ContextCompat.registerReceiver(this, cancelReceiver, new IntentFilter(ACTION_CANCEL),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        release = (Release) intent.getSerializableExtra(EXTRA_RELEASE);

        if(release != null)
            startForeground(release.getVersionTag());

        new Thread(this::download).start();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        broadcastManager.sendBroadcast(new Intent(ACTION_DESTROY)
                .putExtra(EXTRA_SUCCESS, success)
                .putExtra(EXTRA_RELEASE, release));
        unregisterReceiver(cancelReceiver);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void download(){
        try {
            File dir = ((App)getApplication()).getDownloadDir();
            if(!dir.isDirectory())
                dir.mkdir();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://github.com/OffRange/KeyGo/releases/download/")
                    .client(new OkHttpClient.Builder().build()).build();

            WebDownloadService webDownloadService = retrofit.create(WebDownloadService.class);
            Call<ResponseBody> call = webDownloadService.downloadRelease(release.getVersionTag(),
                    release.getAssetName());
            Response<ResponseBody> response = call.execute();

            if(!response.isSuccessful())
                return;

            ResponseBody body = response.body();
            if(body == null)
                return;

            long size = body.contentLength();

            try(ReadableByteChannel readableByteChannel = Channels.newChannel(body.byteStream());
                FileOutputStream outputStream = new FileOutputStream(release.getDownloadedFile((App) getApplication()))){
                FileChannel fileChannel = outputStream.getChannel();

                try{
                    // ----- copied from actual Android source code -----
                    int c = (int)Math.min(size, 8192L);
                    ByteBuffer bb = ByteBuffer.allocate(c);
                    long tw = 0L;
                    long pos = 0;

                    while(tw < size && running) {
                        bb.limit((int)Math.min(size - tw, 8192L));
                        int nr = readableByteChannel.read(bb);
                        if (nr <= 0) {
                            break;
                        }

                        bb.flip();
                        int nw = fileChannel.write(bb, pos);
                        tw += (long)nw;


                        updateProgress(tw, size);

                        if (nw != nr) {
                            break;
                        }

                        pos += (long)nw;
                        bb.clear();
                    }
                    // ---------------------- END -----------------------

                    bb.clear();
                    body.close();

                    success = true;
                }catch (Exception e){
                    e.printStackTrace();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        stopSelf();
    }

    private synchronized void updateProgress(long bytesRead, long total){
        if(!running)
            return;

        long time = System.currentTimeMillis();
        if(lastUpdateTime + 50 > time)
            return;

        lastUpdateTime = time;
        double progress = bytesRead * 100d / total;

        Intent intent = new Intent(ACTION_PROGRESS);
        intent.putExtra(EXTRA_PROGRESS, progress).putExtra(EXTRA_RELEASE, release);

        broadcastManager.sendBroadcast(intent);

        notificationBuilder.setProgress(100, (int) progress, false);
        notificationBuilder.setContentText(getString(R.string.downloading_progress_text,
                decimalFormat.format(progress)));
        setNotification();
    }

    private void startForeground(String vTag){
        notificationBuilder = new NotificationCompat.Builder(this, getPackageName())
                .setContentTitle(getString(R.string.downloading_newer_version, vTag))
                .setContentText(getString(R.string.init_download))
                .setProgress(100, 0, true)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_delete, getString(R.string.cancel), PendingIntent
                        .getBroadcast(this, 0, new Intent(ACTION_CANCEL),
                                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT));


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }

        Notification notification = notificationBuilder.build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void setNotification(){
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel(){
        NotificationChannel channel = new NotificationChannel(
                getPackageName(),
                getString(R.string.version),
                NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(channel);
    }
}
