package de.davis.keygo.core.domain.crypto

import de.davis.keygo.core.domain.model.crypto.CryptographicData
import de.davis.keygo.core.item.domain.model.SecretData
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

context(scope: CryptographicScope)
suspend fun <T> SecretData<T>.decryptSecretData(ctx: CoroutineContext = Dispatchers.Default): T =
    with(scope) {
        decryptedDataType.decode(
            data.asCryptographicData().decrypt(ctx)
        )
    }

context(scope: CryptographicScope)
suspend inline fun <reified T> T.encryptSecretData(ctx: CoroutineContext = Dispatchers.Default): SecretData<T> =
    with(scope) {
        val decryptedDataType = SecretData.DecryptedDataType.getDecryptedDataType<T>()
        val encoded = decryptedDataType.encode(this@encryptSecretData)

        SecretData(
            data = encoded.encrypt(ctx).data,
            decryptedDataType = decryptedDataType
        )
    }


private fun ByteArray.asCryptographicData() = CryptographicData(this)