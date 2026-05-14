package de.davis.keygo.feature.item.core.domain.model

fun resolveTotpDomain(issuer: String?, accountName: String): String? {
    if (!issuer.isNullOrEmpty()) return issuer
    val atIndex = accountName.indexOf('@')
    return if (atIndex >= 0) accountName.substring(atIndex + 1).takeIf { it.isNotEmpty() } else null
}
