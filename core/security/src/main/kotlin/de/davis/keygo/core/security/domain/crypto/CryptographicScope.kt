package de.davis.keygo.core.security.domain.crypto

import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

interface CryptographicScope {

    suspend fun ByteArray.encrypt(context: CoroutineContext = Dispatchers.Default): CryptographicData
    suspend fun CryptographicData.decrypt(context: CoroutineContext = Dispatchers.Default): ByteArray
}