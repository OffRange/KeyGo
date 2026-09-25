package de.davis.keygo.feature.password_health.domain

import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.feature.password_health.domain.model.HealthFingerprint
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

interface LoginFingerprinter {

    suspend fun fingerprint(
        login: Login,
        coroutineContext: CoroutineContext = Dispatchers.Default
    ): HealthFingerprint?
}
