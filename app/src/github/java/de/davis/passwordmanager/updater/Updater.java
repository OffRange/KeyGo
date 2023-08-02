package de.davis.passwordmanager.updater;

import static android.content.pm.PackageInstaller.PACKAGE_SOURCE_OTHER;
import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;
import static android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED;
import static android.content.pm.PackageManager.INSTALL_REASON_USER;
import static de.davis.passwordmanager.updater.installer.InstallBroadcastReceiver.EXTRA_FILE;
import static de.davis.passwordmanager.version.Version.CHANNEL_STABLE;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
import java.util.Arrays;
import java.util.List;

import de.davis.passwordmanager.App;
import de.davis.passwordmanager.updater.downloader.DownloadService;
import de.davis.passwordmanager.updater.exception.RateLimitException;
import de.davis.passwordmanager.updater.installer.InstallBroadcastReceiver;
import de.davis.passwordmanager.updater.version.Release;
import de.davis.passwordmanager.version.Version;

public class Updater {

    public static final String ACTION_INVALID_APK = "de.davis.passwordmanager.action.ACTION_INVALID_APK";

    private static final int REPOSITORY_ID = 581862172;

    private final Context context;

    private boolean fetched;

    private Release cachedRelease;

    public Updater(Context context){
        this.context = context;
    }

    public boolean hasFetched() {
        return fetched;
    }


    @WorkerThread
    @NonNull
    public Release fetchByChannel(@Version.Channel int updateChannel) throws IOException {
        return fetchByChannel(updateChannel, false);
    }

    @WorkerThread
    @NonNull
    public synchronized Release fetchByChannel(@Version.Channel int updateChannel, boolean useCached) throws IOException {
        if (!fetched || !useCached) {
            cachedRelease = fetchReleaseByChannel(updateChannel);
            fetched = true;
        }

        deleteUnusedDownloads(cachedRelease.getDownloadedFile((App) context.getApplicationContext()));
        return cachedRelease;
    }

    @WorkerThread
    @NonNull
    private Release fetchReleaseByChannel(@Version.Channel int updateChannel) throws IOException {
        GitHub gitHub = new GitHubBuilder().withRateLimitHandler(new GitHubRateLimitHandler() {
            @Override
            public void onError(@NonNull GitHubConnectorResponse connectorResponse) throws IOException {
                throw new RateLimitException(connectorResponse);
            }
        }).build();
        GHRepository repository = gitHub.getRepositoryById(REPOSITORY_ID);
        GHRelease stableGhReleases = repository.getLatestRelease();
        Release stableRelease = createRelease(stableGhReleases);
        if (updateChannel == CHANNEL_STABLE)
            return stableRelease;

        List<GHRelease> ghReleases = repository.listReleases().toList();
        GHRelease unstableGhRelease = ghReleases.stream()
                .filter(r -> Version.getChannelByVersionName(r.getTagName()) >= updateChannel)
                .findFirst().orElse(null);
        if (unstableGhRelease == null)
            return stableRelease;

        Release unstableRelease = createRelease(unstableGhRelease);

        if (stableRelease.getVersionCode() > unstableRelease.getVersionCode())
            return stableRelease;

        return unstableRelease;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void deleteUnusedDownloads(File actual){
        File[] files = ((App)context.getApplicationContext()).getDownloadDir().listFiles();
        if(files == null)
            return;

        Arrays.stream(files)
                .filter(file -> !file.equals(actual))
                .forEach(File::delete);
    }

    private Release createRelease(GHRelease ghRelease) throws IOException {
        List<GHAsset> assets = ghRelease.listAssets().toList();
        if (assets.size() == 0)
            return new Release(null, ghRelease.getTagName());

        GHAsset asset = assets.stream()
                .filter(ghAsset -> ghAsset.getContentType().equals("application/vnd.android.package-archive"))
                .findFirst().orElse(null);

        return new Release(asset == null ? null : asset.getName(), ghRelease.getTagName());
    }

    private boolean isApkVerified(File file, long vCode){
        PackageInfo info;
        try{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info = context.getPackageManager().getPackageArchiveInfo(file.getPath(), PackageManager.PackageInfoFlags.of(0));
            }else
                info = context.getPackageManager().getPackageArchiveInfo(file.getPath(), 0);
        }catch (Exception e){
            return false;
        }

        if(info == null)
            return false;

        long apkCode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            apkCode = info.getLongVersionCode();
        }else
            apkCode = info.versionCode;

        return file.isFile() && info.packageName.equals(context.getPackageName()) && apkCode == vCode;
    }

    @WorkerThread
    public void install(Release release){
        File apk = release.getDownloadedFile((App) context.getApplicationContext());
        if(!isApkVerified(apk, release.getVersionCode())) {
            LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(ACTION_INVALID_APK)
                    .putExtra(DownloadService.EXTRA_RELEASE, release));
            return;
        }

        long size = apk.length();

        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        params.setSize(size);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.setInstallReason(INSTALL_REASON_USER);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(USER_ACTION_NOT_REQUIRED);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            params.setPackageSource(PACKAGE_SOURCE_OTHER);
        }

        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        try {
            int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);
            try(OutputStream outputStream = session.openWrite(context.getPackageName(), 0, size);
                InputStream inputStream = new FileInputStream(apk)){

                byte[] buffer = new byte[1024*1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                session.fsync(outputStream);
            }


            session.close();

            Intent intent = new Intent(InstallBroadcastReceiver.ACTION_INSTALL);
            intent.setPackage(context.getPackageName());
            intent.putExtra(EXTRA_FILE, apk);
            intent.putExtra(DownloadService.EXTRA_RELEASE, release);
            session.commit(PendingIntent.getBroadcast(context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE)
                    .getIntentSender());
            session.close();
        } catch (IOException ignored) {}
    }

    public void download(Release release){
        DownloadService.start(release, context);
    }
}
