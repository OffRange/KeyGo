package de.davis.keygo.core.security.data.crypto

import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.SecretKind
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.rust.item.ItemManager
import de.davisalessandro.keygo.rust.EncryptedItemBlob
import de.davisalessandro.keygo.rust.ItemAad
import de.davisalessandro.keygo.rust.ItemDataAad
import de.davisalessandro.keygo.rust.ItemKey
import de.davisalessandro.keygo.rust.SecretKind as FfiSecretKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal class CryptographicScopeImpl(
    private val itemKey: ItemKey,
    private val itemAad: ItemAad,
    private val itemManager: ItemManager,
    private val wrapAction: suspend CoroutineScope.() -> KeyInformation,
) : CryptographicScope {
    override suspend fun ByteArray.encrypt(
        kind: SecretKind,
        context: CoroutineContext,
    ): CryptographicData = withContext(context) {
        val ct = itemManager.encryptItemData(
            itemKey = itemKey,
            data = this@encrypt,
            aad = dataAad(kind),
        )

        CryptographicData(
            data = ct.ciphertext,
            iv = ct.nonce,
        )
    }

    override suspend fun CryptographicData.decrypt(
        kind: SecretKind,
        context: CoroutineContext,
    ): ByteArray = withContext(context) {
        val blob = EncryptedItemBlob(
            ciphertext = data,
            nonce = iv,
        )

        itemManager.decryptItemData(
            itemKey = itemKey,
            blob = blob,
            aad = dataAad(kind),
        )
    }

    override suspend fun wrapCurrentItemKey(context: CoroutineContext): KeyInformation =
        withContext(context, block = wrapAction)

    private fun dataAad(kind: SecretKind) = ItemDataAad(
        itemId = itemAad.itemId,
        kind = kind.toFfi(),
    )
}

internal fun SecretKind.toFfi(): FfiSecretKind = when (this) {
    SecretKind.Password -> FfiSecretKind.PASSWORD
    SecretKind.CardNumber -> FfiSecretKind.CARD_NUMBER
    SecretKind.Cvv -> FfiSecretKind.CVV
    SecretKind.PasskeyPrivateKey -> FfiSecretKind.PASSKEY_PRIVATE_KEY
    SecretKind.TotpSecret -> FfiSecretKind.TOTP_SECRET
    SecretKind.PasswordHealthReport -> FfiSecretKind.PASSWORD_HEALTH_REPORT
}
