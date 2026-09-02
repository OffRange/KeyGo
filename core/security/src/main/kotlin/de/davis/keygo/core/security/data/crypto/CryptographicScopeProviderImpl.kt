package de.davis.keygo.core.security.data.crypto

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.model.CryptoScopeError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.mapSuccess
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.rust.item.ItemManager
import de.davis.keygo.rust.wrap.KeyWrapper
import de.davis.keygo.rust.wrap.unwrapItemKeyWithResult
import de.davis.keygo.rust.wrap.unwrapVaultKeyWithResult
import de.davis.keygo.rust.wrap.wrapItemKeyWithResult
import de.davisalessandro.keygo.rust.ItemAad
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import org.koin.core.annotation.Single

@Single
internal class CryptographicScopeProviderImpl(
    private val session: Session,
    private val itemRepository: ItemRepository,
    private val itemManager: ItemManager,
    private val keyWrapper: KeyWrapper,
) : CryptographicScopeProvider {

    override suspend fun <R> itemScope(
        itemId: ItemId,
        block: suspend CryptographicScope.() -> R
    ): Result<R, CryptoScopeError> {
        val envelope = itemRepository.getItemKeyEnvelope(itemId)
            ?: return Result.Failure(CryptoScopeError.IdNotFound)

        return itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = envelope.vaultKeyInformation,
                vaultId = envelope.vaultId,
            ),
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = ItemAad(
                    itemId = envelope.itemId,
                    vaultId = envelope.vaultId,
                ),
                wrappedItemKey = envelope.itemKeyInformation
            ),
            block = block,
        )
    }

    override suspend fun <R> itemScope(
        wrappedVaultKeyInformation: WrappedVaultKeyInformation,
        wrappedItemKeyInformation: WrappedItemKeyInformation,
        block: suspend CryptographicScope.() -> R,
    ): Result<R, CryptoScopeError> = resultBinding {
        val vaultKey =
            unwrapVaultKeyWithResult(wrappedVaultKeyInformation).bind()

        val itemKey = wrappedItemKeyInformation.wrappedItemKey?.let {
            keyWrapper.unwrapItemKeyWithResult(
                vaultKey = vaultKey,
                wrapped = it.toWrappedKeyBlob(),
                aad = wrappedItemKeyInformation.itemAad
            ).bind(CryptoScopeError::KeyWrapError)
        } ?: itemManager.createNewItemKey()

        CryptographicScopeImpl(
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
    ): Result<KeyInformation, CryptoScopeError> = resultBinding {
        val wrappedItemKey = requireNotNull(sourceItem.wrappedItemKey) {
            "rewrapItemKey requires an existing wrapped item key"
        }
        val destinationAad = ItemAad(
            itemId = sourceItem.itemAad.itemId,
            vaultId = destinationVault.vaultId,
        )

        val sourceVaultKey = unwrapVaultKeyWithResult(sourceVault).bind()
        val itemKey = keyWrapper.unwrapItemKeyWithResult(
            vaultKey = sourceVaultKey,
            wrapped = wrappedItemKey.toWrappedKeyBlob(),
            aad = sourceItem.itemAad,
        ).bind(CryptoScopeError::KeyWrapError)

        val destinationVaultKey = unwrapVaultKeyWithResult(destinationVault).bind()
        keyWrapper.wrapItemKeyWithResult(
            vaultKey = destinationVaultKey,
            itemKey = itemKey,
            aad = destinationAad,
        ).mapSuccess { it.toKeyInformation() }.bind(CryptoScopeError::KeyWrapError)
    }

    private fun unwrapVaultKeyWithResult(info: WrappedVaultKeyInformation) = resultBinding {
        val ark = session.ark.asResult(CryptoScopeError.NoActiveSession).bind()
        keyWrapper.unwrapVaultKeyWithResult(
            ark = ark,
            wrapped = info.wrappedVaultKey.toWrappedKeyBlob(),
            vaultId = info.vaultId,
        ).bind(CryptoScopeError::KeyWrapError)
    }
}

private fun KeyInformation.toWrappedKeyBlob() = WrappedKeyBlob(
    ciphertext = wrappedKey,
    nonce = keyNonce
)

private fun WrappedKeyBlob.toKeyInformation(): KeyInformation =
    KeyInformation(wrappedKey = ciphertext, keyNonce = nonce)
