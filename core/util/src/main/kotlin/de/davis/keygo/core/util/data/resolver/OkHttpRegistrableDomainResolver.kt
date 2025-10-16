package de.davis.keygo.core.util.data.resolver

import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.core.annotation.Single
import java.net.IDN

@Single
internal class OkHttpRegistrableDomainResolver : RegistrableDomainResolver {

    override fun resolve(domain: String): String? {
        val hostOrNull = domain.trim().let {
            if ('@' in it && "://" !in it) // email address, extract domain part
                it.substringAfter('@')
            else it
        }
        val candidate = if ("://" !in hostOrNull) "https://$hostOrNull" else hostOrNull

        val url = candidate.toHttpUrlOrNull()
            ?: "https://${IDN.toASCII(domain)}".toHttpUrlOrNull()
            ?: return null

        return url.topPrivateDomain()
    }
}