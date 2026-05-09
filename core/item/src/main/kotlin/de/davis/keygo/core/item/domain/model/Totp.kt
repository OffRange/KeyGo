package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId

data class Totp(
    val loginId: ItemId,
    val secret: SecretData<String>,
    val accountName: String? = null,
    val issuer: String? = null,
    val algorithm: String = DEFAULT_ALGORITHM,
    val digits: Int = DEFAULT_DIGITS,
    val period: Int = DEFAULT_PERIOD,
) {

    companion object {
        const val DEFAULT_PERIOD = 30
        const val DEFAULT_DIGITS = 6
        const val DEFAULT_ALGORITHM = "sha1"
    }
}
