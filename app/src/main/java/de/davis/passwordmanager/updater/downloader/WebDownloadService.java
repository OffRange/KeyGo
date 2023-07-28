package de.davis.passwordmanager.updater.downloader;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Streaming;

public interface WebDownloadService {

    @GET("{vTag}/{assetName}/")
    @Streaming
    Call<ResponseBody> downloadRelease(@Path("vTag") String vTag, @Path("assetName") String assetName);
}
