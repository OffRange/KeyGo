package de.davis.keygo.feature.autofill.domain.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.autofill.domain.model.DigitalAssetLinkFailure

interface DigitalAssetLinkRepository {

    suspend fun isLinked(
        packageName: String,
        signature: String,
        domain: String,
    ): Result<Boolean, DigitalAssetLinkFailure>
}
