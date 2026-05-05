package de.davis.keygo.core.security.data.crypto

import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.mapSuccess
import de.davis.keygo.rust.item.ItemManager
import de.davis.keygo.rust.wrap.KeyWrapper
import de.davis.keygo.rust.wrap.unwrapItemKeyWithResult
import de.davis.keygo.rust.wrap.unwrapVaultKeyWithResult
import de.davis.keygo.rust.wrap.wrapItemKeyWithResult
import de.davisalessandro.keygo.rust.ItemAad
import de.davisalessandro.keygo.rust.KeyWrapException
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import org.koin.core.annotation.Single

@Single
internal class CryptographicScopeProviderImpl(
    private val session: Session,
    private val itemManager: ItemManager,
    private val keyWrapper: KeyWrapper,
) : CryptographicScopeProvider {

    override suspend fun <R> itemScope(
        wrappedVaultKeyInformation: WrappedVaultKeyInformation,
        wrappedItemKeyInformation: WrappedItemKeyInformation,
        block: suspend CryptographicScope.() -> R,
    ): R {
        val vaultKey = unwrapVaultKeyWithResult(wrappedVaultKeyInformation).get()

        val itemKey = wrappedItemKeyInformation.wrappedItemKey?.let {
            keyWrapper.unwrapItemKeyWithResult(
                vaultKey = vaultKey,
                wrapped = it.toWrappedKeyBlob(),
                aad = wrappedItemKeyInformation.itemAad
            ).get()
        } ?: itemManager.createNewItemKey()

        return CryptographicScopeImpl(
            itemKey = itemKey,
            itemAad = wrappedItemKeyInformation.itemAad,
            itemManager = itemManager,
            wrapAction = {
                keyWrapper.wrapItemKey(
                    vaultKey = vaultKey,
                    itemKey = itemKey,
                    aad = wrappedItemKeyInformation.itemAad,
                ).toKeyInformation()
            },
        ).block()
    }

    override suspend fun rewrapItemKey(
        sourceVault: WrappedVaultKeyInformation,
        sourceItem: WrappedItemKeyInformation,
        destinationVault: WrappedVaultKeyInformation,
    ): Result<KeyInformation, KeyWrapException> {
        val wrappedItemKey = requireNotNull(sourceItem.wrappedItemKey) {
            "rewrapItemKey requires an existing wrapped item key"
        }
        val destinationAad = ItemAad(
            itemId = sourceItem.itemAad.itemId,
            vaultId = destinationVault.vaultId,
        )

        val sourceVaultKey = when (val r = unwrapVaultKeyWithResult(sourceVault)) {
            is Result.Success -> r.success
            is Result.Failure -> return Result.Failure(r.error)
        }
        val itemKey = when (
            val r = keyWrapper.unwrapItemKeyWithResult(
                vaultKey = sourceVaultKey,
                wrapped = wrappedItemKey.toWrappedKeyBlob(),
                aad = sourceItem.itemAad,
            )
        ) {
            is Result.Success -> r.success
            is Result.Failure -> return Result.Failure(r.error)
        }

        val destinationVaultKey = when (val r = unwrapVaultKeyWithResult(destinationVault)) {
            is Result.Success -> r.success
            is Result.Failure -> return Result.Failure(r.error)
        }
        return keyWrapper.wrapItemKeyWithResult(
            vaultKey = destinationVaultKey,
            itemKey = itemKey,
            aad = destinationAad,
        ).mapSuccess { it.toKeyInformation() }
    }

    private fun unwrapVaultKeyWithResult(info: WrappedVaultKeyInformation) =
        keyWrapper.unwrapVaultKeyWithResult(
            ark = session.dek.key.encoded,
            wrapped = info.wrappedVaultKey.toWrappedKeyBlob(),
            vaultId = info.vaultId,
        )
}

private fun KeyInformation.toWrappedKeyBlob() = WrappedKeyBlob(
    ciphertext = wrappedKey,
    nonce = keyNonce
)

private fun WrappedKeyBlob.toKeyInformation(): KeyInformation =
    KeyInformation(wrappedKey = ciphertext, keyNonce = nonce)

private fun <S, E : Exception> Result<S, E>.get(): S = when (this) {
    is Result.Success -> success
    is Result.Failure -> throw error
}
