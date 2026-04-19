package de.davis.keygo.core.security.data.crypto

import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davisalessandro.keygo.rust.ItemManagerInterface
import de.davisalessandro.keygo.rust.KeyWrapperInterface
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import org.koin.core.annotation.Single

@Single
internal class CryptographicScopeProviderImpl(
    private val session: Session,
    private val itemManager: ItemManagerInterface,
    private val keyWrapper: KeyWrapperInterface,
) : CryptographicScopeProvider {

    override suspend fun <R> itemScope(
        wrappedVaultKeyInformation: WrappedVaultKeyInformation,
        wrappedItemKeyInformation: WrappedItemKeyInformation,
        block: suspend CryptographicScope.() -> R,
    ): R {
        val vaultKey = keyWrapper.unwrapVaultKey(
            ark = session.dek.key.encoded,
            wrapped = wrappedVaultKeyInformation.wrappedVaultKey.toWrappedKeyBlob(),
            vaultId = wrappedVaultKeyInformation.vaultId
        )

        val itemKey = wrappedItemKeyInformation.wrappedItemKey?.let {
            keyWrapper.unwrapItemKey(
                vaultKey = vaultKey,
                wrapped = it.toWrappedKeyBlob(),
                aad = wrappedItemKeyInformation.itemAad
            )
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
}

private fun KeyInformation.toWrappedKeyBlob() = WrappedKeyBlob(
    ciphertext = wrappedKey,
    nonce = keyNonce
)

private fun WrappedKeyBlob.toKeyInformation(): KeyInformation =
    KeyInformation(wrappedKey = ciphertext, keyNonce = nonce)
