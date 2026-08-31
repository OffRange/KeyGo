package de.davis.keygo.core.ui.model

import kotlinx.serialization.Serializable

@Serializable
data class PendingTotpImport(
    val totpInfo: String? = null,
    val queries: String? = null,
) {
    val uri: String?
        get() = if (!totpInfo.isNullOrBlank() && !queries.isNullOrBlank())
            "$BASE_PATH/$totpInfo?$queries"
        else null

    companion object {
        /** Must stay in sync with the `otpauth` intent filter in the app's manifest. */
        const val SCHEME = "otpauth"
        const val HOST = "totp"

        const val BASE_PATH = "$SCHEME://$HOST"
    }
}
