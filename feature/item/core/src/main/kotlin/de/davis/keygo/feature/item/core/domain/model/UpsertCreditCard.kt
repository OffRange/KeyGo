package de.davis.keygo.feature.item.core.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Tag

@ConsistentCopyVisibility
data class UpsertCreditCard private constructor(
    override val upsertType: UpsertType,
    override val name: FieldUpdate<String>,
    override val note: FieldUpdate<String>,
    override val tags: FieldUpdate<Set<Tag>>,
    val holder: FieldUpdate<String>,
    val cardNumber: FieldUpdate<String>,
    val cvv: FieldUpdate<String>,
    /** Raw `MM/yy` text; parsed to a `YearMonth` inside the use case. */
    val expirationDate: FieldUpdate<String>,
) : UpsertItem {
    companion object {
        fun create(
            vaultId: VaultId,
            name: String,
            cardNumber: String,
            expirationDate: String,
            holder: String? = null,
            cvv: String? = null,
            note: String? = null,
            tags: Set<Tag> = emptySet(),
        ) = UpsertCreditCard(
            upsertType = UpsertType.Create(vaultId),
            name = FieldUpdate.Set(name),
            cardNumber = FieldUpdate.Set(cardNumber),
            expirationDate = FieldUpdate.Set(expirationDate),
            holder = if (!holder.isNullOrBlank()) FieldUpdate.Set(holder) else FieldUpdate.Clear,
            cvv = if (!cvv.isNullOrBlank()) FieldUpdate.Set(cvv) else FieldUpdate.Clear,
            note = if (!note.isNullOrBlank()) FieldUpdate.Set(note) else FieldUpdate.Clear,
            tags = if (tags.isNotEmpty()) FieldUpdate.Set(tags) else FieldUpdate.Clear,
        )

        fun update(
            itemId: ItemId,
            vaultId: VaultId? = null,
            name: FieldUpdate<String> = keep(),
            cardNumber: FieldUpdate<String> = keep(),
            expirationDate: FieldUpdate<String> = keep(),
            holder: FieldUpdate<String> = keep(),
            cvv: FieldUpdate<String> = keep(),
            note: FieldUpdate<String> = keep(),
            tags: FieldUpdate<Set<Tag>> = keep(),
        ) = UpsertCreditCard(
            upsertType = UpsertType.Update(itemId, vaultId),
            name = name,
            cardNumber = cardNumber,
            expirationDate = expirationDate,
            holder = holder,
            cvv = cvv,
            note = note,
            tags = tags,
        )
    }
}
