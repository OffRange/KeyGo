package de.davis.keygo.core.domain.crypto

import de.davis.keygo.core.domain.model.crypto.CryptographicData
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

interface CryptographicScope {

    suspend fun ByteArray.encrypt(context: CoroutineContext = Dispatchers.Default): CryptographicData
    suspend fun CryptographicData.decrypt(context: CoroutineContext = Dispatchers.Default): ByteArray

    suspend fun ByteArray.hash(): CryptographicData
    suspend fun CryptographicData.checkHash(plainText: ByteArray): Boolean
}