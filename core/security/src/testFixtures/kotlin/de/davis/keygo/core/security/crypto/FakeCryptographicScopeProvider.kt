package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.KeyWrapException
import kotlin.coroutines.CoroutineContext
import kotlin.experimental.xor

class FakeCryptographicScopeProvider : CryptographicScopeProvider {

    sealed interface CallHistory {

        class EncryptCall(val label: String, val plaintext: ByteArray) : CallHistory
        class DecryptCall(val label: String, val data: ByteArray) : CallHistory
        class RewrapCall(
            val sourceVault: WrappedVaultKeyInformation,
            val sourceItem: WrappedItemKeyInformation,
            val destinationVault: WrappedVaultKeyInformation,
        ) : CallHistory
    }

    val callHistory = mutableListOf<CallHistory>()
    val encryptCalls
        get() = callHistory.filterIsInstance<CallHistory.EncryptCall>()
    val rewrapCalls
        get() = callHistory.filterIsInstance<CallHistory.RewrapCall>()

    /** Result returned by the next [rewrapItemKey] call. */
    var rewrapResult: Result<KeyInformation, KeyWrapException> =
        Result.Success(KeyInformation(byteArrayOf(), byteArrayOf()))

    override suspend fun <R> itemScope(
        itemId: ItemId,
        block: suspend CryptographicScope.() -> R
    ): R = throw NotImplementedError()

    override suspend fun <R> itemScope(
        wrappedVaultKeyInformation: WrappedVaultKeyInformation,
        wrappedItemKeyInformation: WrappedItemKeyInformation,
        block: suspend CryptographicScope.() -> R,
    ): R = block(
        object : CryptographicScope {
            override suspend fun ByteArray.encrypt(
                label: String,
                context: CoroutineContext,
            ): CryptographicData {
                callHistory += CallHistory.EncryptCall(label, this.copyOf())
                return CryptographicData(data = transform(this), iv = IV)
            }

            override suspend fun CryptographicData.decrypt(
                label: String,
                context: CoroutineContext,
            ): ByteArray {
                callHistory += CallHistory.DecryptCall(label, this.data.copyOf())
                return transform(data)
            }

            override suspend fun wrapCurrentItemKey(context: CoroutineContext): KeyInformation =
                KeyInformation(byteArrayOf(), byteArrayOf())
        }
    )

    override suspend fun rewrapItemKey(
        sourceVault: WrappedVaultKeyInformation,
        sourceItem: WrappedItemKeyInformation,
        destinationVault: WrappedVaultKeyInformation,
    ): Result<KeyInformation, KeyWrapException> {
        callHistory += CallHistory.RewrapCall(sourceVault, sourceItem, destinationVault)
        return rewrapResult
    }

    companion object {
        val IV: ByteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        private val KEY: ByteArray = byteArrayOf(0xCA.toByte(), 0xFE.toByte())

        /**
         * Performs XOR operation between the input and a fixed key.
         */
        fun transform(data: ByteArray): ByteArray {
            return ByteArray(data.size) { i ->
                data[i] xor KEY[i % KEY.size]
            }
        }
    }
}