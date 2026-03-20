package de.davis.keygo.feature.autofill.data.repository

import de.davis.keygo.feature.autofill.domain.repository.DigitalAssetLinkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.koin.core.annotation.Single
import java.net.URLEncoder

@Single
internal class DigitalAssetLinkRepositoryImpl(
    private val http: OkHttpClient
) : DigitalAssetLinkRepository {

    override suspend fun isLinked(
        packageName: String,
        signature: String,
        domain: String
    ): Boolean = withContext(Dispatchers.IO) {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val response = http.newCall(
            Request.Builder()
                .url(API_ENDPOINT.format(enc(domain), enc(packageName), enc(signature)))
                .build()
        ).execute()

        response.use {
            if (!it.isSuccessful) return@use false

            JSONObject(it.body.string()).optBoolean("linked", false)
        }
    }

    companion object {

        private const val RELATION = "delegate_permission/common.get_login_creds"

        private const val API_ENDPOINT =
            "https://digitalassetlinks.googleapis.com/v1/assetlinks:check" +
                    "?source.web.site=%s" +
                    "&relation=$RELATION" +
                    "&target.androidApp.packageName=%s" +
                    "&target.androidApp.certificate.sha256Fingerprint=%s"
    }
}