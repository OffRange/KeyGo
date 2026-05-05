package de.davis.keygo.core.security.domain.crypto

import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

interface CryptographicScope {

    suspend fun ByteArray.encrypt(
        label: String,
        context: CoroutineContext = Dispatchers.Default,
    ): CryptographicData

    suspend fun CryptographicData.decrypt(
        label: String,
        context: CoroutineContext = Dispatchers.Default,
    ): ByteArray

    suspend fun wrapCurrentItemKey(context: CoroutineContext = Dispatchers.Default): KeyInformation
}
