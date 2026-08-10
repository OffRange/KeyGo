package de.davis.keygo.core.ui.model

import kotlinx.serialization.Serializable

@Serializable
data class PendingTotpImport(
    val totpInfo: String? = null,
    val queries: String? = null,
) {
    val uri: String?
        get() = if (!totpInfo.isNullOrBlank() && !queries.isNullOrBlank())
            "otpauth://totp/$totpInfo?$queries"
        else null

    companion object {
        const val BASE_PATH = "otpauth://totp"
        const val URI_PATTERN = "otpauth://totp/{totpInfo}?{queries}"
    }
}
