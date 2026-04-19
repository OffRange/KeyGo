package de.davis.keygo.core.security.data.crypto

import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.rust.item.ItemManager
import de.davisalessandro.keygo.rust.EncryptedItemBlob
import de.davisalessandro.keygo.rust.ItemAad
import de.davisalessandro.keygo.rust.ItemKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

internal class CryptographicScopeImpl(
    private val itemKey: ItemKey,
    private val itemAad: ItemAad,
    private val itemManager: ItemManager,
    private val wrapAction: suspend CoroutineScope.() -> KeyInformation,
) : CryptographicScope {
    override suspend fun ByteArray.encrypt(
        label: String,
        context: CoroutineContext,
    ): CryptographicData = withContext(context) {
        val ct = itemManager.encryptItemData(
            itemKey = itemKey,
            data = this@encrypt,
            aad = buildDataAad(label),
        )

        CryptographicData(
            data = ct.ciphertext,
            iv = ct.nonce,
        )
    }

    override suspend fun CryptographicData.decrypt(
        label: String,
        context: CoroutineContext,
    ): ByteArray = withContext(context) {
        val blob = EncryptedItemBlob(
            ciphertext = data,
            nonce = iv,
        )

        itemManager.decryptItemData(
            itemKey = itemKey,
            blob = blob,
            aad = buildDataAad(label),
        )
    }

    override suspend fun wrapCurrentItemKey(context: CoroutineContext): KeyInformation =
        withContext(context, block = wrapAction)

    private fun buildDataAad(label: String): ByteArray {
        val labelBytes = label.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(UUID_BYTES + UUID_BYTES + labelBytes.size)
            .putLong(itemAad.vaultId.mostSignificantBits)
            .putLong(itemAad.vaultId.leastSignificantBits)
            .putLong(itemAad.itemId.mostSignificantBits)
            .putLong(itemAad.itemId.leastSignificantBits)
            .put(labelBytes)
            .array()
    }

    private companion object {
        const val UUID_BYTES = 16
    }
}
