package de.davis.passwordmanager.updater;

import static de.davis.passwordmanager.utils.BackgroundUtil.doInBackground;
import static de.davis.passwordmanager.utils.Version.CHANNEL_STABLE;

import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.os.HandlerCompat;

import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitHubRateLimitHandler;
import org.kohsuke.github.connector.GitHubConnectorResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.utils.Version;

public class Updater {

    private static final int REPOSITORY_ID = 581862172;

    private final Handler handler = HandlerCompat.createAsync(Looper.getMainLooper());

    private Update update = Update.EMPTY;
    private static Updater instance;

    private boolean running;

    private Listener listener;

    private Updater() {}

    public static Updater getInstance() {
        if(instance == null)
            instance = new Updater();

        return instance;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void notifyListener(GHRelease release, Context context){
        setRunningStatus(false);
        update = new Update(release, context);
        handler.post(() -> listener.onSuccess(this, update));
    }

    public boolean isRunning() {
        return running;
    }

    public Update getUpdate() {
        return update;
    }

    public static File getVersionApkFile() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "password_manager.apk");
    }

    public void checkForGitHubRelease(@Version.VersionChannel int versionChannel, Context context) {
        if(isRunning())
            return;

        doInBackground(() -> {
            try{
                setRunningStatus(true);
                GitHub gitHub = new GitHubBuilder().withRateLimitHandler(new GitHubRateLimitHandler() {
                    @Override
                    public void onError(@NonNull GitHubConnectorResponse connectorResponse) throws IOException {
                        throw new IOException("Rate limit reached");
                    }
                }).build();
                GHRepository repository = gitHub.getRepositoryById(REPOSITORY_ID);

                GHRelease latest = repository.getLatestRelease();
                if(versionChannel == CHANNEL_STABLE) {
                    notifyListener(latest, context);
                    return;
                }

                ArrayList<GHRelease> releases = new ArrayList<>(repository.listReleases().toList());

                GHRelease r = releases.stream().filter(release -> Version.getChannelTypeByVersionName(release.getTagName()) >= versionChannel).findFirst().orElse(null);
                if(r == null) {
                    notifyListener(latest, context);
                    return;
                }

                if(latest.getPublished_at().after(r.getPublished_at())) {
                    notifyListener(latest, context);
                    return;
                }

                notifyListener(r, context);
            }catch (IOException e){
                setRunningStatus(false);
                handler.post(() -> listener.onError(e));
            }
        });

    }

    private void setRunningStatus(boolean running){
        this.running = running;
        handler.post(() -> listener.onRunningChanged(running));
    }

    public static boolean isNewer(GHRelease release, Context context) {
        String versionName = extractVersionName(release.getBody());
        if(versionName == null)
            return false;

        Pattern pattern = Pattern.compile("(?<=\\s)\\d+(?=-.+$)");
        Matcher currentMatcher = pattern.matcher(Version.getVersion(context).getVersionName());
        Matcher releaseMatcher = pattern.matcher(versionName);
        if(!releaseMatcher.find() || !currentMatcher.find())
            return false;

        int currentBuildNumber = Integer.parseInt(currentMatcher.group());
        int releaseBuildNumber = Integer.parseInt(releaseMatcher.group());

        return currentBuildNumber < releaseBuildNumber;
    }

    public static void downloadRelease(GHRelease release, Context context) throws IOException {
        List<GHAsset> assets = release.listAssets().toList();
        if(assets.size() == 0)
            return;

        String downloadUrl = assets.get(0).getBrowserDownloadUrl();

        if(getVersionApkFile().exists()){
            installRelease(context);
            return;
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setTitle(context.getString(R.string.app_name));
        request.setDescription("Updating to "+ release.getTagName());

        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "password_manager.apk");

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadId = downloadManager.enqueue(request);
    }

    public static void installRelease(Context context) throws IOException {
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.setInstallReason(PackageManager.INSTALL_REASON_USER);
        }

        PackageInstaller.Session session = packageInstaller.openSession(packageInstaller.createSession(params));

        InputStream in = new FileInputStream(getVersionApkFile());
        OutputStream os = session.openWrite("passwordmanager.apk", 0, getVersionApkFile().length());
        byte[] buffer = new byte[1024*1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            os.write(buffer, 0, bytesRead);
        }

        session.fsync(os);
        in.close();
        os.close();
        session.commit(PendingIntent.getService(
                context,
                0,
                new Intent(context, AppInstallerClass.class), PendingIntent.FLAG_MUTABLE).getIntentSender());
        session.close();
    }

    private static String extractVersionName(String body){
        String[] lines = body.split("\r\n");

        for(int i = 0; i < lines.length; i++){
            if(lines[i].startsWith("## Version Tag"))
                return lines[i+1];
        }

        return null;
    }

    public interface Listener {
        void onError(Throwable throwable);
        void onSuccess(Updater updater, Update update);
        void onRunningChanged(boolean running);
    }

    public static class Update{
        private static final Update EMPTY = new Update();

        private final GHRelease release;
        private final boolean newer;

        private Update(){
            release = null;
            newer = false;
        }

        private Update(GHRelease release, Context context){
            this.release = release;
            newer = Updater.isNewer(release, context);
        }

        public GHRelease getRelease() {
            return release;
        }

        public boolean isNewer() {
            return newer;
        }
    }
}
