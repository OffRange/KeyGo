package de.davis.keygo.core.security.domain.crypto

import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.SecretBlueprint
import de.davis.keygo.core.item.domain.model.SecretField
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

context(scope: CryptographicScope)
suspend fun <T, F : SecretField<T>> SecretBlueprint<T, F>.encrypt(
    value: T,
    ctx: CoroutineContext = Dispatchers.Default
): F = with(scope) {
    val encrypted = codec.encode(value).encrypt(label = label, context = ctx)
    createField(encrypted.toPayload())
}

context(scope: CryptographicScope)
suspend fun <T> SecretField<T>.decrypt(
    ctx: CoroutineContext = Dispatchers.Default
): T = with(scope) {
    payload.toCryptographicData().decrypt(label = blueprint.label, context = ctx)
        .let { decryptedBytes ->
            blueprint.codec.decode(decryptedBytes)
        }
}

private fun CryptographicData.toPayload() = EncryptedPayload(
    ciphertext = data,
    iv = iv
)

private fun EncryptedPayload.toCryptographicData() = CryptographicData(
    data = ciphertext,
    iv = iv,
)