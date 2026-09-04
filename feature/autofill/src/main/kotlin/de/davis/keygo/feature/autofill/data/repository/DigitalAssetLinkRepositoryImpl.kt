package de.davis.keygo.feature.autofill.data.repository

import android.util.Log
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.autofill.domain.model.DigitalAssetLinkFailure
import de.davis.keygo.feature.autofill.domain.repository.DigitalAssetLinkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import org.koin.core.annotation.Single
import java.io.IOException
import java.net.URLEncoder

@Single
internal class DigitalAssetLinkRepositoryImpl(
    private val http: OkHttpClient
) : DigitalAssetLinkRepository {

    override suspend fun isLinked(
        packageName: String,
        signature: String,
        domain: String,
    ): Result<Boolean, DigitalAssetLinkFailure> = withContext(Dispatchers.IO) {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val request = Request.Builder()
            .url(API_ENDPOINT.format(enc(domain), enc(packageName), enc(signature)))
            .build()

        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful)
                    return@use Result.Failure(DigitalAssetLinkFailure.NoVerdict)

                Result.Success(JSONObject(response.body.string()).optBoolean("linked", false))
            }
        } catch (e: IOException) {
            // The autofill dialog runs wherever the user happens to be, so being offline, behind a
            // captive portal or on a broken DNS is normal. None of that is a verdict.
            Log.w(TAG, "Could not reach the digital asset link API for $domain", e)
            Result.Failure(DigitalAssetLinkFailure.Unreachable)
        } catch (e: JSONException) {
            Log.w(TAG, "Digital asset link API returned an unreadable body for $domain", e)
            Result.Failure(DigitalAssetLinkFailure.NoVerdict)
        }
    }

    companion object {

        private const val TAG = "DigitalAssetLink"

        private const val RELATION = "delegate_permission/common.get_login_creds"

        private const val API_ENDPOINT =
            "https://digitalassetlinks.googleapis.com/v1/assetlinks:check" +
                    "?source.web.site=%s" +
                    "&relation=$RELATION" +
                    "&target.androidApp.packageName=%s" +
                    "&target.androidApp.certificate.sha256Fingerprint=%s"
    }
}
