package de.davis.keygo.core.security.crypto

import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import kotlin.coroutines.CoroutineContext

class RecordingCryptographicScopeProvider : CryptographicScopeProvider {

    data class EncryptCall(val label: String, val plaintext: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncryptCall

            if (label != other.label) return false
            if (!plaintext.contentEquals(other.plaintext)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = label.hashCode()
            result = 31 * result + plaintext.contentHashCode()
            return result
        }
    }

    val encryptCalls = mutableListOf<EncryptCall>()

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
                encryptCalls += EncryptCall(label, this.copyOf())
                return CryptographicData(data = transform(this), iv = IV)
            }

            override suspend fun CryptographicData.decrypt(
                label: String,
                context: CoroutineContext,
            ): ByteArray = transform(data)

            override suspend fun wrapCurrentItemKey(context: CoroutineContext): KeyInformation =
                KeyInformation(byteArrayOf(), byteArrayOf())
        }
    )

    companion object {
        val IV: ByteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        private val MARKER: ByteArray = byteArrayOf(0xCA.toByte(), 0xFE.toByte())

        fun transform(plaintext: ByteArray): ByteArray = MARKER + plaintext
    }
}