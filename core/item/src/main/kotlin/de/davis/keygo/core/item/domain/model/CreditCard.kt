package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.processor.annotation.VaultEntity
import java.time.YearMonth

@VaultEntity(resString = "credit_card", defaultIconType = "CreditCard")
data class CreditCard(
    override val id: ItemId,
    override val vaultId: VaultId,
    override val name: String,
    override val keyInformation: KeyInformation,
    override val tags: Set<Tag>,
    override val note: String?,
    override val pinned: Boolean,
    val holder: String?,
    val cardNumber: CardNumber?,
    val cvv: CVV?,
    val expirationDate: YearMonth?,
) : Item {
    override val itemType: VaultItemType
        get() = VaultItemType.CreditCard

    val hasAnyContent: Boolean
        get() = !holder.isNullOrBlank()
                || cardNumber != null
                || cvv != null
                || expirationDate != null

    data class CardNumber(
        override val payload: EncryptedPayload,
    ) : SecretField<String> {
        override val blueprint: SecretBlueprint<String, out SecretField<String>> = CardNumber

        companion object : SecretBlueprint<String, CardNumber>() {
            override val label: String = "credit_card_number"
            override val codec: SecretCodec<String> = SecretCodec.StringCodec

            override fun createField(payload: EncryptedPayload): CardNumber = CardNumber(payload)
        }
    }

    data class CVV(
        override val payload: EncryptedPayload,
    ) : SecretField<String> {
        override val blueprint: SecretBlueprint<String, out SecretField<String>> = CVV

        companion object : SecretBlueprint<String, CVV>() {
            override val label: String = "credit_card_cvv"
            override val codec: SecretCodec<String> = SecretCodec.StringCodec

            override fun createField(payload: EncryptedPayload): CVV = CVV(payload)
        }
    }
}