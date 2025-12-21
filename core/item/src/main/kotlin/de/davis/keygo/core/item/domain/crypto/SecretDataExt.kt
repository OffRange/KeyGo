package de.davis.keygo.core.item.domain.crypto

import de.davis.keygo.core.item.domain.model.SecretData
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

context(scope: CryptographicScope)
suspend fun <T> SecretData<T>.decryptSecretData(ctx: CoroutineContext = Dispatchers.Default): T =
    with(scope) {
        decryptedDataType.decode(
            CryptographicData(data, iv).decrypt(ctx)
        )
    }

context(scope: CryptographicScope)
suspend inline fun <reified T> T.encryptSecretData(ctx: CoroutineContext = Dispatchers.Default): SecretData<T> =
    with(scope) {
        val decryptedDataType = SecretData.DecryptedDataType.getDecryptedDataType<T>()
        val encoded = decryptedDataType.encode(this@encryptSecretData)

        val encryptedData = encoded.encrypt(ctx)
        SecretData(
            data = encryptedData.data,
            iv = encryptedData.iv,
            decryptedDataType = decryptedDataType
        )
    }